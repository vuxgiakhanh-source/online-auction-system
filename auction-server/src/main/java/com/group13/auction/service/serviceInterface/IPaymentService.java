package com.group13.auction.service.serviceInterface;

import com.group13.auction.model.auction.Auction;

/**
 * Hợp đồng xử lý thanh toán sau khi phiên đấu giá kết thúc.
 *
 * <p>Đóng với sửa đổi, mở với mở rộng (OCP):
 * muốn thêm kiểu thanh toán mới (VD: trả góp, escrow, crypto...)
 * chỉ cần tạo implementation mới implement interface này —
 * không sửa code hiện có.
 */
public interface IPaymentService {

  /**
   * Xử lý thanh toán sau khi phiên FINISHED (trong 24h).
   * Winner thanh toán phần còn lại → tiền vào SystemBank → bank trừ thuế → chuyển seller.
   * Tất cả trong một khối giao dịch — rollback nếu lỗi.
   *
   * @param auction phiên cần thanh toán
   * @throws IllegalStateException nếu không có winner
   * @throws com.group13.auction.exception.PaymentException nếu không đủ số dư
   */
  void completePayment(Auction auction);

  /**
   * Xử lý hết hạn thanh toán (quá 24h).
   * Tịch thu cọc vào SystemBank.
   * Phạt rating và có thể tự động ban winner.
   * Kích hoạt quy trình second-chance offer.
   *
   * @param auction phiên hết hạn thanh toán
   */
  void expirePayment(Auction auction);

  /**
   * Hoàn lại cọc cho tất cả người đã join phiên (trừ winner).
   * Gọi khi phiên kết thúc (FINISHED / CANCELED / RESERVE_NOT_MET).
   *
   * @param auction phiên vừa kết thúc
   */
  void refundDeposits(Auction auction);
}