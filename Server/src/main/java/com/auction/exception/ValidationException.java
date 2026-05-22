package com.auction.exception;

public class ValidationException extends BaseException {

    /**
     * Constructor 1: Sử dụng thông điệp mặc định cấu hình sẵn trong Enum
     */
    public ValidationException(ValidationErrorCode errorCode) {
        super(errorCode.getDefaultMessage(), errorCode.getCode());
    }

    /**
     * Constructor 2: Cho phép ghi đè thông điệp lỗi chi tiết cho từng trường dữ liệu cụ thể
     */
    public ValidationException(ValidationErrorCode errorCode, String customMessage) {
        super(customMessage, errorCode.getCode());
    }
}