package com.group13.auction.config;

/**
 * Cấu hình HTTP image server (port 8081) — tách khỏi WebSocket auction server.
 */
public final class ImageConfig {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 8081;
    public static final String UPLOAD_PATH = "/upload";
    public static final String ITEMS_BASE_PATH = "/uploads/items/";

    private ImageConfig() {}

    public static String baseUrl() {
        String host = System.getProperty("auction.image.host", DEFAULT_HOST);
        int port = Integer.getInteger("auction.image.port", DEFAULT_PORT);
        return "http://" + host + ":" + port;
    }

    public static String uploadUrl() {
        return baseUrl() + UPLOAD_PATH;
    }

    /** Chuyển path server (/uploads/items/x.jpg) thành URL đầy đủ để hiển thị. */
    public static String toFullUrl(String serverPath) {
        if (serverPath == null || serverPath.isBlank()) {
            return "";
        }
        if (serverPath.startsWith("http://") || serverPath.startsWith("https://")) {
            return serverPath;
        }
        return baseUrl() + (serverPath.startsWith("/") ? serverPath : "/" + serverPath);
    }
}
