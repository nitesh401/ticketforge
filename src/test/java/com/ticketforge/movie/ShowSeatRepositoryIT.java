package com.ticketforge.movie;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketforge.movie.domain.Hold;
import com.ticketforge.movie.repository.HoldRepository;
import com.ticketforge.movie.repository.ShowRepository;
import com.ticketforge.movie.repository.ShowSeatRepository;
import com.ticketforge.support.AbstractPostgresIT;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Shows the rowsUpdated contract of the atomic UPDATE in isolation. */
@Transactional
class ShowSeatRepositoryIT extends AbstractPostgresIT {
    @Autowired ShowSeatRepository showSeats;
    @Autowired ShowRepository shows;
    @Autowired HoldRepository holds;

    @Test
    void atomicUpdate_returnsOneForWinnerAndZeroForLoser() {
        Long showId = shows.findByPublicId(SHOW).orElseThrow().getId();
        Long a1 = showSeats.findIdsByShowAndLabels(showId, List.of("A1")).get(0);
        Instant now = Instant.now();
        Instant expires = now.plus(Duration.ofMinutes(5));
        holds.saveAndFlush(new Hold("HOLD-T1", "user-A", showId, expires, now));
        holds.saveAndFlush(new Hold("HOLD-T2", "user-B", showId, expires, now));

        assertThat(showSeats.acquireSeat(a1, "HOLD-T1", expires, now,
                com.ticketforge.movie.domain.SeatStatus.HELD, com.ticketforge.movie.domain.SeatStatus.AVAILABLE)).isEqualTo(1);
        assertThat(showSeats.tryAcquire(a1, "HOLD-T2", expires, now)).isFalse();
    }

    @Test
    void expiredHoldCanBeTakenOver_butLiveHoldCannot() {
        Long showId = shows.findByPublicId(SHOW).orElseThrow().getId();
        Long a1 = showSeats.findIdsByShowAndLabels(showId, List.of("A1")).get(0);
        Instant now = Instant.now();
        Instant expires = now.plus(Duration.ofMinutes(5));
        holds.saveAndFlush(new Hold("HOLD-T3", "user-A", showId, expires, now));
        holds.saveAndFlush(new Hold("HOLD-T4", "user-B", showId, expires, now));
        assertThat(showSeats.tryAcquire(a1, "HOLD-T3", expires, now)).isTrue();

        assertThat(showSeats.tryAcquire(a1, "HOLD-T4", expires, now.plusSeconds(10))).isFalse();
        assertThat(showSeats.tryAcquire(a1, "HOLD-T4", expires.plus(Duration.ofMinutes(5)),
                expires.plusSeconds(1))).isTrue();
    }
}
