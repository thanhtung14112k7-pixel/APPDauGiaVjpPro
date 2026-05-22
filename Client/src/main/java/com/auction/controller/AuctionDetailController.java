package com.auction.controller;

import com.auction.dto.AuctionDetailDTO;
import com.auction.dto.BidTransactionDTO;
import com.auction.dto.SocketResponse;
import com.auction.network.ClientAuctionApi;
import com.auction.util.SceneNavigator;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * AuctionDetailController là Controller phía Client cho màn hình chi tiết phiên đấu giá.
 *
 * Nhiệm vụ:
 * - Nhận auctionId từ màn hình danh sách đấu giá.
 * - Gọi ClientAuctionApi để gửi request GET_AUCTION_DETAIL sang Server.
 * - Nhận SocketResponse từ Server.
 * - Parse SocketResponse.body thành AuctionDetailDTO.
 * - Hiển thị thông tin chi tiết phiên đấu giá lên giao diện.
 * - Hiển thị lịch sử đặt giá của phiên đấu giá.
 * - Cho phép Bidder nhập số tiền và gửi request PLACE_BID.
 * - Refresh lại dữ liệu sau khi đặt giá thành công.
 *
 * Lưu ý:
 * - Controller này chỉ xử lý giao diện và gọi API phía Client.
 * - Controller này không tự xử lý nghiệp vụ đấu giá.
 * - Server mới là nơi kiểm tra quyền, kiểm tra số tiền bid và cập nhật dữ liệu thật.
 * - Phần realtime SUBSCRIBE_AUCTION chưa bật tự động ở đây.
 *   Lý do: server hiện broadcast message dạng chuỗi thường, chưa phải SocketResponse.event.
 *   Khi hoàn thiện LiveBiddingController và realtime listener thì sẽ nối phần đó sau.
 */
public class AuctionDetailController {
    private final ClientAuctionApi auctionApi = new ClientAuctionApi();

    /*
     * auctionId là ID phiên đấu giá đang được xem.
     * Giá trị này không lấy trực tiếp từ FXML.
     * AuctionListController sẽ truyền sang bằng hàm setAuctionId().
     */
    private String auctionId;

    /*
     * Lưu lại dữ liệu chi tiết hiện tại.
     * Khi đặt giá, controller có thể dùng currentAuctionDetail để kiểm tra nhanh dữ liệu đang hiển thị.
     */
    private AuctionDetailDTO currentAuctionDetail;

    /*
     * ObservableList là danh sách mà TableView theo dõi.
     * Khi bidHistoryItems thay đổi, bảng lịch sử bid sẽ cập nhật theo.
     */
    private final ObservableList<BidTransactionDTO> bidHistoryItems = FXCollections.observableArrayList();

    /*
     * Format thời gian để hiển thị lên giao diện cho dễ đọc.
     */
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * FXML cần có: <Label fx:id="itemNameLabel" ... />
     */
    @FXML
    private Label itemNameLabel;

    /**
     * FXML cần có: <Label fx:id="sellerLabel" ... />
     */
    @FXML
    private Label sellerLabel;

    /**
     * FXML cần có: <Label fx:id="currentPriceLabel" ... />
     */
    @FXML
    private Label currentPriceLabel;

    /**
     * FXML cần có: <Label fx:id="stepPriceLabel" ... />
     */
    @FXML
    private Label stepPriceLabel;

    /**
     * FXML cần có: <Label fx:id="statusLabel" ... />
     */
    @FXML
    private Label statusLabel;

    /**
     * FXML cần có: <Label fx:id="endTimeLabel" ... />
     */
    @FXML
    private Label endTimeLabel;

    /**
     * FXML cần có: <Label fx:id="messageLabel" ... />
     */
    @FXML
    private Label messageLabel;

    /**
     * FXML cần có: <TextArea fx:id="descriptionTextArea" ... />
     */
    @FXML
    private TextArea descriptionTextArea;

    /**
     * FXML cần có: <ImageView fx:id="itemImageView" ... />
     */
    @FXML
    private ImageView itemImageView;

    /**
     * FXML cần có: <TextField fx:id="bidAmountField" ... />
     */
    @FXML
    private TextField bidAmountField;

    /**
     * FXML cần có: <TableView fx:id="bidHistoryTable" ... />
     */
    @FXML
    private TableView<BidTransactionDTO> bidHistoryTable;

    /**
     * FXML cần có: <TableColumn fx:id="bidderNameColumn" ... />
     */
    @FXML
    private TableColumn<BidTransactionDTO, String> bidderNameColumn;

    /**
     * FXML cần có: <TableColumn fx:id="bidAmountColumn" ... />
     */
    @FXML
    private TableColumn<BidTransactionDTO, Number> bidAmountColumn;

    /**
     * FXML cần có: <TableColumn fx:id="bidTimeColumn" ... />
     */
    @FXML
    private TableColumn<BidTransactionDTO, String> bidTimeColumn;

