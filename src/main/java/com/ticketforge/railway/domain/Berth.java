package com.ticketforge.railway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "berths")
public class Berth {
    @Id
    private Long id;
    @Column(name = "coach_id")
    private Long coachId;
    @Column(name = "berth_number")
    private int berthNumber;
    private int ordinal;
    private String label;

    protected Berth() {
    }

    public Long getId() { return id; }
    public int getOrdinal() { return ordinal; }
    public String getLabel() { return label; }
}
