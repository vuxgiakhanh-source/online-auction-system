package com.group13.auction.service.support;

import com.group13.auction.config.ResourcePath;
import com.group13.auction.ui.util.FxThreadUtil;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/** Centralized audio service for client-side background music. */
public final class AudioManager {

    private static final Logger LOGGER = Logger.getLogger(AudioManager.class.getName());
    private static final Object LOCK = new Object();
    private static final double DEFAULT_VOLUME = 0.25;

    private static MediaPlayer backgroundMusicPlayer;
    private static double currentVolume = DEFAULT_VOLUME;
    private static boolean muted;

    private AudioManager() {
        // Utility class.
    }

    /** Starts the background music once and keeps it looping indefinitely. */
    public static void playBackgroundMusic() {
        FxThreadUtil.runOnFxThread(AudioManager::playBackgroundMusicOnFxThread);
    }

    /** Stops and releases the current background music player. */
    public static void stopMusic() {
        FxThreadUtil.runOnFxThread(AudioManager::stopMusicOnFxThread);
    }

    /** Toggles background music mute state without changing the stored volume. */
    public static void toggleMute() {
        FxThreadUtil.runOnFxThread(
                () -> {
                    synchronized (LOCK) {
                        muted = !muted;
                        applyVolume();
                    }
                });
    }

    /**
     * Sets background music volume.
     *
     * @param volume value from {@code 0.0} to {@code 1.0}
     */
    public static void setVolume(double volume) {
        FxThreadUtil.runOnFxThread(
                () -> {
                    synchronized (LOCK) {
                        currentVolume = clampVolume(volume);
                        applyVolume();
                    }
                });
    }

    private static void playBackgroundMusicOnFxThread() {
        synchronized (LOCK) {
            if (backgroundMusicPlayer != null) {
                backgroundMusicPlayer.play();
                return;
            }

            URL musicUrl = AudioManager.class.getResource(ResourcePath.BACKGROUND_MUSIC);
            if (musicUrl == null) {
                LOGGER.warning("Background music resource not found: " + ResourcePath.BACKGROUND_MUSIC);
                return;
            }

            try {
                Media media = new Media(musicUrl.toExternalForm());
                MediaPlayer mediaPlayer = new MediaPlayer(media);
                mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                mediaPlayer.setVolume(muted ? 0.0 : currentVolume);
                media.setOnError(
                        () ->
                                LOGGER.log(
                                        Level.WARNING,
                                        "Background music media error: "
                                                + ResourcePath.BACKGROUND_MUSIC,
                                        media.getError()));
                mediaPlayer.setOnError(
                        () ->
                                LOGGER.log(
                                        Level.WARNING,
                                        "Background music player error: "
                                                + ResourcePath.BACKGROUND_MUSIC,
                                        mediaPlayer.getError()));

                backgroundMusicPlayer = mediaPlayer;
                mediaPlayer.play();
            } catch (RuntimeException exception) {
                LOGGER.log(
                        Level.WARNING,
                        "Cannot initialize background music: " + ResourcePath.BACKGROUND_MUSIC,
                        exception);
            }
        }
    }

    private static void stopMusicOnFxThread() {
        synchronized (LOCK) {
            if (backgroundMusicPlayer == null) {
                return;
            }

            try {
                backgroundMusicPlayer.stop();
                backgroundMusicPlayer.dispose();
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Cannot stop background music cleanly.", exception);
            } finally {
                backgroundMusicPlayer = null;
            }
        }
    }

    private static void applyVolume() {
        if (backgroundMusicPlayer != null) {
            backgroundMusicPlayer.setVolume(muted ? 0.0 : currentVolume);
        }
    }

    private static double clampVolume(double volume) {
        if (Double.isNaN(volume)) {
            return DEFAULT_VOLUME;
        }

        return Math.max(0.0, Math.min(1.0, volume));
    }
}
