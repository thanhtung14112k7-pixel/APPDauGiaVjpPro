package com.auction.exception;

public enum ValidationErrorCode {
    // ===== INPUT VALIDATION ERRORS =====
    BAD_REQUEST("VAL_REQ_001", "The request body format is invalid or cannot be parsed"),
    MISSING_REQUIRED_FIELD("VAL_REQ_002", "A required data field is missing from the payload"),
    INVALID_PARAMETER("VAL_REQ_003", "The provided parameter values violate domain business constraints");

    private final String code;
    private final String message;

    ValidationErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return message;
    }
}