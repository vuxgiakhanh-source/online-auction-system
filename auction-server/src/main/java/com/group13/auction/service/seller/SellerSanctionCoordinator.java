package com.group13.auction.service.seller;

import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.QualityReportDAO;
import com.group13.auction.dao.SecondChanceOfferDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.auction.SecondChanceOffer;
import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.model.user.User.UserRole;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.service.iservice.IAuctionService;
import com.group13.auction.service.iservice.IPaymentService;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xử lý bất đồng bộ khi Seller bị {@link AccountStatus#BANNED} hoặc {@link
 * AccountStatus#SUSPENDED}: hủy phiên OPEN/RUNNING, hoàn cọc, giải phóng quality report PENDING,
 * hủy Second Chance PENDING.
 *
 * <p>Tách khỏi {@link com.group13.auction.service.AccountService} để tránh tight coupling — các
 * điểm đổi trạng thái chỉ gọi {@link #onAccountSanctioned(User, AccountStatus)}.
 */
public class SellerSanctionCoordinator {

  private static final Logger log = LoggerFactory.getLogger(SellerSanctionCoordinator.class);

  private static volatile SellerSanctionCoordinator instance;

  private final IAuctionService auctionService;
  private final IPaymentService paymentService;
  private final AuctionDAO auctionDAO;
  private final QualityReportDAO qualityReportDAO;
  private final SecondChanceOfferDAO secondChanceOfferDAO;
  private final ExecutorService worker;

  public SellerSanctionCoordinator(
      IAuctionService auctionService,
      IPaymentService paymentService,
      AuctionDAO auctionDAO,
      QualityReportDAO qualityReportDAO,
      SecondChanceOfferDAO secondChanceOfferDAO) {
    this.auctionService = auctionService;
    this.paymentService = paymentService;
    this.auctionDAO = auctionDAO;
    this.qualityReportDAO = qualityReportDAO;
    this.secondChanceOfferDAO = secondChanceOfferDAO;
    this.worker =
        Executors.newSingleThreadExecutor(
            new ThreadFactory() {
              private final AtomicInteger n = new AtomicInteger();

              @Override
              public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "seller-sanction-worker-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
              }
            });
  }

  public static void initialize(
      IAuctionService auctionService,
      IPaymentService paymentService,
      AuctionDAO auctionDAO,
      QualityReportDAO qualityReportDAO,
      SecondChanceOfferDAO secondChanceOfferDAO) {
    instance =
        new SellerSanctionCoordinator(
            auctionService, paymentService, auctionDAO, qualityReportDAO, secondChanceOfferDAO);
  }

  public static SellerSanctionCoordinator getInstance() {
    return instance;
  }

  /**
   * Kích hoạt workflow nếu user là Seller và vừa chuyển sang BANNED/SUSPENDED. Fire-and-forget —
   * không block luồng ban/suspend.
   */
  public void onAccountSanctioned(User user, AccountStatus newStatus) {
    if (instance == null || user == null) {
      return;
    }
    if (newStatus != AccountStatus.BANNED && newStatus != AccountStatus.SUSPENDED) {
      return;
    }
    if (!(user instanceof NormalUser normalUser)) {
      return;
    }
    if (!normalUser.hasRole(UserRole.SELLER)) {
      return;
    }
    worker.submit(() -> runSanctionWorkflow(normalUser, newStatus));
  }

  private void runSanctionWorkflow(NormalUser seller, AccountStatus status) {
    log.info(
        "Seller sanction workflow started: userId={}, username={}, status={}",
        seller.getId(),
        seller.getUsername(),
        status);
    try {
      cancelOpenAuctions(seller);
      dismissPendingQualityReports(seller);
      expirePendingSecondChanceOffers(seller);
      invalidateOnlineSession(seller.getId());
    } catch (Exception e) {
      log.error("Seller sanction workflow failed: userId={}, status={}", seller.getId(), status, e);
    }
    log.info("Seller sanction workflow finished: userId={}, status={}", seller.getId(), status);
  }

  private void cancelOpenAuctions(NormalUser seller) {
    List<String> auctionIds = auctionDAO.findUnfinishedAuctionIdsBySellerId(seller.getId());
    for (String auctionId : auctionIds) {
      Auction auction = AuctionManager.getInstance().findAuctionById(auctionId);
      if (auction == null) {
        continue;
      }
      Auction.AuctionStatus st = auction.getStatus();
      if (st != Auction.AuctionStatus.OPEN && st != Auction.AuctionStatus.RUNNING) {
        continue;
      }
      try {
        auctionService.cancelAuction(auction, Admin.CancelReason.SELLER_SANCTIONED);
        paymentService.refundDeposits(auction);
        log.info(
            "Sanction cancel auction: auctionId={}, seller={}", auctionId, seller.getUsername());
      } catch (Exception e) {
        log.warn(
            "Sanction cancel auction failed: auctionId={}, seller={}",
            auctionId,
            seller.getUsername(),
            e);
      }
    }
  }

  /** Đóng hàng đợi admin — chỉ cập nhật DB, không phạt/hoàn tiền lại (seller đã bị xử lý). */
  private void dismissPendingQualityReports(NormalUser seller) {
    for (QualityReport report : qualityReportDAO.findBySellerId(seller.getId())) {
      if (report.getStatus() != QualityReport.ReportStatus.PENDING) {
        continue;
      }
      try {
        report.approve();
        qualityReportDAO.updateReport(report);
        log.info(
            "Sanction auto-resolved quality report: reportId={}, auctionId={}",
            report.getId(),
            report.getAuctionId());
      } catch (Exception e) {
        log.warn("Sanction quality report resolve failed: reportId={}", report.getId(), e);
      }
    }
  }

  private void expirePendingSecondChanceOffers(NormalUser seller) {
    List<SecondChanceOffer> offers =
        secondChanceOfferDAO.findPendingOffersBySellerId(seller.getId());
    for (SecondChanceOffer offer : offers) {
      if (offer.getStatus() != SecondChanceOffer.OfferStatus.PENDING) {
        continue;
      }
      try {
        offer.setStatus(SecondChanceOffer.OfferStatus.EXPIRED);
        secondChanceOfferDAO.updateOfferStatus(offer.getId(), offer.getStatus().name());
        log.info(
            "Sanction expired second-chance offer: offerId={}, auctionId={}",
            offer.getId(),
            offer.getAuctionId());
      } catch (Exception e) {
        log.warn("Sanction second-chance expire failed: offerId={}", offer.getId(), e);
      }
    }
  }

  private void invalidateOnlineSession(String userId) {
    ClientSession session = SessionManager.getInstance().getByUserId(userId);
    if (session != null) {
      session.invalidateCachedUser();
    }
  }

  /** Chỉ dùng trong test / shutdown. */
  void shutdown() {
    worker.shutdown();
  }
}
