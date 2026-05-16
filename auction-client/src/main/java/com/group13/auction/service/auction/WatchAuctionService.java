package com.group13.auction.service.auction;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;

/**
 * JOIN / WATCH / LEAVE phiên đấu giá.
 */
public final class WatchAuctionService extends NetworkService implements ClientEventListener {

    public void join(String auctionId) {
        network().joinAuction(auctionId);
    }

    public void watch(String auctionId) {
        network().watchAuction(auctionId);
    }

    public void leave(String auctionId) {
        network().leaveAuction(auctionId);
    }

    @Override
    public void onJoinAuctionSuccess(AuctionDTOs.JoinAuctionResponseDTO response) {}

    @Override
    public void onJoinAuctionFailed(ErrorDTO error) {}

    @Override
    public void onWatchAuctionSuccess(AuctionDTOs.AuctionDTO auction) {}

    @Override
    public void onWatchAuctionFailed(ErrorDTO error) {}

    @Override
    public void onLeaveAuctionSuccess() {}
}
