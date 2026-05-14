package com.group13.auction.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * DatabaseConnection — Singleton wrapping a HikariCP connection pool.
 *
 * <p>Thay thế DriverManager.getConnection() cũ (tạo connection mới mỗi lần)
 * bằng HikariCP pool để tránh lỗi "Address already in use" / CommunicationsException
 * khi nhiều thread đồng thời yêu cầu kết nối trong load test.</p>
 *
 * <p><b>Cấu hình pool mặc định:</b>
 * maximumPoolSize=50, minimumIdle=5, connectionTimeout=30s</p>
 *
 * <p><b>Để test (Testcontainers):</b> gọi {@link #reconfigure(String, String, String)}
 * sau khi container khởi động.</p>
 */
public class DatabaseConnection {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnection.class);

    private static volatile DatabaseConnection instance;

    private volatile String url;
    private volatile String username;
    private volatile String password;

    private volatile HikariDataSource dataSource;

    private DatabaseConnection() {
        try {
            String envUrl      = System.getenv("DB_URL");
            String envUsername = System.getenv("DB_USERNAME");
            String envPassword = System.getenv("DB_PASSWORD");

            if (envUrl != null && !envUrl.isBlank()) {
                this.url      = envUrl;
                this.username = (envUsername != null) ? envUsername : "";
                this.password = (envPassword != null) ? envPassword : "";
                log.info("Database config loaded from environment variables (Docker mode).");
                buildPool();
            } else {
                Properties props = new Properties();
                InputStream is = getClass().getClassLoader().getResourceAsStream("data.properties");
                if (is == null) {
                    log.warn("data.properties not found and DB_URL not set. " +
                            "Call reconfigure() before using DAOs (Testcontainers mode).");
                    return;
                }
                props.load(is);
                this.url      = props.getProperty("db.url");
                this.username = props.getProperty("db.username");
                this.password = props.getProperty("db.password");
                log.info("Database config loaded from data.properties (local dev mode).");
                buildPool();
            }
        } catch (Exception e) {
            log.error("Error initializing DatabaseConnection", e);
            throw new RuntimeException(e);
        }
    }

    private synchronized void buildPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        // Giảm anomaly đọc/ghi đồng thời giữa các connection (scheduler vs handler).
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");

        // Pool sizing — enough for 50+ concurrent threads in load tests
        config.setMaximumPoolSize(50);
        config.setMinimumIdle(5);

        // Timeouts
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);

        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("AuctionPool");

        dataSource = new HikariDataSource(config);
        log.info("HikariCP pool created. URL: {} | maxPoolSize=50", url);
    }

    public static DatabaseConnection getInstance() {
        if (instance == null) {
            synchronized (DatabaseConnection.class) {
                if (instance == null) {
                    instance = new DatabaseConnection();
                }
            }
        }
        return instance;
    }

    /**
     * Tái cấu hình pool sang URL mới — dùng trong Testcontainers @BeforeAll.
     * Đóng pool cũ và tạo pool mới hoàn toàn. Thread-safe.
     */
    public synchronized void reconfigure(String newUrl, String newUsername, String newPassword) {
        this.url      = newUrl;
        this.username = newUsername;
        this.password = newPassword;
        buildPool();
        log.info("DatabaseConnection reconfigured. URL: {}", newUrl);
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException(
                    "DataSource not initialized. Call reconfigure() first (Testcontainers mode).");
        }
        return dataSource.getConnection();
    }

    public synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("HikariCP pool closed.");
        }
    }

    /** Reset singleton — for test isolation only. Do NOT call in production. */
    static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}
