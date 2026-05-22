package com.auction.exception;

public abstract class BaseException extends RuntimeException {
    private final String errorCode;
    private final long timestamp;

    // Constructor nhận thông điệp tùy biến và mã lỗi dạng String từ các Enum con
    public BaseException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.timestamp = System.currentTimeMillis(); // Giữ nguyên ý tưởng tuyệt vời của bạn để làm log
    }

    public String getErrorCode() {
        return errorCode;
    }

    public long getTimestamp() {
        return timestamp;
    }
}