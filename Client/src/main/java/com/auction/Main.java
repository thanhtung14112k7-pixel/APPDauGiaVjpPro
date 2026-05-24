package com.auction;


import com.auction.util.SceneNavigator;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * ClientApp là cửa khởi động của app người dùng
 1. Khởi động JavaFX
 2. Nhận cửa sổ chính từ JavaFX
 3. Đưa cửa sổ đó cho SceneNavigator quản lý
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        // 1. Lưu Stage vào Navigator như cũ của các bạn
        SceneNavigator.setStage(primaryStage);

        // --- 2. ĐOẠN CODE BẮT SỰ KIỆN F11 TOÀN CỤC CHO STAGE ---
        // Mỗi khi SceneNavigator đổi màn hình (thay Scene mới), đoạn code này sẽ tự động
        // gán quyền lắng nghe phím F11 cho màn hình mới đó mà không làm crash app.
        primaryStage.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.setOnKeyPressed(event -> {
                    if (event.getCode() == javafx.scene.input.KeyCode.F11) {
                        // Đảo ngược trạng thái Fullscreen (Bật <-> Tắt)
                        primaryStage.setFullScreen(!primaryStage.isFullScreen());
                    }
                });
            }
        });

        // Mẹo nhỏ: Xóa bỏ dòng chữ "Press ESC to exit full screen" mặc định nếu bạn thấy vướng mắt
        primaryStage.setFullScreenExitHint("");
        // ------------------------------------------------------

        // 3. Hiển thị màn hình login lên như cũ
        SceneNavigator.showLogin();
    }
    public static void main(String[] args) {
        launch(args);
    }
}
