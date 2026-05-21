package com.group13.auction.service.iservice;

import com.group13.auction.model.user.NormalUser;
import com.group13.auction.service.PaymentService;

/**
 * Hợp đồng quản lý tài chính và cọc tập trung.
 *
 * <p>Mọi thao tác tiền tệ trong hệ thống đấu giá đều đi qua interface này.
 * Tuân thủ DIP: các class phụ thuộc (BidService, PaymentService) nhận
 * {@code IWalletService} qua constructor, không new cứng {@code WalletService}.
 *
 * <p>Tuân thủ OCP: muốn thêm cơ chế thanh toán mới (escrow, crypto, trả góp...)
 * chỉ cần tạo implementation mới --- không sửa code hiện có.
 *
 * <p>Rollback: nếu một bước trong luồng giao dịch thất bại, toàn bộ phải được
 * rollback để tránh mất tiền của người dùng.
 */
public interface IWalletService {

    /**
     * Nạp tiền vào tài khoản NormalUser.
     *
     * @param user user cần nạp
     * @param amount số tiền (phải > 0)
     * @throws IllegalArgumentException nếu amount <= 0
     */
    void deposit(NormalUser user, long amount);

    /**
     * Rút tiền từ tài khoản NormalUser.
     *
     * <p>Chỉ rút được phần availableBalance (không rút tiền đang khóa cọc).
     * Bắt buộc rút hết trước khi xóa tài khoản.
     *
     * @param user user cần rút
     * @param amount số tiền rút (phải > 0)
     * @throws IllegalArgumentException nếu amount <= 0 hoặc vượt số dư khả dụng
     * @throws IllegalStateException nếu tài khoản không đủ điều kiện
     */
    void withdraw(NormalUser user, long amount);

    /**
     * Khóa cọc khi bidder tham gia phiên đấu giá (joinAuction).
     * Trừ trực tiếp khỏi balance và tăng lockedDeposit.
     * Ngăn dùng cùng một số tiền cọc cho nhiều phiên vượt khả năng chi trả.
     *
     * @param bidder        bidder tham gia
     * @param depositAmount số tiền cọc (30% giá khởi điểm)
     * @param auctionId     id phiên
     * @throws com.group13.auction.exception.AuctionBusinessException
     *         nếu số dư không đủ để khóa cọc
     */
    void lockDeposit(NormalUser bidder, long depositAmount, String auctionId);

    /**
     * Hoàn cọc cho bidder không thắng khi phiên kết thúc.
     * Giải phóng lockedDeposit và trả lại balance.
     *
     * @param bidder        bidder được hoàn cọc
     * @param depositAmount số tiền cọc được hoàn
     * @param auctionId     id phiên
     */
    void unlockDeposit(NormalUser bidder, long depositAmount, String auctionId);

    /**
     * Tịch thu cọc của winner không thanh toán đúng hạn.
     * Cọc bị chuyển vào {@link com.group13.auction.bank.SystemBank}.
     * Theo luật đấu giá: winner không thanh toán mất toàn bộ cọc.
     *
     * @param winner        winner vi phạm thanh toán
     * @param depositAmount số tiền cọc bị tịch thu
     * @param auctionId     id phiên
     */
    void forfeitDeposit(NormalUser winner, long depositAmount, String auctionId);

    /**
     * Phạt một phần cọc khi bidder là current leader mà tự rời phiên.
     *
     * <p>Tịch thu {@code penaltyAmount} vào SystemBank, hoàn trả phần còn lại
     * ({@code depositAmount - penaltyAmount}) về available balance.
     * Toàn bộ thực hiện trong một lock để tránh race condition.
     *
     * @param bidder        bidder bị phạt
     * @param depositAmount tổng cọc đã khóa (30% giá khởi điểm)
     * @param penaltyAmount phần bị tịch thu (thường 50% của depositAmount)
     * @param auctionId     id phiên
     */
    void partialForfeitDeposit(NormalUser bidder, long depositAmount,
                               long penaltyAmount, String auctionId);

    /**
     * Chuyển tiền từ Winner -> SystemBank (FUNDS_HELD).
     * Seller CHƯA nhận tiền - chỉ nhận qua {@link PaymentService#releaseToSeller}.
     *
     * <p>Logic:
     * <ol>
     *   <li>Trừ phần {@code remaining = finalPrice - depositPaid} từ balance winner.</li>
     *   <li>Giải phóng cọc (lockedDeposit) rồi trừ luôn khỏi balance - cọc chuyển vào bank.</li>
     *   <li>Bank ghi nhận toàn bộ {@code finalPrice}.</li>
     * </ol>
     *
     * @param winner người thắng
     * @param finalPrice giá cuối cùng
     * @param depositPaid số tiền cọc đã khóa trước
     * @param auctionId id phiên
     */
    void executePaymentToBank(
        NormalUser winner, long finalPrice, long depositPaid, String auctionId);
}