package com.group13.auction.ui.util;

import com.group13.auction.service.support.AudioManager;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;

/** Sound hooks for JavaFX dialogs, whose buttons are outside the main scene graph. */
public final class DialogSoundUtil {

  private static final String INSTALLED_KEY = DialogSoundUtil.class.getName() + ".installed";

  private DialogSoundUtil() {
    // Utility class.
  }

  public static void installButtonClickSound(Dialog<?> dialog) {
    if (dialog == null) {
      return;
    }

    installDialogButtons(dialog);

    EventHandler<DialogEvent> existingHandler = dialog.getOnShown();
    dialog.setOnShown(
        event -> {
          if (existingHandler != null) {
            existingHandler.handle(event);
          }
          installDialogButtons(dialog);
        });
  }

  private static void installDialogButtons(Dialog<?> dialog) {
    for (ButtonType buttonType : dialog.getDialogPane().getButtonTypes()) {
      Node button = dialog.getDialogPane().lookupButton(buttonType);
      if (button instanceof ButtonBase buttonBase) {
        installButton(buttonBase);
      }
    }
  }

  private static void installButton(ButtonBase button) {
    if (Boolean.TRUE.equals(button.getProperties().get(INSTALLED_KEY))) {
      return;
    }

    button.addEventFilter(ActionEvent.ACTION, event -> AudioManager.playClickSound());
    button.getProperties().put(INSTALLED_KEY, Boolean.TRUE);
  }
}
