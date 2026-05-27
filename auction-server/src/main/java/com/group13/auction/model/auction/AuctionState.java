package com.group13.auction.model.auction;

/**
 * Interface State Pattern chuyên biệt cho vòng đời phiên đấu giá.
 *
 * <p>Mỗi trạng thái tự biết nó có thể chuyển sang trạng thái nào tiếp theo, và tự ném {@link
 * IllegalStateException} nếu chuyển trạng thái không hợp lệ.
 *
 * <p>Lớp {@link Auction} chỉ cần gọi {@code state.start()}, {@code state.close()}, {@code
 * state.cancel()}, {@code state.markPaid()} mà không cần quan tâm trạng thái hiện tại là gì. Sẽ
 * phát triển (EXTENDED, DISPUTED...)
 */
public interface AuctionState {

  /**
   * Chuyển sang trạng thái RUNNING (OPEN -> RUNNING).
   *
   * @return trạng thái mới sau khi chuyển
   * @throws IllegalStateException nếu trạng thái hiện tại không cho phép
   */
  AuctionState start();

  /**
   * Đóng phiên khi hết giờ (RUNNING -> FINISHED hoặc -> CANCELED).
   *
   * @param hasWinner true nếu có currentLeader và reserve đã đạt
   * @return trạng thái mới sau khi chuyển
   * @throws IllegalStateException nếu trạng thái hiện tại không cho phép
   */
  AuctionState close(boolean hasWinner);

  /**
   * Hủy phiên (OPEN / RUNNING -> CANCELED).
   *
   * @return trạng thái CANCELED
   * @throws IllegalStateException nếu trạng thái hiện tại không cho phép
   */
  AuctionState cancel();

  /**
   * Đánh dấu đã thanh toán (FINISHED -> PAID).
   *
   * @return trạng thái PAID
   * @throws IllegalStateException nếu trạng thái hiện tại không cho phép
   */
  AuctionState markPaid();

  /**
   * Tên trạng thái hiện tại
   *
   * @return tên trạng thái tương ứng với {@link Auction.AuctionStatus}
   */
  Auction.AuctionStatus getStatus();
}
