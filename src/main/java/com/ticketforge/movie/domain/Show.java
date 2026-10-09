package com.ticketforge.movie.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "shows")
public class Show {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id")
    private String publicId;
    @Column(name = "movie_id")
    private Long movieId;
    @Column(name = "screen_id")
    private Long screenId;
    @Column(name = "starts_at")
    private Instant startsAt;
    @Column(name = "price_minor")
    private long priceMinor;
    private String currency;

    protected Show() {
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public Long getMovieId() { return movieId; }
    public Long getScreenId() { return screenId; }
    public Instant getStartsAt() { return startsAt; }
    public long getPriceMinor() { return priceMinor; }
    public String getCurrency() { return currency; }
}
