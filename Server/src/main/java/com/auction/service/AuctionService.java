package com.auction.service;

import com.auction.dao.*;
import com.auction.dao.impl.*;
import com.auction.dto.AuctionDetailDTO;
import com.auction.dto.AuctionSummaryDTO;
import com.auction.dto.BidTransactionDTO;
import com.auction.enums.AuctionStatus;
import com.auction.enums.BidStatus;
import com.auction.enums.ItemStatus;
import com.auction.exception.AuctionErrorCode;
import com.auction.exception.AuctionException;
import com.auction.exception.WalletErrorCode;
import com.auction.exception.WalletException;
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

        // Lấy đối tượng Live trên RAM để kiểm tra trạng thái tức thì
        Item liveItem = productManage.getProduct(itemId);
        if (liveItem == null) {
            // Nếu RAM chưa load, cố gắng tìm dưới DB
            liveItem = itemDAO.findById(itemId).orElse(null);
            if (liveItem == null) {
                throw new AuctionException(AuctionErrorCode.ITEM_NOT_FOUND);
            }
            // 🔥 NẠP LẠI VÀO RAM: Cất vào kho bộ đệm ProductManage
            productManage.addProduct(liveItem);
        }

        // BẢO VỆ ĐA LUỒNG: Đồng bộ hóa trên chính vật phẩm để chặn đứng spam click tạo 2 phòng song song
        synchronized (liveItem) {
            if (liveItem.getStatus() != ItemStatus.ACTIVE) {
                throw new AuctionException(AuctionErrorCode.ITEM_IS_LOCKED);
            }

            Auction newAuction = new Auction(liveItem, sellerId, stepPrice, startTime, endTime);
            boolean isSaved = auctionDAO.insertAuction(newAuction);

            if (!isSaved) {
                throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Failed to save new auction to the database.");
            }
            // 1. Đẩy lên RAM điều phối vòng đời đếm giờ công khai
            auctionManage.addAuction(newAuction);

            // 2. Đồng bộ chuyển trạng thái vật phẩm sang INACTIVE (Khóa kỹ thuật số)
            itemDAO.updateStatus(itemId, com.auction.enums.ItemStatus.INACTIVE.name());
            liveItem.setStatus(com.auction.enums.ItemStatus.INACTIVE);
        }
    }

    /**
     * Xử lý đặt giá (Process Bid)
     * Luồng: Lock Auction -> Check bước giá & Đóng băng tiền -> Gọi Model RAM thực thi -> Đồng bộ DB
     * 🔥 SỬA ĐỒNG BỘ: Đơn giản hóa cấu trúc code cực kỳ Clean nhờ tận dụng Exception từ Model gửi lên
     */
    public void processBid(Bidder bidder, String auctionId, double amount) {
        Auction auction = auctionManage.getAuctionById(auctionId);

        // 🔥 TÁI NẠP BỘ ĐỆM: Nếu phiên trên RAM bị hạ tải, hồi sinh nó lên RAM
        if (auction == null) {
            auction = auctionDAO.findById(auctionId).orElse(null);
            if (auction != null) {
                itemDAO.findById(auction.getItemId()).ifPresent(auction::setItem);
                auctionManage.addAuction(auction);
            } else {
                throw new AuctionException(AuctionErrorCode.AUCTION_NOT_FOUND);
            }
        }

        // Chốt chặn trạng thái online vật lý
        if (!connectionManage.isUserOnline(bidder.getId())) {
            throw new AuctionException(AuctionErrorCode.BIDDER_NOT_ONLINE);
        }

        // Khóa đúng đối tượng auction để bảo đảm tính tuần tự tuyệt đối
        synchronized (auction) {
            // ⚠️ MẸO HIỆU NĂNG TỐI ƯU: Đọc nhanh giá đỉnh hiện tại trên RAM để chặn sớm trước khi gọi sang DB
            if (amount < auction.getCurrentPrice() + auction.getStepPrice()) {
                throw new AuctionException(AuctionErrorCode.BID_AMOUNT_TOO_LOW);
            }

            // Lưu thông tin người dẫn đầu cũ để hoàn trả tài sản sau
            String oldHighestBidderId = auction.getHighestBidderId();
            double oldPrice = auction.getCurrentPrice();

            // 1. Thực hiện đóng băng tài sản của người đặt giá mới dưới Database (Atomic Update)
            boolean freezeSuccess = userDAO.freezeMoney(bidder.getId(), amount);
            if (!freezeSuccess) {
                throw new WalletException(WalletErrorCode.INSUFFICIENT_BALANCE);
            }

            try {
                String newBidId = UUID.randomUUID().toString();

                // 2. Gọi mô hình RAM thực hiện kiểm tra sâu và tiến hành đột biến dữ liệu.
                // Nếu Model phát hiện ra lỗi (vừa hết giờ xong trong tích tắc), nó sẽ ném văng Exception tại đây.
                BidTransaction resultBid = auction.placeBid(bidder, amount, newBidId);

                // Nếu dòng code chạy được xuống đến đây -> Chắc chắn 100% lượt Đặt giá đã thành công trên RAM
                synchronized (bidder.getId().intern()) {
                    bidder.freeze(amount);
                }

                // 3. Đồng bộ hóa dữ liệu xuống Database cho phiên đấu giá
                boolean updateAuctionDB = auctionDAO.updatePriceAndWinner(
                        auctionId, amount, bidder.getId(), newBidId
                );

                if (!updateAuctionDB) {
                    // Nếu lỗi ghi DB, chủ động ném lỗi để đẩy luồng xuống khối catch xử lý Rollback hoàn tiền
                    throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Sync price to database failed.");
                }

                // 4. Ghi biên lai giao dịch thành công xuống bảng lịch sử bid_transactions
                bidTransactionDAO.insertBid(resultBid);

                // 5. Giải hoàn tiền đóng băng cho người bị Outbid (Người dẫn đầu cũ)
                if (oldHighestBidderId != null && !oldHighestBidderId.equals(bidder.getId())) {
                    userDAO.unfreezeMoney(oldHighestBidderId, oldPrice);

                    // Chuyển biên lai cũ của người bị vượt giá thành REFUNDED dưới DB
                    bidTransactionDAO.updateStatusToRefunded(auctionId, oldHighestBidderId);
                }

                // Tự động đưa phiên này vào danh sách theo dõi của Bidder (nếu chưa có)
                if (!bidder.getJoinedAuctionIds().contains(auctionId)) {
                    userDAO.addJoinedAuction(bidder.getId(), auctionId);
                    synchronized (bidder.getId().intern()) {
                        bidder.addJoinedAuction(auctionId);
                    }
                }

                // 6. Đóng gói Payload phản hồi gộp duy nhất 1 gói tin truyền tải qua mạng
                BidTransactionDTO bidData = new BidTransactionDTO(
                        bidder.getUsername(),
                        amount,
                        LocalDateTime.now(),
                        BidStatus.ACCEPTED.name()
                );

                AuctionEvent bidEvent = new AuctionEvent(
                        auctionId,
                        AuctionEventType.NEW_BID,
                        bidData
                );
                AuctionEventBus.getInstance().publish(bidEvent);
                System.out.println("[AuctionService] 📢 Đặt giá thành công. Broadcast NEW_BID event.");

            } catch (Exception e) {
                // 🔥 ROLLBACK BẢO VỆ TUYỆT ĐỐI: Bất kể lỗi xảy ra do logic RAM từ chối hay lỗi nghẽn DB,
                // hệ thống lập tức mở khóa trả lại tiền nguyên vẹn cho Bidder trong Database và RAM
                userDAO.unfreezeMoney(bidder.getId(), amount);
                synchronized (bidder.getId().intern()) {
                    bidder.unfreeze(amount);
                }

                // Nếu là lỗi chúng ta chủ động quản lý -> ném tiếp lên cho RequestDispatcher xử lý
                if (e instanceof com.auction.exception.BaseException) {
                    throw e;
                }
                // Nếu là lỗi hệ thống không lường trước (NullPointer, SQL crash) -> bọc lại thành lỗi DB
                throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Fatal crash during processBid: " + e.getMessage());
            }
        }
    }

    /**
     * Kết thúc phiên đấu giá (Finalize Auction)
     * Luồng: Deduct Frozen tiền người thắng -> Add Available tiền người bán -> Update Status
     */
    public void finalizeAuction(String auctionId) {
        Auction auction = auctionManage.getAuctionById(auctionId);
        if (auction == null) return;

        synchronized (auction) {
            String winnerId = auction.getHighestBidderId();
            double finalPrice = auction.getCurrentPrice();
            String sellerId = auction.getSellerId();

            String statusMessage;

            if (winnerId != null) {
                // 1. Khấu trừ vĩnh viễn tiền trong cột đóng băng của người thắng
                boolean deductOk = userDAO.deductFrozenMoney(winnerId, finalPrice);
                if (deductOk) {
                    // 2. Cộng tiền vào cột khả dụng cho người bán
                    userDAO.addAvailableBalance(sellerId, finalPrice);
                }
                else {
                    throw new WalletException(WalletErrorCode.DEDUCTION_FAILED);
                }
                statusMessage = "Thông báo: Phiên " + auctionId + " ĐÃ KẾT THÚC. Người thắng: ID " + winnerId + " với giá: " + finalPrice;
                // 🔥 SỬA BỔ SUNG: Chuyển trạng thái Item thành SOLD (Đã bán)
                itemDAO.updateStatus(auction.getItemId(), com.auction.enums.ItemStatus.SOLD.name());
                productManage.getProduct(auction.getItemId()).setStatus(com.auction.enums.ItemStatus.SOLD);
            } else {
                statusMessage = "Thông báo: Phiên " + auctionId + " ĐÃ KẾT THÚC. Không có người đặt giá.";
                // 🔥 SỬA BỔ SUNG: Không ai mua -> Trả tự do cho Item thành ACTIVE
                itemDAO.updateStatus(auction.getItemId(), com.auction.enums.ItemStatus.ACTIVE.name());
                var ramItem = productManage.getProduct(auction.getItemId());
                if (ramItem != null) {
                    ramItem.setStatus(com.auction.enums.ItemStatus.ACTIVE);
                }

            }

            // 3. Cập nhật trạng thái kết thúc trong DB
            auctionDAO.updateStatus(auctionId, AuctionStatus.FINISHED.name());

            // 4. THAY ĐỔI TẠI ĐÂY: Tinh gọn payload, loại bỏ highestId và highestPrice ra khỏi sự kiện vòng đời phòng
            Map<String, Object> statusPayload = new HashMap<>();
            statusPayload.put("newStatus", AuctionStatus.FINISHED.name());
            statusPayload.put("message", statusMessage);

            AuctionEvent statusEvent = new AuctionEvent(
                    auctionId,
                    AuctionEventType.STATUS_CHANGED,
                    statusPayload
            );
            AuctionEventBus.getInstance().publish(statusEvent);

            // 5. Xóa phòng (không ai cần broadcast nữa)
            LiveRoomManage.getInstance().clearRoom(auctionId);
        }
    }

    //Từ userId -> Tìm các auctionId -> Tìm từng Auction -> Đóng gói thành AuctionSummaryDTO -> giúp hiển thị các auction theo dỗi
    //Xây xog DAO quay lại mở khoá
    // 3. [HÀM CỦA BIDDER] - Lấy danh sách các phiên đang tham gia để hiện lên JavaFX
    public List<AuctionSummaryDTO> getJoinedAuctionsSummary(Bidder bidder) {
        List<AuctionSummaryDTO> summaries = new ArrayList<>();
        List<String> joinedIds = bidder.getJoinedAuctionIds();

        for (String id : joinedIds) {
            // Ưu tiên tìm trên RAM (Những phiên đang ACTIVE)
            Auction auction = auctionManage.getAuctionById(id);

            // Nếu không thấy trên RAM, tìm trong DB (Những phiên đã FINISHED)
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
        // Nếu Item chưa được load (transient), bạn có thể load bổ sung ở đây
        String itemName = (auction.getItem() != null) ? auction.getItem().getName() : "Vật phẩm #" + auction.getItemId();

        return new AuctionSummaryDTO(
                auction.getId(),
                itemName,
                auction.getCurrentPrice(),
                auction.getStatus().name(),
                auction.getEndTime()
        );
    }

    // 4. Lấy danh sách TẤT CẢ phiên đang chạy (Để hiển thị trang chủ)
    public List<AuctionSummaryDTO> getAllActiveAuctions() {
        return auctionManage.getAllActive().stream()
                .filter(a -> a.getStatus() == AuctionStatus.RUNNING || a.getStatus() == AuctionStatus.OPEN)
                .map(this::convertToSummaryDTO)
                .collect(Collectors.toList());
    }

    /**
     * Load toàn bộ phiên đang OPEN hoặc RUNNING từ DB lên RAM khi khởi động Server.
     */
    public void loadAuctionsToRAM() {
        List<AuctionStatus> activeStatuses = List.of(AuctionStatus.OPEN, AuctionStatus.RUNNING);
        List<Auction> activeAuctionsFromDb = auctionDAO.findByStatuses(activeStatuses);

        for (Auction auction : activeAuctionsFromDb) {
            // Cần đảm bảo Item được nạp vào Object Auction
            itemDAO.findById(auction.getItemId()).ifPresent(auction::setItem);

            // Đẩy lên RAM để AuctionManage tiếp quản đếm giờ
            auctionManage.addAuction(auction);
        }
        System.out.println("Hệ thống: Đã nạp " + activeAuctionsFromDb.size() + " phiên đấu giá lên RAM.");
    }

    //Hàm Lấy Chi Tiết Phiên đấu giá (Để hiển thị trang chi tiết)
    public AuctionDetailDTO getAuctionDetail(String auctionId) {
        // 1. Lấy dữ liệu thô (Models) từ DB/RAM
        Auction auction = auctionManage.getAuctionById(auctionId); // Ưu tiên tìm trên RAM trước

        // 2. NẾU RAM KHÔNG CÓ (Cache Miss - do đã bị trục xuất sau 10 phút)
        if (auction == null) {
            System.out.println("[Cache] 🔄 Phiên " + auctionId + " đã bị hạ tải. Đang nạp lại từ DB...");

            // Xuống DB tìm lại thực thể gốc
            auction = auctionDAO.findById(auctionId).orElse(null);

            if (auction != null) {
                // 🔥 THAY ĐỔI: Gộp và làm sạch đoạn nạp Item + Đánh thức Auction lên RAM (Tránh trùng lặp code cũ)
                Item itemFromDb = itemDAO.findById(auction.getItemId()).orElse(null);
                auction.setItem(itemFromDb);

                // 🔥 ĐÁNH THỨC: Nạp lại vào RAM để tiếp tục làm bộ đệm và quản lý thời gian
                auctionManage.addAuction(auction);
            }
        }

        if (auction == null) {
            throw new AuctionException(AuctionErrorCode.AUCTION_NOT_FOUND);
        } // Phiên không tồn tại thật sự trong DB

        // 🔥 THAY ĐỔI: Đồng bộ lấy Item từ ProductManage (RAM) trước thay vì ép xuống DB ngay lập tức
        // Điều này giúp tận dụng tối đa bộ đệm ConcurrentHashMap của ProductManage
        Item item = productManage.getProduct(auction.getItemId());
        if (item == null) {
            // Nếu bộ đệm RAM của sản phẩm bị dọn dẹp mất, ta chủ động đọc từ DB lên rồi cất ngược lại vào RAM
            System.out.println("[AuctionService] 🔄 Product Cache Miss! Đang tái nạp vật phẩm " + auction.getItemId() + " từ DB...");
            item = itemDAO.findById(auction.getItemId()).orElse(null);
            if (item != null) {
                productManage.addProduct(item);
            }
        }

        // Lấy các thông tin liên quan
        List<BidTransaction> rawBidHistory = bidTransactionDAO.findTopByAuctionId(auctionId, 15);
        String sellerName = userDAO.findById(auction.getSellerId())
                .map(User::getUsername)
                .orElse("Người bán ẩn danh");

        // 2. Gọi Helper để đóng gói và trả về
        return buildAuctionDetailDTO(auction, item, sellerName, rawBidHistory);
    }

    /**
     * Helper 1: Lắp ráp các thành phần lại thành AuctionDetailDTO
     */
    private AuctionDetailDTO buildAuctionDetailDTO(Auction auction, Item item, String sellerName, List<BidTransaction> rawHistory) {

        // An toàn kiểm tra Item null (phòng trường hợp data lỗi)
        String itemName = (item != null) ? item.getName() : "Vật phẩm #" + auction.getItemId();
        String itemDesc = (item != null) ? item.getDescription() : "Không có mô tả";
        String itemImg = (item != null) ? item.getImageUrl() : "";

        // Đổi List Model sang List DTO thông qua Helper 2
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

    /**
     * Helper 2: Xử lý riêng việc convert danh sách Bid
     */
    private List<BidTransactionDTO> convertToBidHistoryDTO(List<BidTransaction> rawHistory) {
        if (rawHistory == null || rawHistory.isEmpty()) {
            return new ArrayList<>();
        }

        return rawHistory.stream().map(bid -> {
            // Lấy tên người bid (Nếu muốn tối ưu hiệu năng hơn, có thể dùng câu JOIN SQL ở DAO,
            // nhưng với Top 15 thì gọi DB ở đây vẫn chấp nhận được)
            // 🔥 AN TOÀN SỬA ĐỔI: Sử dụng map/orElse để giải quyết Warning Optional.get()
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

    /**
     * Hủy phiên đấu giá ngay lập tức và giải phóng tiền cho người đang dẫn đầu.
     */
    public void cancelAuction(String auctionId, String adminId, String reason) {
        Auction auction = auctionManage.getAuctionById(auctionId);
        if (auction == null || adminId == null) throw new AuctionException(AuctionErrorCode.AUCTION_NOT_FOUND);

        synchronized (auction) {
            // Chống việc hủy một phiên đã đóng từ trước
            if (auction.getStatus() == AuctionStatus.FINISHED || auction.getStatus() == AuctionStatus.CANCELED) {
                throw new AuctionException(AuctionErrorCode.AUCTION_CLOSED);
            }

            // 1. Hoàn tiền đóng băng cho người dẫn đầu hiện tại (nếu có)
            String currentWinnerId = auction.getHighestBidderId();
            if (currentWinnerId != null) {
                userDAO.unfreezeMoney(currentWinnerId, auction.getCurrentPrice());

                // 🔥 BỔ SUNG TẠI ĐÂY: Người dẫn đầu bị hủy phiên -> Hoàn tiền -> Đổi trạng thái sang REFUNDED
                bidTransactionDAO.updateStatusToRefunded(auctionId, currentWinnerId);
            }

            // 2. Cập nhật trạng thái DB và RAM
            auction.setStatus(AuctionStatus.CANCELED);
            auctionDAO.updateStatus(auctionId, AuctionStatus.CANCELED.name());

            // 🔥 SỬA BỔ SUNG: Giải phóng Item trở lại trạng thái ACTIVE
            itemDAO.updateStatus(auction.getItemId(), com.auction.enums.ItemStatus.ACTIVE.name());
            var ramItem = productManage.getProduct(auction.getItemId());
            if (ramItem != null) {
                ramItem.setStatus(com.auction.enums.ItemStatus.ACTIVE);
            }

            // 🔥 THAY ĐỔI TẠI ĐÂY: Tinh gọn payload khi hủy phiên, gỡ bỏ thông tin tài chính dư thừa
            Map<String, Object> cancelPayload = new HashMap<>();
            cancelPayload.put("newStatus", AuctionStatus.CANCELED.name());
            cancelPayload.put("message", "Phiên đấu giá đã bị Admin cưỡng chế hủy bỏ. Lý do: " + reason);

            AuctionEvent cancelEvent = new AuctionEvent(
                    auctionId,
                    AuctionEventType.STATUS_CHANGED,
                    cancelPayload
            );
            AuctionEventBus.getInstance().publish(cancelEvent);
            System.out.println("[AuctionService] 📢 Publish STATUS_CHANGED (CANCELED) event");

            AuctionEventBus.getInstance().publish(cancelEvent);

            // 4. Xóa phòng
            LiveRoomManage.getInstance().clearRoom(auctionId);

            // 5. Xóa khỏi RAM
            auctionManage.removeAuctionById(auctionId);

            // 🔥 BƯỚC CỐT LÕI: GHI NHẬT KÝ HÀNH ĐỘNG HỦY PHIÊN CỦA ADMIN XUỐNG DB
            String logId = UUID.randomUUID().toString();
            String actionDetail = "Cưỡng chế hủy phiên đấu giá bị hủy do: " + reason;
            logDAO.insertLog(logId, adminId, actionDetail, "AUCTION", auctionId);
        }
    }

    /**
     * Bidder join vào phiên đấu giá để tracking + receive notifications
     * Luồng: -> chỉ ghi ấn nút tham gia phiên
     * 1. Lưu vào DB bảng bidder_joined_auctions (persist dữ liệu)
     * 2. Cập nhật RAM của Bidder (joinedAuctionIds)
     * 3. Thêm ClientSession vào LiveRoom (để receive real-time broadcasts)
     *
     * @param bidder Người dùng join
     * @param auctionId Phiên cần join
     * @param clientSession Kết nối socket để gửi real-time notifications
     */
    public void joinAuction(Bidder bidder, String auctionId, ClientSession clientSession) {
        Auction auction = auctionManage.getAuctionById(auctionId);
        if (auction == null) {
            auction = auctionDAO.findById(auctionId).orElse(null);
        }

        if (auction == null) {
            throw new AuctionException(AuctionErrorCode.AUCTION_NOT_FOUND);
        }

        // Phòng trường hợp User đã bấm Tham gia rồi nhưng click lại hoặc kết nối lại Socket
        if (!bidder.getJoinedAuctionIds().contains(auctionId)) {
            // 1. Lưu vào DB (bảng trung gian: bidder_joined_auctions)
            boolean savedToDB = userDAO.addJoinedAuction(bidder.getId(), auctionId);
            if (!savedToDB) {
                throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Failed to save joined auction to database.");
            }

            // 2. Cập nhật RAM của Bidder
            synchronized (bidder.getId().intern()) {
                if (bidder.addJoinedAuction(auctionId)) {
                    System.out.println("✅ Bidder " + bidder.getUsername() + " đã join phiên " + auctionId);
                }
                System.out.println("Join không thành công");
            }
        }

        // 3. Thêm vào LiveRoom để nhận broadcasts real-time
        LiveRoomManage.getInstance().joinRoom(auctionId, clientSession);

        // 4. Broadcast thông báo có người join mới
        String joinMsg = "Thông báo: " + bidder.getUsername() + " đã tham gia phiên";
        LiveRoomManage.getInstance().broadcast(auctionId, joinMsg);
    }

    /**
     * THAY ĐỔI: Người dùng ĐÓNG TAB CHI TIẾT (Chỉ thoát giao diện và ngắt socket)
     * Tuyệt đối không xóa dữ liệu theo dõi hoặc tiền của họ trong DB/RAM.
     */
    public void leaveLiveRoom(Bidder bidder, String auctionId, ClientSession clientSession) {
        // Chỉ dọn dẹp liên kết socket, giữ nguyên tính toàn vẹn dữ liệu tài chính
        LiveRoomManage.getInstance().leaveRoom(auctionId, clientSession);

        String leaveMsg = "Thông báo: " + bidder.getUsername() + " đã thoát màn hình xem trực tuyến.";
        LiveRoomManage.getInstance().broadcast(auctionId, leaveMsg);
    }

    /**
     * THAY ĐỔI: BỔ SUNG THÊM NGIỆP VỤ MỚI
     * Người dùng chủ động bấm nút "Hủy theo dõi" (Unwatch) trên màn hình "Phiên của tôi"
     */
    public void unwatchAuction(Bidder bidder, String auctionId) {
        Auction auction = auctionManage.getAuctionById(auctionId);
        if (auction == null) {
            auction = auctionDAO.findById(auctionId).orElse(null);
        }

        if (auction != null) {
            // LUẬT: Nếu đang là người dẫn đầu, tuyệt đối không được phép bỏ cuộc/ẩn danh sách!
            if (bidder.getId().equals(auction.getHighestBidderId()) && auction.getStatus() == AuctionStatus.RUNNING) {
                throw new AuctionException(AuctionErrorCode.CANNOT_UNWATCH_LEADING_AUCTION);
            }
        }

        // Thực hiện xóa khỏi DB và RAM giám sát của User
        userDAO.removeJoinedAuction(bidder.getId(), auctionId);
        synchronized (bidder.getId().intern()) {
            bidder.removeJoinedAuction(auctionId);
        }
        System.out.println("✅ Bidder " + bidder.getUsername() + " đã ngừng theo dõi phiên " + auctionId);
    }
}

