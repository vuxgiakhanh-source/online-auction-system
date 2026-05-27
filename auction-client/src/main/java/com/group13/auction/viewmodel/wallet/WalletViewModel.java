package com.group13.auction.viewmodel.wallet;

/** Dữ liệu ví đã được format để hiển thị trên giao diện JavaFX. */
public final class WalletViewModel {

  private final long balance;
  private final long lockedDeposit;
  private final long availableBalance;
  private final String balanceText;
  private final String lockedDepositText;
  private final String availableBalanceText;

  /**
   * Tạo view model cho ví người dùng.
   *
   * @param balance tổng số dư
   * @param lockedDeposit khoản đang bị giữ làm đặt cọc
   * @param availableBalance số dư có thể sử dụng
   * @param balanceText tổng số dư đã format
   * @param lockedDepositText khoản đặt cọc đã format
   * @param availableBalanceText số dư khả dụng đã format
   */
  public WalletViewModel(
      long balance,
      long lockedDeposit,
      long availableBalance,
      String balanceText,
      String lockedDepositText,
      String availableBalanceText) {
    this.balance = balance;
    this.lockedDeposit = lockedDeposit;
    this.availableBalance = availableBalance;
    this.balanceText = balanceText;
    this.lockedDepositText = lockedDepositText;
    this.availableBalanceText = availableBalanceText;
  }

  public long balance() {
    return balance;
  }

  public long lockedDeposit() {
    return lockedDeposit;
  }

  public long availableBalance() {
    return availableBalance;
  }

  public String balanceText() {
    return balanceText;
  }

  public String lockedDepositText() {
    return lockedDepositText;
  }

  public String availableBalanceText() {
    return availableBalanceText;
  }
}
