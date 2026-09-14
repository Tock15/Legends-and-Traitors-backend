package com.seproduction.legendsandtraitors.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.seproduction.legendsandtraitors.config.JwtProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * Signs and verifies the HMAC-SHA256 tokens that carry player identity across REST and STOMP.
 *
 * <p>The token is the only place a guest identity exists, so a forged or expired one must never
 * parse: callers get a {@link JwtPrincipal} or an {@link InvalidJwtException}, never a partial read.
 */
@Component
public class JwtTokenProvider {

    static final String CLAIM_DISPLAY_NAME = "displayName";
    static final String CLAIM_IS_GUEST = "isGuest";
    static final String CLAIM_IS_PREMIUM = "isPremium";

    private static final JWSAlgorithm ALGORITHM = JWSAlgorithm.HS256;

    private final JWSSigner signer;
    private final JWSVerifier verifier;
    private final long expirationMs;
    private final Clock clock;

    @Autowired
    public JwtTokenProvider(JwtProperties jwtProperties) {
        this(jwtProperties, Clock.systemUTC());
    }

    JwtTokenProvider(JwtProperties jwtProperties, Clock clock) {
        String secret = jwtProperties.getSecret();
        Assert.hasText(secret, "security.jwt.secret must be configured");
        Assert.isTrue(jwtProperties.getExpirationMs() > 0, "security.jwt.expiration-ms must be greater than 0");

        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        try {
            // Rejects anything under 256 bits, so a weak prod JWT_SECRET fails at startup.
            this.signer = new MACSigner(key);
            this.verifier = new MACVerifier(key);
        } catch (JOSEException e) {
            throw new IllegalStateException("security.jwt.secret is not a usable HS256 key", e);
        }
        this.expirationMs = jwtProperties.getExpirationMs();
        this.clock = clock;
    }

    /**
     * @return a signed token carrying the identity, valid for {@code security.jwt.expiration-ms}
     */
    public String issueToken(String userId, String displayName, boolean guest, boolean premium) {
        Assert.hasText(userId, "userId must not be blank");
        Assert.hasText(displayName, "displayName must not be blank");

        Instant issuedAt = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .claim(CLAIM_DISPLAY_NAME, displayName)
                .claim(CLAIM_IS_GUEST, guest)
                .claim(CLAIM_IS_PREMIUM, premium)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusMillis(expirationMs)))
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(ALGORITHM), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Unable to sign JWT", e);
        }
        return jwt.serialize();
    }

    /**
     * Verifies signature and expiry before reading any claim.
     *
     * @throws InvalidJwtException if the token is malformed, unsigned by us, or expired
     */
    public JwtPrincipal parse(String token) {
        Assert.hasText(token, "token must not be blank");

        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(token);
        } catch (ParseException e) {
            throw new InvalidJwtException("Malformed JWT", e);
        }

        // Pinning the header algorithm stops a token re-signed as HS384/HS512 with the same secret.
        if (!ALGORITHM.equals(jwt.getHeader().getAlgorithm())) {
            throw new InvalidJwtException("Unexpected JWT algorithm: " + jwt.getHeader().getAlgorithm());
        }
        try {
            if (!jwt.verify(verifier)) {
                throw new InvalidJwtException("JWT signature does not match");
            }
        } catch (JOSEException e) {
            throw new InvalidJwtException("JWT signature could not be verified", e);
        }

        JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new InvalidJwtException("Unreadable JWT claims", e);
        }

        Date expiresAt = claims.getExpirationTime();
        if (expiresAt == null || !clock.instant().isBefore(expiresAt.toInstant())) {
            throw new InvalidJwtException("JWT has expired");
        }

        String userId = claims.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new InvalidJwtException("JWT is missing a subject");
        }
        return new JwtPrincipal(
                userId,
                stringClaim(claims, CLAIM_DISPLAY_NAME),
                booleanClaim(claims, CLAIM_IS_GUEST),
                booleanClaim(claims, CLAIM_IS_PREMIUM));
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException e) {
            throw new InvalidJwtException("JWT claim '" + name + "' is not a string", e);
        }
    }

    private static boolean booleanClaim(JWTClaimsSet claims, String name) {
        try {
            return Boolean.TRUE.equals(claims.getBooleanClaim(name));
        } catch (ParseException e) {
            throw new InvalidJwtException("JWT claim '" + name + "' is not a boolean", e);
        }
    }
}
