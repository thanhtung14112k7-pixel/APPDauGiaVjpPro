package com.auction.dto;

import java.io.Serializable;

/**
 * Request DTO cho thao tác rút tiền từ tài khoản
 */
public class WithdrawRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private double amount;

    public WithdrawRequest() {
    }

    public WithdrawRequest(double amount) {
        this.amount = amount;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}

