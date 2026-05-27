package com.group13.auction.ui.controller.shared;

import com.group13.auction.core.navigation.Navigator;
import javafx.fxml.FXML;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Controller cho cụm mở nhanh OMNI chatbot.
 *
 * <p>Launcher này chỉ điều hướng tới màn chatbot. Nó không chứa logic gửi câu hỏi hoặc xử lý FAQ.
 */
public final class ChatbotLauncherController {

  /** Mở màn OMNI chatbot khi người dùng click launcher. */
  @FXML
  public void handleOpenChatbot() {
    Navigator.getInstance().goToChatbot();
  }

  /**
   * Hỗ trợ mở chatbot bằng bàn phím khi launcher được focus.
   *
   * @param event sự kiện phím từ JavaFX
   */
  @FXML
  public void handleLauncherKeyPressed(KeyEvent event) {
    if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
      handleOpenChatbot();
      event.consume();
    }
  }
}
