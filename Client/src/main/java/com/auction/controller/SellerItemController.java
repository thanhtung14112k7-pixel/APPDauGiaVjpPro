package com.auction.controller;

import com.auction.dto.ItemSummaryDTO;
import com.auction.dto.SocketResponse;
import com.auction.network.ClientAuctionApi;
import com.auction.util.SceneNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * SellerItemController là Controller phía Client cho màn hình Seller tạo phiên đấu giá.
 *
 * Nhiệm vụ:
 * - Nhận itemId của vật phẩm mà Seller muốn đưa lên đấu giá.
 * - Cho Seller nhập bước giá, thời gian bắt đầu và thời gian kết thúc.
 * - Kiểm tra dữ liệu nhập cơ bản ở Client trước khi gửi request.
 * - Gọi ClientAuctionApi.createAuction(...) để gửi action CREATE_AUCTION sang Server.
 * - Nhận SocketResponse từ Server.
 * - Hiển thị kết quả tạo phiên đấu giá thành công hoặc thất bại.
 *
 * Lưu ý:
 * - Controller này chỉ xử lý giao diện và gọi API phía Client.
 * - Controller này không tự tạo Auction trong database.
 * - Controller này không tự lấy sellerId từ giao diện.
 * - Server sẽ lấy sellerId từ ClientSession để tránh giả mạo người bán.
 * - Server mới là nơi kiểm tra quyền SELLER/ADMIN và xử lý nghiệp vụ thật.
 *
 * Giai đoạn hiện tại:
 * - Vì chưa có ClientItemApi để lấy danh sách item của Seller,
 *   controller tạm hỗ trợ nhập itemId thủ công.
 * - Sau này nếu có màn danh sách item, có thể gọi setSelectedItem(...) để truyền ItemSummaryDTO vào đây.
 */
public class SellerItemController {
    private final ClientAuctionApi auctionApi = new ClientAuctionApi();

    /*
     * selectedItem lưu vật phẩm được chọn từ màn hình danh sách item.
     * Hiện tại có thể null vì ta chưa làm ClientItemApi.
     */
    private ItemSummaryDTO selectedItem;

    /**
     * FXML cần có: <Label fx:id="selectedItemIdLabel" ... />
     */
    @FXML
    private Label selectedItemIdLabel;

    /**
     * FXML cần có: <Label fx:id="itemNameLabel" ... />
     */
    @FXML
    private Label itemNameLabel;

    /**
     * FXML cần có: <TextField fx:id="itemIdField" ... />
     *
     * Giai đoạn chưa có màn chọn item, Seller nhập itemId trực tiếp ở đây.
     */
    @FXML
    private TextField itemIdField;

    /**
     * FXML cần có: <TextField fx:id="stepPriceField" ... />
     */
    @FXML
    private TextField stepPriceField;

    /**
     * FXML cần có: <TextField fx:id="startTimeField" ... />
     *
     * Format cần nhập:
     * - 2026-05-20T10:30:00
     */
    @FXML
    private TextField startTimeField;

    /**
     * FXML cần có: <TextField fx:id="endTimeField" ... />
     *
     * Format cần nhập:
     * - 2026-05-20T11:30:00
     */
    @FXML
    private TextField endTimeField;

    /**
     * FXML cần có: <Label fx:id="messageLabel" ... />
     */
    @FXML
    private Label messageLabel;

    @FXML
    private StackPane rootContainer;

    @FXML
    private Label dynamicTitleLabel;

    @FXML
    private Label formTitleLabel;

    @FXML
    private Button btnSubmit;

