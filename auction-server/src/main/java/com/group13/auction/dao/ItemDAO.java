package com.group13.auction.dao;

import com.group13.auction.model.item.Art;
import com.group13.auction.model.item.Electronics;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.item.Vehicle;
import com.group13.auction.model.user.NormalUser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ItemDAO {

    public ItemDAO() {}

    /**
     * Thêm sản phẩm mới vào database.
     *
     * <p>Đã sửa bug: sellerId dùng String (UUID) thay vì int để khớp với
     * toàn bộ hệ thống. Connection lấy cục bộ thay vì giữ làm field.
     *
     * @param itemId       UUID của item (sinh từ Entity)
     * @param sellerId     UUID của seller (String, không phải int)
     * @param name         tên sản phẩm
     * @param description  mô tả
     * @param startingPrice giá khởi điểm
     * @param categoryType loại sản phẩm: 'ELECTRONICS', 'ART', 'VEHICLE'
     * @return true nếu insert thành công
     */
    public boolean addItem(String itemId, String sellerId, String name,
                           String description, long startingPrice, String categoryType) {
        String sql = "INSERT INTO items (id, seller_id, name, description, starting_price, category_type) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemId);
            pstmt.setString(2, sellerId);
            pstmt.setString(3, name);
            pstmt.setString(4, description);
            pstmt.setLong(5, startingPrice);
            pstmt.setString(6, categoryType);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi thêm sản phẩm: " + e.getMessage());
            return false;
        }
    }

    /**
     * Tìm Item theo ID và hồi sinh đối tượng đúng loại từ DB.
     *
     * <p>Hồi sinh theo {@code category_type}:
     * <ul>
     *   <li>ELECTRONICS → {@link Electronics#reconstitute}</li>
     *   <li>ART         → {@link Art#reconstitute}</li>
     *   <li>VEHICLE     → {@link Vehicle#reconstitute}</li>
     * </ul>
     *
     * <p>Bắt buộc phải có — {@link AuctionDAO#findAuctionById} và
     * {@link #findItemsBySellerId} đều gọi hàm này.
     *
     * @param itemId UUID của item cần tìm
     * @return Item tương ứng, hoặc null nếu không tìm thấy
     */
    public Item findItemById(String itemId) {
        String sql = "SELECT * FROM items WHERE id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToItem(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi tìm Item theo ID: " + e.getMessage());
        }
        return null;
    }

    /**
     * Lấy danh sách tất cả Item của một Seller từ DB.
     * Dùng để inject {@code setListedItems()} sau khi reconstitute NormalUser.
     *
     * @param sellerId UUID của seller
     * @return danh sách Item đã đăng bán (có thể rỗng, không null)
     */
    public List<Item> findItemsBySellerId(String sellerId) {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE seller_id = ?";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sellerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Item item = mapRowToItem(rs);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi lấy danh sách item của seller: " + e.getMessage());
        }
        return items;
    }

    // ─── Private helper ───────────────────────────────────────────────────────

    /**
     * Map một hàng ResultSet thành đối tượng Item đúng loại.
     * Dùng chung cho findItemById() và findItemsBySellerId().
     *
     * @param rs ResultSet đang trỏ vào hàng cần đọc
     * @return Item tương ứng, hoặc null nếu category_type không được hỗ trợ
     * @throws SQLException nếu đọc cột bị lỗi
     */
    private Item mapRowToItem(ResultSet rs) throws SQLException {
        String id           = rs.getString("id");
        String name         = rs.getString("name");
        String description  = rs.getString("description");
        long startingPrice = rs.getLong("starting_price");
        String categoryType = rs.getString("category_type");
        String sellerId     = rs.getString("seller_id");

        Timestamp createdTs = rs.getTimestamp("created_at");
        LocalDateTime createdAt = (createdTs != null)
                ? createdTs.toLocalDateTime() : LocalDateTime.now();

        // Lấy seller object
        UserDAO userDAO = new UserDAO();
        NormalUser seller = userDAO.findNormalUserById(sellerId);

        // Hồi sinh đúng loại theo category
        switch (categoryType) {
            case "ELECTRONICS": {
                String brand          = rs.getString("brand");
                int    warrantyMonths = rs.getInt("warranty_months");
                String condition      = rs.getString("condition");
                return Electronics.reconstitute(id, createdAt, createdAt,
                        name, description, startingPrice,
                        seller, brand, warrantyMonths, condition);
            }
            case "ART": {
                String artist      = rs.getString("artist");
                int    yearCreated = rs.getInt("year_created");
                String medium      = rs.getString("medium");
                return Art.reconstitute(id, createdAt, createdAt,
                        name, description, startingPrice,
                        seller, artist, yearCreated, medium);
            }
            case "VEHICLE": {
                String manufacturer = rs.getString("manufacturer");
                int    year         = rs.getInt("year");
                double mileage      = rs.getDouble("mileage");
                return Vehicle.reconstitute(id, createdAt, createdAt,
                        name, description, startingPrice,
                        seller, manufacturer, year, mileage);
            }
            default:
                System.err.println("Loại item không được hỗ trợ: " + categoryType);
                return null;
        }
    }

    /**
     * Xóa item theo ID — dùng để rollback khi createAuction() thất bại (FIX Bug #7).
     * Ngăn item trở thành orphan record trong DB.
     *
     * @param itemId UUID của item cần xóa
     * @return true nếu xóa thành công
     */
    public boolean deleteItem(String itemId) {
        String sql = "DELETE FROM items WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, itemId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi xóa item (rollback orphan): " + e.getMessage());
            return false;
        }
    }
}