package com.seproduction.legendsandtraitors.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a non-host player attempts an action reserved strictly for the room host
 * (such as changing settings, kicking players, or starting the game).
 * Responds with HTTP 403 FORBIDDEN and error code "NOT_HOST".
 *
 * Related Ticket: LT-33 / BE-15
 */
public class NotHostException extends BaseGameException {

    public static final String ERROR_CODE = "NOT_HOST";

    public NotHostException(String message) {
        super(HttpStatus.FORBIDDEN, ERROR_CODE, "Forbidden - Not Host", message);
    }

    public static NotHostException forAction(String action) {
        return new NotHostException("Only the room host is permitted to " + action + ".");
    }
}
