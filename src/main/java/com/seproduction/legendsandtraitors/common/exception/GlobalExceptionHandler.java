package com.seproduction.legendsandtraitors.common.exception;

import com.seproduction.legendsandtraitors.security.InvalidJwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Centralized, application-wide exception handling controller advice.
 * <p>
 * Intercepts domain game exceptions, DTO argument validation failures, and uncaught exceptions,
 * translating them into RFC-7807 {@link ProblemDetail} structures with machine-readable error codes
 * and standardized metadata.
 * </p>
 *
 * Related Ticket: LT-33 / BE-15
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String CODE_PROPERTY = "code";
    private static final String TIMESTAMP_PROPERTY = "timestamp";
    private static final String INVALID_PARAMS_PROPERTY = "invalidParams";
    private static final String VALIDATION_FAILED_CODE = "VALIDATION_FAILED";
    private static final String INVALID_TOKEN_CODE = "INVALID_TOKEN";
    private static final String INTERNAL_SERVER_ERROR_CODE = "INTERNAL_SERVER_ERROR";

    /**
     * Handles all business and domain exceptions descending from {@link BaseGameException}.
     *
     * @param ex      the caught domain exception
     * @param request the current HTTP servlet request
     * @return a structured RFC-7807 problem detail response entity
     */
    @ExceptionHandler(BaseGameException.class)
    public ResponseEntity<ProblemDetail> handleBaseGameException(BaseGameException ex, HttpServletRequest request) {
        log.warn("Domain game exception caught [{}]: {}", ex.getErrorCode(), ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problemDetail.setTitle(ex.getTitle());
        problemDetail.setProperty(CODE_PROPERTY, ex.getErrorCode());
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        return ResponseEntity.status(ex.getStatus()).body(problemDetail);
    }

    /**
     * Handles authentication failures caused by invalid, expired, or malformed JWT tokens.
     * Maps to HTTP 401 UNAUTHORIZED with sanitized error detail.
     *
     * @param ex      the caught invalid JWT exception
     * @param request the current HTTP servlet request
     * @return a structured RFC-7807 problem detail response entity
     */
    @ExceptionHandler(InvalidJwtException.class)
    public ResponseEntity<ProblemDetail> handleInvalidJwt(InvalidJwtException ex, HttpServletRequest request) {
        log.debug("Rejected token: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Invalid or expired token"
        );
        problemDetail.setTitle("Unauthorized");
        problemDetail.setProperty(CODE_PROPERTY, INVALID_TOKEN_CODE);
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
    }

    /**
     * Intercepts and formats bean validation failures on controller arguments annotated with {@code @Valid}.
     * Maps field validation failures into a structured {@code invalidParams} array.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<InvalidParam> invalidParams = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new InvalidParam(
                        fe.getField(),
                        fe.getRejectedValue(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid parameter value."
                ))
                .toList();

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid request parameters."
        );
        problemDetail.setTitle("Bad Request");
        problemDetail.setProperty(CODE_PROPERTY, VALIDATION_FAILED_CODE);
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        problemDetail.setProperty(INVALID_PARAMS_PROPERTY, invalidParams);

        if (request instanceof ServletWebRequest servletWebRequest) {
            problemDetail.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(problemDetail);
    }

    /**
     * Fallback exception handler for all unexpected or uncaught runtime exceptions.
     * Logs the full stack trace internally for engineering diagnostics, while shielding clients
     * from internal architecture details and returning a sanitized RFC-7807 response.
     *
     * @param ex      the unexpected exception
     * @param request the current HTTP servlet request
     * @return a sanitized 500 INTERNAL_SERVER_ERROR problem detail response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnhandledException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled internal server error occurred while processing URI: {}", request.getRequestURI(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal server error occurred."
        );
        problemDetail.setTitle(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        problemDetail.setProperty(CODE_PROPERTY, INTERNAL_SERVER_ERROR_CODE);
        problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        if (body instanceof ProblemDetail problemDetail) {
            if (problemDetail.getProperties() == null || !problemDetail.getProperties().containsKey(TIMESTAMP_PROPERTY)) {
                problemDetail.setProperty(TIMESTAMP_PROPERTY, Instant.now());
            }
            if (problemDetail.getProperties() == null || !problemDetail.getProperties().containsKey(CODE_PROPERTY)) {
                problemDetail.setProperty(CODE_PROPERTY, statusCode.toString());
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}
