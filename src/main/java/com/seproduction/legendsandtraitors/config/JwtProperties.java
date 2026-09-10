package com.seproduction.legendsandtraitors.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration properties for security and JWT authentication.
 * Bound to prefix "security.jwt" from application YAML profiles.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    /**
     * Secret key used for signing and verifying HMAC-SHA256 JWT tokens.
     * In production, this must be injected via the JWT_SECRET environment variable.
     */
    private String secret;

    /**
     * Token validity lifetime in milliseconds (default: 86400000 ms = 24 hours).
     */
    private long expirationMs = 86_400_000L;
}
