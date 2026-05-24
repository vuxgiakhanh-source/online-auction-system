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

    private static final String REPORT_SELECT_SQL =
        "SELECT qr.id, qr.auction_id, qr.reporter_id, qr.description, "
            + "qr.image_urls, qr.status, qr.refund_completed, qr.created_at, "
            + "u.username as reporter_username, u.password_hash, u.email, u.rating, "
            + "u.balance, u.locked_balance, u.status as user_status "
            + "FROM quality_reports qr "
            + "LEFT JOIN users u ON qr.reporter_id = u.id ";

    /**
     * Lấy tất cả report đang ở trạng thái PENDING để admin xem xét.
     */
    public java.util.List<QualityReport> findPending() {
        return findByStatus("PENDING");
    }

    /** Lấy toàn bộ báo cáo, mới nhất trước. */
    public java.util.List<QualityReport> findAll() {
        return queryReportsWithoutParameter(REPORT_SELECT_SQL + "ORDER BY qr.created_at DESC");
    }

    /**
     * Lọc báo cáo theo trạng thái. Giá trị {@code ALL} trả về toàn bộ.
     */
    public java.util.List<QualityReport> findByStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return findAll();
        }

        String normalized = status.trim().toUpperCase();
        if (!normalized.equals("PENDING") && !normalized.equals("APPROVED") && !normalized.equals("REJECTED")) {
            log.warn("findByStatus ignored unknown filter: {}", status);
            return findAll();
        }

        return queryReports(
            REPORT_SELECT_SQL + "WHERE qr.status = ? ORDER BY qr.created_at DESC", normalized);
    }

    /**
     * Lấy tất cả báo cáo do một Bidder gửi, mới nhất trước.
     */
    public java.util.List<QualityReport> findByReporterId(String reporterId) {
        if (reporterId == null || reporterId.isBlank()) {
            return java.util.List.of();
        }

        String sql = "SELECT qr.id, qr.auction_id, qr.reporter_id, qr.description, "
            + "qr.image_urls, qr.status, qr.refund_completed, qr.created_at, "
            + "u.username as reporter_username, u.password_hash, u.email, u.rating, "
            + "u.balance, u.locked_balance, u.status as user_status "
            + "FROM quality_reports qr "
            + "LEFT JOIN users u ON qr.reporter_id = u.id "
            + "WHERE qr.reporter_id = ? ORDER BY qr.created_at DESC";

        return queryReports(sql, reporterId);
    }

    /**
     * Lấy tất cả báo cáo liên quan phiên do Seller sở hữu, mới nhất trước.
     */
    public java.util.List<QualityReport> findBySellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            return java.util.List.of();
        }

        String sql = "SELECT qr.id, qr.auction_id, qr.reporter_id, qr.description, "
            + "qr.image_urls, qr.status, qr.refund_completed, qr.created_at, "
            + "u.username as reporter_username, u.password_hash, u.email, u.rating, "
            + "u.balance, u.locked_balance, u.status as user_status "
            + "FROM quality_reports qr "
            + "LEFT JOIN users u ON qr.reporter_id = u.id "
            + "INNER JOIN auctions a ON qr.auction_id = a.id "
            + "INNER JOIN items i ON a.item_id = i.id "
            + "WHERE i.seller_id = ? ORDER BY qr.created_at DESC";

        return queryReports(sql, sellerId);
    }

    private java.util.List<QualityReport> queryReports(String sql, String parameter) {
        java.util.List<QualityReport> result = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, parameter);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapReportRow(rs));
                }
            }
        } catch (java.sql.SQLException e) {
            log.error("Lỗi query quality reports: sql={}", sql, e);
        }
        return result;
    }

    private java.util.List<QualityReport> queryReportsWithoutParameter(String sql) {
        java.util.List<QualityReport> result = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             java.sql.ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                result.add(mapReportRow(rs));
            }
        } catch (java.sql.SQLException e) {
            log.error("Lỗi query quality reports: sql={}", sql, e);
        }
        return result;
    }

    private QualityReport mapReportRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        String reporterId = rs.getString("reporter_id");
        String reporterUsername = rs.getString("reporter_username");
        String pwHash = rs.getString("password_hash");
        String email = rs.getString("email");
        double rating = rs.getDouble("rating");
        long balance = rs.getLong("balance");
        long locked = rs.getLong("locked_balance");
        String userStatus = rs.getString("user_status");

        java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
        java.time.LocalDateTime createdAt =
            createdTs != null ? createdTs.toLocalDateTime() : java.time.LocalDateTime.now();

        java.util.Set<com.group13.auction.model.user.User.UserRole> roles =
            java.util.EnumSet.of(com.group13.auction.model.user.User.UserRole.BIDDER);

        com.group13.auction.model.user.NormalUser reporter =
            com.group13.auction.model.user.NormalUser.reconstitute(
                reporterId, createdAt, createdAt, reporterUsername, pwHash, email,
                parseStatus(userStatus), rating, balance, locked, roles, false, 0, null);

        java.util.List<String> imageUrls = parseJsonList(rs.getString("image_urls"));
        String statusStr = rs.getString("status");
        QualityReport.ReportStatus status = QualityReport.ReportStatus.valueOf(statusStr);

        return QualityReport.reconstitute(
            rs.getString("id"), createdAt, createdAt,
            reporter, rs.getString("auction_id"),
            rs.getString("description"), imageUrls,
            status, null, rs.getBoolean("refund_completed"));
    }

    private com.group13.auction.model.user.User.AccountStatus parseStatus(String s) {
        if (s == null) return com.group13.auction.model.user.User.AccountStatus.ACTIVE;
        try { return com.group13.auction.model.user.User.AccountStatus.valueOf(s); }
        catch (Exception e) { return com.group13.auction.model.user.User.AccountStatus.ACTIVE; }
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return java.util.Collections.emptyList();
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            return gson.fromJson(json, java.util.List.class);
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Kiểm tra reporter đã từng gửi báo cáo cho phiên này chưa
     * (không phân biệt trạng thái — chỉ được gửi 1 lần duy nhất).
     *
     * @param auctionId  ID phiên đấu giá
     * @param reporterId ID người báo cáo
     * @return true nếu đã tồn tại ít nhất 1 report
     */
    public boolean existsByAuctionAndReporter(String auctionId, String reporterId) {
        String sql = "SELECT 1 FROM quality_reports WHERE auction_id = ? AND reporter_id = ? LIMIT 1";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, auctionId);
            pstmt.setString(2, reporterId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (java.sql.SQLException e) {
            log.error("Lỗi existsByAuctionAndReporter: auctionId={}, reporterId={}", auctionId, reporterId, e);
            return false;
        }
    }

    /**
     * Lưu báo cáo chất lượng mới vào Database.
     * Bao gồm description và image_urls (bắt buộc NOT NULL trong schema).
     */
    /**
     * Tìm QualityReport theo reportId — dùng cho approve/reject.
     * JOIN users để load reporter cùng lúc (tránh N+1).
     */
    public com.group13.auction.model.bid.QualityReport findById(String reportId) {
        String sql = "SELECT qr.id, qr.auction_id, qr.reporter_id, qr.description, "
            + "qr.image_urls, qr.status, qr.refund_completed, qr.created_at, "
            + "u.username as reporter_username, u.password_hash, u.email, u.rating, "
            + "u.balance, u.locked_balance, u.status as user_status "
            + "FROM quality_reports qr "
            + "LEFT JOIN users u ON qr.reporter_id = u.id "
            + "WHERE qr.id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, reportId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String rId            = rs.getString("reporter_id");
                    String rUsername      = rs.getString("reporter_username");
                    String pwHash         = rs.getString("password_hash");
                    String email          = rs.getString("email");
                    double rating         = rs.getDouble("rating");
                    long   balance        = rs.getLong("balance");
                    long   locked         = rs.getLong("locked_balance");
                    String userStatus     = rs.getString("user_status");

                    java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
                    java.time.LocalDateTime createdAt = createdTs != null
                        ? createdTs.toLocalDateTime() : java.time.LocalDateTime.now();

                    java.util.Set<com.group13.auction.model.user.User.UserRole> roles =
                        java.util.EnumSet.of(com.group13.auction.model.user.User.UserRole.BIDDER);

                    com.group13.auction.model.user.NormalUser reporter =
                        com.group13.auction.model.user.NormalUser.reconstitute(
                            rId, createdAt, createdAt, rUsername, pwHash, email,
                            parseStatus(userStatus), rating, balance, locked,
                            roles, false, 0, null);

                    java.util.List<String> imageUrls = parseJsonList(rs.getString("image_urls"));
                    String statusStr = rs.getString("status");
                    com.group13.auction.model.bid.QualityReport.ReportStatus status =
                        com.group13.auction.model.bid.QualityReport.ReportStatus.valueOf(statusStr);

                    return com.group13.auction.model.bid.QualityReport.reconstitute(
                        rs.getString("id"), createdAt, createdAt,
                        reporter, rs.getString("auction_id"),
                        rs.getString("description"), imageUrls,
                        status, null, rs.getBoolean("refund_completed"));
                }
            }
        } catch (java.sql.SQLException e) {
            log.error("Lỗi findById quality report: reportId={}", reportId, e);
        }
        return null;
    }

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