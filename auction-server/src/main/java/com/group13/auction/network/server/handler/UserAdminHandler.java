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
import com.group13.auction.dao.QualityReportDAO;
import com.group13.auction.dao.UserDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.bid.QualityReport;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.QualityReportService;
import com.group13.auction.service.RatingService;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xử lý tất cả packet còn lại:
 * User profile, Admin management, Rating, Quality Report.
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

    private final AccountService accountService;
    private final RatingService ratingService;
    private final QualityReportService qualityReportService;
    private final SessionManager sessionManager;
    // FIX Vấn đề 4: cần UserDAO để persist unban xuống DB
    private final UserDAO userDAO;
    // FIX Vấn đề 3: cần QualityReportDAO để load report từ DB cho admin
    private final QualityReportDAO qualityReportDAO;

    public UserAdminHandler(AccountService accountService,
                            RatingService ratingService,
                            QualityReportService qualityReportService,
                            SessionManager sessionManager) {
        this.accountService = accountService;
        this.ratingService = ratingService;
        this.qualityReportService = qualityReportService;
        this.sessionManager = sessionManager;
        this.userDAO = new UserDAO();
        this.qualityReportDAO = new QualityReportDAO();
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
            // User
            case GET_MY_PROFILE          -> handleGetMyProfile(session, requestId);
            case GET_USER_PROFILE        -> handleGetUserProfile(session, payload, requestId);
            case REQUEST_SELLER_ROLE     -> handleRequestSellerRole(session, requestId);
            // Admin user management
            case ADMIN_BAN_USER          -> handleBanUser(session, payload, requestId);
            case ADMIN_UNBAN_USER        -> handleUnbanUser(session, payload, requestId);
            case ADMIN_GET_ALL_USERS     -> handleGetAllUsers(session, requestId);
            case ADMIN_CREATE_STAFF      -> handleCreateStaff(session, payload, requestId);
            case ADMIN_GET_ALL_STAFF     -> handleGetAllStaff(session, requestId);
            case ADMIN_APPROVE_SELLER_ROLE -> handleAdminApproveSellerRole(session, payload, requestId);
            // Rating
            case RATE_SELLER             -> handleRateSeller(session, payload, requestId);
            case RATE_BIDDER             -> handleRateBidder(session, payload, requestId);
            case GET_USER_RATINGS        -> handleGetUserRatings(session, payload, requestId);
            // Quality report
            case SUBMIT_QUALITY_REPORT         -> handleSubmitReport(session, payload, requestId);
            case ADMIN_GET_QUALITY_REPORTS     -> handleAdminGetReports(session, requestId);
            case ADMIN_APPROVE_QUALITY_REPORT  -> handleAdminApproveReport(session, payload, requestId);
            case ADMIN_REJECT_QUALITY_REPORT   -> handleAdminRejectReport(session, payload, requestId);
            case GET_NOTIFICATIONS             -> handleGetNotifications(session, requestId);
            case MARK_NOTIFICATION_READ        -> handleMarkNotificationRead(session, payload, requestId);
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
            // Chỉ gửi balance cho chính chủ hoặc Admin
            boolean showBalance = user.getUsername().equals(session.getUsername())
                    || session.isAdmin();
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

            // Notify client approved
            session.send(Packet.of(PacketType.SELLER_ROLE_APPROVED_NOTIFY,
                    DTOMapper.toUserDTO(normalUser, true)));

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
                    .filter(u -> u.getId().equals(req.getUserId()))
                    .findFirst().orElse(null);
            if (target == null) {
                session.send(Packet.of(PacketType.ADMIN_BAN_USER_FAILED,
                        ErrorDTO.of(ErrorDTO.USER_NOT_FOUND, "User không tồn tại.", requestId), requestId));
                return;
            }
            Admin admin = (Admin) AuctionManager.getInstance()
                    .findUserByUsername(session.getUsername());
            Admin.BanReason reason = Admin.BanReason.valueOf(req.getReason());
            accountService.banUser(admin, target, reason);

            UserDTO dto = DTOMapper.toUserDTO(target, false);
            session.send(Packet.of(PacketType.ADMIN_BAN_USER_SUCCESS, dto, requestId));

            // FIX: dùng RatingDTOs.AccountBannedDTO (class client đang deserialize)
            // và set reason + bannedBy thay vì để rỗng
            RatingDTOs.AccountBannedDTO bannedDTO = new RatingDTOs.AccountBannedDTO();
            bannedDTO.setReason(req.getReason());
            bannedDTO.setBannedBy(session.getUsername());
            sessionManager.sendToUser(req.getUserId(),
                    Packet.of(PacketType.ACCOUNT_BANNED_NOTIFY, bannedDTO));

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
            // FIX Vấn đề 4: cập nhật in-memory VÀ persist xuống DB
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
                .map(u -> DTOMapper.toUserDTO(u, false))
                .collect(Collectors.toList());
        session.send(Packet.of(PacketType.ADMIN_GET_ALL_USERS_SUCCESS, dtos, requestId));
    }

    private void handleCreateStaff(ClientSession session, JsonElement payload, String requestId) {
        if (!session.isMasterAdmin()) {
            session.send(Packet.of(PacketType.ADMIN_CREATE_STAFF_FAILED,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chỉ SystemAdmin (MASTER) mới được tạo Staff.", requestId), requestId));
            return;
        }
        try {
            AdminDTOs.CreateStaffAdminDTO req = PacketCodec.fromElement(
                    payload, AdminDTOs.CreateStaffAdminDTO.class);
            Admin newAdmin = accountService.createStaffAdmin(
                    req.getUsername(), req.getPassword(), req.getEmail());
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
                .map(u -> DTOMapper.toUserDTO(u, false))
                .collect(Collectors.toList());
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
            sessionManager.sendToUser(userId,
                    Packet.of(PacketType.SELLER_ROLE_APPROVED_NOTIFY,
                            DTOMapper.toUserDTO(normalUser, true)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.ADMIN_APPROVE_SELLER_ROLE_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── RATING ────────────────────────────────────────────────────────────────

    private void handleRateSeller(ClientSession session, JsonElement payload, String requestId) {
        try {
            RatingDTOs.RateSellerRequestDTO req = PacketCodec.fromElement(
                    payload, RatingDTOs.RateSellerRequestDTO.class);
            // TODO: gọi ratingService khi có method rateSeller(bidderId, sellerId, rating, comment, auctionId)
            session.send(Packet.of(PacketType.RATE_SELLER_SUCCESS, null, requestId));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.RATE_SELLER_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    private void handleRateBidder(ClientSession session, JsonElement payload, String requestId) {
        try {
            RatingDTOs.RateBidderRequestDTO req = PacketCodec.fromElement(
                    payload, RatingDTOs.RateBidderRequestDTO.class);
            // TODO: gọi ratingService khi có method rateBidder
            session.send(Packet.of(PacketType.RATE_BIDDER_SUCCESS, null, requestId));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.RATE_BIDDER_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    private void handleGetUserRatings(ClientSession session, JsonElement payload, String requestId) {
        // TODO: query RatingDAO khi có
        RatingDTOs.RatingHistoryDTO history = new RatingDTOs.RatingHistoryDTO();
        session.send(Packet.of(PacketType.GET_USER_RATINGS_SUCCESS, history, requestId));
    }

    // ── QUALITY REPORT ────────────────────────────────────────────────────────

    private void handleSubmitReport(ClientSession session, JsonElement payload, String requestId) {
        try {
            ReportDTOs.QualityReportRequestDTO req = PacketCodec.fromElement(
                    payload, ReportDTOs.QualityReportRequestDTO.class);

            NormalUser reporter = requireNormalUser(session, requestId);
            if (reporter == null) return;

            // Validate: phải có ít nhất 1 ảnh minh chứng
            if (req.getEvidenceUrls() == null || req.getEvidenceUrls().isEmpty()) {
                session.send(Packet.of(PacketType.SUBMIT_QUALITY_REPORT_FAILED,
                        ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                                "Báo cáo chất lượng phải đính kèm ít nhất 1 ảnh minh chứng.", requestId), requestId));
                return;
            }

            // Tạo QualityReport domain object (validate ảnh lần 2 trong model)
            // evidenceUrls từ client map sang imageUrls của model
            com.group13.auction.model.bid.QualityReport report =
                    com.group13.auction.model.bid.QualityReport.create(
                            reporter, req.getAuctionId(),
                            req.getDescription(), req.getEvidenceUrls());

            // Lưu qua service
            com.group13.auction.model.bid.QualityReport saved =
                    qualityReportService.submitReport(report);

            // Map sang DTO để trả về client
            ReportDTOs.QualityReportDTO dto = new ReportDTOs.QualityReportDTO();
            dto.setReportId(saved.getId());
            dto.setAuctionId(saved.getAuctionId());
            dto.setReporterId(reporter.getId());
            dto.setReporterUsername(reporter.getUsername());
            dto.setDescription(saved.getDescription());
            dto.setEvidenceUrls(saved.getImageUrls()); // imageUrls → evidenceUrls cho client
            dto.setStatus(saved.getStatus().name());
            dto.setCreatedAt(saved.getCreatedAt());
            session.send(Packet.of(PacketType.SUBMIT_QUALITY_REPORT_SUCCESS, dto, requestId));

            // Notify seller: họ bị report để chuẩn bị hoàn tiền nếu admin approve
            com.group13.auction.model.auction.Auction auction =
                    com.group13.auction.manager.AuctionManager.getInstance()
                            .findAuctionById(req.getAuctionId());
            if (auction != null && auction.getItem() != null
                    && auction.getItem().getSeller() != null) {
                String sellerId = auction.getItem().getSeller().getId();
                sessionManager.sendToUser(sellerId,
                        Packet.of(PacketType.QUALITY_REPORT_RECEIVED_NOTIFY, dto));
                log.info("QUALITY_REPORT_RECEIVED_NOTIFY sent: sellerId={}, reportId={}",
                        sellerId, saved.getId());
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
            java.util.List<com.group13.auction.model.bid.QualityReport> reports =
                    qualityReportDAO.findPending();
            java.util.List<ReportDTOs.QualityReportDTO> dtos = new java.util.ArrayList<>();
            for (com.group13.auction.model.bid.QualityReport r : reports) {
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

            // Tìm report từ DB
            com.group13.auction.model.bid.QualityReport report = qualityReportDAO.findById(reportId);
            if (report == null) {
                session.send(Packet.of(PacketType.ADMIN_APPROVE_QUALITY_REPORT_FAILED,
                        ErrorDTO.of("REPORT_NOT_FOUND", "Không tìm thấy báo cáo: " + reportId, requestId), requestId));
                return;
            }

            // Tìm auction từ AuctionManager
            com.group13.auction.model.auction.Auction auction =
                    com.group13.auction.manager.AuctionManager.getInstance()
                            .findAuctionById(report.getAuctionId());
            if (auction == null) {
                session.send(Packet.of(PacketType.ADMIN_APPROVE_QUALITY_REPORT_FAILED,
                        ErrorDTO.of("AUCTION_NOT_FOUND", "Không tìm thấy phiên đấu giá.", requestId), requestId));
                return;
            }

            // Lấy admin user - cần cast sang Admin
            com.group13.auction.model.user.User adminUser =
                    com.group13.auction.manager.AuctionManager.getInstance()
                            .findUserByUsername(session.getUsername());
            if (!(adminUser instanceof com.group13.auction.model.user.Admin)) {
                session.send(Packet.of(PacketType.ADMIN_APPROVE_QUALITY_REPORT_FAILED,
                        ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chỉ Admin mới được approve.", requestId), requestId));
                return;
            }
            com.group13.auction.model.user.Admin admin =
                    (com.group13.auction.model.user.Admin) adminUser;

            // Capture seller info trước khi approve (để tính penalty delta)
            com.group13.auction.model.user.NormalUser seller =
                    auction.getItem() != null ? auction.getItem().getSeller() : null;
            double sellerRatingBefore = seller != null ? seller.getRating() : 0.0;

            // Gọi service - approve + phạt seller + hoàn tiền winner
            qualityReportService.approveReport(admin, report, auction);

            // Build result DTO
            ReportDTOs.QualityReportResultDTO result = new ReportDTOs.QualityReportResultDTO();
            result.setReportId(reportId);
            result.setAuctionId(report.getAuctionId());
            long finalPrice = auction.getWinner() != null ? auction.getWinner().getFinalPrice() : 0L;
            result.setRefundedAmount(finalPrice);
            if (seller != null) {
                double newRating = seller.getRating();
                result.setSellerRatingPenalty(sellerRatingBefore - newRating);
                result.setSellerNewRating(newRating);
                result.setSellerBanned(seller.getAccountStatus() ==
                        com.group13.auction.model.user.User.AccountStatus.BANNED);
            }

            // Gửi SUCCESS cho admin
            session.send(Packet.of(PacketType.ADMIN_APPROVE_QUALITY_REPORT_SUCCESS, result, requestId));

            // Notify winner: được hoàn tiền
            com.group13.auction.model.user.NormalUser winner = report.getReporter();
            sessionManager.sendToUser(winner.getId(),
                    Packet.of(PacketType.QUALITY_REPORT_APPROVED_NOTIFY, result));

            log.info("Quality report approved: reportId={}, auctionId={}, adminId={}, winnerId={}",
                    reportId, report.getAuctionId(), admin.getId(), winner.getId());

        } catch (IllegalStateException e) {
            // Report không ở PENDING
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

            // Tìm report từ DB
            com.group13.auction.model.bid.QualityReport report = qualityReportDAO.findById(reportId);
            if (report == null) {
                session.send(Packet.of(PacketType.SYSTEM_ERROR,
                        ErrorDTO.of("REPORT_NOT_FOUND", "Không tìm thấy báo cáo: " + reportId, requestId), requestId));
                return;
            }

            // Lấy admin user
            com.group13.auction.model.user.User adminUser =
                    com.group13.auction.manager.AuctionManager.getInstance()
                            .findUserByUsername(session.getUsername());
            if (!(adminUser instanceof com.group13.auction.model.user.Admin)) {
                session.send(Packet.of(PacketType.SYSTEM_ERROR,
                        ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chỉ Admin mới được reject.", requestId), requestId));
                return;
            }

            // Gọi service - reject
            qualityReportService.rejectReport(
                    (com.group13.auction.model.user.Admin) adminUser, report);

            // Gửi SUCCESS cho admin
            session.send(Packet.of(PacketType.ADMIN_REJECT_QUALITY_REPORT_SUCCESS, null, requestId));

            // Notify seller: báo cáo của winner bị từ chối, seller không bị phạt
            com.group13.auction.model.auction.Auction auction =
                    com.group13.auction.manager.AuctionManager.getInstance()
                            .findAuctionById(report.getAuctionId());
            if (auction != null && auction.getItem() != null
                    && auction.getItem().getSeller() != null) {
                String sellerId = auction.getItem().getSeller().getId();
                sessionManager.sendToUser(sellerId,
                        Packet.of(PacketType.QUALITY_REPORT_REJECTED_NOTIFY, reportId));
                log.info("QUALITY_REPORT_REJECTED_NOTIFY sent: sellerId={}, reportId={}",
                        sellerId, reportId);
            }

            log.info("Quality report rejected: reportId={}, adminId={}",
                    reportId, adminUser.getId());

        } catch (IllegalStateException e) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of("INVALID_STATUS", e.getMessage(), requestId), requestId));
        } catch (Exception e) {
            log.error("Admin reject quality report failed: requestId={}", requestId, e);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    private void handleGetNotifications(ClientSession session, String requestId) {
        // TODO: load notifications from persistence when NotificationDAO is available.
        session.send(Packet.of(PacketType.GET_NOTIFICATIONS_SUCCESS,
                List.<AdminDTOs.NotificationDTO>of(), requestId));
    }

    private void handleMarkNotificationRead(ClientSession session, JsonElement payload, String requestId) {
        try {
            PacketCodec.fromElement(payload, String.class);
            // TODO: persist read flag when NotificationDAO is available.
            session.send(Packet.of(PacketType.MARK_NOTIFICATION_READ_SUCCESS, null, requestId));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Lấy NormalUser từ session. Gửi SYSTEM_ERROR nếu user không phải NormalUser.
     * Copy từ BidHandler — UserAdminHandler cần dùng để xử lý SUBMIT_QUALITY_REPORT.
     */
    private com.group13.auction.model.user.NormalUser requireNormalUser(
            ClientSession session, String requestId) {
        com.group13.auction.model.user.User user =
                com.group13.auction.manager.AuctionManager.getInstance()
                        .findUserByUsername(session.getUsername());
        if (!(user instanceof com.group13.auction.model.user.NormalUser)) {
            log.warn("requireNormalUser failed: username={}, requestId={}",
                    session.getUsername(), requestId);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED,
                            "Chỉ NormalUser mới có thể thực hiện hành động này.", requestId), requestId));
            return null;
        }
        return (com.group13.auction.model.user.NormalUser) user;
    }
}