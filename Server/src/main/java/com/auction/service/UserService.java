package com.auction.service;

import com.auction.dao.UserDAO;
import com.auction.dao.LogDAO;
import com.auction.dao.impl.UserDAOImpl;
import com.auction.dao.impl.LogDAOImpl;
import com.auction.dto.*;
import com.auction.enums.UserStatus;
import com.auction.exception.AuthErrorCode;
import com.auction.exception.AuthenticationException;
import com.auction.exception.ValidationErrorCode;
import com.auction.exception.ValidationException;
import com.auction.exception.WalletErrorCode;
import com.auction.exception.WalletException;
import com.auction.manage.ConnectionManage;
import com.auction.manage.UserManage;
import com.auction.models.User.*;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserService {
    private final UserDAO userDAO = new UserDAOImpl(); // Đổi sang Interface UserDAO cho chuẩn Loose Coupling
    private final LogDAO logDAO = new LogDAOImpl();
    private final UserManage userManage = UserManage.getInstance();
    private final ConnectionManage connectionManage = ConnectionManage.getInstance();

    // =========================================================================
    // 1. PHÂN HỆ NGHIỆP VỤ TÀI CHÍNH (ĐỒNG BỘ RAM & DATABASE TUYỆT ĐỐI)
    // =========================================================================

    /**
     * NẠP TIỀN VÀO TÀI KHOẢN
     */
    public void depositMoney(String bidderId, double amount) {
        if (bidderId == null || bidderId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Target identification handle is required.");
        }
        if (amount <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Deposit volume transaction constraint must be positive.");
        }

        synchronized (bidderId.trim().intern()) {
            try (Connection conn = com.auction.config.DatabaseConnection.getConnection()) {

                boolean isSavedDB = userDAO.addAvailableBalance(conn, bidderId, amount);
                if (!isSavedDB) {
                    throw new WalletException(WalletErrorCode.TRANSACTION_FAILED, "Failed to inject balance amount statement into persistence layer.");
                }

            } catch (SQLException e) {
                throw new WalletException(WalletErrorCode.TRANSACTION_FAILED, "Database link failed at depositMoney: " + e.getMessage());
            }

            User ramUser = getOrLoadUser(bidderId);
            if (ramUser instanceof Bidder bidder) {
                bidder.setAvailableBalance(bidder.getAvailableBalance() + amount);
            }
        }
    }

    /**
     * RÚT TIỀN TỪ TÀI KHOẢN
     */
    public void withdrawMoney(String bidderId, double amount) {
        if (bidderId == null || bidderId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Target identity handle is required.");
        }
        if (amount <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Withdrawal criteria amount must be positive.");
        }

        synchronized (bidderId.trim().intern()) {

            User ramUser = getOrLoadUser(bidderId);
            if (ramUser == null) {
                throw new AuthenticationException(AuthErrorCode.USER_NOT_FOUND);
            }

            if (ramUser instanceof Bidder bidder) {
                if (bidder.getAvailableBalance() < amount) {
                    throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
                }

                if (bidder.getStatus() == UserStatus.BANNED) {
                    throw new WalletException(WalletErrorCode.TRANSACTION_FAILED, "Account is currently banned.");
                }
            }

            // 🔥 SỬA: Mở kết nối try-with-resources chủ động ở tầng Service và truyền conn vào DAO
            try (Connection conn = com.auction.config.DatabaseConnection.getConnection()) {

                boolean isDeductDB = userDAO.withdrawAvailableBalance(conn, bidderId, amount); // Truyền conn đã mở
                if (!isDeductDB) {
                    throw new WalletException(WalletErrorCode.TRANSACTION_FAILED, "Balance debit extraction logic rejected at atomic transaction scope.");
                }

            } catch (SQLException e) {
                throw new WalletException(WalletErrorCode.TRANSACTION_FAILED, "Database link failed at withdrawMoney: " + e.getMessage());
            }

            if (ramUser instanceof Bidder bidderLive) {
                bidderLive.setAvailableBalance(bidderLive.getAvailableBalance() - amount);
            }
        }
    }

    // =========================================================================
    // 2. PHÂN HỆ QUẢN LÝ CỦA ADMIN
    // =========================================================================

    /**
     * TÍNH NĂNG MÀN HÌNH QUẢN LÝ CỦA ADMIN
     */
    public List<UserDTO> getAdminUserDashboard(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Frame page metrics parameters must be positive.");
        }

        int offset = (page - 1) * pageSize;
        // Luồng đọc danh sách (SELECT) độc lập, giữ nguyên cấu trúc gọi an toàn
        List<User> dbUsers = userDAO.findPaginated(pageSize, offset);
        List<UserDTO> dtoDashboardList = new ArrayList<>();

        for (User user : dbUsers) {
            User ramUser = userManage.getUser(user.getId());
            UserDTO dto = getUserDTO(user, ramUser);

            if (dto != null) {
                dtoDashboardList.add(dto);
            }
        }
        return dtoDashboardList;
    }

    @Nullable
    private static UserDTO getUserDTO(User user, User ramUser) {
        UserStatus currentStatus = (ramUser != null) ? ramUser.getStatus() : user.getStatus();

        return switch (user) {
            case Bidder b -> new BidderDTO(
                    b.getId(), b.getUsername(), b.getEmail(), b.getUserRole(), currentStatus,
                    b.getAvailableBalance(), b.getFrozenBalance(), b.getJoinedAuctionIds()
            );
            case Seller s -> new SellerDTO(
                    s.getId(), s.getUsername(), s.getEmail(), s.getUserRole(), currentStatus,
                    s.getAvailableBalance(), s.getFrozenBalance(), s.getRating()
            );
            case Admin a -> new AdminDTO(
                    a.getId(), a.getUsername(), a.getEmail(), a.getUserRole(), currentStatus
            );
            default -> null;
        };
    }

    /**
     * TÍNH NĂNG KHÓA TÀI KHOẢN TỨC THÌ CỦA ADMIN
     */
    public void lockUserAccount(String adminId, String userId, UserStatus targetStatus) {
        if (userId == null || userId.trim().isEmpty() || targetStatus == null || adminId == null || adminId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.BAD_REQUEST, "Required parameter constraints for locking operation are missing.");
        }

        if (targetStatus != UserStatus.BANNED) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Target state specification error. Restriction level must be BANNED.");
        }

        synchronized (userId.trim().intern()) {

            // 🔥 SỬA: Bọc quy trình cập nhật trạng thái và ghi Audit Log vào chung một Database Transaction
            Connection conn = null;
            try {
                conn = com.auction.config.DatabaseConnection.getConnection();
                conn.setAutoCommit(false); // Bật transaction nguyên tử

                // 1. Cập nhật trạng thái người dùng (Truyền conn)
                boolean isUpdatedDB = userDAO.updateStatus(conn, userId, targetStatus.name());
                if (!isUpdatedDB) {
                    throw new AuthenticationException(AuthErrorCode.USER_NOT_FOUND);
                }

                // 2. Ghi nhật ký hành động phục vụ mục đích Audit Log (Truyền conn vào chung mạch transaction)
                String logId = UUID.randomUUID().toString();
                String actionDetail = "Admin thay đổi trạng thái tài khoản người dùng sang: " + targetStatus.name();
                logDAO.insertLog(conn, logId, adminId, actionDetail, "USER", userId);

                conn.commit(); // Thành công trọn vẹn cả 2 bước thì chốt hạ lưu vĩnh viễn
                System.out.println("[DB Transaction] ✅ Khóa tài khoản và ghi Audit Log thành công.");

            } catch (Exception e) {
                if (conn != null) {
                    try {
                        conn.rollback(); // Nếu gãy bất kỳ mắt xích nào, lập tức hủy bỏ lệnh cập nhật để tránh rách dữ liệu log
                        System.err.println("[DB Transaction] ❌ Thất bại khi thực thi khóa tài khoản. Đã thực hiện Rollback DB!");
                    } catch (SQLException ex) {
                        System.err.println("[DB Transaction] 🚨 Lỗi khẩn cấp không thể rollback: " + ex.getMessage());
                    }
                }

                if (e instanceof com.auction.exception.BaseException) {
                    throw (com.auction.exception.BaseException) e;
                }
                throw new AuthenticationException(AuthErrorCode.USER_NOT_FOUND);

            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
                }
            }

            // 3. Đồng bộ hóa lên đối tượng trạng thái RAM live sau khi Transaction DB hạ cánh an toàn
            User ramUser = userManage.getUser(userId);
            if (ramUser != null) {
                ramUser.setStatus(targetStatus);
            }

            // Cưỡng chế ngắt kết nối Socket và hủy phiên làm việc ngay lập tức trên hệ thống RAM Core
            if (connectionManage.isUserOnline(userId)) {
                connectionManage.sendMessageToUser(userId, "FORCE_LOGOUT_REASON: ACCOUNT_LOCKED");
                connectionManage.forceDisconnectUser(userId);
                userManage.deleteUser(userId);
            }
        }
    }

    /**
     * Quản lý cơ chế nạp bộ đệm (Cache-Aside Pattern) cho User
     */
    private User getOrLoadUser(String userId) {
        User user = userManage.getUser(userId);
        if (user == null) {
            user = userDAO.findById(userId).orElse(null);
            if (user != null && user.getStatus() != UserStatus.BANNED) {
                userManage.addUser(user);
            }
        }
        return user;
    }
}