package com.group13.auction.network.server.handler;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.dao.AuctionDAO;
import com.group13.auction.dao.ItemDAO;
import com.group13.auction.manager.AuctionManager;
import com.group13.auction.model.auction.Auction;
import com.group13.auction.model.item.Item;
import com.group13.auction.model.item.ItemFactory;
import com.group13.auction.model.user.Admin;
import com.group13.auction.model.user.NormalUser;
import com.group13.auction.model.user.User;
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.AuctionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AuctionHandler implements PacketHandler {

    private static final Logger log = LoggerFactory.getLogger(AuctionHandler.class);

    private static final Set<PacketType> SUPPORTED = EnumSet.of(
            PacketType.CREATE_AUCTION,
            PacketType.GET_AUCTION_LIST,
            PacketType.GET_AUCTION_DETAIL,
            PacketType.UPDATE_AUCTION,
            PacketType.CANCEL_AUCTION_REQUEST,
            PacketType.ADMIN_CANCEL_AUCTION,
            PacketType.ADMIN_GET_ALL_AUCTIONS
    );

    private final AuctionService auctionService;
    private final AccountService accountService;
    private final SessionManager sessionManager;
    private final ItemFactory itemFactory;
    private final ItemDAO itemDAO;
    private final AuctionDAO auctionDAO;

    public AuctionHandler(AuctionService auctionService,
                          AccountService accountService,
                          SessionManager sessionManager,
                          ItemFactory itemFactory) {
        this.auctionService = auctionService;
        this.accountService = accountService;
        this.sessionManager = sessionManager;
        this.itemFactory    = itemFactory;
        this.itemDAO        = new ItemDAO();
        this.auctionDAO     = new AuctionDAO();
    }

    @Override
    public boolean supports(PacketType type) { return SUPPORTED.contains(type); }

    @Override
    public void handle(ClientSession session, PacketType type,
                       JsonElement payload, String requestId) {
        if (!session.isAuthenticated()) {
            log.warn("Reject auction packet from unauthenticated session: type={}, requestId={}",
                    type, requestId);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chưa đăng nhập.", requestId), requestId));
            return;
        }

        switch (type) {
            case CREATE_AUCTION         -> handleCreate(session, payload, requestId);
            case GET_AUCTION_LIST       -> handleGetList(session, payload, requestId);
            case GET_AUCTION_DETAIL     -> handleGetDetail(session, payload, requestId);
            case UPDATE_AUCTION         -> handleUpdate(session, payload, requestId);
            case CANCEL_AUCTION_REQUEST -> handleCancelRequest(session, payload, requestId);
            case ADMIN_CANCEL_AUCTION   -> handleAdminCancel(session, payload, requestId);
            case ADMIN_GET_ALL_AUCTIONS -> handleAdminGetAll(session, payload, requestId);
            default -> {}
        }
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    private void handleCreate(ClientSession session, JsonElement payload, String requestId) {
        String savedItemId = null;
        try {
            AuctionDTOs.CreateAuctionRequestDTO req = PacketCodec.fromElement(
                    payload, AuctionDTOs.CreateAuctionRequestDTO.class);

            NormalUser seller = requireNormalUser(session, requestId);
            if (seller == null) return;

            // Kiểm tra user đã được phê duyệt làm Seller chưa.
            // items.seller_id là FK tới sellers(user_id) — nếu chưa có record
            // trong bảng sellers sẽ gây SQLIntegrityConstraintViolationException.
            if (!seller.hasRole(User.UserRole.SELLER)) {
                session.send(Packet.of(PacketType.CREATE_AUCTION_FAILED,
                        ErrorDTO.of(ErrorDTO.UNAUTHORIZED,
                                "Tài khoản chưa được phê duyệt làm người bán.", requestId), requestId));
                return;
            }
            List<String> imageUrls = req.getImageUrls() != null
                    ? req.getImageUrls() : List.of();

            // Validate số lượng ảnh
            if (imageUrls.size() > Item.MAX_IMAGES) {
                session.send(Packet.of(PacketType.CREATE_AUCTION_FAILED,
                        ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                                "Tối đa " + Item.MAX_IMAGES + " ảnh mỗi sản phẩm.",
                                requestId), requestId));
                return;
            }

            // Validate format URL — phải bắt đầu bằng /uploads/items/ và không chứa ký tự nguy hiểm
            for (String url : imageUrls) {
                if (url == null || !url.startsWith("/uploads/items/")
                        || url.contains("..") || url.contains("\\")
                        || url.length() > 200) {
                    session.send(Packet.of(PacketType.CREATE_AUCTION_FAILED,
                            ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                                    "URL ảnh không hợp lệ: " + url, requestId), requestId));
                    return;
                }
            }

            // 1. Tạo Item từ Factory (truyền imageUrls)
            Item item = itemFactory.create(
                    req.getItemCategory(),
                    req.getItemName(),
                    req.getItemDescription(),
                    (long) req.getStartingPrice(),
                    seller,
                    req.getItemExtraFields(),
                    imageUrls);

            savedItemId = item.getId();

            // 2. Lưu Item vào DB (truyền imageUrls để lưu cột image_urls)
            boolean itemSaved = itemDAO.addItem(
                    item.getId(),
                    seller.getId(),
                    item.getName(),
                    item.getDescription(),
                    item.getStartingPrice(),
                    req.getItemCategory().trim().toUpperCase(),
                    imageUrls);

            if (!itemSaved) {
                session.send(Packet.of(PacketType.CREATE_AUCTION_FAILED,
                        ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, "Lỗi lưu sản phẩm.", requestId), requestId));
                return;
            }

            // 3. Tạo Auction
            Auction auction = auctionService.createAuction(
                    seller,
                    item,
                    req.getStartTime(),
                    req.getEndTime(),
                    (long) req.getReservePrice());

            session.send(Packet.of(PacketType.CREATE_AUCTION_SUCCESS,
                    DTOMapper.toAuctionDTO(auction), requestId));
            log.info("Create auction: auctionId={}, sellerId={}, images={}, requestId={}",
                    auction.getId(), seller.getId(), imageUrls.size(), requestId);

        } catch (IllegalArgumentException | IllegalStateException e) {
            rollbackOrphanItem(savedItemId);
            log.warn("Create auction rejected: username={}, requestId={}, reason={}",
                    session.getUsername(), requestId, e.getMessage());
            session.send(Packet.of(PacketType.CREATE_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR, e.getMessage(), requestId), requestId));
        } catch (Exception e) {
            rollbackOrphanItem(savedItemId);
            log.error("Create auction failed: username={}, requestId={}",
                    session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.CREATE_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    /** Xóa item đã insert nếu tạo phiên thất bại sau bước lưu sản phẩm. */
    private void rollbackOrphanItem(String itemId) {
        if (itemId == null) return;
        try {
            if (itemDAO.deleteItem(itemId)) {
                log.info("Rolled back orphan item after failed create auction: itemId={}", itemId);
            }
        } catch (Exception ex) {
            log.warn("Could not rollback orphan item: itemId={}, error={}", itemId, ex.getMessage());
        }
    }

    // ── GET LIST ──────────────────────────────────────────────────────────────

    private void handleGetList(ClientSession session, JsonElement payload, String requestId) {
        try {
            AuctionDTOs.AuctionListRequestDTO req = payload != null
                    ? PacketCodec.fromElement(payload, AuctionDTOs.AuctionListRequestDTO.class)
                    : new AuctionDTOs.AuctionListRequestDTO();

            List<Auction> auctions;
            if (req.getStatusFilter() != null && !req.getStatusFilter().isEmpty()) {
                Auction.AuctionStatus status = Auction.AuctionStatus.valueOf(req.getStatusFilter());
                auctions = AuctionManager.getInstance().getAuctionsByStatus(status);
            } else {
                auctions = AuctionManager.getInstance().getAllAuctions();
            }

            List<AuctionDTOs.AuctionDTO> dtos = auctions.stream()
                    .map(DTOMapper::toAuctionDTO)
                    .collect(Collectors.toList());

            session.send(Packet.of(PacketType.GET_AUCTION_LIST_SUCCESS,
                    new AuctionDTOs.AuctionListDTO(dtos, dtos.size()), requestId));
            log.debug("Auction list returned: username={}, count={}, requestId={}",
                    session.getUsername(), dtos.size(), requestId);
        } catch (Exception e) {
            log.error("Get auction list failed: username={}, requestId={}",
                    session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── GET DETAIL ────────────────────────────────────────────────────────────

    private void handleGetDetail(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction  = AuctionManager.getInstance().findAuctionById(auctionId);
            if (auction == null) {
                session.send(Packet.of(PacketType.GET_AUCTION_DETAIL_FAILED,
                        ErrorDTO.of(ErrorDTO.AUCTION_NOT_FOUND, "Không tìm thấy.", requestId), requestId));
                return;
            }
            session.send(Packet.of(PacketType.GET_AUCTION_DETAIL_SUCCESS,
                    DTOMapper.toAuctionDTO(auction), requestId));
        } catch (Exception e) {
            log.error("Get auction detail failed: username={}, requestId={}",
                    session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.GET_AUCTION_DETAIL_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    private void handleUpdate(ClientSession session, JsonElement payload, String requestId) {
        try {
            AuctionDTOs.UpdateAuctionDTO req = PacketCodec.fromElement(
                    payload, AuctionDTOs.UpdateAuctionDTO.class);
            NormalUser seller = requireNormalUser(session, requestId);
            if (seller == null) return;

            Auction auction = AuctionManager.getInstance().findAuctionById(req.getAuctionId());
            if (auction == null || auction.getStatus() != Auction.AuctionStatus.OPEN) {
                session.send(Packet.of(PacketType.UPDATE_AUCTION_FAILED,
                        ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                                "Phiên không tồn tại hoặc đã bắt đầu.", requestId), requestId));
                return;
            }

            if (req.getNewEndTime() != null
                    && req.getNewEndTime().isAfter(auction.getStartTime())) {
                auction.extendEndTime(
                        java.time.Duration.between(auction.getEndTime(), req.getNewEndTime()));
                auctionDAO.updateEndTime(auction.getId(), auction.getEndTime());
            }

            session.send(Packet.of(PacketType.UPDATE_AUCTION_SUCCESS,
                    DTOMapper.toAuctionDTO(auction), requestId));
            log.info("Update auction: auctionId={}, sellerId={}, requestId={}",
                    auction.getId(), seller.getId(), requestId);
        } catch (Exception e) {
            log.error("Update auction failed: username={}, requestId={}",
                    session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.UPDATE_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── CANCEL REQUEST ────────────────────────────────────────────────────────

    private void handleCancelRequest(ClientSession session, JsonElement payload, String requestId) {
        try {
            AuctionDTOs.CancelAuctionRequestDTO req = PacketCodec.fromElement(
                    payload, AuctionDTOs.CancelAuctionRequestDTO.class);
            NormalUser seller = requireNormalUser(session, requestId);
            if (seller == null) return;

            Auction auction = AuctionManager.getInstance().findAuctionById(req.getAuctionId());
            accountService.requestCancelAuction(seller, auction, req.getReason());

            session.send(Packet.of(PacketType.CANCEL_AUCTION_REQUEST_SUCCESS,
                    req.getAuctionId(), requestId));
            log.info("Cancel request: auctionId={}, sellerId={}, requestId={}",
                    req.getAuctionId(), seller.getId(), requestId);
        } catch (Exception e) {
            log.error("Cancel auction request failed: username={}, requestId={}",
                    session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.CANCEL_AUCTION_REQUEST_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── ADMIN CANCEL ──────────────────────────────────────────────────────────

    private void handleAdminCancel(ClientSession session, JsonElement payload, String requestId) {
        if (!session.isAdmin()) {
            log.warn("Reject admin cancel from non-admin: username={}, requestId={}",
                    session.getUsername(), requestId);
            return;
        }
        try {
            AuctionDTOs.AdminCancelAuctionDTO req = PacketCodec.fromElement(
                    payload, AuctionDTOs.AdminCancelAuctionDTO.class);
            Auction auction = AuctionManager.getInstance().findAuctionById(req.getAuctionId());
            Admin admin = (Admin) AuctionManager.getInstance()
                    .findUserByUsername(session.getUsername());

            auctionService.cancelAuction(admin, auction, Admin.CancelReason.valueOf(req.getReason()));
            session.send(Packet.of(PacketType.ADMIN_CANCEL_AUCTION_SUCCESS,
                    DTOMapper.toAuctionDTO(auction), requestId));
            log.info("Admin cancel: auctionId={}, adminId={}, reason={}, requestId={}",
                    req.getAuctionId(), admin.getId(), req.getReason(), requestId);
        } catch (Exception e) {
            log.error("Admin cancel failed: username={}, requestId={}",
                    session.getUsername(), requestId, e);
            session.send(Packet.of(PacketType.ADMIN_CANCEL_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId), requestId));
        }
    }

    // ── ADMIN GET ALL ─────────────────────────────────────────────────────────

    private void handleAdminGetAll(ClientSession session, JsonElement payload, String requestId) {
        if (!session.isAdmin()) {
            log.warn("Reject admin get all from non-admin: username={}, requestId={}",
                    session.getUsername(), requestId);
            return;
        }
        List<AuctionDTOs.AuctionDTO> dtos = AuctionManager.getInstance().getAllAuctions()
                .stream().map(DTOMapper::toAuctionDTO).collect(Collectors.toList());
        session.send(Packet.of(PacketType.ADMIN_GET_ALL_AUCTIONS_SUCCESS,
                new AuctionDTOs.AuctionListDTO(dtos, dtos.size()), requestId));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private NormalUser requireNormalUser(ClientSession session, String requestId) {
        com.group13.auction.model.user.User user =
                AuctionManager.getInstance().findUserByUsername(session.getUsername());
        if (!(user instanceof NormalUser)) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Quyền hạn không hợp lệ.", requestId), requestId));
            return null;
        }
        return (NormalUser) user;
    }
}