    /**
     * FXML cần có: <TableColumn fx:id="bidStatusColumn" ... />
     */
    @FXML
    private TableColumn<BidTransactionDTO, String> bidStatusColumn;

    /**
     * initialize() được JavaFX tự động gọi sau khi load auction-detail.fxml.
     *
     * Lưu ý:
     * - Tại thời điểm initialize(), auctionId thường chưa được truyền sang.
     * - Vì vậy initialize() chỉ cấu hình bảng.
     * - Dữ liệu thật sẽ được load trong setAuctionId().
     */
    @FXML
    public void initialize() {
        setupBidHistoryTable();
        showMessage("Chưa chọn phiên đấu giá.");
    }

    /**
     * Hàm để màn hình khác truyền auctionId vào AuctionDetailController.
     *
     * Luồng dự kiến:
     * - AuctionListController lấy selectedAuction.getAuctionId().
     * - Load auction-detail.fxml.
     * - Lấy controller.
     * - Gọi controller.setAuctionId(auctionId).
     *
     * Sau khi có auctionId, controller mới gọi Server để lấy chi tiết.
     */
    public void setAuctionId(String auctionId) {
        this.auctionId = auctionId;
        loadAuctionDetail();
    }

    /**
     * Cấu hình bảng lịch sử đặt giá.
     * TableView không tự biết field nào của BidTransactionDTO hiển thị ở cột nào,
     * nên ta phải chỉ rõ bằng setCellValueFactory().
     */
    private void setupBidHistoryTable() {
        if (bidHistoryTable == null) {
            return;
        }

        bidderNameColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(safeText(cellData.getValue().getBidderName()))
        );

        bidAmountColumn.setCellValueFactory(cellData ->
                new SimpleDoubleProperty(cellData.getValue().getAmount())
        );

        bidTimeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(formatDateTime(cellData.getValue().getTime()))
        );

        bidStatusColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(safeText(cellData.getValue().getStatus()))
        );

        bidHistoryTable.setItems(bidHistoryItems);
    }

    /**
     * Gửi request GET_AUCTION_DETAIL sang Server.
     * Đây là điểm nối chính giữa màn hình chi tiết và Server.
     */
    private void loadAuctionDetail() {
        if (isBlank(auctionId)) {
            showError("Không tìm thấy auctionId của phiên đấu giá.");
            return;
        }

        showMessage("Đang tải chi tiết phiên đấu giá...");

        SocketResponse response = auctionApi.getAuctionDetail(auctionId);

        if (response == null) {
            showError("Server không trả về phản hồi hợp lệ.");
            return;
        }

        if (!response.isSuccess()) {
            showError(response.getMessage());
            return;
        }

        AuctionDetailDTO detail = auctionApi.parseAuctionDetail(response);

        if (detail == null) {
            showError("Không thể đọc dữ liệu chi tiết phiên đấu giá.");
            return;
        }

        currentAuctionDetail = detail;
        displayAuctionDetail(detail);
        showMessage("Đã tải chi tiết phiên đấu giá.");
    }

    /**
     * Đưa dữ liệu từ AuctionDetailDTO lên các control trong FXML.
     */
    private void displayAuctionDetail(AuctionDetailDTO detail) {
        setLabelText(itemNameLabel, detail.getItemName());
        setLabelText(sellerLabel, "Người bán: " + safeText(detail.getSellerUsername()));
        setLabelText(currentPriceLabel, "Giá hiện tại: " + formatMoney(detail.getCurrentPrice()));
        setLabelText(stepPriceLabel, "Bước giá: " + formatMoney(detail.getStepPrice()));
        setLabelText(statusLabel, "Trạng thái: " + safeText(detail.getStatus()));
        setLabelText(endTimeLabel, "Kết thúc: " + formatDateTime(detail.getEndTime()));

        if (descriptionTextArea != null) {
            descriptionTextArea.setText(safeText(detail.getItemDescription()));
        }

        loadItemImage(detail.getImageUrl());
        loadBidHistory(detail.getBidHistory());
    }

    /**
     * Load ảnh vật phẩm nếu DTO có imageUrl.
     */
    private void loadItemImage(String imageUrl) {
        if (itemImageView == null) {
            return;
        }

        if (isBlank(imageUrl)) {
            itemImageView.setImage(null);
            return;
        }

        try {
            Image image = new Image(imageUrl, true);
            itemImageView.setImage(image);
        } catch (Exception e) {
            itemImageView.setImage(null);
            showMessage("Không thể tải ảnh vật phẩm.");
        }
    }

    /**
     * Cập nhật bảng lịch sử đặt giá.
     */
    private void loadBidHistory(List<BidTransactionDTO> bidHistory) {
        if (bidHistory == null) {
            bidHistory = Collections.emptyList();
        }

        bidHistoryItems.setAll(bidHistory);
    }

    /**
     * FXML cần có: <Button onAction="#handlePlaceBid" ... />
     *
     * Luồng đặt giá:
     * - Đọc số tiền từ bidAmountField.
     * - Kiểm tra dữ liệu nhập cơ bản ở Client.
     * - Gửi PLACE_BID sang Server.
     * - Nếu thành công, refresh lại chi tiết phiên.
     */
    @FXML
    private void handlePlaceBid() {
        if (isBlank(auctionId)) {
            showError("Không tìm thấy phiên đấu giá để đặt giá.");
            return;
        }

        Double amount = readBidAmount();

        if (amount == null) {
            return;
        }

        if (amount <= 0) {
            showError("Số tiền đặt giá phải lớn hơn 0.");
            return;
        }

        /*
         * Kiểm tra nhanh ở Client để người dùng biết lỗi sớm.
         * Server vẫn là nơi kiểm tra thật, vì Client không đáng tin tuyệt đối.
         */
        if (currentAuctionDetail != null) {
            double minimumAmount = currentAuctionDetail.getCurrentPrice() + currentAuctionDetail.getStepPrice();

            if (amount < minimumAmount) {
                showError("Giá đặt tối thiểu là " + formatMoney(minimumAmount) + ".");
                return;
            }
        }

        SocketResponse response = auctionApi.placeBid(auctionId, amount);

        if (response == null) {
            showError("Server không trả về phản hồi hợp lệ.");
            return;
        }

        if (!response.isSuccess()) {
            showError(response.getMessage());
            return;
        }

        if (bidAmountField != null) {
            bidAmountField.clear();
        }

        showInfo(response.getMessage());
        loadAuctionDetail();
    }
    /**
     * FXML can co: <Button onAction="#handleOpenLiveBidding" ... />
     *
     * Luong vao phong live bidding:
     * - Kiem tra auctionId hien tai co hop le khong.
     * - Chuyen sang man live-bidding.fxml thong qua SceneNavigator.
     * - SceneNavigator se truyen auctionId sang LiveBiddingController.
     * - LiveBiddingController dung auctionId nay de subscribe realtime room.
     */
    @FXML
    private void handleOpenLiveBidding() {
        if (isBlank(auctionId)) {
            showError("Khong tim thay phien dau gia de vao phong live bidding.");
            return;
        }

        SceneNavigator.showLiveBidding(auctionId);
    }
    /**
     * Đọc và parse số tiền người dùng nhập.
     */
    private Double readBidAmount() {
        if (bidAmountField == null || isBlank(bidAmountField.getText())) {
            showError("Vui lòng nhập số tiền muốn đặt.");
            return null;
        }

        try {
            String rawAmount = bidAmountField.getText().trim().replace(",", ".");
            return Double.parseDouble(rawAmount);
        } catch (NumberFormatException e) {
            showError("Số tiền đặt giá không hợp lệ.");
            return null;
        }
    }

    /**
     * FXML cần có: <Button onAction="#handleRefresh" ... />
     */
    @FXML
    private void handleRefresh() {
        loadAuctionDetail();
    }

    /**
     * FXML cần có: <Button onAction="#handleBack" ... />
     *
     * Hiện SceneNavigator chưa có showAuctionList().
     * Tạm thời quay về Dashboard để code compile được.
     * Sau khi bổ sung điều hướng màn Auction List, đổi thành SceneNavigator.showAuctionList().
     */
    @FXML
    private void handleBack() {
        /*
         * Quay lại màn danh sách đấu giá.
         * Không quay về Dashboard nữa vì luồng đúng là:
         * Auction List -> Auction Detail -> Auction List.
         */
        SceneNavigator.showAuctionList();
    }

    /**
     * Gán text cho Label nhưng kiểm tra null để tránh lỗi nếu FXML chưa gắn fx:id.
     */
    private void setLabelText(Label label, String text) {
        if (label != null) {
            label.setText(safeText(text));
        }
    }

    /**
     * Format tiền để hiển thị.
     */
    private String formatMoney(double value) {
        return String.format("%,.0f", value);
    }

    /**
     * Format LocalDateTime để hiển thị.
     */
    private String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }

        return value.format(dateTimeFormatter);
    }

    /**
     * Tránh hiển thị null lên giao diện.
     */
    private String safeText(String value) {
        return value == null ? "" : value;
    }

    /**
     * Không dùng String.isBlank() để tránh lỗi nếu IDE compile nhầm language level thấp.
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Hiển thị message nhẹ trên màn hình.
     */
    private void showMessage(String message) {
        if (messageLabel != null) {
            messageLabel.setText(safeText(message));
        }
    }

    /**
     * Hiển thị lỗi.
     */
    private void showError(String message) {
        showMessage(message);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(safeText(message));
        alert.showAndWait();
    }

    /**
     * Hiển thị thông báo thông thường.
     */
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(safeText(message));
        alert.showAndWait();
    }
}