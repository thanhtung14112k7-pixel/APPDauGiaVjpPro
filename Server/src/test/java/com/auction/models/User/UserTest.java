package com.auction.models.User;

import com.auction.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    @BeforeEach
    void setUp() {
        // Nạp khuôn đúc trước để có công cụ tạo user ảo test ví tiền
        UserFactory.setRegistry(UserRole.BIDDER, new BidderFactory());
    }

    @Test
    @DisplayName("Test dong bang tien thanh cong khi vi co du tien")
    void testBalanceFreeze_Success() {
        User user = UserFactory.createUser(UserRole.BIDDER, "nguoimua1", "mua@gmail.com", "Pass123@");

        // Nạp 100k vào ví
        user.addAvailableBalance(100.0);

        // Yêu cầu đóng băng 40k để đặt giá
        boolean isFrozen = user.freeze(40.0);

        // Kiem tra
        assertTrue(isFrozen, "Phai cho phep dong bang vi du tien");
        assertEquals(60.0, user.getAvailableBalance(), "Vi kha dung phai bi tru di 40k");
        assertEquals(40.0, user.getFrozenBalance(), "Vi dong bang phai nhan duoc 40k");
    }

    @Test
    @DisplayName("Test tu choi dong bang khi vi khong du tien")
    void testBalanceFreeze_FailNotEnoughMoney() {
        User user = UserFactory.createUser(UserRole.BIDDER, "nguoimua2", "mua2@gmail.com", "Pass123@");

        // Nạp 50k vào ví
        user.addAvailableBalance(50.0);

        // Tham lam đòi đóng băng 100k
        boolean isFrozen = user.freeze(100.0);

        // Kiem tra
        assertFalse(isFrozen, "Phai tu choi vi tien trong vi khong du");
        assertEquals(50.0, user.getAvailableBalance(), "Tien kha dung phai duoc giu nguyen");
        assertEquals(0.0, user.getFrozenBalance(), "Tien dong bang khong duoc tang");
    }

    @Test
    @DisplayName("Test giai phong tien tu dong bang ve lai kha dung")
    void testBalanceUnfreeze_Success() {
        User user = UserFactory.createUser(UserRole.BIDDER, "nguoimua3", "mua3@gmail.com", "Pass123@");
        user.addAvailableBalance(100.0);
        user.freeze(100.0); // Đóng băng toàn bộ 100k

        // Có người trả giá cao hơn, hệ thống nhả lại 100k cho user
        user.unfreeze(100.0);

        // Kiem tra
        assertEquals(100.0, user.getAvailableBalance(), "Tien phai duoc tra lai day du vao vi kha dung");
        assertEquals(0.0, user.getFrozenBalance(), "Tien dong bang phai ve 0");
    }

    @Test
    @DisplayName("Test them phien dau gia vao Bidder khong bi trung lap")
    void testBidderAddAuction_PreventDuplicate() {
        Bidder bidder = (Bidder) UserFactory.createUser(UserRole.BIDDER, "bidder_pro", "pro@gmail.com", "Pass123@");

        // Thêm phiên mã A1 lần đầu
        boolean addFirstTime = bidder.addJoinedAuction("A1");
        // Cố tình thêm mã A1 lần nữa
        boolean addSecondTime = bidder.addJoinedAuction("A1");

        // Kiem tra
        assertTrue(addFirstTime, "Them lan dau phai thanh cong");
        assertFalse(addSecondTime, "Them lan hai phai bi he thong chan lai");
        assertEquals(1, bidder.getJoinedAuctionIds().size(), "Danh sach chi duoc chua 1 ma A1 duy nhat");
    }
}