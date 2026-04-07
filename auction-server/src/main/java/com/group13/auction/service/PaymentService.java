package com.group13.auction.service;

import com.group13.auction.exception.PaymentException;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.AuctionWinner;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.bid.BidTransaction;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.service.serviceInterface.IAuctionService;
import com.group13.auction.service.serviceInterface.IPaymentService;
import com.group13.auction.service.serviceInterface.IRatingService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Xử lý thanh toán sau khi phiên đấu giá kết thúc.
 *
 * <p>Luồng tiền: Winner → System → Seller.
 * Tất cả nằm trong khối logic chặt chẽ (WalletService).
 * Nếu một bước lỗi → Rollback toàn bộ.
 *
 * <p>Second Chance Offer:
 * Khi winner không thanh toán → tìm runner-up → tạo SecondChanceOffer.
 * Runner-up có 24h để quyết định. Nếu chấp nhận → kích hoạt giao dịch.
 *
 * TODO: inject PaymentDAO, SecondChanceOfferDAO.
 */
public class PaymentService implements IPaymentService {

  private final IAuctionService auctionService;
  private final IRatingService ratingService;
  private final WalletService walletService;

  /**
   * @param auctionService để markAsPaid và notify
   * @param ratingService để penalize/reward sau thanh toán
   * @param walletService để thực hiện giao dịch tài chính
   */
  public PaymentService(IAuctionService auctionService, IRatingService ratingService,
                        WalletService walletService) {
    this.auctionService = auctionService;
    this.ratingService = ratingService;
    this.walletService = walletService;
  }

  /**
   * Xử lý thanh toán sau khi phiên FINISHED (trong 24h).
   * Winner thanh toán phần còn lại → tiền vào SystemBank → bank trừ thuế → chuyển seller.
   * Tất cả trong một khối giao dịch — rollback nếu lỗi.
   *
   * @param auction phiên cần thanh toán
   * @throws IllegalStateException nếu không có winner
   * @throws PaymentException nếu không đủ số dư
   */
  @Override
  public void completePayment(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);

    if (auctionWinner.isExpired()) {
      throw new PaymentException(PaymentException.Reason.PAYMENT_EXPIRED,
              "Đã quá hạn 24h thanh toán.");
    }

    NormalUser winner = auctionWinner.getWinner();
    NormalUser seller = auction.getItem().getSeller();

    // Thực hiện giao dịch — rollback nếu lỗi (trong WalletService)
    walletService.executePaymentTransaction(
            winner, seller,
            auctionWinner.getFinalPrice(),
            auctionWinner.getDepositPaid(),
            auction.getId());

    auctionWinner.setPaymentStatus(AuctionWinner.PaymentStatus.COMPLETED);
    auctionService.markAsPaid(auction);

