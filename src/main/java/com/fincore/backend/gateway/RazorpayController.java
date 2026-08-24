package com.fincore.backend.gateway;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fincore.backend.gateway.dto.GatewayOrderRequest;
import com.fincore.backend.gateway.dto.GatewayOrderResponse;
import com.fincore.backend.gateway.dto.GatewayPaymentVerifyRequest;

@RestController
@RequestMapping("/gateway/razorpay")
public class RazorpayController {

    private final RazorpayService razorpayService;

    public RazorpayController(
            RazorpayService razorpayService) {

        this.razorpayService = razorpayService;
    }

    @PostMapping("/orders")
    public ResponseEntity<GatewayOrderResponse> createOrder(
            @Valid @RequestBody GatewayOrderRequest request) {

        GatewayOrderResponse response =
                razorpayService.createOrder(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    
    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @Valid @RequestBody GatewayPaymentVerifyRequest request) {

        GatewayTransaction transaction =
                razorpayService.verifyPayment(
                        request.getRazorpayOrderId(),
                        request.getRazorpayPaymentId(),
                        request.getRazorpaySignature()
                );

        return ResponseEntity.ok(
                java.util.Map.of(
                        "status", transaction.getStatus(),
                        "gateway", transaction.getGateway(),
                        "gatewayOrderId",
                        transaction.getGatewayOrderId(),
                        "gatewayPaymentId",
                        transaction.getGatewayPaymentId(),
                        "walletId",
                        transaction.getWallet().getId(),
                        "amount",
                        transaction.getAmount()
                )
        );
    }
}