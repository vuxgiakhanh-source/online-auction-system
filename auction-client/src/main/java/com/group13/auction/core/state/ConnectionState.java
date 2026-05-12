package com.group13.auction.core.state;

/**
 * Trạng thái kết nối giữa JavaFX client và auction server.
 */
public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}