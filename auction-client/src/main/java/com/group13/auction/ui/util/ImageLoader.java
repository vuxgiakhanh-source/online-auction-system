package com.group13.auction.ui.util;

import com.group13.auction.config.ImageConfig;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import java.util.List;
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

            ImageView imageView = new ImageView();
            imageView.getStyleClass().add("product-gallery-image");
            imageView.setFitWidth(150);
            imageView.setFitHeight(110);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            load(imageView, path);
            pane.getChildren().add(imageView);
        }
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