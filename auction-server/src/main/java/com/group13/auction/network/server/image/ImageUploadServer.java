package com.group13.auction.network.server.image;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP server nhỏ phục vụ upload và serve ảnh sản phẩm.
 *
 * <h3>Hai endpoint:</h3>
 *
 * <ul>
 *   <li>{@code POST /upload} — nhận file ảnh (raw body), lưu vào disk, trả về JSON {@code
 *       {"url":"/uploads/items/{uuid}.jpg"}}
 *   <li>{@code GET /uploads/items/{filename}} — serve file ảnh đã lưu
 * </ul>
 *
 * <h3>Giới hạn:</h3>
 *
 * <ul>
 *   <li>Kích thước file tối đa: {@value #MAX_FILE_BYTES} bytes (2 MB)
 *   <li>Content-Type phải là image/* (jpg, png, webp, gif)
 * </ul>
 *
 * <h3>Cách dùng:</h3>
 *
 * <pre>{@code
 * ImageUploadServer imgServer = new ImageUploadServer(8081, "uploads/items");
 * imgServer.start();
 * // Khi shutdown:
 * imgServer.stop();
 * }</pre>
 */
public class ImageUploadServer {

  private static final Logger log = LoggerFactory.getLogger(ImageUploadServer.class);

  /** Kích thước tối đa mỗi file ảnh = 2 MB. */
  public static final long MAX_FILE_BYTES = 2_000_000L;

  private final int port;
  private final Path uploadDir;
  private HttpServer server;

  /**
   * @param port cổng HTTP (mặc định 8081, khác với WebSocket 8080)
   * @param uploadDir thư mục lưu ảnh trên disk (ví dụ: "uploads/items")
   */
  public ImageUploadServer(int port, String uploadDir) {
    this.port = port;
    this.uploadDir = Paths.get(uploadDir).toAbsolutePath();
  }

  /** Khởi động HTTP server. Gọi sau khi WebSocket server đã start. */
  public void start() throws IOException {
    Files.createDirectories(uploadDir);

    server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/upload", this::handleUpload);
    server.createContext("/uploads/items/", this::handleServe);
    server.setExecutor(
        Executors.newFixedThreadPool(
            4,
            r -> {
              Thread t = new Thread(r, "img-http");
              t.setDaemon(true);
              return t;
            }));
    server.start();
    log.info("ImageUploadServer started: port={}, uploadDir={}", port, uploadDir);
  }

  /** Dừng HTTP server. Gọi trong shutdown hook. */
  public void stop() {
    if (server != null) {
      server.stop(1);
      log.info("ImageUploadServer stopped.");
    }
  }

  // POST /upload

  private void handleUpload(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      respond(exchange, 405, "Method Not Allowed");
      return;
    }

    // Kiểm tra Content-Type
    String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
    if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
      respond(exchange, 415, "{\"error\":\"Content-Type phải là image/*\"}");
      return;
    }

    // Đọc body
    try (InputStream in = exchange.getRequestBody()) {
      byte[] data = in.readNBytes((int) MAX_FILE_BYTES + 1);
      if (data.length > MAX_FILE_BYTES) {
        respond(exchange, 413, "{\"error\":\"File quá lớn, tối đa 2MB\"}");
        return;
      }
      if (data.length == 0) {
        respond(exchange, 400, "{\"error\":\"File rỗng\"}");
        return;
      }

      // Xác định extension
      String ext = extensionFromContentType(contentType);

      // Lưu file
      String filename = UUID.randomUUID() + ext;
      Path dest = uploadDir.resolve(filename);
      Files.write(dest, data);

      String url = "/uploads/items/" + filename;
      String json = "{\"url\":\"" + url + "\"}";

      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
      byte[] response = json.getBytes();
      exchange.sendResponseHeaders(200, response.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(response);
      }
      log.info("Image uploaded: filename={}, bytes={}", filename, data.length);
    }
  }

  // GET /uploads/items/{filename}

  private void handleServe(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      respond(exchange, 405, "Method Not Allowed");
      return;
    }

    String uriPath = exchange.getRequestURI().getPath(); // /uploads/items/abc.jpg
    String filename = Paths.get(uriPath).getFileName().toString();

    // Ngăn path traversal
    if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
      respond(exchange, 400, "Bad Request");
      return;
    }

    Path filePath = uploadDir.resolve(filename);
    if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
      respond(exchange, 404, "Not Found");
      return;
    }

    String mimeType = Files.probeContentType(filePath);
    if (mimeType == null) {
      mimeType = "application/octet-stream";
    }

    byte[] data = Files.readAllBytes(filePath);
    exchange.getResponseHeaders().set("Content-Type", mimeType);
    exchange.getResponseHeaders().set("Cache-Control", "max-age=86400");
    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
    exchange.sendResponseHeaders(200, data.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(data);
    }
  }

  // Helpers

  private static void respond(HttpExchange exchange, int code, String body) throws IOException {
    byte[] bytes = body.getBytes();
    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
    exchange.sendResponseHeaders(code, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private static String extensionFromContentType(String contentType) {
    String ct = contentType.toLowerCase();
    if (ct.contains("png")) {
      return ".png";
    }
    if (ct.contains("gif")) {
      return ".gif";
    }
    if (ct.contains("webp")) {
      return ".webp";
    }
    return ".jpg"; // default jpeg
  }
}
