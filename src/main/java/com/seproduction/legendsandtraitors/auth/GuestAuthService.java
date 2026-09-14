package com.seproduction.legendsandtraitors.auth;

import com.seproduction.legendsandtraitors.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Mints throwaway identities for players who join without an account.
 *
 * <p>Nothing is written to PostgreSQL, so the id is unique only by chance across the 900k
 * six-digit space — two live guests can collide until identities are claimed in Redis.
 */
@Service
@RequiredArgsConstructor
class GuestAuthService {

    private static final int ID_ORIGIN = 100_000;

    private static final int ID_BOUND = 1_000_000;

    private final SecureRandom secureRandom = new SecureRandom();

    private final JwtTokenProvider jwtTokenProvider;

    GuestAuthResponse createGuestSession() {
        String digits = String.valueOf(secureRandom.nextInt(ID_ORIGIN, ID_BOUND));
        String userId = "guest_" + digits;
        String displayName = "Guest" + digits;

        String token = jwtTokenProvider.issueToken(userId, displayName, true, false);
        return new GuestAuthResponse(token, new UserDto(userId, displayName, true, false));
    }
}
