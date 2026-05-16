package com.group13.auction.service.notification;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.network.client.session.ClientEventListener;
import com.group13.auction.service.base.NetworkService;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.List;

/**
 * Hộp thông báo in-app (GET_NOTIFICATIONS).
 */
public final class NotificationService extends NetworkService implements ClientEventListener {

    private final ObservableList<AdminDTOs.NotificationDTO> inbox =
            FXCollections.observableArrayList();
    private final IntegerProperty unreadCount = new SimpleIntegerProperty(0);

    public ObservableList<AdminDTOs.NotificationDTO> inbox() {
        return inbox;
    }

    public IntegerProperty unreadCountProperty() {
        return unreadCount;
    }

    public void refresh() {
        network().getNotifications();
    }

    public void markRead(String notificationId) {
        network().markNotificationRead(notificationId);
    }

    @Override
    public void onNotificationsReceived(List<AdminDTOs.NotificationDTO> notifications) {
        inbox.setAll(notifications != null ? notifications : List.of());
        unreadCount.set((int) inbox.stream().filter(n -> !n.isRead()).count());
    }

    @Override
    public void onMarkNotificationReadSuccess() {
        refresh();
    }
}