    /**
     * initialize() được JavaFX tự động gọi sau khi load FXML.
     *
     * Nhiệm vụ:
     * - Gợi ý sẵn thời gian bắt đầu/kết thúc để người dùng dễ nhập đúng format.
     * - Hiển thị trạng thái ban đầu của màn hình.
     */
    @FXML
    public void initialize() {
        // --- ĐOẠN CODE TỰ ĐỘNG ÁP DỤNG THEME KHI VỪA MỞ MÀN HÌNH SIM ---
        rootContainer.getStylesheets().clear();
        String currentPath = SceneNavigator.isAppDarkMode
                ? "/com/auction/client/view/dark.css"
                : "/com/auction/client/view/light.css";
        try {
            String css = Objects.requireNonNull(getClass().getResource(currentPath)).toExternalForm();
            rootContainer.getStylesheets().add(css);
        } catch (Exception e) {
            System.out.println("Không thể nạp theme hệ thống: " + currentPath);
        }
        // -------------------------------------------------------

        if (SceneNavigator.isAppDarkMode) {
            // 1. Giao diện tối - Kích hoạt các yếu tố ấn tượng ngầm
            if (dynamicTitleLabel != null) {
                dynamicTitleLabel.setText("TIẾN HÀNH GIAO DỊCH NGẦM");
                dynamicTitleLabel.setStyle("-fx-text-fill: #ff9f43;"); // Màu vàng cam rực của Dark Mode
            }
            if (formTitleLabel != null) {
                formTitleLabel.setText("Thông Tin Giao Dịch Bí Mật");
            }
            if (btnSubmit != null) {
                btnSubmit.setText("KÍCH HOẠT GIAO DỊCH NGẦM 🏴‍☠️");
            }
        } else {
            // 2. Giao diện sáng - Quay về chuẩn mực đấu giá thông thường
            if (dynamicTitleLabel != null) {
                dynamicTitleLabel.setText("ĐĂNG KÝ PHIÊN ĐẤU GIÁ");
                dynamicTitleLabel.setStyle("-fx-text-fill: #1877f2;"); // Màu xanh dương của Light Mode
            }
            if (formTitleLabel != null) {
                formTitleLabel.setText("Thông Tin Phiên Đấu Giá");
            }
            if (btnSubmit != null) {
                btnSubmit.setText("XÁC NHẬN TẠO PHIÊN ĐẤU GIÁ 🚀");
            }
        }
        // =====================================================================

        fillDefaultTimeIfEmpty();
        showMessage("Nhập thông tin để tạo phiên đấu giá.");
    }

    /**
     * Hàm này dùng cho giai đoạn sau.
     *
     * Khi đã có màn danh sách item của Seller:
     * - Người dùng chọn một item.
     * - Controller danh sách item load màn SellerItemController.
     * - Sau đó gọi setSelectedItem(item) để truyền item đã chọn sang đây.
     */
    public void setSelectedItem(ItemSummaryDTO item) {
        this.selectedItem = item;

        if (item == null) {
            return;
        }

        if (itemIdField != null) {
            itemIdField.setText(item.getItemId());
        }

        setLabelText(selectedItemIdLabel, "Item ID: " + safeText(item.getItemId()));
        setLabelText(itemNameLabel, "Vật phẩm: " + safeText(item.getItemName()));
    }

    /**
     * FXML cần có: <Button onAction="#handleCreateAuction" ... />
     *
     * Luồng tạo phiên:
     * 1. Đọc dữ liệu từ form.
     * 2. Validate dữ liệu nhập.
     * 3. Gửi CREATE_AUCTION sang Server.
     * 4. Hiển thị kết quả trả về từ Server.
     */
    @FXML
    private void handleCreateAuction() {
        String itemId = readItemId();
        if (isBlank(itemId)) {
            showError("Vui lòng nhập itemId của vật phẩm.");
            return;
        }

        Double stepPrice = readStepPrice();
        if (stepPrice == null) {
            return;
        }

        LocalDateTime startTime = readDateTime(startTimeField, "thời gian bắt đầu");
        if (startTime == null) {
            return;
        }

        LocalDateTime endTime = readDateTime(endTimeField, "thời gian kết thúc");
        if (endTime == null) {
            return;
        }

        if (!endTime.isAfter(startTime)) {
            showError("Thời gian kết thúc phải sau thời gian bắt đầu.");
            return;
        }

        /*
         * Client chỉ gửi dữ liệu cần thiết để tạo phiên.
         * Không gửi sellerId vì Server sẽ lấy sellerId từ ClientSession.
         */
        SocketResponse response = auctionApi.createAuction(
                itemId,
                stepPrice,
                startTime.toString(),
                endTime.toString()
        );

        if (response == null) {
            showError("Server không trả về phản hồi hợp lệ.");
            return;
        }

        if (!response.isSuccess()) {
            showError(response.getMessage());
            return;
        }

        showInfo(response.getMessage());
        showMessage("Tạo phiên đấu giá thành công.");
    }

