package com.auction.manage;

import com.auction.enums.ActionType;
import com.auction.network.ClientSession;
import com.auction.server.event.AuctionObserver;
import com.auction.server.event.AuctionEvent;
import com.auction.server.event.AuctionEventType;
import com.auction.dto.BidTransactionDTO;
import com.auction.dto.SocketResponse;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ============================================================
 * LiveRoomManage - Quản lý Socket Connections & Broadcast Events
 * ============================================================
 * TRƯỚC: Quản lý tập trung tất cả socket connections theo phòng (auctionId)
 *        Chỉ có method: joinRoom(), leaveRoom(), broadcast()
 * BÂY GIỜ (Refactored with Observer Pattern):
 * - Implement AuctionObserver để lắng nghe sự kiện từ AuctionEventBus
 * - Khi nhận event, tự động broadcast đến tất cả clients trong phòng
 * - Giải phóng AuctionService khỏi trách nhiệm quản lý client notifications
 * Lợi ích của cách này:
 * ✅ Decoupling: AuctionService không cần biết về ClientSession
 * ✅ Scalability: Dễ thêm các Observer khác (NotificationService, AnalyticsService)
 * ✅ Maintainability: Tất cả broadcast logic tập trung ở 1 chỗ
 * ✅ Thread-safe: ConcurrentHashMap + CopyOnWriteArrayList
 * SOLID Principles:
 * - Single Responsibility: Chỉ quản lý broadcast & client sessions
 * - Open/Closed: Mở cho extension (thực thi AuctionObserver), đóng cho modification
 * - Dependency Inversion: Phụ thuộc vào AuctionObserver interface, không concrete class
 */
public class LiveRoomManage implements AuctionObserver {
    private static volatile LiveRoomManage instance;

    // Key: auctionId, Value: List<ClientSession> đang theo dõi phiên
    private final Map<String, CopyOnWriteArrayList<ClientSession>> rooms
        = new ConcurrentHashMap<>();

    // JSON serializer - dùng để serialize SocketResponse
    private static final Gson gson = new Gson();

    private LiveRoomManage() {
        System.out.println("[LiveRoom] 🚀 LiveRoomManage khởi động");
    }

    /**
     * Singleton pattern - Double-checked locking
     */
    public static LiveRoomManage getInstance() {
        LiveRoomManage temp = instance;
        if (temp == null) {
            synchronized (LiveRoomManage.class) {
                temp = instance;
                if (temp == null) {
                    temp = instance = new LiveRoomManage();
                }
            }
        }
        return temp;
    }

    // ============================================================
    // OBSERVER PATTERN: Implement AuctionObserver interface
    // ============================================================

    /**
     * Callback gọi khi AuctionEventBus publish sự kiện
     * Mục đích:
     * - Lắng nghe tất cả sự kiện từ AuctionService, AuctionManage
     * - Dựa vào event.type, broadcast message phù hợp đến clients
     * - Giải phóng AuctionService khỏi trách nhiệm quản lý client notifications
     * Flow:
     * 1. AuctionService: Phát sinh sự kiện → publish(event) to EventBus
     * 2. AuctionEventBus: Gửi event đến tất cả observers
     * 3. LiveRoomManage.update(): Nhận event, xử lý, broadcast đến clients
     *
     * @param event Sự kiện đã xảy ra
     */
    @Override
    public void update(AuctionEvent event) {
        if (event == null) {
            return;
        }

        String roomId = event.getRoomId();
        AuctionEventType eventType = event.getType();

        System.out.println("[LiveRoom] 📨 Nhận event: " + eventType + " từ phòng " + roomId);

        // Switch-case xử lý từng loại sự kiện
        switch (eventType) {
            case NEW_BID:
                handleNewBidEvent(roomId, event.getPayload());
                break;

            case TIMER_TICK:
                handleTimerTickEvent(roomId, event.getPayload());
                break;

            case STATUS_CHANGED:
                handleStatusChangedEvent(roomId, event.getPayload());
                break;

            case VIEWER_COUNT_CHANGED:
                handleViewerCountChangedEvent(roomId, event.getPayload());
                break;

            default:
                System.out.println("[LiveRoom] ⚠️ Event type không được xử lý: " + eventType);
        }
    }

