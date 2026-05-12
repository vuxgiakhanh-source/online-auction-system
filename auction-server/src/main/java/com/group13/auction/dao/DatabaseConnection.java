package com.group13.auction.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnection {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);
    private static DatabaseConnection instance;

    // Lưu lại thông tin đăng nhập, KHÔNG lưu lại Connection
    private String url;
    private String username;
    private String password;

    private DatabaseConnection() {
        try {
            // Ưu tiên 1: env vars (Docker / CI)
            // Dockerfile truyền: DB_URL, DB_USERNAME, DB_PASSWORD
            String envUrl      = System.getenv("DB_URL");
            String envUsername = System.getenv("DB_USERNAME");
            String envPassword = System.getenv("DB_PASSWORD");

            if (envUrl != null && !envUrl.isBlank()) {
                // Chạy trong Docker / production
                this.url      = envUrl;
                this.username = envUsername != null ? envUsername : "";
                this.password = envPassword != null ? envPassword : "";
                log.info("Database config loaded from environment variables (Docker mode).");
            } else {
                // Ưu tiên 2: data.properties (dev local)
                Properties props = new Properties();
                InputStream is = getClass().getClassLoader().getResourceAsStream("data.properties");
                if (is == null) {
                    throw new RuntimeException(
                            "Không tìm thấy data.properties và env var DB_URL chưa được set.");
                }
                props.load(is);
                this.url      = props.getProperty("db.url");
                this.username = props.getProperty("db.username");
                this.password = props.getProperty("db.password");
                log.info("Database config loaded from data.properties (local dev mode).");
            }

            // Nạp Driver 1 lần duy nhất khi khởi động hệ thống
            Class.forName("com.mysql.cj.jdbc.Driver");
            log.info("MySQL JDBC Driver registered. DB URL: {}", url);

        } catch (Exception e) {
            log.error("Lỗi khởi tạo Database Connection", e);
            throw new RuntimeException(e);
        }
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    // Quan trọng: Hàm này tạo MỚI kết nối mỗi lần được gọi
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}