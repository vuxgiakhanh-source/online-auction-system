package com.group13.auction.service.iservice;

import com.group13.auction.model.user.NormalUser;

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
    void lockDeposit(NormalUser bidder, double depositAmount, String auctionId);

    /**
     * Hoàn cọc cho bidder không thắng khi phiên kết thúc.
     * Giải phóng lockedDeposit và trả lại balance.
     *
     * @param bidder        bidder được hoàn cọc
     * @param depositAmount số tiền cọc được hoàn
     * @param auctionId     id phiên
     */
    void unlockDeposit(NormalUser bidder, double depositAmount, String auctionId);

    /**
     * Tịch thu cọc của winner không thanh toán đúng hạn.
     * Cọc bị chuyển vào {@link com.group13.auction.bank.SystemBank}.
     * Theo luật đấu giá: winner không thanh toán mất toàn bộ cọc.
     *
     * @param winner        winner vi phạm thanh toán
     * @param depositAmount số tiền cọc bị tịch thu
     * @param auctionId     id phiên
     */
    void forfeitDeposit(NormalUser winner, double depositAmount, String auctionId);

    /**
     * Thực hiện toàn bộ luồng giao dịch thanh toán sau khi phiên FINISHED.
     * Winner → SystemBank → Seller (sau khi trừ thuế).
     *
     * <p>Nếu một bước lỗi → Rollback toàn bộ để tránh mất tiền.
     *
     * @param winner        winner thanh toán
     * @param seller        seller nhận tiền
     * @param finalPrice    giá bán cuối cùng
     * @param depositPaid   cọc winner đã khóa trước đó (được tính vào finalPrice)
     * @param auctionId     id phiên
     * @throws com.group13.auction.exception.PaymentException nếu winner không đủ số dư
     */
    void executePaymentTransaction(NormalUser winner, NormalUser seller,
                                   double finalPrice, double depositPaid, String auctionId);

    /**
     * Hoàn tiền 100% cho winner khi seller vi phạm chất lượng hàng hóa.
     * Seller hoàn phần thực nhận (sau thuế), SystemBank hoàn phần thuế đã thu.
     *
     * @param winner     winner nhận hoàn tiền
     * @param seller     seller phải hoàn tiền
     * @param finalPrice giá bán ban đầu (để tính lại thuế và payout)
     * @param auctionId  id phiên
     */
    void executeRefundToWinner(NormalUser winner, NormalUser seller,
                               double finalPrice, String auctionId);

    /**
     * Thực hiện giao dịch Second Chance Offer khi runner-up chấp nhận mua.
     * Logic tương tự winner ban đầu nhưng với offerPrice (giá bid của runner-up).
     *
     * @param runnerUp    runner-up chấp nhận
     * @param seller      seller nhận tiền
     * @param offerPrice  giá mua theo second chance (giá bid cao nhất của runner-up)
     * @param depositPaid cọc runner-up đã khóa khi joinAuction
     * @param auctionId   id phiên
     * @throws com.group13.auction.exception.PaymentException nếu runner-up không đủ số dư
     */
//    void executeSecondChancePayment(NormalUser runnerUp, NormalUser seller,
//                                    double offerPrice, double depositPaid, String auctionId);
}