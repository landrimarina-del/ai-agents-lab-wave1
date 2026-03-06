package com.rise.backend.common;

import org.springframework.http.HttpStatus;

import java.util.Objects;

public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status is required");
    }

    public HttpStatus getStatus() {
        return status;
    }
}
