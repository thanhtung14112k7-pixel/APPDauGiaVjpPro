package com.auction.util;

import com.auction.controller.AuctionDetailController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * SceneNavigator là class chuyên dùng để chuyển màn hình.
 *
 * Nhiệm vụ:
 * - Giữ Stage chính của ứng dụng.
 * - Load đúng file FXML theo từng màn hình.
 * - Tạo Scene mới và gắn vào Stage.
 * - Cho các Controller gọi các hàm như showLogin(), showDashboard(), showAuctionList().
 *
 * Lý do cần class này:
 * - Nếu không có SceneNavigator, mỗi Controller sẽ phải tự viết lại code load FXML.
 * - Khi gom logic chuyển màn vào một nơi, code Controller gọn hơn và dễ sửa hơn.
 */
public class SceneNavigator {
    private static Stage mainStage;

    private static final String LOGIN_VIEW = "/com/auction/client/view/login.fxml";
    private static final String REGISTER_VIEW = "/com/auction/client/view/register.fxml";
    private static final String DASHBOARD_VIEW = "/com/auction/client/view/DashboardView.fxml";

    private static final String AUCTION_LIST_VIEW = "/com/auction/client/view/auction-list.fxml";
    private static final String AUCTION_DETAIL_VIEW = "/com/auction/client/view/auction-detail.fxml";
    private static final String SELLER_ITEM_MANAGEMENT_VIEW = "/com/auction/client/view/seller-item-management.fxml";

    private SceneNavigator() {
        // Không cho tạo object SceneNavigator vì toàn bộ hàm trong class này là static.
    }

    public static void setStage(Stage stage) {
        mainStage = stage;
    }

    public static void showLogin() {
        loadScene(LOGIN_VIEW, "Login");
    }

    public static void showRegister() {
        loadScene(REGISTER_VIEW, "Register");
    }

    public static void showDashboard() {
        loadScene(DASHBOARD_VIEW, "Dashboard");
    }

    public static void showAuctionList() {
        loadScene(AUCTION_LIST_VIEW, "Auction List");
    }

    public static void showSellerItemManagement() {
        loadScene(SELLER_ITEM_MANAGEMENT_VIEW, "Seller Item Management");
    }

    public static void showAuctionDetail(String auctionId) {
        if (auctionId == null || auctionId.trim().isEmpty()) {
            throw new IllegalArgumentException("auctionId must not be empty.");
        }

        FXMLLoader loader = loadSceneAndReturnLoader(AUCTION_DETAIL_VIEW, "Auction Detail");

        /*
         * auction-detail.fxml tự tạo AuctionDetailController.
         * Sau khi load xong, ta lấy controller ra và truyền auctionId vào.
         */
        AuctionDetailController controller = loader.getController();
        controller.setAuctionId(auctionId);
    }

    private static void loadScene(String fxmlPath, String title) {
        loadSceneAndReturnLoader(fxmlPath, title);
    }

    private static FXMLLoader loadSceneAndReturnLoader(String fxmlPath, String title) {
        if (mainStage == null) {
            throw new IllegalStateException("Main stage has not been set.");
        }

        try {
            URL resource = SceneNavigator.class.getResource(fxmlPath);

            if (resource == null) {
                throw new IllegalArgumentException("Cannot find FXML file: " + fxmlPath);
            }

            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();

            Scene scene = new Scene(root, 900, 600);
            mainStage.setTitle("Online Auction - " + title);
            mainStage.setScene(scene);
            mainStage.centerOnScreen();
            mainStage.show();

            return loader;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load scene: " + fxmlPath, e);
        }
    }
}