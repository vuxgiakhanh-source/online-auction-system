package com.group13.auction.service.auction;

import com.group13.auction.common.dto.auction.AuctionDTOs;
import com.group13.auction.core.workflow.AuctionLifecyclePhase;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Broadcast lifecycle phiên: started / ended / canceled / extended.
 */
public final class AuctionRealtimeService extends NetworkService implements ClientEventListener {

    private final ObjectProperty<AuctionLifecyclePhase> phase =
            new SimpleObjectProperty<>(AuctionLifecyclePhase.OPEN);
    private final ObjectProperty<AuctionDTOs.AuctionUpdateDTO> lastUpdate =
            new SimpleObjectProperty<>();

    public ObjectProperty<AuctionLifecyclePhase> phaseProperty() {
        return phase;
    }

    public ObjectProperty<AuctionDTOs.AuctionUpdateDTO> lastUpdateProperty() {
        return lastUpdate;
    }

    @Override
    public void onAuctionStarted(AuctionDTOs.AuctionUpdateDTO update) {
        phase.set(AuctionLifecyclePhase.RUNNING);
        lastUpdate.set(update);
    }

    @Override
    public void onAuctionEnded(AuctionDTOs.AuctionUpdateDTO update) {
        phase.set(AuctionLifecyclePhase.FINISHED_WITH_WINNER);
        lastUpdate.set(update);
    }

    @Override
    public void onAuctionNoWinner(AuctionDTOs.AuctionUpdateDTO update) {
        phase.set(AuctionLifecyclePhase.NO_WINNER);
        lastUpdate.set(update);
    }

    @Override
    public void onAuctionReserveNotMet(AuctionDTOs.AuctionUpdateDTO update) {
        phase.set(AuctionLifecyclePhase.RESERVE_NOT_MET);
        lastUpdate.set(update);
    }

    @Override
    public void onAuctionCanceled(AuctionDTOs.AuctionUpdateDTO update) {
        phase.set(AuctionLifecyclePhase.CANCELED);
        lastUpdate.set(update);
    }

    @Override
    public void onAuctionExtended(AuctionDTOs.AuctionExtendedDTO dto) {
        phase.set(AuctionLifecyclePhase.EXTENDED);
    }

    @Override
    public void onAuctionUpcomingEnd(AuctionDTOs.AuctionUpcomingEndDTO dto) {
        phase.set(AuctionLifecyclePhase.UPCOMING_END);
    }
}
