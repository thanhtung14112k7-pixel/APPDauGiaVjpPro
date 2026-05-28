package com.auction.service;

import com.auction.dao.impl.LogDAOImpl;
import com.auction.dto.ActionLogDTO;
import com.auction.dto.PageDTO;
import com.auction.exception.ValidationErrorCode;
import com.auction.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;

public class LogService {
    private final LogDAOImpl logDAO = new LogDAOImpl();

    /**
     * Lấy danh sách lịch sử hệ thống đóng gói trọn gói dạng PageDTO dùng chung.
     */
    public PageDTO<ActionLogDTO> getLogsForAdminDashboard(int page, int pageSize) {
        // Hàng rào kiểm tra tham số đầu vào (Rất tốt, giữ nguyên)
        if (page <= 0 || pageSize <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Page index and frame boundary size must be positive.");
        }

        int offset = (page - 1) * pageSize;

        // 1. Lấy danh sách log phân trang trước
        List<ActionLogDTO> logs = logDAO.findPaginatedLogs(pageSize, offset);

        // 🔥 TỐI ƯU 1: CHIẾN LƯỢC TRẢ VỀ SỚM (Early Return)
        // Nếu trang hiện tại không có dữ liệu, trả về ngay DTO trống.
        // Giải phóng hoàn toàn cho Database khỏi gánh nặng chạy câu lệnh COUNT(*) quét hàng triệu dòng log phía dưới.
        if (logs == null || logs.isEmpty()) {
            return new PageDTO<>(new ArrayList<>(), page, 0, 0);
        }

        // 2. Chỉ khi có dữ liệu hiển thị, ta mới tốn tài nguyên đi đếm tổng số phần tử
        long totalElements = logDAO.getTotalLogCount();

        // 🔥 TỐI ƯU 2: Tính toán số trang an toàn, tránh lỗi hiển thị giao diện thanh phân trang
        int totalPages = (totalElements == 0) ? 1 : (int) Math.ceil((double) totalElements / pageSize);

        return new PageDTO<>(logs, page, totalPages, totalElements);
    }
}