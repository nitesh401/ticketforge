package com.ticketforge.payment.controller;

import com.ticketforge.common.exception.ForbiddenOperationException;
import com.ticketforge.config.TicketForgeProperties;
import com.ticketforge.payment.dto.PaymentCallbackRequest;
import com.ticketforge.payment.dto.PaymentCallbackResponse;
import com.ticketforge.payment.dto.ReconciliationItem;
import com.ticketforge.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private final PaymentService paymentService;
    private final byte[] webhookSecret;

    public PaymentController(PaymentService paymentService, TicketForgeProperties props) {
        this.paymentService = paymentService;
        this.webhookSecret = props.security().paymentWebhookSecret().getBytes(StandardCharsets.UTF_8);
    }

    /** Simulated PSP webhook, authenticated by a shared secret. Safe to deliver more than once. */
    @PostMapping("/payments/callback")
    public PaymentCallbackResponse callback(@RequestHeader("X-Webhook-Secret") String secret,
                                            @Valid @RequestBody PaymentCallbackRequest request) {
        if (!MessageDigest.isEqual(webhookSecret, secret.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenOperationException("INVALID_WEBHOOK_SECRET", "Webhook secret mismatch");
        }
        return paymentService.handleCallback(request);
    }

    /** Payments that succeeded at the PSP but could not be fulfilled; input for the refund workflow. */
    @GetMapping("/admin/reconciliation/payments")
    public List<ReconciliationItem> reconciliation() {
        return paymentService.pendingReconciliation();
    }
}
