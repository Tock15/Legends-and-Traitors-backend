package com.seproduction.legendsandtraitors.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation (such as joining, setting roles, or modifying settings)
 * is attempted on a room whose match is already in progress.
 * Responds with HTTP 409 CONFLICT and error code "GAME_ALREADY_STARTED".
 *
 * Related Ticket: LT-33 / BE-15
 */
public class GameAlreadyStartedException extends BaseGameException {

    public static final String ERROR_CODE = "GAME_ALREADY_STARTED";

    public GameAlreadyStartedException(String message) {
        super(HttpStatus.CONFLICT, ERROR_CODE, "Game Already Started", message);
    }

    public static GameAlreadyStartedException forRoom(String roomCode) {
        return new GameAlreadyStartedException("Game in room '" + roomCode + "' has already started.");
    }
}
