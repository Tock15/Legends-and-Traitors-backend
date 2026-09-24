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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class StompAuthChannelInterceptorTest {

    private static final String SECRET = "ThreeChickenUnitTestJwtSigningKeyLongEnoughForHs256Please12345678";

    private static final long EXPIRATION_MS = 86_400_000L;

    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");

    private static final String USER_ID = "guest_948201";

    private static final String DISPLAY_NAME = "Guest948201";

    private static final String SESSION_ID = "session-1";

    private static final MessageChannel CHANNEL = (message, timeout) -> true;

    private final List<Message<?>> sentToClient = new ArrayList<>();

    private JwtTokenProvider jwtTokenProvider;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpirationMs(EXPIRATION_MS);
        jwtTokenProvider = new JwtTokenProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        MessageChannel clientOutboundChannel = (message, timeout) -> sentToClient.add(message);
        interceptor = new StompAuthChannelInterceptor(jwtTokenProvider, clientOutboundChannel);
    }

    private static Message<byte[]> frame(StompCommand command, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(SESSION_ID);
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

    private void assertRejected(Message<?> result, String reason) {
        assertThat(result).as("rejected frame is dropped").isNull();
        assertThat(sentToClient).singleElement().satisfies(error -> {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(error);
            assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
            assertThat(accessor.getSessionId()).isEqualTo(SESSION_ID);
            assertThat(accessor.getMessage()).contains(reason);
        });
    }

    @Test
    @DisplayName("Should bind the token identity as the STOMP user on CONNECT")
    void shouldBindUserOnConnect() {
        String token = jwtTokenProvider.issueToken(USER_ID, DISPLAY_NAME, true, false);

        Message<?> result = connectWith("Bearer " + token);

        assertThat(accessorOf(result).getUser())
                .isInstanceOf(JwtPrincipal.class)
                .extracting(Principal::getName).isEqualTo(USER_ID);
        assertThat(sentToClient).isEmpty();
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
        assertRejected(connectWith(null), "missing an Authorization header");
    }

    @Test
    @DisplayName("Should reject a CONNECT frame whose header is not a Bearer token")
    void shouldRejectNonBearerHeader() {
        assertRejected(connectWith("Basic dXNlcjpwYXNz"), "not a Bearer token");
    }

    @Test
    @DisplayName("Should reject a CONNECT frame carrying a token we did not sign")
    void shouldRejectForgedToken() {
        assertRejected(connectWith("Bearer not-a-jwt"), "Malformed JWT");
    }

    @Test
    @DisplayName("Should reject with a generic ERROR frame, not hang, when authentication fails unexpectedly")
    void shouldRejectOnUnexpectedFailure() {
        JwtTokenProvider failing = mock(JwtTokenProvider.class);
        given(failing.parse(anyString())).willThrow(new IllegalStateException("boom"));
        interceptor = new StompAuthChannelInterceptor(failing, (message, timeout) -> sentToClient.add(message));

        assertRejected(connectWith("Bearer any-token"), "Authentication failed");
    }

    @Test
    @DisplayName("Should echo the rejected frame's receipt id on the ERROR frame")
    void shouldEchoReceiptOnRejection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId(SESSION_ID);
        accessor.setReceipt("receipt-7");
        accessor.setLeaveMutable(true);

        interceptor.preSend(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), CHANNEL);

        assertThat(sentToClient).singleElement()
                .extracting(error -> StompHeaderAccessor.wrap(error).getReceiptId())
                .isEqualTo("receipt-7");
    }

    @Test
    @DisplayName("Should leave frames like DISCONNECT untouched")
    void shouldIgnoreDisconnectFrames() {
        Message<?> result = interceptor.preSend(frame(StompCommand.DISCONNECT, null), CHANNEL);

        assertThat(accessorOf(result).getUser()).isNull();
    }

    @Test
    @DisplayName("Should reject SUBSCRIBE without authenticated user")
    void shouldRejectSubscribeWithoutAuth() {
        assertRejected(interceptor.preSend(frame(StompCommand.SUBSCRIBE, null), CHANNEL), "User is not authenticated");
    }

    @Test
    @DisplayName("Should reject SEND without authenticated user")
    void shouldRejectSendWithoutAuth() {
        assertRejected(interceptor.preSend(frame(StompCommand.SEND, null), CHANNEL), "User is not authenticated");
    }
}
