package com.seproduction.legendsandtraitors.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Authenticates the STOMP CONNECT frame and binds the {@code Principal} that {@code /user/**}
 * destinations route on.
 *
 * <p>Rejects a frame by sending the ERROR frame itself and dropping the original, never by throwing:
 * with {@code setPreserveReceiveOrder} on, Spring's ordered channel logs and swallows an interceptor
 * exception, so a throw would leave the client waiting instead of receiving ERROR and a close.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final MessageChannel clientOutboundChannel;

    // @Lazy breaks the cycle: the broker config that defines the channel needs this interceptor first.
    public StompAuthChannelInterceptor(JwtTokenProvider jwtTokenProvider,
                                       @Lazy @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.clientOutboundChannel = clientOutboundChannel;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // wrap() would copy the headers and drop setUser — the attached accessor is the mutable one.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        try {
            authenticate(accessor);
            return message;
        } catch (InvalidJwtException ex) {
            reject(accessor, ex.getMessage());
            return null;
        }
    }

    private void authenticate(StompHeaderAccessor accessor) {
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT == command || StompCommand.STOMP == command) {
            // Unlike the REST filter, a missing header is fatal: /user/** cannot route without a Principal.
            JwtPrincipal principal = jwtTokenProvider.parse(bearerToken(accessor));
            accessor.setUser(principal);
            if (accessor.getSessionAttributes() != null) {
                accessor.getSessionAttributes().put("userId", principal.id());
                accessor.getSessionAttributes().put("displayName", principal.displayName());
                accessor.getSessionAttributes().put("user", principal);
            }
            return;
        }

        if (StompCommand.SUBSCRIBE == command || StompCommand.SEND == command) {
            if (accessor.getUser() == null) {
                throw new InvalidJwtException("User is not authenticated");
            }
        }
    }

    /** Spring closes the session with {@code PROTOCOL_ERROR} once this ERROR frame is written. */
    private void reject(StompHeaderAccessor frame, String reason) {
        StompHeaderAccessor error = StompHeaderAccessor.create(StompCommand.ERROR);
        error.setMessage(reason);
        error.setSessionId(frame.getSessionId());
        if (frame.getReceipt() != null) {
            error.setReceiptId(frame.getReceipt());
        }
        clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], error.getMessageHeaders()));
    }

    private static String bearerToken(StompHeaderAccessor accessor) {
        List<String> values = accessor.getNativeHeader(HEADER);
        if (values != null && !values.isEmpty()) {
            String value = values.getFirst();
            if (value == null || !value.startsWith(PREFIX)) {
                throw new InvalidJwtException("CONNECT frame " + HEADER + " header is not a Bearer token");
            }

            String token = value.substring(PREFIX.length()).trim();
            if (token.isEmpty()) {
                throw new InvalidJwtException("CONNECT frame Bearer token is empty");
            }
            return token;
        }

        String passcode = accessor.getPasscode();
        if (passcode != null && !passcode.isBlank()) {
            if (passcode.startsWith(PREFIX)) {
                return passcode.substring(PREFIX.length()).trim();
            }
            return passcode.trim();
        }

        throw new InvalidJwtException("CONNECT frame is missing an " + HEADER + " header");
    }
}
