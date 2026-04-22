package com.group13.auction.core.navigation;

import java.io.IOException;
import java.net.URL;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Quản lý việc load FXML và chuyển Scene cho ứng dụng JavaFX.
 */
public final class SceneManager {

    private final Stage primaryStage;

    /**
     * Khởi tạo scene manager với stage chính của ứng dụng.
     *
     * @param primaryStage stage chính
     */
    public SceneManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    /**
     * Chuyển đến màn hình có đường dẫn FXML tương ứng.
     *
     * @param fxmlPath đường dẫn tuyệt đối trong resources
     */
    public void switchTo(String fxmlPath) {
        try {
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                throw new IllegalArgumentException("Không tìm thấy FXML: " + fxmlPath);
            }

            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();

            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không thể load màn hình từ FXML: " + fxmlPath, exception);
        }
    }
}