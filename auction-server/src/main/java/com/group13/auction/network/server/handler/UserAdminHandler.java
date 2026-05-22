package com.group13.auction.network.server.handler;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.dto.rating.RatingDTOs;
import com.group13.auction.common.dto.report.ReportDTOs;
import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.dao.NotificationDAO;
import com.group13.auction.dao.QualityReportDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.notification.Notification;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.QualityReportService;
import com.group13.auction.service.RatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xử lý tất cả packet còn lại:
 * User profile, Admin management, Rating, Quality Report, Notifications.
 */
public class UserAdminHandler implements PacketHandler {

    private static final Logger log = LoggerFactory.getLogger(UserAdminHandler.class);

    private static final Set<PacketType> SUPPORTED = EnumSet.of(
        // User
        PacketType.GET_MY_PROFILE,
        PacketType.GET_USER_PROFILE,
        PacketType.REQUEST_SELLER_ROLE,
        // Admin
        PacketType.ADMIN_BAN_USER,
        PacketType.ADMIN_UNBAN_USER,
        PacketType.ADMIN_GET_ALL_USERS,
        PacketType.ADMIN_CREATE_STAFF,
        PacketType.ADMIN_GET_ALL_STAFF,
        PacketType.ADMIN_APPROVE_SELLER_ROLE,
        // Rating
        PacketType.RATE_SELLER,
        PacketType.RATE_BIDDER,
        PacketType.GET_USER_RATINGS,
        // Quality report
        PacketType.SUBMIT_QUALITY_REPORT,
        PacketType.ADMIN_GET_QUALITY_REPORTS,
        PacketType.ADMIN_APPROVE_QUALITY_REPORT,
        PacketType.ADMIN_REJECT_QUALITY_REPORT,
        // Notifications
        PacketType.GET_NOTIFICATIONS,
        PacketType.MARK_NOTIFICATION_READ,
        // Ping
        PacketType.PING
    );

    private final AccountService       accountService;
    private final RatingService        ratingService;
    private final QualityReportService qualityReportService;
    private final SessionManager       sessionManager;
    private final UserDAO              userDAO;
    private final QualityReportDAO     qualityReportDAO;
    private final NotificationDAO      notificationDAO;

    public UserAdminHandler(AccountService accountService,
                            RatingService ratingService,
                            QualityReportService qualityReportService,
                            SessionManager sessionManager) {
        this.accountService       = accountService;
        this.ratingService        = ratingService;
        this.qualityReportService = qualityReportService;
        this.sessionManager       = sessionManager;
        this.userDAO              = new UserDAO();
        this.qualityReportDAO     = new QualityReportDAO();
        this.notificationDAO      = new NotificationDAO();
    }

    @Override
    public boolean supports(PacketType type) { return SUPPORTED.contains(type); }

    @Override
    public void handle(ClientSession session, PacketType type,
                       JsonElement payload, String requestId) {
        if (type == PacketType.PING) {
            session.send(Packet.of(PacketType.PONG, System.currentTimeMillis(), requestId));
            return;
        }

        log.info("UserAdminHandler: type={}, user={}, requestId={}", type, session.getUsername(), requestId);

        if (!session.isAuthenticated()) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chưa đăng nhập.", requestId), requestId));
            return;
        }

