package com.group13.auction.service.auth;

import com.google.gson.JsonElement;
import com.group13.auction.common.dto.auth.LoginResponseDTO;
import com.group13.auction.common.dto.core.ErrorDTO;
import com.group13.auction.common.protocol.Packet;
import com.group13.auction.common.protocol.PacketCodec;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.session.SessionManager;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.network.client.support.NetworkClientException;
import com.group13.auction.viewmodel.auth.LoginFormState;
import com.group13.auction.viewmodel.auth.RegisterFormState;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service xử lý xác thực người dùng ở phía client.
 *
 * <p>Lớp này không tự xử lý nghiệp vụ đăng nhập/đăng ký. Nhiệm vụ chính là validate dữ liệu nhập,
 * gửi packet xuống server qua network facade, nhận response, rồi lưu session nếu thành công.
 */
public final class AuthService {

    private static final long AUTH_TIMEOUT_SECONDS = 12L;

    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "auth-timeout");
                        thread.setDaemon(true);
                        return thread;
                    });

    private final ClientNetworkFacade networkFacade;
    private final SessionManager sessionManager;

    /** Tạo auth service dùng dependency mặc định của ứng dụng. */
    public AuthService() {
        this(ClientNetworkFacade.getDefault(), AppContext.getInstance().getSessionManager());
    }

    /**
     * Tạo auth service với dependency truyền vào, hữu ích cho test.
     *
     * @param networkFacade facade tầng network
     * @param sessionManager manager lưu session client
     */
    public AuthService(ClientNetworkFacade networkFacade, SessionManager sessionManager) {
        this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
    }

    /**
     * Đăng nhập bằng username và password.
     *
     * @param username tên đăng nhập
     * @param password mật khẩu
     * @return future chứa session nếu đăng nhập thành công
     */
    public CompletableFuture<UserSession> login(String username, String password) {
        return login(new LoginFormState(username, password));
    }

    /**
     * Đăng nhập bằng form state.
     *
     * @param formState dữ liệu form đăng nhập
     * @return future chứa session nếu đăng nhập thành công
     */
    public CompletableFuture<UserSession> login(LoginFormState formState) {
        Objects.requireNonNull(formState, "formState must not be null");

        Optional<String> validationError = formState.validate();
        if (validationError.isPresent()) {
            return failedFuture(validationError.get());
        }

        Packet<?> packet = ClientRequestFactory.login(
                formState.normalizedUsername(), formState.password());
        return sendAuthRequest(packet, PacketType.LOGIN_SUCCESS, "Đăng nhập thất bại.");
    }

    /**
     * Đăng ký tài khoản mới.
     *
     * @param username tên đăng nhập
     * @param password mật khẩu
     * @param email email người dùng
     * @return future chứa session nếu đăng ký thành công
     */
    public CompletableFuture<UserSession> register(String username, String password, String email) {
        return register(new RegisterFormState(email, username, password));
    }

    /**
     * Đăng ký bằng form state.
     *
     * @param formState dữ liệu form đăng ký
     * @return future chứa session nếu đăng ký thành công
     */
    public CompletableFuture<UserSession> register(RegisterFormState formState) {
        Objects.requireNonNull(formState, "formState must not be null");

        Optional<String> validationError = formState.validate();
        if (validationError.isPresent()) {
            return failedFuture(validationError.get());
        }

        Packet<?> packet = ClientRequestFactory.register(
                formState.normalizedUsername(),
                formState.password(),
                formState.normalizedEmail());
        return sendAuthRequest(packet, PacketType.REGISTER_SUCCESS, "Đăng ký thất bại.");
    }

    /**
     * Đăng xuất khỏi client.
     *
     * <p>Client gửi packet logout theo kiểu fire-and-forget rồi xóa session local.
     */
    public void logout() {
        if (networkFacade.isConnected()) {
            networkFacade.logout();
        }
        sessionManager.clearSession();
    }

    private CompletableFuture<UserSession> sendAuthRequest(
            Packet<?> packet, PacketType successType, String fallbackErrorMessage) {
        CompletableFuture<UserSession> future = new CompletableFuture<>();

        try {
            ensureConnected();

            networkFacade.sendAndExpect(
                    packet,
                    (responseType, payload) -> {
                        if (responseType == successType) {
                            completeAuthSuccess(payload, future);
                            return;
                        }

                        String message = extractErrorMessage(payload, fallbackErrorMessage);
                        future.completeExceptionally(new NetworkClientException(message));
                    });

            ScheduledFuture<?> timeoutTask =
                    TIMEOUT_EXECUTOR.schedule(
                            () ->
                                    future.completeExceptionally(
                                            new NetworkClientException(
                                                    "Server không phản hồi. Kiểm tra server đã chạy chưa.")),
                            AUTH_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS);

            future.whenComplete((session, throwable) -> timeoutTask.cancel(false));
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }

        return future;
    }

    private void ensureConnected() {
        if (networkFacade.isConnected()) {
            return;
        }

        boolean connected = networkFacade.connectBlocking();
        if (!connected) {
            throw new NetworkClientException(
                    "Không kết nối được tới server: " + networkFacade.getServerUri());
        }
    }

    private void completeAuthSuccess(JsonElement payload, CompletableFuture<UserSession> future) {
        try {
            LoginResponseDTO response = PacketCodec.fromElement(payload, LoginResponseDTO.class);
            UserSession session = UserSession.from(response);
            sessionManager.startSession(session);
            future.complete(session);
        } catch (RuntimeException exception) {
            future.completeExceptionally(
                    new NetworkClientException("Response đăng nhập/đăng ký không hợp lệ.", exception));
        }
    }

    private String extractErrorMessage(JsonElement payload, String fallbackMessage) {
        if (payload == null || payload.isJsonNull()) {
            return fallbackMessage;
        }

        try {
            ErrorDTO error = PacketCodec.fromElement(payload, ErrorDTO.class);
            if (error != null && error.getMessage() != null && !error.getMessage().isBlank()) {
                return error.getMessage();
            }
        } catch (RuntimeException ignored) {
            // Dùng fallback nếu payload lỗi không parse được.
        }

        return fallbackMessage;
    }

    private CompletableFuture<UserSession> failedFuture(String message) {
        CompletableFuture<UserSession> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalArgumentException(message));
        return future;
    }
}