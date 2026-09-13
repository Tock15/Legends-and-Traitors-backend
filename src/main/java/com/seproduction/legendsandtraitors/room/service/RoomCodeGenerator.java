package com.seproduction.legendsandtraitors.room.service;

import com.seproduction.legendsandtraitors.config.GameRoomProperties;
import com.seproduction.legendsandtraitors.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.security.SecureRandom;

/**
 * Mints the short codes players type or share to reach a lobby.
 *
 * <p>Uniqueness is checked, not reserved: a returned code was free when checked, so racing
 * creations can still collide until the room is saved under a key claimed atomically.
 */
@Service
@RequiredArgsConstructor
class RoomCodeGenerator {

    /** A-Z without I and O, 2-9 without 0 and 1 — unambiguous on a phone, and 32 is bias-free. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();

    private final RoomRepository roomRepository;
    private final GameRoomProperties gameRoomProperties;

    /**
     * @return an unused code of {@code game.room.code-length} characters
     * @throws IllegalStateException if all {@value #MAX_ATTEMPTS} candidates were already taken
     */
    public String generateUniqueRoomCode() {
        int codeLength = gameRoomProperties.getCodeLength();
        Assert.isTrue(codeLength > 0, "game.room.code-length must be greater than 0");

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomCode(codeLength);
            if (!roomRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate unique room code");
    }

    private String randomCode(int codeLength) {
        char[] code = new char[codeLength];
        for (int i = 0; i < codeLength; i++) {
            code[i] = ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }
}
