package com.group13.auction.service.profile;

import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.core.context.AppContext;
import com.group13.auction.core.session.SessionManager;
import com.group13.auction.core.session.UserSession;
import com.group13.auction.mapper.UserViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.profile.UserProfileViewModel;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service xử lý hồ sơ người dùng ở phía client.
 *
 * <p>Lớp này không tự thay đổi quyền user. Việc cấp quyền Seller, kiểm tra điều kiện nâng cấp và
 * trả về hồ sơ mới đều do server xử lý.
 */
public final class ProfileService {

    private final ClientNetworkFacade networkFacade;
    private final SessionManager sessionManager;

    /** Tạo profile service dùng dependency mặc định của app. */
    public ProfileService() {
        this(ClientNetworkFacade.getDefault(), AppContext.getInstance().getSessionManager());
    }

    /**
     * Tạo profile service với dependency truyền vào, hữu ích cho test.
     *
     * @param networkFacade facade tầng network
     * @param sessionManager manager lưu session client
     */
    public ProfileService(ClientNetworkFacade networkFacade, SessionManager sessionManager) {
        this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
    }

    /**
     * Lấy hồ sơ của tài khoản đang đăng nhập.
     *
     * @return future chứa profile view model
     */
    public CompletableFuture<UserProfileViewModel> getMyProfile() {
        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.getMyProfile(),
                        PacketType.GET_MY_PROFILE_SUCCESS,
                        UserDTO.class,
                        "Không tải được hồ sơ người dùng.")
                .thenApply(
                        user -> {
                            refreshCurrentSession(user);
                            return UserViewModelMapper.toProfileViewModel(user);
                        });
    }

    /**
     * Lấy hồ sơ public của người dùng khác.
     *
     * @param userId id người dùng cần xem
     * @return future chứa profile view model
     */
    public CompletableFuture<UserProfileViewModel> getUserProfile(String userId) {
        if (userId == null || userId.isBlank()) {
            return AuctionServiceSupport.failedFuture("Thiếu mã người dùng.");
        }

        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.getUserProfile(userId.trim()),
                        PacketType.GET_USER_PROFILE_SUCCESS,
                        UserDTO.class,
                        "Không tải được hồ sơ người dùng.")
                .thenApply(UserViewModelMapper::toProfileViewModel);
    }

    /**
     * Gửi yêu cầu nâng cấp tài khoản hiện tại lên Seller.
     *
     * <p>Sau khi server xác nhận, client tải lại hồ sơ để cập nhật session và UI theo dữ liệu thật từ
     * server.
     *
     * @return future chứa profile mới nhất sau khi yêu cầu được xử lý
     */
    public CompletableFuture<UserProfileViewModel> requestSellerRole() {
        return AuctionServiceSupport
                .sendVoidRequest(
                        networkFacade,
                        ClientRequestFactory.requestSellerRole(),
                        PacketType.REQUEST_SELLER_ROLE_SUCCESS,
                        "Không gửi được yêu cầu nâng cấp Seller.")
                .thenCompose(ignored -> getMyProfile());
    }

    private void refreshCurrentSession(UserDTO user) {
        if (user == null || user.getId() == null) {
            return;
        }

        sessionManager
                .getCurrentSession()
                .filter(current -> user.getId().equals(current.getUserId()))
                .ifPresent(current -> sessionManager.startSession(UserSession.from(current.getToken(), user)));
    }
}