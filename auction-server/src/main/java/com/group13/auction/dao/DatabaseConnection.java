package com.group13.auction.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DatabaseConnection — Singleton wrapping a HikariCP connection pool.
 *
 * <p>FIX BUG PERFORMANCE — connectionTimeout giảm từ 30s → 6s:
 *
 * <p>Vấn đề cũ: Client WebSocket timeout là 10 giây (AuctionWebSocketClient.sendAndExpect). Khi
 * pool đầy hoặc DB chậm, HikariCP block thread chờ connection tới 30 giây. Client cancel sau 10
 * giây → báo "server không phản hồi" dù server vẫn đang chạy. Handler sau đó nhận được response từ
 * DB, cố gửi về client đã disconnect → silent fail.
 *
 * <p>Fix: connectionTimeout = 6000ms (6s) < client timeout (10s). Khi pool đầy, HikariCP throw
 * SQLTransientConnectionException sau 6s, handler catch lại và gửi ErrorDTO về client TRƯỚC KHI
 * client timeout. Client nhận được "lỗi server" thay vì "server không phản hồi" — UX tốt hơn nhiều.
 *
 * <p>Thêm socketTimeout = 8000ms: nếu query DB hang (deadlock / slow query), MySQL driver sẽ throw
 * exception sau 8s, tránh thread block vô hạn.
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
      String envUrl = System.getenv("DB_URL");
      String envUsername = System.getenv("DB_USERNAME");
      String envPassword = System.getenv("DB_PASSWORD");

      if (envUrl != null && !envUrl.isBlank()) {
        this.url = envUrl;
        this.username = (envUsername != null) ? envUsername : "";
        this.password = (envPassword != null) ? envPassword : "";
        log.warn("Database config loaded from environment variables (Docker mode).");
        buildPool();
      } else {
        Properties props = new Properties();
        InputStream is = getClass().getClassLoader().getResourceAsStream("data.properties");
        if (is == null) {
          log.warn(
              "data.properties not found and DB_URL not set. "
                  + "Call reconfigure() before using DAOs (Testcontainers mode).");
          return;
        }
        props.load(is);
        this.url = props.getProperty("db.url");
        this.username = props.getProperty("db.username");
        this.password = props.getProperty("db.password");
        log.warn("Database config loaded from data.properties (local dev mode).");

        try {
          buildPool();
        } catch (Exception poolEx) {
          log.warn(
              "data.properties pool init failed (DB chưa chạy hoặc Testcontainers mode). "
                  + "Gọi reconfigure() trước khi dùng DAO. Lỗi: {}",
              poolEx.getMessage());
        }
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
    config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");

    // ── Pool sizing ───────────────────────────────────────────────────
    int poolMax = parseEnvInt("DB_POOL_MAX", 40);
    int poolMin = parseEnvInt("DB_POOL_MIN", 5);
    config.setMaximumPoolSize(poolMax);
    config.setMinimumIdle(poolMin);

    // ── Timeouts ──────────────────────────────────────────────────────
    // FIX PERFORMANCE: connectionTimeout = 6000ms (6s).
    //
    // Client WebSocket timeout (AuctionWebSocketClient.sendAndExpect) = 10s.
    // Pool connectionTimeout phải NHỎ HƠN client timeout để server kịp gửi
    // ErrorDTO về trước khi client drop connection.
    //
    // Logic: pool đầy → HikariCP throw SQLTransientConnectionException sau 6s
    //        → handler catch → gửi SYSTEM_ERROR về client (còn ~4s buffer)
    //        → client nhận "lỗi server" thay vì "server không phản hồi"
    config.setConnectionTimeout(6_000); // FIX: 30_000 → 6_000
    config.setIdleTimeout(300_000); // 5 phút
    config.setMaxLifetime(900_000); // 15 phút

    // ── MySQL socket & query timeouts ─────────────────────────────────
    // socketTimeout: nếu DB hang (deadlock, slow query), MySQL driver
    // throw exception sau 8s → tránh thread block vô hạn.
    // connectTimeout: TCP handshake timeout khi tạo connection mới.
    config.addDataSourceProperty("socketTimeout", "8000"); // FIX: thêm mới
    config.addDataSourceProperty("connectTimeout", "5000"); // FIX: thêm mới

    // ── MySQL prepared statement cache ────────────────────────────────
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "50");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "1024");
    config.addDataSourceProperty("useServerPrepStmts", "true");
    config.addDataSourceProperty("rewriteBatchedStatements", "true");

    config.setConnectionTestQuery("SELECT 1");
    config.setPoolName("AuctionPool");

    dataSource = new HikariDataSource(config);
    log.warn(
        "HikariCP pool created. URL: {} | maxPoolSize={} minIdle={} connTimeout=6s"
            + " socketTimeout=8s",
        url,
        poolMax,
        poolMin);
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

  /** Tái cấu hình pool sang URL mới — dùng trong Testcontainers @BeforeAll. */
  public synchronized void reconfigure(String newUrl, String newUsername, String newPassword) {
    this.url = newUrl;
    this.username = newUsername;
    this.password = newPassword;
    buildPool();
    log.warn("DatabaseConnection reconfigured. URL: {}", newUrl);
  }

  /** Đảm bảo pool đã sẵn sàng trước khi server chạy (ServerMain / IDE). */
  public void ensureReady(int maxAttempts, long delayMs) throws SQLException {
    SQLException last = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        if (dataSource == null || dataSource.isClosed()) {
          if (url == null || url.isBlank()) {
            throw new SQLException(
                "Chưa có cấu hình DB. Đặt DB_URL hoặc sửa data.properties, "
                    + "rồi chạy: docker compose up db -d");
          }
          buildPool();
        }
        try (Connection c = dataSource.getConnection()) {
          c.createStatement().execute("SELECT 1");
        }
        log.info("Database ready (attempt {}/{}).", attempt, maxAttempts);
        return;
      } catch (SQLException e) {
        last = e;
        if (dataSource != null && !dataSource.isClosed()) {
          dataSource.close();
        }
        dataSource = null;
        if (attempt < maxAttempts) {
          log.warn(
              "DB chưa sẵn sàng (attempt {}/{}): {} — thử lại sau {}ms",
              attempt,
              maxAttempts,
              e.getMessage(),
              delayMs);
          try {
            Thread.sleep(delayMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for database", ie);
          }
        }
      }
    }
    throw new SQLException(
        "Không kết nối được database sau "
            + maxAttempts
            + " lần thử. "
            + "Chạy: docker compose up db -d (MySQL cổng 3307). Chi tiết: "
            + (last != null ? last.getMessage() : "unknown"),
        last);
  }

  public Connection getConnection() throws SQLException {
    if (dataSource == null || dataSource.isClosed()) {
      throw new SQLException(
          "DataSource not initialized. Gọi ensureReady() hoặc reconfigure() trước. "
              + "Local dev: docker compose up db -d");
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

  private static int parseEnvInt(String key, int defaultVal) {
    String val = System.getenv(key);
    if (val != null && !val.isBlank()) {
      try {
        return Integer.parseInt(val.trim());
      } catch (NumberFormatException ignored) {
      }
    }
    return defaultVal;
  }
}
