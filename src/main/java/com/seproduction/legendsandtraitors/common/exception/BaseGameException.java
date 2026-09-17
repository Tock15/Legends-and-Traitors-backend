package com.seproduction.legendsandtraitors.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Abstract root exception for domain and business rule violations in the Legends and Traitors game backend.
 * <p>
 * Carries an associated {@link HttpStatus}, a distinct machine-readable error code string,
 * a human-readable title, and a detail message intended for RFC-7807 problem details serialization.
 * </p>
 *
 * Related Ticket: LT-33 / BE-15
 */
public abstract class BaseGameException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final String title;

    /**
     * Constructs a new base game exception with a default title matching the HTTP status reason phrase.
     *
     * @param status    the HTTP status to return in the RFC-7807 response
     * @param errorCode the machine-readable error code string (e.g., "ROOM_NOT_FOUND")
     * @param message   the specific detail message explaining the error
     */
    protected BaseGameException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, status.getReasonPhrase(), message);
    }

    /**
     * Constructs a new base game exception with a custom title.
     *
     * @param status    the HTTP status to return in the RFC-7807 response
     * @param errorCode the machine-readable error code string
     * @param title     the title describing the general category of the problem
     * @param message   the specific detail message explaining the error
     */
    protected BaseGameException(HttpStatus status, String errorCode, String title, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getTitle() {
        return title;
    }
}
