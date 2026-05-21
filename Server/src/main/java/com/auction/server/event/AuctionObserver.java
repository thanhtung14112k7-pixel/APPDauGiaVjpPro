package com.auction.server.event;

/**
 * ============================================================
 * AuctionObserver - Interface cho Observer Pattern
 * ============================================================
 *
 * Mục đích:
 * - Định nghĩa contract cho các "Observers" (những class theo dõi sự kiện)
 * - Bất kỳ class nào implement interface này đều có thể nhận sự kiện từ EventBus
 * - Áp dụng Design Pattern: Observer Pattern (từ Gang of Four)
 *
 * Khi nào implement:
 * - LiveRoomManage: Để gửi broadcast message đến clients khi có sự kiện
 * - Trong tương lai: UserNotificationService, AnalyticsService, v.v.
 *
 * Phương thức:
 * - update(AuctionEvent event): Gọi khi có sự kiện mới
 */
public interface AuctionObserver {
    /**
     * Gọi khi Publisher (AuctionEventBus) phát hành một AuctionEvent
     *
     * Các Observer implement method này để:
     * - Kiểm tra type của event
     * - Xử lý logic phù hợp (VD: broadcast nếu là NEW_BID)
     * - NOT BLOCK: method này nên hoàn thành nhanh, không nên chạy lâu
     *
     * @param event Sự kiện đã xảy ra, chứa roomId, type, payload
     */
    void update(AuctionEvent event);
}

