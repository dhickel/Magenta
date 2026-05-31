package io.mindspice.magenta2.api.web;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.mindspice.magenta2.ai.chat.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern CONVERSATION_ID_PATTERN = Pattern.compile(
        "/chat/(stream|send|plan)/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
    );

    private final Optional<AuditService> auditService;

    @Autowired
    public GlobalExceptionHandler(Optional<AuditService> auditService) {
        this.auditService = Objects.requireNonNull(auditService, "auditService");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException exception) {
        List<Map<String, String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
            .map(this::fieldErrorEntry)
            .toList();
        log.warn("Validation error: {}", fieldErrors, exception);
        recordIfConversation("http_validation", exception.getMessage(), stackTrace(exception));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "validation failed", "fieldErrors", fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException exception) {
        List<Map<String, String>> violations = exception.getConstraintViolations().stream()
            .map(violation -> Map.of(
                "field", violation.getPropertyPath().toString(),
                "message", violation.getMessage()
            ))
            .toList();
        log.warn("Constraint violation: {}", violations, exception);
        recordIfConversation("http_validation", exception.getMessage(), stackTrace(exception));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "validation failed", "fieldErrors", violations));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(BindException exception) {
        List<Map<String, String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
            .map(this::fieldErrorEntry)
            .toList();
        log.warn("Bind error: {}", fieldErrors, exception);
        recordIfConversation("http_validation", exception.getMessage(), stackTrace(exception));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "validation failed", "fieldErrors", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        log.warn("Malformed request body: {}", exception.getMostSpecificCause().getMessage(), exception);
        recordIfConversation("http_validation",
            exception.getMostSpecificCause().getMessage(), stackTrace(exception));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "malformed request body: " + exception.getMostSpecificCause().getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        log.warn("Illegal argument: {}", exception.getMessage(), exception);
        recordIfConversation("http_validation", exception.getMessage(), stackTrace(exception));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException exception) {
        log.warn("Illegal state: {}", exception.getMessage(), exception);
        recordIfConversation("http_illegal_state", exception.getMessage(), stackTrace(exception));
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException exception) {
        log.warn("Response status error: {} — {}", exception.getStatusCode(), exception.getReason(), exception);
        recordIfConversation("http_response_status",
            exception.getReason() != null ? exception.getReason() : exception.getMessage(), stackTrace(exception));
        return ResponseEntity.status(exception.getStatusCode())
            .body(Map.of("error", exception.getReason()));
    }

    private void recordIfConversation(String errorType, String errorMessage, String stackTrace) {
        if (auditService.isEmpty()) return;
        String conversationId = conversationIdFromRequest();
        if (conversationId != null) {
            auditService.get().recordError(conversationId, errorType, errorMessage, stackTrace, null);
        }
    }

    private String conversationIdFromRequest() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes servletAttrs) {
                String path = servletAttrs.getRequest().getRequestURI();
                Matcher matcher = CONVERSATION_ID_PATTERN.matcher(path);
                if (matcher.find()) {
                    String candidate = matcher.group(2);
                    UUID.fromString(candidate); // validates format
                    return candidate;
                }
            }
        } catch (Exception ignored) {
            log.debug("Failed to extract conversation ID from request", ignored);
        }
        return null;
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private Map<String, String> fieldErrorEntry(FieldError fieldError) {
        return Map.of(
            "field", fieldError.getField(),
            "message", fieldError.getDefaultMessage() == null ? "invalid value" : fieldError.getDefaultMessage()
        );
    }
}
