package com.ticketforge.movie.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hold_items")
public class HoldItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "hold_id")
    private String holdId;
    @Column(name = "show_seat_id")
    private Long showSeatId;

    protected HoldItem() {
    }

    public HoldItem(String holdId, Long showSeatId) {
        this.holdId = holdId;
        this.showSeatId = showSeatId;
    }

    public Long getShowSeatId() { return showSeatId; }
}
