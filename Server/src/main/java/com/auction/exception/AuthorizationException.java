package com.auction.exception;

public class AuthorizationException extends BaseException {

    /**
     * Constructor 1: Sử dụng thông điệp mặc định của Enum (Khuyên dùng)
     */
    public AuthorizationException(AuthorizationErrorCode errorCode) {
        super(errorCode.getDefaultMessage(), errorCode.getCode());
    }

    /**
     * Constructor 2: Cho phép ghi đè thông điệp chi tiết hơn cho từng case cụ thể
     */
    public AuthorizationException(AuthorizationErrorCode errorCode, String customMessage) {
        super(customMessage, errorCode.getCode());
    }
}