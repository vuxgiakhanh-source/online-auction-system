package com.group13.auction.viewmodel.auction;

import java.util.Optional;

/** Dữ liệu form đăng ký Auto-Bid ở phía client. */
public final class AutoBidFormViewModel {

    private final String maxBidText;

    public AutoBidFormViewModel(String maxBidText) {
        this.maxBidText = maxBidText == null ? "" : maxBidText;
    }

    public String maxBidText() {
        return maxBidText;
    }

    /** Validate giá tối đa trước khi gửi request Auto-Bid. */
    public Optional<String> validate() {
        if (maxBidText.trim().isEmpty()) {
            return Optional.of("Bạn chưa nhập giá tối đa cho auto-bid.");
        }

        try {
            if (maxBidAmount() <= 0) {
                return Optional.of("Giá tối đa phải lớn hơn 0.");
            }
        } catch (NumberFormatException exception) {
            return Optional.of("Giá tối đa phải là số nguyên hợp lệ.");
        }

        return Optional.empty();
    }

    public long maxBidAmount() {
        return Long.parseLong(normalizedAmountText());
    }

    private String normalizedAmountText() {
        return maxBidText.trim().replaceAll("[\\s,.]", "");
    }
}