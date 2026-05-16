package com.group13.auction.core.context;

import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.session.SessionManager;
import com.group13.auction.core.state.ConnectionState;
import com.group13.auction.core.state.ScreenStateStore;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Application context dùng chung trong client.
 *
 * <p>Lớp này giữ các singleton nhẹ ở phía client như navigator, session và screen state. Không đặt
 * nghiệp vụ đấu giá ở đây.
 */
public final class AppContext {

    private static final AppContext INSTANCE = new AppContext();

    private final SessionManager sessionManager = new SessionManager();
    private final ScreenStateStore screenStateStore = new ScreenStateStore();
    private final ObjectProperty<ConnectionState> connectionState =
            new SimpleObjectProperty<>(ConnectionState.DISCONNECTED);

    private Navigator navigator;

    private AppContext() {}

    /**
     * Lấy context dùng chung của client.
     *
     * @return singleton application context
     */
    public static AppContext getInstance() {
        return INSTANCE;
    }

    /**
     * Gắn navigator sau khi JavaFX stage đã được khởi tạo.
     *
     * @param navigator navigator chính của ứng dụng
     */
    public void setNavigator(Navigator navigator) {
        this.navigator = Objects.requireNonNull(navigator, "navigator must not be null");
    }

    /**
     * Lấy navigator hiện tại.
     *
     * @return navigator chính
     */
    public Navigator getNavigator() {
        if (navigator == null) {
            throw new IllegalStateException("Navigator chưa được khởi tạo.");
        }
        return navigator;
    }

    /**
     * Lấy session manager của client.
     *
     * @return session manager
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Lấy kho state tạm thời giữa các màn hình.
     *
     * @return screen state store
     */
    public ScreenStateStore getScreenStateStore() {
        return screenStateStore;
    }

    /**
     * Property trạng thái kết nối để UI có thể bind hoặc lắng nghe thay đổi.
     *
     * @return connection state property
     */
    public ObjectProperty<ConnectionState> connectionStateProperty() {
        return connectionState;
    }

    public ConnectionState getConnectionState() {
        return connectionState.get();
    }

    public void setConnectionState(ConnectionState state) {
        connectionState.set(Objects.requireNonNull(state, "state must not be null"));
    }
}