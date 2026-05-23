
package com.auction.models.Item;

import com.auction.enums.ItemStatus;
import com.auction.enums.ItemType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ArtTest {

    /**
     * Test constructor tạo mới của Art.
     *
     * Art có 2 field riêng:
     * - painter
     * - artStyle
     *
     * Constructor Art tự truyền ItemType.ART lên class cha Item.
     */
    @Test
    void newArtShouldStoreSpecificFields() {
        Art item = new Art(
                "Mona Lisa",
                50000000,
                "Tranh nổi tiếng",
                1503,
                "seller-1",
                "mona-lisa.png",
                "Leonardo da Vinci",
                "Renaissance"
        );

        // Kiểm tra field riêng painter
        assertEquals("Leonardo da Vinci", item.getPainter());

        // Kiểm tra field riêng artStyle
        assertEquals("Renaissance", item.getArtStyle());

        // Art phải có itemType là ART
        assertEquals(ItemType.ART, item.getItemType());
    }

    /**
     * Test setter riêng của Art.
     *
     * Các setter riêng:
     * - setPainter()
     * - setArtStyle()
     */
    @Test
    void artSettersShouldUpdateSpecificFields() {
        Art item = new Art(
                "Mona Lisa",
                50000000,
                "Tranh nổi tiếng",
                1503,
                "seller-1",
                "mona-lisa.png",
                "Leonardo da Vinci",
                "Renaissance"
        );

        // Cập nhật họa sĩ và phong cách
        item.setPainter("Van Gogh");
        item.setArtStyle("Post-Impressionism");

        // Kiểm tra giá trị mới
        assertEquals("Van Gogh", item.getPainter());
        assertEquals("Post-Impressionism", item.getArtStyle());
    }

    /**
     * Test constructor load từ DB của Art.
     *
     * Constructor DB nhận id, status, createdAt
     * và các field riêng của Art.
     */
    @Test
    void dbConstructorShouldRestoreArtData() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(3);

        Art item = new Art(
                "item-2",
                "Starry Night",
                70000000,
                "Tranh sơn dầu",
                1889,
                "seller-2",
                "starry-night.png",
                ItemStatus.ACTIVE,
                createdAt,
                "Vincent van Gogh",
                "Post-Impressionism"
        );

        // Kiểm tra field chung từ Item
        assertEquals("Starry Night", item.getName());
        assertEquals(70000000, item.getStartingPrice());
        assertEquals("Tranh sơn dầu", item.getDescription());
        assertEquals(1889, item.getYearCreated());
        assertEquals("seller-2", item.getSellerId());
        assertEquals("starry-night.png", item.getImageUrl());
        assertEquals(ItemStatus.ACTIVE, item.getStatus());
        assertEquals(ItemType.ART, item.getItemType());

        // Kiểm tra field riêng của Art
        assertEquals("Vincent van Gogh", item.getPainter());
        assertEquals("Post-Impressionism", item.getArtStyle());

        // Kiểm tra createdAt load từ DB
        assertEquals(createdAt, item.getCreatedAt());
    }

    /**
     * Test getInfo() của Art.
     *
     * getInfo() trả về chuỗi mô tả gồm:
     * - [Nghệ thuật]
     * - Tên
     * - Họa sĩ/Tác giả
     * - Phong cách
     * - Năm sáng tác
     * - Giá khởi điểm
     * - Trạng thái
     */
    @Test
    void getInfoShouldContainImportantArtInformation() {
        Art item = new Art(
                "Mona Lisa",
                50000000,
                "Tranh nổi tiếng",
                1503,
                "seller-1",
                "mona-lisa.png",
                "Leonardo da Vinci",
                "Renaissance"
        );

        String info = item.getInfo();

        // Kiểm tra chuỗi info có chứa các thông tin quan trọng
        assertTrue(info.contains("[Nghệ thuật]"));
        assertTrue(info.contains("Mona Lisa"));
        assertTrue(info.contains("Leonardo da Vinci"));
        assertTrue(info.contains("Renaissance"));
        assertTrue(info.contains("1503"));
        assertTrue(info.contains("ACTIVE"));
    }
}