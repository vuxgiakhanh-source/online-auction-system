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
        if (revealBlock != null) {
            // Đồng bộ chiều rộng của video luôn khít với chiều rộng thực tế của khối (Đảm bảo Responsive)
            if (bgMediaView != null) {
                bgMediaView.fitWidthProperty().bind(revealBlock.widthProperty());
                bgMediaView.fitHeightProperty().bind(revealBlock.heightProperty());
            }
        }
        if (revealContent != null) {
            revealContent.setTranslateY(50);
        }

        // 2. Khởi tạo cấu hình và phát Video nền tắt tiếng chạy vòng lặp
        initBackgroundVideo();

        // 3. Lắng nghe sự kiện người dùng cuộn thanh cuộn dọc (vvalue)
        if (mainScrollPane != null) {
            mainScrollPane.vvalueProperty().addListener((observable, oldValue, newValue) -> {
                checkAndRevealBlock();
            });
            Platform.runLater(this::checkAndRevealBlock);
        }
    }

    /**
     * Khởi tạo và thiết lập video nền lặp vô hạn, tắt âm thanh.
     */
    private void initBackgroundVideo() {
        try {
            // ĐƯỜNG DẪN: Đảm bảo đường dẫn này khớp chính xác với thư mục file video trong thư mục resources của bạn
            URL videoUrl = getClass().getResource("/com/group13/auction/assets/videos/bg-landing.mp4");

            if (videoUrl != null) {
                Media media = new Media(videoUrl.toExternalForm());
                mediaPlayer = new MediaPlayer(media);

                mediaPlayer.setMute(true); // Tắt tiếng hoàn toàn
                mediaPlayer.setAutoPlay(true);
                mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE); // Thiết lập vòng lặp vô tận (Loop)
                media.setOnError(() -> System.err.println("Lỗi media video nền: " + media.getError()));
                mediaPlayer.setOnError(() ->
                        System.err.println("Lỗi player video nền: " + mediaPlayer.getError()));
                mediaPlayer.statusProperty().addListener((observable, oldStatus, newStatus) ->
                        System.out.println("Trạng thái video nền: " + newStatus));
                mediaPlayer.setOnReady(() -> {
                    mediaPlayer.seek(Duration.ZERO);
                    mediaPlayer.play();
                });

                bgMediaView.setMediaPlayer(mediaPlayer);
                Platform.runLater(mediaPlayer::play);
            } else {
                System.err.println("Lỗi: Không tìm thấy file video. Vui lòng kiểm tra lại đường dẫn getResource!");
            }
        } catch (Exception e) {
            System.err.println("Không thể phát video nền: " + e.getMessage());
            e.printStackTrace();
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
