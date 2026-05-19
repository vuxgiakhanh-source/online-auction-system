package com.group13.auction.ui.controller.home;

import com.group13.auction.core.navigation.Navigator;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.application.Platform;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

/** Controller cho màn landing/welcome của OmniBid client. */
public class LandingController implements Initializable {

    // --- CÁC THÀNH PHẦN FXML XỬ LÝ ANIMATION & VIDEO NỀN ---
    @FXML
    private ScrollPane mainScrollPane;

    @FXML
    private StackPane revealBlock; // Đã đổi từ VBox sang StackPane để làm khay chứa video nền

    @FXML
    private MediaView bgMediaView; // Khai báo MediaView điều khiển video nền từ FXML

    @FXML
    private VBox revealContent; // Khối chữ được animate, tách riêng khỏi video nền

    private MediaPlayer mediaPlayer; // Khai báo biến global để tránh bị Garbage Collector tự động xóa khi đang phát

    // Biến cờ kiểm tra xem khối giao diện đã được hiện ra chưa
    private boolean isRevealed = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. Chuẩn bị trạng thái ban đầu: Kéo khối giao diện xuống 50px
        if (revealContent != null) {
            revealContent.setTranslateY(50);
        }

        // 2. Cấu hình MediaView và Video nền
        if (bgMediaView != null) {
            bgMediaView.setPreserveRatio(true);
            // Clipping để video không tràn ra ngoài revealBlock khi scale
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
            clip.widthProperty().bind(revealBlock.widthProperty());
            clip.heightProperty().bind(revealBlock.heightProperty());
            revealBlock.setClip(clip);

            // Cập nhật kích thước MediaView để "Cover" toàn bộ vùng chứa mà không bị méo
            revealBlock.widthProperty().addListener((obs, oldVal, newVal) -> updateVideoSize());
            revealBlock.heightProperty().addListener((obs, oldVal, newVal) -> updateVideoSize());
        }

        initBackgroundVideo();

        // 3. Lắng nghe sự kiện người dùng cuộn thanh cuộn dọc (vvalue)
        if (mainScrollPane != null) {
            mainScrollPane.vvalueProperty().addListener((observable, oldValue, newValue) -> {
                checkAndRevealBlock();
            });
            Platform.runLater(this::checkAndRevealBlock);
        }
    }

    private void updateVideoSize() {
        if (bgMediaView == null || revealBlock == null || mediaPlayer == null) return;
        Media media = mediaPlayer.getMedia();
        if (media == null) return;

        double containerW = revealBlock.getWidth();
        double containerH = revealBlock.getHeight();
        double videoW = media.getWidth();
        double videoH = media.getHeight();

        if (containerW <= 0 || containerH <= 0 || videoW <= 0 || videoH <= 0) return;

        double scaleW = containerW / videoW;
        double scaleH = containerH / videoH;
        double maxScale = Math.max(scaleW, scaleH);

        bgMediaView.setFitWidth(videoW * maxScale);
        bgMediaView.setFitHeight(videoH * maxScale);
    }

    /**
     * Khởi tạo và thiết lập video nền lặp vô hạn, tắt âm thanh.
     */
    private void initBackgroundVideo() {
        loadVideo("/com/group13/auction/assets/videos/bg-landing.mp4");
    }

    private void loadVideo(String path) {
        try {
            URL videoUrl = getClass().getResource(path);
            if (videoUrl == null) {
                System.err.println("Không tìm thấy file video: " + path);
                return;
            }

            Media media = new Media(videoUrl.toExternalForm());
            if (mediaPlayer != null) {
                mediaPlayer.dispose();
            }
            mediaPlayer = new MediaPlayer(media);

            // Cấu hình player
            mediaPlayer.setMute(true);
            mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);

            // Xử lý lỗi
            mediaPlayer.setOnError(() -> {
                System.err.println("Lỗi player video nền (" + path + "): " + mediaPlayer.getError());
                // Nếu video chính lỗi, thử video backup
                if (path.equals("/com/group13/auction/assets/videos/bg-landing.mp4")) {
                    Platform.runLater(() -> loadVideo("/com/group13/auction/assets/videos/bg-landing-av1-backup.mp4"));
                }
            });

            // Theo dõi trạng thái
            mediaPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
                if (newStatus == MediaPlayer.Status.READY) {
                    bgMediaView.setMediaPlayer(mediaPlayer);
                    updateVideoSize();
                    mediaPlayer.play();
                }
            });

        } catch (Exception e) {
            System.err.println("Không thể khởi tạo video " + path + ": " + e.getMessage());
        }
    }

    /** * Kiểm tra tọa độ hiện tại khi cuộn và chạy Animation hiện/ẩn linh hoạt.
     */
    private void checkAndRevealBlock() {
        if (revealBlock == null) return;

        // Lấy tọa độ không gian thực của thanh cuộn và khối giao diện
        Bounds scrollBounds = mainScrollPane.localToScene(mainScrollPane.getBoundsInLocal());
        Bounds blockBounds = revealBlock.localToScene(revealBlock.getBoundsInLocal());

        // Điều kiện HIỆN: Cạnh trên của khối lọt vào cách đáy màn hình 100px
        boolean shouldShow = blockBounds.getMinY() < scrollBounds.getMaxY() - 100;

        // Điều kiện ẨN: Cạnh trên của khối bị cuộn tụt hoàn toàn xuống dưới đáy màn hình
        boolean shouldHide = blockBounds.getMinY() > scrollBounds.getMaxY();

        // 1. Chạy hiệu ứng HIỆN (Fade In + Slide Up)
        if (shouldShow && !isRevealed) {
            isRevealed = true;

            FadeTransition fade = new FadeTransition(Duration.millis(600), revealContent);
            fade.setToValue(1.0); // Rõ hoàn toàn

            TranslateTransition slide = new TranslateTransition(Duration.millis(600), revealContent);
            slide.setToY(0); // Trượt về vị trí gốc

            ParallelTransition pt = new ParallelTransition(fade, slide);
            pt.play();
        }
        // 2. Chạy hiệu ứng ẨN (Fade Out + Slide Down)
        else if (shouldHide && isRevealed) {
            isRevealed = false; // Reset trạng thái để sẵn sàng hiện lại

            FadeTransition fade = new FadeTransition(Duration.millis(400), revealContent);
            fade.setToValue(0.0); // Mờ đi (tàng hình)

            TranslateTransition slide = new TranslateTransition(Duration.millis(400), revealContent);
            slide.setToY(50); // Trượt tụt xuống lại 50px

            ParallelTransition pt = new ParallelTransition(fade, slide);
            pt.play();
        }
    }

    // --- CÁC PHƯƠNG THỨC ĐIỀU HƯỚNG CỦA BẠN ---

    /** Chuyển sang màn đăng nhập khi người dùng nhấn nút bắt đầu. */
    @FXML
    private void handleStart() {
        Navigator.getInstance().goToLogin();
    }

    /** Chuyển sang màn đăng nhập từ header. */
    @FXML
    private void handleGoToLogin() {
        Navigator.getInstance().goToLogin();
    }

    /** Chuyển sang màn đăng ký từ header. */
    @FXML
    private void handleGoToRegister() {
        Navigator.getInstance().goToRegister();
    }
}
