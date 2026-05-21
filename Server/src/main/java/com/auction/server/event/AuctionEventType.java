package com.auction.server.event;

/**
 * ============================================================
 * AuctionEventType - Enum định nghĩa tất cả loại sự kiện
 * ============================================================
 *
 * Mục đích:
 * - Liệt kê các loại sự kiện có thể xảy ra trong hệ thống đấu giá
 * - Dùng làm "type" trong AuctionEvent để các observer biết phải xử lý gì
 *
 * Các loại sự kiện:
 * - NEW_BID: Khi có người đặt giá mới
 * - TIMER_TICK: Hệ thống countdown giây, phút (real-time update)
 * - STATUS_CHANGED: Khi trạng thái phiên thay đổi (OPEN -> RUNNING -> FINISHED)
 * - VIEWER_COUNT_CHANGED: Khi số người xem thay đổi
 */
public enum AuctionEventType {
    /**
     * Sự kiện: Có người đặt giá mới
     * Payload: BidTransactionDTO
     * Khi: processBid() thành công trong AuctionService
     */
    NEW_BID,

    /**
     * Sự kiện: Countdown thời gian phiên (real-time)
     * Payload: Map{auctionId, secondsRemaining}
     * Khi: Scheduler mỗi 1 giây trong AuctionManage
     */
    TIMER_TICK,

    /**
     * Sự kiện: Trạng thái phiên thay đổi
     * Payload: Map{auctionId, newStatus, oldStatus}
     * Khi: finalizeAuction(), cancelAuction() trong AuctionService
     */
    STATUS_CHANGED,

    /**
     * Sự kiện: Số lượng người xem phòng thay đổi
     * Payload: Map{auctionId, viewerCount}
     * Khi: joinRoom(), leaveRoom() trong LiveRoomManage
     */
    VIEWER_COUNT_CHANGED
}

