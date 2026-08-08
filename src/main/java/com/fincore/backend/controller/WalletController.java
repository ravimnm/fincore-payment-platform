package com.fincore.backend.controller;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fincore.backend.entity.Wallet;
import com.fincore.backend.enums.WalletStatus;
import com.fincore.backend.service.WalletService;

@RestController
@RequestMapping("/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/user/{userId}")
    public ResponseEntity<WalletResponse> createWallet(
            @PathVariable Long userId) {

        Wallet wallet = walletService.createWallet(userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(wallet));
    }

    @GetMapping("/{walletId}")
    public ResponseEntity<WalletResponse> getWallet(
            @PathVariable Long walletId) {

        Wallet wallet = walletService.getWallet(walletId);

        return ResponseEntity.ok(toResponse(wallet));
    }

    @GetMapping("/{walletId}/balance")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable Long walletId) {

        return ResponseEntity.ok(
                walletService.getBalance(walletId)
        );
    }

    @PostMapping("/{walletId}/freeze")
    public ResponseEntity<Void> freezeWallet(
            @PathVariable Long walletId) {

        walletService.freezeWallet(walletId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{walletId}/activate")
    public ResponseEntity<Void> activateWallet(
            @PathVariable Long walletId) {

        walletService.activateWallet(walletId);

        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{walletId}/fund")
    public ResponseEntity<WalletResponse> fundWallet(@PathVariable Long walletId,@RequestParam BigDecimal amount) {

        Wallet wallet = walletService.fundWallet(walletId,amount);

        return ResponseEntity.ok(toResponse(wallet));
    }

    private WalletResponse toResponse(Wallet wallet) {

        return new WalletResponse(
                wallet.getId(),
                wallet.getWalletNumber(),
                wallet.getUser().getId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getStatus()
        );
    }

    public static class WalletResponse {

        private Long id;
        private String walletNumber;
        private Long userId;
        private BigDecimal balance;
        private String currency;
        private WalletStatus status;

        public WalletResponse() {
        }

        public WalletResponse(
                Long id,
                String walletNumber,
                Long userId,
                BigDecimal balance,
                String currency,
                WalletStatus status) {

            this.id = id;
            this.walletNumber = walletNumber;
            this.userId = userId;
            this.balance = balance;
            this.currency = currency;
            this.status = status;
        }

        public Long getId() {
            return id;
        }

        public String getWalletNumber() {
            return walletNumber;
        }

        public Long getUserId() {
            return userId;
        }

        public BigDecimal getBalance() {
            return balance;
        }

        public String getCurrency() {
            return currency;
        }

        public WalletStatus getStatus() {
            return status;
        }
    }
}