package com.auction.models.Item;

import com.auction.enums.ItemStatus;
import com.auction.enums.ItemType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ElectronicsTest {

    /**
     * Test constructor tạo mới của Electronics.
     *
     * Electronics có 2 field riêng:
     * - brand
     * - warrantyMonths
     *
     * Đồng thời constructor Electronics tự truyền ItemType.ELECTRONICS
     * lên constructor của Item.
     */
    @Test
    void newElectronicsShouldStoreSpecificFields() {
        Electronics item = new Electronics(
                "iPhone 15",
                18000000,
                "Điện thoại Apple",
                2023,
                "seller-1",
                "iphone.png",
                "Apple",
                12
        );

        // Kiểm tra field riêng brand
        assertEquals("Apple", item.getBrand());

        // Kiểm tra field riêng warrantyMonths
        assertEquals(12, item.getWarrantyMonths());

        // Electronics phải có itemType là ELECTRONICS
        assertEquals(ItemType.ELECTRONICS, item.getItemType());
    }

    /**
     * Test setter riêng của Electronics.
     *
     * Các setter riêng:
     * - setBrand()
     * - setWarrantyMonths()
     */
    @Test
    void electronicsSettersShouldUpdateSpecificFields() {
        Electronics item = new Electronics(
                "iPhone 15",
                18000000,
                "Điện thoại Apple",
                2023,
                "seller-1",
                "iphone.png",
                "Apple",
                12
        );

        // Cập nhật brand và thời gian bảo hành
        item.setBrand("Samsung");
        item.setWarrantyMonths(24);

        // Kiểm tra giá trị mới
        assertEquals("Samsung", item.getBrand());
        assertEquals(24, item.getWarrantyMonths());
    }

    /**
     * Test constructor load từ DB của Electronics.
     *
     * Constructor DB nhận id, status, createdAt
     * và các field riêng của Electronics.
     */
    @Test
    void dbConstructorShouldRestoreElectronicsData() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(2);

        Electronics item = new Electronics(
                "item-1",
                "Laptop Dell",
                12000000,
                "Laptop văn phòng",
                2022,
                "seller-1",
                "dell.png",
                ItemStatus.ACTIVE,
                createdAt,
                "Dell",
                36
        );

        // Kiểm tra field chung từ Item
        assertEquals("Laptop Dell", item.getName());
        assertEquals(12000000, item.getStartingPrice());
        assertEquals("Laptop văn phòng", item.getDescription());
        assertEquals(2022, item.getYearCreated());
        assertEquals("seller-1", item.getSellerId());
        assertEquals("dell.png", item.getImageUrl());
        assertEquals(ItemStatus.ACTIVE, item.getStatus());
        assertEquals(ItemType.ELECTRONICS, item.getItemType());

        // Kiểm tra field riêng của Electronics
        assertEquals("Dell", item.getBrand());
        assertEquals(36, item.getWarrantyMonths());

        // Kiểm tra thời gian tạo được load từ DB
        assertEquals(createdAt, item.getCreatedAt());
    }

    /**
     * Test getInfo() của Electronics.
     *
     * getInfo() trả về chuỗi mô tả gồm:
     * - [Điện tử]
     * - Tên
     * - Thương hiệu
     * - Năm sản xuất
     * - Thời gian bảo hành
     * - Giá khởi điểm
     * - Trạng thái
     *
     * Ta không cần so sánh nguyên chuỗi vì có format tiền.
     * Chỉ cần kiểm tra chuỗi có chứa các thông tin quan trọng.
     */
    @Test
    void getInfoShouldContainImportantElectronicsInformation() {
        Electronics item = new Electronics(
                "iPhone 15",
                18000000,
                "Điện thoại Apple",
                2023,
                "seller-1",
                "iphone.png",
                "Apple",
                12
        );

        String info = item.getInfo();

        // Kiểm tra các thông tin quan trọng có xuất hiện trong chuỗi getInfo()
        assertTrue(info.contains("[Điện tử]"));
        assertTrue(info.contains("iPhone 15"));
        assertTrue(info.contains("Apple"));
        assertTrue(info.contains("2023"));
        assertTrue(info.contains("12"));
        assertTrue(info.contains("ACTIVE"));
    }
}
