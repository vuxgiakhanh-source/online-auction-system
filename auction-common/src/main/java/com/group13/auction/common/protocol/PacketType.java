package com.group13.auction.common.protocol;

/**
 * Liệt kê toàn bộ loại packet truyền tải giữa Client và Server qua WebSocket.
 *
 * <p>Quy ước đặt tên:
 * <ul>
 *   <li>Không có hậu tố  → Client gửi lên Server (request).</li>
 *   <li>Hậu tố {@code _SUCCESS} → Server trả về thành công.</li>
 *   <li>Hậu tố {@code _FAILED}  → Server trả về lỗi nghiệp vụ.</li>
 *   <li>Hậu tố {@code _UPDATE}  → Server broadcast realtime tới nhiều client.</li>
 *   <li>Hậu tố {@code _NOTIFY}  → Server push đơn chiều tới 1 client cụ thể.</li>
 * </ul>
 *
 * @author Group 13 – Network Layer
 * @version 2.0
 */
public enum PacketType {

    // ══════════════════════════════════════════════════════════════════════════
    // AUTH — Đăng ký / Đăng nhập / Đăng xuất
    // ══════════════════════════════════════════════════════════════════════════

    /** Client gửi yêu cầu đăng ký tài khoản mới. Payload: {@code RegisterRequestDTO}. */
    REGISTER,

    /** Server xác nhận đăng ký thành công, trả về token + thông tin user.
     *  Payload: {@code LoginResponseDTO}. */
    REGISTER_SUCCESS,

    /** Server từ chối đăng ký (username đã tồn tại, email trùng, v.v.).
     *  Payload: {@code ErrorDTO}. */
    REGISTER_FAILED,

    /** Client gửi yêu cầu đăng nhập. Payload: {@code LoginRequestDTO}. */
    LOGIN,

    /** Server xác nhận đăng nhập thành công, trả về token + thông tin user.
     *  Payload: {@code LoginResponseDTO}. */
    LOGIN_SUCCESS,

    /** Server từ chối đăng nhập (sai mật khẩu, tài khoản bị ban, v.v.).
     *  Payload: {@code ErrorDTO}. */
    LOGIN_FAILED,

    /** Client gửi yêu cầu đăng xuất (giải phóng WebSocket session).
     *  Payload: rỗng. */
    LOGOUT,

    /** Server xác nhận đã đăng xuất thành công. Payload: rỗng. */
    LOGOUT_SUCCESS,

    // ══════════════════════════════════════════════════════════════════════════
    // USER — Quản lý tài khoản & hồ sơ
    // ══════════════════════════════════════════════════════════════════════════

    /** Client yêu cầu lấy thông tin hồ sơ của chính mình.
     *  Payload: rỗng (lấy từ session). */
    GET_MY_PROFILE,

    /** Server trả về thông tin hồ sơ. Payload: {@code UserDTO}. */
    GET_MY_PROFILE_SUCCESS,

    /** Client yêu cầu lấy thông tin hồ sơ người dùng khác theo userId.
     *  Payload: {@code String userId}. */
    GET_USER_PROFILE,

    /** Server trả về thông tin hồ sơ. Payload: {@code UserDTO}. */
    GET_USER_PROFILE_SUCCESS,

    /** Server từ chối (user không tồn tại). Payload: {@code ErrorDTO}. */
    GET_USER_PROFILE_FAILED,

    // ── Nâng cấp tài khoản ──────────────────────────────────────────────────

    /** Client gửi đơn yêu cầu nâng cấp lên Seller. Payload: rỗng. */
    REQUEST_SELLER_ROLE,

    /** Server xác nhận đã nhận đơn yêu cầu, đang chờ hệ thống auto-duyệt.
     *  Payload: rỗng. */
    REQUEST_SELLER_ROLE_SUCCESS,

    /** Server từ chối yêu cầu nâng cấp (đã từng bị phạt, đã là Seller, v.v.).
     *  Payload: {@code ErrorDTO}. */
    REQUEST_SELLER_ROLE_FAILED,

    /** Server push khi hệ thống tự động duyệt (approve) role Seller.
     *  Payload: {@code UserDTO} (đã có role SELLER). */
    SELLER_ROLE_APPROVED_NOTIFY,

    /** Server push khi hệ thống từ chối tự động duyệt role Seller.
     *  Payload: {@code ErrorDTO} với lý do. */
    SELLER_ROLE_REJECTED_NOTIFY,

    // ── Rút tiền ────────────────────────────────────────────────────────────

    /** Client yêu cầu rút tiền khỏi ví. Payload: {@code WithdrawRequestDTO}. */
    WITHDRAW,