    /**
     * Xử lý sự kiện: Có người đặt giá mới (NEW_BID)
     * Payload: BidTransactionDTO
     * Action: Broadcast SocketResponse.event("BID_UPDATED", ...) đến tất cả clients
     *
     * @param roomId auctionId
     * @param payload BidTransactionDTO từ event
     */
    private void handleNewBidEvent(String roomId, Object payload) {
        if (!(payload instanceof BidTransactionDTO bidData)) {
            System.err.println("[LiveRoom] ❌ NEW_BID payload không phải BidTransactionDTO");
            return;
        }

        // Tạo SocketResponse dùng factory method: SocketResponse.event()
        SocketResponse response = SocketResponse.event(
                ActionType.BID_UPDATE,
                "Có lượt đặt giá mới! " + bidData.getBidderName() + " đặt giá: " + bidData.getAmount(),
                bidData
        );

        // Broadcast response đến tất cả clients trong phòng
        String jsonMessage = gson.toJson(response);
        broadcast(roomId, jsonMessage);

        System.out.println("[LiveRoom] 💰 Broadcast NEW_BID event: " + bidData.getBidderName()
            + " đặt giá " + bidData.getAmount());
    }

    /**
     * Xử lý sự kiện: Countdown thời gian (TIMER_TICK)
     * Payload: Integer (số giây còn lại) hoặc Map với chi tiết thời gian
     * Action: Broadcast thời gian countdown đến clients (update UI real-time)
     *
     * @param roomId auctionId
     * @param payload Thông tin thời gian
     */
    private void handleTimerTickEvent(String roomId, Object payload) {
        // ⏳ PLACEHOLDER - Implement bước tiếp theo
        //
        if (payload instanceof Number secondsRemaining) {
            SocketResponse response = SocketResponse.event(
                    ActionType.TIME_UPDATE,
                    "Thời gian còn lại: " + secondsRemaining.longValue() + " giây",
                    Map.of("roomId", roomId, "secondsRemaining", secondsRemaining.longValue())
            );
            broadcast(roomId, gson.toJson(response));
        }
    }

    /**
     * Xử lý sự kiện: Trạng thái phiên thay đổi (STATUS_CHANGED)
     * Payload: Map hoặc Object chứa {oldStatus, newStatus}
     * Action: Broadcast trạng thái mới đến clients (OPEN -> RUNNING -> FINISHED)
     *
     * @param roomId auctionId
     * @param payload Thông tin trạng thái cũ/mới
     */
    private void handleStatusChangedEvent(String roomId, Object payload) {
        // 🔄 PLACEHOLDER - Implement bước tiếp theo
        //
        // VD implementation:
        // 🔥 THAY ĐỔI TẠI ĐÂY: Nhận diện Map chứa thông tin chi tiết trạng thái
        if (payload instanceof Map<?, ?> statusMap) {
            String newStatus = (String) statusMap.get("newStatus");
            String highestId = (String) statusMap.get("highestId");
            Double highestPrice = (Double) statusMap.get("highestPrice");
            String message = (String) statusMap.get("message");

            // Tạo cấu trúc Body phản hồi đồng nhất gửi xuống Client
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("roomId", roomId);
            responseBody.put("newStatus", newStatus);
            responseBody.put("highestId", highestId != null ? highestId : "");
            responseBody.put("highestPrice", highestPrice != null ? highestPrice : 0.0);

            // Đóng gói vào mẫu SocketResponse
            SocketResponse response = SocketResponse.event(
                    ActionType.STATUS_UPDATED,
                    message != null ? message : "Trạng thái phiên thay đổi sang: " + newStatus,
                    responseBody
            );

            String jsonMessage = gson.toJson(response);
            broadcast(roomId, jsonMessage);

            System.out.println("[LiveRoom] 🔄 STATUS_CHANGED: " + newStatus
                    + " | Winner: " + highestId + " | Price: " + highestPrice);

            // 🔥 TỰ ĐỘNG DỌN DẸP PHÒNG MẠNG KHI PHIÊN KẾT THÚC HOẶC BỊ HỦY
            if ("FINISHED".equals(newStatus) || "CANCELED".equals(newStatus)) {
                clearRoom(roomId);
            }
        }
        else {
            System.err.println("[LiveRoom] ❌ STATUS_CHANGED payload không hợp lệ");
        }

    }

