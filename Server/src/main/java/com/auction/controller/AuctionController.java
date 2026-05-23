package com.auction.controller;

import com.auction.dao.UserDAO;
import com.auction.dao.impl.UserDAOImpl;
import com.auction.dto.*;
import com.auction.exception.AuctionErrorCode;
import com.auction.exception.AuctionException;
import com.auction.exception.ValidationErrorCode;
import com.auction.exception.ValidationException;
import com.auction.models.User.Bidder;
import com.auction.models.User.User;
import com.auction.network.ClientSession;
import com.auction.service.AuctionService;
import com.google.gson.Gson;

import java.time.LocalDateTime;
import java.util.List;

/**
 * =========================================================================
 * AuctionController - Bộ điều phối phân phối chức năng đấu giá phía Server
 * =========================================================================
 * Vai trò:
 * - Tiếp nhận chuỗi JSON từ RequestDispatcher thô gửi lên.
 * - Giải mã (Parse) dữ liệu thô thành đối tượng Request DTO cụ thể.
 * - Xác thực định danh bảo mật của User thông qua ClientSession tin cậy.
 * - Điều phối gọi xuống tầng AuctionService để thực thi nghiệp vụ Core.
 * - Trả dữ liệu thuần (DTO/Void) về cho RequestDispatcher đóng gói SocketResponse.
 */
public class AuctionController {
    private final Gson gson = new Gson();
    private final AuctionService auctionService = new AuctionService();
    private final UserDAO userDAO = new UserDAOImpl();

    /**
     * Lấy danh sách các phiên đấu giá đang hoạt động.
     * Action tương ứng: GET_ACTIVE_AUCTIONS
     */
    public List<AuctionSummaryDTO> getActiveAuctions() {
        return auctionService.getAllActiveAuctions();
    }

    /**
     * Lấy chi tiết thông tin của một phiên đấu giá cụ thể.
     * Action tương ứng: GET_AUCTION_DETAIL
     */
    public AuctionDetailDTO getAuctionDetail(String bodyJson) {
        GetAuctionDetailRequest request = parseBody(bodyJson, GetAuctionDetailRequest.class);
        requireText(request.getAuctionId(), "auctionId must not be empty.");

        AuctionDetailDTO detail = auctionService.getAuctionDetail(request.getAuctionId());
        if (detail == null) {
            throw new AuctionException(AuctionErrorCode.AUCTION_NOT_FOUND);
        }
        return detail;
    }

    /**
     * Tạo một phiên đấu giá hoàn toàn mới.
     * Action tương ứng: CREATE_AUCTION
     * Bảo mật: Tự động bốc sellerId từ Session bảo mật, chặn Client giả mạo ID.
     */
    public void createAuction(String bodyJson, ClientSession session) {
        CreateAuctionRequest request = parseBody(bodyJson, CreateAuctionRequest.class);

        String sellerId = requireLoggedInUserId(session);
        requireText(request.getItemId(), "itemId must not be empty.");

        if (request.getStepPrice() <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "stepPrice must be greater than 0.");
        }

        LocalDateTime startTime = parseDateTime(request.getStartTime(), "startTime");
        LocalDateTime endTime = parseDateTime(request.getEndTime(), "endTime");

