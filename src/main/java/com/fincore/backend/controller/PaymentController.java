package com.fincore.backend.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fincore.backend.dto.PaymentRequest;
import com.fincore.backend.dto.PaymentResponse;
import com.fincore.backend.entity.Payment;
import com.fincore.backend.service.PaymentService;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest request) {

        Payment payment = paymentService.processPayment(
                request.getSenderWalletId(),
                request.getReceiverWalletId(),
                request.getAmount(),
                request.getCurrency(),
                request.getIdempotencyKey(),
                request.getDescription()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(payment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable Long id) {

        Payment payment =
                paymentService.getPayment(id);

        return ResponseEntity.ok(toResponse(payment));
    }

    @GetMapping("/idempotency/{key}")
    public ResponseEntity<PaymentResponse> getByIdempotencyKey(
            @PathVariable String key) {

        Payment payment =
                paymentService.getByIdempotencyKey(key);

        return ResponseEntity.ok(toResponse(payment));
    }

    private PaymentResponse toResponse(Payment payment) {

        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentReference(),
                payment.getIdempotencyKey(),
                payment.getSenderWallet().getId(),
                payment.getReceiverWallet().getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getDescription(),
                payment.getCreatedAt(),
                payment.getCompletedAt()
        );
    }
}