        switch (type) {
            case GET_MY_PROFILE              -> handleGetMyProfile(session, requestId);
            case GET_USER_PROFILE            -> handleGetUserProfile(session, payload, requestId);
            case REQUEST_SELLER_ROLE         -> handleRequestSellerRole(session, requestId);
            case ADMIN_BAN_USER              -> handleBanUser(session, payload, requestId);
            case ADMIN_UNBAN_USER            -> handleUnbanUser(session, payload, requestId);
            case ADMIN_GET_ALL_USERS         -> handleGetAllUsers(session, requestId);
            case ADMIN_CREATE_STAFF          -> handleCreateStaff(session, payload, requestId);
            case ADMIN_GET_ALL_STAFF         -> handleGetAllStaff(session, requestId);
            case ADMIN_APPROVE_SELLER_ROLE   -> handleAdminApproveSellerRole(session, payload, requestId);
            case RATE_SELLER                 -> handleRateSeller(session, payload, requestId);
            case RATE_BIDDER                 -> handleRateBidder(session, payload, requestId);
            case GET_USER_RATINGS            -> handleGetUserRatings(session, payload, requestId);
            case SUBMIT_QUALITY_REPORT       -> handleSubmitReport(session, payload, requestId);
            case ADMIN_GET_QUALITY_REPORTS   -> handleAdminGetReports(session, requestId);
            case ADMIN_APPROVE_QUALITY_REPORT -> handleAdminApproveReport(session, payload, requestId);
            case ADMIN_REJECT_QUALITY_REPORT  -> handleAdminRejectReport(session, payload, requestId);
            case GET_NOTIFICATIONS           -> handleGetNotifications(session, requestId);
            case MARK_NOTIFICATION_READ      -> handleMarkNotificationRead(session, payload, requestId);
            default -> {}
        }
    }

    // ── USER PROFILE ──────────────────────────────────────────────────────────

    private void handleGetMyProfile(ClientSession session, String requestId) {
        User user = AuctionManager.getInstance().findUserByUsername(session.getUsername());
        if (user == null) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.USER_NOT_FOUND, "User không tồn tại.", requestId), requestId));
            return;
        }
        session.send(Packet.of(PacketType.GET_MY_PROFILE_SUCCESS,
            DTOMapper.toUserDTO(user, true), requestId));
    }

    private void handleGetUserProfile(ClientSession session, JsonElement payload, String requestId) {
        try {
            String userId = PacketCodec.fromElement(payload, String.class);
            User user = AuctionManager.getInstance().getAllUsers().stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst().orElse(null);
            if (user == null) {
                session.send(Packet.of(PacketType.GET_USER_PROFILE_FAILED,
                    ErrorDTO.of(ErrorDTO.USER_NOT_FOUND, "User không tồn tại.", requestId), requestId));
                return;
            }
            boolean showBalance = user.getUsername().equals(session.getUsername()) || session.isAdmin();
            session.send(Packet.of(PacketType.GET_USER_PROFILE_SUCCESS,
                DTOMapper.toUserDTO(user, showBalance), requestId));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.GET_USER_PROFILE_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── SELLER ROLE ───────────────────────────────────────────────────────────

    private void handleRequestSellerRole(ClientSession session, String requestId) {
        try {
            User user = AuctionManager.getInstance().findUserByUsername(session.getUsername());
            if (!(user instanceof NormalUser normalUser)) {
                session.send(Packet.of(PacketType.REQUEST_SELLER_ROLE_FAILED,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chỉ NormalUser mới được yêu cầu.", requestId), requestId));
                return;
            }
            accountService.autoApproveSellerRole(normalUser);
            session.send(Packet.of(PacketType.REQUEST_SELLER_ROLE_SUCCESS, null, requestId));
            session.send(Packet.of(PacketType.SELLER_ROLE_APPROVED_NOTIFY, DTOMapper.toUserDTO(normalUser, true)));
        } catch (IllegalStateException e) {
            session.send(Packet.of(PacketType.REQUEST_SELLER_ROLE_FAILED,
                ErrorDTO.of(ErrorDTO.SELLER_ROLE_REQUIRED, e.getMessage(), requestId), requestId));
            session.send(Packet.of(PacketType.SELLER_ROLE_REJECTED_NOTIFY,
                ErrorDTO.of(ErrorDTO.SELLER_ROLE_REQUIRED, e.getMessage())));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.REQUEST_SELLER_ROLE_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── ADMIN BAN / UNBAN ─────────────────────────────────────────────────────

    private void handleBanUser(ClientSession session, JsonElement payload, String requestId) {
        if (!session.isAdmin()) {
            session.send(Packet.of(PacketType.ADMIN_BAN_USER_FAILED,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Không có quyền.", requestId), requestId));
            return;
        }
        try {
            AdminDTOs.AdminBanUserDTO req = PacketCodec.fromElement(payload, AdminDTOs.AdminBanUserDTO.class);
            User target = AuctionManager.getInstance().getAllUsers().stream()
                .filter(u -> u.getId().equals(req.getUserId())).findFirst().orElse(null);
            if (target == null) {
                session.send(Packet.of(PacketType.ADMIN_BAN_USER_FAILED,
                    ErrorDTO.of(ErrorDTO.USER_NOT_FOUND, "User không tồn tại.", requestId), requestId));
                return;
            }
            Admin admin = (Admin) AuctionManager.getInstance().findUserByUsername(session.getUsername());
            accountService.banUser(admin, target, Admin.BanReason.valueOf(req.getReason()));

            com.group13.auction.network.server.session.ClientSession targetSession =
                sessionManager.getByUserId(req.getUserId());
            if (targetSession != null) targetSession.invalidateCachedUser();

            session.send(Packet.of(PacketType.ADMIN_BAN_USER_SUCCESS, DTOMapper.toUserDTO(target, false), requestId));

            RatingDTOs.AccountBannedDTO bannedDTO = new RatingDTOs.AccountBannedDTO();
            bannedDTO.setReason(req.getReason());
            bannedDTO.setBannedBy(session.getUsername());
            sessionManager.sendToUser(req.getUserId(), Packet.of(PacketType.ACCOUNT_BANNED_NOTIFY, bannedDTO));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.ADMIN_BAN_USER_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    private void handleUnbanUser(ClientSession session, JsonElement payload, String requestId) {
        if (!session.isAdmin()) {
            session.send(Packet.of(PacketType.ADMIN_UNBAN_USER_FAILED,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Không có quyền.", requestId), requestId));
            return;
        }
        try {
            String userId = PacketCodec.fromElement(payload, String.class);
            User target = AuctionManager.getInstance().getAllUsers().stream()
                .filter(u -> u.getId().equals(userId)).findFirst().orElse(null);
            if (target == null) {
                session.send(Packet.of(PacketType.ADMIN_UNBAN_USER_FAILED,
                    ErrorDTO.of(ErrorDTO.USER_NOT_FOUND, "User không tồn tại.", requestId), requestId));
                return;
            }
            target.setAccountStatus(User.AccountStatus.ACTIVE);
            userDAO.updateAccountStatus(target.getId(), User.AccountStatus.ACTIVE.name());
            session.send(Packet.of(PacketType.ADMIN_UNBAN_USER_SUCCESS,
                DTOMapper.toUserDTO(target, false), requestId));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.ADMIN_UNBAN_USER_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    private void handleGetAllUsers(ClientSession session, String requestId) {
        if (!session.isAdmin()) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Không có quyền.", requestId), requestId));
            return;
        }
        List<UserDTO> dtos = AuctionManager.getInstance().getAllUsers().stream()
            .map(u -> DTOMapper.toUserDTO(u, false)).collect(Collectors.toList());
        session.send(Packet.of(PacketType.ADMIN_GET_ALL_USERS_SUCCESS, dtos, requestId));
    }

    private void handleCreateStaff(ClientSession session, JsonElement payload, String requestId) {
        if (!session.isMasterAdmin()) {
            session.send(Packet.of(PacketType.ADMIN_CREATE_STAFF_FAILED,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chỉ SystemAdmin (MASTER) mới được tạo Staff.", requestId), requestId));
            return;
        }
        try {
            AdminDTOs.CreateStaffAdminDTO req = PacketCodec.fromElement(payload, AdminDTOs.CreateStaffAdminDTO.class);
            Admin newAdmin = accountService.createStaffAdmin(req.getUsername(), req.getPassword(), req.getEmail());
            session.send(Packet.of(PacketType.ADMIN_CREATE_STAFF_SUCCESS,
                DTOMapper.toUserDTO(newAdmin, false), requestId));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.ADMIN_CREATE_STAFF_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    private void handleGetAllStaff(ClientSession session, String requestId) {
        if (!session.isMasterAdmin()) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Không có quyền.", requestId), requestId));
            return;
        }
        List<UserDTO> dtos = AuctionManager.getInstance().getAllUsers().stream()
            .filter(u -> u instanceof Admin)
            .map(u -> DTOMapper.toUserDTO(u, false)).collect(Collectors.toList());
        session.send(Packet.of(PacketType.ADMIN_GET_ALL_STAFF_SUCCESS, dtos, requestId));
    }

    private void handleAdminApproveSellerRole(ClientSession session, JsonElement payload, String requestId) {
        if (!session.isAdmin()) {
            session.send(Packet.of(PacketType.ADMIN_APPROVE_SELLER_ROLE_FAILED,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Không có quyền.", requestId), requestId));
            return;
        }
        try {
            String userId = PacketCodec.fromElement(payload, String.class);
            User target = AuctionManager.getInstance().getAllUsers().stream()
                .filter(u -> u.getId().equals(userId)).findFirst().orElse(null);
            if (!(target instanceof NormalUser normalUser)) {
                session.send(Packet.of(PacketType.ADMIN_APPROVE_SELLER_ROLE_FAILED,
                    ErrorDTO.of(ErrorDTO.USER_NOT_FOUND, "User không tồn tại hoặc không hợp lệ.", requestId), requestId));
                return;
            }
            accountService.autoApproveSellerRole(normalUser);
            session.send(Packet.of(PacketType.ADMIN_APPROVE_SELLER_ROLE_SUCCESS,
                DTOMapper.toUserDTO(normalUser, false), requestId));

            com.group13.auction.network.server.session.ClientSession targetSession =
                sessionManager.getByUserId(userId);
            if (targetSession != null) targetSession.invalidateCachedUser();

            sessionManager.sendToUser(userId, Packet.of(PacketType.SELLER_ROLE_APPROVED_NOTIFY,
                DTOMapper.toUserDTO(normalUser, true)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.ADMIN_APPROVE_SELLER_ROLE_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── RATING ────────────────────────────────────────────────────────────────

    /**
     * Bidder đánh giá Seller sau khi phiên hoàn tất.
     *
     * <p>Validation: phiên phải PAID, requester phải là winner của phiên đó.
     */
    private void handleRateSeller(ClientSession session, JsonElement payload, String requestId) {
        try {
            RatingDTOs.RateSellerRequestDTO req = PacketCodec.fromElement(
                payload, RatingDTOs.RateSellerRequestDTO.class);

            NormalUser rater = requireNormalUser(session, requestId);
            if (rater == null) return;

            // Validate auction tồn tại và đã PAID
            Auction auction = AuctionManager.getInstance().findAuctionById(req.getAuctionId());
            if (auction == null) {
                session.send(Packet.of(PacketType.RATE_SELLER_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Phiên đấu giá không tồn tại.", requestId), requestId));
                return;
            }
            if (auction.getStatus() != Auction.AuctionStatus.PAID) {
                session.send(Packet.of(PacketType.RATE_SELLER_FAILED,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                        "Chỉ có thể đánh giá sau khi phiên đấu giá hoàn tất thanh toán.", requestId), requestId));
                return;
            }

            // Validate rater là winner của phiên
            if (auction.getWinner() == null
                || !auction.getWinner().getWinner().getId().equals(rater.getId())) {
                session.send(Packet.of(PacketType.RATE_SELLER_FAILED,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED,
                        "Chỉ người thắng phiên mới có thể đánh giá seller.", requestId), requestId));
                return;
            }

            // Lấy seller để apply rating
            User seller = auction.getItem() != null ? auction.getItem().getSeller() : null;
            if (seller == null) {
                session.send(Packet.of(PacketType.RATE_SELLER_FAILED,
                    ErrorDTO.of(ErrorDTO.USER_NOT_FOUND, "Không tìm thấy seller.", requestId), requestId));
                return;
            }
            if (!req.getSellerId().equals(seller.getId())) {
                session.send(Packet.of(PacketType.RATE_SELLER_FAILED,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR, "sellerId không khớp với seller của phiên.", requestId), requestId));
                return;
            }

            ratingService.applyUserRating(seller, req.getRating(), req.getAuctionId(), rater.getUsername());
            log.info("RATE_SELLER: rater={}, seller={}, score={}, auctionId={}",
                rater.getUsername(), seller.getUsername(), req.getRating(), req.getAuctionId());

            session.send(Packet.of(PacketType.RATE_SELLER_SUCCESS, null, requestId));

        } catch (IllegalArgumentException e) {
            session.send(Packet.of(PacketType.RATE_SELLER_FAILED,
                ErrorDTO.of(ErrorDTO.VALIDATION_ERROR, e.getMessage(), requestId), requestId));
        } catch (Exception e) {
            log.error("handleRateSeller failed: requestId={}", requestId, e);
            session.send(Packet.of(PacketType.RATE_SELLER_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    /**
     * Seller đánh giá Bidder (winner) sau khi phiên hoàn tất.
     *
     * <p>Validation: phiên phải PAID, requester phải là seller của phiên đó.
     */
    private void handleRateBidder(ClientSession session, JsonElement payload, String requestId) {
        try {
            RatingDTOs.RateBidderRequestDTO req = PacketCodec.fromElement(
                payload, RatingDTOs.RateBidderRequestDTO.class);

            NormalUser rater = requireNormalUser(session, requestId);
            if (rater == null) return;

            Auction auction = AuctionManager.getInstance().findAuctionById(req.getAuctionId());
            if (auction == null) {
                session.send(Packet.of(PacketType.RATE_BIDDER_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Phiên đấu giá không tồn tại.", requestId), requestId));
                return;
            }
            if (auction.getStatus() != Auction.AuctionStatus.PAID) {
                session.send(Packet.of(PacketType.RATE_BIDDER_FAILED,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                        "Chỉ có thể đánh giá sau khi phiên đấu giá hoàn tất thanh toán.", requestId), requestId));
                return;
            }

            // Validate rater là seller của phiên
            NormalUser seller = auction.getItem() != null ? auction.getItem().getSeller() : null;
            if (seller == null || !seller.getId().equals(rater.getId())) {
                session.send(Packet.of(PacketType.RATE_BIDDER_FAILED,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED,
                        "Chỉ seller của phiên mới có thể đánh giá bidder.", requestId), requestId));
                return;
            }

            // Lấy winner để apply rating
            if (auction.getWinner() == null) {
                session.send(Packet.of(PacketType.RATE_BIDDER_FAILED,
                    ErrorDTO.of(ErrorDTO.USER_NOT_FOUND, "Phiên không có người thắng.", requestId), requestId));
                return;
            }
            NormalUser bidder = auction.getWinner().getWinner();
            if (!req.getBidderId().equals(bidder.getId())) {
                session.send(Packet.of(PacketType.RATE_BIDDER_FAILED,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR, "bidderId không khớp với winner của phiên.", requestId), requestId));
                return;
            }

            ratingService.applyUserRating(bidder, req.getRating(), req.getAuctionId(), rater.getUsername());
            log.info("RATE_BIDDER: rater={}, bidder={}, score={}, auctionId={}",
                rater.getUsername(), bidder.getUsername(), req.getRating(), req.getAuctionId());

            session.send(Packet.of(PacketType.RATE_BIDDER_SUCCESS, null, requestId));

        } catch (IllegalArgumentException e) {
            session.send(Packet.of(PacketType.RATE_BIDDER_FAILED,
                ErrorDTO.of(ErrorDTO.VALIDATION_ERROR, e.getMessage(), requestId), requestId));
        } catch (Exception e) {
            log.error("handleRateBidder failed: requestId={}", requestId, e);
            session.send(Packet.of(PacketType.RATE_BIDDER_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    /**
     * Trả về rating hiện tại của user (averageRating từ DB).
     * Entries chi tiết chưa có RatingDAO → trả về list rỗng.
     */
    private void handleGetUserRatings(ClientSession session, JsonElement payload, String requestId) {
        try {
            String targetUserId = PacketCodec.fromElement(payload, String.class);
            User target = AuctionManager.getInstance().getAllUsers().stream()
                .filter(u -> u.getId().equals(targetUserId)).findFirst().orElse(null);

            RatingDTOs.RatingHistoryDTO history = new RatingDTOs.RatingHistoryDTO();
            if (target != null) {
                history.setUserId(target.getId());
                history.setAverageRating(target.getRating());
                history.setTotalRatings(0); // không có bảng ratings riêng
                history.setEntries(List.of());
            }
            session.send(Packet.of(PacketType.GET_USER_RATINGS_SUCCESS, history, requestId));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.GET_USER_RATINGS_SUCCESS,
                new RatingDTOs.RatingHistoryDTO(), requestId));
        }
    }

    // ── QUALITY REPORT ────────────────────────────────────────────────────────

    private void handleSubmitReport(ClientSession session, JsonElement payload, String requestId) {
        try {
            ReportDTOs.QualityReportRequestDTO req = PacketCodec.fromElement(
                payload, ReportDTOs.QualityReportRequestDTO.class);

            NormalUser reporter = requireNormalUser(session, requestId);
            if (reporter == null) return;

            if (req.getEvidenceUrls() == null || req.getEvidenceUrls().isEmpty()) {
                session.send(Packet.of(PacketType.SUBMIT_QUALITY_REPORT_FAILED,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                        "Báo cáo chất lượng phải đính kèm ít nhất 1 ảnh minh chứng.", requestId), requestId));
                return;
            }

            QualityReport report = QualityReport.create(
                reporter, req.getAuctionId(), req.getDescription(), req.getEvidenceUrls());
            QualityReport saved  = qualityReportService.submitReport(report);

            ReportDTOs.QualityReportDTO dto = new ReportDTOs.QualityReportDTO();
            dto.setReportId(saved.getId());
            dto.setAuctionId(saved.getAuctionId());
            dto.setReporterId(reporter.getId());
            dto.setReporterUsername(reporter.getUsername());
            dto.setDescription(saved.getDescription());
            dto.setEvidenceUrls(saved.getImageUrls());
            dto.setStatus(saved.getStatus().name());
            dto.setCreatedAt(saved.getCreatedAt());
            session.send(Packet.of(PacketType.SUBMIT_QUALITY_REPORT_SUCCESS, dto, requestId));

            Auction auction = AuctionManager.getInstance().findAuctionById(req.getAuctionId());
            if (auction != null && auction.getItem() != null && auction.getItem().getSeller() != null) {
                sessionManager.sendToUser(auction.getItem().getSeller().getId(),
                    Packet.of(PacketType.QUALITY_REPORT_RECEIVED_NOTIFY, dto));
            }

            log.info("Quality report submitted: reportId={}, auctionId={}, reporter={}",
                saved.getId(), saved.getAuctionId(), reporter.getUsername());

        } catch (IllegalArgumentException e) {
            session.send(Packet.of(PacketType.SUBMIT_QUALITY_REPORT_FAILED,
                ErrorDTO.of(ErrorDTO.VALIDATION_ERROR, e.getMessage(), requestId), requestId));
        } catch (Exception e) {
            log.error("Submit quality report failed: requestId={}", requestId, e);
            session.send(Packet.of(PacketType.SUBMIT_QUALITY_REPORT_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    private void handleAdminGetReports(ClientSession session, String requestId) {
        if (!session.isAdmin()) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Không có quyền.", requestId), requestId));
            return;
        }
        try {
            List<QualityReport> reports = qualityReportDAO.findPending();
            List<ReportDTOs.QualityReportDTO> dtos = new ArrayList<>();
            for (QualityReport r : reports) {
                ReportDTOs.QualityReportDTO dto = new ReportDTOs.QualityReportDTO();
                dto.setReportId(r.getId());
                dto.setAuctionId(r.getAuctionId());
                if (r.getReporter() != null) {
                    dto.setReporterId(r.getReporter().getId());
                    dto.setReporterUsername(r.getReporter().getUsername());
                }
                dto.setDescription(r.getDescription());
                dto.setEvidenceUrls(r.getImageUrls());
                dto.setStatus(r.getStatus().name());
                dto.setCreatedAt(r.getCreatedAt());
                dto.setRefundCompleted(r.isRefundCompleted());
                dtos.add(dto);
            }
            session.send(Packet.of(PacketType.ADMIN_GET_QUALITY_REPORTS_SUCCESS, dtos, requestId));
        } catch (Exception e) {
            log.error("Admin get quality reports failed: requestId={}", requestId, e);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    private void handleAdminApproveReport(ClientSession session, JsonElement payload, String requestId) {
        if (!session.isAdmin()) {
            session.send(Packet.of(PacketType.ADMIN_APPROVE_QUALITY_REPORT_FAILED,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Không có quyền.", requestId), requestId));
            return;
        }
        try {
            String reportId = PacketCodec.fromElement(payload, String.class);
            QualityReport report = qualityReportDAO.findById(reportId);
            if (report == null) {
                session.send(Packet.of(PacketType.ADMIN_APPROVE_QUALITY_REPORT_FAILED,
                    ErrorDTO.of("REPORT_NOT_FOUND", "Không tìm thấy báo cáo: " + reportId, requestId), requestId));
                return;
            }
            Auction auction = AuctionManager.getInstance().findAuctionById(report.getAuctionId());
            if (auction == null) {
                session.send(Packet.of(PacketType.ADMIN_APPROVE_QUALITY_REPORT_FAILED,
                    ErrorDTO.of("AUCTION_NOT_FOUND", "Không tìm thấy phiên đấu giá.", requestId), requestId));
                return;
            }
            User adminUser = AuctionManager.getInstance().findUserByUsername(session.getUsername());
            if (!(adminUser instanceof Admin admin)) {
                session.send(Packet.of(PacketType.ADMIN_APPROVE_QUALITY_REPORT_FAILED,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chỉ Admin mới được approve.", requestId), requestId));
                return;
            }

            NormalUser seller = auction.getItem() != null ? auction.getItem().getSeller() : null;
            double sellerRatingBefore = seller != null ? seller.getRating() : 0.0;

            qualityReportService.approveReport(admin, report, auction);

            ReportDTOs.QualityReportResultDTO result = new ReportDTOs.QualityReportResultDTO();
            result.setReportId(reportId);
            result.setAuctionId(report.getAuctionId());
            long finalPrice = auction.getWinner() != null ? auction.getWinner().getFinalPrice() : 0L;
            result.setRefundedAmount(finalPrice);
            if (seller != null) {
                result.setSellerRatingPenalty(sellerRatingBefore - seller.getRating());
                result.setSellerNewRating(seller.getRating());
                result.setSellerBanned(seller.getAccountStatus() == User.AccountStatus.BANNED);
            }

            session.send(Packet.of(PacketType.ADMIN_APPROVE_QUALITY_REPORT_SUCCESS, result, requestId));
            sessionManager.sendToUser(report.getReporter().getId(),
                Packet.of(PacketType.QUALITY_REPORT_APPROVED_NOTIFY, result));

            log.info("Quality report approved: reportId={}, adminId={}, winnerId={}",
                reportId, admin.getId(), report.getReporter().getId());

        } catch (IllegalStateException e) {
            session.send(Packet.of(PacketType.ADMIN_APPROVE_QUALITY_REPORT_FAILED,
                ErrorDTO.of("INVALID_STATUS", e.getMessage(), requestId), requestId));
        } catch (Exception e) {
            log.error("Admin approve quality report failed: requestId={}", requestId, e);
            session.send(Packet.of(PacketType.ADMIN_APPROVE_QUALITY_REPORT_FAILED,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    private void handleAdminRejectReport(ClientSession session, JsonElement payload, String requestId) {
        if (!session.isAdmin()) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Không có quyền.", requestId), requestId));
            return;
        }
        try {
            String reportId = PacketCodec.fromElement(payload, String.class);
            QualityReport report = qualityReportDAO.findById(reportId);
            if (report == null) {
                session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of("REPORT_NOT_FOUND", "Không tìm thấy báo cáo: " + reportId, requestId), requestId));
                return;
            }
            User adminUser = AuctionManager.getInstance().findUserByUsername(session.getUsername());
            if (!(adminUser instanceof Admin admin)) {
                session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chỉ Admin mới được reject.", requestId), requestId));
                return;
            }

            qualityReportService.rejectReport(admin, report);
            session.send(Packet.of(PacketType.ADMIN_REJECT_QUALITY_REPORT_SUCCESS, null, requestId));

            Auction auction = AuctionManager.getInstance().findAuctionById(report.getAuctionId());
            if (auction != null && auction.getItem() != null && auction.getItem().getSeller() != null) {
                sessionManager.sendToUser(auction.getItem().getSeller().getId(),
                    Packet.of(PacketType.QUALITY_REPORT_REJECTED_NOTIFY, reportId));
            }
            log.info("Quality report rejected: reportId={}, adminId={}", reportId, admin.getId());

        } catch (IllegalStateException e) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of("INVALID_STATUS", e.getMessage(), requestId), requestId));
        } catch (Exception e) {
            log.error("Admin reject quality report failed: requestId={}", requestId, e);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── NOTIFICATIONS ─────────────────────────────────────────────────────────

    /**
     * Load tất cả notifications của user từ DB, map sang DTO rồi trả về client.
     */
    private void handleGetNotifications(ClientSession session, String requestId) {
        try {
            User user = AuctionManager.getInstance().findUserByUsername(session.getUsername());
            if (user == null) {
                session.send(Packet.of(PacketType.GET_NOTIFICATIONS_SUCCESS, List.of(), requestId));
                return;
            }

            List<Notification> rawList = notificationDAO.findByUserId(user.getId());
            List<AdminDTOs.NotificationDTO> dtos = new ArrayList<>();
            for (Notification n : rawList) {
                AdminDTOs.NotificationDTO dto = new AdminDTOs.NotificationDTO();
                dto.setId(n.getId());
                dto.setTitle(n.getTitle());
                dto.setBody(n.getBody());
                dto.setRead(n.isRead());
                dto.setCreatedAt(n.getCreatedAt());
                dto.setRelatedAuctionId(n.getAuctionId());
                dto.setType("SYSTEM"); // default type — chưa có cột type trong DB
                dtos.add(dto);
            }

            session.send(Packet.of(PacketType.GET_NOTIFICATIONS_SUCCESS, dtos, requestId));
            log.debug("GET_NOTIFICATIONS: userId={}, count={}", user.getId(), dtos.size());

        } catch (Exception e) {
            log.error("handleGetNotifications failed: requestId={}", requestId, e);
            session.send(Packet.of(PacketType.GET_NOTIFICATIONS_SUCCESS, List.of(), requestId));
        }
    }

    /**
     * Đánh dấu một notification đã đọc.
     * Payload: notificationId (String).
     */
    private void handleMarkNotificationRead(ClientSession session, JsonElement payload, String requestId) {
        try {
            String notificationId = PacketCodec.fromElement(payload, String.class);
            User user = AuctionManager.getInstance().findUserByUsername(session.getUsername());
            if (user == null) {
                session.send(Packet.of(PacketType.MARK_NOTIFICATION_READ_SUCCESS, null, requestId));
                return;
            }

            boolean updated = notificationDAO.markRead(notificationId, user.getId());
            if (!updated) {
                log.warn("markRead: notification not found or not owned by user — notificationId={}, userId={}",
                    notificationId, user.getId());
            }

            session.send(Packet.of(PacketType.MARK_NOTIFICATION_READ_SUCCESS, null, requestId));

        } catch (Exception e) {
            log.error("handleMarkNotificationRead failed: requestId={}", requestId, e);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NormalUser requireNormalUser(ClientSession session, String requestId) {
        User user = AuctionManager.getInstance().findUserByUsername(session.getUsername());
        if (!(user instanceof NormalUser)) {
            log.warn("requireNormalUser failed: username={}, requestId={}", session.getUsername(), requestId);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                ErrorDTO.of(ErrorDTO.UNAUTHORIZED,
                    "Chỉ NormalUser mới có thể thực hiện hành động này.", requestId), requestId));
            return null;
        }
        return (NormalUser) user;
    }
}