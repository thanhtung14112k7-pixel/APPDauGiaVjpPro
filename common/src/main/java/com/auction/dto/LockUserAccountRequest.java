package com.auction.dto;

import com.auction.enums.UserStatus;
import java.io.Serializable;

/**
 * Request DTO để khóa tài khoản user (chỉ Admin)
 */
public class LockUserAccountRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;          // ID người dùng cần khóa
    private UserStatus targetStatus; // Trạng thái đích (BANNED)
    private String reason;           // Lý do khóa

    public LockUserAccountRequest() {
    }

    public LockUserAccountRequest(String userId, UserStatus targetStatus, String reason) {
        this.userId = userId;
        this.targetStatus = targetStatus;
        this.reason = reason;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public UserStatus getTargetStatus() {
        return targetStatus;
    }

    public void setTargetStatus(UserStatus targetStatus) {
        this.targetStatus = targetStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

