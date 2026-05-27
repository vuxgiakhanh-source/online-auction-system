package com.group13.auction.ui.util;

import com.group13.auction.util.ValidationUtil;
import javafx.scene.control.TextInputControl;

/** Helper validate form JavaFX ở mức cơ bản trước khi gọi service. */
public final class FormValidationUtil {

  private FormValidationUtil() {
    // Utility class.
  }

  /**
   * Kiểm tra field có text hay không, nếu không thì focus lại field đó.
   *
   * @param field field cần kiểm tra
   * @param message thông báo lỗi
   * @return true nếu field hợp lệ
   */
  public static boolean requireText(TextInputControl field, String message) {
    if (field == null || ValidationUtil.hasText(field.getText())) {
      return true;
    }

    AlertUtil.showWarning(message);
    field.requestFocus();
    return false;
  }

  /**
   * Kiểm tra hai field mật khẩu có khớp nhau hay không.
   *
   * @param passwordField field mật khẩu
   * @param confirmPasswordField field nhập lại mật khẩu
   * @return true nếu hai mật khẩu khớp nhau
   */
  public static boolean passwordsMatch(
      TextInputControl passwordField, TextInputControl confirmPasswordField) {
    String password = passwordField == null ? null : passwordField.getText();
    String confirmPassword = confirmPasswordField == null ? null : confirmPasswordField.getText();
    return password != null && password.equals(confirmPassword);
  }
}
