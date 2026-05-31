package com.group13.auction.ui.util;

import com.group13.auction.config.UiConstants;
import java.util.Objects;
import javafx.stage.Stage;

/** Helper cấu hình {@link Stage} chính theo chuẩn giao diện của client. */
public final class StageUtil {

  private StageUtil() {
    // Utility class.
  }

  /**
   * Cấu hình kích thước và tiêu đề mặc định cho stage chính.
   *
   * @param stage stage chính của JavaFX app
   */
  public static void configurePrimaryStage(Stage stage) {
    Objects.requireNonNull(stage, "stage must not be null");
    stage.setTitle(UiConstants.APP_TITLE);
    stage.setResizable(true);
    stage.setMinWidth(UiConstants.MIN_WIDTH);
    stage.setMinHeight(UiConstants.MIN_HEIGHT);
    stage.setWidth(UiConstants.DEFAULT_WIDTH);
    stage.setHeight(UiConstants.DEFAULT_HEIGHT);
  }

  /**
   * Đưa stage ra giữa màn hình sau khi scene đã được gắn.
   *
   * @param stage stage cần căn giữa
   */
  public static void centerOnScreen(Stage stage) {
    Objects.requireNonNull(stage, "stage must not be null");
    stage.centerOnScreen();
  }
}
