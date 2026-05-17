package com.group13.auction.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.group13.auction.model.bid.QualityReport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class QualityReportDAO {
    private static final Logger log = LoggerFactory.getLogger(QualityReportDAO.class);


    public QualityReportDAO() {}

    /**
     * Lưu báo cáo chất lượng mới vào Database.
     * Bao gồm description và image_urls (bắt buộc NOT NULL trong schema).
     */
    public boolean saveReport(QualityReport report) {
        String sql = "INSERT INTO quality_reports "
                + "(id, auction_id, reporter_id, description, image_urls, status, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, report.getId());
            pstmt.setString(2, report.getAuctionId());
            pstmt.setString(3, report.getReporter().getId());
            pstmt.setString(4, report.getDescription());
            pstmt.setString(5, ItemDAO.toJson(report.getImageUrls()));
            pstmt.setString(6, report.getStatus().name());
            pstmt.setTimestamp(7, Timestamp.valueOf(report.getCreatedAt()));

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            log.error("Lỗi lưu báo cáo chất lượng", e);
            return false;
        }
    }

    /**
     * Cập nhật trạng thái và cờ refund_completed của báo cáo.
     * Được gọi ngay sau khi approve/reject để persist status xuống DB.
     */
    public boolean updateReport(QualityReport report) {
        String sql = "UPDATE quality_reports SET status = ?, refund_completed = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, report.getStatus().name());
            pstmt.setBoolean(2, report.isRefundCompleted());
            pstmt.setString(3, report.getId());

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            log.error("Lỗi cập nhật báo cáo chất lượng", e);
            return false;
        }
    }
}