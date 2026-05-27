package com.group13.auction.ui.util;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.config.ImageConfig;
import java.util.List;
import javafx.scene.Cursor;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;

/** Tải ảnh sản phẩm từ ImageUploadServer. */
public final class ImageLoader {

  private ImageLoader() {}

  /**
   * Load ảnh từ server path vào ImageView.
   *
   * <p>Method này không tự set kích thước ImageView để caller có thể dùng chung cho thumbnail,
   * gallery hoặc preview lớn.
   *
   * @param view ImageView cần hiển thị ảnh
   * @param serverPath path ảnh dạng {@code /uploads/items/{uuid}.jpg} hoặc URL đầy đủ
   */
  public static void load(ImageView view, String serverPath) {
    if (view == null || serverPath == null || serverPath.isBlank()) {
      return;
    }

    String url = ImageConfig.toFullUrl(serverPath);
    view.setImage(new Image(url, true));
    view.setPreserveRatio(true);
    view.setSmooth(true);
  }

  /**
   * Đổ danh sách ảnh sản phẩm vào gallery.
   *
   * @param pane FlowPane dùng làm gallery
   * @param imageUrls danh sách path ảnh server trả về
   */
  public static void fillGallery(FlowPane pane, List<String> imageUrls) {
    fillGallery(pane, imageUrls, false);
  }

  /**
   * Đổ danh sách ảnh sản phẩm vào gallery và cho phép bấm ảnh để xem kích thước lớn.
   *
   * @param pane FlowPane dùng làm gallery
   * @param imageUrls danh sách path ảnh server trả về
   */
  public static void fillPreviewableGallery(FlowPane pane, List<String> imageUrls) {
    fillGallery(pane, imageUrls, true);
  }

  private static void fillGallery(FlowPane pane, List<String> imageUrls, boolean previewEnabled) {
    if (pane == null) {
      return;
    }

    pane.getChildren().clear();

    if (imageUrls == null || imageUrls.isEmpty()) {
      return;
    }

    pane.setHgap(10);
    pane.setVgap(10);

    for (String path : imageUrls) {
      if (path == null || path.isBlank()) {
        continue;
      }

      ImageView imageView = createGalleryImageView(path, previewEnabled);
      pane.getChildren().add(imageView);
    }
  }

  private static ImageView createGalleryImageView(String path, boolean previewEnabled) {
    ImageView imageView = new ImageView();
    imageView.getStyleClass().add("product-gallery-image");
    imageView.setFitWidth(150);
    imageView.setFitHeight(110);
    imageView.setPreserveRatio(true);
    imageView.setSmooth(true);

    load(imageView, path);

    if (previewEnabled) {
      imageView.getStyleClass().add("clickable-gallery-image");
      imageView.setCursor(Cursor.HAND);
      Tooltip.install(imageView, new Tooltip("Nhấn để xem ảnh lớn"));
      imageView.setOnMouseClicked(event -> ImagePreviewDialog.show(imageView, path));
    }

    return imageView;
  }

  /**
   * Đổ ảnh từ ItemDTO vào gallery.
   *
   * @param pane FlowPane dùng làm gallery
   * @param item item DTO chứa imageUrls
   */
  public static void fillGalleryFromItem(FlowPane pane, AuctionDTOs.ItemDTO item) {
    if (item != null && item.hasImages()) {
      fillGallery(pane, item.getImageUrls());
      return;
    }

    if (pane != null) {
      pane.getChildren().clear();
    }
  }
}
