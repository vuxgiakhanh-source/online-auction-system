package com.group13.auction.ui.util;

import com.group13.auction.service.support.SoundManager;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;

/** Installs reusable sound hooks for JavaFX UI controls. */
public final class UiSoundInstaller {

    private static final String INSTALLED_KEY = UiSoundInstaller.class.getName() + ".installed";

    private UiSoundInstaller() {
        // Utility class.
    }

    /** Adds a single scene-level action filter that covers FXML and programmatic buttons. */
    public static void installButtonClickSound(Scene scene) {
        if (scene == null || Boolean.TRUE.equals(scene.getProperties().get(INSTALLED_KEY))) {
            return;
        }

        scene.addEventFilter(
                ActionEvent.ACTION,
                event -> {
                    if (event.getTarget() instanceof ButtonBase) {
                        SoundManager.playClickSound();
                    }
                });
        scene.getProperties().put(INSTALLED_KEY, Boolean.TRUE);
    }
}
