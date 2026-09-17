package com.seproduction.legendsandtraitors.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a player attempts an action in a room from which they have been banned.
 * Responds with HTTP 403 FORBIDDEN and error code "PLAYER_BANNED".
 *
 * Related Ticket: LT-33 / BE-15
 */
public class PlayerBannedException extends BaseGameException {

    public static final String ERROR_CODE = "PLAYER_BANNED";

    public PlayerBannedException(String message) {
        super(HttpStatus.FORBIDDEN, ERROR_CODE, "Player Banned", message);
    }

    public static PlayerBannedException forPlayer(String playerId, String roomCode) {
        return new PlayerBannedException("Player '" + playerId + "' is banned from room '" + roomCode + "'.");
    }
}
