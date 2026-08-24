package com.fincore.backend.gateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fincore.backend.entity.LedgerEntry;
import com.fincore.backend.entity.Wallet;
import com.fincore.backend.enums.LedgerEntryType;
import com.fincore.backend.gateway.RazorpayClient.RazorpayOrder;
import com.fincore.backend.gateway.dto.GatewayOrderRequest;
import com.fincore.backend.gateway.dto.GatewayOrderResponse;
import com.fincore.backend.repository.LedgerEntryRepository;
import com.fincore.backend.service.WalletService;

@Service
public class RazorpayService {

    private final RazorpayClient razorpayClient;
    private final WalletService walletService;
    private final GatewayTransactionRepository gatewayTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    
    public RazorpayService(
            RazorpayClient razorpayClient,
            WalletService walletService,
            GatewayTransactionRepository gatewayTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository) {

        this.razorpayClient = razorpayClient;
        this.walletService = walletService;
        this.gatewayTransactionRepository =
                gatewayTransactionRepository;
        this.ledgerEntryRepository =
                ledgerEntryRepository;
    }

    @Transactional
    public GatewayOrderResponse createOrder(
            GatewayOrderRequest request) {
    	
    	GatewayTransaction existing =
    	        gatewayTransactionRepository
    	                .findByIdempotencyKey(
    	                        request.getIdempotencyKey()
    	                )
    	                .orElse(null);

    	if (existing != null) {

    	    return new GatewayOrderResponse(
    	            existing.getGateway(),
    	            existing.getGatewayOrderId(),
    	            existing.getWallet().getId(),
    	            existing.getAmount(),
    	            existing.getAmount()
    	                    .multiply(BigDecimal.valueOf(100))
    	                    .longValueExact(),
    	            existing.getCurrency(),
    	            existing.getStatus().toLowerCase(),
    	            existing.getIdempotencyKey()
    	    );
    	}

        Wallet wallet =
                walletService.getWallet(request.getWalletId());

        walletService.validateActive(wallet);

        String currency =
                request.getCurrency().toUpperCase();

        if (!wallet.getCurrency().equals(currency)) {
            throw new RuntimeException(
                    "Currency mismatch"
            );
        }

        BigDecimal amount =
                request.getAmount()
                        .setScale(2, RoundingMode.HALF_UP);

        /*
         * The idempotency key is used as the business
         * reference for the gateway order.
         *
         * We are not crediting the wallet here.
         * Money is credited only after successful
         * payment confirmation.
         */

        String receipt =
                "FIN-" +
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 20);

        RazorpayOrder order =
                razorpayClient.createOrder(
                        amount,
                        currency,
                        receipt
                );
        
        GatewayTransaction transaction =
                new GatewayTransaction();

        transaction.setGateway("RAZORPAY");
        transaction.setGatewayOrderId(order.orderId());
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        transaction.setWallet(wallet);
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        transaction.setStatus("CREATED");

        gatewayTransactionRepository.save(transaction);

        return new GatewayOrderResponse(
                "RAZORPAY",
                order.orderId(),
                wallet.getId(),
                amount,
                order.amountInPaise(),
                order.currency(),
                order.status(),
                request.getIdempotencyKey()
        );
    }
    
    @Transactional
    public GatewayTransaction verifyPayment(
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature) {

        /*
         * Lock the gateway transaction.
         *
         * This prevents two concurrent verification requests
         * from both crediting the wallet.
         */
        GatewayTransaction transaction =
                gatewayTransactionRepository
                        .findByGatewayOrderIdForUpdate(
                                razorpayOrderId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Gateway order not found"
                                ));

        /*
         * Idempotency.
         *
         * If the same payment is verified again after the
         * transaction has already completed, return the
         * existing transaction without crediting again.
         */
        if ("COMPLETED".equals(transaction.getStatus())) {
            return transaction;
        }

        /*
         * Verify Razorpay HMAC signature.
         *
         * Signature is generated from:
         *
         * order_id + "|" + payment_id
         */
        boolean valid =
                razorpayClient.verifyPaymentSignature(
                        transaction.getGatewayOrderId(),
                        razorpayPaymentId,
                        razorpaySignature
                );

        if (!valid) {
            throw new RuntimeException(
                    "Invalid Razorpay payment signature"
            );
        }

        /*
         * Lock the actual FinCore wallet.
         */
        Wallet wallet =
                walletService.getWalletForUpdate(
                        transaction.getWallet().getId()
                );

        walletService.validateActive(wallet);

        /*
         * Validate currency.
         */
        if (!wallet.getCurrency().equals(
                transaction.getCurrency())) {

            throw new RuntimeException(
                    "Wallet currency mismatch"
            );
        }

        /*
         * Credit wallet only after successful
         * Razorpay signature verification.
         */
        walletService.credit(
                wallet,
                transaction.getAmount()
        );

        /*
         * Create gateway funding ledger entry.
         */
        LedgerEntry ledgerEntry =
                new LedgerEntry();

        ledgerEntry.setWallet(wallet);

        ledgerEntry.setGatewayTransaction(transaction);

        ledgerEntry.setEntryType(
                LedgerEntryType.CREDIT
        );

        ledgerEntry.setAmount(
                transaction.getAmount()
        );

        ledgerEntry.setBalanceAfter(
                wallet.getBalance()
        );

        /*
         * Save wallet.
         */
        walletService.save(wallet);

        /*
         * Update gateway transaction.
         */
        transaction.setGatewayPaymentId(
                razorpayPaymentId
        );

        transaction.setRazorpaySignature(
                razorpaySignature
        );

        transaction.setStatus(
                "COMPLETED"
        );

        transaction.setCompletedAt(
                LocalDateTime.now()
        );

        GatewayTransaction savedTransaction =
                gatewayTransactionRepository.save(
                        transaction
                );

        /*
         * Save ledger entry.
         */
        ledgerEntryRepository.save(
                ledgerEntry
        );

        return savedTransaction;
    }
}