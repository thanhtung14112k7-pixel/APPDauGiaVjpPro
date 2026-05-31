package com.auction.dto;

import java.io.Serializable;

/**
 * Request DTO chứa dữ liệu khi Seller hoặc Admin muốn hủy một phiên đấu giá.
 * Sử dụng linh hoạt cho cả 2 đối tượng quản trị và người bán (Tái sử dụng DTO).
 */
public class CancelAuctionRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * ID phiên đấu giá cần hủy.
     */
    private String auctionId;

    /**
     * Lý do hủy phiên, dùng để thông báo cho các client đang theo dõi và ghi Audit Log.
     */
    private String reason;

    /**
     * 🔥 BỔ SUNG: Định danh người thực hiện hành vi (Admin ID hoặc Seller ID).
     */
    private String userId;

    public CancelAuctionRequest() {
    }

    public CancelAuctionRequest(String auctionId, String reason, String userId) {
        this.auctionId = auctionId;
        this.reason = reason;
        this.userId = userId;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    // 🔥 BỔ SUNG: Getter/Setter để AdminController bốc dữ liệu truyền vào AuctionService
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}