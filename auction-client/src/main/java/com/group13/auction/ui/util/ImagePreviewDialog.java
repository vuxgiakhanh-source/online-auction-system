package com.group13.auction.ui.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Dialog hiển thị ảnh sản phẩm ở kích thước lớn. */
public final class ImagePreviewDialog {

  private static final double PREVIEW_WIDTH = 900;
  private static final double PREVIEW_HEIGHT = 620;

  private ImagePreviewDialog() {
    // Utility class.
  }

  /**
   * Mở ảnh sản phẩm ở chế độ xem chi tiết.
   *
   * @param ownerNode node đang phát sinh thao tác mở preview
   * @param serverPath path ảnh dạng server trả về hoặc URL đầy đủ
   */
  public static void show(Node ownerNode, String serverPath) {
    if (serverPath == null || serverPath.isBlank()) {
      return;
    }

    Stage stage = new Stage();
    stage.setTitle("Xem ảnh sản phẩm");
    stage.initModality(Modality.APPLICATION_MODAL);

    Window owner = resolveOwner(ownerNode);
    if (owner != null) {
      stage.initOwner(owner);
    }

    ImageView previewImage = new ImageView();
    previewImage.setFitWidth(PREVIEW_WIDTH);
    previewImage.setFitHeight(PREVIEW_HEIGHT);
    previewImage.setPreserveRatio(true);
    previewImage.setSmooth(true);
    ImageLoader.load(previewImage, serverPath);

    StackPane imageFrame = new StackPane(previewImage);
    imageFrame.setAlignment(Pos.CENTER);
    imageFrame.setMinSize(760, 420);
    imageFrame.setStyle(
        "-fx-padding: 16;"
            + "-fx-background-color: rgba(15, 37, 60, 0.96);"
            + "-fx-background-radius: 24;"
            + "-fx-border-color: rgba(255, 255, 255, 0.16);"
            + "-fx-border-radius: 24;"
            + "-fx-border-width: 1;");

    ScrollPane scrollPane = new ScrollPane(imageFrame);
    scrollPane.setFitToWidth(true);
    scrollPane.setFitToHeight(true);
    scrollPane.setPannable(true);
    scrollPane.setStyle(
        "-fx-background: transparent;"
            + "-fx-background-color: transparent;"
            + "-fx-padding: 0;");
    VBox.setVgrow(scrollPane, Priority.ALWAYS);

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

    HBox actionBar = new HBox(closeButton);
    actionBar.setAlignment(Pos.CENTER_RIGHT);

    VBox content = new VBox(14, scrollPane, actionBar);
    content.setPadding(new Insets(18));
    content.setPrefSize(980, 720);
    content.setStyle(
        "-fx-background-color: #0f253c;"
            + "-fx-background-radius: 28;"
            + "-fx-border-color: rgba(255, 255, 255, 0.12);"
            + "-fx-border-radius: 28;"
            + "-fx-border-width: 1;");

    Scene scene = new Scene(content);
    scene.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ESCAPE) {
            stage.close();
            event.consume();
          }
        });

    stage.setScene(scene);
    stage.setMinWidth(860);
    stage.setMinHeight(600);
    stage.showAndWait();
  }

  private static Window resolveOwner(Node ownerNode) {
    if (ownerNode == null || ownerNode.getScene() == null) {
      return null;
    }
    return ownerNode.getScene().getWindow();
  }
}