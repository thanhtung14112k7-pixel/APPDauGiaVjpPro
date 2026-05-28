package com.auction.models.Auction;

import com.auction.enums.AuctionStatus;
import com.auction.enums.BidStatus;
import com.auction.enums.UserRole;
import com.auction.exception.AuctionException;
import com.auction.models.User.Bidder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class AuctionTest {

    private Auction auction;
    private Bidder validBidder;
    private Bidder sellerActingAsBidder;

    @BeforeEach
    void setUp() {
        LocalDateTime startTime = LocalDateTime.now();
        LocalDateTime endTime = startTime.plusHours(1);

        // Su dung ham khoi tao so 2 (Hydration) de khong can phu thuoc vao doi tuong Item
        auction = new Auction(
                "auction_test_001",
                "item_001",
                "seller_999",
                null,
                null,
                100.0,
                10.0,
                startTime,
                endTime,
                startTime,
                startTime,
                AuctionStatus.OPEN
        );

        validBidder = new Bidder("bidder_123", "khach", "khach@gmail.com", "Pass123@",
                UserRole.BIDDER, 5000.0, 0.0, null, null, null);

        sellerActingAsBidder = new Bidder("seller_999", "gian_thuong", "ban@gmail.com", "Pass123@",
                UserRole.BIDDER, 5000.0, 0.0, null, null, null);
    }

    @Test
    @DisplayName("Test cap nhat trang thai: Dang chay sang Ket thuc")
    void testRefreshStatus_TimeExpired() {
        LocalDateTime expiredTime = LocalDateTime.now().plusHours(2);
        auction.refreshStatus(expiredTime);
        assertEquals(AuctionStatus.FINISHED, auction.getStatus());
    }

    @Test
    @DisplayName("Test tu choi: Nguoi ban tu dat gia")
    void testPlaceBid_RejectSeller() {
        AuctionException exception = assertThrows(AuctionException.class, () -> {
            BidTransaction result = auction.placeBid(sellerActingAsBidder, 150.0, "bid_01");
        });

        // Kiểm tra xem mã lỗi ném ra có đúng là do người bán không được phép đặt giá hay không
        assertEquals("AUC_ROOM_008", exception.getErrorCode());
    }

    @Test
    @DisplayName("Test tu choi: Gia dat thap hon buoc gia")
    void testPlaceBid_RejectLowAmount() {
        AuctionException exception = assertThrows(AuctionException.class, () -> {
            BidTransaction result = auction.placeBid(validBidder, 105.0, "bid_02");
        });

        // Kiểm tra xem mã lỗi ném ra có đúng là do người bán không được phép đặt giá hay không
        assertEquals("AUC_ROOM_004", exception.getErrorCode());
    }

    @Test
    @DisplayName("Test dat gia dung va cong gio chong ban tia")
    void testPlaceBid_SuccessAndAntiSniping() {
        // Khởi tạo phòng đấu giá mục tiêu chuẩn bị đóng cửa (Nằm trong biên độ 30s cuối)
        auction = new Auction(
                "auction_test_002",
                "item_001",
                "seller_999",
                null,
                null,
                100.0,                        // Giá hiện tại
                10.0,                         // Bước giá sàn ban đầu
                LocalDateTime.now().minusMinutes(5), // 🔥 SỬA: startTime vừa mới diễn ra 5 phút trước (Hợp lệ)
                LocalDateTime.now().plusSeconds(10), // endTime còn 10 giây cuối (Kích hoạt Anti-sniping)
                LocalDateTime.now().minusMinutes(5), // 🔥 SỬA CỐT LÕI: createdAt vừa tạo 5 phút trước -> Trần cứng 30 phút sẽ là +25 phút nữa ở tương lai!
                LocalDateTime.now().minusMinutes(5), // updatedAt
                AuctionStatus.OPEN            // Trạng thái khởi thủy OPEN
        );

        // Lưu lại mốc thời gian kết thúc ban đầu để làm hệ quy chiếu so sánh
        LocalDateTime tightEndTime = auction.getEndTime();

        // Thực hiện hành vi đặt thầu hợp lệ
        BidTransaction result = auction.placeBid(validBidder, 200.0, "bid_03");

        // =========================================================
        // HỆ THỐNG KIỂM TRA CHỐNG BẮN TỈA CHỐT HẠ (PASSED 100%)
        // =========================================================

        // 1. Biên lai giao dịch sinh ra phải mang trạng thái duyệt chi ACCEPTED
        assertEquals(BidStatus.ACCEPTED, result.getStatus());

        // 2. RAM Core ghi nhận mức giá đỉnh mới nhảy vọt lên 200.0
        assertEquals(200.0, auction.getCurrentPrice());

        // 3. Con trỏ định danh người dẫn đầu cập nhật chuẩn xác sang "bidder_123"
        assertEquals("bidder_123", auction.getHighestBidderId());

        // 4. KIỂM TRA CHỐNG BẮN TỈA: Thời gian kết thúc bắt buộc phải bị đẩy lùi ra phía sau
        assertTrue(auction.getEndTime().isAfter(tightEndTime),
                "Thất bại: Mạch điều khiển Anti-sniping chưa thực hiện gia hạn thêm thời gian!");

        // 5. Khẳng định tịnh tiến thời gian chính xác tăng thêm 60 giây (EXTENSION_SECONDS = 60)
        assertEquals(tightEndTime.plusSeconds(60), auction.getEndTime());
    }
}