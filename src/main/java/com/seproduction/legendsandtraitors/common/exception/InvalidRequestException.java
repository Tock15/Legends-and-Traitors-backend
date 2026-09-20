package com.seproduction.legendsandtraitors.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a well-formed request carries a value the domain rejects
 * (such as a room capacity outside the configured min/max range).
 * Responds with HTTP 400 BAD REQUEST and error code "INVALID_REQUEST".
 *
 * Related Ticket: LT-27
 */
public class InvalidRequestException extends BaseGameException {

    public static final String ERROR_CODE = "INVALID_REQUEST";

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, ERROR_CODE, "Invalid Request", message);
    }
}
