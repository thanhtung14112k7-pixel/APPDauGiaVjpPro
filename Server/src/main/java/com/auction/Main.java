package com.auction;
import com.auction.network.SocketServer;

/**
 * =========================================================================
 * Main - Điểm khởi hỏa duy nhất của hệ thống Đấu giá trực tuyến (Server)
 * =========================================================================
 * Kiến trúc:
 * - Tuân thủ tuyệt đối nguyên lý Tách biệt trách nhiệm (Separation of Concerns).
 * - Không chứa dữ liệu cấu hình cứng (Hardcoded), không chứa logic nghiệp vụ.
 * - Chỉ đóng vai trò là "chìa khóa vặn nổ máy" kích hoạt chuỗi vòng đời hệ thống.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println("   🔨 HỆ THỐNG MÁY CHỦ ĐẤU GIÁ TRỰC TUYẾN (PRODUCTION)   ");
        System.out.println("=========================================================");

        // 1. Giao toàn bộ trọng trách thiết lập hạ tầng kỹ thuật cho bộ tổng chỉ huy Bootstrap
        // Quy trình ngầm: Múi giờ -> Factories -> DB Pool -> Event Bus -> DB Clean -> Hydrate RAM -> Seed Data
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.start();

        // 2. Sau khi hạ tầng nền móng đã xanh mượt, tiến hành kích hoạt mở cổng mạng Socket đón khách
        System.out.println("\n[Main] 🔌 Kích hoạt cổng mạng Socket vật lý...");

        try {
            SocketServer socketServer = new SocketServer();

            // Hàm start() này bên trong sẽ chứa vòng lặp vô hạn `while(true) { serverSocket.accept(); }`
            // để giữ cho Server luôn sống và lắng nghe các gói tin JSON bắn lên từ Client JavaFX
            socketServer.start();

        } catch (Exception e) {
            System.err.println("[Main] 💥 SỰ CỐ MẠNG NGHIÊM TRỌNG! Không thể mở cổng Socket Server.");
            e.printStackTrace();
            System.exit(1); // Cưỡng chế dừng ứng dụng vì không mở được cổng mạng thì Server vô dụng
        }
    }
}