package com.group13.auction.unit.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.group13.auction.bank.SystemBank;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.AuctionWinnerDAO;
import com.group13.auction.dao.FinancialTransactionDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Art;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.SystemAdmin;
import com.group13.auction.observer.AuctionEvent;
import com.group13.auction.observer.AuctionObserver;
import com.group13.auction.service.AuctionService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.unit.TestFixture;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit test cho {@link AuctionService} — lifecycle chính. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuctionService")
class AuctionServiceTest {

  @Mock private IRatingService ratingService;
  @Mock private AuctionDAO auctionDAO;
  @Mock private FinancialTransactionDAO financialTransactionDAO;
  @Mock private AuctionWinnerDAO auctionWinnerDAO;

  private AuctionService sut;
  private NormalUser seller;
  private NormalUser bidder;
  private Art item;

  @BeforeEach
  void setUp() throws Exception {
    bootstrapSystemAdmin();
    resetSystemBankBalance();
    resetAuctionManager();

    sut = new AuctionService(ratingService, auctionDAO, financialTransactionDAO, auctionWinnerDAO);
    lenient().when(auctionDAO.createAuction(any())).thenReturn(true);
    lenient().when(financialTransactionDAO.saveTransaction(any())).thenReturn(true);
    lenient()
        .when(financialTransactionDAO.findLockedDepositAmount(anyString(), anyString()))
        .thenReturn(0L);
    lenient().when(auctionWinnerDAO.saveWinner(any())).thenReturn(true);

    seller = TestFixture.normalSeller("aucSeller1");
    bidder = TestFixture.bidderWithBalance("aucBidder1", 10_000_000L);
    item = TestFixture.art("Tranh Test", 1_000_000L, seller);
  }

  @AfterEach
  void tearDown() throws Exception {
    resetSystemAdmin();
  }

  @Nested
  @DisplayName("createAuction")
  class CreateAuction {

    @Test
    void happyPath_openAuctionPersisted() {
      when(ratingService.canSellerCreateAuction(seller)).thenReturn(true);
      LocalDateTime start = LocalDateTime.now().plusMinutes(10);
      LocalDateTime end = start.plusHours(2);

      Auction result = sut.createAuction(seller, item, start, end, 2_000_000L);

      assertThat(result.getStatus()).isEqualTo(Auction.AuctionStatus.OPEN);
      verify(auctionDAO).createAuction(result);
      assertThat(AuctionManager.getInstance().findAuctionById(result.getId())).isSameAs(result);
    }

    @Test
    void ratingDenied_throws() {
      when(ratingService.canSellerCreateAuction(seller)).thenReturn(false);
      LocalDateTime start = LocalDateTime.now().plusMinutes(10);
      LocalDateTime end = start.plusHours(2);

      assertThatThrownBy(() -> sut.createAuction(seller, item, start, end, 2_000_000L))
          .isInstanceOf(IllegalStateException.class);
      verify(auctionDAO, never()).createAuction(any());
    }
  }

  @Nested
  @DisplayName("startAuction")
  class StartAuction {

    @Test
    void openToRunning() {
      Auction auction = TestFixture.openAuction(seller, 1_000_000L);
      sut.startAuction(auction);
      assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.RUNNING);
      verify(auctionDAO).updateAuctionStatus(auction.getId(), Auction.AuctionStatus.RUNNING.name());
    }

    @Test
    void alreadyRunning_throws() {
      Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
      assertThatThrownBy(() -> sut.startAuction(auction)).isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("closeAuction")
  class CloseAuction {

    @Test
    void noLeader_canceled() {
      Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
      sut.closeAuction(auction);
      assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
      verify(auctionWinnerDAO, never()).saveWinner(any());
    }

    @Test
    void reserveNotMet_canceled() {
      Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
      auction.updateBid(1_500_000L, bidder);
      sut.closeAuction(auction);
      assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.CANCELED);
    }

    @Test
    void reserveMet_finishedAndSaveWinner() {
      Auction auction = TestFixture.runningAuction(seller, 1_000_000L);
      auction.updateBid(3_000_000L, bidder);
      sut.closeAuction(auction);
      assertThat(auction.getStatus()).isEqualTo(Auction.AuctionStatus.FINISHED);
      verify(auctionWinnerDAO).saveWinner(any());
      verify(auctionDAO).updateAuctionResult(auction);
    }
  }

  @Test
  @DisplayName("startAuction notifies observers")
  void startAuction_notifiesObservers() {
    Auction auction = TestFixture.openAuction(seller, 1_000_000L);
    AuctionObserver observer = mock(AuctionObserver.class);
    sut.addObserver(auction.getId(), observer);

    sut.startAuction(auction);

    ArgumentCaptor<AuctionEvent> captor = ArgumentCaptor.forClass(AuctionEvent.class);
    verify(observer).onAuctionEnded(captor.capture());
    assertThat(captor.getValue().getEventType())
        .isEqualTo(AuctionEvent.AuctionEventType.AUCTION_STARTED);
  }

  private static void bootstrapSystemAdmin() throws Exception {
    Field f = SystemAdmin.class.getDeclaredField("INSTANCE");
    f.setAccessible(true);
    if (f.get(null) == null) {
      var ctor = SystemAdmin.class.getDeclaredConstructor(String.class, String.class, String.class);
      ctor.setAccessible(true);
      f.set(null, ctor.newInstance("SYSTEM", "test-password", "system@test.com"));
    }
  }

  private static void resetSystemAdmin() throws Exception {
    Field f = SystemAdmin.class.getDeclaredField("INSTANCE");
    f.setAccessible(true);
    f.set(null, null);
  }

  private static void resetSystemBankBalance() throws Exception {
    Field field = SystemBank.class.getDeclaredField("totalBalance");
    field.setAccessible(true);
    ((AtomicLong) field.get(SystemBank.getInstance())).set(0L);
  }

  private static void resetAuctionManager() throws Exception {
    AuctionManager mgr = AuctionManager.getInstance();
    Field auctions = AuctionManager.class.getDeclaredField("allAuctions");
    auctions.setAccessible(true);
    ((java.util.Map<?, ?>) auctions.get(mgr)).clear();
    Field global = AuctionManager.class.getDeclaredField("globalObservers");
    global.setAccessible(true);
    ((java.util.List<?>) global.get(mgr)).clear();
    Field staff = AuctionManager.class.getDeclaredField("staffObservers");
    staff.setAccessible(true);
    ((java.util.List<?>) staff.get(mgr)).clear();
  }
}
