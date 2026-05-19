package com.group13.auction.service.iservice;

import com.group13.auction.network.server.session.SessionManager;

/**
 * Hợp đồng scheduler nền quản lý vòng đời phiên đấu giá theo thời gian.
 */
public interface IAuctionTimerService {

    /**
     * Khởi động vòng quét định kỳ. Gọi một lần khi server start.
     */
    void start(IAuctionService auctionService,
               IPaymentService paymentService,
               SessionManager sessionManager);

    /**
     * Dừng scheduler. Gọi khi server shutdown.
     */
    void stop();
}
