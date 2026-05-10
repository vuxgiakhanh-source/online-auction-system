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
import com.group13.auction.network.server.session.ClientSession;
import com.group13.auction.network.server.session.SessionManager;
import com.group13.auction.network.server.util.DTOMapper;
import com.group13.auction.service.AccountService;
import com.group13.auction.service.AuctionService;
import com.group13.auction.strategy.ReservePriceStrategy;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xử lý các packet liên quan đến vòng đời phiên đấu giá:
 * CREATE_AUCTION, GET_AUCTION_LIST, GET_AUCTION_DETAIL,
 * UPDATE_AUCTION, CANCEL_AUCTION_REQUEST, ADMIN_CANCEL_AUCTION,
 * ADMIN_GET_ALL_AUCTIONS.
 *
 * FIX (Bug #1): Inject ItemDAO; gọi itemDAO.addItem() trước khi createAuction()
 *               để tránh FK constraint violation trong bảng auctions(item_id).
 * FIX (Bug #2): Throw RuntimeException thay vì return false khi DAO fail,
 *               đảm bảo handler luôn gửi FAILED về client thay vì SUCCESS giả.
 */
public class AuctionHandler implements PacketHandler {

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
    // FIX Bug #1: cần ItemDAO để persist item trước khi tạo auction
    private final ItemDAO itemDAO;
    // FIX Vấn đề 1: cần AuctionDAO để persist endTime khi UPDATE_AUCTION
    private final AuctionDAO auctionDAO;

    public AuctionHandler(AuctionService auctionService,
                          AccountService accountService,
                          SessionManager sessionManager,
                          ItemFactory itemFactory) {
        this.auctionService = auctionService;
        this.accountService = accountService;
        this.sessionManager = sessionManager;
        this.itemFactory = itemFactory;
        this.itemDAO = new ItemDAO();   // FIX Bug #1
        this.auctionDAO = new AuctionDAO(); // FIX Vấn đề 1
    }

    @Override
    public boolean supports(PacketType type) { return SUPPORTED.contains(type); }

    @Override
    public void handle(ClientSession session, PacketType type,
                       JsonElement payload, String requestId) {
        if (!session.isAuthenticated()) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chưa đăng nhập.", requestId)));
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
        try {
            AuctionDTOs.CreateAuctionRequestDTO req = PacketCodec.fromElement(
                    payload, AuctionDTOs.CreateAuctionRequestDTO.class);

            NormalUser seller = requireNormalUser(session, requestId);
            if (seller == null) return;

            Item item = itemFactory.create(
                    req.getItemCategory(),
                    req.getItemName(),
                    req.getItemDescription(),
                    (long) req.getStartingPrice(),
                    seller,
                    req.getItemExtraFields());

            // ── FIX Bug #1: persist Item vào DB TRƯỚC khi tạo Auction ────────
            // AuctionDAO.createAuction() INSERT với item_id là FK → nếu item chưa
            // có trong bảng items thì SQLException: FK constraint violation.
            boolean itemSaved = itemDAO.addItem(
                    item.getId(),
                    seller.getId(),
                    item.getName(),
                    item.getDescription(),
                    item.getStartingPrice(),
                    req.getItemCategory().trim().toUpperCase());

            if (!itemSaved) {
                session.send(Packet.of(PacketType.CREATE_AUCTION_FAILED,
                        ErrorDTO.of(ErrorDTO.INTERNAL_ERROR,
                                "Không thể lưu sản phẩm vào cơ sở dữ liệu.", requestId)));
                return;
            }
            // ─────────────────────────────────────────────────────────────────

            ReservePriceStrategy reserveStrategy =
                    new ReservePriceStrategy((long) req.getReservePrice());

            Auction auction = auctionService.createAuction(
                    seller, item, req.getStartTime(), req.getEndTime(), reserveStrategy.getReservePrice());

            session.send(Packet.of(PacketType.CREATE_AUCTION_SUCCESS,
                    DTOMapper.toAuctionDTO(auction), requestId));

        } catch (IllegalArgumentException | IllegalStateException e) {
            session.send(Packet.of(PacketType.CREATE_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR, e.getMessage(), requestId)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.CREATE_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
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

            AuctionDTOs.AuctionListDTO listDTO = new AuctionDTOs.AuctionListDTO(dtos, dtos.size());
            session.send(Packet.of(PacketType.GET_AUCTION_LIST_SUCCESS, listDTO, requestId));

        } catch (Exception e) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── GET DETAIL ────────────────────────────────────────────────────────────

    private void handleGetDetail(ClientSession session, JsonElement payload, String requestId) {
        try {
            String auctionId = PacketCodec.fromElement(payload, String.class);
            Auction auction = AuctionManager.getInstance().findAuctionById(auctionId);
            if (auction == null) {
                session.send(Packet.of(PacketType.GET_AUCTION_DETAIL_FAILED,
                        ErrorDTO.of(ErrorDTO.AUCTION_NOT_FOUND,
                                "Phiên không tồn tại: " + auctionId, requestId)));
                return;
            }
            session.send(Packet.of(PacketType.GET_AUCTION_DETAIL_SUCCESS,
                    DTOMapper.toAuctionDTO(auction), requestId));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.GET_AUCTION_DETAIL_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
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
            if (auction == null) {
                session.send(Packet.of(PacketType.UPDATE_AUCTION_FAILED,
                        ErrorDTO.of(ErrorDTO.AUCTION_NOT_FOUND, "Phiên không tồn tại.", requestId)));
                return;
            }
            if (auction.getStatus() != Auction.AuctionStatus.OPEN) {
                session.send(Packet.of(PacketType.UPDATE_AUCTION_FAILED,
                        ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                                "Chỉ có thể cập nhật phiên ở trạng thái OPEN.", requestId)));
                return;
            }

            // Kiểm tra seller có phải chủ phiên không
            if (!seller.getAllAuctionIds().contains(auction.getId())) {
                session.send(Packet.of(PacketType.UPDATE_AUCTION_FAILED,
                        ErrorDTO.of(ErrorDTO.UNAUTHORIZED,
                                "Bạn không phải chủ sở hữu phiên đấu giá này.", requestId)));
                return;
            }

            boolean endTimeUpdated = false;

            // Cập nhật endTime nếu được cung cấp và hợp lệ
            if (req.getNewEndTime() != null) {
                if (!req.getNewEndTime().isAfter(auction.getStartTime())) {
                    session.send(Packet.of(PacketType.UPDATE_AUCTION_FAILED,
                            ErrorDTO.of(ErrorDTO.VALIDATION_ERROR,
                                    "endTime mới phải sau startTime của phiên.", requestId)));
                    return;
                }
                if (req.getNewEndTime().isAfter(auction.getEndTime())) {
                    java.time.Duration extension = java.time.Duration.between(
                            auction.getEndTime(), req.getNewEndTime());
                    auction.extendEndTime(extension);
                    endTimeUpdated = true;
                }
            }

            // Persist endTime nếu có thay đổi
            if (endTimeUpdated) {
                auctionDAO.updateEndTime(auction.getId(), auction.getEndTime());
            }

            // Lưu ý: reservePrice là final trong Auction model — không thể thay đổi in-memory
            // sau khi tạo phiên (thiết kế có chủ đích để bảo toàn tính toàn vẹn của phiên).
            // Nếu cần hỗ trợ update reservePrice, model cần thêm setter và AuctionDAO.updateReservePrice().

            session.send(Packet.of(PacketType.UPDATE_AUCTION_SUCCESS,
                    DTOMapper.toAuctionDTO(auction), requestId));

        } catch (IllegalArgumentException e) {
            session.send(Packet.of(PacketType.UPDATE_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR, e.getMessage(), requestId)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.UPDATE_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── CANCEL REQUEST (Seller) ───────────────────────────────────────────────

    private void handleCancelRequest(ClientSession session, JsonElement payload, String requestId) {
        try {
            AuctionDTOs.CancelAuctionRequestDTO req = PacketCodec.fromElement(
                    payload, AuctionDTOs.CancelAuctionRequestDTO.class);

            NormalUser seller = requireNormalUser(session, requestId);
            if (seller == null) return;

            Auction auction = AuctionManager.getInstance().findAuctionById(req.getAuctionId());
            if (auction == null) {
                session.send(Packet.of(PacketType.CANCEL_AUCTION_REQUEST_FAILED,
                        ErrorDTO.of(ErrorDTO.AUCTION_NOT_FOUND, "Phiên không tồn tại.", requestId)));
                return;
            }

            accountService.requestCancelAuction(seller, auction, req.getReason());
            session.send(Packet.of(PacketType.CANCEL_AUCTION_REQUEST_SUCCESS,
                    req.getAuctionId(), requestId));

            AuctionDTOs.SellerCancelRequestNotifyDTO notify = new AuctionDTOs.SellerCancelRequestNotifyDTO();
            notify.setAuctionId(req.getAuctionId());
            notify.setAuctionName(auction.getItem().getName());
            notify.setSellerUsername(seller.getUsername());
            notify.setReason(req.getReason());
            notify.setRequestTime(java.time.LocalDateTime.now());
            sessionManager.broadcastToAdmins(Packet.of(PacketType.SELLER_CANCEL_REQUEST_NOTIFY, notify));

        } catch (IllegalArgumentException | IllegalStateException e) {
            session.send(Packet.of(PacketType.CANCEL_AUCTION_REQUEST_FAILED,
                    ErrorDTO.of(ErrorDTO.VALIDATION_ERROR, e.getMessage(), requestId)));
        } catch (Exception e) {
            session.send(Packet.of(PacketType.CANCEL_AUCTION_REQUEST_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── ADMIN CANCEL ──────────────────────────────────────────────────────────

    private void handleAdminCancel(ClientSession session, JsonElement payload, String requestId) {
        if (!session.isAdmin()) {
            session.send(Packet.of(PacketType.ADMIN_CANCEL_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chỉ Admin mới có thể hủy phiên.", requestId)));
            return;
        }
        try {
            AuctionDTOs.AdminCancelAuctionDTO req = PacketCodec.fromElement(
                    payload, AuctionDTOs.AdminCancelAuctionDTO.class);

            Auction auction = AuctionManager.getInstance().findAuctionById(req.getAuctionId());
            if (auction == null) {
                session.send(Packet.of(PacketType.ADMIN_CANCEL_AUCTION_FAILED,
                        ErrorDTO.of(ErrorDTO.AUCTION_NOT_FOUND, "Phiên không tồn tại.", requestId)));
                return;
            }

            Admin.CancelReason reason = Admin.CancelReason.valueOf(req.getReason());

            // FIX Bug #3: dùng overload cancelAuction(staff, auction, reason) để ghi audit log
            // thay vì cancelAuction(auction, reason) vốn chỉ dành cho system auto-cancel.
            com.group13.auction.model.user.User adminUser =
                    AuctionManager.getInstance().findUserByUsername(session.getUsername());
            if (!(adminUser instanceof Admin)) {
                session.send(Packet.of(PacketType.ADMIN_CANCEL_AUCTION_FAILED,
                        ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Không tìm thấy tài khoản Admin.", requestId)));
                return;
            }
            auctionService.cancelAuction((Admin) adminUser, auction, reason);

            session.send(Packet.of(PacketType.ADMIN_CANCEL_AUCTION_SUCCESS,
                    DTOMapper.toAuctionDTO(auction), requestId));

            AuctionDTOs.AuctionUpdateDTO update = DTOMapper.toAuctionUpdateDTO(auction, req.getReason());
            sessionManager.broadcastToAuction(req.getAuctionId(),
                    Packet.of(PacketType.AUCTION_CANCELED_UPDATE, update));

        } catch (Exception e) {
            session.send(Packet.of(PacketType.ADMIN_CANCEL_AUCTION_FAILED,
                    ErrorDTO.of(ErrorDTO.INTERNAL_ERROR, e.getMessage(), requestId)));
        }
    }

    // ── ADMIN GET ALL ─────────────────────────────────────────────────────────

    private void handleAdminGetAll(ClientSession session, JsonElement payload, String requestId) {
        if (!session.isAdmin()) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Không có quyền.", requestId)));
            return;
        }
        List<AuctionDTOs.AuctionDTO> dtos = AuctionManager.getInstance()
                .getAllAuctions().stream()
                .map(DTOMapper::toAuctionDTO)
                .collect(Collectors.toList());
        AuctionDTOs.AuctionListDTO listDTO = new AuctionDTOs.AuctionListDTO(dtos, dtos.size());
        session.send(Packet.of(PacketType.ADMIN_GET_ALL_AUCTIONS_SUCCESS, listDTO, requestId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private NormalUser requireNormalUser(ClientSession session, String requestId) {
        com.group13.auction.model.user.User user =
                AuctionManager.getInstance().findUserByUsername(session.getUsername());
        if (!(user instanceof NormalUser)) {
            session.send(Packet.of(PacketType.SYSTEM_ERROR,
                    ErrorDTO.of(ErrorDTO.UNAUTHORIZED, "Chỉ NormalUser mới được phép.", requestId)));
            return null;
        }
        return (NormalUser) user;
    }
}