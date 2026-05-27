package com.group13.auction.ui.util;

import com.group13.auction.config.UiConstants;
import com.group13.auction.service.support.AudioManager;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/** Helper hiển thị dialog JavaFX theo format thống nhất của client. */
public final class AlertUtil {

  private AlertUtil() {
    // Utility class.
  }

  /**
   * Hiển thị thông báo thông tin.
   *
   * @param message nội dung thông báo
   */
  public static void showInfo(String message) {
    show(Alert.AlertType.INFORMATION, UiConstants.INFORMATION_TITLE, message);
  }

  /**
   * Hiển thị cảnh báo.
   *
   * @param message nội dung cảnh báo
   */
  public static void showWarning(String message) {
    show(Alert.AlertType.WARNING, UiConstants.WARNING_TITLE, message);
  }

  /**
   * Hiển thị thông báo lỗi.
   *
   * @param message nội dung lỗi
   */
  public static void showError(String message) {
    show(Alert.AlertType.ERROR, UiConstants.ERROR_TITLE, message);
  }

  /**
   * Hiển thị hộp thoại xác nhận.
   *
   * @param message nội dung cần xác nhận
   * @return true nếu người dùng bấm OK
   */
  public static boolean confirm(String message) {
    Alert alert =
        createAlert(Alert.AlertType.CONFIRMATION, UiConstants.CONFIRMATION_TITLE, message);
    Optional<ButtonType> result = alert.showAndWait();
    return result.isPresent() && result.get() == ButtonType.OK;
  }

  private static void show(Alert.AlertType type, String title, String message) {
    playErrorSoundForProblemDialog(type);
    createAlert(type, title, message).showAndWait();
  }

  private static Alert createAlert(Alert.AlertType type, String title, String message) {
    Alert alert = new Alert(type);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message == null || message.isBlank() ? title : message);
    DialogSoundUtil.installButtonClickSound(alert);
    return alert;
  }

  private static void playErrorSoundForProblemDialog(Alert.AlertType type) {
    if (type == Alert.AlertType.ERROR || type == Alert.AlertType.WARNING) {
      AudioManager.playErrorSound();
    }
  }
}
