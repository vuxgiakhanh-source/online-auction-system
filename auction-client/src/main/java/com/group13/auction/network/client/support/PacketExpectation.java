package com.group13.auction.network.client.support;

import com.group13.auction.common.protocol.PacketType;
import java.util.Objects;
import java.util.Optional;

/**
 * Mô tả cặp response dự kiến cho một request gửi từ client lên server.
 *
 * <p>Một số request chỉ có success packet rõ ràng, còn lỗi có thể trả về {@code SYSTEM_ERROR}.
 * Vì vậy {@code failureType} được để optional thay vì bắt buộc.
 */
public final class PacketExpectation {

    private final PacketType requestType;
    private final PacketType successType;
    private final PacketType failureType;

    private PacketExpectation(PacketType requestType, PacketType successType, PacketType failureType) {
        this.requestType = Objects.requireNonNull(requestType, "requestType");
        this.successType = Objects.requireNonNull(successType, "successType");
        this.failureType = failureType;
    }

    /**
     * Tạo expectation có cả success và failure packet riêng.
     *
     * @param requestType packet request client gửi lên
     * @param successType packet server trả về khi thành công
     * @param failureType packet server trả về khi thất bại nghiệp vụ
     * @return expectation tương ứng
     */
    public static PacketExpectation of(
            PacketType requestType, PacketType successType, PacketType failureType) {
        return new PacketExpectation(requestType, successType, failureType);
    }

    /**
     * Tạo expectation chỉ có success packet riêng.
     *
     * @param requestType packet request client gửi lên
     * @param successType packet server trả về khi thành công
     * @return expectation tương ứng
     */
    public static PacketExpectation successOnly(PacketType requestType, PacketType successType) {
        return new PacketExpectation(requestType, successType, null);
    }

    public PacketType requestType() {
        return requestType;
    }

    public PacketType successType() {
        return successType;
    }

    public Optional<PacketType> failureType() {
        return Optional.ofNullable(failureType);
    }
}