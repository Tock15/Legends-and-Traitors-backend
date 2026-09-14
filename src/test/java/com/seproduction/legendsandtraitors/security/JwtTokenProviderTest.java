package com.seproduction.legendsandtraitors.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.seproduction.legendsandtraitors.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "ThreeChickenUnitTestJwtSigningKeyLongEnoughForHs256Please12345678";

    private static final String OTHER_SECRET = "AnotherThreeChickenJwtSigningKeyLongEnoughForHs256Please87654321";

    private static final long EXPIRATION_MS = 86_400_000L;

    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");

    private static final String USER_ID = "guest_948201";

    private static final String DISPLAY_NAME = "Guest948201";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = providerWith(SECRET, NOW);
    }

    private static JwtTokenProvider providerWith(String secret, Instant now) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpirationMs(EXPIRATION_MS);
        return new JwtTokenProvider(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private String guestToken() {
        return jwtTokenProvider.issueToken(USER_ID, DISPLAY_NAME, true, false);
    }

    @Test
    @DisplayName("Should issue a compact three-part token signed with HS256")
    void shouldIssueCompactHs256Token() throws Exception {
        String token = guestToken();

        assertThat(token.split("[.]")).hasSize(3);
        assertThat(SignedJWT.parse(token).getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
    }

    @Test
    @DisplayName("Should round-trip the guest identity through the token claims")
    void shouldRoundTripGuestClaims() {
        JwtPrincipal principal = jwtTokenProvider.parse(guestToken());

        assertThat(principal.id()).isEqualTo(USER_ID);
        assertThat(principal.displayName()).isEqualTo(DISPLAY_NAME);
        assertThat(principal.guest()).isTrue();
        assertThat(principal.premium()).isFalse();
    }

    @Test
    @DisplayName("Should expire exactly security.jwt.expiration-ms after issuing")
    void shouldSetExpiryFromConfiguredLifetime() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(guestToken()).getJWTClaimsSet();

        assertThat(claims.getIssueTime()).isEqualTo(Date.from(NOW));
        assertThat(claims.getExpirationTime()).isEqualTo(Date.from(NOW.plusMillis(EXPIRATION_MS)));
    }

    @Test
    @DisplayName("Should reject a token signed with a different secret")
    void shouldRejectForeignSignature() {
        String foreign = providerWith(OTHER_SECRET, NOW).issueToken(USER_ID, DISPLAY_NAME, true, false);

        assertThatThrownBy(() -> jwtTokenProvider.parse(foreign))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("Should reject a token whose payload was swapped after signing")
    void shouldRejectTamperedPayload() {
        String[] parts = guestToken().split("[.]");
        String forgedPayload = Base64URL.encode("{\"sub\":\"guest_000001\"}").toString();

        assertThatThrownBy(() -> jwtTokenProvider.parse(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    @DisplayName("Should reject a token re-signed as HS512 with the same secret")
    void shouldRejectAlgorithmSubstitution() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(USER_ID)
                .expirationTime(Date.from(NOW.plusMillis(EXPIRATION_MS)))
                .build();
        SignedJWT hs512 = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims);
        hs512.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> jwtTokenProvider.parse(hs512.serialize()))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("algorithm");
    }

    @Test
    @DisplayName("Should reject a token once its expiry has passed")
    void shouldRejectExpiredToken() {
        String token = guestToken();
        JwtTokenProvider aDayLater = providerWith(SECRET, NOW.plusMillis(EXPIRATION_MS));

        assertThatThrownBy(() -> aDayLater.parse(token))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Should still accept a token one millisecond before expiry")
    void shouldAcceptTokenJustBeforeExpiry() {
        String token = guestToken();
        JwtTokenProvider justBefore = providerWith(SECRET, NOW.plusMillis(EXPIRATION_MS - 1));

        assertThat(justBefore.parse(token).id()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Should reject a string that is not a JWT at all")
    void shouldRejectMalformedToken() {
        assertThatThrownBy(() -> jwtTokenProvider.parse("not-a-jwt"))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("Malformed");
    }

    @Test
    @DisplayName("Should fail fast when the configured secret is too short for HS256")
    void shouldRejectShortSecret() {
        assertThatThrownBy(() -> providerWith("tooShortForHs256", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HS256");
    }

    @Test
    @DisplayName("Should fail fast when no secret is configured")
    void shouldRejectMissingSecret() {
        assertThatThrownBy(() -> providerWith("   ", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("security.jwt.secret");
    }
}