    /** Server xác nhận rút tiền thành công, trả về số dư mới.
     *  Payload: {@code WalletBalanceResponseDTO}. */
    WITHDRAW_SUCCESS,

    /** Server từ chối rút tiền (số dư không đủ, tài khoản không hợp lệ, v.v.).
     *  Payload: {@code ErrorDTO}. */
    WITHDRAW_FAILED,

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN — Quản lý tài khoản (chỉ Admin/SystemAdmin)
    // ══════════════════════════════════════════════════════════════════════════

    /** Admin gửi lệnh ban tài khoản user. Payload: {@code AdminBanUserDTO}. */
    ADMIN_BAN_USER,

    /** Server xác nhận ban thành công. Payload: {@code UserDTO}. */
    ADMIN_BAN_USER_SUCCESS,

    /** Server từ chối ban (user không tồn tại, đã bị ban, v.v.).
     *  Payload: {@code ErrorDTO}. */
    ADMIN_BAN_USER_FAILED,

    /** Admin gửi lệnh mở khóa (unban) tài khoản. Payload: {@code String userId}. */
    ADMIN_UNBAN_USER,

    /** Server xác nhận unban thành công. Payload: {@code UserDTO}. */
    ADMIN_UNBAN_USER_SUCCESS,

    /** Server từ chối unban. Payload: {@code ErrorDTO}. */
    ADMIN_UNBAN_USER_FAILED,

    /** Admin yêu cầu danh sách toàn bộ user. Payload: rỗng. */
    ADMIN_GET_ALL_USERS,

    /** Server trả về danh sách user. Payload: {@code List<UserDTO>}. */
    ADMIN_GET_ALL_USERS_SUCCESS,

    /** Admin yêu cầu danh sách tài khoản đang bị khóa. Payload: rỗng. */
    ADMIN_GET_ACCOUNT_BANS,

    /** Server trả danh sách khóa active. Payload: {@code AccountBanDTO[]}. */
    ADMIN_GET_ACCOUNT_BANS_SUCCESS,

    /** Server từ chối lấy danh sách khóa. Payload: {@code ErrorDTO}. */
    ADMIN_GET_ACCOUNT_BANS_FAILED,

    /** SystemAdmin tạo tài khoản Admin STAFF mới. Payload: {@code CreateStaffAdminDTO}. */
    ADMIN_CREATE_STAFF,

    /** Server xác nhận tạo Staff Admin thành công. Payload: {@code UserDTO}. */
    ADMIN_CREATE_STAFF_SUCCESS,

    /** Server từ chối tạo Staff Admin. Payload: {@code ErrorDTO}. */
    ADMIN_CREATE_STAFF_FAILED,

    /** Admin yêu cầu danh sách toàn bộ Staff Admin. Payload: rỗng. */
    ADMIN_GET_ALL_STAFF,

    /** Server trả về danh sách Staff Admin. Payload: {@code List<UserDTO>}. */
    ADMIN_GET_ALL_STAFF_SUCCESS,

    /** Admin approve yêu cầu nâng cấp Seller thủ công. Payload: {@code String userId}. */
    ADMIN_APPROVE_SELLER_ROLE,

    /** Server xác nhận đã cấp role Seller. Payload: {@code UserDTO}. */
    ADMIN_APPROVE_SELLER_ROLE_SUCCESS,

    /** Server từ chối approve. Payload: {@code ErrorDTO}. */
    ADMIN_APPROVE_SELLER_ROLE_FAILED,

    // ══════════════════════════════════════════════════════════════════════════
    // DEPOSIT — Nạp tiền / Quản lý ví
    // ══════════════════════════════════════════════════════════════════════════

    /** Client yêu cầu nạp tiền vào ví. Payload: {@code DepositRequestDTO}. */
    DEPOSIT,

    /** Server xác nhận nạp tiền thành công, trả về số dư mới.
     *  Payload: {@code WalletBalanceResponseDTO}. */
    DEPOSIT_SUCCESS,

    /** Server từ chối nạp tiền (số tiền không hợp lệ, tài khoản bị khóa, v.v.).
     *  Payload: {@code ErrorDTO}. */
    DEPOSIT_FAILED,

    /** Client yêu cầu lấy số dư ví hiện tại. Payload: rỗng. */
    GET_WALLET_BALANCE,

    /** Server trả về số dư ví (balance + lockedDeposit + availableBalance).
     *  Payload: {@code WalletBalanceResponseDTO}. */
    GET_WALLET_BALANCE_SUCCESS,

    // ══════════════════════════════════════════════════════════════════════════
    // AUCTION — Quản lý phiên đấu giá
    // ══════════════════════════════════════════════════════════════════════════

