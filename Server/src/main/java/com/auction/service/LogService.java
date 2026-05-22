package com.auction.service;

import com.auction.dao.impl.LogDAOImpl;
import com.auction.dto.ActionLogDTO;
import com.auction.dto.PageDTO;
import com.auction.exception.ValidationErrorCode;
import com.auction.exception.ValidationException;

import java.util.List;

public class LogService {
    private final LogDAOImpl logDAO = new LogDAOImpl();

    /**
     * Lấy danh sách lịch sử hệ thống đóng gói trọn gói dạng PageDTO dùng chung.
     */
    public PageDTO<ActionLogDTO> getLogsForAdminDashboard(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0) {
            throw new ValidationException(ValidationErrorCode.INVALID_PARAMETER, "Page index and frame boundary size must be positive.");
        }

        int offset = (page - 1) * pageSize;

        List<ActionLogDTO> logs = logDAO.findPaginatedLogs(pageSize, offset);
        long totalElements = logDAO.getTotalLogCount();
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        return new PageDTO<>(logs, page, totalPages, totalElements);
    }
}