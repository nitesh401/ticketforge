package com.ticketforge.infrastructure.redis;

import com.ticketforge.config.TicketForgeProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * SET seat:{showId}:{label} {holdId} NX EX {ttl}
 *   NX = only set if the key does not exist, EX = auto-expire so a crashed node cannot hold a seat forever.
 *
 * Fail-open on Redis errors: if Redis is down we skip the gate and let PostgreSQL decide. Correctness
 * is unaffected because the database transition is atomic; only the early-reject optimisation is lost.
 */
@Component
@ConditionalOnProperty(name = "ticketforge.redis.enabled", havingValue = "true")
public class RedisSeatLockGate implements SeatLockGate {
    private static final Logger log = LoggerFactory.getLogger(RedisSeatLockGate.class);

    // Delete only if the value is still ours, so we never remove a lock someone else acquired after our TTL lapsed.
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final java.time.Duration ttl;

    public RedisSeatLockGate(StringRedisTemplate redis, TicketForgeProperties props) {
        this.redis = redis;
        this.ttl = props.hold().duration();
    }

    @Override
    public boolean tryLock(long showId, Collection<String> seatLabels, String holdId) {
        List<String> acquired = new ArrayList<>();
        try {
            for (String label : seatLabels) {
                Boolean ok = redis.opsForValue().setIfAbsent(key(showId, label), holdId, ttl);
                if (!Boolean.TRUE.equals(ok)) {
                    release(showId, acquired, holdId);
                    return false;
                }
                acquired.add(label);
            }
            return true;
        } catch (DataAccessException e) {
            log.warn("Redis unavailable, falling back to database-only locking: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public void release(long showId, Collection<String> seatLabels, String holdId) {
        try {
            for (String label : seatLabels) {
                redis.execute(RELEASE_SCRIPT, List.of(key(showId, label)), holdId);
            }
        } catch (DataAccessException e) {
            log.warn("Could not release Redis seat locks (TTL will clean up): {}", e.getMessage());
        }
    }

    private static String key(long showId, String label) {
        return "seat:" + showId + ":" + label;
    }
}
