package com.group13.auction.ui.controller.seller;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.service.seller.SellerAuctionService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.FxThreadUtil;
import com.group13.auction.viewmodel.seller.AuctionFormViewModel;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;

/** Controller cho form tạo phiên đấu giá của người bán. */
public final class CreateAuctionController {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final SellerAuctionService sellerAuctionService = new SellerAuctionService();
    private final List<Path> selectedImagePaths = new ArrayList<>();

    @FXML private TextField itemNameField;

    @FXML private ComboBox<String> categoryComboBox;

    @FXML private TextField startingPriceField;

    @FXML private TextField reservePriceField;

    @FXML private DatePicker startDatePicker;

    @FXML private TextField startTimeField;

    @FXML private DatePicker endDatePicker;

    @FXML private TextField endTimeField;

    @FXML private TextArea descriptionArea;

    @FXML private Label extraField1Label;

    @FXML private TextField extraField1;

    @FXML private Label extraField2Label;

    @FXML private TextField extraField2;

    @FXML private Label extraField3Label;

    @FXML private TextField extraField3;

    @FXML private ListView<Path> selectedImagesListView;

    @FXML private Button chooseImagesButton;

    @FXML private Button removeImageButton;

    @FXML private Button clearImagesButton;

    @FXML private Button createButton;

    @FXML private Button resetButton;

    @FXML private Label statusLabel;

    @FXML private ProgressIndicator loadingIndicator;

    /** Khởi tạo dữ liệu mặc định cho form tạo phiên. */
    @FXML
    public void initialize() {
        setupCategoryComboBox();
        setupDefaultDateTime();
        setupImagePreviewList();
        refreshSelectedImagesView();
        setLoading(false, "Nhập thông tin phiên đấu giá.");
    }

    /** Quay lại danh sách phiên của người bán. */
    @FXML
    public void handleBackToSellerList() {
        Navigator.getInstance().goToSellerAuctionList();
    }

    /** Chọn ảnh sản phẩm để upload trước khi tạo phiên đấu giá. */
    @FXML
    public void handleChooseImages() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh sản phẩm");
        fileChooser
            .getExtensionFilters()
            .add(
                new FileChooser.ExtensionFilter(
                    "Image files", "*.png", "*.jpg", "*.jpeg", "*.webp"));

        List<File> files = fileChooser.showOpenMultipleDialog(null);
        if (files == null || files.isEmpty()) {
            return;
        }

        for (File file : files) {
            if (selectedImagePaths.size() >= AuctionFormViewModel.MAX_IMAGE_COUNT) {
                AlertUtil.showWarning(
                    "Chỉ được chọn tối đa " + AuctionFormViewModel.MAX_IMAGE_COUNT + " ảnh.");
                break;
            }

            Path path = file.toPath();
            if (!selectedImagePaths.contains(path)) {
                selectedImagePaths.add(path);
            }
        }