    /** Client (Seller) yêu cầu tạo phiên đấu giá mới.
     *  Payload: {@code CreateAuctionRequestDTO}. */
    CREATE_AUCTION,

    /** Server xác nhận tạo phiên thành công. Payload: {@code AuctionDTO}. */
    CREATE_AUCTION_SUCCESS,

    /** Server từ chối tạo phiên (seller chưa đủ điều kiện, thông tin không hợp lệ, v.v.).
     *  Payload: {@code ErrorDTO}. */
    CREATE_AUCTION_FAILED,

    /** Client yêu cầu danh sách tất cả phiên đấu giá (có thể lọc theo status).
     *  Payload: {@code AuctionListRequestDTO} (status filter, sort, page). */
    GET_AUCTION_LIST,

    /** Server trả về danh sách phiên. Payload: {@code AuctionListDTO}. */
    GET_AUCTION_LIST_SUCCESS,

    /** Client yêu cầu chi tiết một phiên cụ thể. Payload: {@code String auctionId}. */
    GET_AUCTION_DETAIL,

    /** Server trả về chi tiết phiên. Payload: {@code AuctionDTO}. */
    GET_AUCTION_DETAIL_SUCCESS,

    /** Server từ chối (phiên không tồn tại). Payload: {@code ErrorDTO}. */
    GET_AUCTION_DETAIL_FAILED,

    /** Seller yêu cầu cập nhật thông tin phiên (chỉ khi OPEN).
     *  Payload: {@code UpdateAuctionDTO}. */
    UPDATE_AUCTION,

    /** Server xác nhận cập nhật thành công. Payload: {@code AuctionDTO}. */
    UPDATE_AUCTION_SUCCESS,

    /** Server từ chối cập nhật (phiên đã RUNNING, không phải chủ, v.v.).
     *  Payload: {@code ErrorDTO}. */
    UPDATE_AUCTION_FAILED,

    // ── Seller yêu cầu hủy phiên ────────────────────────────────────────────

    /** Seller gửi yêu cầu hủy phiên để Staff Admin xem xét.
     *  Payload: {@code CancelAuctionRequestDTO} (auctionId + reason). */
    CANCEL_AUCTION_REQUEST,

    /** Server xác nhận đã nhận đơn yêu cầu hủy.
     *  Payload: {@code String auctionId}. */
    CANCEL_AUCTION_REQUEST_SUCCESS,

    /** Server từ chối yêu cầu hủy (phiên không ở OPEN, không phải chủ phiên, v.v.).
     *  Payload: {@code ErrorDTO}. */
    CANCEL_AUCTION_REQUEST_FAILED,

    /** Server push thông báo cho Staff Admin khi có Seller yêu cầu hủy phiên.
     *  Payload: {@code SellerCancelRequestNotifyDTO}. */
    SELLER_CANCEL_REQUEST_NOTIFY,

    // ── Admin hủy phiên ─────────────────────────────────────────────────────

    /** Admin/Staff xác nhận hủy phiên (approve seller cancel request hoặc chủ động hủy).
     *  Payload: {@code AdminCancelAuctionDTO} (auctionId + reason). */
    ADMIN_CANCEL_AUCTION,

    /** Server xác nhận Admin đã hủy phiên. Payload: {@code AuctionDTO}. */
    ADMIN_CANCEL_AUCTION_SUCCESS,

    /** Server từ chối hủy (phiên đã FINISHED, không tồn tại, v.v.).
     *  Payload: {@code ErrorDTO}. */
    ADMIN_CANCEL_AUCTION_FAILED,

    /** Admin yêu cầu danh sách tất cả phiên đấu giá để quản lý.
     *  Payload: {@code AuctionListRequestDTO}. */
    ADMIN_GET_ALL_AUCTIONS,

    /** Server trả về danh sách phiên đấu giá. Payload: {@code AuctionListDTO}. */
    ADMIN_GET_ALL_AUCTIONS_SUCCESS,

    // ══════════════════════════════════════════════════════════════════════════
    // BID — Đấu giá thủ công
    // ══════════════════════════════════════════════════════════════════════════

    /** Client yêu cầu tham gia phiên đấu giá (đóng cọc + nhận realtime updates).
     *  Payload: {@code String auctionId}. */
    JOIN_AUCTION,

    /** Server xác nhận đã join thành công, cọc đã bị lock.
     *  Payload: {@code JoinAuctionResponseDTO} (AuctionDTO + depositAmount). */
    JOIN_AUCTION_SUCCESS,

