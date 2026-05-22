package com.auction.exception;

public class AuthenticationException extends BaseException {

    /**
     * Constructor 1: Sử dụng thông điệp mặc định của Enum (Khuyên dùng)
     */
    public AuthenticationException(AuthErrorCode errorCode) {
        // Đẩy thẳng message và code dạng chuỗi (VD: "AUTH_USERNAME_001") lên lớp cha BaseException
        super(errorCode.getDefaultMessage(), errorCode.getCode());
    }

    /**
     * Constructor 2: Cho phép ghi đè thông điệp chi tiết hơn khi cần thiết
     * nhưng vẫn giữ nguyên mã code định danh của lỗi đó
     */
    public AuthenticationException(AuthErrorCode errorCode, String customMessage) {
        super(customMessage, errorCode.getCode());
    }
}