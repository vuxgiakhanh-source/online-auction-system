package com.group13.auction;

/**
 * Delegating launcher dùng cho IDE và Maven JavaFX plugin.
 *
 * <p>Một số môi trường chạy JavaFX ổn định hơn khi main class không trực tiếp kế thừa
 * {@code Application}.
 */
public final class Launcher {

    private Launcher() {
        // Entry class.
    }

    /**
     * Chuyển quyền chạy app sang {@link App}.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        App.main(args);
    }
}