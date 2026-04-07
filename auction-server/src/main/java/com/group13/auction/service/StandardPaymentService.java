package com.group13.auction.service;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.exception.PaymentException;
import com.group13.auction.exception.PaymentException.Reason;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.AuctionWinner.PaymentStatus;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.observer.AuctionEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Xử lý thanh toán sau khi phiên đấu giá kết thúc.
 * Thanh toán tiêu chuẩn — trả phần còn lại một lần sau khi trừ cọc.
 *
 * <p>Là một trong nhiều implementation có thể có của {@link IPaymentService}.
 * (Nếu có time) thêm kiểu thanh toán mới (trả góp, escrow) implements IPaymentService.
 *
 * <p>Nhận {@link IRatingService} và {@link IAuctionService} qua constructor.
 * Tách khỏi BidService để tuân thủ SRP.
 * TODO: inject AuctionDAO, UserDAO để persist xuống DB.
 */
public class StandardPaymentService implements IPaymentService {

  private final IRatingService  ratingService;
  private final IAuctionService auctionService;
  private final SystemBank      bank;

  public StandardPaymentService(IRatingService ratingService,
                                IAuctionService auctionService) {
    this.ratingService  = ratingService;
    this.auctionService = auctionService;
    this.bank           = SystemBank.getInstance();
  }

  /**
   * Xử lý thanh toán sau khi phiên FINISHED (trong 24h).
   * Flow:
   * 1. Winner trả phần còn lại (finalPrice - depositPaid).
   * 2. Tiền vào SystemBank.
   * 3. Bank trừ thuế, chuyển phần còn lại cho seller.
   * 4. Seller được cộng tiền.
   * 5. Cọc của winner được chuyển cho seller (đã khóa từ trước).
   * 6. Đánh dấu PAID.
   * TODO: auctionDAO.update(auction), userDAO.update(winner), userDAO.update(seller).
   *
   * @param auction phiên cần thanh toán
   * @throws IllegalStateException nếu không có winner
   * @throws PaymentException      nếu không đủ số dư hoặc số tiền sai
   */
  @Override
  public void completePayment(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    if (auctionWinner.isExpired()) {
      expirePayment(auction);
      return;
    }

    NormalUser winner     = auctionWinner.getWinner();
    double     finalPrice = auctionWinner.getFinalPrice();
    double     remaining  = auctionWinner.getRemainingAmount();

    // Kiểm tra số dư khả dụng đủ trả phần còn lại
    if (winner.getAvailableBalance() < remaining) {
      throw new PaymentException(Reason.INSUFFICIENT_BALANCE,
              String.format("%s cần trả %.0f nhưng số dư khả dụng chỉ có %.0f.",
                      winner.getUsername(), remaining, winner.getAvailableBalance()));
    }

    // Winner trả đúng số tiền còn lại
    winner.setBalance(winner.getBalance() - remaining);
    // Giải phóng cọc của winner (cọc được chuyển cho seller, không hoàn lại)
    winner.unlockDeposit(auctionWinner.getDepositPaid());

    // Tiền vào bank (toàn bộ finalPrice: remaining + deposit đã thu từ winner)
    bank.receive(finalPrice);

    // Bank chuyển tiền (sau thuế) cho seller
    NormalUser seller      = (NormalUser) auction.getItem().getSeller();
    double     sellerPayout = bank.payoutToSeller(finalPrice);
    seller.setBalance(seller.getBalance() + sellerPayout);

    auctionWinner.setPaymentStatus(PaymentStatus.COMPLETED);
    auctionService.markAsPaid(auction);
    ratingService.rewardBidder(winner);
    ratingService.rewardSeller(seller);

    System.out.printf("[PAYMENT] %s thanh toán thành công %.0f (còn lại: %.0f) | Seller nhận: %.0f%n",
            winner.getUsername(), finalPrice, remaining, sellerPayout);
    // TODO: auctionDAO.update(auction), userDAO.update(winner), userDAO.update(seller)
  }

  /**
   * Xử lý hết hạn thanh toán (quá 24h).
   * Phạt rating nặng, có thể tự động suspend.
   * Kích hoạt SecondChanceOffer cho runner-up.
   * TODO: userDAO.update(winner).
   *
   * @param auction phiên hết hạn thanh toán
   */
  @Override
  public void expirePayment(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    auctionWinner.setPaymentStatus(PaymentStatus.EXPIRED);
    NormalUser winner = auctionWinner.getWinner();
    ratingService.penalizeLatePayment(winner);
    System.out.printf("[PAYMENT] %s không thanh toán đúng hạn. Rating bị phạt.%n",
            winner.getUsername());

    // Hoàn cọc cho winner (vì họ không hoàn thành giao dịch)
    winner.unlockDeposit(auctionWinner.getDepositPaid());
    winner.setBalance(winner.getBalance() + auctionWinner.getDepositPaid());

    // Kích hoạt SecondChanceOffer cho runner-up
    offerSecondChance(auction);
    // TODO: userDAO.update(winner)
  }

