package com.group13.auction.core.navigation;

import com.group13.auction.config.ResourcePath;
import com.group13.auction.util.ResourceUtil;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Quản lý scene gốc và load FXML (full scene hoặc nhúng vào shell).
 */
public final class SceneManager {

    private final Stage primaryStage;

    public SceneManager(Stage primaryStage) {
        this.primaryStage = Objects.requireNonNull(primaryStage, "primaryStage");
    }

    public void switchTo(Route route) {
        switchTo(route.getFxmlPath());
    }

    public void switchTo(String fxmlPath) {
        LoadedView loaded = loadView(fxmlPath);
        applyRoot(loaded.root());
    }

    public LoadedView loadView(String fxmlPath) {
        URL resource = ResourceUtil.requireResource(fxmlPath);
        FXMLLoader loader = new FXMLLoader(resource);
        try {
            Parent root = loader.load();
            return new LoadedView(root, loader.getController());
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể load FXML: " + fxmlPath, exception);
        }
    }

    public void applyRoot(Parent root) {
        Scene scene = primaryStage.getScene();
        if (scene == null) {
            scene = new Scene(root);
            addStylesheet(scene, ResourcePath.APP_CSS);
            primaryStage.setScene(scene);
        } else {
            scene.setRoot(root);
            addStylesheet(scene, ResourcePath.APP_CSS);
        }
    }

    private void addStylesheet(Scene scene, String stylesheetPath) {
        if (!ResourceUtil.exists(stylesheetPath)) {
            return;
        }
        String stylesheet = ResourceUtil.toExternalForm(stylesheetPath);
        if (!scene.getStylesheets().contains(stylesheet)) {
            scene.getStylesheets().add(stylesheet);
        }
    }
}
