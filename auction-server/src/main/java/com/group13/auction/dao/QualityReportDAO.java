package com.group13.auction.dao;

import com.group13.auction.model.bid.QualityReport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class QualityReportDAO {

    public QualityReportDAO() {}

    /**
     * Lưu báo cáo chất lượng mới vào Database.
     */
    public boolean saveReport(QualityReport report) {
        String sql = "INSERT INTO quality_reports (id, auction_id, reporter_id, status, created_at) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, report.getId());
            pstmt.setString(2, report.getAuctionId());
            pstmt.setString(3, report.getReporter().getId());
            pstmt.setString(4, report.getStatus().name());
            pstmt.setTimestamp(5, Timestamp.valueOf(report.getCreatedAt()));

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi lưu báo cáo chất lượng: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật trạng thái, hạn chót hoàn tiền và cờ refund_completed của báo cáo.
     * Đã thực hiện TODO trong QualityReport: persist thêm cột {@code refund_completed} xuống DB.
     */
    public boolean updateReport(QualityReport report) {
        String sql = "UPDATE quality_reports SET status = ?, seller_refund_deadline = ?, refund_completed = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, report.getStatus().name());

            if (report.getSellerRefundDeadline() != null) {
                pstmt.setTimestamp(2, Timestamp.valueOf(report.getSellerRefundDeadline()));
            } else {
                pstmt.setNull(2, java.sql.Types.TIMESTAMP);
            }

            // Đã thực hiện TODO: persist refund_completed
            pstmt.setBoolean(3, report.isRefundCompleted());

            pstmt.setString(4, report.getId());

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật báo cáo chất lượng: " + e.getMessage());
            return false;
        }
    }
}