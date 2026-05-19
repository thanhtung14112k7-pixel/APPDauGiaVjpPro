package com.auction.models.User;

import com.auction.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserFactoryTest {

    @BeforeEach
    void setUp() {
        // Nap cac phan xuong con vao nha may tong truoc moi bai test
        UserFactory.setRegistry(UserRole.BIDDER, new BidderFactory());
        UserFactory.setRegistry(UserRole.SELLER, new SellerFactory());
        UserFactory.setRegistry(UserRole.ADMIN, new AdminFactory());
    }

    @Test
    @DisplayName("Test tao Bidder thanh cong va ma hoa mat khau")
    void testCreateBidder_SuccessAndHashedPassword() {
        String plainPassword = "MatKhauTuyetMat1@";

        // Goi nha may duc ra Bidder
        User bidder = UserFactory.createUser(UserRole.BIDDER, "test_bidder", "bidder@gmail.com", plainPassword);

        // Kiem tra san pham duc ra co dung la Bidder khong
        assertNotNull(bidder, "Doi tuong tao ra khong duoc rong");
        assertEquals(UserRole.BIDDER, bidder.getUserRole(), "Sai quyen han");
        assertEquals("test_bidder", bidder.getUsername(), "Sai ten hien thi");

        // Kiem tra bao mat mat khau
        assertNotEquals(plainPassword, bidder.getPassword(), "Mat khau chua duoc ma hoa an toan");
        assertTrue(bidder.checkPassword(plainPassword), "Mat khau ma hoa khong khop voi ban goc");
    }

    @Test
    @DisplayName("Test tao Seller thanh cong")
    void testCreateSeller_Success() {
        User seller = UserFactory.createUser(UserRole.SELLER, "test_seller", "seller@gmail.com", "Pass123@");

        assertNotNull(seller);
        assertEquals(UserRole.SELLER, seller.getUserRole());
        assertTrue(seller instanceof Seller, "Doi tuong phai la the thien cua lop Seller");
    }

    @Test
    @DisplayName("Test loi khi chua dang ky phan xuong")
    void testCreateUser_UnregisteredRole_ThrowsException() {
        // Gia lap tinh huong quen dang ky hoac xoa phan xuong ADMIN khoi nha may
        UserFactory.setRegistry(UserRole.ADMIN, null);

        // Yeu cau duc ADMIN xem co vang loi khong
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> UserFactory.createUser(UserRole.ADMIN, "loi_admin", "loi@gmail.com", "Pass123@"),
                "Phai vang loi neu phan xuong chua duoc dang ky"
        );

        assertTrue(exception.getMessage().contains("Role chưa được đăng ký trong hệ thống"));
    }
}