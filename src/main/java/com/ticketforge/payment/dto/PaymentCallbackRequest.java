package com.ticketforge.payment.dto;

import com.ticketforge.payment.domain.PaymentOutcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentCallbackRequest(@NotBlank String paymentRef, @NotNull PaymentOutcome outcome, String reason) {
}
