package com.ticketforge.movie.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "holds")
public class Hold {
    @Id
    private String id;
    @Column(name = "user_id")
    private String userId;
    @Column(name = "show_id")
    private Long showId;
    @Enumerated(EnumType.STRING)
    private HoldStatus status;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "created_at")
    private Instant createdAt;
    @Version
    private Long version;

    protected Hold() {
    }

    public Hold(String id, String userId, Long showId, Instant expiresAt, Instant now) {
        this.id = id;
        this.userId = userId;
        this.showId = showId;
        this.status = HoldStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public boolean isActiveAt(Instant now) {
        return status == HoldStatus.ACTIVE && expiresAt.isAfter(now);
    }

    /** What a client should see: an ACTIVE hold past its deadline is EXPIRED even before the sweeper runs. */
    public HoldStatus effectiveStatus(Instant now) {
        return (status == HoldStatus.ACTIVE && !expiresAt.isAfter(now)) ? HoldStatus.EXPIRED : status;
    }

    public void setStatus(HoldStatus status) {
        this.status = status;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public Long getShowId() { return showId; }
    public HoldStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
}
