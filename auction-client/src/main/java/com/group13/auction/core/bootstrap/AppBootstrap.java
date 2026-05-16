package com.group13.auction.core.bootstrap;

import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.context.ServiceRegistry;
import com.group13.auction.core.state.ConnectionState;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.session.ClientEventListener;
import java.util.logging.Logger;

/**
 * Khởi tạo kết nối WebSocket và đăng ký toàn bộ service listener khi app JavaFX start.
 */
public final class AppBootstrap {

    private static final Logger LOG = Logger.getLogger(AppBootstrap.class.getName());

    private AppBootstrap() {}

    /**
     * Kết nối server và gắn dispatcher → services.
     */
    public static void initialize() {
        ClientNetworkFacade facade = ClientNetworkFacade.getDefault();
        ServiceRegistry registry = ServiceRegistry.getInstance();

        for (ClientEventListener listener : registry.networkListeners()) {
            facade.addListener(listener);
        }

        AppContext.getInstance().setConnectionState(ConnectionState.CONNECTING);
        boolean connected = facade.connectBlocking();
        AppContext.getInstance().setConnectionState(
                connected ? ConnectionState.CONNECTED : ConnectionState.DISCONNECTED);

        if (!connected) {
            LOG.warning("Không kết nối được WebSocket server — UI vẫn chạy ở chế độ offline.");
        }
    }

    /** Đóng kết nối khi thoát ứng dụng. */
    public static void shutdown() {
        ClientNetworkFacade.getDefault().shutdown();
        AppContext.getInstance().getSessionManager().clearSession();
        AppContext.getInstance().getScreenStateStore().clear();
        AppContext.getInstance().setConnectionState(ConnectionState.DISCONNECTED);
    }
}
