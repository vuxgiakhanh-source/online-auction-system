package com.group13.auction.config;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Cấu hình kết nối WebSocket từ client tới server.
 *
 * <p>Các giá trị có thể override bằng system property khi chạy Maven/IDE, ví dụ:
 * {@code -Dauction.server.host=localhost -Dauction.server.port=8080}.
 */
public final class SocketConfig {

    public static final String DEFAULT_SCHEME = "ws";
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 8080;
    public static final String DEFAULT_PATH = "/auction";

    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 5_000;
    public static final int DEFAULT_RECONNECT_DELAY_MILLIS = 2_000;

    private SocketConfig() {
        // Utility class.
    }

    /**
     * Tạo URI WebSocket từ system properties hoặc giá trị mặc định.
     *
     * @return URI kết nối tới auction server
     */
    public static URI serverUri() {
        String scheme = System.getProperty("auction.server.scheme", DEFAULT_SCHEME);
        String host = System.getProperty("auction.server.host", DEFAULT_HOST);
        int port = Integer.getInteger("auction.server.port", DEFAULT_PORT);
        String path = System.getProperty("auction.server.path", DEFAULT_PATH);

        try {
            return new URI(scheme, null, host, port, normalizePath(path), null, null);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Không thể tạo WebSocket URI từ cấu hình hiện tại.", exception);
        }
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return DEFAULT_PATH;
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}