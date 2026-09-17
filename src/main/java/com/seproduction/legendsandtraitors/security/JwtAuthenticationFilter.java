package com.seproduction.legendsandtraitors.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.security.Principal;

/**
 * Binds the identity in a {@code Bearer} token to the request so controllers can take a
 * {@link Principal} argument.
 *
 * <p>An absent or non-Bearer header passes through unauthenticated — {@code /api/auth/guest} has no
 * token to send yet. Only a header that is present and bad is rejected.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    // Filters run outside the DispatcherServlet, so a throw here would never reach the advice.
    private final HandlerExceptionResolver handlerExceptionResolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        JwtPrincipal principal;
        try {
            principal = parseBearer(header);
        } catch (InvalidJwtException e) {
            if (handlerExceptionResolver.resolveException(request, response, null, e) == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
            return;
        }
        filterChain.doFilter(withPrincipal(request, principal), response);
    }

    private JwtPrincipal parseBearer(String header) {
        String token = header.substring(PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new InvalidJwtException("Bearer token is empty");
        }
        return jwtTokenProvider.parse(token);
    }

    private static HttpServletRequest withPrincipal(HttpServletRequest request, Principal principal) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public Principal getUserPrincipal() {
                return principal;
            }
        };
    }
}
