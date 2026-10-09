package com.ticketforge.railway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "rail_bookings")
public class RailBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id")
    private String publicId;
    private String pnr;
    @Column(name = "user_id")
    private String userId;
    @Column(name = "train_schedule_id")
    private Long trainScheduleId;
    @Enumerated(EnumType.STRING)
    @Column(name = "quota_type")
    private QuotaType quotaType;
    @Column(name = "passenger_count")
    private int passengerCount;
    private String status;
    @Column(name = "created_at")
    private Instant createdAt;

    protected RailBooking() {
    }

    public RailBooking(String publicId, String pnr, String userId, Long trainScheduleId, QuotaType quotaType,
                       int passengerCount, Instant now) {
        this.publicId = publicId;
        this.pnr = pnr;
        this.userId = userId;
        this.trainScheduleId = trainScheduleId;
        this.quotaType = quotaType;
        this.passengerCount = passengerCount;
        this.status = "CONFIRMED";
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getPnr() { return pnr; }
    public String getUserId() { return userId; }
    public Long getTrainScheduleId() { return trainScheduleId; }
    public QuotaType getQuotaType() { return quotaType; }
    public String getStatus() { return status; }
}
