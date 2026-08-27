package com.vnpt.mac.common.exception;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getCode().status())
                .body(ApiError.of(ex.getCode().name(), ex.getMessage(), List.of(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR", "Dữ liệu không hợp lệ", fields, request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(403).body(ApiError.of("AUTH_FORBIDDEN", "Không có quyền thực hiện", List.of(), request));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> conflict(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return ResponseEntity.status(409).body(ApiError.of("CONCURRENT_MODIFICATION", "Dữ liệu đã được thay đổi", List.of(), request));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(413).body(ApiError.of("ARTIFACT_TOO_LARGE", "File vượt quá kích thước cho phép", List.of(), request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unhandled(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(500).body(ApiError.of("INTERNAL_ERROR", "Lỗi hệ thống", List.of(), request));
    }

    record FieldError(String field, String message) {
    }

    record ApiError(Error error, Meta meta) {
        static ApiError of(String code, String message, List<FieldError> fields, HttpServletRequest request) {
            String requestId = request.getHeader("X-Request-Id");
            if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
            return new ApiError(new Error(code, message, fields), new Meta(requestId, Instant.now()));
        }
    }

    record Error(String code, String message, List<FieldError> fieldErrors) {
    }

    record Meta(String requestId, Instant timestamp) {
    }
}
