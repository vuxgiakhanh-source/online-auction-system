package com.group13.auction.dao;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(ItemDAO.class);
    private static final Gson GSON = new Gson();

    public ItemDAO() {}

    // ── addItem — backward-compatible (không ảnh) ─────────────────────────────

    /**
     * Thêm sản phẩm vào DB, không có ảnh.
     * API cũ — tất cả test integration hiện tại vẫn dùng được.
     */
    public boolean addItem(String itemId, String sellerId, String name,
                           String description, long startingPrice, String categoryType) {
        return addItem(itemId, sellerId, name, description, startingPrice,
                categoryType, List.of());
    }

    // ── addItem — có ảnh ──────────────────────────────────────────────────────

    /**
     * Thêm sản phẩm vào DB kèm danh sách URL ảnh.
     *
     * @param imageUrls danh sách URL "/uploads/items/{uuid}.jpg" (có thể rỗng)
     * @return true nếu insert thành công
     */
    public boolean addItem(String itemId, String sellerId, String name,
                           String description, long startingPrice,
                           String categoryType, List<String> imageUrls) {
        String sql = "INSERT INTO items "
                + "(id, seller_id, name, description, starting_price, category_type, image_urls) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, itemId);
            ps.setString(2, sellerId);
            ps.setString(3, name);
            ps.setString(4, description);
            ps.setLong(5, startingPrice);
            ps.setString(6, categoryType);
            ps.setString(7, toJson(imageUrls));

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi thêm sản phẩm: ", e);
            return false;
        }
    }

    // ── findItemById ──────────────────────────────────────────────────────────

    public Item findItemById(String itemId) {
        String sql = "SELECT * FROM items WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            log.error("Lỗi tìm Item theo ID: ", e);
        }
        return null;
    }

    // ── findItemsBySellerId ───────────────────────────────────────────────────

    public List<Item> findItemsBySellerId(String sellerId) {
        List<Item> items = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE seller_id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Item item = mapRow(rs);
                    if (item != null) items.add(item);
                }
            }
        } catch (SQLException e) {
            log.error("Lỗi lấy danh sách item của seller: ", e);
        }
        return items;
    }

    // ── deleteItem ────────────────────────────────────────────────────────────

    /** Xóa item theo ID — dùng rollback khi createAuction() thất bại. */
    public boolean deleteItem(String itemId) {
        String sql = "DELETE FROM items WHERE id = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("Lỗi xóa item (rollback orphan): ", e);
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Item mapRow(ResultSet rs) throws SQLException {
        String id           = rs.getString("id");
        String name         = rs.getString("name");
        String description  = rs.getString("description");
        long startingPrice  = rs.getLong("starting_price");
        String categoryType = rs.getString("category_type");
        String sellerId     = rs.getString("seller_id");

        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime createdAt = (ts != null) ? ts.toLocalDateTime() : LocalDateTime.now();

        // updated_at co the chua co trong schema cu - fallback ve createdAt
        LocalDateTime updatedAt;
        try {
            Timestamp tsUp = rs.getTimestamp("updated_at");
            updatedAt = (tsUp != null) ? tsUp.toLocalDateTime() : createdAt;
        } catch (SQLException ex) {
            updatedAt = createdAt;
        }

        // Đọc imageUrls — null-safe khi cột chưa tồn tại (migration cũ)
        List<String> imageUrls;
        try {
            imageUrls = fromJson(rs.getString("image_urls"));
        } catch (SQLException ex) {
            imageUrls = List.of(); // cột chưa có trong schema cũ
        }

        NormalUser seller = new UserDAO().findNormalUserById(sellerId);

        switch (categoryType) {
            case "ELECTRONICS": {
                String brand         = rs.getString("brand");
                int warrantyMonths   = rs.getInt("warranty_months");
                String condition     = rs.getString("condition");
                return Electronics.reconstitute(id, createdAt, updatedAt,
                        name, description, startingPrice,
                        seller, brand, warrantyMonths, condition, imageUrls);
            }
            case "ART": {
                String artist   = rs.getString("artist");
                int yearCreated = rs.getInt("year_created");
                String medium   = rs.getString("medium");
                return Art.reconstitute(id, createdAt, updatedAt,
                        name, description, startingPrice,
                        seller, artist, yearCreated, medium, imageUrls);
            }
            case "VEHICLE": {
                String manufacturer = rs.getString("manufacturer");
                int year            = rs.getInt("year");
                double mileage      = rs.getDouble("mileage");
                return Vehicle.reconstitute(id, createdAt, updatedAt,
                        name, description, startingPrice,
                        seller, manufacturer, year, mileage, imageUrls);
            }
            default:
                log.error("Loại item không được hỗ trợ: {}", categoryType);
                return null;
        }
    }

    /** Serialize List<String> → JSON (lưu DB). */
    static String toJson(List<String> urls) {
        return GSON.toJson(urls != null ? urls : List.of());
    }

    /** Deserialize JSON từ DB → List<String>. Null-safe. */
    static List<String> fromJson(String json) {
        if (json == null || json.isBlank() || json.equals("null")) return List.of();
        try {
            List<String> result = GSON.fromJson(json,
                    new TypeToken<List<String>>() {}.getType());
            return result != null ? result : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}