package com.auction.service;

import com.auction.dao.UserDAO;
import com.auction.dto.AdminDTO;
import com.auction.dto.BidderDTO;
import com.auction.dto.SellerDTO;
import com.auction.dto.UserDTO;
import com.auction.enums.UserRole;
import com.auction.enums.UserStatus;
import com.auction.exception.AuthErrorCode;
import com.auction.exception.AuthenticationException;
import com.auction.models.User.Admin;
import com.auction.models.User.AdminFactory;
import com.auction.models.User.Bidder;
import com.auction.models.User.BidderFactory;
import com.auction.models.User.Seller;
import com.auction.models.User.SellerFactory;
import com.auction.models.User.User;
import com.auction.models.User.UserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private AuthService authService;
    private UserDAO userDAO;

    /**
     * Hàm này chạy trước mỗi test.
     *
     * AuthService hiện tại tự tạo UserDAOImpl bằng dòng:
     * private final UserDAO userDAO = new UserDAOImpl();
     *
     * Nếu để nguyên như vậy, test sẽ đụng database thật.
     * Vì ta đang viết unit test nên dùng Mockito để tạo userDAO giả.
     *
     * Sau đó dùng reflection để gắn mock userDAO vào AuthService.
     */
    @BeforeEach
    void setUp() throws Exception {
        // Đăng ký UserFactory giống Main.java đang làm khi server start.
        // Nếu không đăng ký, UserFactory.createUser(...) sẽ không biết tạo Bidder/Seller/Admin.
        UserFactory.setRegistry(UserRole.BIDDER, new BidderFactory());
        UserFactory.setRegistry(UserRole.SELLER, new SellerFactory());
        UserFactory.setRegistry(UserRole.ADMIN, new AdminFactory());

        // Tạo service thật
        authService = new AuthService();

        // Tạo DAO giả
        userDAO = mock(UserDAO.class);

        // Inject userDAO giả vào AuthService
        injectUserDAO(authService, userDAO);
    }

    /**
     * Helper dùng reflection để thay userDAO thật bằng userDAO mock.
     *
     * Lý do cần hàm này:
     * AuthService không nhận UserDAO qua constructor,
     * nên ta không thể truyền mock theo cách bình thường.
     */
    private void injectUserDAO(AuthService authService, UserDAO mockUserDAO) throws Exception {
        Field field = AuthService.class.getDeclaredField("userDAO");
        field.setAccessible(true);
        field.set(authService, mockUserDAO);
    }

    /**
     * Helper kiểm tra AuthenticationException có đúng mã lỗi hay không.
     *
     * BaseException có method getErrorCode().
     * AuthErrorCode có method getCode().
     *
     * Ta so sánh 2 giá trị này để chắc chắn service ném đúng lỗi.
     */
    private void assertAuthError(AuthenticationException exception, AuthErrorCode expectedError) {
        assertEquals(expectedError.getCode(), exception.getErrorCode());
    }

    // =========================================================
    // TEST REGISTER - VALIDATION USERNAME
    // =========================================================

    /**
     * Test register() khi username null.
     *
     * Trong AuthService.validateUsername():
     * nếu username == null hoặc username.isEmpty()
     * thì ném AuthenticationException với code USERNAME_NULL_EMPTY.
     */
    @Test
    void registerShouldThrowWhenUsernameIsNull() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    null,
                    "Password@123",
                    "user@example.com",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.USERNAME_NULL_EMPTY);
    }

    /**
     * Test register() khi username rỗng.
     *
     * Username rỗng không hợp lệ.
     */
    @Test
    void registerShouldThrowWhenUsernameIsEmpty() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "",
                    "Password@123",
                    "user@example.com",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.USERNAME_NULL_EMPTY);
    }

    /**
     * Test register() khi username quá ngắn.
     *
     * Code hiện tại yêu cầu username tối thiểu 5 ký tự.
     */
    @Test
    void registerShouldThrowWhenUsernameIsTooShort() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "abc",
                    "Password@123",
                    "user@example.com",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.USERNAME_TOO_SHORT);
    }

    /**
     * Test register() khi username quá dài.
     *
     * Code hiện tại yêu cầu username tối đa 20 ký tự.
     */
    @Test
    void registerShouldThrowWhenUsernameIsTooLong() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "this_username_is_too_long_123",
                    "Password@123",
                    "user@example.com",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.USERNAME_TOO_LONG);
    }

    /**
     * Test register() khi username sai format.
     *
     * Regex hiện tại chỉ cho phép:
     * chữ cái, chữ số, dấu chấm '.', dấu gạch dưới '_'
     *
     * Username có khoảng trắng hoặc ký tự đặc biệt như @ sẽ bị từ chối.
     */
    @Test
    void registerShouldThrowWhenUsernameHasInvalidFormat() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "bad user",
                    "Password@123",
                    "user@example.com",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.USERNAME_INVALID_FORMAT);
    }

    // =========================================================
    // TEST REGISTER - VALIDATION EMAIL
    // =========================================================

    /**
     * Test register() khi email null.
     */
    @Test
    void registerShouldThrowWhenEmailIsNull() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "user01",
                    "Password@123",
                    null,
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.EMAIL_NULL_EMPTY);
    }

    /**
     * Test register() khi email rỗng.
     */
    @Test
    void registerShouldThrowWhenEmailIsEmpty() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "user01",
                    "Password@123",
                    "",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.EMAIL_NULL_EMPTY);
    }

    /**
     * Test register() khi email sai format.
     *
     * Ví dụ "not-an-email" không có dạng user@example.com.
     */
    @Test
    void registerShouldThrowWhenEmailFormatIsInvalid() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "user01",
                    "Password@123",
                    "not-an-email",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.EMAIL_INVALID_FORMAT);
    }

    // =========================================================
    // TEST REGISTER - VALIDATION PASSWORD
    // =========================================================

    /**
     * Test register() khi password null.
     */
    @Test
    void registerShouldThrowWhenPasswordIsNull() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "user01",
                    null,
                    "user@example.com",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.PASSWORD_NULL_EMPTY);
    }

    /**
     * Test register() khi password rỗng.
     */
    @Test
    void registerShouldThrowWhenPasswordIsEmpty() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "user01",
                    "",
                    "user@example.com",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.PASSWORD_NULL_EMPTY);
    }

    /**
     * Test register() khi password quá ngắn.
     *
     * Code hiện tại yêu cầu password ít nhất 8 ký tự.
     */
    @Test
    void registerShouldThrowWhenPasswordIsTooShort() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "user01",
                    "Aa@1",
                    "user@example.com",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.PASSWORD_TOO_SHORT);
    }

    /**
     * Test register() khi password yếu.
     *
     * Regex hiện tại yêu cầu password có:
     * - ít nhất 1 chữ hoa
     * - ít nhất 1 chữ thường
     * - ít nhất 1 số
     * - ít nhất 1 ký tự đặc biệt
     * - không có khoảng trắng
     */
    @Test
    void registerShouldThrowWhenPasswordIsWeak() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "user01",
                    "password",
                    "user@example.com",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.PASSWORD_WEAK);
    }

    // =========================================================
    // TEST REGISTER - ROLE, DUPLICATE, DB SAVE
    // =========================================================

    /**
     * Test register() khi role null.
     *
     * Role null thì service không biết tạo Bidder/Seller/Admin,
     * nên phải ném ROLE_INVALID.
     */
    @Test
    void registerShouldThrowWhenRoleIsNull() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "user01",
                    "Password@123",
                    "user@example.com",
                    null
            );
        });

        assertAuthError(exception, AuthErrorCode.ROLE_INVALID);
    }

    /**
     * Test register() khi username đã tồn tại trong database.
     *
     * Mock:
     * userDAO.findByUsername("user01") trả về Optional có User.
     *
     * Expected:
     * service ném USERNAME_ALREADY_EXISTS.
     */
    @Test
    void registerShouldThrowWhenUsernameAlreadyExists() {
        Bidder existingUser = new Bidder(
                "user01",
                "old@example.com",
                "hashed-password"
        );

        when(userDAO.findByUsername("user01")).thenReturn(Optional.of(existingUser));

        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "user01",
                    "Password@123",
                    "user@example.com",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.USERNAME_ALREADY_EXISTS);
    }

    /**
     * Test register() khi email đã tồn tại trong database.
     *
     * Mock:
     * username chưa tồn tại
     * email đã tồn tại
     *
     * Expected:
     * service ném EMAIL_ALREADY_EXISTS.
     */
    @Test
    void registerShouldThrowWhenEmailAlreadyExists() {
        Bidder existingUser = new Bidder(
                "otherUser",
                "user@example.com",
                "hashed-password"
        );

        when(userDAO.findByUsername("user01")).thenReturn(Optional.empty());
        when(userDAO.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser));

        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "user01",
                    "Password@123",
                    "user@example.com",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }

    /**
     * Test register() khi database insert thất bại.
     *
     * Mock:
     * username/email chưa tồn tại
     * insertUser(...) trả về false
     *
     * Expected:
     * service ném REGISTRATION_FAILED.
     */
    @Test
    void registerShouldThrowWhenInsertUserFails() {
        when(userDAO.findByUsername("user01")).thenReturn(Optional.empty());
        when(userDAO.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(userDAO.insertUser(any(User.class))).thenReturn(false);

        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.register(
                    "user01",
                    "Password@123",
                    "user@example.com",
                    UserRole.BIDDER
            );
        });

        assertAuthError(exception, AuthErrorCode.REGISTRATION_FAILED);
    }

    /**
     * Test register() thành công với role BIDDER.
     *
     * Mock:
     * username/email chưa tồn tại
     * insertUser(...) trả true
     *
     * Expected:
     * trả về BidderDTO
     * username/email/role đúng
     * không trả password ra DTO
     */
    @Test
    void registerShouldReturnBidderDTOWhenRegisterBidderSuccessfully() throws AuthenticationException {
        when(userDAO.findByUsername("user01")).thenReturn(Optional.empty());
        when(userDAO.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(userDAO.insertUser(any(User.class))).thenReturn(true);

        UserDTO result = authService.register(
                "user01",
                "Password@123",
                "user@example.com",
                UserRole.BIDDER
        );

        assertNotNull(result);
        assertInstanceOf(BidderDTO.class, result);
        assertEquals("user01", result.getUsername());
        assertEquals("user@example.com", result.getEmail());
        assertEquals(UserRole.BIDDER, result.getRole());
        assertEquals(UserStatus.ACTIVE, result.getStatus());

        verify(userDAO).insertUser(any(User.class));
    }

    /**
     * Test register() thành công với role SELLER.
     */
    @Test
    void registerShouldReturnSellerDTOWhenRegisterSellerSuccessfully() throws AuthenticationException {
        when(userDAO.findByUsername("seller01")).thenReturn(Optional.empty());
        when(userDAO.findByEmail("seller@example.com")).thenReturn(Optional.empty());
        when(userDAO.insertUser(any(User.class))).thenReturn(true);

        UserDTO result = authService.register(
                "seller01",
                "Password@123",
                "seller@example.com",
                UserRole.SELLER
        );

        assertNotNull(result);
        assertInstanceOf(SellerDTO.class, result);
        assertEquals("seller01", result.getUsername());
        assertEquals("seller@example.com", result.getEmail());
        assertEquals(UserRole.SELLER, result.getRole());
    }

    /**
     * Test register() thành công với role ADMIN.
     */
    @Test
    void registerShouldReturnAdminDTOWhenRegisterAdminSuccessfully() throws AuthenticationException {
        when(userDAO.findByUsername("admin01")).thenReturn(Optional.empty());
        when(userDAO.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(userDAO.insertUser(any(User.class))).thenReturn(true);

        UserDTO result = authService.register(
                "admin01",
                "Password@123",
                "admin@example.com",
                UserRole.ADMIN
        );

        assertNotNull(result);
        assertInstanceOf(AdminDTO.class, result);
        assertEquals("admin01", result.getUsername());
        assertEquals("admin@example.com", result.getEmail());
        assertEquals(UserRole.ADMIN, result.getRole());
    }

    // =========================================================
    // TEST LOGIN
    // =========================================================

    /**
     * Test login() khi username/email null.
     *
     * AuthService.login() kiểm tra:
     * usernameOrEmail == null || password == null || isEmpty()
     *
     * Nếu sai thì ném INPUT_NULL_EMPTY.
     */
    @Test
    void loginShouldThrowWhenUsernameOrEmailIsNull() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.login(null, "Password@123");
        });

        assertAuthError(exception, AuthErrorCode.INPUT_NULL_EMPTY);
    }

    /**
     * Test login() khi password null.
     */
    @Test
    void loginShouldThrowWhenPasswordIsNull() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.login("user01", null);
        });

        assertAuthError(exception, AuthErrorCode.INPUT_NULL_EMPTY);
    }

    /**
     * Test login() khi username/email rỗng.
     */
    @Test
    void loginShouldThrowWhenUsernameOrEmailIsEmpty() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.login("", "Password@123");
        });

        assertAuthError(exception, AuthErrorCode.INPUT_NULL_EMPTY);
    }

    /**
     * Test login() khi password rỗng.
     */
    @Test
    void loginShouldThrowWhenPasswordIsEmpty() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.login("user01", "");
        });

        assertAuthError(exception, AuthErrorCode.INPUT_NULL_EMPTY);
    }

    /**
     * Test login() khi không tìm thấy user bằng username.
     *
     * Nếu input không chứa "@", AuthService sẽ gọi findByUsername().
     *
     * Mock:
     * findByUsername trả Optional.empty()
     *
     * Expected:
     * INVALID_CREDENTIALS.
     */
    @Test
    void loginShouldThrowInvalidCredentialsWhenUsernameNotFound() {
        when(userDAO.findByUsername("notFoundUser")).thenReturn(Optional.empty());

        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.login("notFoundUser", "Password@123");
        });

        assertAuthError(exception, AuthErrorCode.INVALID_CREDENTIALS);
    }

    /**
     * Test login() khi không tìm thấy user bằng email.
     *
     * Nếu input có chứa "@", AuthService sẽ gọi findByEmail().
     */
    @Test
    void loginShouldThrowInvalidCredentialsWhenEmailNotFound() {
        when(userDAO.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.login("notfound@example.com", "Password@123");
        });

        assertAuthError(exception, AuthErrorCode.INVALID_CREDENTIALS);
    }

    /**
     * Test login() khi password sai.
     *
     * Để checkPassword() hoạt động đúng,
     * user trong mock phải được tạo bằng UserFactory
     * để password được hash bằng BCrypt.
     */
    @Test
    void loginShouldThrowInvalidCredentialsWhenPasswordIsWrong() {
        Bidder user = UserFactory.createUser(
                UserRole.BIDDER,
                "loginuser01",
                "loginuser01@example.com",
                "Correct@123"
        );

        when(userDAO.findByUsername("loginuser01")).thenReturn(Optional.of(user));

        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.login("loginuser01", "Wrong@123");
        });

        assertAuthError(exception, AuthErrorCode.INVALID_CREDENTIALS);
    }

    /**
     * Test login() thành công bằng username.
     *
     * Mock:
     * findByUsername trả về user có password hash đúng.
     *
     * Expected:
     * trả về BidderDTO với username/email/role đúng.
     */
    @Test
    void loginShouldReturnDTOWhenUsernameAndPasswordAreCorrect() throws AuthenticationException {
        Bidder user = UserFactory.createUser(
                UserRole.BIDDER,
                "loginuser02",
                "loginuser02@example.com",
                "Correct@123"
        );

        when(userDAO.findByUsername("loginuser02")).thenReturn(Optional.of(user));

        UserDTO result = authService.login("loginuser02", "Correct@123");

        assertNotNull(result);
        assertInstanceOf(BidderDTO.class, result);
        assertEquals("loginuser02", result.getUsername());
        assertEquals("loginuser02@example.com", result.getEmail());
        assertEquals(UserRole.BIDDER, result.getRole());
    }

    /**
     * Test login() thành công bằng email.
     *
     * Nếu usernameOrEmail chứa "@",
     * AuthService sẽ tìm bằng userDAO.findByEmail().
     */
    @Test
    void loginShouldReturnDTOWhenEmailAndPasswordAreCorrect() throws AuthenticationException {
        Seller user = UserFactory.createUser(
                UserRole.SELLER,
                "sellerlogin01",
                "sellerlogin01@example.com",
                "Correct@123"
        );

        when(userDAO.findByEmail("sellerlogin01@example.com")).thenReturn(Optional.of(user));

        UserDTO result = authService.login("sellerlogin01@example.com", "Correct@123");

        assertNotNull(result);
        assertInstanceOf(SellerDTO.class, result);
        assertEquals("sellerlogin01", result.getUsername());
        assertEquals("sellerlogin01@example.com", result.getEmail());
        assertEquals(UserRole.SELLER, result.getRole());
    }

    // =========================================================
    // TEST LOGOUT
    // =========================================================

    /**
     * Test logout() khi userId null.
     *
     * AuthService.logout() kiểm tra:
     * userId == null || userId.trim().isEmpty()
     *
     * Nếu sai thì ném USER_NOT_FOUND.
     */
    @Test
    void logoutShouldThrowWhenUserIdIsNull() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.logout(null);
        });

        assertAuthError(exception, AuthErrorCode.USER_NOT_FOUND);
    }

    /**
     * Test logout() khi userId rỗng hoặc toàn dấu cách.
     */
    @Test
    void logoutShouldThrowWhenUserIdIsBlank() {
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.logout("   ");
        });

        assertAuthError(exception, AuthErrorCode.USER_NOT_FOUND);
    }

    /**
     * Test logout() khi không tìm thấy user trong RAM hoặc DB.
     *
     * Mock:
     * userDAO.findById(...) trả Optional.empty()
     *
     * Expected:
     * USER_NOT_FOUND.
     */
    @Test
    void logoutShouldThrowWhenUserDoesNotExist() {
        when(userDAO.findById("missing-user-id")).thenReturn(Optional.empty());

        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authService.logout("missing-user-id");
        });

        assertAuthError(exception, AuthErrorCode.USER_NOT_FOUND);
    }

    /**
     * Test logout() khi user tồn tại trong DB.
     *
     * AuthService.logout() không trả về gì.
     * Nếu user tồn tại thì không được ném exception.
     */
    @Test
    void logoutShouldNotThrowWhenUserExistsInDatabase() {
        Bidder user = new Bidder(
                "logoutUser",
                "logout@example.com",
                "hashed-password"
        );

        when(userDAO.findById("existing-user-id")).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> {
            authService.logout("existing-user-id");
        });
    }

    // =========================================================
    // TEST convertUserToDTO
    // =========================================================

    /**
     * Test convertUserToDTO(null).
     *
     * Code hiện tại:
     * if (user == null) return null;
     */
    @Test
    void convertUserToDTOShouldReturnNullWhenUserIsNull() {
        UserDTO result = authService.convertUserToDTO(null);

        assertNull(result);
    }

    /**
     * Test convertUserToDTO() với Bidder.
     *
     * Expected:
     * trả về BidderDTO
     * có username, email, role, status, balance
     * có joinedAuctionIds.
     */
    @Test
    void convertUserToDTOShouldConvertBidderToBidderDTO() {
        Bidder bidder = new Bidder(
                "bidderdto01",
                "bidderdto01@example.com",
                "hashed-password"
        );
        bidder.addAvailableBalance(500.0);
        bidder.freeze(100.0);
        bidder.addJoinedAuction("auction-1");
        bidder.addJoinedAuction("auction-2");

        UserDTO result = authService.convertUserToDTO(bidder);

        assertNotNull(result);
        assertInstanceOf(BidderDTO.class, result);

        BidderDTO dto = (BidderDTO) result;

        assertEquals("bidderdto01", dto.getUsername());
        assertEquals("bidderdto01@example.com", dto.getEmail());
        assertEquals(UserRole.BIDDER, dto.getRole());
        assertEquals(UserStatus.ACTIVE, dto.getStatus());

        // Sau khi nạp 500 và freeze 100:
        // available = 400, frozen = 100
        assertEquals(400.0, dto.getAvailableBalance());
        assertEquals(100.0, dto.getFrozenBalance());

        assertEquals(List.of("auction-1", "auction-2"), dto.getJoinedAuctionIds());
    }

    /**
     * Test convertUserToDTO() với Seller.
     *
     * Expected:
     * trả về SellerDTO
     * có rating và balance.
     */
    @Test
    void convertUserToDTOShouldConvertSellerToSellerDTO() {
        Seller seller = new Seller(
                "sellerdto01",
                "sellerdto01@example.com",
                "hashed-password"
        );
        seller.addAvailableBalance(1000.0);
        seller.freeze(200.0);

        UserDTO result = authService.convertUserToDTO(seller);

        assertNotNull(result);
        assertInstanceOf(SellerDTO.class, result);

        SellerDTO dto = (SellerDTO) result;

        assertEquals("sellerdto01", dto.getUsername());
        assertEquals("sellerdto01@example.com", dto.getEmail());
        assertEquals(UserRole.SELLER, dto.getRole());
        assertEquals(UserStatus.ACTIVE, dto.getStatus());

        // Sau khi nạp 1000 và freeze 200:
        // available = 800, frozen = 200
        assertEquals(800.0, dto.getAvailableBalance());
        assertEquals(200.0, dto.getFrozenBalance());

        // Seller mới có rating mặc định là 0
        assertEquals(0.0, dto.getRating());
    }

    /**
     * Test convertUserToDTO() với Admin.
     *
     * Expected:
     * trả về AdminDTO
     * role ADMIN
     * balance trong AdminDTO mặc định là 0.0 theo constructor AdminDTO.
     */
    @Test
    void convertUserToDTOShouldConvertAdminToAdminDTO() {
        Admin admin = new Admin(
                "admindto01",
                "admindto01@example.com",
                "hashed-password"
        );

        UserDTO result = authService.convertUserToDTO(admin);

        assertNotNull(result);
        assertInstanceOf(AdminDTO.class, result);

        AdminDTO dto = (AdminDTO) result;

        assertEquals("admindto01", dto.getUsername());
        assertEquals("admindto01@example.com", dto.getEmail());
        assertEquals(UserRole.ADMIN, dto.getRole());
        assertEquals(UserStatus.ACTIVE, dto.getStatus());

        // AdminDTO constructor truyền mặc định 0.0 cho 2 loại số dư
        assertEquals(0.0, dto.getAvailableBalance());
        assertEquals(0.0, dto.getFrozenBalance());
    }
}