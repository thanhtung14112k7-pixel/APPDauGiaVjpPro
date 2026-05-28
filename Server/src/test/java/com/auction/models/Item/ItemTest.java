package com.auction.models.Item;

import com.auction.enums.ItemStatus;
import com.auction.enums.ItemType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ItemTest {

    /**
     * Test constructor tạo mới của Item thông qua class con Electronics.
     * <p>
     * Vì Item là abstract class nên không thể new Item() trực tiếp.
     * Ta dùng Electronics để kiểm tra các field chung của Item:
     * - name
     * - startingPrice
     * - description
     * - yearCreated
     * - sellerId
     * - itemType
     * - imageUrl
     */
    @Test
    void newItemShouldStoreBasicInformation() {
        Electronics item = new Electronics(
                "Laptop Gaming",
                15000000,
                "Laptop còn mới",
                2024,
                "seller-1",
                "laptop.png",
                "Asus",
                24
        );

        // Kiểm tra tên item
        assertEquals("Laptop Gaming", item.getName());

        // Kiểm tra giá khởi điểm
        assertEquals(15000000, item.getStartingPrice());

        // Kiểm tra mô tả
        assertEquals("Laptop còn mới", item.getDescription());

        // Kiểm tra năm sản xuất/tạo ra
        assertEquals(2024, item.getYearCreated());

        // Kiểm tra người bán
        assertEquals("seller-1", item.getSellerId());

        // Vì đang tạo Electronics nên itemType phải là ELECTRONICS
        assertEquals(ItemType.ELECTRONICS, item.getItemType());

        // Kiểm tra imageUrl
        assertEquals("laptop.png", item.getImageUrl());
    }

    /**
     * Test trạng thái mặc định của item mới.
     * <p>
     * Trong constructor tạo mới của Item:
     * this.status = ItemStatus.ACTIVE;
     * <p>
     * Vì vậy item mới tạo phải có status ACTIVE.
     */
    @Test
    void newItemShouldBeActiveByDefault() {
        Electronics item = new Electronics(
                "Laptop Gaming",
                15000000,
                "Laptop còn mới",
                2024,
                "seller-1",
                "laptop.png",
                "Asus",
                24
        );

        // Item mới tạo mặc định phải ACTIVE
        assertEquals(ItemStatus.ACTIVE, item.getStatus());
    }

    /**
     * Test createdAt của item mới.
     * <p>
     * Trong constructor tạo mới của Item:
     * this.createdAt = LocalDateTime.now();
     * <p>
     * Vì vậy createdAt không được null.
     */
    @Test
    void newItemShouldHaveCreatedAt() {
        Electronics item = new Electronics(
                "Laptop Gaming",
                15000000,
                "Laptop còn mới",
                2024,
                "seller-1",
                "laptop.png",
                "Asus",
                24
        );

        // createdAt phải được tự động tạo
        assertNotNull(item.getCreatedAt());
    }

    /**
     * Test các setter chung của Item.
     * <p>
     * Các setter này gồm:
     * - setName()
     * - setDescription()
     * - setImageUrl()
     * - setStartingPrice()
     * - setYearCreated()
     * <p>
     * Mục tiêu:
     * kiểm tra sau khi gọi setter, getter trả về giá trị mới.
     */
    @Test
    void itemSettersShouldUpdateCommonFields() {
        Electronics item = new Electronics(
                "Old name",
                1000,
                "Old description",
                2020,
                "seller-1",
                "old.png",
                "Old brand",
                12
        );

        // Cập nhật thông tin item
        item.setName("New name");
        item.setDescription("New description");
        item.setImageUrl("new.png");
        item.setStartingPrice(2000);
        item.setYearCreated(2024);

        // Kiểm tra các field đã đổi đúng chưa
        assertEquals("New name", item.getName());
        assertEquals("New description", item.getDescription());
        assertEquals("new.png", item.getImageUrl());
        assertEquals(2000, item.getStartingPrice());
        assertEquals(2024, item.getYearCreated());
    }

    /**
     * Test setStatus().
     * <p>
     * Expected đúng:
     * item ban đầu ACTIVE
     * gọi setStatus(ItemStatus.SOLD hoặc status khác)
     * getStatus() phải trả về status mới.
     * <p>
     * Lưu ý:
     * Code Item.java hiện tại đang có bug:
     * <p>
     * public void setStatus(ItemStatus newStatus) {
     *     this.status = status;
     * }
     * <p>
     * Đúng phải là:
     * this.status = newStatus;
     * <p>
     * Vì vậy test này có thể fail cho đến khi bạn sửa Item.java.
     */
    @Test
    void setStatusShouldUpdateStatus_ButCurrentlyThisTestWillExposeBug() {
        Electronics item = new Electronics(
                "Laptop Gaming",
                15000000,
                "Laptop còn mới",
                2024,
                "seller-1",
                "laptop.png",
                "Asus",
                24
        );

        // Nếu enum ItemStatus của bạn không có SOLD,
        // hãy đổi SOLD thành trạng thái thật đang có trong project.
        item.setStatus(ItemStatus.SOLD);

        // Kỳ vọng status đã đổi sang SOLD
        assertEquals(ItemStatus.SOLD, item.getStatus());
    }

    /**
     * Test constructor load từ DB của Item thông qua Electronics.
     * <p>
     * Constructor DB nhận sẵn:
     * - id
     * - status
     * - createdAt
     * <p>
     * Mục tiêu:
     * kiểm tra object load từ DB giữ đúng dữ liệu.
     */
    @Test
    void dbConstructorShouldRestoreCommonItemData() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);

        Electronics item = new Electronics(
                "item-1",
                "Laptop Gaming",
                15000000,
                "Laptop còn mới",
                2024,
                "seller-1",
                "laptop.png",
                ItemStatus.ACTIVE,
                createdAt,
                "Asus",
                24
        );

        // Kiểm tra dữ liệu chung
        assertEquals("Laptop Gaming", item.getName());
        assertEquals(15000000, item.getStartingPrice());
        assertEquals("Laptop còn mới", item.getDescription());
        assertEquals(2024, item.getYearCreated());
        assertEquals("seller-1", item.getSellerId());
        assertEquals("laptop.png", item.getImageUrl());
        assertEquals(ItemType.ELECTRONICS, item.getItemType());
        assertEquals(ItemStatus.ACTIVE, item.getStatus());

        // getCreatedAt() hiện trả về Object, nên so sánh trực tiếp vẫn được
        assertEquals(createdAt, item.getCreatedAt());
    }
}