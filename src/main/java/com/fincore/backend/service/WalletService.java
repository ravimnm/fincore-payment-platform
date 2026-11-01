
package com.fincore.backend.service;

import java.math.BigDecimal;
import java.security.SecureRandom;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fincore.backend.entity.User;
import com.fincore.backend.entity.Wallet;
import com.fincore.backend.enums.WalletStatus;
import com.fincore.backend.repository.UserRepository;
import com.fincore.backend.repository.WalletRepository;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public WalletService(
            WalletRepository walletRepository,
            UserRepository userRepository) {

        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
    }

    // Automatically create wallet after user registration
    @Transactional
    public Wallet createWallet(Long userId) {

        User user = userRepository.findById(userId)
            .orElseThrow(() ->
                new RuntimeException("User not found"));

        if (walletRepository.findByUserId(userId).isPresent()) {
            throw new RuntimeException(
                "User already has a wallet");
        }

        Wallet wallet = new Wallet();

        wallet.setWalletNumber(generateWalletNumber());
        wallet.setUser(user);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setCurrency("INR");
        wallet.setStatus(WalletStatus.ACTIVE);

        return walletRepository.save(wallet);
    }

    // Every user gets a wallet automatically. Registration creates it,
    // and this covers users who have none (older accounts, OAuth sign-ups).
    @Transactional
    public Wallet getOrCreateWalletForUser(Long userId) {

        return walletRepository.findByUserId(userId)
            .orElseGet(() -> createWallet(userId));
    }

    // Returns the wallet only if it belongs to the given username
    @Transactional(readOnly = true)
    public Wallet getOwnedWallet(Long walletId, String username) {

        Wallet wallet = getWallet(walletId);

        if (!isOwner(wallet, username)) {
            throw new AccessDeniedException(
                "You do not have access to this wallet");
        }

        return wallet;
    }

    public boolean isOwner(Wallet wallet, String username) {

        return wallet.getUser() != null
            && wallet.getUser().getUsername().equals(username);
    }

    // Retrieve wallet using authenticated user's ID
    @Transactional(readOnly = true)
    public Wallet getWalletByUserId(Long userId) {

        return walletRepository.findByUserId(userId)
            .orElseThrow(() ->
                new RuntimeException("Wallet not found"));
    }

    // Retrieve wallet using public wallet number
    @Transactional(readOnly = true)
    public Wallet getWalletByNumber(String walletNumber) {

        return walletRepository.findByWalletNumber(walletNumber)
            .orElseThrow(() ->
                new RuntimeException("Wallet not found"));
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(Long walletId) {

        return walletRepository.findById(walletId)
            .orElseThrow(() ->
                new RuntimeException("Wallet not found"));
    }

    @Transactional
    public Wallet save(Wallet wallet) {
        return walletRepository.save(wallet);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long walletId) {
        return getWallet(walletId).getBalance();
    }

    @Transactional
    public Wallet activateWallet(Long walletId) {

        Wallet wallet = getWalletForUpdate(walletId);

        if (wallet.getStatus() == WalletStatus.CLOSED) {
            throw new RuntimeException(
                "Closed wallet cannot be activated");
        }

        wallet.setStatus(WalletStatus.ACTIVE);

        return wallet;
    }

    @Transactional
    public void freezeWallet(Long walletId) {

        Wallet wallet = getWalletForUpdate(walletId);

        if (wallet.getStatus() == WalletStatus.CLOSED) {
            throw new RuntimeException(
                "Closed wallet cannot be frozen");
        }

        wallet.setStatus(WalletStatus.FROZEN);
    }

    @Transactional
    public Wallet getWalletForUpdate(Long walletId) {

        return walletRepository.findByIdForUpdate(walletId)
            .orElseThrow(() ->
                new RuntimeException("Wallet not found"));
    }

    public void validateActive(Wallet wallet) {

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new RuntimeException("Wallet is not active");
        }
    }

    public void validateSufficientBalance(
            Wallet wallet,
            BigDecimal amount) {

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException(
                "Insufficient wallet balance");
        }
    }

    public void debit(Wallet wallet, BigDecimal amount) {

        validateActive(wallet);
        validateSufficientBalance(wallet, amount);

        wallet.setBalance(
            wallet.getBalance().subtract(amount)
        );
    }

    public void credit(Wallet wallet, BigDecimal amount) {

        validateActive(wallet);

        wallet.setBalance(
            wallet.getBalance().add(amount)
        );
    }

    @Transactional
    public Wallet fundWallet(Long walletId, BigDecimal amount) {

        if (amount == null ||
                amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new RuntimeException(
                "Funding amount must be greater than zero");
        }

        Wallet wallet = getWalletForUpdate(walletId);

        validateActive(wallet);

        wallet.setBalance(
            wallet.getBalance().add(amount)
        );

        return walletRepository.save(wallet);
    }

    private String generateWalletNumber() {

        String walletNumber;

        do {
            StringBuilder number = new StringBuilder();

            for (int i = 0; i < 12; i++) {
                number.append(secureRandom.nextInt(10));
            }

            walletNumber = number.toString();

        } while (walletRepository.existsByWalletNumber(walletNumber));

        return walletNumber;
    }
}
