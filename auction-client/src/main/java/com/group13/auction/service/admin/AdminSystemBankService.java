package com.group13.auction.service.admin;

import com.group13.auction.common.dto.bank.SystemBankDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.mapper.SystemBankViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.admin.FinancialTransactionPageViewModel;
import com.group13.auction.viewmodel.admin.SystemBankSummaryViewModel;
import java.util.concurrent.CompletableFuture;

/** Service phía client cho màn System Bank của Admin. */
public final class AdminSystemBankService {

  private static final int DEFAULT_PAGE = 1;
  private static final int DEFAULT_PAGE_SIZE = 50;

  private final ClientNetworkFacade networkFacade;

  /** Khởi tạo service bằng network facade mặc định. */
  public AdminSystemBankService() {
    this(ClientNetworkFacade.getDefault());
  }

  /** Constructor hỗ trợ test hoặc inject network facade tùy chỉnh. */
  public AdminSystemBankService(ClientNetworkFacade networkFacade) {
    this.networkFacade = networkFacade;
  }

  /** Lấy tổng quan System Bank. */
  public CompletableFuture<SystemBankSummaryViewModel> getSummary() {
    if (!currentUserIsAdmin()) {
      return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
    }

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.adminGetSystemBankSummary(),
            PacketType.ADMIN_GET_SYSTEM_BANK_SUMMARY_SUCCESS,
            SystemBankDTOs.SystemBankSummaryDTO.class,
            "Không tải được tổng quan System Bank.")
        .thenApply(SystemBankViewModelMapper::toSummaryViewModel);
  }

  /** Lấy danh sách giao dịch tài chính gần nhất. */
  public CompletableFuture<FinancialTransactionPageViewModel> getTransactions(
      String transactionType, String auctionId, int page, int pageSize) {
    if (!currentUserIsAdmin()) {
      return AuctionServiceSupport.failedFuture("Tài khoản hiện tại không có quyền Admin.");
    }

    SystemBankDTOs.FinancialTransactionListRequestDTO request =
        new SystemBankDTOs.FinancialTransactionListRequestDTO();
    request.setTransactionType(normalizeFilter(transactionType));
    request.setAuctionId(normalizeFilter(auctionId));
    request.setPage(page > 0 ? page : DEFAULT_PAGE);
    request.setPageSize(pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE);

    return AuctionServiceSupport.sendRequest(
            networkFacade,
            ClientRequestFactory.adminGetFinancialTransactions(request),
            PacketType.ADMIN_GET_FINANCIAL_TRANSACTIONS_SUCCESS,
            SystemBankDTOs.FinancialTransactionListDTO.class,
            "Không tải được lịch sử giao dịch tài chính.")
        .thenApply(SystemBankViewModelMapper::toTransactionPageViewModel);
  }

  private boolean currentUserIsAdmin() {
    return AppContext.getInstance()
        .getSessionManager()
        .getCurrentSession()
        .map(session -> session.isAdmin())
        .orElse(false);
  }

  private String normalizeFilter(String value) {
    return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())
        ? null
        : value.trim();
  }
}