    /** Server từ chối join (không đủ số dư, đã join, phiên không RUNNING, v.v.).
     *  Payload: {@code ErrorDTO}. */
    JOIN_AUCTION_FAILED,

    /** Client yêu cầu theo dõi phiên (xem realtime, không đặt cọc).
     *  Payload: {@code String auctionId}. */
    WATCH_AUCTION,

    /** Server xác nhận đã watch thành công.
     *  Payload: {@code AuctionDTO} (trạng thái phiên hiện tại). */
    WATCH_AUCTION_SUCCESS,

    /** Server từ chối watch. Payload: {@code ErrorDTO}. */
    WATCH_AUCTION_FAILED,

    /** Client ngừng theo dõi phiên (rời khỏi view).
     *  Payload: {@code String auctionId}. */
    LEAVE_AUCTION,

    /** Server xác nhận đã rời phiên. Payload: rỗng. */
    LEAVE_AUCTION_SUCCESS,

    /** Client đặt một giá thủ công. Payload: {@code BidRequestDTO}. */
    PLACE_BID,

    /** Server xác nhận bid được chấp nhận. Payload: {@code BidResultDTO}. */
    PLACE_BID_SUCCESS,

    /** Server từ chối bid (giá thấp hơn hiện tại, phiên đã đóng, chưa join, v.v.).
     *  Payload: {@code ErrorDTO}. */
    PLACE_BID_FAILED,

    // ══════════════════════════════════════════════════════════════════════════
    // AUTO-BID — Đấu giá tự động
    // ══════════════════════════════════════════════════════════════════════════

    /** Client đăng ký Auto-Bid (maxBid).
     *  Payload: {@code AutoBidRequestDTO}. */
    REGISTER_AUTO_BID,

    /** Server xác nhận đăng ký Auto-Bid thành công.
     *  Payload: {@code AutoBidRegistrationDTO}. */
    REGISTER_AUTO_BID_SUCCESS,

    /** Server từ chối đăng ký Auto-Bid (maxBid <= giá hiện tại, chưa join, v.v.).
     *  Payload: {@code ErrorDTO}. */
    REGISTER_AUTO_BID_FAILED,

    /** Client cập nhật maxBid của Auto-Bid đang hoạt động.
     *  Payload: {@code AutoBidRequestDTO} (maxBid mới). */
    UPDATE_AUTO_BID,

    /** Server xác nhận đã cập nhật maxBid. Payload: {@code AutoBidRegistrationDTO}. */
    UPDATE_AUTO_BID_SUCCESS,

    /** Server từ chối cập nhật maxBid. Payload: {@code ErrorDTO}. */
    UPDATE_AUTO_BID_FAILED,

    /** Client hủy Auto-Bid đang hoạt động. Payload: {@code String auctionId}. */
    CANCEL_AUTO_BID,

    /** Server xác nhận đã hủy Auto-Bid. Payload: {@code String auctionId}. */
    CANCEL_AUTO_BID_SUCCESS,

    /** Server từ chối hủy Auto-Bid (không có auto-bid nào đang chạy, v.v.).
     *  Payload: {@code ErrorDTO}. */
    CANCEL_AUTO_BID_FAILED,

    /** Client yêu cầu xem trạng thái Auto-Bid hiện tại của mình trong phiên.
     *  Payload: {@code String auctionId}. */
    GET_AUTO_BID_STATUS,

    /** Server trả về trạng thái Auto-Bid. Payload: {@code AutoBidRegistrationDTO}. */
    GET_AUTO_BID_STATUS_SUCCESS,

    /**
     * Server push thông báo khi hệ thống tự động đặt giá thay client.
     * Payload: {@code AutoBidTriggeredDTO}.
     */
    AUTO_BID_TRIGGERED_NOTIFY,

    /**
     * Server push thông báo khi Auto-Bid của client bị vượt qua bởi đối thủ
     * và đã đạt maxBid (không thể tự bid tiếp).
     * Payload: {@code AutoBidExhaustedDTO}.
     */
    AUTO_BID_EXHAUSTED_NOTIFY,

    // ══════════════════════════════════════════════════════════════════════════
    // REALTIME UPDATE — Server push sự kiện tới client đang xem phiên
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Server broadcast khi có bid mới hợp lệ và reserve price đã đạt.
     * Tất cả client đang watch/join phiên đó đều nhận packet này.
     * Payload: {@code BidUpdateDTO}.
     */
    BID_UPDATE,

    /**
     * Server broadcast khi so nguoi dang xem phien thay doi (join / watch / leave / disconnect).
     * Payload: {@code ViewerCountUpdateDTO}.
     */
    VIEWER_COUNT_UPDATE,

