package com.auction.event;

/**
 * ============================================================
 * AuctionEventType - Enum định nghĩa tất cả loại sự kiện
 * ============================================================
 * Mục đích:
 * - Liệt kê các loại sự kiện có thể xảy ra trong hệ thống đấu giá
 * - Dùng làm "type" trong AuctionEvent để các observer biết phải xử lý gì
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
     * Sự kiện: Người dùng đăng ký theo dõi phiên (Business State)
     * Payload: Map{username, message, viewerCount}
     * Khi: joinAuction() trong AuctionService
     * Mục đích: Ghi nhận tính toàn vẹn dữ liệu trong DB/RAM
     */
    AUCTION_SUBSCRIBED,

    /**
     * Sự kiện: Người dùng hủy đăng ký theo dõi phiên (Business State)
     * Payload: Map{username, message, viewerCount}
     * Khi: leaveAuction() trong AuctionService
     * Mục đích: Xóa kiểm toàn từ DB/RAM + Notify clients
     */
    AUCTION_UNSUBSCRIBED,

    /**
     * Sự kiện: Người dùng mở tab chi tiết để xem real-time (UI/Network State)
     * Payload: Map{username, message, viewerCount}
     * Khi: joinLiveRoom() trong AuctionService
     * Mục đích: Thêm ClientSession vào phòng + Broacast viewer count tăng
     */
    LIVE_ENTERED,

    /**
     * Sự kiện: Người dùng đóng tab chi tiết nhưng vẫn tracking (UI/Network State)
     * Payload: Map{username, message, viewerCount}
     * Khi: leaveLiveRoom() trong AuctionService
     * Mục đích: Xóa ClientSession khỏi phòng + Broadcast viewer count giảm
     */
    LIVE_EXITED
}

