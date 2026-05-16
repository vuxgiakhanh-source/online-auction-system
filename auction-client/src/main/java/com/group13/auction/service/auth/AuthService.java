package com.group13.auction.service.auth;

import com.group13.auction.common.dto.auth.LoginResponseDTO;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.network.client.AuctionWebSocketClient;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import com.group13.auction.ui.util.AlertUtil;
import com.group13.auction.ui.util.ErrorMessages;
import com.group13.auction.ui.util.FxThreadUtil;
import java.util.function.Consumer;

/**
 * Luồng AUTH: REGISTER / LOGIN / LOGOUT.
 */
public final class AuthService extends NetworkService implements ClientEventListener {

    private Consumer<Boolean> loginCallback;
    private Consumer<String> loginErrorCallback;
    private Consumer<Boolean> registerCallback;
    private Consumer<String> registerErrorCallback;
    private Runnable logoutCallback;

    public void login(String username, String password, Consumer<Boolean> onResult, Consumer<String> onError) {
        this.loginCallback = onResult;
        this.loginErrorCallback = onError;
        network().login(username, password);
    }

    public void register(
            String username,
            String password,
            String email,
            Consumer<Boolean> onResult,
            Consumer<String> onError) {
        this.registerCallback = onResult;
        this.registerErrorCallback = onError;
        network().register(username, password, email);
    }

    public void logout(Runnable onDone) {
        this.logoutCallback = onDone;
        network().logout();
    }

    @Override
    public void onLoginSuccess(LoginResponseDTO response) {
        startSession(response);
        Consumer<Boolean> success = loginCallback;
        loginCallback = null;
        loginErrorCallback = null;
        FxThreadUtil.runOnFxThread(() -> {
            if (success != null) {
                success.accept(true);
            }
        });
    }

    @Override
    public void onLoginFailed(ErrorDTO error) {
        String msg = ErrorMessages.from(error);
        Consumer<String> errCb = loginErrorCallback;
        loginCallback = null;
        loginErrorCallback = null;
        FxThreadUtil.runOnFxThread(() -> {
            if (errCb != null) {
                errCb.accept(msg);
            } else {
                AlertUtil.showError(msg);
            }
        });
    }

    @Override
    public void onRegisterSuccess(LoginResponseDTO response) {
        startSession(response);
        Consumer<Boolean> reg = registerCallback;
        registerCallback = null;
        registerErrorCallback = null;
        FxThreadUtil.runOnFxThread(() -> {
            if (reg != null) {
                reg.accept(true);
            }
        });
    }

    @Override
    public void onRegisterFailed(ErrorDTO error) {
        String msg = ErrorMessages.from(error);
        Consumer<Boolean> reg = registerCallback;
        Consumer<String> errCb = registerErrorCallback;
        registerCallback = null;
        registerErrorCallback = null;
        FxThreadUtil.runOnFxThread(() -> {
            if (errCb != null) {
                errCb.accept(msg);
            } else {
                AlertUtil.showError(msg);
            }
            if (reg != null) {
                reg.accept(false);
            }
        });
    }

    @Override
    public void onLogoutSuccess() {
        AppContext.getInstance().getSessionManager().clearSession();
        AppContext.getInstance().getScreenStateStore().clear();
        AppContext.getInstance().clearShell();
        try {
            AuctionWebSocketClient.getInstance().clearAuthState();
        } catch (IllegalStateException ignored) {
            // client chưa mở
        }
        Runnable done = logoutCallback;
        logoutCallback = null;
        if (done != null) {
            FxThreadUtil.runOnFxThread(done);
        }
    }

    private void startSession(LoginResponseDTO response) {
        AppContext.getInstance().getSessionManager().startSession(UserSession.from(response));
        syncWsAuth(response);
    }

    private void syncWsAuth(LoginResponseDTO response) {
        try {
            AuctionWebSocketClient client = AuctionWebSocketClient.getInstance();
            client.setAuthState(
                    response.getToken(),
                    response.getUser().getId(),
                    response.getUser().getUsername());
        } catch (IllegalStateException ignored) {
            // chưa kết nối WS
        }
    }
}
