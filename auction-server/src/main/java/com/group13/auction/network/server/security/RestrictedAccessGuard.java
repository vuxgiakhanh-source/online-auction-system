package com.group13.auction.network.server.security;

import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.session.ClientSession;

/** Chặn packet nghiệp vụ khi session ở chế độ restricted (BANNED/SUSPENDED đã đăng nhập). */
public final class RestrictedAccessGuard {

  private RestrictedAccessGuard() {}

  /**
   * @return true nếu đã gửi phản hồi từ chối và caller phải dừng xử lý
   */
  public static boolean blockIfRestricted(
      ClientSession session, PacketType type, String requestId) {
    if (session == null || !session.isAuthenticated() || !session.isRestricted()) {
      return false;
    }
    if (RestrictedPacketPolicy.isAllowed(type)) {
      return false;
    }
    String code = ErrorDTO.ACCOUNT_RESTRICTED;
    session.send(
        Packet.of(
            failedPacketType(type),
            ErrorDTO.of(code, RestrictedPacketPolicy.denialMessage(), requestId),
            requestId));
    return true;
  }

  private static PacketType failedPacketType(PacketType requestType) {
    return switch (requestType) {
      case DEPOSIT -> PacketType.DEPOSIT_FAILED;
      case WITHDRAW -> PacketType.WITHDRAW_FAILED;
      case GET_WALLET_BALANCE -> PacketType.SYSTEM_ERROR;
      case ADMIN_GET_SYSTEM_BANK_SUMMARY -> PacketType.ADMIN_GET_SYSTEM_BANK_SUMMARY_FAILED;
      case ADMIN_GET_FINANCIAL_TRANSACTIONS -> PacketType.ADMIN_GET_FINANCIAL_TRANSACTIONS_FAILED;
      default -> PacketType.SYSTEM_ERROR;
    };
  }
}