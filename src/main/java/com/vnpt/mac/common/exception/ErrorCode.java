package com.vnpt.mac.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    AUTH_MFA_REQUIRED(HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_MFA(HttpStatus.UNAUTHORIZED),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN),
    PARTNER_NOT_FOUND(HttpStatus.NOT_FOUND),
    PARTNER_NOT_ACTIVE(HttpStatus.CONFLICT),
    PARTNER_TAX_CODE_EXISTS(HttpStatus.CONFLICT),
    PARTNER_STATUS_INVALID(HttpStatus.CONFLICT),
    PARTNER_QUOTA_EXCEEDED(HttpStatus.CONFLICT),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    USER_EMAIL_EXISTS(HttpStatus.CONFLICT),
    USER_STATUS_INVALID(HttpStatus.CONFLICT),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVITATION_INVALID(HttpStatus.BAD_REQUEST),
    API_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND),
    ASSIGNMENT_INVALID(HttpStatus.BAD_REQUEST),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    VERSION_NOT_FOUND(HttpStatus.NOT_FOUND),
    VERSION_STATUS_INVALID(HttpStatus.CONFLICT),
    VERSION_NOT_EDITABLE(HttpStatus.CONFLICT),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND),
    ARTIFACT_TYPE_MISMATCH(HttpStatus.BAD_REQUEST),
    ARTIFACT_MISSING(HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(HttpStatus.CONFLICT),
    REVIEW_FEEDBACK_REQUIRED(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;
    ErrorCode(HttpStatus status) { this.status = status; }
    public HttpStatus status() { return status; }
}
