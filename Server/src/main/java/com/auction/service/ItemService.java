package com.auction.service;

import com.auction.dao.ItemDAO;
import com.auction.dao.impl.ItemDAOImpl;
import com.auction.dto.ItemSummaryDTO;
import com.auction.enums.ItemStatus;
import com.auction.exception.AuctionErrorCode;
import com.auction.exception.AuctionException;
import com.auction.exception.ValidationErrorCode;
import com.auction.exception.ValidationException;
import com.auction.manage.ProductManage;
import com.auction.models.Item.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ItemService {
    private final ItemDAO itemDAO = new ItemDAOImpl();
    private final ProductManage productManage = ProductManage.getInstance();

    /**
     * 1. [HÀM CHUNG] - THÊM VẬT PHẨM MỚI
     */
    public void addItem(String type, Map<String, Object> data) {
        if (type == null || data == null) {
            throw new ValidationException(ValidationErrorCode.BAD_REQUEST, "Missing type or parameter payload data.");
        }

        try {
            data.put("id", UUID.randomUUID().toString());
            data.put("status", ItemStatus.ACTIVE);
            data.put("createdAt", LocalDateTime.now());

            Item newItem = ItemFactory.createItem(type, data);

            boolean isSavedDB = itemDAO.insertItem(newItem);
            if (!isSavedDB) {
                throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Persisting new item failed.");
            }
            productManage.addProduct(newItem);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Factory payload evaluation error: " + e.getMessage());
        }
    }

    /**
     * 2. [HÀM CHUNG] - CHỈNH SỬA THÔNG TIN VẬT PHẨM
     */
    public void updateItemInfo(String itemId, String type, Map<String, Object> incomingData) {
        if (itemId == null || type == null || incomingData == null) {
            throw new ValidationException(ValidationErrorCode.BAD_REQUEST, "Invalid update request mapping criteria.");
        }

        Item liveItem = getItemById(itemId);
        if (liveItem == null) {
            throw new AuctionException(AuctionErrorCode.ITEM_NOT_FOUND);
        }

        synchronized (liveItem.getId().intern()) {
            if (liveItem.getStatus() != ItemStatus.ACTIVE) {
                throw new AuctionException(AuctionErrorCode.ITEM_IS_LOCKED);
            }

            updateLiveItemFields(liveItem, incomingData);
            boolean isUpdatedDB = itemDAO.updateItem(liveItem);

            if (!isUpdatedDB) {
                throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Synchronizing modified item properties to store failed.");
            }
            productManage.updateProduct(itemId, liveItem);
        }
    }

    /**
     * 3. [HÀM CỦA SELLER] - Lấy danh sách vật phẩm dạng DTO siêu nhẹ đổ lên JavaFX
     */
    public List<ItemSummaryDTO> getSellerItems(String sellerId) {
        if (sellerId == null || sellerId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Seller identification constraint is empty.");
        }

        List<Item> dbItems = itemDAO.findBySellerId(sellerId);
        List<ItemSummaryDTO> result = new ArrayList<>();

        for (Item item : dbItems) {
            Item ramItem = productManage.getProduct(item.getId());

            if (ramItem == null) {
                productManage.addProduct(item);
                ramItem = item;
            }

            result.add(new ItemSummaryDTO(
                    item.getId(),
                    item.getName(),
                    item.getStartingPrice(),
                    item.getItemType().name(),
                    ramItem.getStatus().name()
            ));
        }
        return result;
    }

    /**
     * 4. [HÀM CỦA SELLER] - Lấy chi tiết toàn bộ một vật phẩm
     */
    public Item getDetailedItem(String itemId) {
        if (itemId == null || itemId.trim().isEmpty()) {
            throw new ValidationException(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Item criteria constraint target is empty.");
        }
        Item item = getItemById(itemId);
        if (item == null) {
            throw new AuctionException(AuctionErrorCode.ITEM_NOT_FOUND);
        }
        return item;
    }

    /**
     * 5. [HÀM HỆ THỐNG] - Cập nhật trạng thái nhanh (Internal Trigger)
     */
    public void updateItemStatus(String itemId, ItemStatus newStatus) {
        if (itemId == null || newStatus == null) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Trường cập nhật trạng thái không hợp lệ.");
        }

        boolean isUpdatedDB = itemDAO.updateStatus(itemId, newStatus.name());
        if (!isUpdatedDB) {
            throw new AuctionException(AuctionErrorCode.DATABASE_ERROR, "Failed to update item status.");
        }

        Item ramItem = productManage.getProduct(itemId);
        if (ramItem == null) {
            itemDAO.findById(itemId).ifPresent(productManage::addProduct);
        } else {
            ramItem.setStatus(newStatus);
        }
    }

    private Item getItemById(String itemId) {
        Item item = productManage.getProduct(itemId);
        if (item == null) {
            item = itemDAO.findById(itemId).orElse(null);
            if (item != null) {
                productManage.addProduct(item);
            }
        }
        return item;
    }

    private void updateLiveItemFields(Item liveItem, Map<String, Object> incomingData) {
        if (incomingData.containsKey("name")) liveItem.setName((String) incomingData.get("name"));
        if (incomingData.containsKey("description")) liveItem.setDescription((String) incomingData.get("description"));
        if (incomingData.containsKey("imageUrl")) liveItem.setImageUrl((String) incomingData.get("imageUrl"));
        if (incomingData.containsKey("startingPrice")) {
            liveItem.setStartingPrice(Double.parseDouble(incomingData.get("startingPrice").toString()));
        }
        if (incomingData.containsKey("yearCreated")) {
            liveItem.setYearCreated(Integer.parseInt(incomingData.get("yearCreated").toString()));
        }

        switch (liveItem) {
            case Art art -> {
                if (incomingData.containsKey("painter")) art.setPainter((String) incomingData.get("painter"));
                if (incomingData.containsKey("artStyle")) art.setArtStyle((String) incomingData.get("artStyle"));
            }
            case Electronics elec -> {
                if (incomingData.containsKey("brand")) elec.setBrand((String) incomingData.get("brand"));
                if (incomingData.containsKey("warrantyMonths")) {
                    elec.setWarrantyMonths(Integer.parseInt(incomingData.get("warrantyMonths").toString()));
                }
            }
            case Vehicle vehicle -> {
                if (incomingData.containsKey("model")) vehicle.setModel((String) incomingData.get("model"));
                if (incomingData.containsKey("engineType")) vehicle.setEngineType((String) incomingData.get("engineType"));
                if (incomingData.containsKey("licensePlate")) vehicle.setLicensePlate((String) incomingData.get("licensePlate"));
                if (incomingData.containsKey("kmAge")) {
                    vehicle.setKmAge(Double.parseDouble(incomingData.get("kmAge").toString()));
                }
            }
            default -> {}
        }
    }
}