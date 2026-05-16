package com.group13.auction.service.base;

import com.group13.auction.network.client.facade.ClientNetworkFacade;

/**
 * Lớp cơ sở cho service gọi API qua WebSocket.
 */
public abstract class NetworkService {

    private final ClientNetworkFacade network;

    protected NetworkService() {
        this.network = ClientNetworkFacade.getDefault();
    }

    protected ClientNetworkFacade network() {
        return network;
    }
}
