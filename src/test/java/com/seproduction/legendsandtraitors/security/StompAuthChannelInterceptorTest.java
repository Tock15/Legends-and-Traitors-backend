package com.seproduction.legendsandtraitors.security;

import com.seproduction.legendsandtraitors.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompAuthChannelInterceptorTest {

    private static final String SECRET = "ThreeChickenUnitTestJwtSigningKeyLongEnoughForHs256Please12345678";

    private static final long EXPIRATION_MS = 86_400_000L;

    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");

    private static final String USER_ID = "guest_948201";

    private static final String DISPLAY_NAME = "Guest948201";

    private static final MessageChannel CHANNEL = (message, timeout) -> true;

    private JwtTokenProvider jwtTokenProvider;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpirationMs(EXPIRATION_MS);
        jwtTokenProvider = new JwtTokenProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        interceptor = new StompAuthChannelInterceptor(jwtTokenProvider);
    }

    private static Message<byte[]> frame(StompCommand command, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<?> connectWith(String authorization) {
        return interceptor.preSend(frame(StompCommand.CONNECT, authorization), CHANNEL);
    }

    private static StompHeaderAccessor accessorOf(Message<?> message) {
        return MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    }

    @Test
    @DisplayName("Should bind the token identity as the STOMP user on CONNECT")
    void shouldBindUserOnConnect() {
        String token = jwtTokenProvider.issueToken(USER_ID, DISPLAY_NAME, true, false);

        Message<?> result = connectWith("Bearer " + token);

        assertThat(accessorOf(result).getUser())
                .isInstanceOf(JwtPrincipal.class)
                .extracting(Principal::getName).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Should populate session attributes when present on CONNECT")
    void shouldPopulateSessionAttributesOnConnect() {
        String token = jwtTokenProvider.issueToken(USER_ID, DISPLAY_NAME, true, false);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        java.util.Map<String, Object> sessionAttrs = new java.util.HashMap<>();
        accessor.setSessionAttributes(sessionAttrs);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, CHANNEL);

        assertThat(sessionAttrs)
                .containsEntry("userId", USER_ID)
                .containsEntry("displayName", DISPLAY_NAME);
        assertThat(sessionAttrs.get("user"))
                .isInstanceOf(JwtPrincipal.class);
    }

    @Test
    @DisplayName("Should authenticate via passcode header fallback")
    void shouldAuthenticateViaPasscode() {
        String token = jwtTokenProvider.issueToken(USER_ID, DISPLAY_NAME, true, false);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setPasscode(token);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, CHANNEL);

        assertThat(accessorOf(result).getUser())
                .isInstanceOf(JwtPrincipal.class)
                .extracting(Principal::getName).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Should reject a CONNECT frame that carries no Authorization header")
    void shouldRejectConnectWithoutHeader() {
        assertThatThrownBy(() -> connectWith(null))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("missing an Authorization header");
    }

    @Test
    @DisplayName("Should reject a CONNECT frame whose header is not a Bearer token")
    void shouldRejectNonBearerHeader() {
        assertThatThrownBy(() -> connectWith("Basic dXNlcjpwYXNz"))
                .isInstanceOf(InvalidJwtException.class)
                .hasMessageContaining("not a Bearer token");
    }

    @Test
    @DisplayName("Should reject a CONNECT frame carrying a token we did not sign")
    void shouldRejectForgedToken() {
        assertThatThrownBy(() -> connectWith("Bearer not-a-jwt"))
                .isInstanceOf(InvalidJwtException.class);
    }

    @Test
    @DisplayName("Should leave frames other than CONNECT untouched")
    void shouldIgnoreNonConnectFrames() {
        Message<?> result = interceptor.preSend(frame(StompCommand.SEND, null), CHANNEL);

        assertThat(accessorOf(result).getUser()).isNull();
    }
}
