package com.group13.auction.network.client.support;

/**
 * Exception riêng của tầng network client.
 *
 * <p>Dùng cho các lỗi cấu hình/kết nối phía client, không đại diện cho lỗi nghiệp vụ server.
 */
public class NetworkClientException extends RuntimeException {

    public NetworkClientException(String message) {
        super(message);
    }

    public NetworkClientException(String message, Throwable cause) {
        super(message, cause);
    }
}