    /**
     * Server broadcast khi có bid hợp lệ nhưng chưa đạt reserve price.
     * Payload: {@code BidUpdateDTO}.
     */
    BID_RESERVE_NOT_MET_UPDATE,

    /**
     * Server broadcast khi phiên chuyển sang trạng thái RUNNING.
     * Payload: {@code AuctionUpdateDTO}.
     */
    AUCTION_STARTED_UPDATE,

    /**
     * Server broadcast khi phiên sắp kết thúc (cảnh báo 5 phút, 1 phút).
     * Payload: {@code AuctionUpcomingEndDTO} (auctionId + remainingSeconds).
     */
    AUCTION_UPCOMING_END_NOTIFY,

    /**
     * Server broadcast khi phiên kết thúc (FINISHED) — có người thắng và đã đạt reserve.
     * Payload: {@code AuctionUpdateDTO} (chứa winner + finalPrice).
     */
    AUCTION_ENDED_UPDATE,

    /**
     * Server push khi phiên kết thúc không có ai đặt giá (CANCELED, no_winner).
     * Payload: {@code AuctionUpdateDTO}.
     */
    AUCTION_NO_WINNER_UPDATE,

    /**
     * Server push khi phiên kết thúc nhưng chưa đạt reserve price (RESERVE_NOT_MET).
     * Payload: {@code AuctionUpdateDTO}.
     */
    AUCTION_RESERVE_NOT_MET_UPDATE,

    /**
     * Server push khi phiên bị hủy (CANCELED) bởi Admin hoặc system.
     * Payload: {@code AuctionUpdateDTO}.
     */
    AUCTION_CANCELED_UPDATE,

    // ── Anti-sniping ─────────────────────────────────────────────────────────

    /**
     * Server push thông báo khi phiên được gia hạn do có bid trong giây cuối (Anti-sniping).
     * Tất cả client đang xem phiên đều nhận packet này.
     * Payload: {@code AuctionExtendedDTO} (chứa extendedEndTime mới).
     */
    AUCTION_EXTENDED_NOTIFY,

    // ══════════════════════════════════════════════════════════════════════════
    // PAYMENT — Thanh toán sau đấu giá
    // ══════════════════════════════════════════════════════════════════════════

    /** Người thắng gửi yêu cầu thanh toán. Payload: {@code PaymentRequestDTO}. */
    PAYMENT_REQUEST,

    /** Server xác nhận thanh toán thành công (trạng thái PAID).
     *  Payload: {@code PaymentResultDTO}. */
    PAYMENT_SUCCESS,

    /** Server từ chối thanh toán (không đủ số dư, phiên chưa FINISHED, đã quá hạn, v.v.).
     *  Payload: {@code ErrorDTO}. */
    PAYMENT_FAILED,

    /**
     * Server push thông báo cho Seller khi người thắng đã thanh toán.
     * Payload: {@code PaymentResultDTO}.
     */
    PAYMENT_COMPLETED_NOTIFY,

    /**
     * Winner xác nhận đã nhận hàng — mở khóa quyền submit Quality Report.
     * Payload: {@code String auctionId}.
     */
    CONFIRM_ITEM_RECEIVED,
    /** Server xác nhận nhận hàng thành công. Payload: {@link com.group13.auction.common.dto.payment.PaymentDTOs.ConfirmItemReceivedResultDTO}. */
    CONFIRM_ITEM_RECEIVED_SUCCESS,
    /** Server từ chối — chưa thanh toán, không phải winner, hoặc đã xác nhận rồi. Payload: {@code ErrorDTO}. */
    CONFIRM_ITEM_RECEIVED_FAILED,

    /**
     * Server push thông báo cho Winner khi hết hạn thanh toán (24h).
     * Payload: {@code PaymentExpiredDTO} (auctionId + depositForfeited).
     */
    PAYMENT_EXPIRED_NOTIFY,

    // ── Hoàn cọc ─────────────────────────────────────────────────────────────

    /**
     * Server push thông báo hoàn cọc cho Bidder thua phiên.
     * Payload: {@code DepositRefundDTO} (auctionId + refundAmount + newBalance).
     */
    DEPOSIT_REFUND_NOTIFY,

    /**
     * Server push thông báo tịch thu cọc khi Winner không thanh toán đúng hạn.
     * Payload: {@code DepositForfeitedDTO} (auctionId + forfeitedAmount).
     */
    DEPOSIT_FORFEITED_NOTIFY,

    // ══════════════════════════════════════════════════════════════════════════
    // SECOND CHANCE — Đề nghị mua thứ cấp (runner-up)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Server push đề nghị Second Chance Offer tới runner-up.
     * Payload: {@code SecondChanceOfferDTO}.
     */
    SECOND_CHANCE_OFFER_NOTIFY,

