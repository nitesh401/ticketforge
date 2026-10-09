package com.ticketforge.support;

import com.ticketforge.common.exception.SeatAlreadyHeldException;
import com.ticketforge.movie.domain.LockStrategy;
import com.ticketforge.movie.dto.BookingRequest;
import com.ticketforge.movie.dto.BookingResponse;
import com.ticketforge.movie.dto.HoldRequest;
import com.ticketforge.movie.dto.HoldResponse;
import com.ticketforge.movie.service.BookingService;
import com.ticketforge.movie.service.HoldExpiryService;
import com.ticketforge.movie.service.HoldService;
import com.ticketforge.payment.domain.PaymentOutcome;
import com.ticketforge.payment.dto.PaymentCallbackRequest;
import com.ticketforge.payment.dto.PaymentCallbackResponse;
import com.ticketforge.payment.service.PaymentService;
import com.ticketforge.security.AuthenticatedUser;
import com.ticketforge.security.JwtService;
import com.ticketforge.security.Role;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for Testcontainers tests: one PostgreSQL container for the whole test run.
 * Tests are skipped (not failed) when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
@EnabledIf("com.ticketforge.support.AbstractPostgresIT#dockerAvailable")
public abstract class AbstractPostgresIT {
    protected static final String SHOW = "SHOW-AVENGERS-1930";
    protected static final String OTHER_SHOW = "SHOW-AVENGERS-2230";

    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (dockerAvailable()) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    public static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "40");
        r.add("spring.datasource.hikari.connection-timeout", () -> "180000");
        r.add("ticketforge.security.jwt-secret", () -> "test-secret-test-secret-test-secret-123456");
        r.add("ticketforge.security.payment-webhook-secret", () -> "test-webhook-secret");
        r.add("ticketforge.hold.sweeper-enabled", () -> "false");
        r.add("ticketforge.retry.max-attempts", () -> "8");
        r.add("ticketforge.demo.unsafe-inventory-enabled", () -> "true");
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MutableClock clock;
    @Autowired protected HoldService holdService;
    @Autowired protected BookingService bookingService;
    @Autowired protected PaymentService paymentService;
    @Autowired protected HoldExpiryService expiryService;
    @Autowired protected JwtService jwtService;

    @BeforeEach
    void resetState() {
        clock.reset();
        jdbc.execute("DELETE FROM payments");
        jdbc.execute("DELETE FROM booking_items");
        jdbc.execute("DELETE FROM bookings");
        jdbc.execute("DELETE FROM hold_items");
        jdbc.execute("UPDATE show_seats SET status = 'AVAILABLE', hold_id = NULL, hold_expires_at = NULL, version = 0");
        jdbc.execute("DELETE FROM holds");
        jdbc.execute("DELETE FROM idempotency_keys");
        jdbc.execute("DELETE FROM rail_passengers");
        jdbc.execute("DELETE FROM rail_bookings");
        jdbc.execute("UPDATE quota_inventory SET available_count = total_capacity, version = 0");
    }

    protected AuthenticatedUser user(String id) {
        return new AuthenticatedUser(id, Role.USER);
    }

    protected String bearer(String userId) {
        return "Bearer " + jwtService.issue(userId, Role.USER);
    }

    protected HoldResponse hold(String userId, String show, LockStrategy strategy, String... seats) {
        return holdService.createHold(show, new HoldRequest(userId, List.of(seats)), user(userId), null, strategy).body();
    }

    /** true = hold created, false = SEAT_ALREADY_HELD. Any other exception propagates as a test error. */
    protected boolean tryHold(String userId, String show, LockStrategy strategy, String... seats) {
        try {
            hold(userId, show, strategy, seats);
            return true;
        } catch (SeatAlreadyHeldException e) {
            return false;
        }
    }

    protected BookingResponse book(String userId, String holdId) {
        return bookingService.createBooking(new BookingRequest(holdId), user(userId), UUID.randomUUID().toString()).body();
    }

    protected PaymentCallbackResponse pay(String paymentRef, PaymentOutcome outcome) {
        return paymentService.handleCallback(new PaymentCallbackRequest(paymentRef, outcome, null));
    }

    protected String seatStatus(String show, String label) {
        return seatColumn("status", show, label);
    }

    protected String seatHoldId(String show, String label) {
        return seatColumn("hold_id", show, label);
    }

    private String seatColumn(String column, String show, String label) {
        return jdbc.queryForObject("SELECT ss." + column + " FROM show_seats ss JOIN seats s ON s.id = ss.seat_id "
                + "JOIN shows sh ON sh.id = ss.show_id WHERE sh.public_id = ? AND s.label = ?",
                String.class, show, label);
    }

    protected long countSeats(String show, String status) {
        return jdbc.queryForObject("SELECT count(*) FROM show_seats ss JOIN shows sh ON sh.id = ss.show_id "
                + "WHERE sh.public_id = ? AND ss.status = ?", Long.class, show, status);
    }

    protected String holdStatus(String holdId) {
        return jdbc.queryForObject("SELECT status FROM holds WHERE id = ?", String.class, holdId);
    }

    /** Creates (once) a show with N seats so tests can run "1000 users, 100 seats". */
    protected String largeShow(int seats) {
        String code = "AUDITORIUM-XL" + seats;
        String showId = "SHOW-XL" + seats;
        jdbc.update("INSERT INTO screens (theatre_id, code, name) SELECT id, ?, 'XL' FROM theatres LIMIT 1 "
                + "ON CONFLICT DO NOTHING", code);
        jdbc.update("INSERT INTO seats (screen_id, label) SELECT sc.id, 'S' || g FROM screens sc, "
                + "generate_series(1, ?) AS g WHERE sc.code = ? ON CONFLICT DO NOTHING", seats, code);
        jdbc.update("INSERT INTO shows (public_id, movie_id, screen_id, starts_at, price_minor) "
                + "SELECT ?, m.id, sc.id, now() + interval '30 days', 10000 FROM movies m, screens sc "
                + "WHERE m.title = 'Avengers' AND sc.code = ? ON CONFLICT (public_id) DO NOTHING", showId, code);
        jdbc.update("INSERT INTO show_seats (show_id, seat_id) SELECT sh.id, se.id FROM shows sh "
                + "JOIN seats se ON se.screen_id = sh.screen_id WHERE sh.public_id = ? "
                + "ON CONFLICT (show_id, seat_id) DO NOTHING", showId);
        return showId;
    }
}
