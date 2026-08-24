package com.fincore.backend.gateway;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.security.MessageDigest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RazorpayClient {

    private static final String BASE_URL =
            "https://api.razorpay.com/v1";

    private final RestClient restClient;
    private final RazorpayConfig config;

    public RazorpayClient(RazorpayConfig config) {

        this.config = config;

        String credentials =
                config.getKeyId() + ":" + config.getKeySecret();

        String encodedCredentials =
                Base64.getEncoder()
                        .encodeToString(
                                credentials.getBytes(StandardCharsets.UTF_8)
                        );

        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Basic " + encodedCredentials
                )
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    @SuppressWarnings("unchecked")
    public RazorpayOrder createOrder(
            BigDecimal amount,
            String currency,
            String receipt) {

        long amountInPaise = amount
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();

        Map<String, Object> request = Map.of(
                "amount", amountInPaise,
                "currency", currency,
                "receipt", receipt
        );

        Map<String, Object> response =
                restClient.post()
                        .uri("/orders")
                        .body(request)
                        .retrieve()
                        .body(Map.class);

        if (response == null) {
            throw new RuntimeException(
                    "Empty response received from Razorpay"
            );
        }

        String orderId =
                (String) response.get("id");

        String status =
                (String) response.get("status");

        Number responseAmount =
                (Number) response.get("amount");

        return new RazorpayOrder(
                orderId,
                responseAmount.longValue(),
                currency,
                status
        );
    }
    
    public boolean verifyPaymentSignature(
            String orderId,
            String paymentId,
            String receivedSignature) {

        try {

            String payload =
                    orderId + "|" + paymentId;

            Mac mac = Mac.getInstance("HmacSHA256");

            SecretKeySpec secretKey =
                    new SecretKeySpec(
                            config.getKeySecret().getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"
                    );

            mac.init(secretKey);

            byte[] digest =
                    mac.doFinal(
                            payload.getBytes(StandardCharsets.UTF_8)
                    );

            String generatedSignature =
                    bytesToHex(digest);

            return MessageDigest.isEqual(
                    generatedSignature.getBytes(StandardCharsets.UTF_8),
                    receivedSignature.getBytes(StandardCharsets.UTF_8)
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to verify Razorpay payment signature",
                    e
            );
        }
    }

    private String bytesToHex(byte[] bytes) {

        StringBuilder result = new StringBuilder();

        for (byte b : bytes) {
            result.append(
                    String.format("%02x", b)
            );
        }

        return result.toString();
    }

    public record RazorpayOrder(
            String orderId,
            long amountInPaise,
            String currency,
            String status
    ) {
    }
}