package com.auction.network;
/*
 SocketServer mở cổng Server để Client kết nối vào.
 - Mở cổng server, ví dụ port 5555
 - Chờ client kết nối
 - Mỗi khi có client mới, tạo một ClientHandler riêng
 - Chạy ClientHandler bằng Thread riêng
 */

import com.auction.manage.ConnectionManage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketServer {
    private static final int PORT = 5555;
    private static final int MAX_CLIENTS = 300; // Trần cứng khống chế quy mô hệ thống

    // Sử dụng Virtual Threads (luồng ảo) của Java để xử lý số lượng client không giới hạn, tránh nghẽn thread pool
    private final ExecutorService threadPool = Executors.newVirtualThreadPerTaskExecutor();
    public void start() {
        System.out.println("[Server] Đang chạy tại port " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {      // Mở ServerSocket tại port 5555.
            while (true) {                                              // Từ lúc này, Server sẵn sàng nhận kết nối.
                Socket clientSocket = serverSocket.accept();

                // Kiểm tra nếu số lượng kết nối live hiện tại vượt quá 100
                if (ConnectionManage.getInstance().getOnlineCount() >= MAX_CLIENTS) {
                    // Từ chối khéo, đóng socket ngay lập tức để bảo vệ tài nguyên Server
                    clientSocket.close();
                    continue;
                }

                System.out.println("[Server] Có client kết nối: "
                        + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);

                // Quăng việc cho ThreadPool lo, không tự new Thread nữa
                threadPool.execute(clientHandler);
            }
        } catch (IOException e) {
            System.err.println("[Server] Lỗi SocketServer: " + e.getMessage());
        }
    }
}