package com.auction.dto;

import java.io.Serializable;

/**
 * Request DTO để lấy danh sách user (chỉ Admin)
 */
public class GetUserDashboardRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private int page;      // Trang hiện tại (bắt đầu từ 1)
    private int pageSize;  // Số dòng mỗi trang

    public GetUserDashboardRequest() {
    }

    public GetUserDashboardRequest(int page, int pageSize) {
        this.page = page;
        this.pageSize = pageSize;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}

