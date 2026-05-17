package com.group13.auction.viewmodel.seller;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * View model đại diện dữ liệu form tạo phiên đấu giá của Seller.
 *
 * <p>Lớp này chỉ validate dữ liệu nhập cơ bản ở phía client và chuyển sang DTO dùng chung trong
 * {@code auction-common}. Nghiệp vụ chính như quyền Seller, rating, trạng thái phiên và điều kiện
 * hợp lệ cuối cùng vẫn do server xử lý.
 */
public final class AuctionFormViewModel {

    private final String itemName;
    private final String itemDescription;
    private final String itemCategory;
    private final double startingPrice;
    private final double reservePrice;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final Map<String, Object> itemExtraFields;

    /**
     * Tạo view model cho form tạo phiên đấu giá.
     *
     * @param itemName tên sản phẩm
     * @param itemDescription mô tả sản phẩm
     * @param itemCategory loại sản phẩm: {@code ELECTRONICS}, {@code ART}, {@code VEHICLE}
     * @param startingPrice giá khởi điểm
     * @param reservePrice giá sàn bí mật
     * @param startTime thời gian bắt đầu
     * @param endTime thời gian kết thúc
     * @param itemExtraFields thông tin mở rộng theo loại sản phẩm
     */
    public AuctionFormViewModel(
            String itemName,
            String itemDescription,
            String itemCategory,
            double startingPrice,
            double reservePrice,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Map<String, Object> itemExtraFields) {
        this.itemName = trimToEmpty(itemName);
        this.itemDescription = trimToEmpty(itemDescription);
        this.itemCategory = trimToEmpty(itemCategory).toUpperCase();
        this.startingPrice = startingPrice;
        this.reservePrice = reservePrice;
        this.startTime = startTime;
        this.endTime = endTime;
        this.itemExtraFields = normalizeExtraFields(itemExtraFields);
    }

    /**
     * Validate form tạo phiên ở mức cơ bản trước khi gửi request.
     *
     * <p>Đây không thay thế validation nghiệp vụ của server. Client chỉ chặn lỗi nhập liệu rõ ràng để
     * tránh gửi request vô nghĩa.
     */
    public void validateForCreate() {
        if (itemName.isBlank()) {
            throw new IllegalArgumentException("Tên sản phẩm không được để trống.");
        }
        if (itemDescription.isBlank()) {
            throw new IllegalArgumentException("Mô tả sản phẩm không được để trống.");
        }
        if (!isSupportedCategory(itemCategory)) {
            throw new IllegalArgumentException(
                    "Loại sản phẩm chỉ hỗ trợ ELECTRONICS, ART hoặc VEHICLE.");
        }
        if (startingPrice <= 0) {
            throw new IllegalArgumentException("Giá khởi điểm phải lớn hơn 0.");
        }
        if (reservePrice <= 0) {
            throw new IllegalArgumentException("Giá sàn phải lớn hơn 0.");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Thời gian bắt đầu không được để trống.");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("Thời gian kết thúc không được để trống.");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }
    }

    /**
     * Chuyển form sang DTO request của {@code auction-common}.
     *
     * @return request tạo phiên đấu giá
     */
    public AuctionDTOs.CreateAuctionRequestDTO toCreateRequest() {
        validateForCreate();

        AuctionDTOs.CreateAuctionRequestDTO request =
                new AuctionDTOs.CreateAuctionRequestDTO();
        request.setItemName(itemName);
        request.setItemDescription(itemDescription);
        request.setItemCategory(itemCategory);
        request.setStartingPrice(startingPrice);
        request.setReservePrice(reservePrice);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setItemExtraFields(itemExtraFields);
        return request;
    }

    public String itemName() {
        return itemName;
    }

    public String itemDescription() {
        return itemDescription;
    }

    public String itemCategory() {
        return itemCategory;
    }

    public double startingPrice() {
        return startingPrice;
    }

    public double reservePrice() {
        return reservePrice;
    }

    public LocalDateTime startTime() {
        return startTime;
    }

    public LocalDateTime endTime() {
        return endTime;
    }

    public Map<String, Object> itemExtraFields() {
        return itemExtraFields;
    }

    private static boolean isSupportedCategory(String category) {
        return "ELECTRONICS".equals(category) || "ART".equals(category) || "VEHICLE".equals(category);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static Map<String, Object> normalizeExtraFields(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        fields.forEach(
                (key, value) -> {
                    if (key == null || key.isBlank() || value == null) {
                        return;
                    }
                    if (value instanceof String text && text.isBlank()) {
                        return;
                    }
                    normalized.put(key.trim(), value);
                });
        return Map.copyOf(normalized);
    }
}