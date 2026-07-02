package com.example.fileshare.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid token"),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "Expired token"),
    AUTH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    AUTH_DUPLICATED_EMAIL(HttpStatus.CONFLICT, "Duplicated email"),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation error"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "File size exceeds the limit"),
    FILE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "Invalid file type"),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "File upload failed"),
    FILE_SCAN_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "File scan failed"),
    FILE_QUARANTINED(HttpStatus.FORBIDDEN, "File is quarantined"),
    FILE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "File delete failed"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
