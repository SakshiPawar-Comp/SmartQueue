package com.smartqueue.common.errors;

import com.smartqueue.auth.application.AuthException;
import com.smartqueue.common.api.ApiResponse;
import com.smartqueue.common.tenant.TenantViolationException;
import com.smartqueue.queue.application.QueueAccessDeniedException;
import com.smartqueue.queue.application.QueueInactiveException;
import com.smartqueue.queue.application.QueueNotFoundException;
import com.smartqueue.queue.application.TokenNotFoundException;
import com.smartqueue.queue.application.InvalidTokenTransitionException;
import com.smartqueue.queue.application.DuplicateActiveTokenException;
import com.smartqueue.queue.application.EmptyQueueException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.security.access.AccessDeniedException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthException ex) {
        String message = ex.getMessage();
        HttpStatus status;
        String code;

        if (message != null && message.contains("already registered")) {
            status = HttpStatus.CONFLICT;
            code = "DUPLICATE_EMAIL";
        } else if (message != null && message.contains("Invalid email or password")) {
            status = HttpStatus.UNAUTHORIZED;
            code = "INVALID_CREDENTIALS";
        } else if (message != null && message.contains("Refresh token reuse detected")) {
            status = HttpStatus.UNAUTHORIZED;
            code = "REFRESH_TOKEN_REUSE";
        } else if (message != null && (message.contains("expired") || message.contains("Invalid refresh token") || message.contains("inactive"))) {
            status = HttpStatus.UNAUTHORIZED;
            code = "REFRESH_TOKEN_INVALID";
        } else if (message != null && message.contains("not found")) {
            status = HttpStatus.NOT_FOUND;
            code = "NOT_FOUND";
        } else {
            status = HttpStatus.UNAUTHORIZED;
            code = "AUTH_ERROR";
        }

        return buildResponse(status, code, message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        String code = "VALIDATION_ERROR";
        String message = "Request validation failed";
        log.debug("Validation error: {}", fieldErrors);

        return buildResponseWithDetails(HttpStatus.BAD_REQUEST, code, message, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        v -> v.getMessage() != null ? v.getMessage() : "Invalid value",
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        String code = "CONSTRAINT_VIOLATION";
        String message = "Request constraint violated";
        log.debug("Constraint violation: {}", violations);

        return buildResponseWithDetails(HttpStatus.BAD_REQUEST, code, message, violations);
    }

    @ExceptionHandler(TenantViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantViolation(TenantViolationException ex) {
        return buildResponse(
                HttpStatus.FORBIDDEN,
                "TENANT_VIOLATION",
                ex.getMessage()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "You do not have permission to perform this action"
        );
    }

    @ExceptionHandler(QueueNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleQueueNotFound(QueueNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "QUEUE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(QueueAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleQueueAccessDenied(QueueAccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "QUEUE_ACCESS_DENIED", ex.getMessage());
    }

    @ExceptionHandler(QueueInactiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleQueueInactive(QueueInactiveException ex) {
        return buildResponse(HttpStatus.CONFLICT, "QUEUE_INACTIVE", ex.getMessage());
    }

    @ExceptionHandler(TokenNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenNotFound(TokenNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "TOKEN_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTokenTransition(InvalidTokenTransitionException ex) {
        return buildResponse(HttpStatus.CONFLICT, "INVALID_TOKEN_TRANSITION", ex.getMessage());
    }

    @ExceptionHandler(DuplicateActiveTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateActiveToken(DuplicateActiveTokenException ex) {
        return buildResponse(HttpStatus.CONFLICT, "DUPLICATE_ACTIVE_TOKEN", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(org.springframework.security.authentication.BadCredentialsException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_CREDENTIALS", ex.getMessage());
    }

    @ExceptionHandler(EmptyQueueException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmptyQueue(EmptyQueueException ex) {
        return buildResponse(HttpStatus.CONFLICT, "EMPTY_QUEUE", ex.getMessage());
    }

    @ExceptionHandler(com.smartqueue.queue.application.QueueCapacityExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleQueueCapacityExceeded(com.smartqueue.queue.application.QueueCapacityExceededException ex) {
        return buildResponse(HttpStatus.CONFLICT, "QUEUE_CAPACITY_EXCEEDED", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.warn("Malformed HTTP request body: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed or unreadable request body");
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(org.springframework.web.bind.MissingServletRequestParameterException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred"
        );
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(HttpStatus status, String code, String message) {
        String correlationId = MDC.get("correlationId");
        ApiResponse<Void> body = new ApiResponse<>(
                null,
                new ApiResponse.ApiError(code, message, null),
                correlationId
        );
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<ApiResponse<Void>> buildResponseWithDetails(
            HttpStatus status, String code, String message, Object details
    ) {
        String correlationId = MDC.get("correlationId");
        ApiResponse<Void> body = new ApiResponse<>(
                null,
                new ApiResponse.ApiError(code, message, details),
                correlationId
        );
        return ResponseEntity.status(status).body(body);
    }
}