        if (!endTime.isAfter(startTime)) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "endTime must be after startTime.");
        }

        auctionService.createAuction(
                request.getItemId(),
                sellerId,
                request.getStepPrice(),
                startTime,
                endTime
        );
    }

    /**
     * Thực hiện đặt một bước giá mới vào phiên đấu giá.
     * Action tương ứng: PLACE_BID
     */
    public void placeBid(String bodyJson, ClientSession session) {
        PlaceBidRequest request = parseBody(bodyJson, PlaceBidRequest.class);
        requireText(request.getAuctionId(), "auctionId must not be empty.");

        if (request.getAmount() <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "amount must be greater than 0.");
        }

        Bidder bidder = getCurrentBidder(session);
        auctionService.processBid(
                bidder,
                request.getAuctionId(),
                request.getAmount()
        );
    }

    // =========================================================================
    // 🌐 NHÓM 1: CÁC TÁC VỤ GIAO DIỆN / MẠNG TRỰC TIẾP (STATELESS LIVE ROOM)
    // =========================================================================

    /**
     * Người dùng mở Tab xem màn hình chi tiết thời gian thực công khai.
     * Action tương ứng: SUBSCRIBE_AUCTION
     * Luồng: Cắm kết nối Socket vật lý vào LiveRoomManage để nghe đếm giây và chat phòng.
     */
    public AuctionDetailDTO joinLiveRoom(String bodyJson, ClientSession session) {
        AuctionSubscriptionRequest request = parseBody(bodyJson, AuctionSubscriptionRequest.class);
        requireText(request.getAuctionId(), "auctionId must not be empty.");

        Bidder bidder = getCurrentBidder(session);

        // Gọi luồng kích hoạt Socket vãng lai trực tiếp
        auctionService.joinLiveRoom(
                bidder,
                request.getAuctionId(),
                session
        );

        // Trả về dữ liệu chi tiết thô ngay lập tức để Client vẽ UI ban đầu
        return auctionService.getAuctionDetail(request.getAuctionId());
    }

    /**
     * Người dùng đóng/thoát Tab xem chi tiết để chuyển đi màn hình khác.
     * Action tương ứng: UNSUBSCRIBE_AUCTION
     * Luồng: Tháo Socket kết nối ra khỏi phòng phát loa để tiết kiệm tài nguyên mạng vật lý.
     */
    public void leaveLiveRoom(String bodyJson, ClientSession session) {
        AuctionSubscriptionRequest request = parseBody(bodyJson, AuctionSubscriptionRequest.class);
        requireText(request.getAuctionId(), "auctionId must not be empty.");

        Bidder bidder = getCurrentBidder(session);

        // Rút phích cắm truyền thông phòng
        auctionService.leaveLiveRoom(
                bidder,
                request.getAuctionId(),
                session
        );
    }

    // =========================================================================
    // 💼 NHÓM 2: CÁC TÁC VỤ NGHIỆP VỤ LƯU TRỮ BỀN VỮNG (STATEFUL BUSINESS STATE)
    // =========================================================================

    /**
     * Người dùng chủ động ấn nút "Theo dõi phiên" từ giao diện danh sách.
     * Action tương ứng: JOIN_AUCTION (Hoặc mã đăng ký nền tương đương của bạn)
     * Luồng: Đồng bộ lưu vết vĩnh viễn mối quan hệ xuống MySQL/RAM để nhận Toast đè giá ngầm.
     */
    public void joinAuction(String bodyJson, ClientSession session) {
        AuctionSubscriptionRequest request = parseBody(bodyJson, AuctionSubscriptionRequest.class);
        requireText(request.getAuctionId(), "auctionId must not be empty.");

        Bidder bidder = getCurrentBidder(session);

        // Thực thi nghiệp vụ lưu vết bền vững không đụng tới kết nối dây mạng vật lý
        auctionService.joinAuction(
                bidder,
                request.getAuctionId()
        );
    }

    /**
     * Người dùng ấn nút "Hủy theo dõi" (Unwatch) ra khỏi bộ sưu tập cá nhân.
     * Action tương ứng: LEAVE_AUCTION (Hoặc mã hủy đăng ký nền tương đương của bạn)
     * Luồng: Cắt đứt triệt để liên kết MySQL/RAM, bẫy dọn sạch Socket phòng hờ rò rỉ.
     */
    public void leaveAuction(String bodyJson, ClientSession session) {
        AuctionSubscriptionRequest request = parseBody(bodyJson, AuctionSubscriptionRequest.class);
        requireText(request.getAuctionId(), "auctionId must not be empty.");

        Bidder bidder = getCurrentBidder(session);

        // Thực thi cưỡng chế hủy diệt và giải phóng rác tài nguyên toàn diện
        auctionService.leaveAuction(
                bidder,
                request.getAuctionId(),
                session
        );
    }

    // =========================================================================
    // 🛡️ NHÓM 3: ĐIỀU PHỐI VÒNG ĐỜI VÀ TIỆN ÍCH BỔ TRỢ (UTILITIES)
    // =========================================================================

    /**
     * Cưỡng chế hủy bỏ một phiên đấu giá đang diễn ra.
     * Action tương ứng: CANCEL_AUCTION
     */
    public void cancelAuction(String bodyJson, ClientSession session) {
        CancelAuctionRequest request = parseBody(bodyJson, CancelAuctionRequest.class);
        String userId = requireLoggedInUserId(session);

        requireText(request.getAuctionId(), "auctionId must not be empty.");

        String reason = request.getReason();
        if (isBlank(reason)) {
            reason = "No reason provided.";
        }

        auctionService.cancelAuction(
                request.getAuctionId(),
                userId,
                reason
        );
    }

    private <T> T parseBody(String bodyJson, Class<T> requestType) {
        if (isBlank(bodyJson)) {
            throw new ValidationException(ValidationErrorCode.BAD_REQUEST, "Request body must not be empty.");
        }
        T request = gson.fromJson(bodyJson, requestType);
        if (request == null) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Request body is invalid.");
        }
        return request;
    }

    private String requireLoggedInUserId(ClientSession session) {
        if (session == null || isBlank(session.getUserId())) {
            throw new ValidationException(ValidationErrorCode.BAD_REQUEST, "User is not logged in.");
        }
        return session.getUserId();
    }

    private User getCurrentUser(ClientSession session) {
        String userId = requireLoggedInUserId(session);
        return userDAO.findById(userId)
                .orElseThrow(() -> new AuctionException(AuctionErrorCode.ITEM_NOT_FOUND, "Current user not found."));
    }

    private Bidder getCurrentBidder(ClientSession session) {
        User user = getCurrentUser(session);
        if (!(user instanceof Bidder)) {
            throw new ValidationException(ValidationErrorCode.BAD_REQUEST, "Current user is not a bidder.");
        }
        return (Bidder) user;
    }

    private LocalDateTime parseDateTime(String value, String fieldName) {
        requireText(value, fieldName + " must not be empty.");
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, fieldName + " must be ISO local date time, for example 2026-05-18T10:30:00.");
        }
    }

    private void requireText(String value, String message) {
        if (isBlank(value)) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}