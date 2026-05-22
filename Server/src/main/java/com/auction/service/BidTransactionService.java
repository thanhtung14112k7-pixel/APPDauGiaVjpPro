package com.auction.service;

import com.auction.dao.BidTransactionDAO;
import com.auction.dao.UserDAO;
import com.auction.dao.impl.BidTransactionDAOImpl;
import com.auction.dao.impl.UserDAOImpl;
import com.auction.dto.BidTransactionDTO;
import com.auction.dto.PageDTO;
import com.auction.exception.AuctionErrorCode;
import com.auction.exception.AuctionException;
import com.auction.exception.ValidationErrorCode;
import com.auction.exception.ValidationException;
import com.auction.models.Auction.BidTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BidTransactionService {
    private final BidTransactionDAO bidTransactionDAO = new BidTransactionDAOImpl();
    private final UserDAO userDAO = new UserDAOImpl();

    /**
     * Ghi nhận lượt đặt giá mới vào hệ thống
     */
    public void recordNewBid(BidTransaction bid) {
        if (bid == null) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Bid transaction data must not be null.");
        }
        boolean isSaved = bidTransactionDAO.insertBid(bid);
        if (!isSaved) {
            throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Failed to persist bid transaction.");
        }
    }

    /**
     * LẤY LỊCH SỬ ĐẶT GIÁ THEO PHIÊN (PHÂN TRANG TRỌN GÓI)
     */
    public PageDTO<BidTransactionDTO> getAuctionBidsPaged(String auctionId, int page, int pageSize) {
        if (auctionId == null || auctionId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Auction ID is required.");
        }
        if (page <= 0 || pageSize <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Page index and size must be greater than 0.");
        }

        int offset = (page - 1) * pageSize;

        List<BidTransaction> rawBids = bidTransactionDAO.findByAuctionIdPaged(auctionId, pageSize, offset);
        List<BidTransactionDTO> dtoList = convertToTransactionDTOs(rawBids);

        long totalElements = bidTransactionDAO.getTotalBidCountByAuction(auctionId);
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        return new PageDTO<>(dtoList, page, totalPages, totalElements);
    }

    /**
     * LẤY LỊCH SỬ ĐI ĐẤU GIÁ CỦA MỘT BIDDER (PHÂN TRANG TRỌN GÓI)
     */
    public PageDTO<BidTransactionDTO> getBidderHistoryPaged(String bidderId, int page, int pageSize) {
        if (bidderId == null || bidderId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Bidder ID is required.");
        }
        if (page <= 0 || pageSize <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Page index and size must be greater than 0.");
        }

        int offset = (page - 1) * pageSize;

        List<BidTransaction> rawBids = bidTransactionDAO.findByBidderIdPaged(bidderId, pageSize, offset);
        List<BidTransactionDTO> dtoList = convertToTransactionDTOs(rawBids);

        long totalElements = bidTransactionDAO.getTotalBidCountByBidder(bidderId);
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        return new PageDTO<>(dtoList, page, totalPages, totalElements);
    }

    private List<BidTransactionDTO> convertToTransactionDTOs(List<BidTransaction> rawBids) {
        if (rawBids == null || rawBids.isEmpty()) return new ArrayList<>();

        List<String> bidderIds = rawBids.stream()
                .map(BidTransaction::getBidderId)
                .distinct()
                .toList();

        Map<String, String> userMap = bidderIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> userDAO.findById(id)
                                .map(com.auction.models.User.User::getUsername)
                                .orElse("Người dùng ẩn danh")
                ));

        return rawBids.stream().map(bid -> new BidTransactionDTO(
                userMap.getOrDefault(bid.getBidderId(), "Người dùng ẩn danh"),
                bid.getAmount(),
                bid.getTime(),
                bid.getStatus().name()
        )).collect(Collectors.toList());
    }
}