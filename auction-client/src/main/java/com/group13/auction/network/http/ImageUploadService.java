package com.group13.auction.network.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.group13.auction.config.ImageConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Upload ảnh lên ImageUploadServer — POST raw binary, nhận {@code {"url":"/uploads/items/..."}}.
 */
public final class ImageUploadService {

  private static final long MAX_BYTES = 2L * 1024 * 1024;
  private static final ImageUploadService INSTANCE = new ImageUploadService();

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private ImageUploadService() {}

  public static ImageUploadService getInstance() {
    return INSTANCE;
  }

  /**
   * Upload file từ disk.
   *
   * @param filePath đường dẫn file ảnh local
   * @return URL server dạng /uploads/items/{uuid}.ext
   */
  public String upload(Path filePath) throws IOException, InterruptedException {
    String contentType = Files.probeContentType(filePath);
    if (contentType == null || !contentType.startsWith("image/")) {
      contentType = "image/jpeg";
    }
    byte[] data = Files.readAllBytes(filePath);
    return uploadBytes(data, contentType);
  }

  /** Upload bytes ảnh. */
  public String uploadBytes(byte[] data, String contentType)
      throws IOException, InterruptedException {
    if (data.length > MAX_BYTES) {
      throw new IOException("Ảnh vượt quá 2MB.");
    }
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(ImageConfig.uploadUrl()))
            .header("Content-Type", contentType)
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofByteArray(data))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IOException(
          "Upload thất bại HTTP " + response.statusCode() + ": " + response.body());
    }

    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
    if (!json.has("url")) {
      throw new IOException("Phản hồi upload không có trường url.");
    }
    return json.get("url").getAsString();
  }
}
