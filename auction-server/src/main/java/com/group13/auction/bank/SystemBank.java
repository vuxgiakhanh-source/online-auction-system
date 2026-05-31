package com.group13.auction.bank;

import com.group13.auction.dao.SystemBankDAO;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ngân hàng hệ thống — nơi lưu thuế và trung gian chuyển tiền.
 *
 * <p>Singleton. Số dư được giữ trong RAM và đồng bộ xuống bảng {@code system_bank} sau mỗi thao
 * tác. Khi server khởi động, gọi {@link #loadFromDatabase()} để khôi phục số dư sau restart.
 */
public class SystemBank {

  private static final Logger log = LoggerFactory.getLogger(SystemBank.class);
  private static final SystemBank INSTANCE = new SystemBank();

  private static final long PRICE_TIER_LOW = 1_000_000L;
  private static final long PRICE_TIER_MID = 10_000_000L;
  private static final double TAX_RATE_LOW = 0.05;
  private static final double TAX_RATE_MID = 0.03;
  private static final double TAX_RATE_HIGH = 0.02;

  private final AtomicLong totalBalance = new AtomicLong(0L);
  private final SystemBankDAO systemBankDAO = new SystemBankDAO();

  /** Tắt khi unit test không có DB; mặc định bật trong production. */
  private volatile boolean dbPersistenceEnabled = true;

  private SystemBank() {}

  public static SystemBank getInstance() {
    return INSTANCE;
  }

  /** Chỉ dùng trong test để tránh ghi DB. */
  public void setDbPersistenceEnabled(boolean enabled) {
    this.dbPersistenceEnabled = enabled;
  }

  /** Khôi phục số dư từ DB — gọi từ {@link com.group13.auction.ServerMain} sau khi DB sẵn sàng. */
  public synchronized void loadFromDatabase() {
    systemBankDAO.ensureRowExists();
    long fromDb = systemBankDAO.loadTotalBalance();
    totalBalance.set(fromDb);
    log.info("SystemBank loaded from database: totalBalance={}", fromDb);
  }

  public long calculateTax(long salePrice) {
    double rate;
    if (salePrice < PRICE_TIER_LOW) {
      rate = TAX_RATE_LOW;
    } else if (salePrice <= PRICE_TIER_MID) {
      rate = TAX_RATE_MID;
    } else {
      rate = TAX_RATE_HIGH;
    }
    return Math.round(salePrice * rate);
  }

  public long calculateSellerPayout(long salePrice) {
    return salePrice - calculateTax(salePrice);
  }

  public synchronized void receive(long amount) {
    long current = totalBalance.addAndGet(amount);
    log.info("Bank.receive: amount={}, totalBalance={}", amount, current);
    persistBalance(current);
  }

  public synchronized long payoutToSeller(long salePrice) {
    long tax = calculateTax(salePrice);
    long payout = salePrice - tax;
    long current = totalBalance.addAndGet(-payout);
    log.info(
        "Bank.payoutToSeller: salePrice={}, tax={}, payout={}, totalBalance={}",
        salePrice,
        tax,
        payout,
        current);
    persistBalance(current);
    return payout;
  }

  public synchronized void refundToWinner(long amount) {
    long current = totalBalance.addAndGet(-amount);
    log.info("Bank.refundToWinner: amount={}, totalBalance={}", amount, current);
    persistBalance(current);
  }

  public synchronized void receiveForfeittedDeposit(long depositAmount) {
    long current = totalBalance.addAndGet(depositAmount);
    log.warn(
        "Bank.receiveForfeittedDeposit: depositAmount={}, totalBalance={}", depositAmount, current);
    persistBalance(current);
  }

  /** Số dư hiện tại (RAM, đã đồng bộ DB sau mỗi mutation nếu persistence bật). */
  public long getTotalBalance() {
    return totalBalance.get();
  }

  /** Đọc trực tiếp từ DB — dùng đối soát / admin, không thay thế hot path. */
  public long getTotalBalanceFromDatabase() {
    return systemBankDAO.loadTotalBalance();
  }

  private void persistBalance(long current) {
    if (!dbPersistenceEnabled) {
      return;
    }
    if (!systemBankDAO.saveTotalBalance(current)) {
      throw new IllegalStateException(
          "Không thể persist SystemBank balance xuống DB: totalBalance=" + current);
    }
  }
}
