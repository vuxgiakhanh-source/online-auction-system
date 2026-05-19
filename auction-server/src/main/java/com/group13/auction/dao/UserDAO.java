package com.group13.auction.dao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.ResultSetMetaData;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    private static final Logger log = LoggerFactory.getLogger(UserDAO.class);

    public UserDAO() {}

    private static User.AccountStatus parseAccountStatus(String statusStr) {
        if (statusStr == null) return User.AccountStatus.ACTIVE;
        if ("DELETED".equalsIgnoreCase(statusStr)) {
            return User.AccountStatus.BANNED;
        }
        try {
            return User.AccountStatus.valueOf(statusStr);
        } catch (IllegalArgumentException ex) {
            return User.AccountStatus.BANNED;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BUG FIX #1 — UserDAO không load SELLER role từ bảng sellers
    //
    // Tất cả 3 methods (findUserByUsername, findNormalUserById,
    // findUserCoreByUsername) đều hardcode:
    //     roles = EnumSet.of(UserRole.BIDDER)
    // và không bao giờ đọc bảng sellers.
    //
    // Hậu quả: user được duyệt Seller vẫn thấy mình là BIDDER sau mỗi lần
    // login, trừ khi vào trang Profile (lúc đó AuctionManager in-memory đã
    // có role SELLER do autoApproveSellerRole() đã thêm vào object).
    //
    // Fix: thêm loadRoles() gọi query sellers trên cùng Connection để tránh
    // tốn thêm connection-pool slot, rồi gọi nó trong mọi findUser* method.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Tải roles cho user từ DB.
     * Luôn bao gồm BIDDER; thêm SELLER nếu approval_status = 'APPROVED'.
     * Dùng Connection đang mở để không tốn thêm connection-pool slot.
     */
    private Set<User.UserRole> loadRoles(Connection conn, String userId) throws SQLException {
        Set<User.UserRole> roles = EnumSet.of(User.UserRole.BIDDER);
        String sql = "SELECT 1 FROM sellers WHERE user_id = ? AND approval_status = 'APPROVED' LIMIT 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    roles.add(User.UserRole.SELLER);
                }
            }
        }
        return roles;
    }

    // ═══════════════════════════════════════════════════════════════════════

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
            log.error("Lỗi đăng ký người dùng: ", e);
        }
        return null;
    }

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
            log.error("Lỗi xác thực: ", e);
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
            log.error("Lỗi cập nhật trạng thái: ", e);
            return false;
        }
    }

    public boolean addBalance(String userId, long amount) {
        String sql = "UPDATE users SET balance = balance + ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, amount);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi nạp tiền: ", e);
            return false;
        }
    }

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
            log.error("Lỗi lưu hoạt động tham gia/theo dõi của người dùng: ", e);
            return false;
        }
    }

    /**
     * Tìm kiếm NormalUser theo ID.
     * BUG FIX #1: gọi loadRoles() để lấy role thực tế từ DB.
     */
    public NormalUser findNormalUserById(String userId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("id");
                    String username = rs.getString("username");
                    String passwordHash = rs.getString("password_hash");
                    String email = rs.getString("email");
                    double rating = rs.getDouble("rating");
                    long balance = rs.getLong("balance");
                    long lockedBalance = rs.getLong("locked_balance");
                    String statusStr = rs.getString("status");
                    boolean hasEverBeenPenalized = getBooleanOrDefault(rs, "has_ever_been_penalized", false);
                    boolean hasEverBeenRestored = getBooleanOrDefault(rs, "has_ever_been_restored", false);

                    java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
                    java.time.LocalDateTime createdAt = (createdTs != null) ? createdTs.toLocalDateTime() : java.time.LocalDateTime.now();
                    java.sql.Timestamp suspendedTs = rs.getTimestamp("suspended_at");
                    java.time.LocalDateTime suspendedAt = (suspendedTs != null) ? suspendedTs.toLocalDateTime() : null;

                    // BUG FIX #1: load roles thực tế từ DB
                    Set<User.UserRole> roles = loadRoles(conn, id);

                    NormalUser user = NormalUser.reconstitute(
                            id, createdAt, createdAt, username, passwordHash, email,
                            parseAccountStatus(statusStr), rating, balance, lockedBalance,
                            roles, hasEverBeenPenalized, hasEverBeenRestored, suspendedAt);

                    user.setJoinedAuctionIds(findJoinedAuctionIdsByUserId(id));
                    user.setWatchListAuctionIds(findWatchListByUserId(id));
                    return user;
                }
            }
        } catch (SQLException e) {
            log.error("Lỗi tìm User theo ID: ", e);
        }
        return null;
    }

    public boolean updateRating(String userId, double rating) {
        String sql = "UPDATE users SET rating = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, rating);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi cập nhật rating: ", e);
            return false;
        }
    }

    public boolean updateRatingAndPenalty(String userId, double rating, boolean isPenalized) {
        String sql = "UPDATE users SET rating = ?, has_ever_been_penalized = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, rating);
            pstmt.setBoolean(2, isPenalized);
            pstmt.setString(3, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi cập nhật điểm và trạng thái vi phạm: ", e);
            return false;
        }
    }

    public boolean updateBalances(String userId, long balance, long lockedBalance) {
        String sql = "UPDATE users SET balance = ?, locked_balance = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, balance);
            pstmt.setLong(2, lockedBalance);
            pstmt.setString(3, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi cập nhật số dư tài khoản: ", e);
            return false;
        }
    }

    /**
     * Tìm user nhanh để xác thực login — 1 query users + 1 query sellers,
     * KHÔNG load joinedAuctionIds / watchListAuctionIds.
     * BUG FIX #1: gọi loadRoles() để lấy role thực tế.
     */
    public NormalUser findUserCoreByUsername(String username) {
        String sql = "SELECT id, username, password_hash, email, rating, balance, " +
                "locked_balance, status, has_ever_been_penalized, has_ever_been_restored, " +
                "created_at, suspended_at FROM users WHERE username = ? AND status != 'DELETED'";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id             = rs.getString("id");
                    String fetchedUsername= rs.getString("username");
                    String passwordHash   = rs.getString("password_hash");
                    String email          = rs.getString("email");
                    double rating         = rs.getDouble("rating");
                    long   balance        = rs.getLong("balance");
                    long   lockedBalance  = rs.getLong("locked_balance");
                    String statusStr      = rs.getString("status");
                    boolean penalized     = getBooleanOrDefault(rs, "has_ever_been_penalized", false);
                    boolean restored      = getBooleanOrDefault(rs, "has_ever_been_restored", false);

                    java.sql.Timestamp createdTs  = rs.getTimestamp("created_at");
                    java.sql.Timestamp suspendedTs = rs.getTimestamp("suspended_at");
                    java.time.LocalDateTime createdAt   = createdTs  != null ? createdTs.toLocalDateTime()   : java.time.LocalDateTime.now();
                    java.time.LocalDateTime suspendedAt = suspendedTs != null ? suspendedTs.toLocalDateTime() : null;

                    // BUG FIX #1: load roles thực tế từ DB
                    Set<User.UserRole> roles = loadRoles(conn, id);

                    return NormalUser.reconstitute(id, createdAt, createdAt, fetchedUsername,
                            passwordHash, email, parseAccountStatus(statusStr), rating,
                            balance, lockedBalance, roles, penalized, restored, suspendedAt);
                }
            }
        } catch (SQLException e) {
            log.error("Lỗi findUserCoreByUsername: ", e);
        }
        return null;
    }

    /**
     * Tìm kiếm NormalUser theo Username.
     * BUG FIX #1: gọi loadRoles() để lấy role thực tế.
     */
    public NormalUser findUserByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String id = rs.getString("id");
                    String fetchedUsername = rs.getString("username");
                    String passwordHash = rs.getString("password_hash");
                    String email = rs.getString("email");
                    double rating = rs.getDouble("rating");
                    long balance = rs.getLong("balance");
                    long lockedBalance = rs.getLong("locked_balance");
                    String statusStr = rs.getString("status");
                    boolean hasEverBeenPenalized = getBooleanOrDefault(rs, "has_ever_been_penalized", false);
                    boolean hasEverBeenRestored = getBooleanOrDefault(rs, "has_ever_been_restored", false);

                    java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
                    java.time.LocalDateTime createdAt = (createdTs != null) ? createdTs.toLocalDateTime() : java.time.LocalDateTime.now();
                    java.sql.Timestamp suspendedTs = rs.getTimestamp("suspended_at");
                    java.time.LocalDateTime suspendedAt = (suspendedTs != null) ? suspendedTs.toLocalDateTime() : null;

                    // BUG FIX #1: load roles thực tế từ DB
                    Set<User.UserRole> roles = loadRoles(conn, id);

                    NormalUser user = NormalUser.reconstitute(
                            id, createdAt, createdAt, fetchedUsername, passwordHash, email,
                            parseAccountStatus(statusStr), rating, balance, lockedBalance,
                            roles, hasEverBeenPenalized, hasEverBeenRestored, suspendedAt);

                    user.setJoinedAuctionIds(findJoinedAuctionIdsByUserId(id));
                    user.setWatchListAuctionIds(findWatchListByUserId(id));
                    return user;
                }
            }
        } catch (SQLException e) {
            log.error("Lỗi tìm User theo username: ", e);
        }
        return null;
    }

    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                NormalUser user = findNormalUserById(id);
                if (user != null) users.add(user);
            }
        } catch (SQLException e) {
            log.error("Lỗi lấy danh sách User: ", e);
        }
        return users;
    }

    public boolean save(User user) {
        String sql = "INSERT INTO users (id, username, password_hash, email, status, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getId());
            pstmt.setString(2, user.getUsername());
            pstmt.setString(3, user.getHashedPassword());
            pstmt.setString(4, user.getEmail());
            pstmt.setString(5, user.getAccountStatus().name());
            pstmt.setTimestamp(6, java.sql.Timestamp.valueOf(user.getCreatedAt()));
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi lưu User mới: ", e);
            return false;
        }
    }

    public boolean delete(User user) {
        String sql = "UPDATE users SET status = 'DELETED' WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, user.getId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi xóa User: ", e);
            return false;
        }
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ? AND status != 'DELETED' LIMIT 1";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            log.error("Lỗi kiểm tra username tồn tại: ", e);
            return false;
        }
    }

    public boolean existsByEmail(String email) {
        String sql = "SELECT 1 FROM users WHERE email = ? AND status != 'DELETED' LIMIT 1";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            log.error("Lỗi kiểm tra email tồn tại: ", e);
            return false;
        }
    }

    public Set<String> findJoinedAuctionIdsByUserId(String userId) {
        return findAuctionIdsByUserIdAndActivityType(userId, "JOINED");
    }

    public List<String> findWatchListByUserId(String userId) {
        return new ArrayList<>(findAuctionIdsByUserIdAndActivityType(userId, "WATCHING"));
    }

    private Set<String> findAuctionIdsByUserIdAndActivityType(String userId, String activityType) {
        Set<String> ids = new HashSet<>();
        String sql = "SELECT auction_id FROM user_auction_activity WHERE user_id = ? AND activity_type = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, activityType);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("auction_id"));
            }
        } catch (SQLException e) {
            log.error("Lỗi lấy lịch sử activity ( {}", activityType + "): " + e.getMessage());
        }
        return ids;
    }

    private static boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        for (int i = 1; i <= count; i++) {
            if (columnName.equalsIgnoreCase(meta.getColumnLabel(i))) return true;
        }
        return false;
    }

    private static boolean getBooleanOrDefault(ResultSet rs, String columnName, boolean defaultValue) throws SQLException {
        if (!hasColumn(rs, columnName)) return defaultValue;
        return rs.getBoolean(columnName);
    }

    public boolean updateHasEverBeenRestored(String userId, boolean hasEverBeenRestored) {
        String sql = "UPDATE users SET has_ever_been_restored = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, hasEverBeenRestored);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi cập nhật cờ hasEverBeenRestored: ", e);
            return false;
        }
    }
}