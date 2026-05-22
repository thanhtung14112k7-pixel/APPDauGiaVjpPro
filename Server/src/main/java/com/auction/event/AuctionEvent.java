package com.auction.event;

/**
 * ============================================================
 * AuctionEvent - Đối tượng đại diện cho một sự kiện
 * ============================================================
 * Mục đích:
 * - Là phong bì chứa thông tin về sự kiện xảy ra
 * - Được gửi từ "Publisher" (AuctionService, AuctionManage) đến "Observers" (LiveRoomManage)
 * - Immutable: sau khi tạo không thay đổi (thread-safe)
 * Cấu trúc:
 * - roomId: ID của phiên đấu giá (auctionId)
 * - type: Loại sự kiện (NEW_BID, TIMER_TICK, ...)
 * - payload: Dữ liệu cụ thể của sự kiện (có thể là DTO, Map, v.v.)
 * Ví dụ:
 * - Event bid mới: new AuctionEvent("auction-123", AuctionEventType.NEW_BID, bidTransactionDTO)
 * - Event countdown: new AuctionEvent("auction-123", AuctionEventType.TIMER_TICK, 45)
 */
public class AuctionEvent {
    private final String roomId;          // ID phiên đấu giá (auctionId)
    private final AuctionEventType type;  // Loại sự kiện
    private final Object payload;         // Dữ liệu của sự kiện (DTO, Map, số, ...)
    private final long timestamp;         // Thời gian sự kiện xảy ra

    /**
     * Constructor đầy đủ
     *
     * @param roomId ID của phiên đấu giá (auctionId)
     * @param type Loại sự kiện
     * @param payload Dữ liệu sự kiện (có thể null)
     */
    public AuctionEvent(String roomId, AuctionEventType type, Object payload) {
        this.roomId = roomId;
        this.type = type;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis(); // Tự động ghi thời gian hiện tại
    }

    // ===== GETTERS =====

    /**
     * Lấy ID phiên đấu giá
     * @return roomId (auctionId)
     */
    public String getRoomId() {
        return roomId;
    }

    /**
     * Lấy loại sự kiện
     * @return AuctionEventType
     */
    public AuctionEventType getType() {
        return type;
    }

    /**
     * Lấy dữ liệu của sự kiện
     * @return payload (Object - có thể cast về DTO, Map, v.v.)
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * Lấy thời gian sự kiện xảy ra (milliseconds)
     * @return timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Để debug - in ra thông tin sự kiện
     */
    @Override
    public String toString() {
        return "AuctionEvent{" +
                "roomId='" + roomId + '\'' +
                ", type=" + type +
                ", payloadClass=" + (payload != null ? payload.getClass().getSimpleName() : "null") +
                ", timestamp=" + timestamp +
                '}';
    }
}