    /** Runner-up chấp nhận Second Chance Offer.
     *  Payload: {@code String auctionId}. */
    SECOND_CHANCE_ACCEPT,

    /** Server xác nhận chấp nhận Second Chance thành công.
     *  Payload: {@code PaymentResultDTO} (trạng thái PENDING — cần thanh toán tiếp). */
    SECOND_CHANCE_ACCEPT_SUCCESS,

    /** Server từ chối chấp nhận (offer đã hết hạn, offer không PENDING, v.v.).
     *  Payload: {@code ErrorDTO}. */
    SECOND_CHANCE_ACCEPT_FAILED,

    /** Runner-up từ chối Second Chance Offer.
     *  Payload: {@code String auctionId}. */
    SECOND_CHANCE_DECLINE,

    /** Server xác nhận đã ghi nhận từ chối. Payload: rỗng. */
    SECOND_CHANCE_DECLINE_SUCCESS,

    /**
     * Server push thông báo Second Chance Offer đã hết hạn (không phản hồi trong 24h).
     * Payload: {@code String auctionId}.
     */
    SECOND_CHANCE_EXPIRED_NOTIFY,

    /**
     * Broadcast tới tất cả watcher của phiên khi runner-up chấp nhận Second Chance Offer.
     * Phiên có winner mới — client cần cập nhật UI hiển thị winner.
     * Payload: {@link com.group13.auction.common.dto.auction.AuctionDTOs.AuctionUpdateDTO}.
     */
    SECOND_CHANCE_ACCEPTED_UPDATE,

    // ══════════════════════════════════════════════════════════════════════════
    // BID HISTORY — Lịch sử đấu giá (cho biểu đồ realtime)
    // ══════════════════════════════════════════════════════════════════════════

    /** Client yêu cầu toàn bộ lịch sử bid của một phiên (dùng để khởi tạo line chart).
     *  Payload: {@code String auctionId}. */
    GET_BID_HISTORY,

    /** Server trả về lịch sử bid. Payload: {@code BidHistoryResponseDTO}. */
    GET_BID_HISTORY_SUCCESS,

    /** Server từ chối (phiên không tồn tại). Payload: {@code ErrorDTO}. */
    GET_BID_HISTORY_FAILED,

    /**
     * Server push một điểm dữ liệu mới lên biểu đồ realtime khi có bid hợp lệ.
     * Client append điểm mới vào chart mà không cần refresh.
     * Payload: {@code BidChartPointDTO} (timestamp + price + bidderName).
     */
    BID_CHART_POINT_UPDATE,

    // ══════════════════════════════════════════════════════════════════════════
    // QUALITY REPORT — Báo cáo chất lượng sản phẩm
    // ══════════════════════════════════════════════════════════════════════════

    /** Bidder thắng gửi báo cáo chất lượng sản phẩm.
     *  Payload: {@code QualityReportRequestDTO}. */
    SUBMIT_QUALITY_REPORT,

    /** Server xác nhận nhận báo cáo (PENDING).
     *  Payload: {@code QualityReportDTO}. */
    SUBMIT_QUALITY_REPORT_SUCCESS,

    /** Server từ chối nhận báo cáo (không phải winner, phiên chưa PAID, v.v.).
     *  Payload: {@code ErrorDTO}. */
    SUBMIT_QUALITY_REPORT_FAILED,

    /** Admin yêu cầu danh sách báo cáo chất lượng theo bộ lọc trạng thái.
     *  Payload: {@code String} — {@code PENDING} | {@code APPROVED} | {@code REJECTED} | {@code ALL}. */
    ADMIN_GET_QUALITY_REPORTS,

    /** Server trả về danh sách báo cáo. Payload: {@code List<QualityReportDTO>}. */
    ADMIN_GET_QUALITY_REPORTS_SUCCESS,

    /** Admin approve báo cáo chất lượng → trừ rating seller + hoàn tiền winner.
     *  Payload: {@code String reportId}. */
    ADMIN_APPROVE_QUALITY_REPORT,

    /** Server xác nhận đã approve báo cáo, seller bị phạt.
     *  Payload: {@code QualityReportResultDTO}. */
    ADMIN_APPROVE_QUALITY_REPORT_SUCCESS,

    /** Server từ chối approve (report không PENDING, v.v.).
     *  Payload: {@code ErrorDTO}. */
    ADMIN_APPROVE_QUALITY_REPORT_FAILED,

