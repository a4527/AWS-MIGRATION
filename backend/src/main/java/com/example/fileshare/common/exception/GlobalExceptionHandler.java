package com.example.fileshare.common.exception;

import com.example.fileshare.common.api.ApiResponse;
import com.example.fileshare.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final int IN_PROGRESS_RETRY_AFTER_SECONDS = 5;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        ResponseEntity.BodyBuilder response = ResponseEntity.status(errorCode.status());
        if (errorCode == ErrorCode.IDEMPOTENCY_IN_PROGRESS) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(IN_PROGRESS_RETRY_AFTER_SECONDS));
        }
        return response.body(ApiResponse.fail(errorCode.name(), exception.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded() {
        return ResponseEntity
                .status(ErrorCode.FILE_TOO_LARGE.status())
                .body(ApiResponse.fail(ErrorCode.FILE_TOO_LARGE.name(), ErrorCode.FILE_TOO_LARGE.defaultMessage()));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationDeniedException() {
        return ResponseEntity
                .status(ErrorCode.AUTH_ACCESS_DENIED.status())
                .body(ApiResponse.fail(ErrorCode.AUTH_ACCESS_DENIED.name(), ErrorCode.AUTH_ACCESS_DENIED.defaultMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception) {
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.status())
                .body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR.name(), ErrorCode.VALIDATION_ERROR.defaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception. method={}, path={}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.status())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR.name(), ErrorCode.INTERNAL_SERVER_ERROR.defaultMessage()));
    }
}
