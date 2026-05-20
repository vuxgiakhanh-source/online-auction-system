package com.group13.auction.ui.controller.home;

import com.group13.auction.core.navigation.Navigator;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/** Controller cho màn landing/welcome của OmniBid client. */
public class LandingController implements Initializable {

    @FXML private ScrollPane mainScrollPane;
    @FXML private StackPane revealBlock;
    @FXML private MediaView bgMediaView;
    @FXML private VBox revealContent;
    @FXML private AnchorPane slideBlockO;
    @FXML private AnchorPane slideBlockM;
    @FXML private AnchorPane slideBlockN;
    @FXML private AnchorPane slideBlockI;
    
    // Đã thêm biến này để điều khiển màn đen
    @FXML private Pane gradientOverlay; 

    // Quản lý MediaPlayer
    private static MediaPlayer globalPlayer;
    private boolean isRevealed = false;
    private final List<AnchorPane> slideBlocks = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. Reset nội dung chữ và lớp phủ
        if (revealContent != null) {
            revealContent.setTranslateY(80);
            revealContent.setOpacity(0.0);
        }
        if (gradientOverlay != null) {
            gradientOverlay.setOpacity(0.0);
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

        slideBlocks.addAll(List.of(slideBlockO, slideBlockM, slideBlockN, slideBlockI));
        for (AnchorPane slideBlock : slideBlocks) {
            if (slideBlock != null) {
                slideBlock.setTranslateX(-100);
            }
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

        bgMediaView.setPreserveRatio(true);
        bgMediaView.fitWidthProperty().bind(revealBlock.widthProperty());

        try {
            URL videoUrl = getClass().getResource("/com/group13/auction/assets/videos/bg-landing.mp4");
            if (videoUrl == null) return;

            Media media = new Media(videoUrl.toExternalForm());
            globalPlayer = new MediaPlayer(media);
            globalPlayer.setMute(true);
            globalPlayer.setCycleCount(MediaPlayer.INDEFINITE);

            bgMediaView.setMediaPlayer(globalPlayer);
            
            globalPlayer.setOnReady(() -> {
                if (globalPlayer != null) globalPlayer.play();
            });

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

    private void handleSlideBlockReveal() {
        if (mainScrollPane == null) return;

        Bounds scrollBounds = mainScrollPane.localToScene(mainScrollPane.getBoundsInLocal());
        if (scrollBounds == null) return;

        for (AnchorPane slideBlock : slideBlocks) {
            if (slideBlock == null) continue;

            Bounds blockBounds = slideBlock.localToScene(slideBlock.getBoundsInLocal());
            if (blockBounds == null) continue;

            boolean shouldShow = blockBounds.getMinY() < scrollBounds.getMaxY() - 100;
            boolean shouldHide = blockBounds.getMinY() > scrollBounds.getMaxY();
            boolean isShown = slideBlock.getOpacity() > 0.5;

            if (shouldShow && !isShown) {
                FadeTransition fade = new FadeTransition(Duration.millis(800), slideBlock);
                fade.setToValue(1.0);

                TranslateTransition slide = new TranslateTransition(Duration.millis(800), slideBlock);
                slide.setToX(0);

                new ParallelTransition(fade, slide).play();
            } else if (shouldHide && isShown) {
                FadeTransition fade = new FadeTransition(Duration.millis(500), slideBlock);
                fade.setToValue(0.0);

                TranslateTransition slide = new TranslateTransition(Duration.millis(500), slideBlock);
                slide.setToX(-100);

                new ParallelTransition(fade, slide).play();
            }
        }
    }

    private void checkAndRevealBlock() {
        if (revealBlock == null || mainScrollPane == null) return;
        Bounds scrollBounds = mainScrollPane.localToScene(mainScrollPane.getBoundsInLocal());
        Bounds blockBounds = revealBlock.localToScene(revealBlock.getBoundsInLocal());
        if (scrollBounds == null || blockBounds == null) return;

        // Tính toán chiều cao thực tế của khối video
        double blockHeight = blockBounds.getHeight();

        // ĐIỀU KIỆN HIỆN: Mép dưới màn hình phải vượt qua đúng 50% chiều cao của khối video
        boolean shouldShow = scrollBounds.getMaxY() > blockBounds.getMinY() + (blockHeight * 0.5);
        
        // ĐIỀU KIỆN ẨN: Khi cuộn ngược lên, màn hình chỉ còn chạm vào 20% chiều cao của khối
        boolean shouldHide = scrollBounds.getMaxY() < blockBounds.getMinY() + (blockHeight * 0.2);

        if (shouldShow && !isRevealed) {
            isRevealed = true;
            
            FadeTransition fadeText = new FadeTransition(Duration.millis(1000), revealContent);
            fadeText.setToValue(1.0);
            
            TranslateTransition slideText = new TranslateTransition(Duration.millis(1000), revealContent);
            slideText.setToY(0); 

            fadeText.setDelay(Duration.millis(300));
            slideText.setDelay(Duration.millis(300));

            if (gradientOverlay != null) {
                FadeTransition fadeOverlay = new FadeTransition(Duration.millis(1200), gradientOverlay);
                fadeOverlay.setToValue(1.0);
                new ParallelTransition(fadeText, slideText, fadeOverlay).play();
            } else {
                new ParallelTransition(fadeText, slideText).play();
            }
            
        } else if (shouldHide && isRevealed) {
            isRevealed = false;
            
            FadeTransition fadeText = new FadeTransition(Duration.millis(600), revealContent);
            fadeText.setToValue(0.0);
            
            TranslateTransition slideText = new TranslateTransition(Duration.millis(600), revealContent);
            slideText.setToY(80); 

            if (gradientOverlay != null) {
                FadeTransition fadeOverlay = new FadeTransition(Duration.millis(600), gradientOverlay);
                fadeOverlay.setToValue(0.0);
                new ParallelTransition(fadeText, slideText, fadeOverlay).play();
            } else {
                new ParallelTransition(fadeText, slideText).play();
            }
        }

        handleSlideBlockReveal();
    }

    @FXML private void handleStart() { Navigator.getInstance().goToLogin(); }
    @FXML private void handleGoToLogin() { Navigator.getInstance().goToLogin(); }
    @FXML private void handleGoToRegister() { Navigator.getInstance().goToRegister(); }
}