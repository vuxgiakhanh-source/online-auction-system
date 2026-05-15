package com.group13.auction.unit.image;

import com.group13.auction.network.image.ImageUploadServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests cho {@link ImageUploadServer}.
 *
 * <p>Khởi động server thật trên cổng ngẫu nhiên, không mock network.
 * Không cần DB, không cần Docker.
 */
@DisplayName("ImageUploadServer — HTTP upload & serve")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImageUploadServerTest {

    private static final int TEST_PORT = 18081;
    private static ImageUploadServer server;
    private static Path uploadDir;

    @BeforeAll
    static void startServer() throws Exception {
        uploadDir = Files.createTempDirectory("img-test-");
        server = new ImageUploadServer(TEST_PORT, uploadDir.toString());
        server.start();
        Thread.sleep(100); // đợi server khởi động
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.stop();
        // Dọn thư mục tạm
        try (var walk = Files.walk(uploadDir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    // =========================================================================
    // POST /upload — happy paths
    // =========================================================================

    @Nested
    @DisplayName("POST /upload — happy path")
    class UploadHappyPath {

        @Test
        @Order(1)
        @DisplayName("JPEG nhỏ → 200, JSON chứa url bắt đầu bằng /uploads/items/")
        void jpeg_small_returns200AndUrl() throws Exception {
            byte[] fakeJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

            HttpURLConnection conn = openPost("/upload", "image/jpeg");
            conn.getOutputStream().write(fakeJpeg);

            assertThat(conn.getResponseCode()).isEqualTo(200);
            String body = readBody(conn);
            assertThat(body).contains("\"url\"");
            assertThat(body).contains("/uploads/items/");
            assertThat(body).endsWith("\"}");
        }

        @Test
        @Order(2)
        @DisplayName("PNG → 200, filename kết thúc bằng .png")
        void png_returns200WithPngExtension() throws Exception {
            byte[] fakePng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};

            HttpURLConnection conn = openPost("/upload", "image/png");
            conn.getOutputStream().write(fakePng);

            assertThat(conn.getResponseCode()).isEqualTo(200);
            String body = readBody(conn);
            assertThat(body).contains(".png");
        }

        @ParameterizedTest
        @ValueSource(strings = {"image/webp", "image/gif"})
        @Order(3)
        @DisplayName("webp và gif content-type → 200")
        void webpAndGif_accepted(String contentType) throws Exception {
            HttpURLConnection conn = openPost("/upload", contentType);
            conn.getOutputStream().write(new byte[]{1, 2, 3, 4});
            assertThat(conn.getResponseCode()).isEqualTo(200);
        }

        @Test
        @Order(4)
        @DisplayName("URL trả về có thể dùng để GET ảnh ngay sau khi upload")
        void uploadedFile_canBeServedViaGet() throws Exception {
            byte[] data = "fake-image-data".getBytes();
            HttpURLConnection uploadConn = openPost("/upload", "image/jpeg");
            uploadConn.getOutputStream().write(data);

            assertThat(uploadConn.getResponseCode()).isEqualTo(200);
            String json  = readBody(uploadConn);
            String url   = json.replaceAll(".*\"url\":\"([^\"]+)\".*", "$1");

            // GET ảnh vừa upload
            HttpURLConnection getConn = (HttpURLConnection)
                    URI.create("http://localhost:" + TEST_PORT + url)
                            .toURL().openConnection();
            getConn.setRequestMethod("GET");

            assertThat(getConn.getResponseCode()).isEqualTo(200);
            byte[] served = getConn.getInputStream().readAllBytes();
            assertThat(served).isEqualTo(data);
        }

        @Test
        @Order(5)
        @DisplayName("CORS header Access-Control-Allow-Origin được set")
        void upload_corsHeaderPresent() throws Exception {
            HttpURLConnection conn = openPost("/upload", "image/jpeg");
            conn.getOutputStream().write(new byte[]{1, 2, 3});
            conn.getResponseCode();
            assertThat(conn.getHeaderField("Access-Control-Allow-Origin")).isEqualTo("*");
        }
    }

    // =========================================================================
    // POST /upload — error paths
    // =========================================================================

    @Nested
    @DisplayName("POST /upload — error paths")
    class UploadErrorPaths {

        @Test
        @Order(10)
        @DisplayName("Content-Type không phải image/* → 415")
        void nonImageContentType_returns415() throws Exception {
            HttpURLConnection conn = openPost("/upload", "application/json");
            conn.getOutputStream().write("{\"foo\":\"bar\"}".getBytes());
            assertThat(conn.getResponseCode()).isEqualTo(415);
        }

        @Test
        @Order(11)
        @DisplayName("Body rỗng → 400")
        void emptyBody_returns400() throws Exception {
            HttpURLConnection conn = openPost("/upload", "image/jpeg");
            conn.getOutputStream().write(new byte[0]);
            assertThat(conn.getResponseCode()).isEqualTo(400);
        }

        @Test
        @Order(12)
        @DisplayName("File > MAX_FILE_BYTES → 413")
        void oversizedFile_returns413() throws Exception {
            // MAX = 2_000_000, gửi 2_000_001 bytes
            byte[] huge = new byte[(int) ImageUploadServer.MAX_FILE_BYTES + 1];
            HttpURLConnection conn = openPost("/upload", "image/jpeg");
            conn.getOutputStream().write(huge);
            assertThat(conn.getResponseCode()).isEqualTo(413);
        }

        @Test
        @Order(13)
        @DisplayName("GET /upload → 405 Method Not Allowed")
        void getOnUploadEndpoint_returns405() throws Exception {
            HttpURLConnection conn = (HttpURLConnection)
                    URI.create("http://localhost:" + TEST_PORT + "/upload")
                            .toURL().openConnection();
            conn.setRequestMethod("GET");
            assertThat(conn.getResponseCode()).isEqualTo(405);
        }
    }

    // =========================================================================
    // GET /uploads/items/{filename} — serve
    // =========================================================================

    @Nested
    @DisplayName("GET /uploads/items/ — serve")
    class ServeTests {

        @Test
        @Order(20)
        @DisplayName("File không tồn tại → 404")
        void nonExistentFile_returns404() throws Exception {
            HttpURLConnection conn = (HttpURLConnection)
                    URI.create("http://localhost:" + TEST_PORT + "/uploads/items/notexist.jpg")
                            .toURL().openConnection();
            conn.setRequestMethod("GET");
            assertThat(conn.getResponseCode()).isEqualTo(404);
        }

        @Test
        @Order(21)
        @DisplayName("Path traversal attempt → 400")
        void pathTraversal_returns400() throws Exception {
            HttpURLConnection conn = (HttpURLConnection)
                    URI.create("http://localhost:" + TEST_PORT + "/uploads/items/..%2F..%2Fetc%2Fpasswd")
                            .toURL().openConnection();
            conn.setRequestMethod("GET");
            // server sẽ trả 400 hoặc 404 — cả hai đều chấp nhận (không được 200)
            assertThat(conn.getResponseCode()).isNotEqualTo(200);
        }

        @Test
        @Order(22)
        @DisplayName("POST /uploads/items/ → 405")
        void postOnServeEndpoint_returns405() throws Exception {
            HttpURLConnection conn = (HttpURLConnection)
                    URI.create("http://localhost:" + TEST_PORT + "/uploads/items/x.jpg")
                            .toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.getOutputStream().write(new byte[]{1});
            assertThat(conn.getResponseCode()).isEqualTo(405);
        }
    }

    // =========================================================================
    // MAX_FILE_BYTES constant
    // =========================================================================

    @Test
    @DisplayName("MAX_FILE_BYTES = 2_000_000")
    void maxFileBytesConstant() {
        assertThat(ImageUploadServer.MAX_FILE_BYTES).isEqualTo(2_000_000L);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static HttpURLConnection openPost(String path, String contentType) throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                URI.create("http://localhost:" + TEST_PORT + path)
                        .toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", contentType);
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    private static String readBody(HttpURLConnection conn) throws Exception {
        InputStream is = conn.getResponseCode() < 400
                ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "";
        return new String(is.readAllBytes());
    }
}