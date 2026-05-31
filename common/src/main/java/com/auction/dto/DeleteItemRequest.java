package com.auction.dto;

import java.io.Serializable;

/**
 * Request DTO phục vụ luồng cưỡng chế gỡ bỏ/hạ tải vật phẩm vi phạm.
 * Sử dụng linh hoạt cho cả Seller (tự ẩn sản phẩm) và Admin (cưỡng chế xóa).
 */
public class DeleteItemRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String itemId;
    private String reason;
    private String userId; // 🔥 BỔ SUNG: Định danh người thực hiện hành vi (Admin hoặc Seller)

    public DeleteItemRequest() {
    }

    public DeleteItemRequest(String itemId, String reason, String userId) {
        this.itemId = itemId;
        this.reason = reason;
        this.userId = userId;
    }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getUserId() { return userId; } // 🔥 BỔ SUNG: Getter/Setter để AdminController bốc dữ liệu mạng
    public void setUserId(String userId) { this.userId = userId; }
}