package com.auction.models.Item;

import com.auction.enums.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemFactoryTest {

    /**
     * Hàm này chạy trước mỗi test.
     *
     * ItemFactory dùng registry:
     * Map<String, ItemFactory>
     *
     * Muốn gọi ItemFactory.createItem(type, data),
     * ta phải đăng ký factory cho từng type trước.
     */
    @BeforeEach
    void setUp() {
        ItemFactory.register("ART", new ArtFactory());
        ItemFactory.register("VEHICLE", new VehicleFactory());
        ItemFactory.register("ELECTRONICS", new ElectronicsFactory());
    }

    /**
     * Tạo dữ liệu chung cho Electronics.
     *
     * ElectronicsFactory cần các field bắt buộc:
     * - name
     * - startingPrice
     * - yearCreated
     * - sellerId
     * - brand
     * - warrantyMonths
     */
    private Map<String, Object> electronicsData() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Laptop Dell");
        data.put("startingPrice", 12000000);
        data.put("description", "Laptop văn phòng");
        data.put("yearCreated", 2022);
        data.put("sellerId", "seller-1");
        data.put("imageUrl", "dell.png");
        data.put("brand", "Dell");
        data.put("warrantyMonths", 24);
        return data;
    }

    /**
     * Tạo dữ liệu chung cho Art.
     *
     * ArtFactory cần các field bắt buộc:
     * - name
     * - startingPrice
     * - yearCreated
     * - sellerId
     * - painter
     * - artStyle
     */
    private Map<String, Object> artData() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Mona Lisa");
        data.put("startingPrice", 50000000);
        data.put("description", "Tranh nổi tiếng");
        data.put("yearCreated", 1503);
        data.put("sellerId", "seller-2");
        data.put("imageUrl", "mona-lisa.png");
        data.put("painter", "Leonardo da Vinci");
        data.put("artStyle", "Renaissance");
        return data;
    }

    /**
     * Tạo dữ liệu chung cho Vehicle.
     *
     * VehicleFactory cần các field bắt buộc:
     * - name
     * - startingPrice
     * - yearCreated
     * - sellerId
     * - model
     * - engineType
     * - licensePlate
     * - kmAge
     */
    private Map<String, Object> vehicleData() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Toyota Camry");
        data.put("startingPrice", 600000000);
        data.put("description", "Xe còn mới");
        data.put("yearCreated", 2020);
        data.put("sellerId", "seller-3");
        data.put("imageUrl", "camry.png");
        data.put("model", "Camry 2.5Q");
        data.put("engineType", "Gasoline");
        data.put("licensePlate", "30A-12345");
        data.put("kmAge", 25000.5);
        return data;
    }

    /**
     * Test factory tạo Electronics.
     *
     * Khi type là ELECTRONICS,
     * ItemFactory phải trả về object Electronics.
     */
    @Test
    void createItemShouldCreateElectronicsWhenTypeIsElectronics() {
        Item item = ItemFactory.createItem("ELECTRONICS", electronicsData());

        // Object tạo ra phải đúng kiểu Electronics
        assertInstanceOf(Electronics.class, item);

        Electronics electronics = (Electronics) item;

        // Kiểm tra dữ liệu chung
        assertEquals("Laptop Dell", electronics.getName());
        assertEquals(12000000, electronics.getStartingPrice());
        assertEquals("Laptop văn phòng", electronics.getDescription());
        assertEquals(2022, electronics.getYearCreated());
        assertEquals("seller-1", electronics.getSellerId());
        assertEquals("dell.png", electronics.getImageUrl());
        assertEquals(ItemType.ELECTRONICS, electronics.getItemType());

        // Kiểm tra dữ liệu riêng
        assertEquals("Dell", electronics.getBrand());
        assertEquals(24, electronics.getWarrantyMonths());
    }

    /**
     * Test factory tạo Art.
     *
     * Khi type là ART,
     * ItemFactory phải trả về object Art.
     */
    @Test
    void createItemShouldCreateArtWhenTypeIsArt() {
        Item item = ItemFactory.createItem("ART", artData());

        // Object tạo ra phải đúng kiểu Art
        assertInstanceOf(Art.class, item);

        Art art = (Art) item;

        // Kiểm tra dữ liệu chung
        assertEquals("Mona Lisa", art.getName());
        assertEquals(50000000, art.getStartingPrice());
        assertEquals("Tranh nổi tiếng", art.getDescription());
        assertEquals(1503, art.getYearCreated());
        assertEquals("seller-2", art.getSellerId());
        assertEquals("mona-lisa.png", art.getImageUrl());
        assertEquals(ItemType.ART, art.getItemType());

        // Kiểm tra dữ liệu riêng
        assertEquals("Leonardo da Vinci", art.getPainter());
        assertEquals("Renaissance", art.getArtStyle());
    }

    /**
     * Test factory tạo Vehicle.
     *
     * Khi type là VEHICLE,
     * ItemFactory phải trả về object Vehicle.
     */
    @Test
    void createItemShouldCreateVehicleWhenTypeIsVehicle() {
        Item item = ItemFactory.createItem("VEHICLE", vehicleData());

        // Object tạo ra phải đúng kiểu Vehicle
        assertInstanceOf(Vehicle.class, item);

        Vehicle vehicle = (Vehicle) item;

        // Kiểm tra dữ liệu chung
        assertEquals("Toyota Camry", vehicle.getName());
        assertEquals(600000000, vehicle.getStartingPrice());
        assertEquals("Xe còn mới", vehicle.getDescription());
        assertEquals(2020, vehicle.getYearCreated());
        assertEquals("seller-3", vehicle.getSellerId());
        assertEquals("camry.png", vehicle.getImageUrl());
        assertEquals(ItemType.VEHICLES, vehicle.getItemType());

        // Kiểm tra dữ liệu riêng
        assertEquals("Camry 2.5Q", vehicle.getModel());
        assertEquals("Gasoline", vehicle.getEngineType());
        assertEquals("30A-12345", vehicle.getLicensePlate());
        assertEquals(25000.5, vehicle.getKmage());
    }

    /**
     * Test type không phân biệt chữ hoa/thường.
     *
     * Trong ItemFactory.register():
     * registry.put(type.toUpperCase(), factory);
     *
     * Trong createItem():
     * registry.get(type.toUpperCase());
     *
     * Vì vậy "electronics", "ELECTRONICS", "Electronics"
     * đều phải hoạt động như nhau.
     */
    @Test
    void createItemShouldIgnoreCaseOfType() {
        Item item = ItemFactory.createItem("electronics", electronicsData());

        // Dù type viết thường, factory vẫn tạo Electronics
        assertInstanceOf(Electronics.class, item);
    }

    /**
     * Test type không được hỗ trợ.
     *
     * Nếu type chưa đăng ký trong registry,
     * ItemFactory.createItem() phải ném IllegalArgumentException.
     */
    @Test
    void createItemShouldThrowExceptionWhenTypeIsUnsupported() {
        Map<String, Object> data = electronicsData();

        assertThrows(IllegalArgumentException.class, () -> {
            ItemFactory.createItem("BOOK", data);
        });
    }

    /**
     * Test thiếu field bắt buộc.
     *
     * ElectronicsFactory bắt buộc có field "name".
     * Nếu thiếu name, getRequiredString() phải ném IllegalArgumentException.
     */
    @Test
    void createElectronicsShouldThrowExceptionWhenRequiredStringIsMissing() {
        Map<String, Object> data = electronicsData();

        // Xóa field bắt buộc name
        data.remove("name");

        assertThrows(IllegalArgumentException.class, () -> {
            ItemFactory.createItem("ELECTRONICS", data);
        });
    }

    /**
     * Test field String bắt buộc nhưng chỉ chứa dấu cách.
     *
     * getRequiredString() có trim().isEmpty(),
     * nên chuỗi "   " phải bị coi là không hợp lệ.
     */
    @Test
    void createElectronicsShouldThrowExceptionWhenRequiredStringIsBlank() {
        Map<String, Object> data = electronicsData();

        // name chỉ toàn dấu cách
        data.put("name", "   ");

        assertThrows(IllegalArgumentException.class, () -> {
            ItemFactory.createItem("ELECTRONICS", data);
        });
    }

    /**
     * Test field double sai định dạng.
     *
     * startingPrice là double bắt buộc.
     * Nếu truyền "abc", getRequiredDouble() phải ném IllegalArgumentException.
     */
    @Test
    void createElectronicsShouldThrowExceptionWhenRequiredDoubleIsInvalid() {
        Map<String, Object> data = electronicsData();

        // startingPrice sai định dạng số
        data.put("startingPrice", "abc");

        assertThrows(IllegalArgumentException.class, () -> {
            ItemFactory.createItem("ELECTRONICS", data);
        });
    }

    /**
     * Test field int sai định dạng.
     *
     * yearCreated là int bắt buộc.
     * Nếu truyền "abc", getRequiredInt() phải ném IllegalArgumentException.
     */
    @Test
    void createElectronicsShouldThrowExceptionWhenRequiredIntIsInvalid() {
        Map<String, Object> data = electronicsData();

        // yearCreated sai định dạng số nguyên
        data.put("yearCreated", "abc");

        assertThrows(IllegalArgumentException.class, () -> {
            ItemFactory.createItem("ELECTRONICS", data);
        });
    }

    /**
     * Test optional field description.
     *
     * ElectronicsFactory dùng:
     * getOptionalString(data, "description", "Không có mô tả")
     *
     * Nếu không gửi description,
     * object tạo ra phải có description mặc định.
     */
    @Test
    void createElectronicsShouldUseDefaultDescriptionWhenDescriptionIsMissing() {
        Map<String, Object> data = electronicsData();

        // Xóa description optional
        data.remove("description");

        Electronics item = (Electronics) ItemFactory.createItem("ELECTRONICS", data);

        // Kỳ vọng dùng description mặc định
        assertEquals("Không có mô tả", item.getDescription());
    }

    /**
     * Test optional field imageUrl.
     *
     * ElectronicsFactory dùng:
     * getOptionalString(data, "imageUrl", "default_electronics.png")
     *
     * Nếu không gửi imageUrl,
     * object tạo ra phải có imageUrl mặc định.
     */
    @Test
    void createElectronicsShouldUseDefaultImageUrlWhenImageUrlIsMissing() {
        Map<String, Object> data = electronicsData();

        // Xóa imageUrl optional
        data.remove("imageUrl");

        Electronics item = (Electronics) ItemFactory.createItem("ELECTRONICS", data);

        // Kỳ vọng dùng image mặc định của Electronics
        assertEquals("default_electronics.png", item.getImageUrl());
    }

    /**
     * Test getRequiredDouble() nhận được String số.
     *
     * Trong factory, startingPrice có thể đến từ JSON hoặc form dưới dạng String.
     * Nếu là "12000000", factory phải parse được sang double.
     */
    @Test
    void createElectronicsShouldParseDoubleFromString() {
        Map<String, Object> data = electronicsData();

        // Truyền startingPrice dưới dạng String
        data.put("startingPrice", "12000000");

        Electronics item = (Electronics) ItemFactory.createItem("ELECTRONICS", data);

        // Kỳ vọng parse thành số thành công
        assertEquals(12000000, item.getStartingPrice());
    }

    /**
     * Test getRequiredInt() nhận được String số.
     *
     * yearCreated có thể được gửi dưới dạng "2022".
     * Factory phải parse được sang int.
     */
    @Test
    void createElectronicsShouldParseIntFromString() {
        Map<String, Object> data = electronicsData();

        // Truyền yearCreated dưới dạng String
        data.put("yearCreated", "2022");

        Electronics item = (Electronics) ItemFactory.createItem("ELECTRONICS", data);

        // Kỳ vọng parse thành int thành công
        assertEquals(2022, item.getYearCreated());
    }
}