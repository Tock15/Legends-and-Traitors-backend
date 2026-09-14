package com.seproduction.legendsandtraitors.security;

/** Thrown when a presented token is malformed, unsigned by us, or past its expiry. */
public class InvalidJwtException extends RuntimeException {

    public InvalidJwtException(String message) {
        super(message);
    }

    public InvalidJwtException(String message, Throwable cause) {
        super(message, cause);
    }
}
