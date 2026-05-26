package com.auction.controller;

import com.auction.dto.CreateItemRequest;
import com.auction.dto.ItemDetailDTO;
import com.auction.dto.ItemSummaryDTO;
import com.auction.dto.SocketResponse;
import com.auction.dto.UpdateItemRequest;
import com.auction.network.ClientAuctionApi;
import com.auction.network.ClientItemApi;
import com.auction.util.SceneNavigator;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * SellerItemController là controller phía Client cho màn Seller quản lý vật phẩm.
 *
 * Vai trò chính:
 * - Quản lý danh sách item của Seller.
 * - Tạo item mới.
 * - Xem chi tiết item.
 * - Cập nhật item.
 * - Xóa/ẩn item.
 * - Chọn item để tạo phiên đấu giá.
 * - Gửi request qua ClientItemApi / ClientAuctionApi, không xử lý socket trực tiếp.
 *
 * Controller này được thiết kế để người làm FXML có thể dựng màn hoàn chỉnh.
 * Các fx:id/onAction bên dưới là contract giữa FXML và controller.
 */
public class SellerItemController {
    private final ClientAuctionApi auctionApi = new ClientAuctionApi();
    private final ClientItemApi itemApi = new ClientItemApi();

    private final ObservableList<ItemSummaryDTO> sellerItems = FXCollections.observableArrayList();

    private ItemSummaryDTO selectedItem;

    // =========================
    // Root / Header / Status UI
    // =========================

    @FXML private StackPane rootContainer;
    @FXML private Label dynamicTitleLabel;
    @FXML private Label formTitleLabel;
    @FXML private Label selectedItemIdLabel;
    @FXML private Label itemNameLabel;
    @FXML private Label messageLabel;
    @FXML private Button btnSubmit;

    // =========================
    // Item list UI
    // =========================
    // FXML nên khai báo TableView này để Seller chọn item cụ thể.

    @FXML private TableView<ItemSummaryDTO> sellerItemsTable;
    @FXML private TableColumn<ItemSummaryDTO, String> itemIdColumn;
    @FXML private TableColumn<ItemSummaryDTO, String> itemNameColumn;
    @FXML private TableColumn<ItemSummaryDTO, String> itemTypeColumn;
    @FXML private TableColumn<ItemSummaryDTO, Number> startingPriceColumn;
    @FXML private TableColumn<ItemSummaryDTO, String> statusColumn;

    // =========================
    // Item form UI
    // =========================
    // Có thể dùng ComboBox hoặc TextField cho itemType.
    // Nếu FXML có cả hai, ComboBox được ưu tiên.

    @FXML private ComboBox<String> itemTypeComboBox;
    @FXML private TextField itemTypeField;
    @FXML private TextField itemNameField;
    @FXML private TextField startingPriceField;
    @FXML private TextField descriptionField;
    @FXML private TextField yearCreatedField;
    @FXML private TextField imageUrlField;

    // Field riêng cho ART.
    @FXML private VBox artFieldsBox;
    @FXML private TextField painterField;
    @FXML private TextField artStyleField;

    // Field riêng cho ELECTRONICS.
    @FXML private VBox electronicsFieldsBox;
    @FXML private TextField brandField;
    @FXML private TextField warrantyMonthsField;

    // Field riêng cho VEHICLES.
    @FXML private VBox vehicleFieldsBox;
    @FXML private TextField modelField;
    @FXML private TextField engineTypeField;
    @FXML private TextField licensePlateField;
    @FXML private TextField kmAgeField;

    // Các field phụ cho update/delete/detail.
    @FXML private TextField updateItemIdField;
    @FXML private TextField deleteItemIdField;
    @FXML private TextField detailItemIdField;

    // =========================
    // Auction form UI
    // =========================

    @FXML private TextField itemIdField;
    @FXML private TextField stepPriceField;
    @FXML private TextField startTimeField;
    @FXML private TextField endTimeField;

    @FXML
    public void initialize() {
        applyTheme();
        initializeItemTypeControl();
        initializeSellerItemsTable();

        fillDefaultTimeIfEmpty();
        updateTypeSpecificFieldsVisibility(readItemType());

        showMessage("Tải danh sách item hoặc tạo item mới.");
    }

