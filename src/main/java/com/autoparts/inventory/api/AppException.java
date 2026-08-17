package com.autoparts.inventory.api;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public AppException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static AppException badRequest(String code, String message) {
        return new AppException(code, message, HttpStatus.BAD_REQUEST);
    }

    public static AppException unauthorized(String message) {
        return new AppException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }

    public static AppException notFound(String message) {
        return new AppException("NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    public static AppException conflict(String code, String message) {
        return new AppException(code, message, HttpStatus.CONFLICT);
    }

    public static AppException tooManyRequests(String code, String message) {
        return new AppException(code, message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
