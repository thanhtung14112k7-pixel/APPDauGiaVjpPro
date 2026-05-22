package com.auction.exception;

public class AuctionException extends BaseException {

    // Bản thân Exception con sẽ ôm định danh Enum riêng của nó để bảo đảm Type-Safety
    public AuctionException(AuctionErrorCode errorCode) {
        // Gọi lên Constructor của lớp cha (BaseException), truyền Message và tên của Enum làm ErrorCode
        super(errorCode.getDefaultMessage(), errorCode.getCode());
    }

    public AuctionException(AuctionErrorCode errorCode, String customMessage) {
        super(customMessage, errorCode.getCode());
    }
}