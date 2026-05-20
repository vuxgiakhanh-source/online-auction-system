package com.group13.auction.service.support;

import com.group13.auction.config.ResourcePath;
import com.group13.auction.ui.util.FxThreadUtil;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/** Centralized singleton for client-side background music and sound effects. */
public final class AudioManager {

    private static final Logger LOGGER = Logger.getLogger(AudioManager.class.getName());
    private static final AudioManager INSTANCE = new AudioManager();
    private static final double BACKGROUND_VOLUME = 0.25;
    private static final double EFFECT_VOLUME = 0.35;

    private final Object lock = new Object();
    private final Map<String, CompletableFuture<String>> effectResourceUris = new ConcurrentHashMap<>();
    private final Set<MediaPlayer> activeEffectPlayers =
            Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ExecutorService audioLoader = Executors.newSingleThreadExecutor(new AudioThreadFactory());

    private MediaPlayer backgroundMusicPlayer;
    private volatile boolean backgroundMuted;
    private volatile boolean effectsMuted;

    private AudioManager() {
        // Singleton.
    }

    public static AudioManager getInstance() {
        return INSTANCE;
    }

    /** Starts the background music once and keeps it looping indefinitely. */
    public static void playBackgroundMusic() {
        INSTANCE.playBackgroundMusicInternal();
    }

    /** Stops and releases the current background music player. */
    public static void stopBackgroundMusic() {
        INSTANCE.stopBackgroundMusicInternal();
    }

    /** Plays the standard button click sound when sound effects are enabled. */
    public static void playClickSound() {
        INSTANCE.playEffect(ResourcePath.CLICK_SOUND);
    }

    /** Plays the standard validation/error sound when sound effects are enabled. */
    public static void playErrorSound() {
        INSTANCE.playEffect(ResourcePath.ERROR_SOUND);
    }

    /** Mutes background music immediately and releases the current player. */
    public static void muteBackgroundMusic() {
        INSTANCE.muteBackgroundMusicInternal();
    }

    /** Unmutes background music and starts it again. */
    public static void unmuteBackgroundMusic() {
        INSTANCE.unmuteBackgroundMusicInternal();
    }

    /** Mutes sound effects and stops any short effect still playing. */
    public static void muteSoundEffects() {
        INSTANCE.muteSoundEffectsInternal();
    }

    /** Allows future sound effects to play. */
    public static void unmuteSoundEffects() {
        INSTANCE.effectsMuted = false;
    }

    public static boolean isBackgroundMuted() {
        return INSTANCE.backgroundMuted;
    }

    public static boolean isEffectsMuted() {
        return INSTANCE.effectsMuted;
    }

    /** Releases media players and background loader resources during application shutdown. */
    public static void shutdown() {
        stopBackgroundMusic();
        muteSoundEffects();
        INSTANCE.audioLoader.shutdownNow();
    }

    /** Backward-compatible alias for older code. */
    public static void stopMusic() {
        stopBackgroundMusic();
    }

    /** Backward-compatible alias for older code. */
    public static void toggleMute() {
        if (isBackgroundMuted()) {
            unmuteBackgroundMusic();
        } else {
            muteBackgroundMusic();
        }
    }

    private void playBackgroundMusicInternal() {
        FxThreadUtil.runOnFxThread(this::playBackgroundMusicOnFxThread);
    }

    private void stopBackgroundMusicInternal() {
        FxThreadUtil.runOnFxThread(this::stopBackgroundMusicOnFxThread);
    }

    private void muteBackgroundMusicInternal() {
        backgroundMuted = true;
        stopBackgroundMusicInternal();
    }

    private void unmuteBackgroundMusicInternal() {
        backgroundMuted = false;
        playBackgroundMusicInternal();
    }

    private void muteSoundEffectsInternal() {
        effectsMuted = true;
        FxThreadUtil.runOnFxThread(
                () -> {
                    for (MediaPlayer mediaPlayer : activeEffectPlayers) {
                        disposeEffectPlayer(mediaPlayer);
                    }
                    activeEffectPlayers.clear();
                });
    }

    private void playBackgroundMusicOnFxThread() {
        synchronized (lock) {
            if (backgroundMuted) {
                stopBackgroundMusicOnFxThread();
                return;
            }

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
                mediaPlayer.setVolume(BACKGROUND_VOLUME);
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

    private void stopBackgroundMusicOnFxThread() {
        synchronized (lock) {
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

    private void playEffect(String resourcePath) {
        if (effectsMuted) {
            return;
        }

        effectResourceUris
                .computeIfAbsent(resourcePath, this::loadResourceUriAsync)
                .thenAccept(uri -> FxThreadUtil.runOnFxThread(() -> playEffectOnFxThread(uri, resourcePath)))
                .exceptionally(
                        throwable -> {
                            LOGGER.log(Level.FINE, "Cannot load sound effect: " + resourcePath, throwable);
                            return null;
                        });
    }

    private CompletableFuture<String> loadResourceUriAsync(String resourcePath) {
        return CompletableFuture.supplyAsync(() -> copyClasspathResourceToTempFile(resourcePath), audioLoader);
    }

    private String copyClasspathResourceToTempFile(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath must not be null");

        try (InputStream inputStream = AudioManager.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Sound resource not found: " + resourcePath);
            }

            Path tempFile = Files.createTempFile("auction-sound-", fileExtension(resourcePath));
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();
            return tempFile.toUri().toString();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot prepare sound resource: " + resourcePath, exception);
        }
    }

    private void playEffectOnFxThread(String mediaUri, String resourcePath) {
        if (effectsMuted) {
            return;
        }

        try {
            Media media = new Media(mediaUri);
            MediaPlayer mediaPlayer = new MediaPlayer(media);
            activeEffectPlayers.add(mediaPlayer);
            mediaPlayer.setVolume(EFFECT_VOLUME);
            mediaPlayer.setOnEndOfMedia(() -> disposeEffectPlayer(mediaPlayer));
            mediaPlayer.setOnStopped(() -> disposeEffectPlayer(mediaPlayer));
            mediaPlayer.setOnError(
                    () -> {
                        LOGGER.log(
                                Level.FINE,
                                "Sound player error: " + resourcePath,
                                mediaPlayer.getError());
                        disposeEffectPlayer(mediaPlayer);
                    });
            media.setOnError(
                    () ->
                            LOGGER.log(
                                    Level.FINE,
                                    "Sound media error: " + resourcePath,
                                    media.getError()));
            mediaPlayer.play();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.FINE, "Cannot play sound effect: " + resourcePath, exception);
        }
    }

    private void disposeEffectPlayer(MediaPlayer mediaPlayer) {
        if (mediaPlayer == null) {
            return;
        }

        activeEffectPlayers.remove(mediaPlayer);
        try {
            mediaPlayer.setOnEndOfMedia(null);
            mediaPlayer.setOnStopped(null);
            mediaPlayer.stop();
            mediaPlayer.dispose();
        } catch (RuntimeException exception) {
            LOGGER.log(Level.FINE, "Cannot dispose sound effect player cleanly.", exception);
        }
    }

    private String fileExtension(String resourcePath) {
        int dotIndex = resourcePath.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == resourcePath.length() - 1) {
            return ".tmp";
        }
        return resourcePath.substring(dotIndex);
    }

    private static final class AudioThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "auction-audio-loader");
            thread.setDaemon(true);
            return thread;
        }
    }
}
