package com.group13.auction.ui.util;

import javafx.application.Platform;

/** Helper đảm bảo code cập nhật UI luôn chạy trên JavaFX Application Thread. */
public final class FxThreadUtil {

  private FxThreadUtil() {
    // Utility class.
  }

  /**
   * Chạy task trên JavaFX Application Thread.
   *
   * @param task công việc cần chạy
   */
  public static void runOnFxThread(Runnable task) {
    if (task == null) {
      return;
    }

    if (Platform.isFxApplicationThread()) {
      task.run();
    } else {
      Platform.runLater(task);
    }
  }
}
