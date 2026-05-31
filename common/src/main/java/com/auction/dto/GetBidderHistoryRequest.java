package com.auction.dto;

public class GetBidderHistoryRequest {
    private int page;
    private int pageSize;

    public GetBidderHistoryRequest() {}

    public GetBidderHistoryRequest(int page, int pageSize) {
        this.page = page;
        this.pageSize = pageSize;
    }

    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
}