package com.microservices.notifications_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        String reason = e.getReason() != null ? e.getReason() : status.getReasonPhrase();
        return errorResponse(status, status.getReasonPhrase(), reason, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e, HttpServletRequest req) {
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred", req);
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String error, String message, HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", error);
        body.put("path", req.getRequestURI());
        body.put("timestamp", Instant.now().toString());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
