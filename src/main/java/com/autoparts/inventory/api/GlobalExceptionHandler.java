package com.autoparts.inventory.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleApp(AppException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiEnvelope.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError field = ex.getBindingResult().getFieldError();
        String name = field == null ? null : field.getField();
        String message = field == null ? "Invalid request" : field.getDefaultMessage();
        return ResponseEntity.badRequest()
                .body(ApiEnvelope.error("VALIDATION_ERROR", message, name));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleBadBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiEnvelope.error("VALIDATION_ERROR", "Invalid request body"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleNoRoute(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiEnvelope.error("NOT_FOUND", "Route not found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiEnvelope.error("METHOD_NOT_ALLOWED", "HTTP method not supported for this path"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<Void>> handleOther(Exception ex) {
        log.error("unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiEnvelope.error("SERVER_ERROR", "An unexpected error occurred"));
    }
}