    /**
     * Áp dụng theme hiện tại của app.
     * Nếu FXML chưa có rootContainer thì bỏ qua để tránh crash.
     */
    private void applyTheme() {
        if (rootContainer == null) {
            return;
        }

        rootContainer.getStylesheets().clear();
        String cssPath = SceneNavigator.isAppDarkMode
                ? "/com/auction/client/view/dark.css"
                : "/com/auction/client/view/light.css";

        try {
            String css = Objects.requireNonNull(getClass().getResource(cssPath)).toExternalForm();
            rootContainer.getStylesheets().add(css);
        } catch (Exception e) {
            System.out.println("Không thể nạp theme: " + cssPath);
        }

        if (SceneNavigator.isAppDarkMode) {
            setLabelText(dynamicTitleLabel, "QUẢN LÝ VẬT PHẨM");
            setLabelText(formTitleLabel, "Thông Tin Vật Phẩm");
            if (dynamicTitleLabel != null) {
                dynamicTitleLabel.setStyle("-fx-text-fill: #ff9f43;");
            }
            if (btnSubmit != null) {
                btnSubmit.setText("TẠO PHIÊN ĐẤU GIÁ");
            }
        } else {
            setLabelText(dynamicTitleLabel, "QUẢN LÝ VẬT PHẨM");
            setLabelText(formTitleLabel, "Thông Tin Vật Phẩm");
            if (dynamicTitleLabel != null) {
                dynamicTitleLabel.setStyle("-fx-text-fill: #1877f2;");
            }
            if (btnSubmit != null) {
                btnSubmit.setText("TẠO PHIÊN ĐẤU GIÁ");
            }
        }
    }

    /**
     * ComboBox giúp FXML tránh cho người dùng gõ sai item type.
     */
    private void initializeItemTypeControl() {
        if (itemTypeComboBox != null) {
            itemTypeComboBox.setItems(FXCollections.observableArrayList(
                    "ART",
                    "ELECTRONICS",
                    "VEHICLES"
            ));

            itemTypeComboBox.valueProperty().addListener((obs, oldValue, newValue) ->
                    updateTypeSpecificFieldsVisibility(newValue)
            );
        }

        if (itemTypeField != null) {
            itemTypeField.textProperty().addListener((obs, oldValue, newValue) -> {
                if (itemTypeComboBox == null) {
                    updateTypeSpecificFieldsVisibility(newValue);
                }
            });
        }
    }

    /**
     * TableView là phần quan trọng để FXML có màn quản lý item hoàn chỉnh.
     * Người dùng chọn item trong bảng, controller sẽ tự load detail và fill form.
     */
    private void initializeSellerItemsTable() {
        if (sellerItemsTable == null) {
            return;
        }

        sellerItemsTable.setItems(sellerItems);

        if (itemIdColumn != null) {
            itemIdColumn.setCellValueFactory(data ->
                    new SimpleStringProperty(safeText(data.getValue().getItemId()))
            );
        }

        if (itemNameColumn != null) {
            itemNameColumn.setCellValueFactory(data ->
                    new SimpleStringProperty(safeText(data.getValue().getItemName()))
            );
        }

        if (itemTypeColumn != null) {
            itemTypeColumn.setCellValueFactory(data ->
                    new SimpleStringProperty(safeText(data.getValue().getItemType()))
            );
        }

        if (startingPriceColumn != null) {
            startingPriceColumn.setCellValueFactory(data ->
                    new SimpleDoubleProperty(data.getValue().getStartingPrice())
            );
        }

        if (statusColumn != null) {
            statusColumn.setCellValueFactory(data ->
                    new SimpleStringProperty(safeText(data.getValue().getStatus()))
            );
        }

        sellerItemsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                return;
            }

