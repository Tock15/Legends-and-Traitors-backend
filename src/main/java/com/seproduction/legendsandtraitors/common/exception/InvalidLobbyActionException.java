package com.seproduction.legendsandtraitors.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an invalid or illegal action is performed within a game lobby
 * (such as attempting to start before reaching minimum player counts or when not all players are ready).
 * Responds with HTTP 400 BAD REQUEST and error code "INVALID_ACTION".
 *
 * Related Ticket: LT-33 / BE-15
 */
public class InvalidLobbyActionException extends BaseGameException {

    public static final String ERROR_CODE = "INVALID_ACTION";

    public InvalidLobbyActionException(String message) {
        super(HttpStatus.BAD_REQUEST, ERROR_CODE, "Invalid Lobby Action", message);
    }
}
