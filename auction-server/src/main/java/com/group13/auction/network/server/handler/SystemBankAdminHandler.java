package com.group13.auction.network.server.handler;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.bank.SystemBankDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.service.SystemBankAdminService;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Handler cho màn System Bank dành cho tài khoản Admin. */
public class SystemBankAdminHandler implements PacketHandler {

  private static final Logger log = LoggerFactory.getLogger(SystemBankAdminHandler.class);

  private static final Set<PacketType> SUPPORTED =
      EnumSet.of(
          PacketType.ADMIN_GET_SYSTEM_BANK_SUMMARY,
          PacketType.ADMIN_GET_FINANCIAL_TRANSACTIONS);

  private final SystemBankAdminService systemBankAdminService;

  /** Khởi tạo handler với service mặc định. */
  public SystemBankAdminHandler() {
    this(new SystemBankAdminService());
  }

  /** Constructor hỗ trợ test hoặc inject service tùy chỉnh. */
  public SystemBankAdminHandler(SystemBankAdminService systemBankAdminService) {
    this.systemBankAdminService = systemBankAdminService;
  }

  @Override
  public boolean supports(PacketType type) {
    return SUPPORTED.contains(type);
  }

  @Override
  public void handle(ClientSession session, PacketType type, JsonElement payload, String requestId) {
    log.info(
        "SystemBankAdminHandler: type={}, user={}, requestId={}",
        type,
        session.getUsername(),
        requestId);

    if (!session.isAuthenticated()) {
      sendFailure(session, type, ErrorDTO.UNAUTHORIZED, "Chưa đăng nhập.", requestId);
      return;
    }

    if (!session.isAdmin()) {
      sendFailure(
          session,
          type,
          ErrorDTO.UNAUTHORIZED,
          "Chỉ tài khoản Admin được truy cập System Bank.",
          requestId);
      return;
    }

    try {
      switch (type) {
        case ADMIN_GET_SYSTEM_BANK_SUMMARY -> handleGetSummary(session, requestId);
        case ADMIN_GET_FINANCIAL_TRANSACTIONS ->
            handleGetFinancialTransactions(session, payload, requestId);
        default -> {}
      }
    } catch (IllegalArgumentException exception) {
      sendFailure(session, type, ErrorDTO.VALIDATION_ERROR, exception.getMessage(), requestId);
    } catch (Exception exception) {
      log.error("System Bank request failed: type={}", type, exception);
      sendFailure(
          session,
          type,
          ErrorDTO.INTERNAL_ERROR,
          "Không tải được dữ liệu System Bank.",
          requestId);
    }
  }

  private void handleGetSummary(ClientSession session, String requestId) {
    session.send(
        Packet.of(
            PacketType.ADMIN_GET_SYSTEM_BANK_SUMMARY_SUCCESS,
            systemBankAdminService.getSummary(),
            requestId));
  }

  private void handleGetFinancialTransactions(
      ClientSession session, JsonElement payload, String requestId) {
    SystemBankDTOs.FinancialTransactionListRequestDTO request =
        payload == null || payload.isJsonNull()
            ? new SystemBankDTOs.FinancialTransactionListRequestDTO()
            : PacketCodec.fromElement(
            payload, SystemBankDTOs.FinancialTransactionListRequestDTO.class);

    session.send(
        Packet.of(
            PacketType.ADMIN_GET_FINANCIAL_TRANSACTIONS_SUCCESS,
            systemBankAdminService.getTransactions(request),
            requestId));
  }

  private void sendFailure(
      ClientSession session, PacketType requestType, String code, String message, String requestId) {
    session.send(
        Packet.of(failureTypeFor(requestType), ErrorDTO.of(code, message, requestId), requestId));
  }

  private PacketType failureTypeFor(PacketType requestType) {
    return switch (requestType) {
      case ADMIN_GET_SYSTEM_BANK_SUMMARY -> PacketType.ADMIN_GET_SYSTEM_BANK_SUMMARY_FAILED;
      case ADMIN_GET_FINANCIAL_TRANSACTIONS -> PacketType.ADMIN_GET_FINANCIAL_TRANSACTIONS_FAILED;
      default -> PacketType.SYSTEM_ERROR;
    };
  }
}