    /** Admin reject báo cáo chất lượng. Payload: {@code String reportId}. */
    ADMIN_REJECT_QUALITY_REPORT,

    /** Server xác nhận đã reject báo cáo. Payload: rỗng. */
    ADMIN_REJECT_QUALITY_REPORT_SUCCESS,

    /** Bidder xem các báo cáo chất lượng do mình gửi. Payload: rỗng. */
    GET_MY_QUALITY_REPORTS,

    /** Server trả về danh sách báo cáo của Bidder. Payload: {@code List<QualityReportDTO>}. */
    GET_MY_QUALITY_REPORTS_SUCCESS,

    /** Server từ chối yêu cầu xem báo cáo của Bidder. Payload: {@code ErrorDTO}. */
    GET_MY_QUALITY_REPORTS_FAILED,

    /** Seller xem các báo cáo chất lượng liên quan phiên của mình. Payload: rỗng. */
    GET_SELLER_QUALITY_REPORTS,

    /** Server trả về danh sách báo cáo của Seller. Payload: {@code List<QualityReportDTO>}. */
    GET_SELLER_QUALITY_REPORTS_SUCCESS,

    /** Server từ chối yêu cầu xem báo cáo của Seller. Payload: {@code ErrorDTO}. */
    GET_SELLER_QUALITY_REPORTS_FAILED,

    /**
     * Server push thông báo cho Winner khi báo cáo được duyệt và tiền đã được hoàn.
     * Payload: {@code QualityReportResultDTO}.
     */
    QUALITY_REPORT_APPROVED_NOTIFY,

    /**
     * Server push thông báo cho Seller khi bị báo cáo chất lượng.
     * Payload: {@code QualityReportDTO}.
     */
    QUALITY_REPORT_RECEIVED_NOTIFY,

    /**
     * Server push thông báo cho Seller khi báo cáo bị reject (tức là Seller không bị phạt).
     * Payload: {@code String reportId}.
     */
    QUALITY_REPORT_REJECTED_NOTIFY,

    // ── Seller hoàn tiền sau khi report được approve ─────────────────────────

    /**
     * Server push khi Seller bị ban vĩnh viễn do không hoàn tiền trong 24h.
     * Payload: {@code String sellerId}.
     */
    SELLER_REFUND_OVERDUE_NOTIFY,

    // ══════════════════════════════════════════════════════════════════════════
    // RATING — Đánh giá (Bidder ↔ Seller)
    // ══════════════════════════════════════════════════════════════════════════

    /** Bidder đánh giá Seller sau khi giao dịch hoàn tất.
     *  Payload: {@code RateSellerRequestDTO} (sellerId + rating + comment). */
    RATE_SELLER,

    /** Server xác nhận đánh giá thành công. Payload: rỗng. */
    RATE_SELLER_SUCCESS,

    /** Server từ chối đánh giá (đã đánh giá rồi, phiên chưa PAID, v.v.).
     *  Payload: {@code ErrorDTO}. */
    RATE_SELLER_FAILED,

    /** Seller đánh giá Bidder sau khi giao dịch hoàn tất.
     *  Payload: {@code RateBidderRequestDTO} (bidderId + rating + comment). */
    RATE_BIDDER,

    /** Server xác nhận đánh giá thành công. Payload: rỗng. */
    RATE_BIDDER_SUCCESS,

    /** Server từ chối đánh giá. Payload: {@code ErrorDTO}. */
    RATE_BIDDER_FAILED,

    /** Client yêu cầu lấy lịch sử đánh giá của một user.
     *  Payload: {@code String userId}. */
    GET_USER_RATINGS,

    /** Server trả về danh sách đánh giá. Payload: {@code RatingHistoryDTO}. */
    GET_USER_RATINGS_SUCCESS,

    // ══════════════════════════════════════════════════════════════════════════
    // RATING RESTORE — Khôi phục tài khoản SUSPENDED
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Server push thông báo khi hệ thống tự động restore tài khoản SUSPENDED sau 3 tháng.
     * Payload: {@code AccountRestoredDTO} (newRating + newStatus).
     */
    ACCOUNT_RESTORED_NOTIFY,

    /**
     * Server push thông báo khi tài khoản bị SUSPEND (rating xuống dưới ngưỡng).
     * Payload: {@code AccountSuspendedDTO} (reason + rating).
     */
    ACCOUNT_SUSPENDED_NOTIFY,

    /**
     * Server push thông báo khi tài khoản bị BAN (vĩnh viễn).
     * Payload: {@code AccountBannedDTO} (reason).
     */
    ACCOUNT_BANNED_NOTIFY,

