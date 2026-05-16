package com.group13.auction.ui.controller.admin;

import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.service.admin.AdminUserService;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public final class AdminSellerApprovalsController extends BaseController implements PageLifecycle {

    @FXML private TableView<UserDTO> pendingTable;

    @FXML
    private void initialize() {
        TableColumn<UserDTO, String> u = new TableColumn<>("Username");
        u.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        u.setPrefWidth(160);
        TableColumn<UserDTO, String> e = new TableColumn<>("Email");
        e.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        e.setPrefWidth(220);
        TableColumn<UserDTO, String> r = new TableColumn<>("Rating");
        r.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getRating())));
        pendingTable.getColumns().setAll(u, e, r);
        pendingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    @Override
    public void onShow() {
        services().adminUserService().loadAllUsers();
        refreshPending();
        services().adminUserService().users().addListener(
                (javafx.collections.ListChangeListener<UserDTO>) c -> refreshPending());
    }

    @FXML
    private void onRefresh() {
        services().adminUserService().loadAllUsers();
    }

    @FXML
    private void onApprove() {
        UserDTO user = pendingTable.getSelectionModel().getSelectedItem();
        if (user == null) {
            AlertUtil.showWarning("Chọn user cần duyệt Seller.");
            return;
        }
        services().adminUserService().approveSeller(user.getId());
    }

    private void refreshPending() {
        pendingTable.setItems(FXCollections.observableArrayList(
                services().adminUserService().users().filtered(AdminUserService::isPendingSeller)));
    }
}
