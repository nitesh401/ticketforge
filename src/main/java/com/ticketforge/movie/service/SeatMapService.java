package com.ticketforge.movie.service;

import com.ticketforge.common.exception.ResourceNotFoundException;
import com.ticketforge.movie.domain.SeatStatus;
import com.ticketforge.movie.domain.Show;
import com.ticketforge.movie.dto.SeatMapResponse;
import com.ticketforge.movie.dto.SeatView;
import com.ticketforge.movie.repository.ShowRepository;
import com.ticketforge.movie.repository.ShowSeatRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatMapService {
    private final ShowRepository shows;
    private final ShowSeatRepository showSeats;
    private final Clock clock;

    public SeatMapService(ShowRepository shows, ShowSeatRepository showSeats, Clock clock) {
        this.shows = shows;
        this.showSeats = showSeats;
        this.clock = clock;
    }

    /** Advisory only: two users can both see AVAILABLE. Only the hold endpoint is authoritative. */
    @Transactional(readOnly = true)
    public SeatMapResponse seatMap(String showPublicId) {
        Show show = shows.findByPublicId(showPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("SHOW_NOT_FOUND", "Show not found"));
        Instant now = clock.instant();
        var seats = showSeats.findSeatMap(show.getId()).stream().map(row -> {
            boolean lapsed = row.status() == SeatStatus.HELD && row.holdExpiresAt() != null
                    && row.holdExpiresAt().isBefore(now);
            return new SeatView(row.label(), lapsed ? SeatStatus.AVAILABLE : row.status());
        }).toList();
        return new SeatMapResponse(show.getPublicId(), seats,
                "Advisory view: availability is only guaranteed by a successful hold.");
    }
}
