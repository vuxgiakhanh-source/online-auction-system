package com.group13.auction.core.navigation;

import com.group13.auction.config.ResourcePath;
import com.group13.auction.config.UiConstants;
import com.group13.auction.ui.util.UiSoundInstaller;
import com.group13.auction.util.ResourceUtil;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/** Quản lý việc load FXML và chuyển scene cho ứng dụng JavaFX. */
public final class SceneManager {

  private final Stage primaryStage;
  private Pane scaledViewport;
  private Group scaledContent;
  private DoubleBinding contentScale;

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
    prepareScaledContent(root);
    Scene scene = primaryStage.getScene();

    if (scene == null) {
      scaledViewport = createScaledViewport();
      setScaledContent(root);
      scene = new Scene(scaledViewport, UiConstants.DEFAULT_WIDTH, UiConstants.DEFAULT_HEIGHT);
      bindScaledViewport(scene);
      primaryStage.setScene(scene);
    } else {
      ensureScaledViewport(scene);
      setScaledContent(root);
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

  private Pane createScaledViewport() {
    Pane viewport = new Pane();
    viewport.getStyleClass().add("screen-root");
    viewport.setMinSize(0.0, 0.0);
    viewport.setPrefSize(UiConstants.BASE_WIDTH, UiConstants.BASE_HEIGHT);

    scaledContent = new Group();
    scaledContent.setManaged(false);
    viewport.getChildren().add(scaledContent);
    return viewport;
  }

  private void ensureScaledViewport(Scene scene) {
    if (scaledViewport != null && scaledContent != null) {
      return;
    }

    scaledViewport = createScaledViewport();
    scene.setRoot(scaledViewport);
    bindScaledViewport(scene);
  }

  private void setScaledContent(Parent root) {
    scaledContent.getChildren().setAll(root);
  }

  private void prepareScaledContent(Parent root) {
    if (root instanceof Region region) {
      region.setMinSize(UiConstants.BASE_WIDTH, UiConstants.BASE_HEIGHT);
      region.setPrefSize(UiConstants.BASE_WIDTH, UiConstants.BASE_HEIGHT);
      region.setMaxSize(UiConstants.BASE_WIDTH, UiConstants.BASE_HEIGHT);
    }
    root.resize(UiConstants.BASE_WIDTH, UiConstants.BASE_HEIGHT);
  }

  private void bindScaledViewport(Scene scene) {
    if (contentScale != null) {
      contentScale.dispose();
    }

    contentScale =
        Bindings.createDoubleBinding(
            () -> {
              double width = Math.max(scene.getWidth(), 1.0);
              double height = Math.max(scene.getHeight(), 1.0);
              return Math.min(width / UiConstants.BASE_WIDTH, height / UiConstants.BASE_HEIGHT);
            },
            scene.widthProperty(),
            scene.heightProperty());

    scaledContent.scaleXProperty().bind(contentScale);
    scaledContent.scaleYProperty().bind(contentScale);
    scaledContent
        .layoutXProperty()
        .bind(
            Bindings.createDoubleBinding(
                () -> (scene.getWidth() - UiConstants.BASE_WIDTH * contentScale.get()) / 2.0,
                scene.widthProperty(),
                contentScale));
    scaledContent
        .layoutYProperty()
        .bind(
            Bindings.createDoubleBinding(
                () -> (scene.getHeight() - UiConstants.BASE_HEIGHT * contentScale.get()) / 2.0,
                scene.heightProperty(),
                contentScale));
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
