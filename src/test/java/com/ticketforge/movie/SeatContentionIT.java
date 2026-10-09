package com.ticketforge.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketforge.common.exception.SeatAlreadyHeldException;
import com.ticketforge.movie.domain.LockStrategy;
import com.ticketforge.support.AbstractPostgresIT;
import com.ticketforge.support.ConcurrencyHarness;
import com.ticketforge.support.ConcurrencyHarness.Stats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SeatContentionIT extends AbstractPostgresIT {

    @ParameterizedTest
    @EnumSource(LockStrategy.class)
    void twoUsersRequestSameSeat_exactlyOneWins(LockStrategy strategy) {
        Stats stats = ConcurrencyHarness.run("2 users, seat A1, " + strategy, 2,
                i -> tryHold("user-" + i, SHOW, strategy, "A1"));

        assertThat(stats.errors()).isZero();
        assertThat(stats.success()).isEqualTo(1);
        assertThat(stats.rejected()).isEqualTo(1);
        assertThat(seatStatus(SHOW, "A1")).isEqualTo("HELD");
    }

    @ParameterizedTest
    @EnumSource(LockStrategy.class)
    void hundredUsersRequestSameSeat_exactlyOneWins(LockStrategy strategy) {
        Stats stats = ConcurrencyHarness.run("100 users, seat A1, " + strategy, 100,
                i -> tryHold("user-" + i, SHOW, strategy, "A1"));

        assertThat(stats.errors()).isZero();
        assertThat(stats.success()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM holds", Long.class)).isEqualTo(1L);
    }

    @ParameterizedTest
    @EnumSource(LockStrategy.class)
    void thousandUsersHundredSeats_noSeatIsHeldTwice(LockStrategy strategy) {
        String show = largeShow(100);
        Stats stats = ConcurrencyHarness.run("1000 users, 100 seats, " + strategy, 1000,
                i -> tryHold("user-" + i, show, strategy, "S" + (i % 100 + 1)));

        assertThat(stats.errors()).isZero();
        assertThat(stats.success()).isEqualTo(100);
        assertThat(countSeats(show, "HELD")).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT hold_id) FROM show_seats WHERE status = 'HELD' "
                + "AND show_id = (SELECT id FROM shows WHERE public_id = ?)", Long.class, show)).isEqualTo(100L);
    }

    @ParameterizedTest
    @EnumSource(LockStrategy.class)
    void multiSeatHoldIsAllOrNothing(LockStrategy strategy) {
        hold("user-B", SHOW, strategy, "A2");

        assertThatThrownBy(() -> hold("user-A", SHOW, strategy, "A1", "A2", "A3"))
                .isInstanceOf(SeatAlreadyHeldException.class);

        // A1 and A3 must not stay half-held after the failed request.
        assertThat(seatStatus(SHOW, "A1")).isEqualTo("AVAILABLE");
        assertThat(seatStatus(SHOW, "A3")).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM holds", Long.class)).isEqualTo(1L);
    }

    @ParameterizedTest
    @EnumSource(LockStrategy.class)
    void overlappingMultiSeatRequestsInOppositeOrderNeitherDeadlockNorDoubleBook(LockStrategy strategy) {
        String[][] sets = {{"A1", "A2"}, {"A2", "A1"}, {"A2", "A3"}, {"A3", "A2"}, {"A3", "A1"}, {"A1", "A3"}};
        Stats stats = ConcurrencyHarness.run("overlapping pairs, " + strategy, 120,
                i -> tryHold("user-" + i, SHOW, strategy, sets[i % sets.length]));

        assertThat(stats.errors()).as("no deadlock / unexpected errors: %s", stats.errorSamples()).isZero();
        // Any two of the three pairs overlap, so exactly one request can win.
        assertThat(stats.success()).isEqualTo(1);
        assertThat(countSeats(SHOW, "HELD")).isEqualTo(2);
    }

    @Test
    void sameSeatCanBeHeldInDifferentShows() {
        assertThat(tryHold("user-A", SHOW, LockStrategy.ATOMIC, "A1")).isTrue();
        assertThat(tryHold("user-B", OTHER_SHOW, LockStrategy.ATOMIC, "A1")).isTrue();
        assertThat(seatStatus(SHOW, "A1")).isEqualTo("HELD");
        assertThat(seatStatus(OTHER_SHOW, "A1")).isEqualTo("HELD");
    }
}
