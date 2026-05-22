package com.auction.service;

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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserService {
    private final UserDAOImpl userDAO = new UserDAOImpl();
    private final LogDAO logDAO = new LogDAOImpl();
    private final UserManage userManage = UserManage.getInstance();
    private final ConnectionManage connectionManage = ConnectionManage.getInstance();

    // =========================================================================
    // 1. PHÂN HỆ NGHIỆP VỤ TÀI CHÍNH (ĐỒNG BỘ RAM & DATABASE)
    // =========================================================================

    /**
     * NẠP TIỀN VÀO TÀI KHOẢN
     */
    public void depositMoney(String bidderId, double amount) {
        if (bidderId == null) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Target identification handle is required.");
        }
        if (amount <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Deposit volume transaction constraint must be positive.");
        }

        boolean isSavedDB = userDAO.addAvailableBalance(bidderId, amount);
        if (!isSavedDB) {
            throw new WalletException(WalletErrorCode.TRANSACTION_FAILED, "Failed to inject balance amount statement into persistence layer.");
        }

        User ramUser = userManage.getUser(bidderId);
        if (ramUser instanceof Bidder bidder) {
            synchronized (bidder.getId().intern()) {
                bidder.setAvailableBalance(bidder.getAvailableBalance() + amount);
            }
        }
    }

    /**
     * RÚT TIỀN TỪ TÀI KHOẢN
     */
    public void withdrawMoney(String bidderId, double amount) {
        if (bidderId == null) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Target identity handle is required.");
        }
        if (amount <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Withdrawal criteria amount must be positive.");
        }

        User ramUser = userManage.getUser(bidderId);
        if (ramUser instanceof Bidder bidder) {
            if (bidder.getAvailableBalance() < amount) {
                throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
            }
        }

        boolean isDeductDB = userDAO.withdrawAvailableBalance(bidderId, amount);
        if (!isDeductDB) {
            throw new WalletException(WalletErrorCode.TRANSACTION_FAILED, "Balance debit extraction logic rejected at atomic transaction scope.");
        }

        if (ramUser instanceof Bidder bidderLive) {
            synchronized (bidderLive.getId().intern()) {
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
        List<User> dbUsers = userDAO.findPaginated(pageSize, offset);
        List<UserDTO> dtoDashboardList = new ArrayList<>();

        for (User user : dbUsers) {
            User ramUser = userManage.getUser(user.getId());
            UserStatus currentStatus = (ramUser != null) ? ramUser.getStatus() : user.getStatus();

            UserDTO dto = switch (user) {
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

            if (dto != null) {
                dtoDashboardList.add(dto);
            }
        }
        return dtoDashboardList;
    }

    /**
     * TÍNH NĂNG KHÓA TÀI KHOẢN TỨC THÌ CỦA ADMIN
     */
    public void lockUserAccount(String adminId, String userId, UserStatus targetStatus) {
        if (userId == null || targetStatus == null || adminId == null) {
            throw new ValidationException(ValidationErrorCode.BAD_REQUEST, "Required parameter constraints for locking operation are missing.");
        }

        if (targetStatus != UserStatus.BANNED) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Target state specification error. Restriction level must be BANNED.");
        }

        boolean isUpdatedDB = userDAO.updateStatus(userId, targetStatus.name());
        if (!isUpdatedDB) {
            throw new AuthenticationException(AuthErrorCode.USER_NOT_FOUND);
        }

        User ramUser = userManage.getUser(userId);
        if (ramUser != null) {
            synchronized (ramUser) {
                ramUser.setStatus(targetStatus);
            }
        }

        if (connectionManage.isUserOnline(userId)) {
            connectionManage.sendMessageToUser(userId, "FORCE_LOGOUT_REASON: ACCOUNT_LOCKED");
            connectionManage.forceDisconnectUser(userId);
            userManage.deleteUser(userId);
        }

        String logId = UUID.randomUUID().toString();
        String actionDetail = "Admin thay đổi trạng thái tài khoản người dùng sang: " + targetStatus.name();
        logDAO.insertLog(logId, adminId, actionDetail, "USER", userId);
    }
}