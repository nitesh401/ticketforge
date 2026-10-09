package com.ticketforge.movie.domain;

import com.ticketforge.common.exception.InvalidStateTransitionException;
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

@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id")
    private String publicId;
    @Column(name = "user_id")
    private String userId;
    @Column(name = "show_id")
    private Long showId;
    @Column(name = "hold_id")
    private String holdId;
    @Enumerated(EnumType.STRING)
    private BookingStatus status;
    @Column(name = "total_amount_minor")
    private long totalAmountMinor;
    private String currency;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Version
    private Long version;

    protected Booking() {
    }

    public Booking(String publicId, String userId, Long showId, String holdId, long totalAmountMinor,
                   String currency, Instant now) {
        this.publicId = publicId;
        this.userId = userId;
        this.showId = showId;
        this.holdId = holdId;
        this.totalAmountMinor = totalAmountMinor;
        this.currency = currency;
        this.status = BookingStatus.INITIATED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** The only way to change status; invalid edges throw instead of silently corrupting state. */
    public void transitionTo(BookingStatus next, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidStateTransitionException(status.name(), next.name());
        }
        this.status = next;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getUserId() { return userId; }
    public Long getShowId() { return showId; }
    public String getHoldId() { return holdId; }
    public BookingStatus getStatus() { return status; }
    public long getTotalAmountMinor() { return totalAmountMinor; }
    public String getCurrency() { return currency; }
}
