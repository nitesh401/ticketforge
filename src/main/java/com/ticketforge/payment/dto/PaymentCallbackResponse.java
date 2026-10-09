package com.ticketforge.payment.dto;

public record PaymentCallbackResponse(String paymentRef, String paymentStatus, String bookingId,
                                      String bookingStatus, boolean reconciliationRequired) {
}
