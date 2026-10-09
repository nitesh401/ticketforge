package com.ticketforge.railway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "train_schedules")
public class TrainSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "train_id")
    private Long trainId;
    @Column(name = "journey_date")
    private LocalDate journeyDate;

    protected TrainSchedule() {
    }

    public Long getId() { return id; }
    public Long getTrainId() { return trainId; }
    public LocalDate getJourneyDate() { return journeyDate; }
}
