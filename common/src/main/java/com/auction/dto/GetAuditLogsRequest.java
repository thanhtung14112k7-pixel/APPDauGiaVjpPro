package com.auction.dto;

/**
 * Request DTO chứa tham số phân trang để tải danh sách Audit Logs dành cho Admin.
 */
public class GetAuditLogsRequest {
    private final int page;
    private final int pageSize;

    // Constructor mặc định (Giữ lại phòng trường hợp các bộ Parser cũ yêu cầu)
    public GetAuditLogsRequest() {
        this.page = 1;
        this.pageSize = 10;
    }

    public GetAuditLogsRequest(int page, int pageSize) {
        this.page = page;
        this.pageSize = pageSize;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    // Tuyệt đối không viết hàm Setter để bảo vệ tính toàn vẹn của dữ liệu Request
}
