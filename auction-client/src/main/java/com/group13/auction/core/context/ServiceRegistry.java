package com.group13.auction.core.context;

import com.group13.auction.service.admin.AdminAuctionService;
import com.group13.auction.service.admin.AdminModerationService;
import com.group13.auction.service.admin.AdminUserService;
import com.group13.auction.service.auction.AuctionCommandService;
import com.group13.auction.service.auction.AuctionRealtimeService;
import com.group13.auction.service.auction.AutoBidService;
import com.group13.auction.service.auction.BidHistoryService;
import com.group13.auction.service.auction.BidService;
import com.group13.auction.service.auction.WatchAuctionService;
import com.group13.auction.service.auth.AuthService;
import com.group13.auction.service.chatbot.ChatbotService;
import com.group13.auction.service.notification.NotificationService;
import com.group13.auction.service.payment.PaymentService;
import com.group13.auction.service.profile.ProfileService;
import com.group13.auction.service.rating.RatingService;
import com.group13.auction.service.report.QualityReportService;
import com.group13.auction.service.seller.SellerAuctionService;
import com.group13.auction.service.support.ClientNotificationService;
import com.group13.auction.service.support.ErrorHandlingService;
import com.group13.auction.service.support.PermissionService;
import com.group13.auction.service.support.PushNavigationService;
import com.group13.auction.service.wallet.WalletService;
import com.group13.auction.network.client.session.ClientEventListener;
import java.util.List;

/**
 * Registry tập trung toàn bộ service phía client — mỗi service ánh xạ 1 domain packet/server handler.
 */
public final class ServiceRegistry {

    private static final ServiceRegistry INSTANCE = new ServiceRegistry();

    private final AuthService authService = new AuthService();
    private final ProfileService profileService = new ProfileService();
    private final WalletService walletService = new WalletService();
    private final PaymentService paymentService = new PaymentService();
    private final AuctionCommandService auctionCommandService = new AuctionCommandService();
    private final SellerAuctionService sellerAuctionService = new SellerAuctionService();
    private final WatchAuctionService watchAuctionService = new WatchAuctionService();
    private final BidService bidService = new BidService();
    private final AutoBidService autoBidService = new AutoBidService();
    private final BidHistoryService bidHistoryService = new BidHistoryService();
    private final AuctionRealtimeService auctionRealtimeService = new AuctionRealtimeService();
    private final AdminUserService adminUserService = new AdminUserService();
    private final AdminAuctionService adminAuctionService = new AdminAuctionService();
    private final AdminModerationService adminModerationService = new AdminModerationService();
    private final RatingService ratingService = new RatingService();
    private final QualityReportService qualityReportService = new QualityReportService();
    private final NotificationService notificationService = new NotificationService();
    private final ChatbotService chatbotService = new ChatbotService();
    private final ClientNotificationService clientNotificationService = new ClientNotificationService();
    private final ErrorHandlingService errorHandlingService = new ErrorHandlingService();
    private final PushNavigationService pushNavigationService = new PushNavigationService();
    private final PermissionService permissionService = PermissionService.getInstance();

    private ServiceRegistry() {}

    public static ServiceRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Tất cả listener đăng ký vào {@link com.group13.auction.network.client.facade.ClientNetworkFacade}.
     */
    public List<ClientEventListener> networkListeners() {
        return List.of(
                authService,
                profileService,
                walletService,
                paymentService,
                auctionCommandService,
                sellerAuctionService,
                watchAuctionService,
                bidService,
                autoBidService,
                bidHistoryService,
                auctionRealtimeService,
                adminUserService,
                adminAuctionService,
                adminModerationService,
                ratingService,
                qualityReportService,
                notificationService,
                chatbotService,
                clientNotificationService,
                errorHandlingService,
                pushNavigationService);
    }

    public AuthService authService() { return authService; }
    public ProfileService profileService() { return profileService; }
    public WalletService walletService() { return walletService; }
    public PaymentService paymentService() { return paymentService; }
    public AuctionCommandService auctionCommandService() { return auctionCommandService; }
    public SellerAuctionService sellerAuctionService() { return sellerAuctionService; }
    public WatchAuctionService watchAuctionService() { return watchAuctionService; }
    public BidService bidService() { return bidService; }
    public AutoBidService autoBidService() { return autoBidService; }
    public BidHistoryService bidHistoryService() { return bidHistoryService; }
    public AuctionRealtimeService auctionRealtimeService() { return auctionRealtimeService; }
    public AdminUserService adminUserService() { return adminUserService; }
    public AdminAuctionService adminAuctionService() { return adminAuctionService; }
    public AdminModerationService adminModerationService() { return adminModerationService; }
    public RatingService ratingService() { return ratingService; }
    public QualityReportService qualityReportService() { return qualityReportService; }
    public NotificationService notificationService() { return notificationService; }
    public ChatbotService chatbotService() { return chatbotService; }
    public ClientNotificationService clientNotificationService() { return clientNotificationService; }
    public ErrorHandlingService errorHandlingService() { return errorHandlingService; }
    public PermissionService permissionService() { return permissionService; }
}
