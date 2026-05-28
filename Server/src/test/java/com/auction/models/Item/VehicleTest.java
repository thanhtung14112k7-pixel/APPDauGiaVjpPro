package com.auction.models.Item;

import com.auction.enums.ItemStatus;
import com.auction.enums.ItemType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class VehicleTest {

    /**
     * Test constructor tạo mới của Vehicle.
     * <p>
     * Vehicle có các field riêng:
     * - model
     * - engineType
     * - licensePlate
     * - kmage
     * <p>
     * Constructor Vehicle tự truyền ItemType.VEHICLES lên class cha Item.
     */
    @Test
    void newVehicleShouldStoreSpecificFields() {
        Vehicle item = new Vehicle(
                "Toyota Camry",
                600000000,
                "Xe còn mới",
                2020,
                "seller-1",
                "camry.png",
                "Camry 2.5Q",
                "Gasoline",
                "30A-12345",
                25000.5
        );

        // Kiểm tra field riêng model
        assertEquals("Camry 2.5Q", item.getModel());

        // Kiểm tra field riêng engineType
        assertEquals("Gasoline", item.getEngineType());

        // Kiểm tra field riêng licensePlate
        assertEquals("30A-12345", item.getLicensePlate());

        // Kiểm tra số km đã đi
        assertEquals(25000.5, item.getKmage());

        // getKmAge() cũng trả về cùng field kmage
        assertEquals(25000.5, item.getKmAge());

        // Vehicle phải có itemType là VEHICLES
        assertEquals(ItemType.VEHICLES, item.getItemType());
    }

    /**
     * Test các setter riêng của Vehicle.
     * <p>
     * Các setter:
     * - setModel()
     * - setEngineType()
     * - setLicensePlate()
     * - setKmage()
     */
    @Test
    void vehicleSettersShouldUpdateSpecificFields() {
        Vehicle item = new Vehicle(
                "Toyota Camry",
                600000000,
                "Xe còn mới",
                2020,
                "seller-1",
                "camry.png",
                "Camry 2.5Q",
                "Gasoline",
                "30A-12345",
                25000.5
        );

        // Cập nhật thông tin xe
        item.setModel("Civic RS");
        item.setEngineType("Hybrid");
        item.setLicensePlate("29A-99999");
        item.setKmage(10000.0);

        // Kiểm tra giá trị mới
        assertEquals("Civic RS", item.getModel());
        assertEquals("Hybrid", item.getEngineType());
        assertEquals("29A-99999", item.getLicensePlate());
        assertEquals(10000.0, item.getKmage());
    }

    /**
     * Test setKmAge().
     * <p>
     * Trong Vehicle có cả:
     * - setKmage(double kmage)
     * - setKmAge(double kmAge)
     * <p>
     * Cả hai đều cập nhật cùng field kmage.
     */
    @Test
    void setKmAgeShouldUpdateKmageField() {
        Vehicle item = new Vehicle(
                "Toyota Camry",
                600000000,
                "Xe còn mới",
                2020,
                "seller-1",
                "camry.png",
                "Camry 2.5Q",
                "Gasoline",
                "30A-12345",
                25000.5
        );

        // Dùng setter setKmAge
        item.setKmAge(7777.7);

        // Cả getKmAge và getKmage đều phải trả cùng giá trị mới
        assertEquals(7777.7, item.getKmAge());
        assertEquals(7777.7, item.getKmage());
    }

    /**
     * Test constructor load từ DB của Vehicle.
     * <p>
     * Constructor DB nhận id, status, createdAt
     * và các field riêng của Vehicle.
     */
    @Test
    void dbConstructorShouldRestoreVehicleData() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(4);

        Vehicle item = new Vehicle(
                "item-3",
                "Honda Civic",
                700000000,
                "Xe sedan",
                2021,
                "seller-3",
                "civic.png",
                ItemStatus.ACTIVE,
                createdAt,
                "Civic RS",
                "Gasoline",
                "29A-88888",
                15000.0
        );

        // Kiểm tra field chung từ Item
        assertEquals("Honda Civic", item.getName());
        assertEquals(700000000, item.getStartingPrice());
        assertEquals("Xe sedan", item.getDescription());
        assertEquals(2021, item.getYearCreated());
        assertEquals("seller-3", item.getSellerId());
        assertEquals("civic.png", item.getImageUrl());
        assertEquals(ItemStatus.ACTIVE, item.getStatus());
        assertEquals(ItemType.VEHICLES, item.getItemType());

        // Kiểm tra field riêng của Vehicle
        assertEquals("Civic RS", item.getModel());
        assertEquals("Gasoline", item.getEngineType());
        assertEquals("29A-88888", item.getLicensePlate());
        assertEquals(15000.0, item.getKmage());

        // Kiểm tra createdAt load từ DB
        assertEquals(createdAt, item.getCreatedAt());
    }

    /**
     * Test getInfo() của Vehicle.
     * <p>
     * getInfo() trả về chuỗi mô tả gồm:
     * - [Phương tiện]
     * - Tên
     * - Dòng xe
     * - Số km đã đi
     * - Động cơ
     * - Biển số
     * - Năm sản xuất
     * - Giá khởi điểm
     * - Trạng thái
     */
    @Test
    void getInfoShouldContainImportantVehicleInformation() {
        Vehicle item = new Vehicle(
                "Toyota Camry",
                600000000,
                "Xe còn mới",
                2020,
                "seller-1",
                "camry.png",
                "Camry 2.5Q",
                "Gasoline",
                "30A-12345",
                25000.5
        );

        String info = item.getInfo();

        // Kiểm tra chuỗi info có chứa thông tin quan trọng
        assertTrue(info.contains("[Phương tiện]"));
        assertTrue(info.contains("Toyota Camry"));
        assertTrue(info.contains("Camry 2.5Q"));
        assertTrue(info.contains("Gasoline"));
        assertTrue(info.contains("30A-12345"));
        assertTrue(info.contains("2020"));
        assertTrue(info.contains("ACTIVE"));
    }
}