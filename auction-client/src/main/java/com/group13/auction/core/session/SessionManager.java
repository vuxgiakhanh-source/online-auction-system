package com.group13.auction.core.session;

import java.util.Optional;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/**
 * Quản lý trạng thái đăng nhập của JavaFX client.
 *
 * <p>Lớp này chỉ lưu session trong memory khi app đang chạy, không lưu token xuống file.
 */
public final class SessionManager {

    private final BooleanProperty loggedIn = new SimpleBooleanProperty(false);

    private UserSession currentSession;

    /**
     * Lưu session mới sau khi đăng nhập hoặc đăng ký thành công.
     *
     * @param session session hiện tại
     */
    public void startSession(UserSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }

        currentSession = session;
        loggedIn.set(true);
    }

    /** Xóa session hiện tại khi logout hoặc mất xác thực. */
    public void clearSession() {
        currentSession = null;
        loggedIn.set(false);
    }

    /**
     * Kiểm tra client đã đăng nhập chưa.
     *
     * @return true nếu có session
     */
    public boolean isLoggedIn() {
        return loggedIn.get();
    }

    /**
     * Property trạng thái đăng nhập để UI có thể bind nếu cần.
     *
     * @return logged in property
     */
    public BooleanProperty loggedInProperty() {
        return loggedIn;
    }

    /**
     * Lấy session hiện tại dưới dạng optional.
     *
     * @return optional session
     */
    public Optional<UserSession> getCurrentSession() {
        return Optional.ofNullable(currentSession);
    }

    /**
     * Lấy session hiện tại, ném lỗi nếu chưa đăng nhập.
     *
     * @return session hiện tại
     */
    public UserSession requireSession() {
        return getCurrentSession()
                .orElseThrow(() -> new IllegalStateException("Người dùng chưa đăng nhập."));
    }

    /**
     * Lấy token hiện tại, dùng khi tạo request tới server.
     *
     * @return session token
     */
    public String requireToken() {
        return requireSession().getToken();
    }
}