  /**
   * Hoàn lại cọc cho tất cả người đã join phiên, trừ winner.
   * Winner sẽ dùng cọc trong quy trình thanh toán.
   * Gọi khi phiên CANCELED / RESERVE_NOT_MET / không có winner.
   *
   * @param auction phiên vừa kết thúc
   */
  @Override
  public void refundDeposits(Auction auction) {
    double depositAmount = auction.getItem().getStartingPrice() * 0.30;
    NormalUser winner = auction.getCurrentLeader();

    // Lấy tất cả bidder đã join — từ lịch sử bid transaction
    auction.getBidTransactionIds().forEach(txId -> {
      // TODO: trong thực tế query BidTransactionDAO.findById(txId).getBidder()
      // Tại đây chỉ log concept
    });

    System.out.printf("[PAYMENT] Hoàn cọc %.0f cho tất cả bidder (trừ winner %s nếu có).%n",
            depositAmount,
            winner != null ? winner.getUsername() : "không có");
    // TODO: userDAO.update tất cả bidder đã join (trừ winner)
  }

  // ── Quality Report processing ──────────────────────────────────────────

  /**
   * Xử lý khi admin phê duyệt báo cáo chất lượng.
   * 1. Trừ rating seller.
   * 2. Yêu cầu seller hoàn trả tiền trong 24h (QualityReport lưu deadline).
   * 3. Nếu seller không hoàn trả đúng hạn → gọi {@link #banSellerForRefundDefault}.
   *
   * @param report   báo cáo đã được phê duyệt
   * @param auction  phiên liên quan
   * @param seller   seller bị báo cáo
   */
  public void processApprovedQualityReport(QualityReport report,
                                           Auction auction, NormalUser seller) {
    ratingService.penalizeSeller(seller);
    auctionService.notify(auction,
            AuctionEvent.AuctionEventType.QUALITY_REPORT_APPROVED,
            report.getReporter(), auction.getWinner().getFinalPrice());
    System.out.printf("[PAYMENT] Báo cáo chất lượng duyệt: seller %s phải hoàn trả %.0f trong 24h.%n",
            seller.getUsername(), auction.getWinner().getFinalPrice());
  }

  /**
   * Seller hoàn trả tiền (khi báo cáo chất lượng được duyệt).
   * Seller phải trả đúng và đủ số tiền winner đã thanh toán.
   * Tiền chuyển: Seller → SystemBank → Winner (hoàn lại).
   *
   * @param seller  seller hoàn trả
   * @param auction phiên liên quan
   * @throws PaymentException nếu seller trả sai số tiền hoặc không đủ
   */
  public void processSellerRefund(NormalUser seller, Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);
    double        refundAmount  = auctionWinner.getFinalPrice();

    if (seller.getBalance() < refundAmount) {
      throw new PaymentException(Reason.INSUFFICIENT_BALANCE,
              String.format("Seller %s không đủ tiền hoàn trả (cần %.0f, có %.0f).",
                      seller.getUsername(), refundAmount, seller.getBalance()));
    }

    seller.setBalance(seller.getBalance() - refundAmount);
    bank.receive(refundAmount);
    NormalUser winner = auctionWinner.getWinner();
    bank.refundToWinner(refundAmount);
    winner.setBalance(winner.getBalance() + refundAmount);
    System.out.printf("[PAYMENT] Seller %s hoàn trả %.0f → Winner %s.%n",
            seller.getUsername(), refundAmount, winner.getUsername());
    // TODO: userDAO.update(seller), userDAO.update(winner)
  }

  /**
   * Ban vĩnh viễn seller khi không hoàn trả đúng hạn.
   * Gọi bởi scheduler sau 24h kể từ khi báo cáo được duyệt.
   *
   * @param seller seller không hoàn trả
   */
  public void banSellerForRefundDefault(NormalUser seller) {
    seller.setAccountStatus(NormalUser.AccountStatus.BANNED);
    System.out.printf("[PAYMENT] Seller %s bị BAN VĨNH VIỄN do không hoàn trả đúng hạn.%n",
            seller.getUsername());
    // TODO: userDAO.update(seller)
  }

  // ── Second Chance Offer ────────────────────────────────────────────────

  /**
   * Tìm runner-up và tạo SecondChanceOffer khi winner không thanh toán.
   * Runner-up là người bid cao nhất sau winner.
   * Nếu runner-up bid chưa đạt reserve price → hủy ngay (không tạo offer).
   *
   * @param auction phiên cần tạo second-chance offer
   */
  private void offerSecondChance(Auction auction) {
    // TODO: trong thực tế query BidTransactionDAO để tìm runner-up
    // Concept logic được giữ lại để implement sau
    System.out.printf("[PAYMENT] Tìm runner-up cho phiên %s để tạo SecondChanceOffer...%n",
            auction.getId());
    /*
     * Logic đúng:
     * 1. Tìm bid ACCEPTED cao nhất (trừ winner) → runner-up.
     * 2. Kiểm tra offerPrice >= reservePrice.
     *    - Nếu không đủ reserve → CANCEL, lên lịch lại auction sau 2 ngày.
     *    - Nếu đủ reserve → tạo SecondChanceOffer, notify runner-up.
     * 3. Sau 24h: nếu runner-up không quyết định → CANCEL, lên lịch lại.
     */
  }

  // ── Helpers ────────────────────────────────────────────────────────────

  /** Lấy AuctionWinner — ném exception nếu không có. */
  private AuctionWinner requireWinner(Auction auction) {
    AuctionWinner w = auction.getWinner();
    if (w == null) {
      throw new IllegalStateException("Phiên này không có winner.");
    }
    return w;
  }
}
