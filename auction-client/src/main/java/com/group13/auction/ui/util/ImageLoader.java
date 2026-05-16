package com.group13.auction.ui.util;

import com.group13.auction.config.ImageConfig;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import java.util.List;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;

/** Tải ảnh sản phẩm từ ImageUploadServer (HTTP 8081). */
public final class ImageLoader {

    private ImageLoader() {}

    public static void load(ImageView view, String serverPath) {
        if (view == null || serverPath == null || serverPath.isBlank()) {
            return;
        }
        String url = ImageConfig.toFullUrl(serverPath);
        view.setImage(new Image(url, true));
        view.setFitWidth(280);
        view.setPreserveRatio(true);
    }

    public static void fillGallery(FlowPane pane, List<String> imageUrls) {
        if (pane == null) {
            return;
        }
        pane.getChildren().clear();
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        pane.setHgap(8);
        pane.setVgap(8);
        for (String path : imageUrls) {
            ImageView iv = new ImageView();
            iv.setFitWidth(120);
            iv.setFitHeight(120);
            iv.setPreserveRatio(true);
            load(iv, path);
            pane.getChildren().add(iv);
        }
    }

    public static void fillGalleryFromItem(FlowPane pane, AuctionDTOs.ItemDTO item) {
        if (item != null && item.hasImages()) {
            fillGallery(pane, item.getImageUrls());
        }
    }
}
