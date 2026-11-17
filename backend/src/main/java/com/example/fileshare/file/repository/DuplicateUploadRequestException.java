package com.example.fileshare.file.repository;

public class DuplicateUploadRequestException extends RuntimeException {

    public DuplicateUploadRequestException(Throwable cause) {
        super("Upload request already exists for the idempotency key", cause);
    }
}
