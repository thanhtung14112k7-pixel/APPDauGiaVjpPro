package com.auction.models.User;

import com.auction.enums.UserRole;
import com.auction.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BidderTest {

    /**
     * Test constructor đăng ký mới của Bidder.
     *
     * Trong Bidder.java, constructor:
     * public Bidder(String username, String email, String password)
     *
     * sẽ gọi:
     * super(username, email, password, UserRole.BIDDER);
     *
     * Vì vậy Bidder mới tạo phải có role là BIDDER.
     */
    @Test
    void newBidderShouldHaveBidderRole() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );

        // getUserRole() trả về enum UserRole
        assertEquals(UserRole.BIDDER, bidder.getUserRole());

        // getRole() trả về String từ role.toString()
        assertEquals("BIDDER", bidder.getRole());
    }

    /**
     * Test joinedAuctionIds ban đầu.
     *
     * Trong constructor Bidder, code có:
     * this.joinedAuctionIds = new ArrayList<>();
     *
     * Vì vậy bidder mới tạo chưa tham gia auction nào,
     * danh sách joinedAuctionIds phải rỗng.
     */
    @Test
    void newBidderShouldStartWithEmptyJoinedAuctionIds() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );

        // Kiểm tra danh sách auction đã tham gia ban đầu là rỗng
        assertTrue(bidder.getJoinedAuctionIds().isEmpty());
    }

    /**
     * Test addJoinedAuction() khi auctionId chưa tồn tại.
     *
     * Trong Bidder.java:
     * nếu joinedAuctionIds chưa chứa auctionId,
     * method sẽ add vào list và return true.
     */
    @Test
    void addJoinedAuctionShouldReturnTrueWhenAuctionIdIsNew() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );

        // Thêm auction-1 lần đầu
        boolean result = bidder.addJoinedAuction("auction-1");

        // Vì auction-1 chưa tồn tại nên phải trả về true
        assertTrue(result);

        // Danh sách bây giờ phải chứa đúng auction-1
        assertEquals(List.of("auction-1"), bidder.getJoinedAuctionIds());
    }

    /**
     * Test addJoinedAuction() khi auctionId đã tồn tại.
     *
     * Bidder không nên lưu trùng cùng một auctionId.
     *
     * Case:
     * add auction-1 lần 1 => true
     * add auction-1 lần 2 => false
     * danh sách vẫn chỉ có 1 phần tử
     */
    @Test
    void addJoinedAuctionShouldReturnFalseWhenAuctionIdAlreadyExists() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );

        // Thêm lần đầu
        bidder.addJoinedAuction("auction-1");

        // Thêm lại cùng auctionId
        boolean result = bidder.addJoinedAuction("auction-1");

        // Vì auctionId đã tồn tại nên phải trả về false
        assertFalse(result);

        // Danh sách không được bị thêm trùng
        assertEquals(1, bidder.getJoinedAuctionIds().size());
        assertEquals("auction-1", bidder.getJoinedAuctionIds().get(0));
    }

    /**
     * Test removeJoinedAuction() khi auctionId tồn tại.
     *
     * Trong Bidder.java:
     * return joinedAuctionIds.remove(auctionId);
     *
     * Nếu auctionId có trong list, remove thành công và trả về true.
     */
    @Test
    void removeJoinedAuctionShouldReturnTrueWhenAuctionIdExists() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );

        // Chuẩn bị dữ liệu: bidder đã tham gia auction-1
        bidder.addJoinedAuction("auction-1");

        // Xóa auction-1 khỏi danh sách
        boolean result = bidder.removeJoinedAuction("auction-1");

        // Xóa thành công nên trả về true
        assertTrue(result);

        // Sau khi xóa, danh sách phải rỗng
        assertTrue(bidder.getJoinedAuctionIds().isEmpty());
    }

    /**
     * Test removeJoinedAuction() khi auctionId không tồn tại.
     *
     * Nếu auctionId không có trong list,
     * remove sẽ không xóa được gì và trả về false.
     */
    @Test
    void removeJoinedAuctionShouldReturnFalseWhenAuctionIdDoesNotExist() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );

        // Thử xóa một auction chưa từng được thêm
        boolean result = bidder.removeJoinedAuction("not-exist");

        // Không tồn tại nên remove phải trả về false
        assertFalse(result);
    }

    /**
     * Test getJoinedAuctionIds().
     *
     * Trong Bidder.java, method này trả về:
     * Collections.unmodifiableList(joinedAuctionIds)
     *
     * Ý nghĩa:
     * code bên ngoài chỉ được đọc danh sách,
     * không được sửa trực tiếp danh sách nội bộ của Bidder.
     */
    @Test
    void getJoinedAuctionIdsShouldReturnUnmodifiableList() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );

        bidder.addJoinedAuction("auction-1");

        // Lấy danh sách từ getter
        List<String> joinedAuctionIds = bidder.getJoinedAuctionIds();

        // Vì list là unmodifiable, add trực tiếp phải ném UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () -> {
            joinedAuctionIds.add("auction-2");
        });
    }

    /**
     * Test setJoinedAuctionIds().
     *
     * Method này dành cho DAO bơm dữ liệu từ database lên object Bidder.
     *
     * Case:
     * DB trả về auction-1, auction-2
     * gọi setJoinedAuctionIds(...)
     * object Bidder phải có đúng 2 auction đó.
     */
    @Test
    void setJoinedAuctionIdsShouldReplaceInternalListWithCopiedList() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );

        List<String> idsFromDb = new ArrayList<>();
        idsFromDb.add("auction-1");
        idsFromDb.add("auction-2");

        // Giả lập DAO bơm dữ liệu vào Bidder
        bidder.setJoinedAuctionIds(idsFromDb);

        // Kiểm tra dữ liệu trong Bidder đã đúng chưa
        assertEquals(List.of("auction-1", "auction-2"), bidder.getJoinedAuctionIds());
    }

    /**
     * Test setJoinedAuctionIds() có copy list đầu vào hay không.
     *
     * Trong Bidder.java:
     * this.joinedAuctionIds = new ArrayList<>(idsFromDB);
     *
     * Nghĩa là Bidder tạo một list mới,
     * không dùng trực tiếp list bên ngoài.
     *
     * Vì vậy sau khi set xong, nếu list gốc bị sửa,
     * dữ liệu trong Bidder không được thay đổi theo.
     */
    @Test
    void setJoinedAuctionIdsShouldCopyInputListNotReuseIt() {
        Bidder bidder = new Bidder(
                "bidder1",
                "bidder1@example.com",
                "hashed-password"
        );

        List<String> idsFromDb = new ArrayList<>();
        idsFromDb.add("auction-1");

        // Set dữ liệu vào Bidder
        bidder.setJoinedAuctionIds(idsFromDb);

        // Sửa list gốc sau khi đã set
        idsFromDb.add("auction-2");

        // Nếu Bidder copy đúng, danh sách trong Bidder vẫn chỉ có auction-1
        assertEquals(List.of("auction-1"), bidder.getJoinedAuctionIds());
    }

    /**
     * Test constructor thứ 2 của Bidder: constructor load từ DB.
     *
     * Constructor này nhận:
     * id, username, email, password, role, availableBalance,
     * frozenBalance, status, createdAt, updatedAt.
     *
     * Mục tiêu:
     * kiểm tra object load từ DB có giữ đúng dữ liệu hay không.
     */
    @Test
    void dbConstructorShouldRestoreBidderData() {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        LocalDateTime updatedAt = LocalDateTime.now();

        Bidder bidder = new Bidder(
                "user-id-1",
                "bidder1",
                "bidder1@example.com",
                "hashed-password",
                UserRole.BIDDER,
                100.0,
                30.0,
                UserStatus.ACTIVE,
                createdAt,
                updatedAt
        );

        // Kiểm tra thông tin cơ bản
        assertEquals("bidder1", bidder.getUsername());
        assertEquals("bidder1@example.com", bidder.getEmail());
        assertEquals("hashed-password", bidder.getPassword());

        // Kiểm tra role
        assertEquals(UserRole.BIDDER, bidder.getUserRole());
        assertEquals("BIDDER", bidder.getRole());

        // Kiểm tra số dư được load từ DB
        assertEquals(100.0, bidder.getAvailableBalance());
        assertEquals(30.0, bidder.getFrozenBalance());

        // Kiểm tra trạng thái tài khoản
        assertEquals(UserStatus.ACTIVE, bidder.getStatus());

        // Constructor DB vẫn khởi tạo joinedAuctionIds là list rỗng
        assertTrue(bidder.getJoinedAuctionIds().isEmpty());
    }
}