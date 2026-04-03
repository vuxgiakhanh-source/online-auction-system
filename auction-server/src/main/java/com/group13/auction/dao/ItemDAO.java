package com.group13.auction.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ItemDAO {
    private Connection conn;

    public ItemDAO() {
        this.conn = DatabaseConnection.getInstance().getConnection();
    }

    // Thêm sản phẩm mới vào database
    public boolean addItem(int sellerId, String name, String description, double startingPrice, String categoryType) {
        String sql = "INSERT INTO items (seller_id, name, description, starting_price, category_type) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, sellerId);
            pstmt.setString(2, name);
            pstmt.setString(3, description);
            pstmt.setDouble(4, startingPrice);
            pstmt.setString(5, categoryType); // 'ELECTRONICS', 'ART', 'VEHICLE'

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Lỗi thêm sản phẩm: " + e.getMessage());
            return false;
        }
    }
}