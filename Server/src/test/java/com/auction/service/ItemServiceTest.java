package com.auction.service;

import com.auction.dao.ItemDAO;
import com.auction.dto.ItemDetailDTO;
import com.auction.dto.ItemSummaryDTO;
import com.auction.enums.ItemStatus;
import com.auction.enums.ItemType;
import com.auction.exception.AuctionErrorCode;
import com.auction.exception.AuctionException;
import com.auction.exception.ValidationErrorCode;
import com.auction.exception.ValidationException;
import com.auction.manage.ProductManage;
import com.auction.models.Item.Art;
import com.auction.models.Item.ArtFactory;
import com.auction.models.Item.Electronics;
import com.auction.models.Item.ElectronicsFactory;
import com.auction.models.Item.Item;
import com.auction.models.Item.ItemFactory;
import com.auction.models.Item.Vehicle;
import com.auction.models.Item.VehicleFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ItemServiceTest {

    private ItemService itemService;
    private ItemDAO itemDAO;
    private ProductManage productManage;

    /**
     * Chạy trước mỗi test.
     *
     * ItemService hiện tự tạo DAO thật:
     * private final ItemDAO itemDAO = new ItemDAOImpl();
     *
     * Nếu để nguyên thì test sẽ đụng MySQL thật.
     * Vì đây là unit test, ta dùng Mockito tạo ItemDAO giả,
     * rồi inject vào ItemService bằng reflection.
     *
     * Đồng thời phải register ItemFactory,
     * vì ItemService.addItem() gọi ItemFactory.createItem(...).
     */
    @BeforeEach
    void setUp() throws Exception {
        itemService = new ItemService();

        itemDAO = mock(ItemDAO.class);
        injectField(itemService, "itemDAO", itemDAO);

        productManage = ProductManage.getInstance();
        clearProductManage();

        ItemFactory.register(ItemType.ART, new ArtFactory());
        ItemFactory.register(ItemType.ELECTRONICS, new ElectronicsFactory());
        ItemFactory.register(ItemType.VEHICLES, new VehicleFactory());
    }

    /**
     * Helper inject field private trong service.
     *
     * Dùng để thay ItemDAO thật bằng ItemDAO mock.
     */
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * ProductManage là singleton, dữ liệu có thể còn sót từ test trước.
     *
     * Helper này clear 2 map private:
     * - items
     * - lastAccessedTime
     *
     * Nhờ vậy mỗi test bắt đầu với RAM cache sạch.
     */
    @SuppressWarnings("unchecked")
    private void clearProductManage() throws Exception {
        Field itemsField = ProductManage.class.getDeclaredField("items");
        itemsField.setAccessible(true);
        Map<String, Item> items = (Map<String, Item>) itemsField.get(productManage);
        items.clear();

        Field timeField = ProductManage.class.getDeclaredField("lastAccessedTime");
        timeField.setAccessible(true);
        Map<String, LocalDateTime> times = (Map<String, LocalDateTime>) timeField.get(productManage);
        times.clear();
    }

    /**
     * Helper kiểm tra ValidationException có đúng mã lỗi không.
     */
    private void assertValidationError(ValidationException exception, ValidationErrorCode expectedError) {
        assertEquals(expectedError.getCode(), exception.getErrorCode());
    }

    /**
     * Helper kiểm tra AuctionException có đúng mã lỗi không.
     */
    private void assertAuctionError(AuctionException exception, AuctionErrorCode expectedError) {
        assertEquals(expectedError.getCode(), exception.getErrorCode());
    }

    /**
     * Data hợp lệ để tạo Electronics.
     *
     * ElectronicsFactory cần:
     * - name
     * - startingPrice
     * - yearCreated
     * - sellerId
     * - brand
     * - warrantyMonths
     *
     * description và imageUrl là optional nhưng vẫn thêm để test DTO rõ hơn.
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
     * Data hợp lệ để tạo Art.
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
     * Data hợp lệ để tạo Vehicle.
     *
     * Lưu ý key là "kmAge",
     * vì VehicleFactory đang đọc getRequiredDouble(data, "kmAge").
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
     * Helper tạo Electronics có id cố định để test get/update.
     */
    private Electronics sampleElectronics(String itemId) {
        Electronics item = new Electronics(
                "Laptop Dell",
                12000000,
                "Laptop văn phòng",
                2022,
                "seller-1",
                "dell.png",
                "Dell",
                24
        );
        item.setId(itemId);
        return item;
    }

    /**
     * Helper tạo Art có id cố định.
     */
    private Art sampleArt(String itemId) {
        Art item = new Art(
                "Mona Lisa",
                50000000,
                "Tranh nổi tiếng",
                1503,
                "seller-2",
                "mona-lisa.png",
                "Leonardo da Vinci",
                "Renaissance"
        );
        item.setId(itemId);
        return item;
    }

    /**
     * Helper tạo Vehicle có id cố định.
     */
    private Vehicle sampleVehicle(String itemId) {
        Vehicle item = new Vehicle(
                "Toyota Camry",
                600000000,
                "Xe còn mới",
                2020,
                "seller-3",
                "camry.png",
                "Camry 2.5Q",
                "Gasoline",
                "30A-12345",
                25000.5
        );
        item.setId(itemId);
        return item;
    }

    // =========================================================
    // TEST addItem()
    // =========================================================

    /**
     * Test addItem() khi type null.
     *
     * ItemService.addItem(ItemType type, Map data) kiểm tra:
     * if (type == null || data == null) throw BAD_REQUEST.
     */
    @Test
    void addItemShouldThrowBadRequestWhenTypeIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.addItem((ItemType) null, electronicsData());
        });

        assertValidationError(exception, ValidationErrorCode.BAD_REQUEST);
    }

    /**
     * Test addItem() khi data null.
     *
     * Payload null là request lỗi.
     */
    @Test
    void addItemShouldThrowBadRequestWhenDataIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.addItem(ItemType.ELECTRONICS, null);
        });

        assertValidationError(exception, ValidationErrorCode.BAD_REQUEST);
    }

    /**
     * Test addItem(String type, data) khi type sai.
     *
     * parseItemType("BOOK") sẽ lỗi,
     * ItemService bọc lại thành ValidationException INVALID_PARAMETER.
     */
    @Test
    void addItemShouldThrowInvalidParameterWhenTypeStringIsInvalid() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.addItem("BOOK", electronicsData());
        });

        assertValidationError(exception, ValidationErrorCode.INVALID_PARAMETER);
    }

    /**
     * Test addItem(String type, data) với legacy type "VEHICLE".
     *
     * ItemFactory.parseItemType() hỗ trợ:
     * "VEHICLE" -> ItemType.VEHICLES.
     *
     * Nếu DB insert thành công, service phải tạo Vehicle DTO.
     */
    @Test
    void addItemShouldAcceptLegacyVehicleTypeString() {
        when(itemDAO.insertItem(any(Item.class))).thenReturn(true);

        ItemDetailDTO dto = itemService.addItem("VEHICLE", vehicleData());

        assertNotNull(dto);
        assertEquals("VEHICLES", dto.getItemType());
        assertEquals("Toyota Camry", dto.getItemName());
        assertEquals("Camry 2.5Q", dto.getModel());
    }

    /**
     * Test addItem() khi thiếu field bắt buộc.
     *
     * Electronics thiếu "name" thì factory ném IllegalArgumentException.
     * ItemService bắt lỗi này và ném ValidationException INVALID_PARAMETER.
     */
    @Test
    void addItemShouldThrowInvalidParameterWhenRequiredFieldIsMissing() {
        Map<String, Object> data = electronicsData();
        data.remove("name");

        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.addItem(ItemType.ELECTRONICS, data);
        });

        assertValidationError(exception, ValidationErrorCode.INVALID_PARAMETER);
    }

    /**
     * Test addItem() khi DB insert fail.
     *
     * Luồng:
     * - Factory tạo item thành công.
     * - itemDAO.insertItem(newItem) trả false.
     * - Service ném AuctionException DATABASE_ERROR.
     */
    @Test
    void addItemShouldThrowDatabaseErrorWhenInsertItemFails() {
        when(itemDAO.insertItem(any(Item.class))).thenReturn(false);

        AuctionException exception = assertThrows(AuctionException.class, () -> {
            itemService.addItem(ItemType.ELECTRONICS, electronicsData());
        });

        assertAuctionError(exception, AuctionErrorCode.DATABASE_ERROR);
    }

    /**
     * Test addItem() thành công với Electronics.
     *
     * Expected:
     * - itemDAO.insertItem được gọi.
     * - trả về ItemDetailDTO đúng thông tin chung.
     * - DTO có field riêng brand, warrantyMonths.
     */
    @Test
    void addItemShouldReturnElectronicsDetailDTOWhenSuccess() {
        when(itemDAO.insertItem(any(Item.class))).thenReturn(true);

        ItemDetailDTO dto = itemService.addItem(ItemType.ELECTRONICS, electronicsData());

        assertNotNull(dto);

        // Thông tin chung
        assertEquals("Laptop Dell", dto.getItemName());
        assertEquals(12000000.0, dto.getStartingPrice());
        assertEquals("ELECTRONICS", dto.getItemType());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals("Laptop văn phòng", dto.getDescription());
        assertEquals(2022, dto.getYearCreated());
        assertEquals("dell.png", dto.getImageUrl());
        assertEquals("seller-1", dto.getSellerId());

        // Thông tin riêng của Electronics
        assertEquals("Dell", dto.getBrand());
        assertEquals(24, dto.getWarrantyMonths());

        verify(itemDAO).insertItem(any(Item.class));
    }

    /**
     * Test addItem() thành công với Art.
     */
    @Test
    void addItemShouldReturnArtDetailDTOWhenSuccess() {
        when(itemDAO.insertItem(any(Item.class))).thenReturn(true);

        ItemDetailDTO dto = itemService.addItem(ItemType.ART, artData());

        assertNotNull(dto);

        assertEquals("Mona Lisa", dto.getItemName());
        assertEquals(50000000.0, dto.getStartingPrice());
        assertEquals("ART", dto.getItemType());
        assertEquals("ACTIVE", dto.getStatus());

        // Field riêng của Art
        assertEquals("Leonardo da Vinci", dto.getPainter());
        assertEquals("Renaissance", dto.getArtStyle());

        verify(itemDAO).insertItem(any(Item.class));
    }

    /**
     * Test addItem() thành công với Vehicle.
     */
    @Test
    void addItemShouldReturnVehicleDetailDTOWhenSuccess() {
        when(itemDAO.insertItem(any(Item.class))).thenReturn(true);

        ItemDetailDTO dto = itemService.addItem(ItemType.VEHICLES, vehicleData());

        assertNotNull(dto);

        assertEquals("Toyota Camry", dto.getItemName());
        assertEquals(600000000.0, dto.getStartingPrice());
        assertEquals("VEHICLES", dto.getItemType());
        assertEquals("ACTIVE", dto.getStatus());

        // Field riêng của Vehicle
        assertEquals("Camry 2.5Q", dto.getModel());
        assertEquals("Gasoline", dto.getEngineType());
        assertEquals("30A-12345", dto.getLicensePlate());
        assertEquals(25000.5, dto.getKmAge());

        verify(itemDAO).insertItem(any(Item.class));
    }

    // =========================================================
    // TEST getDetailedItem()
    // =========================================================

    /**
     * Test getDetailedItem() khi itemId null.
     *
     * Service phải ném ValidationException MISSING_REQUIRED_FIELD.
     */
    @Test
    void getDetailedItemShouldThrowWhenItemIdIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.getDetailedItem(null);
        });

        assertValidationError(exception, ValidationErrorCode.MISSING_REQUIRED_FIELD);
    }

    /**
     * Test getDetailedItem() khi itemId rỗng/toàn dấu cách.
     */
    @Test
    void getDetailedItemShouldThrowWhenItemIdIsBlank() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.getDetailedItem("   ");
        });

        assertValidationError(exception, ValidationErrorCode.MISSING_REQUIRED_FIELD);
    }

    /**
     * Test getDetailedItem() khi item không tồn tại.
     *
     * Luồng:
     * - RAM không có item.
     * - itemDAO.findById(...) trả Optional.empty().
     * - Service ném ITEM_NOT_FOUND.
     */
    @Test
    void getDetailedItemShouldThrowWhenItemNotFound() {
        when(itemDAO.findById("missing-item")).thenReturn(Optional.empty());

        AuctionException exception = assertThrows(AuctionException.class, () -> {
            itemService.getDetailedItem("missing-item");
        });

        assertAuctionError(exception, AuctionErrorCode.ITEM_NOT_FOUND);
    }

    /**
     * Test getDetailedItem() khi item tồn tại trong DB.
     *
     * Service sẽ:
     * - load item từ DB
     * - add vào ProductManage RAM
     * - convert sang ItemDetailDTO
     */
    @Test
    void getDetailedItemShouldReturnDetailDTOWhenItemExistsInDatabase() {
        Electronics item = sampleElectronics("item-detail-1");

        when(itemDAO.findById("item-detail-1")).thenReturn(Optional.of(item));

        ItemDetailDTO dto = itemService.getDetailedItem("item-detail-1");

        assertNotNull(dto);
        assertEquals("item-detail-1", dto.getItemId());
        assertEquals("Laptop Dell", dto.getItemName());
        assertEquals("ELECTRONICS", dto.getItemType());
        assertEquals("Dell", dto.getBrand());
        assertEquals(24, dto.getWarrantyMonths());

        // Sau khi get detail, item phải được cache vào ProductManage
        assertNotNull(productManage.getProduct("item-detail-1"));
    }

    // =========================================================
    // TEST getSellerItems()
    // =========================================================

    /**
     * Test getSellerItems() khi sellerId null.
     */
    @Test
    void getSellerItemsShouldThrowWhenSellerIdIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.getSellerItems(null);
        });

        assertValidationError(exception, ValidationErrorCode.MISSING_REQUIRED_FIELD);
    }

    /**
     * Test getSellerItems() khi sellerId blank.
     */
    @Test
    void getSellerItemsShouldThrowWhenSellerIdIsBlank() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.getSellerItems("   ");
        });

        assertValidationError(exception, ValidationErrorCode.MISSING_REQUIRED_FIELD);
    }

    /**
     * Test getSellerItems() khi seller không có item nào.
     *
     * DAO trả list rỗng thì service cũng phải trả list rỗng.
     */
    @Test
    void getSellerItemsShouldReturnEmptyListWhenSellerHasNoItems() {
        when(itemDAO.findBySellerId("seller-empty")).thenReturn(List.of());

        List<ItemSummaryDTO> result = itemService.getSellerItems("seller-empty");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * Test getSellerItems() khi DAO trả item.
     *
     * Service phải convert Item sang ItemSummaryDTO.
     */
    @Test
    void getSellerItemsShouldReturnSummaryDTOs() {
        Electronics item = sampleElectronics("item-summary-1");

        when(itemDAO.findBySellerId("seller-1")).thenReturn(List.of(item));

        List<ItemSummaryDTO> result = itemService.getSellerItems("seller-1");

        assertEquals(1, result.size());

        ItemSummaryDTO dto = result.get(0);

        assertEquals("item-summary-1", dto.getItemId());
        assertEquals("Laptop Dell", dto.getItemName());
        assertEquals(12000000.0, dto.getStartingPrice());
        assertEquals("ELECTRONICS", dto.getItemType());
        assertEquals("ACTIVE", dto.getStatus());
    }

    // =========================================================
    // TEST updateItemStatus()
    // =========================================================

    /**
     * Test updateItemStatus() khi itemId null.
     */
    @Test
    void updateItemStatusShouldThrowWhenItemIdIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.updateItemStatus(null, ItemStatus.SOLD);
        });

        assertValidationError(exception, ValidationErrorCode.INVALID_PARAMETER);
    }

    /**
     * Test updateItemStatus() khi newStatus null.
     */
    @Test
    void updateItemStatusShouldThrowWhenNewStatusIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.updateItemStatus("item-1", null);
        });

        assertValidationError(exception, ValidationErrorCode.INVALID_PARAMETER);
    }

    /**
     * Test updateItemStatus() khi DB update fail.
     *
     * itemDAO.updateStatus(...) trả false,
     * service phải ném AuctionException DATABASE_ERROR.
     */
    @Test
    void updateItemStatusShouldThrowDatabaseErrorWhenDaoUpdateFails() {
        when(itemDAO.updateStatus("item-1", ItemStatus.SOLD.name())).thenReturn(false);

        AuctionException exception = assertThrows(AuctionException.class, () -> {
            itemService.updateItemStatus("item-1", ItemStatus.SOLD);
        });

        assertAuctionError(exception, AuctionErrorCode.DATABASE_ERROR);
    }

    /**
     * Test updateItemStatus() khi item đang có trong RAM.
     *
     * Expected:
     * - DB update thành công.
     * - item RAM đổi status.
     *
     * Nếu test này fail ở status,
     * kiểm tra lại bug Item.setStatus().
     */
    @Test
    void updateItemStatusShouldUpdateRamItemWhenItemIsAlreadyCached() {
        Electronics item = sampleElectronics("item-status-1");
        productManage.addProduct(item);

        when(itemDAO.updateStatus("item-status-1", ItemStatus.SOLD.name())).thenReturn(true);

        itemService.updateItemStatus("item-status-1", ItemStatus.SOLD);

        assertEquals(ItemStatus.SOLD, item.getStatus());
        verify(itemDAO).updateStatus("item-status-1", ItemStatus.SOLD.name());
    }

    /**
     * Test updateItemStatus() khi RAM chưa có item.
     *
     * Service sẽ:
     * - update DB status
     * - vì RAM chưa có item, gọi itemDAO.findById(itemId)
     * - nếu DB trả item thì add vào ProductManage
     */
    @Test
    void updateItemStatusShouldLoadItemFromDatabaseWhenItemIsNotCached() {
        Electronics item = sampleElectronics("item-status-2");

        when(itemDAO.updateStatus("item-status-2", ItemStatus.SOLD.name())).thenReturn(true);
        when(itemDAO.findById("item-status-2")).thenReturn(Optional.of(item));

        itemService.updateItemStatus("item-status-2", ItemStatus.SOLD);

        verify(itemDAO).findById("item-status-2");
        assertNotNull(productManage.getProduct("item-status-2"));
    }

    // =========================================================
    // TEST updateItemInfo()
    // =========================================================

    /**
     * Test updateItemInfo() khi itemId null.
     *
     * Service phải ném BAD_REQUEST.
     */
    @Test
    void updateItemInfoShouldThrowBadRequestWhenItemIdIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.updateItemInfo(null, ItemType.ELECTRONICS, Map.of("name", "New Name"));
        });

        assertValidationError(exception, ValidationErrorCode.BAD_REQUEST);
    }

    /**
     * Test updateItemInfo() khi type null.
     */
    @Test
    void updateItemInfoShouldThrowBadRequestWhenTypeIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.updateItemInfo("item-1", (ItemType) null, Map.of("name", "New Name"));
        });

        assertValidationError(exception, ValidationErrorCode.BAD_REQUEST);
    }

    /**
     * Test updateItemInfo() khi incomingData null.
     */
    @Test
    void updateItemInfoShouldThrowBadRequestWhenIncomingDataIsNull() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.updateItemInfo("item-1", ItemType.ELECTRONICS, null);
        });

        assertValidationError(exception, ValidationErrorCode.BAD_REQUEST);
    }

    /**
     * Test updateItemInfo() khi item không tồn tại.
     *
     * Service tìm RAM trước, không thấy thì tìm DB.
     * Nếu DB không có thì ném ITEM_NOT_FOUND.
     */
    @Test
    void updateItemInfoShouldThrowWhenItemNotFound() {
        when(itemDAO.findById("missing-item")).thenReturn(Optional.empty());

        AuctionException exception = assertThrows(AuctionException.class, () -> {
            itemService.updateItemInfo("missing-item", ItemType.ELECTRONICS, Map.of("name", "New Name"));
        });

        assertAuctionError(exception, AuctionErrorCode.ITEM_NOT_FOUND);
    }

    /**
     * Test updateItemInfo() khi type request khác type của item.
     *
     * Ví dụ item thật là ELECTRONICS,
     * nhưng request update lại gửi type ART.
     *
     * Service không cho đổi loại vật phẩm.
     */
    @Test
    void updateItemInfoShouldThrowWhenTypeDoesNotMatchExistingItemType() {
        Electronics item = sampleElectronics("item-type-mismatch");

        when(itemDAO.findById("item-type-mismatch")).thenReturn(Optional.of(item));

        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.updateItemInfo("item-type-mismatch", ItemType.ART, Map.of("name", "New Name"));
        });

        assertValidationError(exception, ValidationErrorCode.BAD_REQUEST);
    }

    /**
     * Test updateItemInfo() khi item bị khóa.
     *
     * Chỉ item ACTIVE mới được sửa.
     * Nếu item INACTIVE hoặc SOLD thì ném ITEM_IS_LOCKED.
     */
    @Test
    void updateItemInfoShouldThrowWhenItemIsLocked() {
        Electronics item = new Electronics(
                "locked-item",
                "Laptop Dell",
                12000000,
                "Laptop văn phòng",
                2022,
                "seller-1",
                "dell.png",
                ItemStatus.INACTIVE,
                LocalDateTime.now(),
                "Dell",
                24
        );

        when(itemDAO.findById("locked-item")).thenReturn(Optional.of(item));

        AuctionException exception = assertThrows(AuctionException.class, () -> {
            itemService.updateItemInfo("locked-item", ItemType.ELECTRONICS, Map.of("name", "New Name"));
        });

        assertAuctionError(exception, AuctionErrorCode.ITEM_IS_LOCKED);
    }

    /**
     * Test updateItemInfo() khi incomingData sai format.
     *
     * Ví dụ startingPrice = "abc".
     *
     * validateMergedItemData() sẽ gọi ItemFactory.createItem(...),
     * factory phát hiện sai định dạng số và ném IllegalArgumentException.
     * Service bọc lại thành ValidationException INVALID_PARAMETER.
     */
    @Test
    void updateItemInfoShouldThrowInvalidParameterWhenIncomingDataIsInvalid() {
        Electronics item = sampleElectronics("item-invalid-update");

        when(itemDAO.findById("item-invalid-update")).thenReturn(Optional.of(item));

        Map<String, Object> incomingData = new HashMap<>();
        incomingData.put("startingPrice", "abc");

        ValidationException exception = assertThrows(ValidationException.class, () -> {
            itemService.updateItemInfo("item-invalid-update", ItemType.ELECTRONICS, incomingData);
        });

        assertValidationError(exception, ValidationErrorCode.INVALID_PARAMETER);
    }

    /**
     * Test updateItemInfo() khi DB update fail.
     *
     * Luồng:
     * - Item tồn tại và ACTIVE.
     * - Data hợp lệ.
     * - itemDAO.updateItem(liveItem) trả false.
     * - Service ném DATABASE_ERROR.
     */
    @Test
    void updateItemInfoShouldThrowDatabaseErrorWhenDaoUpdateFails() {
        Electronics item = sampleElectronics("item-update-fail");

        when(itemDAO.findById("item-update-fail")).thenReturn(Optional.of(item));
        when(itemDAO.updateItem(any(Item.class))).thenReturn(false);

        Map<String, Object> incomingData = new HashMap<>();
        incomingData.put("name", "Laptop Dell Updated");

        AuctionException exception = assertThrows(AuctionException.class, () -> {
            itemService.updateItemInfo("item-update-fail", ItemType.ELECTRONICS, incomingData);
        });

        assertAuctionError(exception, AuctionErrorCode.DATABASE_ERROR);
    }

    /**
     * Test updateItemInfo() thành công với Electronics.
     *
     * Expected:
     * - Các field chung được update.
     * - Các field riêng của Electronics được update.
     * - DAO updateItem được gọi.
     * - DTO trả về đúng dữ liệu mới.
     */
    @Test
    void updateItemInfoShouldUpdateElectronicsWhenValid() {
        Electronics item = sampleElectronics("item-update-success");

        when(itemDAO.findById("item-update-success")).thenReturn(Optional.of(item));
        when(itemDAO.updateItem(any(Item.class))).thenReturn(true);

        Map<String, Object> incomingData = new HashMap<>();
        incomingData.put("name", "Laptop Dell Updated");
        incomingData.put("description", "Mô tả mới");
        incomingData.put("imageUrl", "new-dell.png");
        incomingData.put("startingPrice", 15000000);
        incomingData.put("yearCreated", 2023);
        incomingData.put("brand", "Dell Pro");
        incomingData.put("warrantyMonths", 36);

        ItemDetailDTO dto = itemService.updateItemInfo(
                "item-update-success",
                ItemType.ELECTRONICS,
                incomingData
        );

        assertNotNull(dto);

        assertEquals("Laptop Dell Updated", dto.getItemName());
        assertEquals("Mô tả mới", dto.getDescription());
        assertEquals("new-dell.png", dto.getImageUrl());
        assertEquals(15000000.0, dto.getStartingPrice());
        assertEquals(2023, dto.getYearCreated());

        assertEquals("Dell Pro", dto.getBrand());
        assertEquals(36, dto.getWarrantyMonths());

        verify(itemDAO).updateItem(any(Item.class));
    }

    /**
     * Test updateItemInfo(String type, data) với type string.
     *
     * Case này đảm bảo overload dùng String hoạt động,
     * ví dụ client gửi "ELECTRONICS" từ JSON/socket.
     */
    @Test
    void updateItemInfoShouldAcceptTypeString() {
        Electronics item = sampleElectronics("item-update-string-type");

        when(itemDAO.findById("item-update-string-type")).thenReturn(Optional.of(item));
        when(itemDAO.updateItem(any(Item.class))).thenReturn(true);

        Map<String, Object> incomingData = new HashMap<>();
        incomingData.put("name", "New Name From String Type");

        ItemDetailDTO dto = itemService.updateItemInfo(
                "item-update-string-type",
                "ELECTRONICS",
                incomingData
        );

        assertEquals("New Name From String Type", dto.getItemName());
    }
}