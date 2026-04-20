package com.group13.auction.dao;

import com.group13.auction.model.auction.Auction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

public class AuctionDAO {

    public AuctionDAO() {
        // Constructor rỗng, lấy Connection cục bộ trong từng hàm
    }

    /**
     * Lưu phiên đấu giá mới vào DB.
     * ID được sinh từ tầng Entity (Java) và truyền xuống.
     */
    public boolean createAuction(Auction auction) {
        String sql = "INSERT INTO auctions (id, item_id, start_time, end_time, status, reserve_price, current_price, current_leader_id, current_highest_price, winning_bidder_id, viewer_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auction.getId());
            pstmt.setString(2, auction.getItem().getId());
            pstmt.setTimestamp(3, Timestamp.valueOf(auction.getStartTime()));
            pstmt.setTimestamp(4, Timestamp.valueOf(auction.getEndTime()));
            pstmt.setString(5, auction.getStatus().name());
            pstmt.setDouble(6, auction.getReserveStrategy().getReservePrice());
            pstmt.setDouble(7, auction.getCurrentPrice());
            if (auction.getCurrentLeader() != null) {
                pstmt.setString(8, auction.getCurrentLeader().getId());
            } else {
                pstmt.setNull(8, java.sql.Types.VARCHAR);
            }
            // Legacy columns: giữ đồng bộ để các query/handler cũ vẫn hoạt động
            pstmt.setDouble(9, auction.getCurrentPrice());
            if (auction.getCurrentLeader() != null) {
                pstmt.setString(10, auction.getCurrentLeader().getId());
            } else {
                pstmt.setNull(10, java.sql.Types.VARCHAR);
            }
            pstmt.setInt(11, auction.getViewerCount());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi tạo phiên đấu giá: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật trạng thái của phiên (OPEN -> RUNNING, FINISHED, CANCELED...)
     */
    public boolean updateAuctionStatus(String auctionId, String status) {
        String sql = "UPDATE auctions SET status = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, status);
            pstmt.setString(2, auctionId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật trạng thái phiên: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật toàn bộ kết quả khi phiên kết thúc.
     * (Lưu trạng thái, giá cao nhất hiện tại và ID người chiến thắng nếu có).
     */
    public boolean updateAuctionResult(Auction auction) {
        String sql = "UPDATE auctions SET status = ?, current_price = ?, current_leader_id = ?, current_highest_price = ?, winning_bidder_id = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auction.getStatus().name());
            pstmt.setDouble(2, auction.getCurrentPrice()); // current_price
            if (auction.getCurrentLeader() != null) {
                pstmt.setString(3, auction.getCurrentLeader().getId());
            } else {
                pstmt.setNull(3, java.sql.Types.VARCHAR);
            }
            // legacy current_highest_price
            pstmt.setDouble(4, auction.getCurrentPrice());

            // Xử lý trường hợp không có người chiến thắng (NULL)
            if (auction.getCurrentLeader() != null) {
                pstmt.setString(5, auction.getCurrentLeader().getId());
            } else {
                pstmt.setNull(5, java.sql.Types.VARCHAR);
            }

            pstmt.setString(6, auction.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật kết quả phiên: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật giá cao nhất khi có người đặt giá hợp lệ (Bid).
     * Hàm này bạn đã có, tôi chỉ sửa lại kiểu dữ liệu String cho khớp UUID.
     */
    public boolean updateHighestPrice(String auctionId, double newPrice, String bidderId) {
        String sql = "UPDATE auctions SET current_price = ?, current_leader_id = ?, current_highest_price = ?, winning_bidder_id = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, newPrice);
            pstmt.setString(2, bidderId);
            // legacy columns
            pstmt.setDouble(3, newPrice);
            pstmt.setString(4, bidderId);
            pstmt.setString(5, auctionId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật giá đấu: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật số lượng người theo dõi (viewer_count) của phiên đấu giá
     */
    public boolean updateViewerCount(String auctionId, int count) {
        String sql = "UPDATE auctions SET viewer_count = ? WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, count);
            pstmt.setString(2, auctionId);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật số lượt xem phiên đấu giá: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật end_time của phiên (phục vụ anti-sniping).
     */
    public boolean updateEndTime(String auctionId, java.time.LocalDateTime endTime) {
        String sql = "UPDATE auctions SET end_time = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.valueOf(endTime));
            pstmt.setString(2, auctionId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật end_time của phiên: " + e.getMessage());
            return false;
        }
    }

    /**
     * Tìm kiếm và hồi sinh một phiên đấu giá (Auction) dựa vào ID.
     */
    public com.group13.auction.model.auction.Auction findAuctionById(String auctionId) {
        String sql = "SELECT * FROM auctions WHERE id = ?";

        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, auctionId);

            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // 1. Rút trích dữ liệu cơ bản
                    String id = rs.getString("id");
                    String statusStr = rs.getString("status");
                    String itemId = rs.getString("item_id");
                    String leaderId = rs.getString("current_leader_id");
                    double currentPrice = rs.getDouble("current_price");

                    // 2. Xử lý thời gian an toàn
                    java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
                    java.time.LocalDateTime createdAt = (createdTs != null) ? createdTs.toLocalDateTime() : java.time.LocalDateTime.now();

                    java.sql.Timestamp startTs = rs.getTimestamp("start_time");
                    java.time.LocalDateTime startTime = (startTs != null) ? startTs.toLocalDateTime() : null;

                    java.sql.Timestamp endTs = rs.getTimestamp("end_time");
                    java.time.LocalDateTime endTime = (endTs != null) ? endTs.toLocalDateTime() : null;

                    // 3. TÌM CÁC OBJECT LIÊN QUAN (Item và User)
                    ItemDAO itemDAO = new ItemDAO();
                    com.group13.auction.model.item.Item item = itemDAO.findItemById(itemId);

                    UserDAO userDAO = new UserDAO();
                    com.group13.auction.model.user.NormalUser currentLeader = null;
                    if (leaderId != null && !leaderId.trim().isEmpty()) {
                        currentLeader = userDAO.findNormalUserById(leaderId);
                    }

                    // Đã thực hiện TODO: Đọc reserve_price từ DB và khởi tạo Strategy thực tế.
                    // Bảng auctions cần có cột reserve_price (BIGINT/DECIMAL, NOT NULL).
                    double reservePrice = rs.getDouble("reserve_price");
                    // Nếu cột reserve_price chưa tồn tại hoặc = 0, dùng giá hiện tại làm fallback
                    // để tránh NullPointerException trong Auction.isReserveMet().
                    if (reservePrice <= 0) {
                        reservePrice = currentPrice > 0 ? currentPrice : 1.0;
                    }
                    com.group13.auction.strategy.ReservePriceStrategy reserveStrategy =
                            new com.group13.auction.strategy.ReservePriceStrategy(reservePrice);

                    // 4. HỒI SINH AUCTION BẰNG RECONSTITUTE
                    com.group13.auction.model.auction.Auction auction = com.group13.auction.model.auction.Auction.reconstitute(
                            id,
                            createdAt,
                            createdAt, // Dùng tạm createdAt cho updatedAt
                            item,
                            startTime,
                            endTime,
                            currentPrice,
                            com.group13.auction.model.auction.Auction.AuctionStatus.valueOf(statusStr),
                            reserveStrategy
                    );

                    // 5. Nạp thêm các thuộc tính không có trong hàm reconstitute
                    if (currentLeader != null) {
                        auction.setCurrentLeader(currentLeader);
                    }

                    return auction;
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Lỗi tìm Auction theo ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lấy toàn bộ danh sách phiên đấu giá từ Database (phục vụ khởi động hệ thống).
     */
    public java.util.List<com.group13.auction.model.auction.Auction> findAll() {
        java.util.List<com.group13.auction.model.auction.Auction> auctions = new java.util.ArrayList<>();
        String sql = "SELECT * FROM auctions";

        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql);
             java.sql.ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                // Tương tự, nếu bạn đã có hàm findAuctionById, hãy gọi nó để tái tạo Object
                // Hoặc bạn rút trích dữ liệu tại đây và gọi Auction.reconstitute(...)
                String id = rs.getString("id");

                // Giả sử bạn đã có hàm findAuctionById trong AuctionDAO:
                com.group13.auction.model.auction.Auction auction = findAuctionById(id);
                if (auction != null) {
                    auctions.add(auction);
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Lỗi lấy danh sách Auction: " + e.getMessage());
        }
        return auctions;
    }

    /**
     * Lấy danh sách auctionId của Seller đang ở trạng thái OPEN hoặc RUNNING trực tiếp từ DB.
     * Đã thực hiện TODO trong NormalUser.getUnfinishedAuctionIds():
     * thay thế filter in-memory bằng query DB.
     *
     * <p>Query join qua bảng items để lấy seller_id vì auctions không lưu seller_id trực tiếp.
     *
     * @param sellerId UUID của seller
     * @return danh sách auctionId còn đang mở/chạy
     */
    public java.util.List<String> findUnfinishedAuctionIdsBySellerId(String sellerId) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        String sql = "SELECT a.id FROM auctions a " +
                "JOIN items i ON a.item_id = i.id " +
                "WHERE i.seller_id = ? AND a.status IN ('OPEN', 'RUNNING')";

        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sellerId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString("id"));
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Lỗi tìm phiên chưa kết thúc của seller: " + e.getMessage());
        }
        return ids;
    }

    /**
     * Lấy danh sách tất cả auctionId của Seller (mọi trạng thái) từ DB.
     * Dùng để inject setAllAuctionIds() sau khi reconstitute NormalUser.
     *
     * @param sellerId UUID của seller
     * @return danh sách auctionId của seller
     */
    public java.util.List<String> findAuctionIdsBySellerId(String sellerId) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        String sql = "SELECT a.id FROM auctions a " +
                "JOIN items i ON a.item_id = i.id " +
                "WHERE i.seller_id = ?";

        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sellerId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString("id"));
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Lỗi lấy danh sách auctionId của seller: " + e.getMessage());
        }
        return ids;
    }

}