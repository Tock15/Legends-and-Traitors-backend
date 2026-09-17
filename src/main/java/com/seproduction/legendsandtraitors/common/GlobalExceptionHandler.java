package com.seproduction.legendsandtraitors.common;

import com.seproduction.legendsandtraitors.security.InvalidJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(InvalidJwtException.class)
    ProblemDetail onInvalidJwt(InvalidJwtException e) {
        // The message names the check that failed — useful in logs, an oracle on the wire.
        log.debug("Rejected token: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail onInvalidRequest(InvalidRequestException e) {
        // Echoed unlike a token failure: the message states the rule the caller has to satisfy.
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