    /**
     * Lấy itemId.
     *
     * Ưu tiên:
     * - Nếu đã có selectedItem thì lấy từ selectedItem.
     * - Nếu chưa có selectedItem thì lấy từ itemIdField.
     */
    private String readItemId() {
        if (selectedItem != null && !isBlank(selectedItem.getItemId())) {
            return selectedItem.getItemId();
        }

        if (itemIdField == null) {
            return null;
        }

        return itemIdField.getText();
    }

    /**
     * Đọc và parse bước giá.
     */
    private Double readStepPrice() {
        if (stepPriceField == null || isBlank(stepPriceField.getText())) {
            showError("Vui lòng nhập bước giá.");
            return null;
        }

        try {
            String rawStepPrice = stepPriceField.getText().trim().replace(",", ".");
            double stepPrice = Double.parseDouble(rawStepPrice);

            if (stepPrice <= 0) {
                showError("Bước giá phải lớn hơn 0.");
                return null;
            }

            return stepPrice;
        } catch (NumberFormatException e) {
            showError("Bước giá không hợp lệ.");
            return null;
        }
    }

    /**
     * Đọc và parse LocalDateTime từ TextField.
     *
     * Format hợp lệ:
     * - 2026-05-20T10:30:00
     */
    private LocalDateTime readDateTime(TextField field, String fieldName) {
        if (field == null || isBlank(field.getText())) {
            showError("Vui lòng nhập " + fieldName + ".");
            return null;
        }

        try {
            return LocalDateTime.parse(field.getText().trim());
        } catch (DateTimeParseException e) {
            showError(fieldName + " không hợp lệ. Ví dụ đúng: 2026-05-20T10:30:00.");
            return null;
        }
    }

    /**
     * FXML cần có: <Button onAction="#handleUseDefaultTime" ... />
     *
     * Gợi ý nhanh:
     * - Bắt đầu sau 5 phút.
     * - Kết thúc sau 1 giờ.
     */
    @FXML
    private void handleUseDefaultTime() {
        fillDefaultTime();
        showMessage("Đã điền thời gian gợi ý.");
    }

    /**
     * FXML cần có: <Button onAction="#handleClearForm" ... />
     */
    @FXML
    private void handleClearForm() {
        if (selectedItem == null && itemIdField != null) {
            itemIdField.clear();
        }

        if (stepPriceField != null) {
            stepPriceField.clear();
        }

        fillDefaultTime();
        showMessage("Đã làm mới form tạo phiên đấu giá.");
    }

    /**
     * FXML cần có: <Button onAction="#handleBack" ... />
     */
    @FXML
    private void handleBack() {
        SceneNavigator.showDashboard();
    }

    /**
     * Chỉ điền thời gian mặc định nếu form đang trống.
     */
    private void fillDefaultTimeIfEmpty() {
        if ((startTimeField == null || isBlank(startTimeField.getText()))
                && (endTimeField == null || isBlank(endTimeField.getText()))) {
            fillDefaultTime();
        }
    }

    /**
     * Điền thời gian gợi ý theo ISO LocalDateTime.
     */
    private void fillDefaultTime() {
        LocalDateTime startTime = LocalDateTime.now()
                .plusMinutes(5)
                .withSecond(0)
                .withNano(0);

        LocalDateTime endTime = startTime.plusHours(1);

        if (startTimeField != null) {
            startTimeField.setText(startTime.toString());
        }

        if (endTimeField != null) {
            endTimeField.setText(endTime.toString());
        }
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