package com.auction.exception;

public class WalletException extends BaseException {
    public WalletException(WalletErrorCode errorCode) {
        super(errorCode.getDefaultMessage(), errorCode.getCode());
    }

    public WalletException(WalletErrorCode errorCode, String customMessage) {
        super(customMessage, errorCode.getCode());
    }
}