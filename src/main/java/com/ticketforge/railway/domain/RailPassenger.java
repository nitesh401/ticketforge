package com.ticketforge.railway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rail_passengers")
public class RailPassenger {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "rail_booking_id")
    private Long railBookingId;
    @Column(name = "train_schedule_id")
    private Long trainScheduleId;
    private String name;
    private int age;
    @Column(name = "berth_id")
    private Long berthId;

    protected RailPassenger() {
    }

    public RailPassenger(Long railBookingId, Long trainScheduleId, String name, int age, Long berthId) {
        this.railBookingId = railBookingId;
        this.trainScheduleId = trainScheduleId;
        this.name = name;
        this.age = age;
        this.berthId = berthId;
    }

    public Long getRailBookingId() { return railBookingId; }
    public String getName() { return name; }
    public int getAge() { return age; }
    public Long getBerthId() { return berthId; }
}
