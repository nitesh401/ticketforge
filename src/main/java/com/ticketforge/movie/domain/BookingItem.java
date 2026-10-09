package com.ticketforge.movie.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "booking_items")
public class BookingItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "booking_id")
    private Long bookingId;
    @Column(name = "show_seat_id")
    private Long showSeatId;
    @Column(name = "price_minor")
    private long priceMinor;

    protected BookingItem() {
    }

    public BookingItem(Long bookingId, Long showSeatId, long priceMinor) {
        this.bookingId = bookingId;
        this.showSeatId = showSeatId;
        this.priceMinor = priceMinor;
    }

    public Long getShowSeatId() { return showSeatId; }
}
