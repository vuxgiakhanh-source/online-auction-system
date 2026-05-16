package com.group13.auction.ui.controller.notification;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

public final class NotificationsController extends BaseController implements PageLifecycle {

    @FXML private ListView<AdminDTOs.NotificationDTO> listView;

    @FXML
    private void initialize() {
        listView.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(AdminDTOs.NotificationDTO item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText((item.isRead() ? "" : "● ") + item.getTitle() + " — " + item.getBody());
                }
            }
        });
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                AdminDTOs.NotificationDTO n = listView.getSelectionModel().getSelectedItem();
                if (n != null) {
                    services().notificationService().markRead(n.getId());
                }
            }
        });
    }

    @Override
    public void onShow() {
        services().notificationService().refresh();
        listView.setItems(services().notificationService().inbox());
    }
}
