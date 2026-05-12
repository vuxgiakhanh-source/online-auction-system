package com.group13.auction.core.context;

import com.group13.auction.core.session.SessionManager;
import com.group13.auction.service.auction.AuctionQueryService;
import com.group13.auction.service.auction.BidHistoryService;
import com.group13.auction.service.auction.BidService;
import com.group13.auction.service.auth.AuthService;
import com.group13.auction.service.network.NetworkGateway;
import com.group13.auction.service.seller.SellerAuctionService;
import com.group13.auction.service.wallet.WalletService;

/** Container thủ công đơn giản cho các service dùng chung trong client. */
public final class AppContext {

    private static final AppContext INSTANCE = new AppContext();

    private final NetworkGateway networkGateway = NetworkGateway.getInstance();
    private final SessionManager sessionManager = SessionManager.getInstance();
    private final AuthService authService = new AuthService(networkGateway, sessionManager);
    private final AuctionQueryService auctionQueryService = new AuctionQueryService(networkGateway);
    private final BidService bidService = new BidService(networkGateway);
    private final BidHistoryService bidHistoryService = new BidHistoryService(networkGateway);
    private final SellerAuctionService sellerAuctionService = new SellerAuctionService(networkGateway);
    private final WalletService walletService = new WalletService(networkGateway);

    private AppContext() {}

    public static AppContext getInstance() {
        return INSTANCE;
    }

    public NetworkGateway getNetworkGateway() {
        return networkGateway;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public AuctionQueryService getAuctionQueryService() {
        return auctionQueryService;
    }

    public BidService getBidService() {
        return bidService;
    }

    public BidHistoryService getBidHistoryService() {
        return bidHistoryService;
    }

    public SellerAuctionService getSellerAuctionService() {
        return sellerAuctionService;
    }

    public WalletService getWalletService() {
        return walletService;
    }
}
