package com.group13.auction.core.navigation;

import com.group13.auction.config.ResourcePath;
import com.group13.auction.config.UiConstants;
import com.group13.auction.ui.util.UiSoundInstaller;
import com.group13.auction.util.ResourceUtil;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Quản lý việc load FXML và chuyển scene cho ứng dụng JavaFX. */
public final class SceneManager {

  private final Stage primaryStage;

  /**
   * Khởi tạo scene manager với stage chính của ứng dụng.
   *
   * @param primaryStage stage chính
   */
  public SceneManager(Stage primaryStage) {
    this.primaryStage = Objects.requireNonNull(primaryStage, "primaryStage must not be null");
  }

  /**
   * Chuyển đến màn hình tương ứng với route.
   *
   * @param route route cần mở
   */
  public void switchTo(Route route) {
    Objects.requireNonNull(route, "route must not be null");
    switchTo(route.getFxmlPath());
  }

  /**
   * Chuyển đến màn hình có đường dẫn FXML tương ứng.
   *
   * @param fxmlPath đường dẫn tuyệt đối trong resources
   */
  public void switchTo(String fxmlPath) {
    Parent root = loadView(Objects.requireNonNull(fxmlPath, "fxmlPath must not be null"));
    Scene scene = primaryStage.getScene();

    if (scene == null) {
      scene = new Scene(root, UiConstants.DEFAULT_WIDTH, UiConstants.DEFAULT_HEIGHT);
      primaryStage.setScene(scene);
    } else {
      scene.setRoot(root);
    }

    addStylesheet(scene, ResourcePath.APP_CSS);
    UiSoundInstaller.installButtonClickSound(scene);
  }

  private Parent loadView(String fxmlPath) {
    URL resource = ResourceUtil.requireResource(fxmlPath);
    FXMLLoader loader = new FXMLLoader(resource);

    try {
      return loader.load();
    } catch (IOException exception) {
      throw new IllegalStateException("Không thể load màn hình từ FXML: " + fxmlPath, exception);
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
