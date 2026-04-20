package com.group13.auction.client;

/**
 * Entry point để khởi chạy ứng dụng JavaFX.
 */
public final class Launcher {

    private Launcher() {
        // Ngăn khởi tạo đối tượng cho utility class.
    }

    /**
     * Hàm main dùng để chạy ứng dụng.
     *
     * @param args tham số dòng lệnh
     */
    public static void main(String[] args) {
        App.launch(App.class, args);
    }
}