package com.group13.auction.ui.controller.base;

/**
 * Gọi khi màn hình được load vào shell — dùng để refresh dữ liệu từ server.
 */
public interface PageLifecycle {

    void onShow();
}
