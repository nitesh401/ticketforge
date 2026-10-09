package com.ticketforge.movie.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** Per-show inventory row: the unit of locking and the single source of truth for seat state. */
@Entity
@Table(name = "show_seats")
public class ShowSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "show_id")
    private Long showId;
    @Column(name = "seat_id")
    private Long seatId;
    @Enumerated(EnumType.STRING)
    private SeatStatus status;
    @Column(name = "hold_id")
    private String holdId;
    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;
    @Version
    private Long version;
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ShowSeat() {
    }

    /** AVAILABLE, or HELD by a hold whose time has run out (lazy expiry before the sweeper gets to it). */
    public boolean isAcquirableAt(Instant now) {
        return status == SeatStatus.AVAILABLE
                || (status == SeatStatus.HELD && holdExpiresAt != null && holdExpiresAt.isBefore(now));
    }

    public boolean isHeldBy(String holdId, Instant now) {
        return status == SeatStatus.HELD && holdId.equals(this.holdId)
                && holdExpiresAt != null && !holdExpiresAt.isBefore(now);
    }

    public void hold(String holdId, Instant expiresAt, Instant now) {
        this.status = SeatStatus.HELD;
        this.holdId = holdId;
        this.holdExpiresAt = expiresAt;
        this.updatedAt = now;
    }

    public void markBooked(Instant now) {
        this.status = SeatStatus.BOOKED;
        this.holdExpiresAt = null;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getShowId() { return showId; }
    public Long getSeatId() { return seatId; }
    public SeatStatus getStatus() { return status; }
    public String getHoldId() { return holdId; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public Long getVersion() { return version; }
}
