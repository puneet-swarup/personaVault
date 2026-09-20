/*
 * Copyright 2025 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.config;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.personavault.service.IngestionService.IngestionException;

/**
 * Global exception handler that maps domain exceptions to appropriate
 * HTTP responses with consistent JSON error bodies.
 *
 * <p>Follows the principle of <b>fail fast, fail loud</b>: unexpected exceptions
 * are logged at ERROR level and return 500; expected domain exceptions return 400.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles ingestion pipeline failures (file too large, parse error, etc.).
     */
    @ExceptionHandler(IngestionException.class)
    public ResponseEntity<Map<String, Object>> handleIngestion(IngestionException ex) {
        log.warn("Ingestion failed: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Handles missing static resources (favicon, 404 pages) without
     * logging at ERROR level.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
        log.debug("Resource not found: {}", ex.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    /**
     * Catch-all for unhandled exceptions. Logged at ERROR for investigation.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of(
                        "status", status.value(),
                        "error", status.getReasonPhrase(),
                        "message", message,
                        "timestamp", Instant.now().toString()));
    }
}
