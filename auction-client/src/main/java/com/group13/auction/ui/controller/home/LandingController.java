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

    @FXML private ScrollPane mainScrollPane;
    @FXML private StackPane revealBlock;
    @FXML private MediaView bgMediaView;
    @FXML private VBox revealContent;

    // Quản lý MediaPlayer
    private static MediaPlayer globalPlayer;
    private boolean isRevealed = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. Reset nội dung chữ
        if (revealContent != null) {
            revealContent.setTranslateY(50);
            revealContent.setOpacity(0.0);
        }

        // 2. Cleanup player cũ nếu có
        cleanupVideo();

        // 3. Khởi tạo video với độ trễ ngắn để ổn định giao diện
        Platform.runLater(() -> {
            try {
                initBackgroundVideo();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // 4. Lắng nghe cuộn trang
        if (mainScrollPane != null) {
            mainScrollPane.vvalueProperty().addListener((obs, oldV, newV) -> checkAndRevealBlock());
            Platform.runLater(this::checkAndRevealBlock);
        }

        // 5. Giải phóng video khi chuyển scene
        if (revealBlock != null) {
            revealBlock.sceneProperty().addListener((obs, oldS, newS) -> {
                if (newS == null) cleanupVideo();
            });
        }
    }

    private synchronized void cleanupVideo() {
        if (globalPlayer != null) {
            try {
                globalPlayer.stop();
                globalPlayer.dispose();
            } catch (Exception e) {
                // Ignore
            } finally {
                globalPlayer = null;
            }
        }
    }

    private void initBackgroundVideo() {
        if (bgMediaView == null || revealBlock == null) return;

        // Cấu hình MediaView: Chỉ bind Width, Height tự tính theo tỉ lệ
        bgMediaView.setPreserveRatio(true);
        bgMediaView.fitWidthProperty().bind(revealBlock.widthProperty());

        try {
            URL videoUrl = getClass().getResource("/com/group13/auction/assets/videos/bg-landing.mp4");
            if (videoUrl == null) return;

            Media media = new Media(videoUrl.toExternalForm());
            globalPlayer = new MediaPlayer(media);
            globalPlayer.setMute(true);
            globalPlayer.setCycleCount(MediaPlayer.INDEFINITE);

            // Gán player và phát
            bgMediaView.setMediaPlayer(globalPlayer);
            
            globalPlayer.setOnReady(() -> {
                if (globalPlayer != null) globalPlayer.play();
            });

            // Vòng lặp thủ công để đảm bảo 100% không dừng
            globalPlayer.setOnEndOfMedia(() -> {
                if (globalPlayer != null) {
                    globalPlayer.seek(Duration.ZERO);
                    globalPlayer.play();
                }
            });

        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo video: " + e.getMessage());
        }
    }

    private void checkAndRevealBlock() {
        if (revealBlock == null || mainScrollPane == null) return;
        Bounds scrollBounds = mainScrollPane.localToScene(mainScrollPane.getBoundsInLocal());
        Bounds blockBounds = revealBlock.localToScene(revealBlock.getBoundsInLocal());
        if (scrollBounds == null || blockBounds == null) return;

        boolean shouldShow = blockBounds.getMinY() < scrollBounds.getMaxY() - 100;
        boolean shouldHide = blockBounds.getMinY() > scrollBounds.getMaxY();

        if (shouldShow && !isRevealed) {
            isRevealed = true;
            FadeTransition fade = new FadeTransition(Duration.millis(600), revealContent);
            fade.setToValue(1.0);
            TranslateTransition slide = new TranslateTransition(Duration.millis(600), revealContent);
            slide.setToY(0);
            new ParallelTransition(fade, slide).play();
        } else if (shouldHide && isRevealed) {
            isRevealed = false;
            FadeTransition fade = new FadeTransition(Duration.millis(400), revealContent);
            fade.setToValue(0.0);
            TranslateTransition slide = new TranslateTransition(Duration.millis(400), revealContent);
            slide.setToY(50);
            new ParallelTransition(fade, slide).play();
        }
    }

    @FXML private void handleStart() { Navigator.getInstance().goToLogin(); }
    @FXML private void handleGoToLogin() { Navigator.getInstance().goToLogin(); }
    @FXML private void handleGoToRegister() { Navigator.getInstance().goToRegister(); }
}
