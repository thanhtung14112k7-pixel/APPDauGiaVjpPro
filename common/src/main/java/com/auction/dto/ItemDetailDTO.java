package com.auction.dto; // Dat DTO trong common de Client va Server dung chung

import java.io.Serial;
import java.io.Serializable; // Cho phep DTO an toan neu sau nay can truyen/luu theo dang serialize
import java.time.LocalDateTime; // Dung de tra thoi diem tao san pham theo kieu du lieu ro rang

public class ItemDetailDTO implements Serializable { // DTO Server tra ve khi Client can xem/sua chi tiet san pham
    @Serial
    private static final long serialVersionUID = 1L; // Giu version on dinh cho DTO serialize

    private String itemId; // Id san pham
    private String itemName; // Ten san pham
    private Double startingPrice; // Gia khoi diem
    private String itemType; // Loai san pham chuan: ART, ELECTRONICS, VEHICLES
    private String status; // Trang thai san pham: ACTIVE, INACTIVE, SOLD
    private String description; // Mo ta san pham
    private Integer yearCreated; // Nam tao/san xuat
    private String imageUrl; // Anh dai dien san pham
    private String sellerId; // Id nguoi ban so huu san pham
    private LocalDateTime createdAt; // Thoi diem san pham duoc tao
    private String painter; // Field rieng cho ART
    private String artStyle; // Field rieng cho ART
    private String brand; // Field rieng cho ELECTRONICS
    private Integer warrantyMonths; // Field rieng cho ELECTRONICS
    private String model; // Field rieng cho VEHICLES
    private String engineType; // Field rieng cho VEHICLES
    private String licensePlate; // Field rieng cho VEHICLES
    private Double kmAge; // Field rieng cho VEHICLES

    public ItemDetailDTO() { // Constructor rong bat buoc de Gson parse JSON thanh DTO
    } // Ket thuc constructor rong

    public ItemDetailDTO(String itemId, String itemName, Double startingPrice, String itemType, String status, String description, Integer yearCreated, String imageUrl, String sellerId, LocalDateTime createdAt, String painter, String artStyle, String brand, Integer warrantyMonths, String model, String engineType, String licensePlate, Double kmAge) { // Constructor day du de Server tao response chi tiet
        this.itemId = itemId; // Luu id san pham
        this.itemName = itemName; // Luu ten san pham
        this.startingPrice = startingPrice; // Luu gia khoi diem
        this.itemType = itemType; // Luu loai san pham
        this.status = status; // Luu trang thai san pham
        this.description = description; // Luu mo ta san pham
        this.yearCreated = yearCreated; // Luu nam tao/san xuat
        this.imageUrl = imageUrl; // Luu anh dai dien
        this.sellerId = sellerId; // Luu id nguoi ban
        this.createdAt = createdAt; // Luu thoi diem tao
        this.painter = painter; // Luu hoa si/tac gia neu la ART
        this.artStyle = artStyle; // Luu phong cach neu la ART
        this.brand = brand; // Luu thuong hieu neu la ELECTRONICS
        this.warrantyMonths = warrantyMonths; // Luu thoi gian bao hanh neu la ELECTRONICS
        this.model = model; // Luu dong xe/mau xe neu la VEHICLES
        this.engineType = engineType; // Luu loai dong co neu la VEHICLES
        this.licensePlate = licensePlate; // Luu bien so neu la VEHICLES
        this.kmAge = kmAge; // Luu so km da di neu la VEHICLES
    } // Ket thuc constructor day du

    public ItemSummaryDTO toSummaryDTO() { // Tao DTO tom tat tu DTO chi tiet de UI co the tai su dung danh sach
        return new ItemSummaryDTO(itemId, itemName, startingPrice == null ? 0.0 : startingPrice, itemType, status); // Giu format tom tat dang dung o SellerItemController
    } // Ket thuc ham doi sang ItemSummaryDTO

    public String getItemId() { return itemId; } // Tra id san pham
    public void setItemId(String itemId) { this.itemId = itemId; } // Gan id san pham
    public String getItemName() { return itemName; } // Tra ten san pham
    public void setItemName(String itemName) { this.itemName = itemName; } // Gan ten san pham
    public Double getStartingPrice() { return startingPrice; } // Tra gia khoi diem
    public void setStartingPrice(Double startingPrice) { this.startingPrice = startingPrice; } // Gan gia khoi diem
    public String getItemType() { return itemType; } // Tra loai san pham
    public void setItemType(String itemType) { this.itemType = itemType; } // Gan loai san pham
    public String getStatus() { return status; } // Tra trang thai san pham
    public void setStatus(String status) { this.status = status; } // Gan trang thai san pham
    public String getDescription() { return description; } // Tra mo ta san pham
    public void setDescription(String description) { this.description = description; } // Gan mo ta san pham
    public Integer getYearCreated() { return yearCreated; } // Tra nam tao/san xuat
    public void setYearCreated(Integer yearCreated) { this.yearCreated = yearCreated; } // Gan nam tao/san xuat
    public String getImageUrl() { return imageUrl; } // Tra URL anh san pham
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; } // Gan URL anh san pham
    public String getSellerId() { return sellerId; } // Tra id nguoi ban
    public void setSellerId(String sellerId) { this.sellerId = sellerId; } // Gan id nguoi ban
    public LocalDateTime getCreatedAt() { return createdAt; } // Tra thoi diem tao san pham
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; } // Gan thoi diem tao san pham
    public String getPainter() { return painter; } // Tra hoa si/tac gia cua ART
    public void setPainter(String painter) { this.painter = painter; } // Gan hoa si/tac gia cua ART
    public String getArtStyle() { return artStyle; } // Tra phong cach cua ART
    public void setArtStyle(String artStyle) { this.artStyle = artStyle; } // Gan phong cach cua ART
    public String getBrand() { return brand; } // Tra thuong hieu cua ELECTRONICS
    public void setBrand(String brand) { this.brand = brand; } // Gan thuong hieu cua ELECTRONICS
    public Integer getWarrantyMonths() { return warrantyMonths; } // Tra so thang bao hanh cua ELECTRONICS
    public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; } // Gan so thang bao hanh cua ELECTRONICS
    public String getModel() { return model; } // Tra model cua VEHICLES
    public void setModel(String model) { this.model = model; } // Gan model cua VEHICLES
    public String getEngineType() { return engineType; } // Tra loai dong co cua VEHICLES
    public void setEngineType(String engineType) { this.engineType = engineType; } // Gan loai dong co cua VEHICLES
    public String getLicensePlate() { return licensePlate; } // Tra bien so cua VEHICLES
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; } // Gan bien so cua VEHICLES
    public Double getKmAge() { return kmAge; } // Tra so km da di cua VEHICLES
    public void setKmAge(Double kmAge) { this.kmAge = kmAge; } // Gan so km da di cua VEHICLES
} // Ket thuc ItemDetailDTO