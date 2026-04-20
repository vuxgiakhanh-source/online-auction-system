package com.group13.auction.core.navigation;

import com.group13.auction.config.UiConstants;
import com.group13.auction.util.ResourceUtil;
import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Loads FXML views and installs them on the primary stage.
 */
public final class SceneManager {

    private final Stage primaryStage;

    /**
     * Creates a scene manager bound to the given stage.
     *
     * @param primaryStage primary application stage
     */
    public SceneManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    /**
     * Replaces the current scene content with the provided FXML view.
     *
     * @param fxmlPath classpath path to the FXML file
     */
    public void setRoot(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(ResourceUtil.requireResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = primaryStage.getScene();

            if (scene == null) {
                scene = new Scene(root, UiConstants.DEFAULT_WIDTH, UiConstants.DEFAULT_HEIGHT);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load view: " + fxmlPath, exception);
        }
    }
}
