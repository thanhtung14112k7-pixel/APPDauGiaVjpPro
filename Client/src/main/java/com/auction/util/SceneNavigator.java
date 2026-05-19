package com.auction.util;
/**
 SceneNavigator là class chuyên dùng để chuyển màn hình.
 Giả sử không có SceneNavigator, mỗi controller sẽ phải tự viết lại đoạn code load FXML
 Nhiem vu: Cho controller gọi showLogin(), showRegister(), showDashboard()
 */
import java.io.IOException;
import java.net.URL;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SceneNavigator {
    private static Stage mainStage;

    // --- BIẾN ĐƯỢC THÊM MỚI ĐỂ LƯU TRẠNG THÁI THEME TOÀN HỆ THỐNG ---
    // Mặc định ban đầu là false (Chế độ sáng - Light Mode)
    // Khi đổi sang Dark Mode, biến này sẽ thành true để các màn hình sau đọc được và tự bật Dark Mode theo.
    public static boolean isAppDarkMode = false;
    // ---------------------------------------------------------------

    private static final String LOGIN_VIEW = "/com/auction/client/view/login.fxml";
    private static final String DASHBOARD_VIEW = "/com/auction/client/view/DashboardView.fxml";
    private static final String REGISTER_VIEW = "/com/auction/client/view/register.fxml";

    private SceneNavigator() {      // để private ể ko cho tạo object SceneNavigator
        // vì toàn bộ hàm trong class này là static nên việc tạo object là thừa
    }
    public static void setStage(Stage stage) {
        mainStage = stage;
    }   // nhận Stage tu Main
    public static void showLogin() {
        loadScene(LOGIN_VIEW, "Login");
    }       // chuyển về maàn hinh dang nhap
    public static void showRegister(){                      // chuyển về màn hình đăng ki
        loadScene(REGISTER_VIEW, "Register");
    }
    public static void showDashboard() {
        loadScene(DASHBOARD_VIEW, "Dashboard");
    }



    private static void loadScene(String fxmlPath, String title) {  // Hàm dùng chung để load FXML
        if (mainStage == null) {
            throw new IllegalStateException("Main stage has not been set.");
        }
        try {
            URL resource = SceneNavigator.class.getResource(fxmlPath);  // Tìm file FXML trong thư mục resources

            if (resource == null) {
                throw new IllegalArgumentException("Cannot find FXML file: " + fxmlPath);
            }

            FXMLLoader loader = new FXMLLoader(resource);       // Đọc file FXML và tự tạo Controller tương ứng
            Parent root = loader.load();

            // --- ĐOẠN CODE ĐƯỢC SỬA LẠI ĐỂ FIX LỖI THU NHỎ MÀN HÌNH KHI FULLSCREEN ---
            // Kiểm tra xem mainStage đã được khởi tạo Scene nào trước đó chưa
            if (mainStage.getScene() == null) {
                // Nếu CHƯA CÓ (Lần đầu tiên bật app lên ở màn hình Login) -> Tạo mới Scene kích thước mặc định 900x600
                Scene scene = new Scene(root, 900, 600);
                mainStage.setScene(scene);
            } else {
                // Nếu ĐÃ CÓ Scene rồi (Khi chuyển từ Login sang Register hoặc sang Dashboard)
                // Ta chỉ thay đổi tấm lõi giao diện bên trong (Root), giữ nguyên hoàn toàn trạng thái Fullscreen/Maximized của cửa sổ!
                mainStage.getScene().setRoot(root);
            }
            // -----------------------------------------------------------------------

            mainStage.setTitle("Online Auction - " + title);

            // Chỉ thực hiện căn giữa màn hình nếu người dùng KHÔNG phóng to cửa sổ
            if (!mainStage.isMaximized()) {
                mainStage.centerOnScreen();
            }

            mainStage.show();

        } catch (IOException e) {
            throw new RuntimeException("Failed to load scene: " + fxmlPath, e);
        }
    }
}
/**
 Hien tai trong Main.java ở Client dang goi : SceneNavigator.showLogin();
 Trong SceneNavigator.java, hàm showLogin() sẽ tìm file:
 /com/auction/client/view/LoginView.fxml
 Tức là nó cần file thật ở vị trí:
 src/main/resources/com/auction/client/view/LoginView.fxml (bay gio sẽ sang file LoginView.fxml
 */