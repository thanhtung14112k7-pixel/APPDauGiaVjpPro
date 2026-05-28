package com.auction.dao;

import com.auction.dto.ActionLogDTO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface LogDAO {

    void insertLog(Connection conn, String logId, String adminId, String actionDetail, String targetType, String targetId) throws SQLException;

    List<ActionLogDTO> findPaginatedLogs(int limit, int offset);

    long getTotalLogCount();
}
