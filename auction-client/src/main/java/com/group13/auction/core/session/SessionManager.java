package com.group13.auction.core.session;

import com.group13.auction.common.dto.auth.LoginResponseDTO;
import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.network.client.AuctionWebSocketClient;
import java.util.List;
import java.util.Optional;

/** Quản lý phiên đăng nhập hiện tại ở phía client. */
public final class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();

    private UserSession currentSession;

    private SessionManager() {}

    /**
     * Thực thi thao tác getInstance của thành phần client.
     */
    public static SessionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Trả về giá trị currentSession dùng cho tầng giao diện hoặc service.
     *
     * @return giá trị currentSession
     */
    public Optional<UserSession> getCurrentSession() {
        return Optional.ofNullable(currentSession);
    }

    /**
     * Kiểm tra trạng thái loggedIn hiện tại.
     *
     * @return {@code true} nếu điều kiện đúng, ngược lại {@code false}
     */
    public boolean isLoggedIn() {
        return currentSession != null;
    }

    /**
     * Khởi tạo stage chính của ứng dụng JavaFX.
     *
     * @param primaryStage stage chính được JavaFX cung cấp
     */
    public void start(LoginResponseDTO response) {
        if (response == null || response.getUser() == null) {
            return;
        }
        currentSession = new UserSession(response.getToken(), response.getUser());
        try {
            AuctionWebSocketClient.getInstance()
                    .setAuthState(
                            response.getToken(), response.getUser().getId(), response.getUser().getUsername());
        } catch (IllegalStateException ignored) {
            // Network chưa khởi tạo thì UI vẫn dùng session nội bộ.
        }
    }

    /** Tạo session demo để em test UI khi server chưa chạy. */
    /**
     * Thực thi thao tác startDemo của thành phần client.
     */
    public void startDemo(String username) {
        UserDTO user = new UserDTO();
        user.setId("U-DEMO-01");
        user.setUsername(username == null || username.isBlank() ? "beo.demo" : username);
        user.setEmail("demo@omnibid.local");
        user.setRoles(List.of("BIDDER", "SELLER"));
        user.setAccountStatus("ACTIVE");
        user.setRating(4.9);
        user.setBalance(8_500_000);
        user.setLockedDeposit(650_000);
        user.setAvailableBalance(7_850_000);
        currentSession = new UserSession("demo-token", user);
    }

    /**
     * Thực thi thao tác clear của thành phần client.
     */
    public void clear() {
        currentSession = null;
        try {
            AuctionWebSocketClient.getInstance().clearAuthState();
        } catch (IllegalStateException ignored) {
            // Không có connection để clear.
        }
    }
}
