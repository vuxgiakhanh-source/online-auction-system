package com.group13.auction.core.state;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Kho state nhỏ để truyền dữ liệu tạm thời giữa các màn hình.
 *
 * <p>Ví dụ: màn danh sách đấu giá lưu {@code selectedAuctionId}, sau đó màn chi tiết đọc lại id này.
 */
public final class ScreenStateStore {

    private final Map<String, Object> values = new HashMap<>();

    /**
     * Lưu một giá trị theo key.
     *
     * @param key tên khóa
     * @param value giá trị cần lưu
     */
    public void put(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }

        if (value == null) {
            values.remove(key);
            return;
        }

        values.put(key, value);
    }

    /**
     * Đọc giá trị theo key và kiểm tra kiểu dữ liệu.
     *
     * @param key tên khóa
     * @param type kiểu mong muốn
     * @param <T> kiểu dữ liệu trả về
     * @return optional chứa giá trị nếu tồn tại và đúng kiểu
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        if (key == null || key.isBlank() || type == null) {
            return Optional.empty();
        }

        Object value = values.get(key);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    /**
     * Xóa một key khỏi store.
     *
     * @param key key cần xóa
     */
    public void remove(String key) {
        values.remove(key);
    }

    /** Xóa toàn bộ state tạm thời. */
    public void clear() {
        values.clear();
    }

    /**
     * Trả về bản sao chỉ đọc của state hiện tại.
     *
     * @return map state chỉ đọc
     */
    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(values));
    }
}