    /**
     * Xử lý sự kiện: Số người xem thay đổi (VIEWER_COUNT_CHANGED)
     * Payload: Integer (số người xem mới) hoặc Map với chi tiết
     * Action: Broadcast số viewer đến clients (update viewer count trên UI)
     *
     * @param roomId auctionId
     * @param payload Số lượng viewer hoặc thông tin chi tiết
     */
    private void handleViewerCountChangedEvent(String roomId, Object payload) {
        // 👥 PLACEHOLDER - Implement bước tiếp theo
        //
        // VD implementation:
         if (payload instanceof Integer) {
             Integer viewerCount = (Integer) payload;
             SocketResponse response = SocketResponse.event(
                 ActionType.VIEWER_COUNT_UPDATED,
                 "Hiện có " + viewerCount + " người xem phiên",
                 Map.of("viewerCount", viewerCount)
             );
             String jsonMessage = gson.toJson(response);
             broadcast(roomId, jsonMessage);
         }
    }

    // ============================================================
    // CÁC METHOD HOẠT ĐỘNG QUẢN LÝ PHÒNG (KHÔNG ĐỔI)
    // ============================================================

    /**
     * Join vào phòng đấu giá (Subscribe)
     * - Tạo room nếu chưa tồn tại
     * - Thêm client vào danh sách subscribers
     */
    public void joinRoom(String auctionId, ClientSession clientSession) {
        if (auctionId == null || clientSession == null) return;

        rooms.compute(auctionId, (key, room) -> {
            if (room == null) room = new CopyOnWriteArrayList<>();
            if (!room.contains(clientSession)) {
                room.add(clientSession);
                System.out.println("[LiveRoom] ✅ Client JOIN phiên " + auctionId);
            }
            return room; // Trả list đã update ngược lại vào Map
        });
    }

    /**
     * Rút khỏi phòng đấu giá (Unsubscribe)
     * - Xóa client khỏi danh sách -> ngắt kế nối khi đóng tab liveroom, hoac mất mạng
     * - Xóa room nếu trống (dọn dẹp bộ nhớ)
     */
    public void leaveRoom(String auctionId, ClientSession clientSession) {
        if (auctionId == null || clientSession == null) return;

        // Tính toán lại giá trị của phòng này trong trạng thái khóa an toàn
        rooms.computeIfPresent(auctionId, (key, room) -> {
            room.remove(clientSession);
            System.out.println("[LiveRoom] ❌ Client LEAVE phiên " + auctionId);

            // Nếu List rỗng, trả về null -> ConcurrentHashMap sẽ TỰ ĐỘNG XÓA key này!
            return room.isEmpty() ? null : room;
        });
    }

    /**
     * Phát sóng message đến tất cả clients trong phòng (Broadcast)
     * - Thread-safe: CopyOnWriteArrayList cho phép iterate an toàn khi add/remove
     * - Nếu gửi lỗi, tự động xóa client khỏi phòng
     */
    public void broadcast(String auctionId, String jsonMessage) {
        if (auctionId == null || jsonMessage == null) {
            return;
        }

        CopyOnWriteArrayList<ClientSession> room = rooms.get(auctionId);
        if (room == null || room.isEmpty()) {
            return; // Không có ai trong phòng
        }

        System.out.println("[LiveRoom] 📢 Broadcast tới " + room.size()
            + " clients ở phiên " + auctionId);

        // Iterate từ CopyOnWriteArrayList - thread-safe
        for (ClientSession client : room) {
            try {
                client.sendMessage(jsonMessage);
            } catch (Exception e) {
                // Nếu gửi lỗi (client disconnect), tự động xóa khỏi phòng
                System.err.println("[LiveRoom] ⚠️ Gửi message lỗi tới "
                    + client.getUserId() + ": " + e.getMessage());
                leaveRoom(auctionId, client);
            }
        }
    }

    /**
     * Lấy số lượng clients trong phòng (for monitoring)
     */
    public int getRoomSize(String auctionId) {
        CopyOnWriteArrayList<ClientSession> room = rooms.get(auctionId);
        return (room != null) ? room.size() : 0;
    }

    /**
     * Lấy tất cả phòng đang hoạt động (for monitoring)
     */
    public int getTotalRooms() {
        return rooms.size();
    }

    /**
     * Xóa toàn bộ clients khỏi phòng (khi phiên kết thúc)
     * - Gọi khi finalizeAuction hoặc cancelAuction để dọn dẹp
     */
    public void clearRoom(String auctionId) {
        CopyOnWriteArrayList<ClientSession> room = rooms.remove(auctionId);
        if (room != null) {
            room.clear();
            System.out.println("[LiveRoom] Phòng " + auctionId + " đã bị xóa");
        }
    }
}

