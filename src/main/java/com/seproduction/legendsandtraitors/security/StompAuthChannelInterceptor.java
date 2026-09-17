package com.seproduction.legendsandtraitors.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Authenticates the STOMP CONNECT frame and binds the {@code Principal} that {@code /user/**}
 * destinations route on.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // wrap() would copy the headers and drop setUser — the attached accessor is the mutable one.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || StompCommand.CONNECT != accessor.getCommand()) {
            return message;
        }

        // Unlike the REST filter, a missing header is fatal: /user/** cannot route without a Principal.
        accessor.setUser(jwtTokenProvider.parse(bearerToken(accessor)));
        return message;
    }

    private static String bearerToken(StompHeaderAccessor accessor) {
        List<String> values = accessor.getNativeHeader(HEADER);
        if (values == null || values.isEmpty()) {
            throw new InvalidJwtException("CONNECT frame is missing an " + HEADER + " header");
        }

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
}
