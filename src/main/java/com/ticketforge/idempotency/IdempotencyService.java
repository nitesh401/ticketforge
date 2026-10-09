package com.ticketforge.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketforge.common.TicketMetrics;
import com.ticketforge.common.exception.BusinessRuleException;
import com.ticketforge.common.exception.ConflictException;
import com.ticketforge.infrastructure.db.TransactionalRunner;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Retry-safe execution. The key row and the business effects commit in ONE transaction:
 *
 *  - INSERT ... ON CONFLICT DO NOTHING claims the key. A concurrent duplicate blocks on the unique
 *    index until the first transaction finishes, then sees 0 rows inserted and replays the stored response.
 *  - If the business action throws, the whole transaction (including the key row) rolls back, so the
 *    client may retry the same key after fixing the cause.
 *  - A crash can never leave "booking created but key missing", which is the classic dual-write bug.
 *
 * A client timeout does not mean the operation failed; replaying the key returns the original result.
 */
@Service
public class IdempotencyService {
    private static final int MAX_KEY_LENGTH = 128;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionalRunner tx;
    private final TicketMetrics metrics;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper mapper, TransactionalRunner tx, TicketMetrics metrics) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tx = tx;
        this.metrics = metrics;
    }

    public <T> IdempotentResult<T> execute(String userId, String key, Object request, Class<T> responseType,
                                           int httpStatus, Supplier<T> action) {
        validate(key);
        String requestHash = hash(request);
        return tx.run(() -> {
            int claimed = jdbc.update("""
                    INSERT INTO idempotency_keys (user_id, idempotency_key, request_hash, status)
                    VALUES (?, ?, ?, 'IN_PROGRESS')
                    ON CONFLICT (user_id, idempotency_key) DO NOTHING
                    """, userId, key, requestHash);
            if (claimed == 0) {
                return replay(userId, key, requestHash, responseType);
            }
            T result = action.get();
            jdbc.update("""
                    UPDATE idempotency_keys SET status = 'COMPLETED', response_status = ?, response_body = ?
                     WHERE user_id = ? AND idempotency_key = ?
                    """, httpStatus, toJson(result), userId, key);
            return new IdempotentResult<>(result, false);
        });
    }

    private <T> IdempotentResult<T> replay(String userId, String key, String requestHash, Class<T> type) {
        List<StoredKey> rows = jdbc.query("""
                SELECT request_hash, status, response_body FROM idempotency_keys
                 WHERE user_id = ? AND idempotency_key = ?
                """, (rs, i) -> new StoredKey(rs.getString(1), rs.getString(2), rs.getString(3)), userId, key);
        if (rows.isEmpty()) {
            throw new ConflictException("IDEMPOTENCY_RACE", "Idempotency key changed concurrently, retry");
        }
        StoredKey stored = rows.get(0);
        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessRuleException("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used with a different request payload");
        }
        if (!"COMPLETED".equals(stored.status()) || stored.body() == null) {
            throw new ConflictException("REQUEST_IN_PROGRESS", "A request with this Idempotency-Key is still in progress");
        }
        metrics.idempotentReplay();
        try {
            return new IdempotentResult<>(mapper.readValue(stored.body(), type), true);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response is unreadable", e);
        }
    }

    private static void validate(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be 1-" + MAX_KEY_LENGTH + " characters");
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize response", e);
        }
    }

    private String hash(Object request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("Cannot hash request", e);
        }
    }

    private record StoredKey(String requestHash, String status, String body) {
    }
}
