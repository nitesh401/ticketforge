package com.ticketforge.movie.controller;

import com.ticketforge.idempotency.IdempotentResult;
import com.ticketforge.movie.domain.LockStrategy;
import com.ticketforge.movie.dto.HoldRequest;
import com.ticketforge.movie.dto.HoldResponse;
import com.ticketforge.movie.service.HoldService;
import com.ticketforge.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HoldController {
    private final HoldService holdService;

    public HoldController(HoldService holdService) {
        this.holdService = holdService;
    }

    /** strategy=PESSIMISTIC|ATOMIC lets you compare both concurrency mechanisms on the same endpoint. */
    @PostMapping("/shows/{showId}/holds")
    public ResponseEntity<HoldResponse> createHold(
            @PathVariable String showId,
            @Valid @RequestBody HoldRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(value = "strategy", required = false) LockStrategy strategy) {
        IdempotentResult<HoldResponse> result =
                holdService.createHold(showId, request, CurrentUser.require(), idempotencyKey, strategy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.body());
    }

    @GetMapping("/holds/{holdId}")
    public HoldResponse getHold(@PathVariable String holdId) {
        return holdService.getHold(holdId, CurrentUser.require());
    }
}
