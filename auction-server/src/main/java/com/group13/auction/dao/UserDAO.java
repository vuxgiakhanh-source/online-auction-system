package com.group13.auction.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import com.group13.auction.model.user.NormalUser;

public class UserDAO {

    public UserDAO() {}

    /**
     * Đăng ký User mới (Mặc định là Bidder)
     * Trả về UUID của user vừa tạo nếu thành công, null nếu thất bại.
     */
    public String registerUser(String username, String passwordHash, String email) {
        String userId = UUID.randomUUID().toString();
        String sql = "INSERT INTO users (id, username, password_hash, email) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setString(2, username);
            pstmt.setString(3, passwordHash);
            pstmt.setString(4, email);

            if (pstmt.executeUpdate() > 0) return userId;
        } catch (SQLException e) {
            System.err.println("Lỗi đăng ký người dùng: " + e.getMessage());
        }
        return null;
    }

    /**
     * Xác thực và lấy ID người dùng (Trả về String UUID, null nếu thất bại)
     */
    public String authenticateAndGetId(String username, String passwordHash) {
        String sql = "SELECT id FROM users WHERE username = ? AND password_hash = ? AND status != 'DELETED'";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("id");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi xác thực: " + e.getMessage());
        }
        return null;
    }

    public boolean updateAccountStatus(String userId, String status) {
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật trạng thái: " + e.getMessage());
            return false;
        }
    }

    public boolean addBalance(String userId, double amount) {
        String sql = "UPDATE users SET balance = balance + ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi nạp tiền: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lưu trạng thái tham gia hoặc theo dõi phiên đấu giá của người dùng
     */
    public boolean saveUserAuctionActivity(String userId, String auctionId, String activityType) {
        String sql = "INSERT INTO user_auction_activity (user_id, auction_id, activity_type) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE activity_type = VALUES(activity_type)";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            pstmt.setString(2, auctionId);
            pstmt.setString(3, activityType);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi lưu hoạt động tham gia/theo dõi của người dùng: " + e.getMessage());
            return false;
        }
    }

    /**
     * Tìm kiếm NormalUser theo ID
     */
    /**
     * Tìm kiếm NormalUser theo ID và hồi sinh đối tượng từ Database.
     */
    public NormalUser findNormalUserById(String userId) {
        String sql = "SELECT * FROM users WHERE id = ?";

        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // 1. Lấy dữ liệu cơ bản
                    String id = rs.getString("id");
                    String username = rs.getString("username");
                    String passwordHash = rs.getString("password_hash");
                    String email = rs.getString("email");
                    int rating = rs.getInt("rating");
                    double balance = rs.getDouble("balance");
                    double lockedBalance = rs.getDouble("locked_balance");
                    String statusStr = rs.getString("status");
                    boolean hasEverBeenPenalized = rs.getBoolean("has_ever_been_penalized");

                    // 2. Xử lý thời gian an toàn
                    java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
                    java.time.LocalDateTime createdAt = (createdTs != null) ? createdTs.toLocalDateTime() : java.time.LocalDateTime.now();

                    java.sql.Timestamp suspendedTs = rs.getTimestamp("suspended_at");
                    java.time.LocalDateTime suspendedAt = (suspendedTs != null) ? suspendedTs.toLocalDateTime() : null;

                    // 3. Xử lý các trường không có sẵn trong bảng users hiện tại
                    // Mặc định gán false, nếu sau này bạn thêm cột `has_ever_been_restored` vào DB thì dùng rs.getBoolean
                    boolean hasEverBeenRestored = false;

                    // Mặc định khởi tạo role là BIDDER
                    java.util.Set<com.group13.auction.model.user.User.UserRole> roles =
                            java.util.EnumSet.of(com.group13.auction.model.user.User.UserRole.BIDDER);

                    // (Tùy chọn: Nếu bạn muốn check xem user có phải SELLER không, bạn có thể query thêm bảng sellers ở đây)

                    // 4. Hồi sinh Object
                    return NormalUser.reconstitute(
                            id,
                            createdAt,
                            createdAt, // Dùng tạm createdAt cho updatedAt
                            username,
                            passwordHash,
                            email,
                            com.group13.auction.model.user.User.AccountStatus.valueOf(statusStr),
                            rating,
                            balance,
                            lockedBalance,
                            roles,
                            hasEverBeenPenalized,
                            hasEverBeenRestored,
                            suspendedAt
                    );
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Lỗi tìm User theo ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Cập nhật điểm rating của User.
     */
    public boolean updateRating(String userId, double rating) {
        String sql = "UPDATE users SET rating = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, rating);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật rating: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cập nhật điểm rating và đánh dấu vi phạm.
     */
    public boolean updateRatingAndPenalty(String userId, double rating, boolean isPenalized) {
        String sql = "UPDATE users SET rating = ?, has_ever_been_penalized = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, rating);
            pstmt.setBoolean(2, isPenalized);
            pstmt.setString(3, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật điểm và trạng thái vi phạm: " + e.getMessage());
            return false;
        }
    }

    /**
     * Đồng bộ số dư (balance) và tiền cọc đang khóa (locked_balance) của User.
     */
    public boolean updateBalances(String userId, double balance, double lockedBalance) {
        String sql = "UPDATE users SET balance = ?, locked_balance = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, balance);
            pstmt.setDouble(2, lockedBalance);
            pstmt.setString(3, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi cập nhật số dư tài khoản: " + e.getMessage());
            return false;
        }
    }

    /**
     * Tìm kiếm NormalUser theo Username để phục vụ việc Đăng nhập.
     */
    /**
     * Tìm kiếm NormalUser theo Username để phục vụ việc Đăng nhập.
     */
    public NormalUser findUserByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // 1. Lấy dữ liệu cơ bản
                    String id = rs.getString("id");
                    String fetchedUsername = rs.getString("username"); // Lấy chính xác từ DB
                    String passwordHash = rs.getString("password_hash");
                    String email = rs.getString("email");
                    int rating = rs.getInt("rating");
                    double balance = rs.getDouble("balance");
                    double lockedBalance = rs.getDouble("locked_balance");
                    String statusStr = rs.getString("status");
                    boolean hasEverBeenPenalized = rs.getBoolean("has_ever_been_penalized");

                    // 2. Xử lý thời gian an toàn
                    java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
                    java.time.LocalDateTime createdAt = (createdTs != null) ? createdTs.toLocalDateTime() : java.time.LocalDateTime.now();

                    java.sql.Timestamp suspendedTs = rs.getTimestamp("suspended_at");
                    java.time.LocalDateTime suspendedAt = (suspendedTs != null) ? suspendedTs.toLocalDateTime() : null;

                    // 3. Các trường phụ thuộc (mặc định)
                    boolean hasEverBeenRestored = false;
                    java.util.Set<com.group13.auction.model.user.User.UserRole> roles =
                            java.util.EnumSet.of(com.group13.auction.model.user.User.UserRole.BIDDER);

                    // 4. Hồi sinh Object
                    return NormalUser.reconstitute(
                            id,
                            createdAt,
                            createdAt,
                            fetchedUsername,
                            passwordHash,
                            email,
                            com.group13.auction.model.user.User.AccountStatus.valueOf(statusStr),
                            rating,
                            balance,
                            lockedBalance,
                            roles,
                            hasEverBeenPenalized,
                            hasEverBeenRestored,
                            suspendedAt
                    );
                }
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Lỗi tìm User theo username: " + e.getMessage());
        }

        // Nếu không có dòng nào trong DB khớp với username, trả về null
        return null;
    }
}