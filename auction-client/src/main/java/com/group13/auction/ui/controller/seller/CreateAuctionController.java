package com.group13.auction.ui.controller.seller;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.network.http.ImageUploadService;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

public final class CreateAuctionController extends BaseController implements PageLifecycle {

    @FXML private TextField nameField;
    @FXML private TextArea descArea;
    @FXML private ComboBox<String> categoryBox;
    @FXML private TextField startPriceField;
    @FXML private TextField reservePriceField;
    @FXML private DatePicker startDate;
    @FXML private DatePicker endDate;
    @FXML private Label imageStatusLabel;

    private final List<String> uploadedUrls = new ArrayList<>();

    @FXML
    private void initialize() {
        categoryBox.getItems().setAll("ELECTRONICS", "ART", "VEHICLE");
        categoryBox.getSelectionModel().selectFirst();
    }

    @Override
    public void onShow() {
        uploadedUrls.clear();
        imageStatusLabel.setText("Chưa upload ảnh");
    }

    @FXML
    private void onPickImages() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        List<File> files = chooser.showOpenMultipleDialog(nameField.getScene().getWindow());
        if (files == null || files.isEmpty()) {
            return;
        }
        try {
            uploadedUrls.clear();
            for (File f : files) {
                String url = ImageUploadService.getInstance().upload(Path.of(f.getAbsolutePath()));
                uploadedUrls.add(url);
            }
            imageStatusLabel.setText("Đã upload " + uploadedUrls.size() + " ảnh");
        } catch (Exception e) {
            AlertUtil.showError("Upload thất bại: " + e.getMessage());
        }
    }

    @FXML
    private void onCreate() {
        try {
            AuctionDTOs.CreateAuctionRequestDTO req = new AuctionDTOs.CreateAuctionRequestDTO();
            req.setItemName(nameField.getText().trim());
            req.setItemDescription(descArea.getText());
            req.setItemCategory(categoryBox.getValue());
            req.setStartingPrice(Double.parseDouble(startPriceField.getText().trim()));
            req.setReservePrice(Double.parseDouble(reservePriceField.getText().trim()));
            req.setStartTime(startDate.getValue() != null
                    ? startDate.getValue().atStartOfDay() : LocalDateTime.now());
            req.setEndTime(endDate.getValue() != null
                    ? endDate.getValue().atTime(23, 59) : LocalDateTime.now().plusDays(1));
            if (!uploadedUrls.isEmpty()) {
                req.setImageUrls(new ArrayList<>(uploadedUrls));
            }
            services().sellerAuctionService().createAuction(req);
        } catch (Exception e) {
            AlertUtil.showError("Tạo phiên thất bại: " + e.getMessage());
        }
    }
}
