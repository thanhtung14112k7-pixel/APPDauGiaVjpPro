package com.auction.service;

import com.auction.dao.LogDAO;
import com.auction.dao.impl.UserDAOImpl;
import com.auction.dto.AdminDTO;
import com.auction.dto.BidderDTO;
import com.auction.dto.SellerDTO;
import com.auction.dto.UserDTO;
import com.auction.enums.UserRole;
import com.auction.enums.UserStatus;
import com.auction.exception.AuthErrorCode;
import com.auction.exception.AuthenticationException;
import com.auction.exception.ValidationErrorCode;
import com.auction.exception.ValidationException;
import com.auction.exception.WalletErrorCode;
import com.auction.exception.WalletException;
import com.auction.models.User.Admin;
import com.auction.models.User.Bidder;
import com.auction.models.User.Seller;
import com.auction.models.User.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private UserService userService;
    private FakeUserDAO userDAO;
    private FakeLogDAO logDAO;

    /**
     * Chạy trước mỗi test.
     *
     * Không dùng Mockito cho UserDAOImpl nữa,
     * vì máy của bạn báo Mockito không mock được class UserDAOImpl trên Java 25.
     *
     * Thay vào đó ta dùng:
     * - FakeUserDAO extends UserDAOImpl
     * - FakeLogDAO implements LogDAO
     *
     * Hai fake này không gọi database thật.
     */
    @BeforeEach
    void setUp() throws Exception {
        userService = new UserService();

        userDAO = new FakeUserDAO();
        logDAO = new FakeLogDAO();

        injectField(userService, "userDAO", userDAO);
        injectField(userService, "logDAO", logDAO);
    }

    /**
     * Helper inject field private trong UserService.
     *
     * UserService đang có:
     * private final UserDAOImpl userDAO = new UserDAOImpl();
     * private final LogDAO logDAO = new LogDAOImpl();
     *
     * Test thay 2 object thật này bằng fake object.
     */
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void assertValidationError(ValidationException exception, ValidationErrorCode expectedError) {
        assertEquals(expectedError.getCode(), exception.getErrorCode());
    }

    private void assertWalletError(WalletException exception, WalletErrorCode expectedError) {
        assertEquals(expectedError.getCode(), exception.getErrorCode());
    }

    private void assertAuthError(AuthenticationException exception, AuthErrorCode expectedError) {
        assertEquals(expectedError.getCode(), exception.getErrorCode());
    }

    // =========================================================
    // FAKE DAO
    // =========================================================

    /**
     * FakeUserDAO thay cho UserDAOImpl thật.
     *
     * Nó không kết nối database.
     * Test muốn DAO trả true/false thì set các biến bên dưới.
     */
    private static class FakeUserDAO extends UserDAOImpl {
        boolean addAvailableBalanceResult = true;
        boolean withdrawAvailableBalanceResult = true;
        boolean updateStatusResult = true;

        String lastAddBalanceUserId;
        double lastAddBalanceAmount;

        String lastWithdrawUserId;
        double lastWithdrawAmount;

        String lastUpdateStatusUserId;
        String lastUpdateStatus;

        List<User> paginatedUsers = new ArrayList<>();
        int lastPageSize;
        int lastOffset;

        @Override
        public boolean addAvailableBalance(String userId, double amount) {
            lastAddBalanceUserId = userId;
            lastAddBalanceAmount = amount;
            return addAvailableBalanceResult;
        }

        @Override
        public boolean withdrawAvailableBalance(String userId, double amount) {
            lastWithdrawUserId = userId;
            lastWithdrawAmount = amount;
            return withdrawAvailableBalanceResult;
        }

        @Override
        public boolean updateStatus(String userId, String status) {
            lastUpdateStatusUserId = userId;
            lastUpdateStatus = status;
            return updateStatusResult;
        }

        @Override
        public List<User> findPaginated(int limit, int offset) {
            lastPageSize = limit;
            lastOffset = offset;
            return paginatedUsers;
        }
    }

    /**
     * FakeLogDAO thay cho LogDAOImpl thật.
     *
     * Dùng để kiểm tra lockUserAccount() có ghi log không.
     */
    private static class FakeLogDAO implements LogDAO {
        boolean insertLogCalled = false;

        String lastAdminId;
        String lastActionDetail;
        String lastTargetType;
        String lastTargetId;

        @Override
        public void insertLog(String logId, String adminId, String actionDetail, String targetType, String targetId) {
            insertLogCalled = true;
            lastAdminId = adminId;
            lastActionDetail = actionDetail;
            lastTargetType = targetType;
            lastTargetId = targetId;
        }

        @Override
        public long getTotalLogCount() {
            return 0;
        }
    }

    // =========================================================
    // HELPER TẠO USER MẪU
    // =========================================================

    /**
     * Helper tạo Bidder có dữ liệu cố định.
     *
     * Dùng constructor load từ DB để tự set id,
     * vì dashboard cần kiểm tra id, role, balance, status.
     */
    private Bidder sampleBidder(String id) {
        return new Bidder(
                id,
                "bidder_" + id,
                id + "@example.com",
                "hashed-password",
                UserRole.BIDDER,
                500.0,
                100.0,
                UserStatus.ACTIVE,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now()
        );
    }

    /**
     * Helper tạo Seller có dữ liệu cố định.
     */
    private Seller sampleSeller(String id) {
        return new Seller(
                id,
                "seller_" + id,
                id + "@example.com",
                "hashed-password",
                UserRole.SELLER,
                800.0,
                50.0,
                UserStatus.ACTIVE,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                4.5
        );
    }

    /**
     * Helper tạo Admin có dữ liệu cố định.
     */
    private Admin sampleAdmin(String id) {
        return new Admin(
                id,
                "admin_" + id,
                id + "@example.com",
                "hashed-password",
                UserRole.ADMIN,
                0.0,
                0.0,
                UserStatus.ACTIVE,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now()
        );
    }

    // =========================================================
    // depositMoney()
    // =========================================================

    /**
     * depositMoney(): bidderId null thì báo thiếu field.
     */
    @Test
    void depositMoneyShouldThrowWhenBidderIdIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            userService.depositMoney(null, 100.0);
        });

        assertValidationError(exception, ValidationErrorCode.MISSING_REQUIRED_FIELD);
    }

    /**
     * depositMoney(): amount = 0 thì không hợp lệ.
     */
    @Test
    void depositMoneyShouldThrowWhenAmountIsZero() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            userService.depositMoney("bidder-1", 0.0);
        });

        assertValidationError(exception, ValidationErrorCode.INVALID_PARAMETER);
    }

    /**
     * depositMoney(): amount âm thì không hợp lệ.
     */
    @Test
    void depositMoneyShouldThrowWhenAmountIsNegative() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            userService.depositMoney("bidder-1", -50.0);
        });

        assertValidationError(exception, ValidationErrorCode.INVALID_PARAMETER);
    }

    /**
     * depositMoney(): DAO cộng tiền thất bại thì báo TRANSACTION_FAILED.
     */
    @Test
    void depositMoneyShouldThrowWhenDaoAddBalanceFails() {
        userDAO.addAvailableBalanceResult = false;

        WalletException exception = assertThrows(WalletException.class, () -> {
            userService.depositMoney("bidder-1", 100.0);
        });

        assertWalletError(exception, WalletErrorCode.TRANSACTION_FAILED);
    }

    /**
     * depositMoney(): DAO cộng tiền thành công thì không lỗi.
     */
    @Test
    void depositMoneyShouldNotThrowWhenDaoAddBalanceSuccess() {
        userDAO.addAvailableBalanceResult = true;

        assertDoesNotThrow(() -> {
            userService.depositMoney("bidder-1", 100.0);
        });

        assertEquals("bidder-1", userDAO.lastAddBalanceUserId);
        assertEquals(100.0, userDAO.lastAddBalanceAmount);
    }

    // =========================================================
    // withdrawMoney()
    // =========================================================

    /**
     * withdrawMoney(): bidderId null thì báo thiếu field.
     */
    @Test
    void withdrawMoneyShouldThrowWhenBidderIdIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            userService.withdrawMoney(null, 100.0);
        });

        assertValidationError(exception, ValidationErrorCode.MISSING_REQUIRED_FIELD);
    }

    /**
     * withdrawMoney(): amount = 0 thì không hợp lệ.
     */
    @Test
    void withdrawMoneyShouldThrowWhenAmountIsZero() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            userService.withdrawMoney("bidder-1", 0.0);
        });

        assertValidationError(exception, ValidationErrorCode.INVALID_PARAMETER);
    }

    /**
     * withdrawMoney(): amount âm thì không hợp lệ.
     */
    @Test
    void withdrawMoneyShouldThrowWhenAmountIsNegative() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            userService.withdrawMoney("bidder-1", -100.0);
        });

        assertValidationError(exception, ValidationErrorCode.INVALID_PARAMETER);
    }

    /**
     * withdrawMoney(): DAO rút tiền thất bại thì báo TRANSACTION_FAILED.
     */
    @Test
    void withdrawMoneyShouldThrowWhenDaoWithdrawFails() {
        userDAO.withdrawAvailableBalanceResult = false;

        WalletException exception = assertThrows(WalletException.class, () -> {
            userService.withdrawMoney("bidder-1", 100.0);
        });

        assertWalletError(exception, WalletErrorCode.TRANSACTION_FAILED);
    }

    /**
     * withdrawMoney(): DAO rút tiền thành công thì không lỗi.
     */
    @Test
    void withdrawMoneyShouldNotThrowWhenDaoWithdrawSuccess() {
        userDAO.withdrawAvailableBalanceResult = true;

        assertDoesNotThrow(() -> {
            userService.withdrawMoney("bidder-1", 100.0);
        });

        assertEquals("bidder-1", userDAO.lastWithdrawUserId);
        assertEquals(100.0, userDAO.lastWithdrawAmount);
    }

    // =========================================================
    // getAdminUserDashboard() - VALIDATION
    // =========================================================

    /**
     * getAdminUserDashboard(): page phải > 0.
     */
    @Test
    void getAdminUserDashboardShouldThrowWhenPageInvalid() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            userService.getAdminUserDashboard(0, 10);
        });

        assertValidationError(exception, ValidationErrorCode.INVALID_PARAMETER);
    }

    /**
     * getAdminUserDashboard(): pageSize phải > 0.
     */
    @Test
    void getAdminUserDashboardShouldThrowWhenPageSizeInvalid() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            userService.getAdminUserDashboard(1, 0);
        });

        assertValidationError(exception, ValidationErrorCode.INVALID_PARAMETER);
    }

    // =========================================================
    // getAdminUserDashboard() - CONVERT DTO
    // =========================================================

    /**
     * getAdminUserDashboard(): DAO trả list rỗng thì service trả list rỗng.
     *
     * Với page = 1, pageSize = 10:
     * offset = (1 - 1) * 10 = 0
     */
    @Test
    void getAdminUserDashboardShouldReturnEmptyListWhenDaoReturnsEmptyList() {
        userDAO.paginatedUsers = List.of();

        List<UserDTO> result = userService.getAdminUserDashboard(1, 10);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        assertEquals(10, userDAO.lastPageSize);
        assertEquals(0, userDAO.lastOffset);
    }

    /**
     * getAdminUserDashboard(): kiểm tra công thức offset.
     *
     * Với page = 3, pageSize = 20:
     * offset = (3 - 1) * 20 = 40
     */
    @Test
    void getAdminUserDashboardShouldCalculateOffsetCorrectly() {
        userDAO.paginatedUsers = List.of();

        userService.getAdminUserDashboard(3, 20);

        assertEquals(20, userDAO.lastPageSize);
        assertEquals(40, userDAO.lastOffset);
    }

    /**
     * getAdminUserDashboard(): convert Bidder sang BidderDTO.
     */
    @Test
    void getAdminUserDashboardShouldConvertBidderToBidderDTO() {
        Bidder bidder = sampleBidder("bidder-dashboard-1");
        bidder.addJoinedAuction("auction-1");
        bidder.addJoinedAuction("auction-2");

        userDAO.paginatedUsers = List.of(bidder);

        List<UserDTO> result = userService.getAdminUserDashboard(1, 10);

        assertEquals(1, result.size());
        assertInstanceOf(BidderDTO.class, result.get(0));

        BidderDTO dto = (BidderDTO) result.get(0);

        assertEquals("bidder-dashboard-1", dto.getId());
        assertEquals("bidder_bidder-dashboard-1", dto.getUsername());
        assertEquals("bidder-dashboard-1@example.com", dto.getEmail());
        assertEquals(UserRole.BIDDER, dto.getRole());
        assertEquals(UserStatus.ACTIVE, dto.getStatus());

        assertEquals(500.0, dto.getAvailableBalance());
        assertEquals(100.0, dto.getFrozenBalance());
        assertEquals(List.of("auction-1", "auction-2"), dto.getJoinedAuctionIds());
    }

    /**
     * getAdminUserDashboard(): convert Seller sang SellerDTO.
     */
    @Test
    void getAdminUserDashboardShouldConvertSellerToSellerDTO() {
        Seller seller = sampleSeller("seller-dashboard-1");

        userDAO.paginatedUsers = List.of(seller);

        List<UserDTO> result = userService.getAdminUserDashboard(1, 10);

        assertEquals(1, result.size());
        assertInstanceOf(SellerDTO.class, result.get(0));

        SellerDTO dto = (SellerDTO) result.get(0);

        assertEquals("seller-dashboard-1", dto.getId());
        assertEquals("seller_seller-dashboard-1", dto.getUsername());
        assertEquals("seller-dashboard-1@example.com", dto.getEmail());
        assertEquals(UserRole.SELLER, dto.getRole());
        assertEquals(UserStatus.ACTIVE, dto.getStatus());

        assertEquals(800.0, dto.getAvailableBalance());
        assertEquals(50.0, dto.getFrozenBalance());
        assertEquals(4.5, dto.getRating());
    }

    /**
     * getAdminUserDashboard(): convert Admin sang AdminDTO.
     */
    @Test
    void getAdminUserDashboardShouldConvertAdminToAdminDTO() {
        Admin admin = sampleAdmin("admin-dashboard-1");

        userDAO.paginatedUsers = List.of(admin);

        List<UserDTO> result = userService.getAdminUserDashboard(1, 10);

        assertEquals(1, result.size());
        assertInstanceOf(AdminDTO.class, result.get(0));

        AdminDTO dto = (AdminDTO) result.get(0);

        assertEquals("admin-dashboard-1", dto.getId());
        assertEquals("admin_admin-dashboard-1", dto.getUsername());
        assertEquals("admin-dashboard-1@example.com", dto.getEmail());
        assertEquals(UserRole.ADMIN, dto.getRole());
        assertEquals(UserStatus.ACTIVE, dto.getStatus());
    }

    /**
     * getAdminUserDashboard(): convert nhiều loại user cùng lúc.
     */
    @Test
    void getAdminUserDashboardShouldConvertMixedUsersToCorrectDTOs() {
        Bidder bidder = sampleBidder("bidder-mixed");
        Seller seller = sampleSeller("seller-mixed");
        Admin admin = sampleAdmin("admin-mixed");

        userDAO.paginatedUsers = List.of(bidder, seller, admin);

        List<UserDTO> result = userService.getAdminUserDashboard(1, 10);

        assertEquals(3, result.size());

        assertInstanceOf(BidderDTO.class, result.get(0));
        assertInstanceOf(SellerDTO.class, result.get(1));
        assertInstanceOf(AdminDTO.class, result.get(2));

        assertEquals(UserRole.BIDDER, result.get(0).getRole());
        assertEquals(UserRole.SELLER, result.get(1).getRole());
        assertEquals(UserRole.ADMIN, result.get(2).getRole());
    }

    // =========================================================
    // lockUserAccount()
    // =========================================================

    /**
     * lockUserAccount(): thiếu adminId thì BAD_REQUEST.
     */
    @Test
    void lockUserAccountShouldThrowWhenAdminIdIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            userService.lockUserAccount(null, "user-1", UserStatus.BANNED);
        });

        assertValidationError(exception, ValidationErrorCode.BAD_REQUEST);
    }

    /**
     * lockUserAccount(): thiếu userId thì BAD_REQUEST.
     */
    @Test
    void lockUserAccountShouldThrowWhenUserIdIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            userService.lockUserAccount("admin-1", null, UserStatus.BANNED);
        });

        assertValidationError(exception, ValidationErrorCode.BAD_REQUEST);
    }

    /**
     * lockUserAccount(): thiếu targetStatus thì BAD_REQUEST.
     */
    @Test
    void lockUserAccountShouldThrowWhenTargetStatusIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            userService.lockUserAccount("admin-1", "user-1", null);
        });

        assertValidationError(exception, ValidationErrorCode.BAD_REQUEST);
    }

    /**
     * lockUserAccount(): chỉ cho khóa sang BANNED.
     */
    @Test
    void lockUserAccountShouldThrowWhenTargetStatusIsNotBanned() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            userService.lockUserAccount("admin-1", "user-1", UserStatus.ACTIVE);
        });

        assertValidationError(exception, ValidationErrorCode.INVALID_PARAMETER);
    }

    /**
     * lockUserAccount(): DAO updateStatus fail thì USER_NOT_FOUND.
     */
    @Test
    void lockUserAccountShouldThrowWhenDaoUpdateStatusFails() {
        userDAO.updateStatusResult = false;

        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            userService.lockUserAccount("admin-1", "user-1", UserStatus.BANNED);
        });

        assertAuthError(exception, AuthErrorCode.USER_NOT_FOUND);
    }

    /**
     * lockUserAccount(): updateStatus thành công thì ghi log.
     */
    @Test
    void lockUserAccountShouldInsertLogWhenSuccess() {
        userDAO.updateStatusResult = true;

        assertDoesNotThrow(() -> {
            userService.lockUserAccount("admin-1", "user-1", UserStatus.BANNED);
        });

        assertEquals("user-1", userDAO.lastUpdateStatusUserId);
        assertEquals(UserStatus.BANNED.name(), userDAO.lastUpdateStatus);

        assertTrue(logDAO.insertLogCalled);
        assertEquals("admin-1", logDAO.lastAdminId);
        assertEquals("USER", logDAO.lastTargetType);
        assertEquals("user-1", logDAO.lastTargetId);
        assertTrue(logDAO.lastActionDetail.contains("BANNED"));
    }
}