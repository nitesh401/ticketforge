package com.ticketforge.payment.domain;

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
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "provider_ref")
    private String providerRef;
    @Column(name = "booking_id")
    private Long bookingId;
    @Column(name = "amount_minor")
    private long amountMinor;
    private String currency;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    private String reason;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;
    @Version
    private Long version;

    protected Payment() {
    }

    public Payment(String providerRef, Long bookingId, long amountMinor, String currency, Instant now) {
        this.providerRef = providerRef;
        this.bookingId = bookingId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markSucceeded(Instant now) {
        change(PaymentStatus.SUCCEEDED, null, now);
    }

    public void markFailed(String reason, Instant now) {
        change(PaymentStatus.FAILED, reason, now);
    }

    /** Money was taken but the seats could not be delivered: hand over to the refund workflow. */
    public void markRefundRequired(String reason, Instant now) {
        change(PaymentStatus.REFUND_REQUIRED, reason, now);
    }

    private void change(PaymentStatus next, String reason, Instant now) {
        this.status = next;
        this.reason = reason;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getProviderRef() { return providerRef; }
    public Long getBookingId() { return bookingId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getReason() { return reason; }
}
