package com.ticketforge.payment.dto;

public record ReconciliationItem(String paymentRef, String bookingId, long amountMinor, String currency, String reason) {
}
