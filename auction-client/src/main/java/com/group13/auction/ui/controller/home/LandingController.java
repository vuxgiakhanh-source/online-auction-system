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
    private MediaPlayer backgroundPlayer;
    private boolean videoInitialized = false;
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

        // 2. Chỉ khởi tạo video khi node đã gắn vào scene
        if (revealBlock != null) {
            revealBlock.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    initBackgroundVideo();
                } else {
                    cleanupVideo();
                }
            });
        }

        // 3. Lắng nghe cuộn trang
        if (mainScrollPane != null) {
            mainScrollPane.vvalueProperty().addListener((obs, oldV, newV) -> checkAndRevealBlock());
            Platform.runLater(this::checkAndRevealBlock);
        }

        slideBlocks.addAll(List.of(slideBlockO, slideBlockM, slideBlockN, slideBlockI));
        for (AnchorPane slideBlock : slideBlocks) {
            if (slideBlock != null) {
                slideBlock.setTranslateX(-100);
            }
        }
    }

    private synchronized void cleanupVideo() {
        if (backgroundPlayer != null) {
            try {
                backgroundPlayer.stop();
                backgroundPlayer.dispose();
            } catch (Exception e) {
                // Ignore
            } finally {
                backgroundPlayer = null;
                videoInitialized = false;
            }
        }
    }

    private synchronized void initBackgroundVideo() {
        if (videoInitialized || bgMediaView == null || revealBlock == null) return;

        URL videoUrl = getClass().getResource("/com/group13/auction/assets/videos/bg-landing.mp4");
        if (videoUrl == null) {
            System.err.println("Không tìm thấy video nền: /com/group13/auction/assets/videos/bg-landing.mp4");
            return;
        }

        try {
            bgMediaView.setPreserveRatio(true);
            bgMediaView.setManaged(true);
            bgMediaView.fitWidthProperty().unbind();
            bgMediaView.fitWidthProperty().bind(revealBlock.widthProperty());

            Media media = new Media(videoUrl.toExternalForm());
            backgroundPlayer = new MediaPlayer(media);
            backgroundPlayer.setMute(true);
            backgroundPlayer.setCycleCount(MediaPlayer.INDEFINITE);

            backgroundPlayer.setOnReady(() -> {
                MediaPlayer player = backgroundPlayer;
                if (player != null) {
                    player.play();
                }
            });

            backgroundPlayer.setOnError(() -> {
                MediaPlayer player = backgroundPlayer;
                if (player != null && player.getError() != null) {
                    System.err.println("Lỗi MediaPlayer: " + player.getError().getMessage());
                }
            });

            media.setOnError(() -> {
                if (media.getError() != null) {
                    System.err.println("Lỗi Media: " + media.getError().getMessage());
                }
            });

            bgMediaView.setMediaPlayer(backgroundPlayer);
            bgMediaView.setVisible(true);

            Platform.runLater(() -> {
                MediaPlayer player = backgroundPlayer;
                if (player != null) {
                    player.play();
                }
            });

            videoInitialized = true;

        } catch (Exception e) {
            System.err.println("Lỗi khởi tạo video: " + e.getMessage());
            cleanupVideo();
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
        if (blockBounds.getHeight() <= 0) return;
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