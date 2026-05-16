package com.group13.auction.ui.controller.admin;

import com.group13.auction.common.dto.admin.AdminDTOs;
import com.group13.auction.common.dto.user.UserDTO;
import com.group13.auction.ui.controller.base.BaseController;
import com.group13.auction.ui.controller.base.PageLifecycle;
import com.group13.auction.ui.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public final class AdminUsersController extends BaseController implements PageLifecycle {

    @FXML private TableView<UserDTO> userTable;
    @FXML private TextField banReasonField;

    @FXML
    private void initialize() {
        TableColumn<UserDTO, String> u = new TableColumn<>("Username");
        u.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        TableColumn<UserDTO, String> r = new TableColumn<>("Roles");
        r.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRoles() != null ? String.join(",", c.getValue().getRoles()) : ""));
        TableColumn<UserDTO, String> s = new TableColumn<>("Status");
        s.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAccountStatus()));
        userTable.getColumns().setAll(u, r, s);
        userTable.setItems(services().adminUserService().users());
    }

    @Override
    public void onShow() {
        services().adminUserService().loadAllUsers();
    }

    @FXML
    private void onBan() {
        UserDTO user = userTable.getSelectionModel().getSelectedItem();
        if (user == null) {
            AlertUtil.showWarning("Chọn user.");
            return;
        }
        AdminDTOs.AdminBanUserDTO dto = new AdminDTOs.AdminBanUserDTO();
        dto.setUserId(user.getId());
        dto.setReason(banReasonField.getText().isBlank() ? "OTHER" : banReasonField.getText());
        services().adminUserService().banUser(dto);
    }

    @FXML
    private void onUnban() {
        UserDTO user = userTable.getSelectionModel().getSelectedItem();
        if (user != null) {
            services().adminUserService().unbanUser(user.getId());
        }
    }

    @FXML
    private void onApproveSeller() {
        UserDTO user = userTable.getSelectionModel().getSelectedItem();
        if (user != null) {
            services().adminUserService().approveSeller(user.getId());
        }
    }
}