    // ══════════════════════════════════════════════════════════════════════════
    // FRAUD — Phát hiện gian lận (Admin only)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Server push khi hệ thống phát hiện gian lận (shill bidding, v.v.).
     * Chỉ gửi cho SystemAdmin/Staff Admin.
     * Payload: {@code FraudDetectedDTO} (auctionId + suspectedUserId + description).
     */
    FRAUD_DETECTED_NOTIFY,

    // ══════════════════════════════════════════════════════════════════════════
    // NOTIFICATION GENERAL — Thông báo chung cho user
    // ══════════════════════════════════════════════════════════════════════════

    /** Client yêu cầu danh sách thông báo chưa đọc. Payload: rỗng. */
    GET_NOTIFICATIONS,

    /** Server trả về danh sách thông báo. Payload: {@code List<NotificationDTO>}. */
    GET_NOTIFICATIONS_SUCCESS,

    /** Client đánh dấu thông báo đã đọc. Payload: {@code String notificationId}. */
    MARK_NOTIFICATION_READ,

    /** Server xác nhận đã đánh dấu đọc. Payload: rỗng. */
    MARK_NOTIFICATION_READ_SUCCESS,

    // ══════════════════════════════════════════════════════════════════════════
    // SYSTEM — Ping / Pong / Lỗi hệ thống
    // ══════════════════════════════════════════════════════════════════════════

    /** Client gửi ping để kiểm tra kết nối còn sống. Payload: {@code long timestamp}. */
    PING,

    /** Server phản hồi pong. Payload: {@code long timestamp} (echo). */
    PONG,

    /**
     * Server push lỗi hệ thống không thuộc nghiệp vụ cụ thể nào
     * (mất kết nối DB, lỗi nội bộ, v.v.).
     * Payload: {@code ErrorDTO}.
     */
    SYSTEM_ERROR,

    /**
     * Server push thông báo tới toàn bộ client đang kết nối (broadcast toàn hệ thống).
     * Dùng cho bảo trì, thông báo khẩn cấp.
     * Payload: {@code SystemAnnouncementDTO} (message + severity).
     */
    SYSTEM_ANNOUNCEMENT,

    /**
     * Server push thông báo server sắp shutdown/maintenance.
     * Client nên hiển thị cảnh báo và lưu trạng thái.
     * Payload: {@code SystemShutdownDTO} (reason + shutdownInSeconds).
     */
    SERVER_SHUTDOWN_NOTIFY,

    // ══════════════════════════════════════════════════════════════════════════
    // SEARCH — Tìm kiếm sản phẩm theo tên
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Client gửi yêu cầu tìm kiếm phiên đấu giá theo tên sản phẩm.
     * Hỗ trợ phân trang và sắp xếp.
     * Payload: {@code SearchDTOs.ItemSearchRequestDTO}.
     */
    SEARCH_ITEMS,

    /**
     * Server trả về kết quả tìm kiếm (có phân trang).
     * Payload: {@code SearchDTOs.ItemSearchResponseDTO}.
     */
    SEARCH_ITEMS_SUCCESS,

    /**
     * Server từ chối tìm kiếm (keyword rỗng, lỗi hệ thống, v.v.).
     * Payload: {@code ErrorDTO}.
     */
    SEARCH_ITEMS_FAILED,

    // ══════════════════════════════════════════════════════════════════════════
    // CHATBOT — Hỗ trợ khách hàng tự động (Rule-based FAQ)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Client gửi câu hỏi tới chatbot, kèm id câu hỏi hoặc từ khóa tìm kiếm.
     * Payload: {@code ChatbotRequestDTO} (query + optional category).
     */
    CHATBOT_ASK,

    /**
     * Server trả về câu trả lời tìm được từ FAQ.
     * Payload: {@code ChatbotResponse} (faqId + question + answer + category).
     */
    CHATBOT_ANSWER,

    /**
     * Server trả về khi không tìm thấy câu trả lời phù hợp.
     * Payload: {@code ChatbotResponse} (message gợi ý liên hệ admin).
     */
    CHATBOT_NOT_FOUND,

    /**
     * Client yêu cầu lấy toàn bộ danh sách câu hỏi FAQ theo category.
     * Payload: {@code String category} (GENERAL | BIDDING | PAYMENT | RATING | SELLER).
     *          Nếu null → trả về tất cả category.
     */
    CHATBOT_GET_FAQ_LIST,

    /**
     * Server trả về danh sách FAQ (dùng để hiển thị menu câu hỏi gợi ý).
     * Payload: {@code ChatbotFaqListResponse} (List&lt;FAQ&gt; theo category).
     */
    CHATBOT_FAQ_LIST_SUCCESS
}