package com.fincore.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fincore.backend.service.WalletService;

// Access is restricted to ROLE_ADMIN / ROLE_SUPER_ADMIN in SecurityConfig
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final WalletService walletService;

    public AdminController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/wallets/{walletId}")
    public ResponseEntity<WalletController.WalletResponse> getWallet(
            @PathVariable Long walletId) {

        return ResponseEntity.ok(
            WalletController.toResponse(
                walletService.getWallet(walletId)));
    }

    @PostMapping("/wallets/{walletId}/freeze")
    public ResponseEntity<WalletController.WalletResponse> freeze(
            @PathVariable Long walletId) {

        walletService.freezeWallet(walletId);

        return ResponseEntity.ok(
            WalletController.toResponse(
                walletService.getWallet(walletId)));
    }

    @PostMapping("/wallets/{walletId}/activate")
    public ResponseEntity<WalletController.WalletResponse> activate(
            @PathVariable Long walletId) {

        return ResponseEntity.ok(
            WalletController.toResponse(
                walletService.activateWallet(walletId)));
    }
}