        refreshSelectedImagesView();
        statusLabel.setText("Đã chọn " + selectedImagePaths.size() + " ảnh sản phẩm.");
    }

    /** Xóa ảnh đang được chọn trong danh sách. */
    @FXML
    public void handleRemoveSelectedImage() {
        int selectedIndex =
            selectedImagesListView == null
                ? -1
                : selectedImagesListView.getSelectionModel().getSelectedIndex();

        if (selectedIndex < 0 || selectedIndex >= selectedImagePaths.size()) {
            AlertUtil.showWarning("Vui lòng chọn ảnh cần xóa.");
            return;
        }

        selectedImagePaths.remove(selectedIndex);
        refreshSelectedImagesView();
        statusLabel.setText("Đã xóa ảnh đã chọn.");
    }

    /** Xóa toàn bộ ảnh đã chọn. */
    @FXML
    public void handleClearImages() {
        selectedImagePaths.clear();
        refreshSelectedImagesView();
        statusLabel.setText("Đã xóa toàn bộ ảnh sản phẩm.");
    }

    /** Xóa dữ liệu đang nhập và đưa form về trạng thái mặc định. */
    @FXML
    public void handleResetForm() {
        itemNameField.clear();
        startingPriceField.clear();
        reservePriceField.clear();
        descriptionArea.clear();

        categoryComboBox.getSelectionModel().selectFirst();
        clearExtraFields();
        selectedImagePaths.clear();
        refreshSelectedImagesView();
        setupDefaultDateTime();

        setLoading(false, "Form đã được đặt lại.");
    }

    /** Tạo phiên đấu giá mới bằng request thật tới server. */
    @FXML
    public void handleCreateAuction() {
        AuctionFormViewModel form;
        try {
            form = buildFormViewModel();
        } catch (IllegalArgumentException exception) {
            AlertUtil.showWarning(exception.getMessage());
            statusLabel.setText(exception.getMessage());
            return;
        }

        setLoading(
            true,
            selectedImagePaths.isEmpty()
                ? "Đang tạo phiên đấu giá..."
                : "Đang upload ảnh và tạo phiên đấu giá...");

        sellerAuctionService
            .createAuction(form)
            .thenAccept(
                createdAuction ->
                    FxThreadUtil.runOnFxThread(
                        () -> {
                            AlertUtil.showInfo("Phiên đấu giá đã được tạo thành công.");
                            Navigator.getInstance().goToSellerAuctionList();
                        }))
            .exceptionally(
                throwable -> {
                    FxThreadUtil.runOnFxThread(
                        () -> {
                            setLoading(false, "Không tạo được phiên đấu giá.");
                            AlertUtil.showError(extractMessage(throwable));
                        });
                    return null;
                });
    }

    private void setupCategoryComboBox() {
        categoryComboBox.getItems().setAll("Điện tử", "Nghệ thuật", "Phương tiện");
        categoryComboBox
            .getSelectionModel()
            .selectedItemProperty()
            .addListener((observable, oldValue, newValue) -> updateExtraFieldLabels());
        categoryComboBox.getSelectionModel().selectFirst();
        updateExtraFieldLabels();
    }

    private void setupDefaultDateTime() {
        LocalDateTime start = LocalDateTime.now().plusMinutes(15).withSecond(0).withNano(0);
        LocalDateTime end = start.plusDays(1);

        startDatePicker.setValue(start.toLocalDate());
        startTimeField.setText(start.toLocalTime().format(TIME_FORMATTER));

        endDatePicker.setValue(end.toLocalDate());
        endTimeField.setText(end.toLocalTime().format(TIME_FORMATTER));
    }

    private void updateExtraFieldLabels() {
        clearExtraFields();

        switch (selectedCategoryCode()) {
            case "ELECTRONICS" -> {
                extraField1Label.setText("Thương hiệu");
                extraField1.setPromptText("Ví dụ: Sony, Apple, Samsung");

                extraField2Label.setText("Bảo hành (tháng)");
                extraField2.setPromptText("Ví dụ: 12");

                extraField3Label.setText("Tình trạng");
                extraField3.setPromptText("Ví dụ: Mới, đã qua sử dụng");
            }
            case "ART" -> {
                extraField1Label.setText("Nghệ sĩ");
                extraField1.setPromptText("Ví dụ: Van Gogh");

                extraField2Label.setText("Năm sáng tác");
                extraField2.setPromptText("Ví dụ: 1889");

                extraField3Label.setText("Chất liệu");
                extraField3.setPromptText("Ví dụ: Sơn dầu");
            }
            case "VEHICLE" -> {
                extraField1Label.setText("Nhà sản xuất");
                extraField1.setPromptText("Ví dụ: Toyota, Honda");

                extraField2Label.setText("Năm sản xuất");
                extraField2.setPromptText("Ví dụ: 2022");

                extraField3Label.setText("Số km đã đi");
                extraField3.setPromptText("Ví dụ: 15000");
            }
            default -> throw new IllegalStateException("Loại sản phẩm không hợp lệ.");
        }
    }

    private AuctionFormViewModel buildFormViewModel() {
        String itemName = requireText(itemNameField.getText(), "Tên sản phẩm không được để trống.");
        String description = requireText(descriptionArea.getText(), "Mô tả sản phẩm không được để trống.");
        String categoryCode = selectedCategoryCode();

        double startingPrice = parsePositiveAmount(startingPriceField.getText(), "Giá khởi điểm");
        double reservePrice = parsePositiveAmount(reservePriceField.getText(), "Giá sàn");

        // FIX Bug #2: giá sàn bí mật không được thấp hơn giá khởi điểm
        if (reservePrice < startingPrice) {
            throw new IllegalArgumentException(
                "Giá sàn bí mật (" + (long) reservePrice + " đ) không được thấp hơn giá khởi điểm ("
                    + (long) startingPrice + " đ).");
        }

        LocalDateTime startTime =
            parseDateTime(startDatePicker.getValue(), startTimeField.getText(), "Thời gian bắt đầu");
        LocalDateTime endTime =
            parseDateTime(endDatePicker.getValue(), endTimeField.getText(), "Thời gian kết thúc");

        Map<String, Object> extraFields = buildExtraFields(categoryCode);

        return new AuctionFormViewModel(
            itemName,
            description,
            categoryCode,
            startingPrice,
            reservePrice,
            startTime,
            endTime,
            extraFields,
            List.copyOf(selectedImagePaths));
    }

    private Map<String, Object> buildExtraFields(String categoryCode) {
        Map<String, Object> fields = new LinkedHashMap<>();

        switch (categoryCode) {
            case "ELECTRONICS" -> {
                putOptionalText(fields, "brand", extraField1.getText());
                fields.put("warrantyMonths", parseNonNegativeInt(extraField2.getText(), "Bảo hành"));
                putOptionalText(fields, "condition", extraField3.getText());
            }
            case "ART" -> {
                putOptionalText(fields, "artist", extraField1.getText());
                fields.put("yearCreated", parseNonNegativeInt(extraField2.getText(), "Năm sáng tác"));
                putOptionalText(fields, "medium", extraField3.getText());
            }
            case "VEHICLE" -> {
                putOptionalText(fields, "manufacturer", extraField1.getText());
                fields.put("year", parseNonNegativeInt(extraField2.getText(), "Năm sản xuất"));
                fields.put("mileage", parseNonNegativeDouble(extraField3.getText(), "Số km đã đi"));
            }
            default -> throw new IllegalArgumentException("Loại sản phẩm không hợp lệ.");
        }

        return fields;
    }

    private String selectedCategoryCode() {
        String selected = categoryComboBox.getValue();

        if ("Điện tử".equals(selected)) {
            return "ELECTRONICS";
        }
        if ("Nghệ thuật".equals(selected)) {
            return "ART";
        }
        if ("Phương tiện".equals(selected)) {
            return "VEHICLE";
        }

        throw new IllegalArgumentException("Vui lòng chọn loại sản phẩm.");
    }

    private LocalDateTime parseDateTime(LocalDate date, String timeText, String fieldName) {
        if (date == null) {
            throw new IllegalArgumentException(fieldName + " chưa có ngày.");
        }

        try {
            LocalTime time = LocalTime.parse(requireText(timeText, fieldName + " chưa có giờ."), TIME_FORMATTER);
            return LocalDateTime.of(date, time);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(fieldName + " phải có định dạng HH:mm.");
        }
    }

    private double parsePositiveAmount(String value, String fieldName) {
        try {
            double amount = Double.parseDouble(normalizeNumber(value));
            if (amount <= 0) {
                throw new IllegalArgumentException(fieldName + " phải lớn hơn 0.");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " phải là số hợp lệ.");
        }
    }

    private int parseNonNegativeInt(String value, String fieldName) {
        String normalized = normalizeNumber(value);
        if (normalized.isBlank()) {
            return 0;
        }

        try {
            int number = Integer.parseInt(normalized);
            if (number < 0) {
                throw new IllegalArgumentException(fieldName + " không được âm.");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " phải là số nguyên hợp lệ.");
        }
    }

    private double parseNonNegativeDouble(String value, String fieldName) {
        String normalized = normalizeNumber(value);
        if (normalized.isBlank()) {
            return 0;
        }

        try {
            double number = Double.parseDouble(normalized);
            if (number < 0) {
                throw new IllegalArgumentException(fieldName + " không được âm.");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " phải là số hợp lệ.");
        }
    }

    private void putOptionalText(Map<String, Object> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value.trim());
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeNumber(String value) {
        return value == null ? "" : value.trim().replace(",", "");
    }

    private void clearExtraFields() {
        extraField1.clear();
        extraField2.clear();
        extraField3.clear();
    }

    private void setupImagePreviewList() {
        if (selectedImagesListView == null) {
            return;
        }

        selectedImagesListView.setFixedCellSize(72.0);
        selectedImagesListView.setCellFactory(listView -> createImagePreviewCell());
    }

    private ListCell<Path> createImagePreviewCell() {
        return new ListCell<>() {
            private final ImageView previewImage = new ImageView();
            private final Label fileNameLabel = new Label();
            private final HBox row = new HBox(12.0, previewImage, fileNameLabel);

            {
                previewImage.setFitWidth(72.0);
                previewImage.setFitHeight(54.0);
                previewImage.setPreserveRatio(true);
                previewImage.setSmooth(true);
                previewImage.getStyleClass().add("seller-image-preview");

                fileNameLabel.getStyleClass().add("seller-image-preview-name");

                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("seller-image-preview-row");
            }

            @Override
            protected void updateItem(Path path, boolean empty) {
                super.updateItem(path, empty);

                if (empty || path == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                previewImage.setImage(new Image(path.toUri().toString(), 72.0, 54.0, true, true, true));
                fileNameLabel.setText(path.getFileName().toString());

                setText(null);
                setGraphic(row);
            }
        };
    }

    private void refreshSelectedImagesView() {
        if (selectedImagesListView == null) {
            return;
        }

        selectedImagesListView.getItems().setAll(selectedImagePaths);

        boolean hasImages = !selectedImagePaths.isEmpty();
        if (removeImageButton != null) {
            removeImageButton.setDisable(!hasImages);
        }
        if (clearImagesButton != null) {
            clearImagesButton.setDisable(!hasImages);
        }
    }

    private void setLoading(boolean loading, String message) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);

        createButton.setDisable(loading);
        resetButton.setDisable(loading);

        if (chooseImagesButton != null) {
            chooseImagesButton.setDisable(loading);
        }
        if (removeImageButton != null) {
            removeImageButton.setDisable(loading || selectedImagePaths.isEmpty());
        }
        if (clearImagesButton != null) {
            clearImagesButton.setDisable(loading || selectedImagePaths.isEmpty());
        }

        statusLabel.setText(message);
    }

    private String extractMessage(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();
        return message == null || message.isBlank() ? "Không xử lý được yêu cầu." : message;
    }
}