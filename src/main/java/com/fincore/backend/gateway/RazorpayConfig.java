package com.fincore.backend.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "razorpay")
public class RazorpayConfig {

    private String keyId;
    private String keySecret;

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public void setKeySecret(String keySecret) {
        this.keySecret = keySecret;
    }
}