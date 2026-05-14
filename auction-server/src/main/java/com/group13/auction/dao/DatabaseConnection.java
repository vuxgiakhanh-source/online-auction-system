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
 * FIX #5 — HikariCP tuning cho load test:
 *
 * maximumPoolSize: 50 → 20
 *   Cũ dùng 50 nhưng load test chỉ có 12 thread bidding.
 *   Pool quá lớn làm MySQL tốn tài nguyên duy trì idle connections.
 *   20 là đủ cho 12 bid thread + overhead (join, watch, anti-sniping).
 *
 * minimumIdle: 5 → 12
 *   Pre-warm đủ connection sẵn cho 12 thread → không phải chờ tạo mới.
 *
 * connectionTimeout: 30s → 5s
 *   Load test expect bid nhanh — nếu pool hết connection sau 5s thì
 *   nên fail nhanh thay vì block thread 30s.
 *
 * prepStmtCacheSize + prepStmtCacheSqlLimit:
 *   Cache prepared statement → tránh parse lại SQL mỗi lần INSERT bid_transactions.
 *   MySQL driver hỗ trợ server-side prepared statement cache.
 *
 * useServerPrepStmts + cachePrepStmts:
 *   Bật server-side prepared statement → giảm round-trip parse SQL trên MySQL.
 *
 * rewriteBatchedStatements:
 *   Dù hiện tại không dùng batch, bật sẵn để MySQL driver tự gộp INSERT
 *   nếu sau này chuyển sang executeBatch().
 *
 * autoCommit: true (default, giữ nguyên)
 *   Mỗi saveTransaction() là 1 INSERT đơn → autoCommit phù hợp.
 *   Nếu sau này dùng batch thì cần tắt và commit thủ công.
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
                log.warn("Database config loaded from environment variables (Docker mode).");
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
                log.warn("Database config loaded from data.properties (local dev mode).");
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

        // ── FIX #5: Pool sizing ────────────────────────────────────────────
        // Load test: 12 bid thread + vài thread join/watch/anti-sniping → 20 đủ
        // Quá lớn (50) gây MySQL overhead duy trì idle connections
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(12);  // Pre-warm đủ cho 12 bid thread

        // ── FIX #5: Timeouts ──────────────────────────────────────────────
        config.setConnectionTimeout(5_000);    // Fail nhanh nếu pool cạn (cũ: 30s)
        config.setIdleTimeout(300_000);         // 5 phút (cũ: 10 phút)
        config.setMaxLifetime(900_000);         // 15 phút (cũ: 30 phút)

        // ── FIX #5: MySQL prepared statement cache ────────────────────────
        // Tránh parse lại SQL mỗi lần INSERT bid_transactions
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "50");       // cache 50 statement
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "1024"); // max 1024 ký tự/stmt
        config.addDataSourceProperty("useServerPrepStmts", "true");    // server-side cache

        // FIX #5: Batch insert sẵn sàng khi cần
        config.addDataSourceProperty("rewriteBatchedStatements", "true");

        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("AuctionPool");

        dataSource = new HikariDataSource(config);
        log.warn("HikariCP pool created. URL: {} | maxPoolSize=20, minIdle=12", url);
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
        log.warn("DatabaseConnection reconfigured. URL: {}", newUrl);
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
            log.warn("HikariCP pool closed.");
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