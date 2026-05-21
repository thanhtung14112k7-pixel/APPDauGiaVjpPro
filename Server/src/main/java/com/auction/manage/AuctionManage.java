package com.auction.manage;

import com.auction.enums.AuctionStatus;
import com.auction.models.Auction.Auction;
import com.auction.server.event.AuctionEvent;
import com.auction.server.event.AuctionEventBus;
import com.auction.server.event.AuctionEventType;
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
    }

    public void removeAuctionById(String id){
        activeAuctions.remove(id);
    }

    public Auction getAuctionById(String id){
        return activeAuctions.get(id);
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
            }
        }
    }


    //Quản lý vòng đời của auction tự động bằng realtime
    // Quản lý vòng đời của auction tự động bằng realtime
    public void startLifecycleMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            LocalDateTime now = LocalDateTime.now();

            for (Auction auction : activeAuctions.values()) {
                // 1. Lưu lại trạng thái cũ
                AuctionStatus oldStatus = auction.getStatus();

                // 2. Refresh trạng thái theo thời gian thực
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
                }

                // 2. Thông báo khi VỪA MỚI chuyển trạng thái từ OPEN sang RUNNING
                if (oldStatus == OPEN && newStatus == RUNNING) {
                    // 🔥 THAY ĐỔI TẠI ĐÂY: Đồng bộ các Khóa dữ liệu khi phiên chính thức khai hỏa
                    AuctionEvent startEvent = getAuctionEvent(auction, auctionId);
                    AuctionEventBus.getInstance().publish(startEvent);
                }

                // 4. Nếu VỪA MỚI kết thúc thì gọi hàm xử lý
                if (oldStatus == RUNNING && newStatus == FINISHED) {
                    finishAuction(auction.getId());
                }
            }
        }, 0, 1, TimeUnit.SECONDS); // Quét mỗi giây 1 lần
    }

    @NotNull
    private static AuctionEvent getAuctionEvent(Auction auction, String auctionId) {
        Map<String, Object> statusPayload = new HashMap<>();
        statusPayload.put("newStatus", AuctionStatus.RUNNING.name());
        statusPayload.put("highestId", auction.getHighestBidderId()); // Thường là null tại vạch xuất phát
        statusPayload.put("highestPrice", auction.getCurrentPrice());  // Giá khởi điểm ban đầu
        statusPayload.put("message", "Phiên đấu giá ĐÃ BẮT ĐẦU! Hãy nhanh tay đặt giá!");

        AuctionEvent startEvent = new AuctionEvent(
                auctionId,
                AuctionEventType.STATUS_CHANGED,
                statusPayload
        );
        return startEvent;
    }
}
