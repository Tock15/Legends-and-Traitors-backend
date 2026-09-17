package com.seproduction.legendsandtraitors.common.exception;

/**
 * Structured representation of a parameter or field validation failure,
 * serialized into the {@code invalidParams} property of an RFC-7807 problem details response.
 *
 * @param field         the name of the rejected field or parameter
 * @param rejectedValue the invalid value that was supplied
 * @param message       the human-readable description of why the value was rejected
 *
 * Related Ticket: LT-33 / BE-15
 */
public record InvalidParam(
        String field,
        Object rejectedValue,
        String message
) {}
