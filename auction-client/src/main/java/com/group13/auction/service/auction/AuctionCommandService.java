package com.group13.auction.service.auction;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * CRUD phiên đấu giá phía bidder/public: list, detail.
 */
public final class AuctionCommandService extends NetworkService implements ClientEventListener {

    private final ObjectProperty<AuctionDTOs.AuctionListDTO> auctionList =
            new SimpleObjectProperty<>();
    private final ObjectProperty<AuctionDTOs.AuctionDTO> selectedAuction =
            new SimpleObjectProperty<>();

    public ObjectProperty<AuctionDTOs.AuctionListDTO> auctionListProperty() {
        return auctionList;
    }

    public ObjectProperty<AuctionDTOs.AuctionDTO> selectedAuctionProperty() {
        return selectedAuction;
    }

    public void loadAuctionList() {
        network().getAuctionList();
    }

    public void loadAuctionList(AuctionDTOs.AuctionListRequestDTO request) {
        network().getAuctionList(request);
    }

    public void loadAuctionDetail(String auctionId) {
        network().getAuctionDetail(auctionId);
    }

    @Override
    public void onAuctionListReceived(AuctionDTOs.AuctionListDTO list) {
        auctionList.set(list);
    }

    @Override
    public void onAuctionDetailReceived(AuctionDTOs.AuctionDTO auction) {
        selectedAuction.set(auction);
    }

    @Override
    public void onAuctionDetailFailed(ErrorDTO error) {}
}
