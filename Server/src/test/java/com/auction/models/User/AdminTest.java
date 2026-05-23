package com.auction.models.User;

import com.auction.enums.UserRole;
import com.auction.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AdminTest {

    /**
     * Test constructor đăng ký mới của Admin.
     *
     * Trong Admin.java, constructor:
     * public Admin(String username, String email, String password)
     *
     * gọi:
     * super(username, email, password, UserRole.ADMIN);
     *
     * Vì vậy Admin mới tạo phải có role ADMIN.
     */
    @Test
    void newAdminShouldHaveAdminRole() {
        Admin admin = new Admin(
                "admin1",
                "admin1@example.com",
                "hashed-password"
        );

        // getUserRole() trả về enum UserRole
        assertEquals(UserRole.ADMIN, admin.getUserRole());

        // getRole() trả về String
        assertEquals("ADMIN", admin.getRole());
    }

    /**
     * Test số dư ban đầu của Admin.
     *
     * Admin kế thừa User,
     * nên constructor User sẽ set:
     * availableBalance = 0
     * frozenBalance = 0
     */
    @Test
    void newAdminShouldHaveZeroBalances() {
        Admin admin = new Admin(
                "admin1",
                "admin1@example.com",
                "hashed-password"
        );

        // Admin mới chưa có số dư khả dụng
        assertEquals(0.0, admin.getAvailableBalance());

        // Admin mới chưa có số tiền đóng băng
        assertEquals(0.0, admin.getFrozenBalance());
    }

    /**
     * Test trạng thái mặc định của Admin mới.
     *
     * Vì Admin gọi constructor User,
     * mà User set status mặc định là ACTIVE.
     */
    @Test
    void newAdminShouldBeActiveByDefault() {
        Admin admin = new Admin(
                "admin1",
                "admin1@example.com",
                "hashed-password"
        );

        // Admin mới tạo phải có status ACTIVE
        assertEquals(UserStatus.ACTIVE, admin.getStatus());
    }

    /**
     * Test Admin dùng được setter status kế thừa từ User.
     *
     * Method setStatus() nằm trong User,
     * nhưng Admin là class con nên vẫn dùng được.
     *
     * Lưu ý:
     * Nếu UserStatus của bạn không có BANNED,
     * hãy đổi sang enum thật đang có trong project.
     */
    @Test
    void adminStatusShouldBeUpdatedBySetStatus() {
        Admin admin = new Admin(
                "admin1",
                "admin1@example.com",
                "hashed-password"
        );

        // Đổi trạng thái admin
        admin.setStatus(UserStatus.BANNED);

        // Kiểm tra trạng thái đã được đổi chưa
        assertEquals(UserStatus.BANNED, admin.getStatus());
    }

    /**
     * Test Admin dùng được các method balance kế thừa từ User.
     *
     * Dù nghiệp vụ thực tế Admin có thể không cần đấu giá,
     * nhưng vì Admin kế thừa User nên các method balance vẫn tồn tại.
     *
     * Test này kiểm tra kế thừa hoạt động ổn.
     */
    @Test
    void adminShouldUseBalanceMethodsInheritedFromUser() {
        Admin admin = new Admin(
                "admin1",
                "admin1@example.com",
                "hashed-password"
        );

        // Nạp tiền
        admin.addAvailableBalance(1000.0);

        // Đóng băng 250
        boolean result = admin.freeze(250.0);

        // Freeze thành công
        assertTrue(result);

        // availableBalance giảm còn 750
        assertEquals(750.0, admin.getAvailableBalance());

        // frozenBalance tăng lên 250
        assertEquals(250.0, admin.getFrozenBalance());
    }

    /**
     * Test constructor thứ 2 của Admin: constructor load từ DB.
     *
     * Constructor này nhận dữ liệu đã có sẵn trong database.
     *
     * Mục tiêu:
     * đảm bảo Admin load từ DB giữ đúng username, email, password,
     * role, balance và status.
     */
    @Test
    void dbConstructorShouldRestoreAdminData() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        LocalDateTime updatedAt = LocalDateTime.now();

        Admin admin = new Admin(
                "admin-id-1",
                "admin1",
                "admin1@example.com",
                "hashed-password",
                UserRole.ADMIN,
                1000.0,
                200.0,
                UserStatus.ACTIVE,
                createdAt,
                updatedAt
        );

        // Kiểm tra thông tin cơ bản
        assertEquals("admin1", admin.getUsername());
        assertEquals("admin1@example.com", admin.getEmail());
        assertEquals("hashed-password", admin.getPassword());

        // Kiểm tra role
        assertEquals(UserRole.ADMIN, admin.getUserRole());
        assertEquals("ADMIN", admin.getRole());

        // Kiểm tra balance được load từ DB
        assertEquals(1000.0, admin.getAvailableBalance());
        assertEquals(200.0, admin.getFrozenBalance());

        // Kiểm tra status
        assertEquals(UserStatus.ACTIVE, admin.getStatus());
    }
}