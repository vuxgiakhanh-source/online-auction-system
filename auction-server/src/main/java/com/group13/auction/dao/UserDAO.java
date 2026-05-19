package com.group13.auction.dao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.ResultSetMetaData;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.group13.auction.model.user.NormalUser;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    private static final Logger log = LoggerFactory.getLogger(UserDAO.class);

    public UserDAO() {}

    /**
     * Parse trạng thái tài khoản từ DB một cách an toàn.
     *
     * <p>DB có thể có trạng thái soft-delete (DELETED). Trong code domain hiện tại
     * {@link com.group13.auction.model.user.User.AccountStatus} không có DELETED,
     * nên map DELETED -> BANNED để chặn mọi thao tác xác thực/đấu giá với tài khoản đã xoá.
     */
    private static com.group13.auction.model.user.User.AccountStatus parseAccountStatus(String statusStr) {
        if (statusStr == null) return com.group13.auction.model.user.User.AccountStatus.ACTIVE;
        if ("DELETED".equalsIgnoreCase(statusStr)) {
            return com.group13.auction.model.user.User.AccountStatus.BANNED;
        }
        try {
            return com.group13.auction.model.user.User.AccountStatus.valueOf(statusStr);
        } catch (IllegalArgumentException ex) {
            // Fallback an toàn nếu DB chứa giá trị không mong đợi
            return com.group13.auction.model.user.User.AccountStatus.BANNED;
        }
    }

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
            log.error("Lỗi đăng ký người dùng: ", e);
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
            log.error("Lỗi lưu hoạt động tham gia/theo dõi của người dùng: ", e);
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
                    double rating = rs.getDouble("rating");
                    long balance = rs.getLong("balance");
                    long lockedBalance = rs.getLong("locked_balance");
                    String statusStr = rs.getString("status");
                    boolean hasEverBeenPenalized = getBooleanOrDefault(rs, "has_ever_been_penalized", false);

                    // 2. Xử lý thời gian an toàn
                    java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
                    java.time.LocalDateTime createdAt = (createdTs != null) ? createdTs.toLocalDateTime() : java.time.LocalDateTime.now();

                    java.sql.Timestamp suspendedTs = rs.getTimestamp("suspended_at");
                    java.time.LocalDateTime suspendedAt = (suspendedTs != null) ? suspendedTs.toLocalDateTime() : null;

                    // 3. Xử lý các trường không có sẵn trong bảng users hiện tại
                    // Đã thực hiện TODO: đọc has_ever_been_restored từ DB
                    boolean hasEverBeenRestored = getBooleanOrDefault(rs, "has_ever_been_restored", false);

                    // Mặc định khởi tạo role là BIDDER
                    java.util.Set<com.group13.auction.model.user.User.UserRole> roles =
                            java.util.EnumSet.of(com.group13.auction.model.user.User.UserRole.BIDDER);


                    // 4. Hồi sinh Object
                    NormalUser user = NormalUser.reconstitute(
                            id,
                            createdAt,
                            createdAt, // Dùng tạm createdAt cho updatedAt
                            username,
                            passwordHash,
                            email,
                            parseAccountStatus(statusStr),
                            rating,
                            balance,
                            lockedBalance,
                            roles,
                            hasEverBeenPenalized,
                            hasEverBeenRestored,
                            suspendedAt
                    );

                    // 5. Đã thực hiện TODO: inject dữ liệu lịch sử sau reconstitute
                    user.setJoinedAuctionIds(findJoinedAuctionIdsByUserId(id));
                    user.setWatchListAuctionIds(findWatchListByUserId(id));
                    // setBidHistory được bỏ qua ở đây để tránh vòng lặp đệ quy
                    // (findBidHistoryByUserId gọi lại findNormalUserById).
                    // Caller có thể tự inject nếu cần: user.setBidHistory(userDAO.findBidHistoryByUserId(id))

                    return user;
                }
            }
        } catch (java.sql.SQLException e) {
            log.error("Lỗi tìm User theo ID: ", e);
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
            log.error("Lỗi cập nhật rating: ", e);
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
            log.error("Lỗi cập nhật điểm và trạng thái vi phạm: ", e);
            return false;
        }
    }

    /**
     * Đồng bộ số dư (balance) và tiền cọc đang khóa (locked_balance) của User.
     */
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
     * Tìm user theo username CHỈ để xác thực — 1 query duy nhất, KHÔNG load
     * joinedAuctionIds + watchListAuctionIds (2 subquery không cần thiết cho auth).
     *
     * FIX LOGIN SLOWNESS: findUserByUsername() cũ làm 3 queries cho mỗi lần login.
     * Method này chỉ cần 1 query → giảm latency login ~60-70%.
     * Sau khi xác thực xong, UserService trả về user đầy đủ từ AuctionManager (in-memory).
     */
    public NormalUser findUserCoreByUsername(String username) {
        String sql = "SELECT id, username, password_hash, email, rating, balance, " +
                "locked_balance, status, has_ever_been_penalized, has_ever_been_restored, " +
                "created_at, suspended_at FROM users WHERE username = ? AND status != 'DELETED'";

        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
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

                    java.util.Set<com.group13.auction.model.user.User.UserRole> roles =
                            java.util.EnumSet.of(com.group13.auction.model.user.User.UserRole.BIDDER);

                    // Không gọi findJoinedAuctionIdsByUserId() hay findWatchListByUserId()
                    // — chỉ cần data cơ bản để verify password + status
                    return NormalUser.reconstitute(id, createdAt, createdAt, fetchedUsername,
                            passwordHash, email, parseAccountStatus(statusStr), rating,
                            balance, lockedBalance, roles, penalized, restored, suspendedAt);
                }
            }
        } catch (java.sql.SQLException e) {
            log.error("Lỗi findUserCoreByUsername: ", e);
        }
        return null;
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
                    double rating = rs.getDouble("rating");
                    long balance = rs.getLong("balance");
                    long lockedBalance = rs.getLong("locked_balance");
                    String statusStr = rs.getString("status");
                    boolean hasEverBeenPenalized = getBooleanOrDefault(rs, "has_ever_been_penalized", false);

                    // 2. Xử lý thời gian an toàn
                    java.sql.Timestamp createdTs = rs.getTimestamp("created_at");
                    java.time.LocalDateTime createdAt = (createdTs != null) ? createdTs.toLocalDateTime() : java.time.LocalDateTime.now();

                    java.sql.Timestamp suspendedTs = rs.getTimestamp("suspended_at");
                    java.time.LocalDateTime suspendedAt = (suspendedTs != null) ? suspendedTs.toLocalDateTime() : null;

                    // 3. Các trường phụ thuộc
                    // Đã thực hiện TODO: đọc has_ever_been_restored từ DB
                    boolean hasEverBeenRestored = getBooleanOrDefault(rs, "has_ever_been_restored", false);
                    java.util.Set<com.group13.auction.model.user.User.UserRole> roles =
                            java.util.EnumSet.of(com.group13.auction.model.user.User.UserRole.BIDDER);

                    // 4. Hồi sinh Object
                    NormalUser user = NormalUser.reconstitute(
                            id,
                            createdAt,
                            createdAt,
                            fetchedUsername,
                            passwordHash,
                            email,
                            parseAccountStatus(statusStr),
                            rating,
                            balance,
                            lockedBalance,
                            roles,
                            hasEverBeenPenalized,
                            hasEverBeenRestored,
                            suspendedAt
                    );

                    // 5. Đã thực hiện TODO: inject dữ liệu lịch sử sau reconstitute
                    user.setJoinedAuctionIds(findJoinedAuctionIdsByUserId(id));
                    user.setWatchListAuctionIds(findWatchListByUserId(id));

                    return user;
                }
            }
        } catch (java.sql.SQLException e) {
            log.error("Lỗi tìm User theo username: ", e);
        }

        // Nếu không có dòng nào trong DB khớp với username, trả về null
        return null;
    }

    /**
     * Lấy toàn bộ danh sách User từ Database (phục vụ khởi động hệ thống).
     */
    public java.util.List<com.group13.auction.model.user.User> findAll() {
        java.util.List<com.group13.auction.model.user.User> users = new java.util.ArrayList<>();
        String sql = "SELECT * FROM users";

        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql);
             java.sql.ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                // Tận dụng lại logic của hàm findNormalUserById để nạp dữ liệu
                String id = rs.getString("id");
                // Tạm thời gọi lại hàm tìm kiếm chi tiết để tái tạo object (Hoặc bạn có thể copy nguyên khối hồi sinh Object vào đây cho tối ưu)
                com.group13.auction.model.user.NormalUser user = findNormalUserById(id);
                if (user != null) {
                    users.add(user);
                }
            }
        } catch (java.sql.SQLException e) {
            log.error("Lỗi lấy danh sách User: ", e);
        }
        return users;
    }

    /**
     * Lưu một User mới xuống Database.
     */
    public boolean save(com.group13.auction.model.user.User user) {
        String sql = "INSERT INTO users (id, username, password_hash, email, status, created_at) VALUES (?, ?, ?, ?, ?, ?)";

        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getId());
            pstmt.setString(2, user.getUsername());
            pstmt.setString(3, user.getHashedPassword());
            pstmt.setString(4, user.getEmail());
            pstmt.setString(5, user.getAccountStatus().name());

            java.sql.Timestamp createdTs = java.sql.Timestamp.valueOf(user.getCreatedAt());
            pstmt.setTimestamp(6, createdTs);

            return pstmt.executeUpdate() > 0;
        } catch (java.sql.SQLException e) {
            log.error("Lỗi lưu User mới: ", e);
            return false;
        }
    }

    /**
     * Xóa User (Soft-delete: Đổi trạng thái thành DELETED).
     */
    public boolean delete(com.group13.auction.model.user.User user) {
        String sql = "UPDATE users SET status = 'DELETED' WHERE id = ?";

        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getId());
            return pstmt.executeUpdate() > 0;

        } catch (java.sql.SQLException e) {
            log.error("Lỗi xóa User: ", e);
            return false;
        }
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ? AND status != 'DELETED' LIMIT 1";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
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
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
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
                while (rs.next()) {
                    ids.add(rs.getString("auction_id"));
                }
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
    /**
     * Cập nhật cờ hasEverBeenRestored của User xuống DB.
     * Đã thực hiện TODO trong RatingService.checkAndRestoreSuspended():
     * persist để flag không bị mất khi restart server.
     *
     * @param userId ID của user
     * @param hasEverBeenRestored giá trị cần cập nhật
     * @return true nếu cập nhật thành công
     */
    public boolean updateHasEverBeenRestored(String userId, boolean hasEverBeenRestored) {
        String sql = "UPDATE users SET has_ever_been_restored = ? WHERE id = ?";
        try (java.sql.Connection conn = DatabaseConnection.getInstance().getConnection();
             java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, hasEverBeenRestored);
            pstmt.setString(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (java.sql.SQLException e) {
            log.error("Lỗi cập nhật cờ hasEverBeenRestored: ", e);
            return false;
        }
    }

}