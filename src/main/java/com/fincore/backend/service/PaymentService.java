package com.fincore.backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fincore.backend.entity.LedgerEntry;
import com.fincore.backend.entity.Payment;
import com.fincore.backend.entity.Wallet;
import com.fincore.backend.enums.LedgerEntryType;
import com.fincore.backend.enums.PaymentStatus;
import com.fincore.backend.repository.PaymentRepository;
@Service
public class PaymentService {
	private final PaymentRepository paymentRepository;
    private final WalletService walletService;

    public PaymentService(
            PaymentRepository paymentRepository,
            WalletService walletService) {

        this.paymentRepository = paymentRepository;
        this.walletService = walletService;
    }
    
    @Transactional(readOnly = true)
    public Payment getPayment(Long paymentId) {

        return paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new RuntimeException("Payment not found"));
    }

    @Transactional(readOnly = true)
    public Payment getByIdempotencyKey(
            String idempotencyKey) {

        return paymentRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() ->
                        new RuntimeException("Payment not found"));
    }
    
    @Transactional
    public Payment processPayment(Long senderWalletId, Long receiverWalletId, BigDecimal amount, String currency, String idempotencyKey, String description) {
    	validateRequest(senderWalletId,receiverWalletId,amount,currency,idempotencyKey);
    	/*
         * Idempotency check.
         *
         * If the client retries the exact same request,
         * return the previously created payment instead
         * of moving money again.
         */
    	Payment existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);

        if (existingPayment != null) {
            return existingPayment;
        }
        /*
         * Lock wallets in a deterministic order.
         *
         * This prevents two concurrent transfers in opposite
         * directions from acquiring locks in different orders.
         */
        Wallet senderWallet;
        Wallet receiverWallet;

        if (senderWalletId < receiverWalletId) {

            senderWallet =
                    walletService.getWalletForUpdate(senderWalletId);

            receiverWallet =
                    walletService.getWalletForUpdate(receiverWalletId);

        } else {

            receiverWallet =
                    walletService.getWalletForUpdate(receiverWalletId);

            senderWallet =
                    walletService.getWalletForUpdate(senderWalletId);
        }

        /*
         * The same wallet cannot be both sender and receiver.
         */
        if (senderWallet.getId().equals(receiverWallet.getId())) {
            throw new RuntimeException(
                    "Sender and receiver wallets must be different"
            );
        }

        /*
         * Validate wallet state.
         */
        walletService.validateActive(senderWallet);
        walletService.validateActive(receiverWallet);

        /*
         * Currency validation.
         */
        if (!senderWallet.getCurrency().equals(currency)
                || !receiverWallet.getCurrency().equals(currency)) {

            throw new RuntimeException(
                    "Currency mismatch"
            );
        }

        /*
         * Validate available balance before changing anything.
         */
        walletService.validateSufficientBalance(
                senderWallet,
                amount
        );

        /*
         * Move money.
         */
        walletService.debit(
                senderWallet,
                amount
        );

        walletService.credit(
                receiverWallet,
                amount
        );

        /*
         * Create payment record.
         */
        Payment payment = new Payment(
                generatePaymentReference(),
                idempotencyKey,
                senderWallet,
                receiverWallet,
                amount,
                currency,
                description
        );

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCompletedAt(LocalDateTime.now());

        /*
         * Create immutable ledger entries.
         *
         * Sender:
         *      DEBIT
         *
         * Receiver:
         *      CREDIT
         */
        LedgerEntry debitEntry = new LedgerEntry(
                payment,
                senderWallet,
                LedgerEntryType.DEBIT,
                amount,
                senderWallet.getBalance()
        );

        LedgerEntry creditEntry = new LedgerEntry(
                payment,
                receiverWallet,
                LedgerEntryType.CREDIT,
                amount,
                receiverWallet.getBalance()
        );

        payment.addLedgerEntry(debitEntry);
        payment.addLedgerEntry(creditEntry);

        /*
         * Save wallet changes.
         *
         * Because this method is @Transactional, all changes
         * participate in the same database transaction.
         */
        walletService.save(senderWallet);
        walletService.save(receiverWallet);

        /*
         * Cascade persists the ledger entries because
         * Payment.ledgerEntries uses CascadeType.ALL.
         */
        return paymentRepository.save(payment);
    }
    

    private void validateRequest(Long senderWalletId,Long receiverWalletId,BigDecimal amount,String currency,String idempotencyKey) {

        if (senderWalletId == null || receiverWalletId == null) {

            throw new RuntimeException("Sender and receiver wallet IDs are required");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new RuntimeException("Payment amount must be greater than zero");
        }

        if (currency == null || currency.length() != 3) {

            throw new RuntimeException("Currency must be a valid 3-letter code");
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new RuntimeException("Idempotency key is required");
        }

        if (idempotencyKey.length() > 100) {

            throw new RuntimeException("Idempotency key cannot exceed 100 characters");
        }
    }

    private String generatePaymentReference() {

        return "PAY-" +
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 20)
                        .toUpperCase();
    }
}