            setSelectedItem(newItem);
            loadItemDetailIntoForm(newItem.getItemId(), false);
        });
    }

    /**
     * FXML action: tải danh sách item của seller đang đăng nhập.
     */
    @FXML
    private void handleLoadSellerItems() {
        refreshSellerItems(true);
    }

    /**
     * FXML action: tạo item mới.
     */
    @FXML
    private void handleCreateItem() {
        CreateItemRequest request = buildCreateItemRequest();
        if (request == null) {
            return;
        }

        SocketResponse response = itemApi.createItem(request);
        if (!isSuccessful(response)) {
            showError(response == null ? "Server không trả về phản hồi hợp lệ." : response.getMessage());
            return;
        }

        ItemDetailDTO createdItem = itemApi.parseItemDetail(response);
        if (createdItem != null) {
            fillItemForm(createdItem);
            setSelectedItem(createdItem.toSummaryDTO());
        }

        refreshSellerItems(false);
        showInfo(response.getMessage());
        showMessage("Tạo sản phẩm thành công.");
    }

    /**
     * FXML action: cập nhật item đang chọn hoặc itemId nhập ở updateItemIdField.
     */
    @FXML
    private void handleUpdateItem() {
        UpdateItemRequest request = buildUpdateItemRequest();
        if (request == null) {
            return;
        }

        SocketResponse response = itemApi.updateItem(request);
        if (!isSuccessful(response)) {
            showError(response == null ? "Server không trả về phản hồi hợp lệ." : response.getMessage());
            return;
        }

        ItemDetailDTO updatedItem = itemApi.parseItemDetail(response);
        if (updatedItem != null) {
            fillItemForm(updatedItem);
            setSelectedItem(updatedItem.toSummaryDTO());
        }

        refreshSellerItems(false);
        showInfo(response.getMessage());
        showMessage("Cập nhật sản phẩm thành công.");
    }

    /**
     * FXML action: xóa/ẩn item.
     * Server hiện xử lý delete bằng cách đổi status sang INACTIVE.
     */
    @FXML
    private void handleDeleteItem() {
        String itemId = firstNonBlank(readText(deleteItemIdField), readItemId());
        if (isBlank(itemId)) {
            showError("Vui lòng nhập itemId cần xóa.");
            return;
        }

        SocketResponse response = itemApi.deleteItem(itemId, "Deleted from seller item controller.");
        if (!isSuccessful(response)) {
            showError(response == null ? "Server không trả về phản hồi hợp lệ." : response.getMessage());
            return;
        }

        refreshSellerItems(false);
        showInfo(response.getMessage());
        showMessage("Đã xóa/ẩn sản phẩm: " + itemId);
    }

    /**
     * FXML action: load chi tiết item theo detailItemIdField hoặc item đang chọn.
     */
    @FXML
    private void handleLoadItemDetail() {
        String itemId = firstNonBlank(readText(detailItemIdField), readItemId());
        if (isBlank(itemId)) {
            showError("Vui lòng nhập itemId cần xem chi tiết.");
            return;
        }

        loadItemDetailIntoForm(itemId, true);
    }

    /**
     * FXML action: khi đổi item type bằng ComboBox.
     */
    @FXML
    private void handleItemTypeChanged() {
        updateTypeSpecificFieldsVisibility(readItemType());
    }

    /**
     * FXML action: clear form item.
     */
    @FXML
    private void handleClearItemForm() {
        selectedItem = null;

        if (sellerItemsTable != null) {
            sellerItemsTable.getSelectionModel().clearSelection();
        }

        clearText(itemTypeField);
        if (itemTypeComboBox != null) {
            itemTypeComboBox.getSelectionModel().clearSelection();
        }

        clearText(itemNameField);
        clearText(startingPriceField);
        clearText(descriptionField);
        clearText(yearCreatedField);
        clearText(imageUrlField);
        clearText(painterField);
        clearText(artStyleField);
        clearText(brandField);
        clearText(warrantyMonthsField);
        clearText(modelField);
        clearText(engineTypeField);
        clearText(licensePlateField);
        clearText(kmAgeField);
        clearText(updateItemIdField);
        clearText(deleteItemIdField);
        clearText(detailItemIdField);

        setLabelText(selectedItemIdLabel, "Item ID: Chưa chọn");
        setLabelText(itemNameLabel, "Vật phẩm: Chưa chọn");

        updateTypeSpecificFieldsVisibility(null);
        showMessage("Đã làm mới form item.");
    }

    /**
     * FXML action: tạo phiên đấu giá từ item đang chọn hoặc itemId nhập thủ công.
     */
    @FXML
    private void handleCreateAuction() {
        String itemId = readItemId();
        if (isBlank(itemId)) {
            showError("Vui lòng nhập hoặc chọn itemId của vật phẩm.");
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

        SocketResponse response = auctionApi.createAuction(
                itemId,
                stepPrice,
                startTime.toString(),
                endTime.toString()
        );

        if (!isSuccessful(response)) {
            showError(response == null ? "Server không trả về phản hồi hợp lệ." : response.getMessage());
            return;
        }

        showInfo(response.getMessage());
        showMessage("Tạo phiên đấu giá thành công.");
    }

    /**
     * FXML action: điền nhanh thời gian gợi ý cho form auction.
     */
    @FXML
    private void handleUseDefaultTime() {
        fillDefaultTime();
        showMessage("Đã điền thời gian gợi ý.");
    }

    /**
     * FXML action cũ: giữ lại để tương thích FXML hiện tại.
     * Nếu muốn tách rõ, FXML mới nên dùng handleClearItemForm và handleClearAuctionForm.
     */
    @FXML
    private void handleClearForm() {
        handleClearItemForm();
        handleClearAuctionForm();
        showMessage("Đã làm mới form.");
    }

    /**
     * FXML action: clear riêng form tạo auction.
     */
    @FXML
    private void handleClearAuctionForm() {
        if (selectedItem == null) {
            clearText(itemIdField);
        }

        clearText(stepPriceField);
        fillDefaultTime();
        showMessage("Đã làm mới form tạo phiên đấu giá.");
    }

    /**
     * FXML action: quay về dashboard.
     */
    @FXML
    private void handleBack() {
        SceneNavigator.showDashboard();
    }

    /**
     * Cập nhật item đang chọn từ bảng hoặc sau khi create/detail/update.
     */
    public void setSelectedItem(ItemSummaryDTO item) {
        this.selectedItem = item;

        if (item == null) {
            return;
        }

        if (itemIdField != null) {
            itemIdField.setText(item.getItemId());
        }

        if (updateItemIdField != null) {
            updateItemIdField.setText(item.getItemId());
        }

        if (deleteItemIdField != null) {
            deleteItemIdField.setText(item.getItemId());
        }

        if (detailItemIdField != null) {
            detailItemIdField.setText(item.getItemId());
        }

        setLabelText(selectedItemIdLabel, "Item ID: " + safeText(item.getItemId()));
        setLabelText(itemNameLabel, "Vật phẩm: " + safeText(item.getItemName()));
        selectItemInTable(item.getItemId());
    }

    private void refreshSellerItems(boolean showResultMessage) {
        SocketResponse response = itemApi.getSellerItems();
        if (!isSuccessful(response)) {
            showError(response == null ? "Server không trả về phản hồi hợp lệ." : response.getMessage());
            return;
        }

        List<ItemSummaryDTO> items = itemApi.parseItemSummaryList(response);
        sellerItems.setAll(items);

        if (selectedItem != null) {
            selectItemInTable(selectedItem.getItemId());
        } else if (!items.isEmpty()) {
            setSelectedItem(items.get(0));
        }

        if (showResultMessage) {
            showMessage("Đã tải " + items.size() + " sản phẩm của người bán.");
        }
    }

    private void loadItemDetailIntoForm(String itemId, boolean showSuccessMessage) {
        SocketResponse response = itemApi.getItemDetail(itemId);
        if (!isSuccessful(response)) {
            showError(response == null ? "Server không trả về phản hồi hợp lệ." : response.getMessage());
            return;
        }

        ItemDetailDTO itemDetail = itemApi.parseItemDetail(response);
        if (itemDetail == null) {
            showError("Không đọc được chi tiết sản phẩm từ phản hồi server.");
            return;
        }

        fillItemForm(itemDetail);
        setSelectedItem(itemDetail.toSummaryDTO());

        if (showSuccessMessage) {
            showMessage("Đã tải chi tiết sản phẩm: " + safeText(itemDetail.getItemName()));
        }
    }

    /**
     * Fill toàn bộ form từ ItemDetailDTO.
     * Đây là hàm quan trọng để FXML có trải nghiệm chọn item -> thấy detail.
     */
    private void fillItemForm(ItemDetailDTO item) {
        if (item == null) {
            return;
        }

        setText(itemIdField, item.getItemId());
        setText(updateItemIdField, item.getItemId());
        setText(deleteItemIdField, item.getItemId());
        setText(detailItemIdField, item.getItemId());

        setItemTypeValue(item.getItemType());
        setText(itemNameField, item.getItemName());
        setText(startingPriceField, numberToText(item.getStartingPrice()));
        setText(descriptionField, item.getDescription());
        setText(yearCreatedField, numberToText(item.getYearCreated()));
        setText(imageUrlField, item.getImageUrl());

        setText(painterField, item.getPainter());
        setText(artStyleField, item.getArtStyle());

        setText(brandField, item.getBrand());
        setText(warrantyMonthsField, numberToText(item.getWarrantyMonths()));

        setText(modelField, item.getModel());
        setText(engineTypeField, item.getEngineType());
        setText(licensePlateField, item.getLicensePlate());
        setText(kmAgeField, numberToText(item.getKmAge()));

        updateTypeSpecificFieldsVisibility(item.getItemType());
    }

    private CreateItemRequest buildCreateItemRequest() {
        String itemType = readItemType();
        String name = readText(itemNameField);

        if (isBlank(itemType)) {
            showError("Vui lòng chọn loại sản phẩm.");
            return null;
        }

        if (isBlank(name)) {
            showError("Vui lòng nhập tên sản phẩm.");
            return null;
        }

        Double startingPrice = readRequiredPositiveDouble(startingPriceField, "giá khởi điểm");
        if (startingPrice == null) {
            return null;
        }

        Integer yearCreated = readRequiredInteger(yearCreatedField, "năm tạo/sản xuất");
        if (yearCreated == null) {
            return null;
        }

        if (!validateTypeSpecificRequiredFields(itemType)) {
            return null;
        }

        Integer warrantyMonths = readOptionalInteger(warrantyMonthsField, "số tháng bảo hành");
        Double kmAge = readOptionalPositiveDouble(kmAgeField, "số km đã đi");

        if (hasInvalidOptionalNumber(warrantyMonthsField, warrantyMonths)
                || hasInvalidOptionalNumber(kmAgeField, kmAge)) {
            return null;
        }

        return new CreateItemRequest(
                itemType,
                name,
                startingPrice,
                readText(descriptionField),
                yearCreated,
                readText(imageUrlField),
                readText(painterField),
                readText(artStyleField),
                readText(brandField),
                warrantyMonths,
                readText(modelField),
                readText(engineTypeField),
                readText(licensePlateField),
                kmAge
        );
    }

    private UpdateItemRequest buildUpdateItemRequest() {
        String itemId = firstNonBlank(readText(updateItemIdField), readItemId());
        if (isBlank(itemId)) {
            showError("Vui lòng nhập hoặc chọn itemId cần cập nhật.");
            return null;
        }

        String itemType = firstNonBlank(
                readItemType(),
                selectedItem == null ? null : selectedItem.getItemType()
        );

        if (isBlank(itemType)) {
            showError("Vui lòng chọn loại sản phẩm để server xác thực.");
            return null;
        }

        Double startingPrice = readOptionalPositiveDouble(startingPriceField, "giá khởi điểm");
        Integer yearCreated = readOptionalInteger(yearCreatedField, "năm tạo/sản xuất");
        Integer warrantyMonths = readOptionalInteger(warrantyMonthsField, "số tháng bảo hành");
        Double kmAge = readOptionalPositiveDouble(kmAgeField, "số km đã đi");

        if (hasInvalidOptionalNumber(startingPriceField, startingPrice)
                || hasInvalidOptionalNumber(yearCreatedField, yearCreated)
                || hasInvalidOptionalNumber(warrantyMonthsField, warrantyMonths)
                || hasInvalidOptionalNumber(kmAgeField, kmAge)) {
            return null;
        }

        return new UpdateItemRequest(
                itemId,
                itemType,
                readText(itemNameField),
                startingPrice,
                readText(descriptionField),
                yearCreated,
                readText(imageUrlField),
                readText(painterField),
                readText(artStyleField),
                readText(brandField),
                warrantyMonths,
                readText(modelField),
                readText(engineTypeField),
                readText(licensePlateField),
                kmAge
        );
    }

    private boolean validateTypeSpecificRequiredFields(String itemType) {
        String normalizedType = normalizeItemType(itemType);

        if ("ART".equals(normalizedType)) {
            if (isBlank(readText(painterField))) {
                showError("Vui lòng nhập họa sĩ/tác giả.");
                return false;
            }
            if (isBlank(readText(artStyleField))) {
                showError("Vui lòng nhập phong cách nghệ thuật.");
                return false;
            }
        }

        if ("ELECTRONICS".equals(normalizedType)) {
            if (isBlank(readText(brandField))) {
                showError("Vui lòng nhập thương hiệu.");
                return false;
            }
            if (readRequiredInteger(warrantyMonthsField, "số tháng bảo hành") == null) {
                return false;
            }
        }

        if ("VEHICLES".equals(normalizedType)) {
            if (isBlank(readText(modelField))) {
                showError("Vui lòng nhập dòng xe/model.");
                return false;
            }
            if (isBlank(readText(engineTypeField))) {
                showError("Vui lòng nhập loại động cơ.");
                return false;
            }
            if (isBlank(readText(licensePlateField))) {
                showError("Vui lòng nhập biển số.");
                return false;
            }
            if (readRequiredPositiveDouble(kmAgeField, "số km đã đi") == null) {
                return false;
            }
        }

        return true;
    }

    private void updateTypeSpecificFieldsVisibility(String itemType) {
        String normalizedType = normalizeItemType(itemType);

        setNodeVisible(artFieldsBox, "ART".equals(normalizedType));
        setNodeVisible(electronicsFieldsBox, "ELECTRONICS".equals(normalizedType));
        setNodeVisible(vehicleFieldsBox, "VEHICLES".equals(normalizedType));
    }

    private void selectItemInTable(String itemId) {
        if (sellerItemsTable == null || isBlank(itemId)) {
            return;
        }

        for (ItemSummaryDTO item : sellerItems) {
            if (itemId.equals(item.getItemId())) {
                sellerItemsTable.getSelectionModel().select(item);
                return;
            }
        }
    }

    private String readItemId() {
        if (selectedItem != null && !isBlank(selectedItem.getItemId())) {
            return selectedItem.getItemId();
        }

        return readText(itemIdField);
    }

    private String readItemType() {
        if (itemTypeComboBox != null && !isBlank(itemTypeComboBox.getValue())) {
            return itemTypeComboBox.getValue();
        }

        return readText(itemTypeField);
    }

    private void setItemTypeValue(String itemType) {
        String normalizedType = normalizeItemType(itemType);

        if (itemTypeComboBox != null) {
            itemTypeComboBox.setValue(normalizedType);
        }

        setText(itemTypeField, normalizedType);
    }

    private String normalizeItemType(String itemType) {
        if (isBlank(itemType)) {
            return null;
        }

        String normalizedType = itemType.trim().toUpperCase();
        return "VEHICLE".equals(normalizedType) ? "VEHICLES" : normalizedType;
    }

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

    private void fillDefaultTimeIfEmpty() {
        if ((startTimeField == null || isBlank(startTimeField.getText()))
                && (endTimeField == null || isBlank(endTimeField.getText()))) {
            fillDefaultTime();
        }
    }

    private void fillDefaultTime() {
        LocalDateTime startTime = LocalDateTime.now()
                .plusMinutes(5)
                .withSecond(0)
                .withNano(0);

        LocalDateTime endTime = startTime.plusHours(1);

        setText(startTimeField, startTime.toString());
        setText(endTimeField, endTime.toString());
    }

    private String readText(TextField field) {
        return field == null ? null : field.getText();
    }

    private void setText(TextField field, String value) {
        if (field != null) {
            field.setText(safeText(value));
        }
    }

    private void clearText(TextField field) {
        if (field != null) {
            field.clear();
        }
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private Double readRequiredPositiveDouble(TextField field, String fieldName) {
        String value = readText(field);
        if (isBlank(value)) {
            showError("Vui lòng nhập " + fieldName + ".");
            return null;
        }
        return parsePositiveDouble(value, fieldName);
    }

    private Double readOptionalPositiveDouble(TextField field, String fieldName) {
        String value = readText(field);
        if (isBlank(value)) {
            return null;
        }
        return parsePositiveDouble(value, fieldName);
    }

    private Double parsePositiveDouble(String value, String fieldName) {
        try {
            double number = Double.parseDouble(value.trim().replace(",", "."));
            if (number < 0) {
                showError(fieldName + " phải lớn hơn hoặc bằng 0.");
                return null;
            }
            return number;
        } catch (NumberFormatException e) {
            showError(fieldName + " không hợp lệ.");
            return null;
        }
    }

    private Integer readRequiredInteger(TextField field, String fieldName) {
        String value = readText(field);
        if (isBlank(value)) {
            showError("Vui lòng nhập " + fieldName + ".");
            return null;
        }
        return parseInteger(value, fieldName);
    }

    private Integer readOptionalInteger(TextField field, String fieldName) {
        String value = readText(field);
        if (isBlank(value)) {
            return null;
        }
        return parseInteger(value, fieldName);
    }

    private Integer parseInteger(String value, String fieldName) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            showError(fieldName + " không hợp lệ.");
            return null;
        }
    }

    private boolean hasInvalidOptionalNumber(TextField field, Number parsedValue) {
        return field != null && !isBlank(field.getText()) && parsedValue == null;
    }

    private boolean isSuccessful(SocketResponse response) {
        return response != null && response.isSuccess();
    }

    private void setNodeVisible(Node node, boolean visible) {
        if (node != null) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private void setLabelText(Label label, String text) {
        if (label != null) {
            label.setText(safeText(text));
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String numberToText(Number value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void showMessage(String message) {
        if (messageLabel != null) {
            messageLabel.setText(safeText(message));
        }
    }

    private void showError(String message) {
        showMessage(message);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(safeText(message));
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(safeText(message));
        alert.showAndWait();
    }
}