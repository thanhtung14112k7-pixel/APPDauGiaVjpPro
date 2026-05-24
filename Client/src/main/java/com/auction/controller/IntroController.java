package com.auction.controller;

import com.auction.service.ClientSocketService;
import com.auction.util.SceneNavigator;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public class IntroController {

    @FXML private AnchorPane introRoot;
    @FXML private Pane leftPersonNode, rightPersonNode, dealNode;
    @FXML private SVGPath leftLegA, leftLegB, rightLegA, rightLegB;
    @FXML private Label statusLabel;

    private Timeline walkingTimeline;

    @FXML
    public void initialize() {
        // Tạo hiệu ứng đổi chân bước đi liên tục (150ms một nhịp dứt khoát)
        walkingTimeline = new Timeline(new KeyFrame(Duration.millis(150), e -> {
            boolean toggle = leftLegA.isVisible();
            leftLegA.setVisible(!toggle);
            leftLegB.setVisible(toggle);
            rightLegA.setVisible(!toggle);
            rightLegB.setVisible(toggle);
        }));
        walkingTimeline.setCycleCount(Animation.INDEFINITE);
        walkingTimeline.play();

        // Luồng chạy background kết nối socket mạng
        Thread initThread = new Thread(() -> {
            try {
                // Bước 1: Các quý ông nhỏ gọn bước vào sảnh chờ
                animate(0, 100, 0, -100, 1400, "Đang mời các nhà đầu tư vào phòng đấu giá...");
                Thread.sleep(1500);

                // Bước 2: Thực hiện gọi kết nối Server thực tế
                Platform.runLater(() -> statusLabel.setText("Đang kiểm tra chứng thực kết nối..."));
                long start = System.currentTimeMillis();
                ClientSocketService.getInstance();
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed < 900) Thread.sleep(900 - elapsed);

                // Bước 3: Tăng tốc tiến sát lại gần nhau để chốt hợp đồng
                animate(100, 245, -100, -245, 700, "Khớp lệnh thành công! Đang đóng dấu hợp đồng...");
                Thread.sleep(750);

                // Bước 4: Chạm nhau -> Dừng chân, dập con dấu mộc SUCCESS nổi bật
                Platform.runLater(() -> {
                    walkingTimeline.stop(); // Dừng hiệu ứng bước chân
                    leftPersonNode.setVisible(false);
                    rightPersonNode.setVisible(false);
                    dealNode.setVisible(true); // Hiện con dấu thành công màu xanh rực sáng
                });
                Thread.sleep(1200); // Giữ hình ảnh con dấu lại 1.4 giây tạo điểm nhấn

                // Bước 5: Chuyển sang màn hình đăng nhập
                Platform.runLater(this::fadeOutAndSwitch);

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Lỗi kết nối máy chủ! Vui lòng thử lại.");
                    statusLabel.setTextFill(javafx.scene.paint.Color.web("#ef4444"));
                    walkingTimeline.stop();
                });
            }
        });
        initThread.setDaemon(true);
        initThread.start();
    }

    private void animate(double lFrom, double lTo, double rFrom, double rTo, double ms, String txt) {
        Platform.runLater(() -> {
            statusLabel.setText(txt);
            Duration d = Duration.millis(ms);

            TranslateTransition mLeft = new TranslateTransition(d, leftPersonNode);
            mLeft.setFromX(lFrom); mLeft.setToX(lTo);
            mLeft.setInterpolator(Interpolator.LINEAR);
            mLeft.play();

            TranslateTransition mRight = new TranslateTransition(d, rightPersonNode);
            mRight.setFromX(rFrom); mRight.setToX(rTo);
            mRight.setInterpolator(Interpolator.LINEAR);
            mRight.play();
        });
    }

    private void fadeOutAndSwitch() {
        FadeTransition fade = new FadeTransition(Duration.millis(500), introRoot);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        fade.setOnFinished(e -> SceneNavigator.showLogin());
        fade.play();
    }

    public void playOutroAndExit() {
        Platform.runLater(() -> {
            // 1. Ẩn dấu tích V, hiện lại 2 quý ông
            dealNode.setVisible(false);
            leftPersonNode.setVisible(true);
            rightPersonNode.setVisible(true);

            // 2. Quay người hướng ra ngoài (Đảo ngược hướng mặt)
            leftPersonNode.setScaleX(-1.4); // Quay lưng đi về bên trái
            rightPersonNode.setScaleX(1.4);  // Quay lưng đi về bên phải

            // 3. Đổi chữ trạng thái tạm biệt
            statusLabel.setText("Cảm ơn quý nhà đầu tư. Hệ thống đang an toàn đóng sảnh...");
            statusLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #e5c158;");

            // 4. Kích hoạt lại bước chân di chuyển
            walkingTimeline.play();

            // 5. Hoạt cảnh tịnh tiến đi lùi ra xa (Thời gian 1 giây)
            Duration duration = Duration.millis(1000);

            TranslateTransition moveLeft = new TranslateTransition(duration, leftPersonNode);
            moveLeft.setFromX(245); moveLeft.setToX(0); // Đi ngược về biên trái
            moveLeft.play();

            TranslateTransition moveRight = new TranslateTransition(duration, rightPersonNode);
            moveRight.setFromX(-245); moveRight.setToX(0); // Đi ngược về biên phải
            moveRight.play();

            // 6. Hiệu ứng màn hình tối dần và tắt hẳn App
            FadeTransition fadeOut = new FadeTransition(duration, introRoot);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                walkingTimeline.stop();
                Platform.exit(); // Đóng ứng dụng hoàn toàn mạng sạch sẽ
                System.exit(0);
            });
            fadeOut.play();
        });
    }
}