package com.group13.auction.ui.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Dialog dùng để xem một nội dung JavaFX ở kích thước lớn. */
public final class ContentPreviewDialog {

  private static final double PREVIEW_WIDTH = 1040;
  private static final double PREVIEW_HEIGHT = 700;

  private ContentPreviewDialog() {
    // Utility class.
  }

  /**
   * Mở nội dung ở dạng preview modal.
   *
   * @param ownerNode node đang phát sinh thao tác mở preview
   * @param title tiêu đề hiển thị trên dialog
   * @param content nội dung JavaFX cần xem lớn
   */
  public static void show(Node ownerNode, String title, Node content) {
    if (content == null) {
      return;
    }

    Stage stage = new Stage();
    stage.setTitle(title == null || title.isBlank() ? "Preview" : title);
    stage.initModality(Modality.APPLICATION_MODAL);

    Window owner = resolveOwner(ownerNode);
    if (owner != null) {
      stage.initOwner(owner);
    }

    Label titleLabel = new Label(stage.getTitle());
    titleLabel.setStyle(
        "-fx-font-size: 22px;"
            + "-fx-font-weight: 900;"
            + "-fx-text-fill: #ffffff;");

    Button closeButton = new Button("Đóng");
    closeButton.setDefaultButton(true);
    closeButton.setCancelButton(true);
    closeButton.setOnAction(event -> stage.close());
    closeButton.setStyle(
        "-fx-background-color: #ffffff;"
            + "-fx-background-radius: 18;"
            + "-fx-text-fill: #0f253c;"
            + "-fx-font-weight: 800;"
            + "-fx-padding: 8 22;");

    HBox header = new HBox(16, titleLabel, spacer(), closeButton);
    header.setAlignment(Pos.CENTER_LEFT);

    StackPane contentFrame = new StackPane(content);
    contentFrame.setAlignment(Pos.CENTER);
    contentFrame.setPadding(new Insets(14));
    contentFrame.setStyle(
        "-fx-background-color: rgba(255, 255, 255, 0.07);"
            + "-fx-background-radius: 24;"
            + "-fx-border-color: rgba(255, 255, 255, 0.13);"
            + "-fx-border-radius: 24;"
            + "-fx-border-width: 1;");
    VBox.setVgrow(contentFrame, Priority.ALWAYS);

    VBox root = new VBox(14, header, contentFrame);
    root.setPadding(new Insets(18));
    root.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
    root.setStyle(
        "-fx-background-color: #0f253c;"
            + "-fx-background-radius: 28;"
            + "-fx-border-color: rgba(255, 255, 255, 0.12);"
            + "-fx-border-radius: 28;"
            + "-fx-border-width: 1;");

    Scene scene = new Scene(root, PREVIEW_WIDTH, PREVIEW_HEIGHT);
    copyOwnerStylesheets(ownerNode, scene);
    scene.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ESCAPE) {
            stage.close();
            event.consume();
          }
        });

    stage.setScene(scene);
    stage.setMinWidth(900);
    stage.setMinHeight(620);
    stage.showAndWait();
  }

  private static RegionSpacer spacer() {
    RegionSpacer spacer = new RegionSpacer();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    return spacer;
  }

  private static void copyOwnerStylesheets(Node ownerNode, Scene previewScene) {
    if (ownerNode == null || ownerNode.getScene() == null) {
      return;
    }

    copyStylesheets(ownerNode.getScene().getStylesheets(), previewScene);

    Node current = ownerNode;
    while (current != null) {
      if (current instanceof Parent parent) {
        copyStylesheets(parent.getStylesheets(), previewScene);
      }
      current = current.getParent();
    }
  }

  private static void copyStylesheets(
      Iterable<String> stylesheets, Scene previewScene) {
    for (String stylesheet : stylesheets) {
      if (!previewScene.getStylesheets().contains(stylesheet)) {
        previewScene.getStylesheets().add(stylesheet);
      }
    }
  }

  private static Window resolveOwner(Node ownerNode) {
    if (ownerNode == null || ownerNode.getScene() == null) {
      return null;
    }
    return ownerNode.getScene().getWindow();
  }

  private static final class RegionSpacer extends javafx.scene.layout.Region {
    private RegionSpacer() {
      // Region spacer.
    }
  }
}