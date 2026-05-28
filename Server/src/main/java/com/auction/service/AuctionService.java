package com.auction.service;

import com.auction.dao.*;
import com.auction.dao.impl.*;
import com.auction.dto.AuctionDetailDTO;
import com.auction.dto.AuctionSummaryDTO;
import com.auction.dto.BidTransactionDTO;
import com.auction.enums.AuctionStatus;
import com.auction.enums.ItemStatus;
import com.auction.exception.*;
import com.auction.manage.AuctionManage;
import com.auction.manage.ConnectionManage;
import com.auction.manage.LiveRoomManage;
import com.auction.manage.ProductManage;
import com.auction.models.Auction.Auction;
import com.auction.models.User.User;
import com.auction.network.ClientSession;
import com.auction.models.Auction.BidTransaction;
import com.auction.models.Item.Item;
import com.auction.models.User.Bidder;
import com.auction.event.AuctionEvent;
import com.auction.event.AuctionEventBus;
import com.auction.event.AuctionEventType;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class AuctionService {
    private final AuctionManage auctionManage = AuctionManage.getInstance();
    private final ConnectionManage connectionManage = ConnectionManage.getInstance();
    private final ProductManage productManage = ProductManage.getInstance();

    private final UserDAO userDAO = new UserDAOImpl();
    private final AuctionDAO auctionDAO = new AuctionDAOImpl();
    private final ItemDAO itemDAO = new ItemDAOImpl();
    private final BidTransactionDAO bidTransactionDAO = new BidTransactionDAOImpl();
    private final LogDAO logDAO = new LogDAOImpl();

    public void createAuction(String itemId, String sellerId,
                              double stepPrice, LocalDateTime startTime, LocalDateTime endTime) {

        // =================================================================
        // HÀNG RÀO 1: VALIDATION ĐẦU VÀO (Sử dụng ValidationException)
        // =================================================================
        validateAuctionInput(itemId, sellerId, stepPrice, startTime, endTime);

        // =================================================================
        // HÀNG RÀO 2: KHÓA ĐỒNG BỘ DỰA TRÊN ID (Chống trùng lặp đa luồng)
        // =================================================================
        synchronized (itemId.intern()) {

            // Check RAM trước
            Item liveItem = productManage.getProduct(itemId);

            if (liveItem == null) {
                // RAM chưa có -> Check DB
                liveItem = itemDAO.findById(itemId)
                        .orElseThrow(() -> new AuctionException(AuctionErrorCode.ITEM_NOT_FOUND, "Product item does not exist in the warehouse."));

                // Đẩy lại vào cache RAM
                productManage.addProduct(liveItem);
            }

            // TÁI KIỂM TRA TRẠNG THÁI (Double-Check sau khi vào synchronized)
            if (liveItem.getStatus() != ItemStatus.ACTIVE) {
                throw new AuctionException(AuctionErrorCode.ITEM_IS_LOCKED, "This item is currently locked because it is live on the floor or already sold.");
            }

            // KIỂM TRA QUYỀN SỞ HỮU VẬT PHẨM
            if (!liveItem.getSellerId().equals(sellerId)) {
                throw new AuthorizationException(AuthorizationErrorCode.RESOURCE_OWNERSHIP_VIOLATION, "Access denied: You do not own this item resource.");
            }

            // =================================================================
            // HÀNG RÀO 3: THỰC THI XUỐNG CƠ SỞ DỮ LIỆU
            // =================================================================
            Auction newAuction = new Auction(liveItem, sellerId, stepPrice, startTime, endTime);

            // 🛠️ THAY ĐỔI CƠ CHẾ KẾT NỐI: Tạo Connection ngắn hạn bọc lót tính toàn vẹn cho 2 lệnh ghi DB lồng nhau
            try (Connection conn = com.auction.config.DatabaseConnection.getConnection()) {
                conn.setAutoCommit(false); // Bật transaction cho createAuction

                // 1. Insert phòng đấu giá mới (Truyền conn dùng chung)
                boolean isAuctionSaved = auctionDAO.insertAuction(conn, newAuction);
                if (!isAuctionSaved) {
                    throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Internal data persistence failed for creating auction.");
                }

                // 2. Chuyển trạng thái vật phẩm xuống DB thành INACTIVE để khóa lại (Truyền conn dùng chung)
                boolean isItemUpdated = itemDAO.updateStatus(conn, itemId, ItemStatus.INACTIVE.name());
                if (!isItemUpdated) {
                    throw new AuctionException(AuctionErrorCode.UPDATE_FAILED, "Database update operation failed for item status.");
                }

                conn.commit(); // Thành công thì commit
            } catch (SQLException e) {
                throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Transaction failed at createAuction scope: " + e.getMessage());
            }

            // =================================================================
            // HÀNG RÀO 4: ĐỒNG BỘ LÊN RAM (Chỉ khi DB đã xử lý an toàn)
            // =================================================================
            liveItem.setStatus(ItemStatus.INACTIVE);
            auctionManage.addAuction(newAuction);
        }
    }

    private void validateAuctionInput(String itemId, String sellerId, double stepPrice,
                                      LocalDateTime startTime, LocalDateTime endTime) {

        if (itemId == null || itemId.trim().isEmpty() || sellerId == null || sellerId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    "Item ID and Seller ID cannot be empty.");
        }

        if (startTime == null || endTime == null) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD,
                    "Start time and End time cannot be null.");
        }

        if (stepPrice <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_STEP_PRICE,
                    "Step price must be greater than zero.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (startTime.isBefore(now.minusSeconds(5))) {
            throw new ValidationException(ValidationErrorCode.START_TIME_IN_PAST,
                    "Auction start time cannot be in the past.");
        }

        if (!endTime.isAfter(startTime)) {
            throw new ValidationException(ValidationErrorCode.INVALID_END_TIME,
                    "End time must be after start time.");
        }
    }

    public void processBid(Bidder bidder, String auctionId, double amount) {
        validateBidInput(bidder, auctionId, amount);

        Auction auction = getAuctionContext(auctionId);

        if (!connectionManage.isUserOnline(bidder.getId())) {
            throw new AuctionException(AuctionErrorCode.BIDDER_NOT_ONLINE);
        }

        synchronized (auction) {
            if (auction.getStatus() != AuctionStatus.RUNNING) {
                throw new AuctionException(AuctionErrorCode.AUCTION_NOT_RUNNING);
            }

            if (amount < auction.getCurrentPrice() + auction.getStepPrice()) {
                throw new AuctionException(AuctionErrorCode.BID_AMOUNT_TOO_LOW);
            }

            String oldHighestBidderId = auction.getHighestBidderId();
            double oldPrice = auction.getCurrentPrice();
            LocalDateTime oldEndTime = auction.getEndTime();

            String newBidId = UUID.randomUUID().toString();
            Connection conn = null;

            try {
                conn = com.auction.config.DatabaseConnection.getConnection();
                conn.setAutoCommit(false);

                boolean freezeSuccess = userDAO.freezeMoney(conn, bidder.getId(), amount);
                if (!freezeSuccess) {
                    throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
                }

                BidTransaction resultBid = auction.placeBid(bidder, amount, newBidId);

                boolean updateAuctionDB = auctionDAO.updatePriceAndWinner(
                        conn, auctionId, amount, bidder.getId(), newBidId, auction.getEndTime(),auction.getLiveStepPrice()
                );
                if (!updateAuctionDB) {
                    throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Sync price to database failed.");
                }

                boolean insertedBid = bidTransactionDAO.insertBid(conn, resultBid);
                if (!insertedBid) {
                    throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Failed to persist bid transaction.");
                }

                if (oldHighestBidderId != null && !oldHighestBidderId.equals(bidder.getId())) {
                    userDAO.unfreezeMoney(conn, oldHighestBidderId, oldPrice);
                    bidTransactionDAO.updateStatusToRefunded(conn, auctionId, oldHighestBidderId);
                }

                if (!bidder.getJoinedAuctionIds().contains(auctionId)) {
                    userDAO.addJoinedAuction(conn, bidder.getId(), auctionId);
                }

                conn.commit();
                System.out.println("[DB Transaction] ✅ Commit thành công luồng đặt giá xuống Database.");

                synchronized (bidder.getId().intern()) {
                    bidder.freeze(amount);
                    if (!bidder.getJoinedAuctionIds().contains(auctionId)) {
                        bidder.addJoinedAuction(auctionId);
                    }
                }

                BidTransactionDTO bidData = new BidTransactionDTO(
                        bidder.getUsername(), amount, LocalDateTime.now(), com.auction.enums.BidStatus.ACCEPTED.name()
                );

                AuctionEvent bidEvent = new AuctionEvent(auctionId, AuctionEventType.NEW_BID, bidData);
                AuctionEventBus.getInstance().publish(bidEvent);
                System.out.println("[AuctionService] 📢 Đặt giá thành công. Broadcast NEW_BID event.");

            } catch (Exception e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                        System.err.println("[DB Transaction] ❌ Phát hiện lỗi trong luồng. Đã thực hiện Rollback DB hoàn toàn!");
                    } catch (SQLException ex) {
                        System.err.println("[DB Transaction] 🚨 Lỗi nghiêm trọng: Không thể rollback hệ thống: " + ex.getMessage());
                    }
                }

                auction.rollbackBidInRam(oldHighestBidderId, oldPrice, oldEndTime);

                if (e instanceof com.auction.exception.BaseException) {
                    throw (com.auction.exception.BaseException) e;
                }
                throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Fatal crash during processBid: " + e.getMessage());

            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    private void validateBidInput(Bidder bidder, String auctionId, double amount) {
        if (bidder == null || bidder.getId() == null) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Bidder data cannot be null.");
        }
        if (auctionId == null || auctionId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Auction ID cannot be empty.");
        }
        if (amount <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Bid amount must be strictly greater than zero.");
        }
    }

    public void finalizeAuction(String auctionId) {
        if (auctionId == null || auctionId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Auction ID is required for finalization.");
        }

        synchronized (auctionId.trim().intern()) {
            Auction auction = auctionManage.getAuctionById(auctionId);

            if (auction == null) {
                System.out.println("[Startup Cleanup] 🔄 Phát hiện phiên " + auctionId + " chưa nạp RAM. Tiến hành nạp cứu vãn dữ liệu...");

                auction = auctionDAO.findById(auctionId)
                        .orElseThrow(() -> new AuctionException(AuctionErrorCode.AUCTION_NOT_FOUND, "Cannot finalize an invalid auction."));

                Item itemFromDb = itemDAO.findById(auction.getItemId()).orElse(null);
                auction.setItem(itemFromDb);
            }

            if (auction.getStatus() == AuctionStatus.FINISHED || auction.getStatus() == AuctionStatus.CANCELED) {
                System.out.println("[Finalize Guard] ℹ️ Phiên " + auctionId + " đã được xử lý kết thúc từ trước. Bỏ qua.");
                return;
            }

            String winnerId = auction.getHighestBidderId();
            double finalPrice = auction.getCurrentPrice();
            String sellerId = auction.getSellerId();
            String statusMessage;

            // 🛠️ THAY ĐỔI CƠ CHẾ KẾT NỐI: Mở Connection dùng chung cho toàn bộ tiến trình đóng phiên đấu giá
            try (Connection conn = com.auction.config.DatabaseConnection.getConnection()) {
                conn.setAutoCommit(false); // Kích hoạt Transaction cho khối hoàn tất tài sản

                if (winnerId != null) {
                    // 1. Khấu trừ tiền đóng băng người thắng (Truyền conn)
                    boolean deductOk = userDAO.deductFrozenMoney(conn, winnerId, finalPrice);
                    if (deductOk) {
                        // 2. Cộng tiền khả dụng người bán (Truyền conn)
                        userDAO.addAvailableBalance(conn, sellerId, finalPrice);
                    } else {
                        throw new WalletException(WalletErrorCode.DEDUCTION_FAILED);
                    }
                    statusMessage = "Thông báo: Phiên " + auctionId + " ĐÃ KẾT THÚC. Người thắng: ID " + winnerId + " với giá: " + finalPrice;

                    // Chuyển trạng thái Item thành SOLD dưới DB (Truyền conn)
                    itemDAO.updateStatus(conn, auction.getItemId(), com.auction.enums.ItemStatus.SOLD.name());

                    var ramItem = productManage.getProduct(auction.getItemId());
                    if (ramItem != null) {
                        ramItem.setStatus(com.auction.enums.ItemStatus.SOLD);
                    }
                } else {
                    statusMessage = "Thông báo: Phiên " + auctionId + " ĐÃ KẾT THÚC. Không có người đặt giá.";

                    // Không ai mua -> Trả tự do cho Item thành ACTIVE dưới DB (Truyền conn)
                    itemDAO.updateStatus(conn, auction.getItemId(), com.auction.enums.ItemStatus.ACTIVE.name());

                    var ramItem = productManage.getProduct(auction.getItemId());
                    if (ramItem != null) {
                        ramItem.setStatus(com.auction.enums.ItemStatus.ACTIVE);
                    }
                }

                // 3. Cập nhật trạng thái kết thúc trong DB (Truyền conn)
                auctionDAO.updateStatus(conn, auctionId, AuctionStatus.FINISHED.name());

                conn.commit(); // Đồng bộ tất cả thay đổi tài chính xuống DB an toàn
            } catch (SQLException e) {
                throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Finalization transaction failed: " + e.getMessage());
            }

            // 4. Tinh gọn payload và broadcast
            Map<String, Object> statusPayload = new HashMap<>();
            statusPayload.put("newStatus", AuctionStatus.FINISHED.name());
            statusPayload.put("message", statusMessage);

            AuctionEvent statusEvent = new AuctionEvent(auctionId, AuctionEventType.STATUS_CHANGED, statusPayload);
            AuctionEventBus.getInstance().publish(statusEvent);

            // 5. Xóa phòng khỏi RAM Core
            LiveRoomManage.getInstance().clearRoom(auctionId);
            auctionManage.removeAuctionById(auctionId);
        }
    }

    public List<AuctionSummaryDTO> getJoinedAuctionsSummary(Bidder bidder) {
        List<AuctionSummaryDTO> summaries = new ArrayList<>();
        List<String> joinedIds = bidder.getJoinedAuctionIds();

        for (String id : joinedIds) {
            Auction auction = auctionManage.getAuctionById(id);
            if (auction == null) {
                auction = auctionDAO.findById(id).orElse(null);
            }
            if (auction != null) {
                summaries.add(convertToSummaryDTO(auction));
            }
        }
        return summaries;
    }

    private AuctionSummaryDTO convertToSummaryDTO(Auction auction) {
        String itemName = (auction.getItem() != null) ? auction.getItem().getName() : "Vật phẩm #" + auction.getItemId();
        return new AuctionSummaryDTO(
                auction.getId(),
                itemName,
                auction.getCurrentPrice(),
                auction.getStatus().name(),
                auction.getEndTime()
        );
    }

    public List<AuctionSummaryDTO> getAllActiveAuctions() {
        List<AuctionSummaryDTO> resultList = new ArrayList<>();

        // 🔥 SỬA TẠI ĐÂY: Service chủ động mở kết nối để quản lý tài nguyên tập trung
        try (Connection conn = com.auction.config.DatabaseConnection.getConnection()) {

            // Truyền conn vào hàm DAO theo đúng kiến trúc đồng bộ
            List<Auction> dbAuctions = auctionDAO.findByStatuses(conn, List.of(AuctionStatus.OPEN, AuctionStatus.RUNNING));

            if (dbAuctions == null || dbAuctions.isEmpty()) {
                return resultList;
            }

            // Tiến hành hòa trộn (Merge) dữ liệu với RAM
            for (Auction dbAuction : dbAuctions) {
                Auction ramAuction = auctionManage.getAuctionById(dbAuction.getId());

                Auction finalAuction = (ramAuction != null) ? ramAuction : dbAuction;
                resultList.add(convertToSummaryDTO(finalAuction));
            }

            // Sắp xếp hiển thị: RUNNING lên trước, OPEN ra sau
            resultList.sort((a, b) -> b.getStatus().compareTo(a.getStatus()));

        } catch (SQLException e) {
            System.err.println("[Central Guard] ❌ Lỗi kết nối Database khi tải danh sách: " + e.getMessage());

            // Fallback khẩn cấp: Nếu DB nghẽn, tạm thời đọc đỡ những gì RAM đang có để cứu vớt giao diện
            for (Auction ramAuction : auctionManage.getAllActive()) {
                resultList.add(convertToSummaryDTO(ramAuction));
            }
        }

        return resultList;
    }

    public void loadAuctionsToRAM() {
        List<AuctionStatus> activeStatuses = List.of(AuctionStatus.OPEN, AuctionStatus.RUNNING);

        // 🔥 SỬA TẠI ĐÂY: Chủ động mở kết nối vật lý ngắn hạn để truyền vào DAO
        try (Connection conn = com.auction.config.DatabaseConnection.getConnection()) {

            // Gọi DAO lấy danh sách phiên kèm theo Item đã nạp sẵn từ bên trong findByStatuses
            List<Auction> activeAuctionsFromDb = auctionDAO.findByStatuses(conn, activeStatuses);

            for (Auction auction : activeAuctionsFromDb) {
                itemDAO.findById(auction.getItemId()).ifPresent(auction::setItem);
                // Đẩy thẳng đối tượng sạch vào bộ đệm quản lý RAM
                auctionManage.addAuction(auction);
            }

            System.out.println("Hệ thống: Đã nạp thành công " + activeAuctionsFromDb.size() + " phiên đấu giá lên RAM.");

        } catch (SQLException e) {
            System.err.println("❌ Lỗi nghiêm trọng khi khởi động nạp dữ liệu lên RAM: " + e.getMessage());
        }
    }


    public AuctionDetailDTO getAuctionDetail(String auctionId) {
        if (auctionId == null || auctionId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Auction ID cannot be empty.");
        }

        Auction auction = getAuctionContext(auctionId);

        Item item = productManage.getProduct(auction.getItemId());
        if (item == null) {
            System.out.println("[AuctionService] 🔄 Product Cache Miss! Đang tái nạp vật phẩm " + auction.getItemId() + " từ DB...");
            item = itemDAO.findById(auction.getItemId()).orElse(null);
            if (item != null) {
                productManage.addProduct(item);
            }
        }

        List<BidTransaction> rawBidHistory = bidTransactionDAO.findTopByAuctionId(auctionId, 15);
        String sellerName = userDAO.findById(auction.getSellerId())
                .map(User::getUsername)
                .orElse("Người bán ẩn danh");

        return buildAuctionDetailDTO(auction, item, sellerName, rawBidHistory);
    }

    /**
     * 🔥 SMART CACHE GUARD: Trạm hồi sức cấp cứu nạp lại Cache (Read-Through)
     * Dùng nội bộ tại các cửa ngõ tiếp nhận Action (placeBid, joinLiveRoom...) để bốc Entity gốc.
     */
    private Auction getAuctionContext(String auctionId) {
        Auction auction = auctionManage.getAuctionById(auctionId);

        // Nếu RAM trống (do bị cơ chế Idle 10 phút trục xuất trước đó), tiến hành hồi sinh từ DB
        if (auction == null) {
            synchronized (auctionManage) { // Khóa an toàn đa luồng chặn nạp chồng
                auction = auctionManage.getAuctionById(auctionId);
                if (auction == null) {
                    System.out.println("[Cache Guard] 🔄 Phiên " + auctionId + " đã bị hạ tải. Đang nạp ngược từ DB...");

                    // Kéo Entity sạch từ DB lên
                    auction = auctionDAO.findById(auctionId).orElse(null);

                    if (auction != null) {
                        // Nạp item đi kèm cho đúng thiết kế mô hình
                        itemDAO.findById(auction.getItemId()).ifPresent(auction::setItem);

                        // Chỉ hồi sinh đưa lên bộ đếm giây của RAM nếu phiên vẫn còn hạn chạy (OPEN/RUNNING)
                        if (auction.getStatus() == AuctionStatus.OPEN || auction.getStatus() == AuctionStatus.RUNNING) {
                            auctionManage.addAuction(auction);
                        }
                    }
                }
            }
        }

        if (auction == null) {
            throw new AuctionException(AuctionErrorCode.AUCTION_NOT_FOUND);
        }
        return auction;
    }

    private AuctionDetailDTO buildAuctionDetailDTO(Auction auction, Item item, String sellerName, List<BidTransaction> rawHistory) {
        String itemName = (item != null) ? item.getName() : "Vật phẩm #" + auction.getItemId();
        String itemDesc = (item != null) ? item.getDescription() : "Không có mô tả";
        String itemImg = (item != null) ? item.getImageUrl() : "";

        List<BidTransactionDTO> historyDTOs = convertToBidHistoryDTO(rawHistory);

        return new AuctionDetailDTO(
                auction.getId(),
                auction.getCurrentPrice(),
                auction.getStepPrice(),
                auction.getEndTime(),
                auction.getStatus().name(),
                itemName,
                itemDesc,
                itemImg,
                sellerName,
                historyDTOs
        );
    }

    private List<BidTransactionDTO> convertToBidHistoryDTO(List<BidTransaction> rawHistory) {
        if (rawHistory == null || rawHistory.isEmpty()) {
            return new ArrayList<>();
        }

        return rawHistory.stream().map(bid -> {
            String bidderName = userDAO.findById(bid.getBidderId())
                    .map(User::getUsername)
                    .orElse("Người dùng ẩn danh");

            return new BidTransactionDTO(
                    bidderName,
                    bid.getAmount(),
                    bid.getTime(),
                    bid.getStatus().name()
            );
        }).collect(Collectors.toList());
    }

    public void cancelAuction(String auctionId, String adminId, String reason) {
        if (auctionId == null || auctionId.trim().isEmpty() || adminId == null || adminId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Auction ID and Admin ID cannot be empty.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "A valid reason must be provided for cancelling an auction.");
        }

        Auction auction = getAuctionContext(auctionId);

        synchronized (auction) {
            if (auction.getStatus() == AuctionStatus.FINISHED || auction.getStatus() == AuctionStatus.CANCELED) {
                throw new AuctionException(AuctionErrorCode.AUCTION_CLOSED);
            }

            // 🛠️ THAY ĐỔI CƠ CHẾ KẾT NỐI: Quản lý Connection và Transaction tập trung
            Connection conn = null;
            try {
                conn = com.auction.config.DatabaseConnection.getConnection();
                conn.setAutoCommit(false); // Bật Transaction

                // 1. Hoàn tiền đóng băng cho người dẫn đầu hiện tại (nếu có)
                String currentWinnerId = auction.getHighestBidderId();
                if (currentWinnerId != null) {
                    userDAO.unfreezeMoney(conn, currentWinnerId, auction.getCurrentPrice());
                    bidTransactionDAO.updateStatusToRefunded(conn, auctionId, currentWinnerId);
                }

                // 2. Cập nhật trạng thái DB
                auction.setStatus(AuctionStatus.CANCELED);
                auctionDAO.updateStatus(conn, auctionId, AuctionStatus.CANCELED.name());

                // Giải phóng Item trở lại trạng thái ACTIVE dưới DB
                itemDAO.updateStatus(conn, auction.getItemId(), com.auction.enums.ItemStatus.ACTIVE.name());

                // 3. ĐỒNG BỘ: Đưa log vào chung mạch Transaction để bảo vệ tính toàn vẹn dữ liệu
                String logId = UUID.randomUUID().toString();
                String actionDetail = "Cưỡng chế hủy phiên đấu giá bị hủy do: " + reason;
                logDAO.insertLog(conn, logId, adminId, actionDetail, "AUCTION", auctionId); // Đã truyền conn

                conn.commit(); // Chốt hạ lưu vĩnh viễn xuống đĩa cứng
                System.out.println("[DB Transaction] ✅ Đã hủy phiên đấu giá và ghi Audit Log thành công.");

            } catch (SQLException e) {
                // Trạm cứu hộ lỗi hạ tầng: Đảo ngược dữ liệu DB về trạng thái sạch nếu có biến cố
                if (conn != null) {
                    try {
                        conn.rollback();
                        System.err.println("[DB Transaction] ❌ Hủy phiên đấu giá thất bại, hệ thống đã rollback DB hoàn toàn!");
                    } catch (SQLException ex) {
                        System.err.println("[DB Transaction] 🚨 Lỗi khẩn cấp không thể rollback: " + ex.getMessage());
                    }
                }
                throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Cancellation transaction failed: " + e.getMessage());
            } finally {
                if (conn != null) {
                    try {
                        conn.setAutoCommit(true);
                        conn.close();
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
            }

            // =========================================================================
            // TIẾN TRÌNH ĐỒNG BỘ RAM VÀ BROADCAST (Chỉ chạy khi DB đã Commit an toàn)
            // =========================================================================
            var ramItem = productManage.getProduct(auction.getItemId());
            if (ramItem != null) {
                ramItem.setStatus(com.auction.enums.ItemStatus.ACTIVE);
            }

            Map<String, Object> cancelPayload = new HashMap<>();
            cancelPayload.put("newStatus", AuctionStatus.CANCELED.name());
            cancelPayload.put("message", "Phiên đấu giá đã bị Admin cưỡng chế hủy bỏ. Lý do: " + reason);

            AuctionEvent cancelEvent = new AuctionEvent(auctionId, AuctionEventType.STATUS_CHANGED, cancelPayload);
            AuctionEventBus.getInstance().publish(cancelEvent);
            System.out.println("[AuctionService] 📢 Publish STATUS_CHANGED (CANCELED) event");

            LiveRoomManage.getInstance().clearRoom(auctionId);
            auctionManage.removeAuctionById(auctionId);
        }
    }

    public void joinAuction(Bidder bidder, String auctionId) {
        if (bidder == null || auctionId == null || auctionId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Invalid Bidder session or Auction ID.");
        }
        Auction auction = auctionManage.getAuctionById(auctionId);
        if (auction == null) {
            auction = auctionDAO.findById(auctionId).orElse(null);
        }

        if (auction == null) {
            throw new AuctionException(AuctionErrorCode.AUCTION_NOT_FOUND);
        }

        if (!bidder.getJoinedAuctionIds().contains(auctionId)) {

            // 🛠️ THAY ĐỔI CƠ CHẾ KẾT NỐI: Mở Connection đơn lẻ để thực thi addJoinedAuction
            try (Connection conn = com.auction.config.DatabaseConnection.getConnection()) {
                boolean savedToDB = userDAO.addJoinedAuction(conn, bidder.getId(), auctionId); // Truyền conn
                if (!savedToDB) {
                    throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Failed to save joined auction to database.");
                }
            } catch (SQLException e) {
                throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Database link failed at joinAuction: " + e.getMessage());
            }

            synchronized (bidder.getId().intern()) {
                if (bidder.addJoinedAuction(auctionId)) {
                    System.out.println("[AuctionService] ✅ Bidder " + bidder.getUsername() + " đã đăng ký tracking phiên " + auctionId);
                } else {
                    System.out.println("[AuctionService] ⚠️ Bidder " + bidder.getUsername() + " tracking thất bại");
                }
            }

            Map<String, Object> subscribePayload = new HashMap<>();
            subscribePayload.put("username", bidder.getUsername());
            subscribePayload.put("message", "Bidder " + bidder.getUsername() + " đã đăng ký theo dõi phiên.");
            subscribePayload.put("viewerCount", LiveRoomManage.getInstance().getRoomSize(auctionId));

            AuctionEvent subscribeEvent = new AuctionEvent(auctionId, AuctionEventType.AUCTION_SUBSCRIBED, subscribePayload);
            AuctionEventBus.getInstance().publish(subscribeEvent);
            System.out.println("[AuctionService] 📢 Publish AUCTION_SUBSCRIBED event thành công lần đầu.");

        } else {
            System.out.println("[AuctionService] ℹ️ Bidder " + bidder.getUsername() + " đã tracking phiên này từ trước, bỏ qua phát sự kiện.");
        }
    }

    public void joinLiveRoom(Bidder bidder, String auctionId, ClientSession clientSession) {
        if (bidder == null || auctionId == null || auctionId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Invalid Bidder session or Auction ID.");
        }
        joinAuction(bidder, auctionId);

        LiveRoomManage.getInstance().joinRoom(auctionId, clientSession);
        System.out.println("[AuctionService] ✅ ClientSession của " + bidder.getUsername() + " đã được thêm vào phòng " + auctionId);

        int viewerCount = LiveRoomManage.getInstance().getRoomSize(auctionId);
        Map<String, Object> liveEnteredPayload = new HashMap<>();
        liveEnteredPayload.put("username", clientSession.getUsername());
        liveEnteredPayload.put("message", bidder.getUsername() + " vừa mở tab livestream.");
        liveEnteredPayload.put("viewerCount", viewerCount);

        AuctionEvent liveEnteredEvent = new AuctionEvent(auctionId, AuctionEventType.LIVE_ENTERED, liveEnteredPayload);
        AuctionEventBus.getInstance().publish(liveEnteredEvent);
        System.out.println("[AuctionService] 📢 Publish LIVE_ENTERED event. Viewer count: " + viewerCount);
    }

    public void leaveLiveRoom(Bidder bidder, String auctionId, ClientSession clientSession) {
        if (bidder == null || auctionId == null || auctionId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Invalid Bidder session or Auction ID.");
        }
        LiveRoomManage.getInstance().leaveRoom(auctionId, clientSession);
        System.out.println("[AuctionService] ❌ ClientSession của " + bidder.getUsername() + " đã rời khỏi phòng " + auctionId);

        int viewerCount = LiveRoomManage.getInstance().getRoomSize(auctionId);
        Map<String, Object> liveExitedPayload = new HashMap<>();
        liveExitedPayload.put("username", clientSession.getUsername());
        liveExitedPayload.put("message", bidder.getUsername() + " đã đóng tab livestream.");
        liveExitedPayload.put("viewerCount", viewerCount);

        AuctionEvent liveExitedEvent = new AuctionEvent(auctionId, AuctionEventType.LIVE_EXITED, liveExitedPayload);
        AuctionEventBus.getInstance().publish(liveExitedEvent);
        System.out.println("[AuctionService] 📢 Publish LIVE_EXITED event. Viewer count: " + viewerCount);
    }

    public void leaveAuction(Bidder bidder, String auctionId, ClientSession clientSession) {
        if (bidder == null || auctionId == null || auctionId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Invalid Bidder session or Auction ID.");
        }
        Auction auction = auctionManage.getAuctionById(auctionId);
        if (auction == null) {
            auction = auctionDAO.findById(auctionId).orElse(null);
        }

        if (auction != null) {
            boolean isLeadingBidder = bidder.getId().equals(auction.getHighestBidderId());
            boolean isAuctionRunning = auction.getStatus() == AuctionStatus.RUNNING;

            if (isLeadingBidder && isAuctionRunning) {
                throw new AuctionException(
                        AuctionErrorCode.CANNOT_UNWATCH_LEADING_AUCTION,
                        "Bạn không thể hủy theo dõi vì bạn là người dẫn đầu trên phiên này"
                );
            }
        }

        // 🛠️ THAY ĐỔI CƠ CHẾ KẾT NỐI: Mở kết nối tự động đóng phục vụ luồng xóa dữ liệu tracking con
        try (Connection conn = com.auction.config.DatabaseConnection.getConnection()) {

            // Truyền đối tượng conn dùng chung vào hàm xóa bảng trung gian của DAO
            userDAO.removeJoinedAuction(conn, bidder.getId(), auctionId);

        } catch (SQLException e) {
            // Hứng lỗi hạ tầng từ DAO ném lên để bảo vệ luồng chạy mạng vật lý
            throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Database link failed at leaveAuction: " + e.getMessage());
        }

        synchronized (bidder.getId().intern()) {
            bidder.removeJoinedAuction(auctionId);
        }
        System.out.println("[AuctionService] ✅ Bidder " + bidder.getUsername() + " đã hủy tracking phiên " + auctionId);

        if (clientSession != null) {
            LiveRoomManage.getInstance().leaveRoom(auctionId, clientSession);
            System.out.println("[AuctionService] ℹ️ Đã tháo Socket Session ra khỏi phòng Live của phiên " + auctionId);
        }

        int viewerCount = LiveRoomManage.getInstance().getRoomSize(auctionId);

        Map<String, Object> unsubscribePayload = new HashMap<>();
        unsubscribePayload.put("username", bidder.getUsername());
        unsubscribePayload.put("message", "Bidder " + bidder.getUsername() + " đã hủy theo dõi phiên.");
        unsubscribePayload.put("viewerCount", viewerCount);

        AuctionEvent unsubscribeEvent = new AuctionEvent(auctionId, AuctionEventType.AUCTION_UNSUBSCRIBED, unsubscribePayload);
        AuctionEventBus.getInstance().publish(unsubscribeEvent);
        System.out.println("[AuctionService] 📢 Publish AUCTION_UNSUBSCRIBED event");
    }
}