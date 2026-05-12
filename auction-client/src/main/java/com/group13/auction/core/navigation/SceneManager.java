package com.group13.auction.core.navigation;

import java.io.IOException;
import java.net.URL;

import com.group13.auction.config.ResourcePath;
import com.group13.auction.util.ResourceUtil;
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
     * Khởi tạo SceneManager với dữ liệu cần thiết cho module client.
     */
    public SceneManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    /**
     * Thực thi thao tác load của thành phần client.
     */
    public Parent load(String fxmlPath) {
        try {
            URL resource = ResourceUtil.requireResource(fxmlPath);
            FXMLLoader loader = new FXMLLoader(resource);
            return loader.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể load FXML: " + fxmlPath, exception);
        }
    }

    /**
     * Thực thi thao tác switchTo của thành phần client.
     */
    public void switchTo(String fxmlPath) {
        Parent root = load(fxmlPath);
        Scene scene = primaryStage.getScene();
        if (scene == null) {
            scene = new Scene(root);
            scene.getStylesheets()
                    .add(ResourceUtil.requireResource(ResourcePath.APP_CSS).toExternalForm());
            primaryStage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
    }
}
