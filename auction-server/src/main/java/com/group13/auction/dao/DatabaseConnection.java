package com.group13.auction.dao;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnection {
    // Biến tĩnh lưu trữ instance duy nhất (Singleton Pattern)
    private static DatabaseConnection instance;
    private Connection connection;

    // Constructor private ngăn chặn việc tạo đối tượng bằng từ khóa 'new' từ bên ngoài
    private DatabaseConnection() {
        try {
            // Đọc thông tin cấu hình từ file database.properties
            Properties props = new Properties();
            InputStream is = getClass().getClassLoader().getResourceAsStream("database.properties");

            if (is == null) {
                System.err.println("Lỗi: Không tìm thấy file database.properties trong thư mục resources!");
                return;
            }
            props.load(is);

            // Lấy URL, Username và Password từ file
            String url = props.getProperty("db.url");
            String username = props.getProperty("db.username");
            String password = props.getProperty("db.password");

            // Nạp Driver và thiết lập kết nối MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.connection = DriverManager.getConnection(url, username, password);
            System.out.println("Kết nối Database thành công!");

        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo kết nối Database: " + e.getMessage());
        }
    }

    // Phương thức cung cấp điểm truy cập toàn cục và an toàn đa luồng (Thread-safe)
    public static synchronized DatabaseConnection getInstance() {
        try {
            if (instance == null) {
                instance = new DatabaseConnection();
            } else {
                Connection conn = instance.getConnection();

                // Thêm null-check trước khi gọi isClosed()
                if (conn == null || conn.isClosed()) {
                    instance = new DatabaseConnection();
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi kiểm tra trạng thái kết nối: " + e.getMessage());
        }
        return instance;
    }



    // Cung cấp Connection cho các lớp UserDAO, ItemDAO,... sử dụng
    public Connection getConnection() {
        return connection;
    }
}