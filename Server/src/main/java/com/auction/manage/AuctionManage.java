package com.auction.manage;

import com.auction.enums.AuctionStatus;
import com.auction.models.Auction.Auction;
import com.auction.event.AuctionEvent;
import com.auction.event.AuctionEventBus;
import com.auction.event.AuctionEventType;
import com.auction.service.AuctionService;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static com.auction.enums.AuctionStatus .*;

public class AuctionManage {
    public static volatile AuctionManage instance;
    // 🔥 THÊM MỚI: Quản lý thời gian tương tác cuối cùng của các phiên trên RAM (Để dọn dẹp phiên rác)
    private final Map<String, LocalDateTime> lastAccessedTime = new ConcurrentHashMap<>();
    // Cấu hình thời gian tối đa một phiên được phép "nằm im" trên RAM nếu không phải trạng thái RUNNING
    private static final long MAX_IDLE_MINUTES = 10;
    private final Map<String, Auction> activeAuctions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private AuctionService auctionService;

    private AuctionManage(){}
    public static AuctionManage getInstance(){
        AuctionManage temp = instance;
        if (temp == null){
            synchronized (AuctionManage.class){
                temp = instance;
                if(temp == null){
                    temp = instance  = new AuctionManage();
                }
            }
        }
        return temp;
    }

    // Hàm gọi lười AuctionService
    private AuctionService getAuctionService() {
        if (auctionService == null) {
            auctionService = new AuctionService();
        }
        return auctionService;
    }

    public void addAuction(Auction auction){
        activeAuctions.put(auction.getId(),auction);
        // 🔥 THÊM MỚI: Đánh dấu thời gian nạp vào RAM ban đầu
        lastAccessedTime.put(auction.getId(), LocalDateTime.now());
    }

    public void removeAuctionById(String id){
        activeAuctions.remove(id);
        lastAccessedTime.remove(id); // 🔥 Tháo dỡ vết cache
    }

    public Auction getAuctionById(String id){
        Auction auction = activeAuctions.get(id);
        if (auction != null) {
            // 🔥 THÊM MỚI: Mỗi khi có người truy cập (xem chi tiết, đặt giá), cập nhật mốc thời gian "sống"
            lastAccessedTime.put(id, LocalDateTime.now());
        }
        return auction;
    }

    public List<Auction> getAllActive(){
        return new ArrayList<>(activeAuctions.values());
    }

    private void finishAuction(String auctionId) {
        Auction auction = activeAuctions.get(auctionId);
        if (auction != null) {
            synchronized (auction) {
                // DOUBLE-CHECK: Lỡ có ai vừa vặn đặt giá và gia hạn thêm 60s khi ta đang đứng đợi khóa thì sao?
                auction.refreshStatus(LocalDateTime.now());
                if (auction.getStatus() != FINISHED) {
                    return; // Quay xe, chưa hết giờ!
                }

                // 1. Gọi Service Kế toán để trừ/cộng tiền trong Database
                getAuctionService().finalizeAuction(auctionId);

                // Xóa khỏi danh sách "đang hoạt động" để giải phóng bộ nhớ RAM
                activeAuctions.remove(auctionId);
                lastAccessedTime.remove(auctionId);
            }
        }
    }


    //Quản lý vòng đời của auction tự động bằng realtime
    public void startLifecycleMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            LocalDateTime now = LocalDateTime.now();

            // Luồng dọn dẹp sản phẩm rác ké luồng đếm giờ hệ thống
            ProductManage.getInstance().cleanupIdleProducts();

            for (Auction auction : activeAuctions.values()) {
                //  Lưu lại trạng thái cũ
                AuctionStatus oldStatus = auction.getStatus();

                //  Refresh trạng thái theo thời gian thực
                auction.refreshStatus(now);
                AuctionStatus newStatus = auction.getStatus();
                String auctionId = auction.getId();

                // 1. Tự động phát sự kiện TIMER_TICK mỗi giây cho phòng đang chạy
                if (newStatus == RUNNING) {
                    long secondsLeft = Duration.between(now, auction.getEndTime()).toSeconds();
                    if (secondsLeft < 0) secondsLeft = 0;

                    AuctionEvent timerEvent = new AuctionEvent(
                            auctionId,
                            AuctionEventType.TIMER_TICK,
                            secondsLeft
                    );
                    AuctionEventBus.getInstance().publish(timerEvent);
                    // Vì phòng đang RUNNING và bắn tick liên tục, ta luôn cập nhật để giữ nó trên RAM
                    lastAccessedTime.put(auctionId, now);
                }

                // 2. Thông báo khi VỪA MỚI chuyển trạng thái từ OPEN sang RUNNING
                if (oldStatus == OPEN && newStatus == RUNNING) {
                    // 🔥 THAY ĐỔI TẠI ĐÂY: Đồng bộ các Khóa dữ liệu khi phiên chính thức khai hỏa
                    AuctionEvent startEvent = getAuctionEvent(auctionId);
                    AuctionEventBus.getInstance().publish(startEvent);
                }

                // 3. Nếu VỪA MỚI kết thúc thì gọi hàm xử lý
                if (oldStatus == RUNNING && newStatus == FINISHED) {
                    finishAuction(auction.getId());
                    continue;
                }

                // 🔥 LUỒNG 4 (THÊM MỚI): KIỂM TRA ĐỂ TRỤC XUẤT CÁC PHIÊN KHÔNG HOẠT ĐỘNG (CACHE EVICTION)
                // Điều kiện trục xuất: Phiên KHÔNG PHẢI đang chạy (có thể là OPEN hoặc đã đóng)
                // VÀ không có ai tương tác (đặt giá, xem chi tiết) quá MAX_IDLE_MINUTES
                if (newStatus != RUNNING) {
                    // Tính toán xem còn bao nhiêu phút nữa thì phiên này bắt đầu chạy (startTime)
                    long minutesUntilStart = Duration.between(now, auction.getStartTime()).toMinutes();

                    // LUẬT BẢO VỆ: Nếu còn dưới 15 phút nữa là mở cửa, KHÔNG ĐƯỢC trục xuất khỏi RAM
                    if (minutesUntilStart > 15) {
                        LocalDateTime lastAccess = lastAccessedTime.get(auctionId);
                        if (lastAccess != null) {
                            long idleMinutes = Duration.between(lastAccess, now).toMinutes();
                            if (idleMinutes >= MAX_IDLE_MINUTES) {
                                // Đủ điều kiện nằm im và còn lâu mới chạy -> Tiến hành dọn dẹp giải phóng RAM
                                activeAuctions.remove(auctionId);
                                lastAccessedTime.remove(auctionId);
                            }
                        }
                    }
                }
            }
        }, 0, 1, TimeUnit.SECONDS); // Quét mỗi giây 1 lần
    }

    @NotNull
    private static AuctionEvent getAuctionEvent(String auctionId) {
        Map<String, Object> statusPayload = new HashMap<>();
        statusPayload.put("newStatus", AuctionStatus.RUNNING.name());
        statusPayload.put("message", "Phiên đấu giá ĐÃ BẮT ĐẦU! Hãy nhanh tay đặt giá!");

        return new AuctionEvent(
                auctionId,
                AuctionEventType.STATUS_CHANGED,
                statusPayload
        );
    }
}
