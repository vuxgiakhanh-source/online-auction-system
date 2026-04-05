package com.group13.auction.model.user;

/**
 * Factory chuyên tạo Admin.
 */
public class AdminFactory extends UserFactory {
    /**
     * @param args tham số phụ:
     * args[0]: level (int) - Cấp độ quản trị (mặc định là 1)
     */
    @Override
    protected User createProduct(String username, String password, String email, Object... args) {
        // Admin chỉ được tạo bởi Admin khác (lỗi #9)
        // Tại đây chỉ dùng để seed admin đầu tiên từ hệ thống
        int level = (args.length > 0) ? (int) args[0] : 1;
        return Admin.create(username, password, email, level);
    }
}
