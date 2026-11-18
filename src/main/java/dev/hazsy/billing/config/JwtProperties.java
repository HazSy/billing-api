package dev.hazsy.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, String issuer, long ttlMinutes) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret debe tener al menos 32 bytes (HS256).");
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "billing-api";
        }
        if (ttlMinutes <= 0) {
            ttlMinutes = 60;
        }
    }
}
