package com.ticketforge.movie.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Physical seat. Immutable reference data: bookings never lock or modify it. */
@Entity
@Table(name = "seats")
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "screen_id")
    private Long screenId;
    private String label;

    protected Seat() {
    }

    public Long getId() { return id; }
    public Long getScreenId() { return screenId; }
    public String getLabel() { return label; }
}
