package com.auction.service;

import com.auction.dao.AuctionDAO;
import com.auction.dao.BidTransactionDAO;
import com.auction.dao.ItemDAO;
import com.auction.dao.LogDAO;
import com.auction.dao.UserDAO;
import com.auction.dto.AuctionSummaryDTO;
import com.auction.enums.AuctionStatus;
import com.auction.enums.ItemStatus;
import com.auction.exception.AuctionErrorCode;
import com.auction.exception.AuctionException;
import com.auction.manage.AuctionManage;
import com.auction.manage.ProductManage;
import com.auction.models.Auction.Auction;
import com.auction.models.Item.Electronics;
import com.auction.models.Item.Item;
import com.auction.models.User.Bidder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuctionServiceTest {

    private AuctionService auctionService;

    private UserDAO userDAO;
    private AuctionDAO auctionDAO;
    private ItemDAO itemDAO;
    private BidTransactionDAO bidTransactionDAO;
    private LogDAO logDAO;

    private AuctionManage auctionManage;
    private ProductManage productManage;

    /**
     * Hàm này chạy trước mỗi test.
     *
     * Mục tiêu:
     * - Tạo AuctionService thật để test logic service.
     * - Tạo DAO giả bằng Mockito để không gọi database thật.
     * - Inject DAO giả vào AuctionService bằng reflection.
     * - Lấy các singleton manager thật: AuctionManage, ProductManage.
     *
     * Vì AuctionService đang khai báo DAO là private final,
     * nên ta không truyền mock qua constructor được.
     * Do đó phải dùng reflection giống AuthServiceTest.
     */
    @BeforeEach
    void setUp() throws Exception {
        auctionService = new AuctionService();

        userDAO = mock(UserDAO.class);
        auctionDAO = mock(AuctionDAO.class);
        itemDAO = mock(ItemDAO.class);
        bidTransactionDAO = mock(BidTransactionDAO.class);
        logDAO = mock(LogDAO.class);

        injectField(auctionService, "userDAO", userDAO);
        injectField(auctionService, "auctionDAO", auctionDAO);
        injectField(auctionService, "itemDAO", itemDAO);
        injectField(auctionService, "bidTransactionDAO", bidTransactionDAO);
        injectField(auctionService, "logDAO", logDAO);

        auctionManage = AuctionManage.getInstance();
        productManage = ProductManage.getInstance();
    }

    /**
     * Helper dùng reflection để thay field private trong AuctionService.
     *
     * Ví dụ:
     * AuctionService có private final ItemDAO itemDAO = new ItemDAOImpl();
     *
     * Trong test, ta thay itemDAO thật bằng itemDAO mock.
     */
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Helper kiểm tra AuctionException có đúng mã lỗi không.
     *
     * AuctionException kế thừa BaseException,
     * nên có method getErrorCode().
     */
    private void assertAuctionError(AuctionException exception, AuctionErrorCode expectedError) {
        assertEquals(expectedError.getCode(), exception.getErrorCode());
    }

    /**
     * Helper tạo item Electronics có id tự đặt.
     *
     * Lý do cần setId:
     * ProductManage lưu item theo item.getId().
     * Nếu id random thì khi gọi productManage.getProduct("item-1") sẽ không thấy.
     */
    private Electronics sampleItem(String itemId) {
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
     * Helper tạo auction mẫu.
     *
     * Auction cần Item, sellerId, stepPrice, startTime, endTime.
     */
    private Auction sampleAuction(String auctionId, Item item) {
        Auction auction = new Auction(
                item,
                "seller-1",
                100000,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusHours(1)
        );

        auction.setId(auctionId);
        return auction;
    }

    private LocalDateTime startTime() {
        return LocalDateTime.now().plusMinutes(1);
    }

    private LocalDateTime endTime() {
        return LocalDateTime.now().plusHours(1);
    }

    // =========================================================
    // TEST createAuction()
    // =========================================================

    /**
     * Test createAuction() khi item không tồn tại.
     *
     * Luồng code trong AuctionService:
     * 1. Tìm item trong ProductManage RAM.
     * 2. Nếu RAM không có thì gọi itemDAO.findById(itemId).
     * 3. Nếu DB cũng không có thì ném AuctionException ITEM_NOT_FOUND.
     */
    @Test
    void createAuctionShouldThrowWhenItemNotFound() {
        String itemId = "missing-item";

        // Giả vờ DB không tìm thấy item
        when(itemDAO.findById(itemId)).thenReturn(Optional.empty());

        AuctionException exception = assertThrows(AuctionException.class, () -> {
            auctionService.createAuction(
                    itemId,
                    "seller-1",
                    100000,
                    startTime(),
                    endTime()
            );
        });

        // Phải ném đúng lỗi ITEM_NOT_FOUND
        assertAuctionError(exception, AuctionErrorCode.ITEM_NOT_FOUND);

        // Vì item không tồn tại nên không được insert auction
        verify(auctionDAO, never()).insertAuction(any(Auction.class));
    }

    /**
     * Test createAuction() khi item đã bị khóa.
     *
     * Item chỉ được tạo auction khi status là ACTIVE.
     * Nếu item là INACTIVE hoặc SOLD thì service phải ném ITEM_IS_LOCKED.
     */
    @Test
    void createAuctionShouldThrowWhenItemIsNotActive() {
        String itemId = "item-locked";

        Item item = new Electronics(
                itemId,
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

        // RAM không có, DB trả về item INACTIVE
        when(itemDAO.findById(itemId)).thenReturn(Optional.of(item));

        AuctionException exception = assertThrows(AuctionException.class, () -> {
            auctionService.createAuction(
                    itemId,
                    "seller-1",
                    100000,
                    startTime(),
                    endTime()
            );
        });

        // Phải báo item bị khóa
        assertAuctionError(exception, AuctionErrorCode.ITEM_IS_LOCKED);

        // Không được lưu auction nếu item không ACTIVE
        verify(auctionDAO, never()).insertAuction(any(Auction.class));
    }

    /**
     * Test createAuction() khi insert auction xuống DB thất bại.
     *
     * Luồng:
     * - Item tồn tại và ACTIVE.
     * - auctionDAO.insertAuction(...) trả false.
     * - Service phải ném DATABASE_ERROR.
     */
    @Test
    void createAuctionShouldThrowWhenInsertAuctionFails() {
        String itemId = "item-insert-fail";
        Item item = sampleItem(itemId);

        when(itemDAO.findById(itemId)).thenReturn(Optional.of(item));
        when(auctionDAO.insertAuction(any(Auction.class))).thenReturn(false);

        AuctionException exception = assertThrows(AuctionException.class, () -> {
            auctionService.createAuction(
                    itemId,
                    "seller-1",
                    100000,
                    startTime(),
                    endTime()
            );
        });

        assertAuctionError(exception, AuctionErrorCode.DATABASE_ERROR);

        // Có gọi insert auction nhưng thất bại
        verify(auctionDAO).insertAuction(any(Auction.class));

        // Vì insert fail nên không được update status item
        verify(itemDAO, never()).updateStatus(eq(itemId), anyString());
    }

    /**
     * Test createAuction() thành công.
     *
     * Expected:
     * - auctionDAO.insertAuction(...) được gọi.
     * - itemDAO.updateStatus(itemId, "INACTIVE") được gọi.
     * - item trên RAM đổi trạng thái sang INACTIVE.
     *
     * Lưu ý:
     * Nếu test fail ở assert item status,
     * khả năng cao là Item.setStatus() đang bị bug:
     * this.status = status;
     * phải sửa thành:
     * this.status = newStatus;
     */
    @Test
    void createAuctionShouldCreateAuctionAndLockItemWhenValid() {
        String itemId = "item-create-success";
        Item item = sampleItem(itemId);

        when(itemDAO.findById(itemId)).thenReturn(Optional.of(item));
        when(auctionDAO.insertAuction(any(Auction.class))).thenReturn(true);

        auctionService.createAuction(
                itemId,
                "seller-1",
                100000,
                startTime(),
                endTime()
        );

        // Kiểm tra service đã lưu auction xuống DB
        verify(auctionDAO).insertAuction(any(Auction.class));

        // Kiểm tra service đã khóa item trong DB
        verify(itemDAO).updateStatus(itemId, ItemStatus.INACTIVE.name());

        // Kiểm tra item trên RAM cũng bị khóa
        assertEquals(ItemStatus.INACTIVE, item.getStatus());
    }

    // =========================================================
    // TEST getJoinedAuctionsSummary()
    // =========================================================

    /**
     * Test getJoinedAuctionsSummary() khi bidder chưa tham gia auction nào.
     *
     * Bidder mới tạo có joinedAuctionIds rỗng.
     * Service phải trả về list rỗng.
     */
    @Test
    void getJoinedAuctionsSummaryShouldReturnEmptyListWhenBidderJoinedNothing() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );

        List<AuctionSummaryDTO> result = auctionService.getJoinedAuctionsSummary(bidder);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * Test getJoinedAuctionsSummary() khi auction không có trên RAM nhưng có trong DB.
     *
     * Luồng:
     * - Bidder có joinedAuctionIds chứa auction-1.
     * - AuctionManage RAM không có auction-1.
     * - auctionDAO.findById("auction-1") trả về auction.
     * - Service convert auction sang AuctionSummaryDTO.
     */
    @Test
    void getJoinedAuctionsSummaryShouldLoadAuctionFromDatabaseWhenNotInRam() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );
        bidder.addJoinedAuction("auction-1");

        Item item = sampleItem("item-summary-1");
        Auction auction = sampleAuction("auction-1", item);

        when(auctionDAO.findById("auction-1")).thenReturn(Optional.of(auction));

        List<AuctionSummaryDTO> result = auctionService.getJoinedAuctionsSummary(bidder);

        assertEquals(1, result.size());

        AuctionSummaryDTO dto = result.get(0);

        // DTO phải chứa id auction
        assertEquals("auction-1", dto.getAuctionId());

        // Vì auction có item, itemName phải là tên item
        assertEquals("Laptop Dell", dto.getItemName());

        // currentPrice lấy từ auction
        assertEquals(auction.getCurrentPrice(), dto.getCurrentPrice());

        // status lấy từ auction.getStatus().name()
        assertEquals(auction.getStatus().name(), dto.getStatus());

        // endTime lấy từ auction
        assertEquals(auction.getEndTime(), dto.getEndTime());
    }

    /**
     * Test getJoinedAuctionsSummary() khi auctionId bị thiếu trong cả RAM và DB.
     *
     * Nếu bidder có joinedAuctionId nhưng service không tìm thấy auction,
     * service sẽ bỏ qua id đó và không add vào result.
     */
    @Test
    void getJoinedAuctionsSummaryShouldSkipMissingAuction() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );
        bidder.addJoinedAuction("missing-auction");

        when(auctionDAO.findById("missing-auction")).thenReturn(Optional.empty());

        List<AuctionSummaryDTO> result = auctionService.getJoinedAuctionsSummary(bidder);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    /**
     * Test getJoinedAuctionsSummary() khi auction có trong DB nhưng auction chưa load Item.
     *
     * convertToSummaryDTO() có fallback:
     * nếu auction.getItem() == null
     * thì itemName = "Vật phẩm #" + auction.getItemId()
     */
    @Test
    void getJoinedAuctionsSummaryShouldUseFallbackNameWhenAuctionItemIsNull() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );
        bidder.addJoinedAuction("auction-no-item");

        Item item = sampleItem("item-no-object");
        Auction auction = sampleAuction("auction-no-item", item);

        // Chủ động set item null để test fallback
        auction.setItem(null);

        when(auctionDAO.findById("auction-no-item")).thenReturn(Optional.of(auction));

        List<AuctionSummaryDTO> result = auctionService.getJoinedAuctionsSummary(bidder);

        assertEquals(1, result.size());
        assertEquals("Vật phẩm #" + auction.getItemId(), result.get(0).getItemName());
    }

    // =========================================================
    // TEST getAllActiveAuctions()
    // =========================================================

    /**
     * Test getAllActiveAuctions().
     *
     * Method này lấy tất cả auction trong AuctionManage RAM,
     * sau đó chỉ giữ auction có status OPEN hoặc RUNNING.
     *
     * Vì AuctionManage là singleton dùng chung,
     * test này chủ yếu kiểm tra:
     * - result không null.
     * - mọi DTO trả về phải có status OPEN hoặc RUNNING.
     */
    @Test
    void getAllActiveAuctionsShouldReturnOnlyOpenOrRunningAuctions() {
        List<AuctionSummaryDTO> result = auctionService.getAllActiveAuctions();

        assertNotNull(result);

        for (AuctionSummaryDTO dto : result) {
            assertTrue(
                    dto.getStatus().equals(AuctionStatus.OPEN.name())
                            || dto.getStatus().equals(AuctionStatus.RUNNING.name())
            );
        }
    }

    // =========================================================
    // TEST loadAuctionsToRAM()
    // =========================================================

    /**
     * Test loadAuctionsToRAM().
     *
     * Luồng code:
     * 1. Service gọi auctionDAO.findByStatuses([OPEN, RUNNING]).
     * 2. Với mỗi auction lấy từ DB, service gọi itemDAO.findById(itemId).
     * 3. Nếu tìm thấy item thì auction.setItem(item).
     * 4. Service add auction vào AuctionManage RAM.
     */
    @Test
    void loadAuctionsToRAMShouldLoadOpenAndRunningAuctionsFromDatabase() {
        Item item = sampleItem("item-load-1");
        Auction auction = sampleAuction("auction-load-1", item);

        when(auctionDAO.findByStatuses(anyList())).thenReturn(List.of(auction));
        when(itemDAO.findById(auction.getItemId())).thenReturn(Optional.of(item));

        auctionService.loadAuctionsToRAM();

        // Kiểm tra service có tìm auction theo status OPEN/RUNNING
        verify(auctionDAO).findByStatuses(anyList());

        // Kiểm tra service có load item cho auction
        verify(itemDAO).findById(auction.getItemId());

        // Sau khi load, auction phải có item
        assertNotNull(auction.getItem());
        assertEquals("Laptop Dell", auction.getItem().getName());

        // Auction phải được add vào RAM
        assertNotNull(auctionManage.getAuctionById("auction-load-1"));
    }
}