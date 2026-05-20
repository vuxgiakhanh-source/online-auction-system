package com.group13.auction.service.notification;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.protocol.PacketType;
import com.group13.auction.mapper.NotificationViewModelMapper;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.network.client.request.ClientRequestFactory;
import com.group13.auction.service.auction.AuctionServiceSupport;
import com.group13.auction.viewmodel.notification.NotificationItemViewModel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Service xử lý notification center phía client.
 *
 * <p>Lớp này chỉ gửi request và map response. Việc lưu thông báo, đánh dấu đã đọc và phát sinh
 * notification là trách nhiệm server.
 */
public final class NotificationService {

    private final ClientNetworkFacade networkFacade;

    /** Tạo notification service dùng network facade mặc định của app. */
    public NotificationService() {
        this(ClientNetworkFacade.getDefault());
    }

    /**
     * Tạo notification service với dependency truyền vào, hữu ích cho test.
     *
     * @param networkFacade facade tầng network
     */
    public NotificationService(ClientNetworkFacade networkFacade) {
        this.networkFacade = Objects.requireNonNull(networkFacade, "networkFacade must not be null");
    }

    /**
     * Lấy danh sách thông báo của user hiện tại.
     *
     * @return future chứa danh sách view model thông báo
     */
    public CompletableFuture<List<NotificationItemViewModel>> getNotifications() {
        return AuctionServiceSupport
                .sendRequest(
                        networkFacade,
                        ClientRequestFactory.getNotifications(),
                        PacketType.GET_NOTIFICATIONS_SUCCESS,
                        AdminDTOs.NotificationDTO[].class,
                        "Không tải được danh sách thông báo.")
                .thenApply(NotificationViewModelMapper::toViewModels);
    }

    /**
     * Đánh dấu một thông báo là đã đọc.
     *
     * @param notificationId mã thông báo
     * @return future hoàn tất khi server xác nhận
     */
    public CompletableFuture<Void> markNotificationRead(String notificationId) {
        if (notificationId == null || notificationId.isBlank()) {
            return AuctionServiceSupport.failedFuture("Thiếu mã thông báo.");
        }

        return AuctionServiceSupport.sendVoidRequest(
                networkFacade,
                ClientRequestFactory.markNotificationRead(notificationId.trim()),
                PacketType.MARK_NOTIFICATION_READ_SUCCESS,
                "Không đánh dấu được thông báo là đã đọc.");
    }
}