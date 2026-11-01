package com.fincore.backend.controller;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.fincore.backend.dto.PaymentHistoryItem;
import com.fincore.backend.dto.PaymentRequest;
import com.fincore.backend.dto.PaymentResponse;
import com.fincore.backend.entity.Payment;
import com.fincore.backend.entity.User;
import com.fincore.backend.entity.Wallet;
import com.fincore.backend.repository.UserRepository;
import com.fincore.backend.security.AuthUtil;
import com.fincore.backend.service.PaymentService;
import com.fincore.backend.service.WalletService;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final WalletService walletService;
    private final UserRepository userRepository;

    public PaymentController(
            PaymentService paymentService,
            WalletService walletService,
            UserRepository userRepository) {

        this.paymentService = paymentService;
        this.walletService = walletService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest request,
            Authentication authentication) {

        // The sender wallet must belong to the logged-in user
        walletService.getOwnedWallet(
                request.getSenderWalletId(),
                authentication.getName());

        Payment payment = paymentService.processPayment(
                request.getSenderWalletId(),
                request.getReceiverWalletId(),
                request.getAmount(),
                request.getCurrency(),
                request.getIdempotencyKey(),
                request.getDescription()
        );

        // An idempotency key replays the stored payment; make sure it
        // is this user's payment and not someone else's.
        if (!payment.getSenderWallet().getId()
                .equals(request.getSenderWalletId())) {

            throw new RuntimeException(
                    "Idempotency key is already in use");
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(payment));
    }

    // Payment history of the logged-in user's wallet
    @GetMapping("/me")
    public ResponseEntity<List<PaymentHistoryItem>> myPayments(
            @RequestParam(defaultValue = "50") int limit,
            Authentication authentication) {

        User user = userRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        Wallet wallet =
                walletService.getOrCreateWalletForUser(user.getId());

        int safeLimit = Math.max(1, Math.min(limit, 200));

        return ResponseEntity.ok(
                paymentService.getHistory(wallet.getId(), safeLimit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable Long id,
            Authentication authentication) {

        Payment payment = paymentService.getPayment(id);

        assertParticipantOrAdmin(payment, authentication);

        return ResponseEntity.ok(toResponse(payment));
    }

    @GetMapping("/idempotency/{key}")
    public ResponseEntity<PaymentResponse> getByIdempotencyKey(
            @PathVariable String key,
            Authentication authentication) {

        Payment payment = paymentService.getByIdempotencyKey(key);

        assertParticipantOrAdmin(payment, authentication);

        return ResponseEntity.ok(toResponse(payment));
    }

    private void assertParticipantOrAdmin(
            Payment payment,
            Authentication authentication) {

        String username = authentication.getName();

        boolean participant =
                walletService.isOwner(payment.getSenderWallet(), username)
                || walletService.isOwner(payment.getReceiverWallet(), username);

        if (!participant && !AuthUtil.isAdmin(authentication)) {
            throw new AccessDeniedException(
                    "You do not have access to this payment");
        }
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
