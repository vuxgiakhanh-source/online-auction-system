package com.group13.auction.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.group13.auction.dao.*;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.model.user.User.AccountStatus;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.iservice.IRatingService;
import com.group13.auction.unit.TestFixture;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService")
class AccountServiceTest {

  @Mock IRatingService ratingService;
  @Mock UserDAO userDAO;
  @Mock SellerDAO sellerDAO;
  @Mock AdminDAO adminDAO;
  @Mock AuctionDAO auctionDAO;
  @Mock AuctionWinnerDAO auctionWinnerDAO;
  @Mock NotificationDAO notificationDAO;
  @Mock AccountBanDAO accountBanDAO;

  AccountService sut;
  Admin staffAdmin;

  @BeforeEach
  void setUp() throws Exception {
    TestFixture.bootstrapSystemAdmin();
    sut =
        new AccountService(
            ratingService,
            userDAO,
            sellerDAO,
            adminDAO,
            auctionDAO,
            auctionWinnerDAO,
            notificationDAO,
            accountBanDAO);
    staffAdmin =
        Admin.reconstitute(
            UUID.randomUUID().toString(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            "staffAdmin",
            User.hashPassword("adminPass1"),
            "staff@test.com",
            AccountStatus.ACTIVE,
            5.0,
            Admin.LEVEL_STAFF,
            null);
  }

  @AfterEach
  void tearDown() throws Exception {
    TestFixture.resetSystemAdmin();
  }

  @Test
  void deposit_increasesBalanceAndPersists() {
    NormalUser user = TestFixture.bidderWithBalance("depUser", 500_000L);
    when(ratingService.isWalletOperationAllowed(user)).thenReturn(true);
    when(userDAO.findUserCoreByUsername("depUser"))
        .thenReturn(TestFixture.bidderWithBalance("depUser", 800_000L));
    sut.deposit(user, 300_000L);
    assertEquals(800_000L, user.getBalance());
    verify(userDAO).addBalance(user.getId(), 300_000L);
  }

  @Test
  void deposit_invalidAmount_throws() {
    NormalUser user = TestFixture.bidderWithBalance("depBad", 500_000L);
    when(ratingService.isWalletOperationAllowed(user)).thenReturn(true);
    assertThrows(IllegalArgumentException.class, () -> sut.deposit(user, 0L));
    verify(userDAO, never()).addBalance(anyString(), anyLong());
  }

  @Test
  void withdraw_reducesBalanceAndPersists() {
    NormalUser user = TestFixture.bidderWithBalance("wdUser", 1_000_000L);
    when(ratingService.isWalletOperationAllowed(user)).thenReturn(true);
    sut.withdraw(user, 400_000L);
    assertEquals(600_000L, user.getBalance());
    verify(userDAO).updateBalances(user.getId(), 600_000L, user.getLockedDeposit());
  }

  @Test
  void withdraw_insufficientAvailable_throws() {
    NormalUser user = TestFixture.bidderWithBalance("wdLow", 1_000_000L);
    user.lockDeposit(800_000L);
    when(ratingService.isWalletOperationAllowed(user)).thenReturn(true);
    assertThrows(IllegalArgumentException.class, () -> sut.withdraw(user, 300_000L));
  }

  @Test
  void banUser_setsBannedAndPersists() {
    NormalUser target = TestFixture.normalBidder("banTarget");
    sut.banUser(staffAdmin, target, Admin.BanReason.LOW_RATING);
    assertEquals(AccountStatus.BANNED, target.getAccountStatus());
    verify(userDAO).updateAccountStatus(target.getId(), AccountStatus.BANNED.name());
    verify(accountBanDAO)
        .insertBan(
            eq(target.getId()),
            eq(staffAdmin.getId()),
            eq(staffAdmin.getUsername()),
            eq("LOW_RATING"),
            isNull());
  }

  @Test
  void banUser_nullReason_throws() {
    NormalUser target = TestFixture.normalBidder("banNull");
    assertThrows(IllegalArgumentException.class, () -> sut.banUser(staffAdmin, target, null));
  }

  @Test
  void autoApproveSellerRole_eligible_addsRole() {
    NormalUser user = TestFixture.normalBidder("sellerOk");
    when(ratingService.isEligible(user)).thenReturn(true);
    sut.autoApproveSellerRole(user);
    assertTrue(user.hasRole(User.UserRole.SELLER));
    verify(sellerDAO).approveSellerRole(user.getId());
  }

  @Test
  void autoApproveSellerRole_penalized_throws() {
    NormalUser user = TestFixture.penalizedBidder("sellerBad");
    when(ratingService.isEligible(user)).thenReturn(true);
    assertThrows(IllegalStateException.class, () -> sut.autoApproveSellerRole(user));
  }
}
