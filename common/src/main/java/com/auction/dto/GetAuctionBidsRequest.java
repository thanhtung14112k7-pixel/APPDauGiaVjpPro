package com.auction.dto;

public class GetAuctionBidsRequest {
    private String auctionId;
    private int page;
    private int pageSize;

    public GetAuctionBidsRequest() {}

    public GetAuctionBidsRequest(String auctionId, int page, int pageSize) {
        this.auctionId = auctionId;
        this.page = page;
        this.pageSize = pageSize;
    }

    public String getAuctionId() { return auctionId; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
}