    // TODO: auctionWinnerDAO.update(auctionWinner)
  }

  /**
   * Xử lý hết hạn thanh toán (quá 24h).
   * Winner không thanh toán:
   * 1. Tịch thu cọc → SystemBank.
   * 2. Phạt rating winner.
   * 3. Kích hoạt Second Chance Offer cho runner-up.
   *
   * @param auction phiên hết hạn thanh toán
   */
  @Override
  public void expirePayment(Auction auction) {
    AuctionWinner auctionWinner = requireWinner(auction);

    if (!auctionWinner.isExpired()) {
      return; // chưa hết hạn
    }

    NormalUser winner = auctionWinner.getWinner();

    // 1. Tịch thu cọc vào SystemBank
    walletService.forfeitDeposit(winner, auctionWinner.getDepositPaid(), auction.getId());

    // 2. Phạt rating winner
    ratingService.penalizeLatePayment(winner);

    // 3. Auto-ban nếu rating xuống dưới ngưỡng
    SystemAdmin.getInstance().autoBanIfNeeded(winner);

    auctionWinner.setPaymentStatus(AuctionWinner.PaymentStatus.EXPIRED);

    // 4. Kích hoạt Second Chance Offer
    offerSecondChance(auction);

    System.out.printf("[PAYMENT] Winner %s không thanh toán | Cọc tịch thu | Rating phạt.%n",
            winner.getUsername());
    // TODO: auctionWinnerDAO.update(auctionWinner)
  }

  /**
   * Hoàn lại cọc cho tất cả người đã join phiên (trừ winner nếu FINISHED).
   * Gọi khi phiên kết thúc (FINISHED / CANCELED / RESERVE_NOT_MET).
   *
   * @param auction phiên vừa kết thúc
   */
  @Override
  public void refundDeposits(Auction auction) {
    String winnerId = auction.getWinner() != null
            ? auction.getWinner().getWinner().getId()
            : null;

    // TODO: trong thực tế query userDAO để lấy danh sách bidder đã join phiên này
    // Concept: với mỗi bidder đã join auction này (trừ winner)
    // walletService.unlockDeposit(bidder, depositAmount, auction.getId());
    System.out.printf("[PAYMENT] Hoàn cọc cho tất cả bidder phiên %s (trừ winner %s).%n",
            auction.getId(), winnerId != null ? winnerId : "N/A");
    // TODO: refundDeposits implementation đầy đủ khi có UserDAO
  }

  /**
   * Runner-up quyết định chấp nhận Second Chance Offer.
   *
   * <p>Logic giao dịch:
   * <ol>
   * <li>Kiểm tra runner-up đủ điều kiện (ACTIVE, rating >= threshold).</li>
   * <li>Kiểm tra offer chưa hết hạn.</li>
   * <li>Thực hiện giao dịch: runner-up trả phần còn lại (offerPrice - deposit).</li>
   * <li>Tiền đi theo luồng: runner-up → SystemBank → Seller (sau thuế).</li>
   * <li>Phiên chuyển trạng thái PAID.</li>
   * </ol>
   *
   * @param offer second chance offer
   * @param auction phiên liên quan
   * @throws IllegalStateException nếu offer không còn PENDING
   * @throws PaymentException nếu runner-up không đủ số dư
   */
  public void acceptSecondChanceOffer(SecondChanceOffer offer, Auction auction) {
    if (offer.getStatus() != SecondChanceOffer.OfferStatus.PENDING) {
      throw new IllegalStateException(
              "Second Chance Offer không còn ở trạng thái PENDING: " + offer.getStatus());
    }
    if (offer.isExpired()) {
      offer.setStatus(SecondChanceOffer.OfferStatus.EXPIRED);
      System.out.printf("[PAYMENT] Second Chance Offer hết hạn — phiên %s bị hủy.%n",
              auction.getId());
      auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
      return;
    }

    NormalUser runnerUp = offer.getRunnerUp();
    NormalUser seller = auction.getItem().getSeller();

    // Thực hiện giao dịch second chance — rollback nếu lỗi
    walletService.executeSecondChancePayment(
            runnerUp, seller,
            offer.getOfferPrice(),
            offer.getDepositPaid(),
            auction.getId());

    offer.setStatus(SecondChanceOffer.OfferStatus.ACCEPTED);
    auctionService.markAsPaid(auction);

    System.out.printf("[PAYMENT] Runner-up %s chấp nhận Second Chance Offer | Giá: %.0f%n",
            runnerUp.getUsername(), offer.getOfferPrice());
    // TODO: secondChanceOfferDAO.update(offer)
  }

  /**
   * Runner-up từ chối Second Chance Offer.
   * Hoàn cọc cho runner-up và hủy phiên.
   *
   * @param offer second chance offer
   * @param auction phiên liên quan
   */
  public void declineSecondChanceOffer(SecondChanceOffer offer, Auction auction) {
    if (offer.getStatus() != SecondChanceOffer.OfferStatus.PENDING) {
      throw new IllegalStateException(
              "Second Chance Offer không còn ở trạng thái PENDING: " + offer.getStatus());
    }
    // Hoàn cọc cho runner-up
    walletService.unlockDeposit(offer.getRunnerUp(), offer.getDepositPaid(), auction.getId());

    offer.setStatus(SecondChanceOffer.OfferStatus.DECLINED);
    auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);

    System.out.printf("[PAYMENT] Runner-up %s từ chối Second Chance Offer — phiên %s bị hủy.%n",
            offer.getRunnerUp().getUsername(), auction.getId());
    // TODO: secondChanceOfferDAO.update(offer)
  }

  /**
   * Xử lý khi Seller không hoàn tiền đúng hạn sau khi báo cáo chất lượng được duyệt.
   * Seller bị ban vĩnh viễn và hệ thống cố gắng hoàn tiền cho winner từ số dư còn lại.
   *
   * @param seller seller vi phạm
   * @param winner winner cần hoàn tiền
   * @param auction phiên liên quan
   */
  public void handleSellerRefundDefault(NormalUser seller, NormalUser winner, Auction auction) {
    // Ban seller vĩnh viễn
    seller.setAccountStatus(NormalUser.AccountStatus.BANNED);
    String log = String.format("[PAYMENT] Seller %s bị BAN VĨNH VIỄN do không hoàn trả đúng hạn.",
            seller.getUsername());
    SystemAdmin.getInstance().addActionLog(log);
    System.out.println(log);

    // Thực hiện hoàn tiền (100%) cho winner từ seller + system
    walletService.executeRefundToWinner(winner, seller, auction.getWinner().getFinalPrice(), auction.getId());
    // TODO: userDAO.update(seller)
  }

  /**
   * Xử lý báo cáo chất lượng được Admin duyệt.
   * Phạt rating seller, yêu cầu hoàn tiền trong 24h.
   *
   * @param seller seller bị báo cáo
   * @param winner winner gửi báo cáo
   */
  public void handleQualityReportApproved(NormalUser seller, NormalUser winner) {
    ratingService.penalizeSeller(seller);
    SystemAdmin.getInstance().autoBanIfNeeded(seller);
    System.out.printf("[PAYMENT] Seller %s bị phạt rating | Winner %s sẽ nhận hoàn tiền trong 24h.%n",
            seller.getUsername(), winner.getUsername());
    // TODO: gửi thông báo cho seller về deadline 24h
  }

  // ── Second Chance Offer logic ──────────────────────────────────────────────

  /**
   * Tìm runner-up và tạo SecondChanceOffer khi winner không thanh toán.
   * Runner-up là người bid ACCEPTED cao nhất sau winner.
   * Nếu runner-up bid chưa đạt reserve price → hủy phiên (không tạo offer).
   *
   * @param auction phiên cần tạo second-chance offer
   */
  private void offerSecondChance(Auction auction) {
    // Tìm runner-up từ lịch sử bid của phiên
    // TODO: trong thực tế query BidTransactionDAO.findByAuction(auction).sortedByAmount()
    // Logic đúng được implement theo dữ liệu in-memory:

    String winnerId = auction.getWinner() != null
            ? auction.getWinner().getWinner().getId() : null;

    // Tìm bid ACCEPTED cao nhất của người không phải winner
    Optional<BidTransaction> runnerUpBid = auction.getItem().getSeller()
            // placeholder — trong thực tế từ BidTransactionDAO
            .getAllAuctionIds().stream()
            .filter(id -> id.equals(auction.getId()))
            .findFirst()
            .map(id -> null); // TODO: bidTransactionDAO.findRunnerUpBid(auction, winnerId)

    // Concept logic (sẽ implement đầy đủ khi có DAO):
    System.out.printf("[PAYMENT] Tìm runner-up cho phiên %s để tạo SecondChanceOffer...%n",
            auction.getId());

    /*
     * Logic đúng:
     * 1. Tìm bid ACCEPTED cao nhất (trừ winner) → runner-up.
     * 2. Kiểm tra offerPrice >= reservePrice.
     *    - Nếu không đủ reserve → hủy phiên.
     *    - Nếu đủ reserve → tạo SecondChanceOffer, notify runner-up.
     * 3. Runner-up có 24h để quyết định:
     *    - acceptSecondChanceOffer() → giao dịch hoàn chỉnh.
     *    - declineSecondChanceOffer() → hoàn cọc runner-up, hủy phiên.
     *    - Hết 24h không phản hồi → expire, hoàn cọc runner-up, hủy phiên.
     * Cọc của runner-up đã bị lock từ khi joinAuction — không cần lock thêm.
     */
  }

  /**
   * Tạo SecondChanceOffer thực sự khi đã có runner-up hợp lệ.
   * Được gọi từ offerSecondChance() sau khi tìm được runner-up.
   *
   * @param runnerUp runner-up nhận đề nghị
   * @param auction phiên liên quan
   * @param offerPrice giá mua (= giá runner-up đã bid)
   * @param depositPaid cọc runner-up đã lock
   * @return SecondChanceOffer mới
   */
  public SecondChanceOffer createSecondChanceOffer(NormalUser runnerUp,
                                                   Auction auction, double offerPrice, double depositPaid) {

    if (offerPrice < auction.getReserveStrategy().getReservePrice()) {
      System.out.printf("[PAYMENT] Runner-up bid %.0f chưa đạt reserve — không tạo SecondChanceOffer. Hủy phiên.%n",
              offerPrice);
      auctionService.cancelAuction(auction, com.group13.auction.model.user.Admin.CancelReason.NO_WINNER);
      return null;
    }

    SecondChanceOffer offer = SecondChanceOffer.create(
            runnerUp, auction.getId(), offerPrice, depositPaid);

    // Notify runner-up qua observer
    auctionService.notify(auction, AuctionEvent.AuctionEventType.SECOND_CHANCE_OFFERED,
            runnerUp, offerPrice,
            String.format("Second Chance Offer: mua với giá %.0f trong 24h", offerPrice));

    System.out.printf("[PAYMENT] Second Chance Offer tạo cho %s | Giá: %.0f | Hạn: %s%n",
            runnerUp.getUsername(), offerPrice, offer.getDeadline());
    // TODO: secondChanceOfferDAO.save(offer)
    return offer;
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  /** Lấy AuctionWinner — ném exception nếu không có. */
  private AuctionWinner requireWinner(Auction auction) {
    AuctionWinner w = auction.getWinner();
    if (w == null) {
      throw new IllegalStateException("Phiên này không có winner.");
    }
    return w;
  }
}