package com.fincore.backend.controller;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.fincore.backend.entity.User;
import com.fincore.backend.entity.Wallet;
import com.fincore.backend.enums.WalletStatus;
import com.fincore.backend.repository.UserRepository;
import com.fincore.backend.security.AuthUtil;
import com.fincore.backend.service.WalletService;

@RestController
@RequestMapping("/wallet")
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    // Direct "test credit" funding. Keep false in production;
    // real top-ups go through Razorpay.
    @Value("${fincore.dev-funding-enabled:false}")
    private boolean devFundingEnabled;

    public WalletController(
            WalletService walletService,
            UserRepository userRepository) {

        this.walletService = walletService;
        this.userRepository = userRepository;
    }

    // The current user's wallet. Created on first access if missing,
    // so the user never has to "create a wallet" themselves.
    @GetMapping("/me")
    public ResponseEntity<WalletResponse> getMyWallet(
            Authentication authentication) {

        User user = userRepository
            .findByUsername(authentication.getName())
            .orElseThrow(() ->
                new RuntimeException("User not found"));

        Wallet wallet =
            walletService.getOrCreateWalletForUser(user.getId());

        return ResponseEntity.ok(toResponse(wallet));
    }

    @GetMapping("/{walletNumber}")
    public ResponseEntity<WalletResponse> getWalletByNumber(
            @PathVariable String walletNumber,
            Authentication authentication) {

        Wallet wallet =
            walletService.getWalletByNumber(walletNumber);

        assertOwnerOrAdmin(wallet, authentication);

        return ResponseEntity.ok(toResponse(wallet));
    }

    @GetMapping("/{walletId}/balance")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable Long walletId,
            Authentication authentication) {

        Wallet wallet = walletService.getWallet(walletId);

        assertOwnerOrAdmin(wallet, authentication);

        return ResponseEntity.ok(wallet.getBalance());
    }

    @PostMapping("/{walletId}/fund")
    public ResponseEntity<WalletResponse> fundWallet(
            @PathVariable Long walletId,
            @RequestParam BigDecimal amount,
            Authentication authentication) {

        Wallet target = walletService.getWallet(walletId);

        boolean allowed =
            AuthUtil.isAdmin(authentication)
            || (devFundingEnabled
                && walletService.isOwner(
                    target, authentication.getName()));

        if (!allowed) {
            throw new AccessDeniedException(
                "Direct wallet funding is disabled");
        }

        Wallet wallet =
            walletService.fundWallet(walletId, amount);

        return ResponseEntity.ok(toResponse(wallet));
    }

    private void assertOwnerOrAdmin(
            Wallet wallet,
            Authentication authentication) {

        if (!AuthUtil.isAdmin(authentication)
                && !walletService.isOwner(
                    wallet, authentication.getName())) {

            throw new AccessDeniedException(
                "You do not have access to this wallet");
        }
    }

    public static WalletResponse toResponse(Wallet wallet) {

        return new WalletResponse(
            wallet.getId(),
            wallet.getUser() != null ? wallet.getUser().getId() : null,
            wallet.getWalletNumber(),
            wallet.getBalance(),
            wallet.getCurrency(),
            wallet.getStatus()
        );
    }

    public static class WalletResponse {

        private Long id;
        private Long userId;
        private String walletNumber;
        private BigDecimal balance;
        private String currency;
        private WalletStatus status;

        public WalletResponse(
                Long id,
                Long userId,
                String walletNumber,
                BigDecimal balance,
                String currency,
                WalletStatus status) {

            this.id = id;
            this.userId = userId;
            this.walletNumber = walletNumber;
            this.balance = balance;
            this.currency = currency;
            this.status = status;
        }

        public Long getId() {
            return id;
        }

        public Long getUserId() {
            return userId;
        }

        public String getWalletNumber() {
            return walletNumber;
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
