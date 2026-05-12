package com.group13.auction.config;

import java.net.URI;

/** Cấu hình kết nối WebSocket của client. */
public final class SocketConfig {

    public static final String DEFAULT_SERVER_URI = "ws://localhost:8080";

    private SocketConfig() {}

    /**
     * Lấy URI server từ system property {@code auction.server.uri}, hoặc dùng localhost.
     *
     * @return URI WebSocket của server
     */
    public static URI serverUri() {
        return URI.create(System.getProperty("auction.server.uri", DEFAULT_SERVER_URI));
    }
}
