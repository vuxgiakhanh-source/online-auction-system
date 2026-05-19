package com.group13.auction.service.support;

import com.group13.auction.config.ResourcePath;
import com.group13.auction.ui.util.FxThreadUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/** Centralized service for short UI sound effects. */
public final class SoundManager {

    private static final Logger LOGGER = Logger.getLogger(SoundManager.class.getName());
    private static final double EFFECT_VOLUME = 0.35;
    private static final Map<String, CompletableFuture<String>> RESOURCE_URIS = new ConcurrentHashMap<>();
    private static final ExecutorService AUDIO_LOADER =
            Executors.newSingleThreadExecutor(new SoundThreadFactory());

    private SoundManager() {
        // Utility class.
    }

    /** Plays the standard button click sound. */
    public static void playClickSound() {
        playEffect(ResourcePath.CLICK_SOUND);
    }

    /** Plays the standard validation/error sound. */
    public static void playErrorSound() {
        playEffect(ResourcePath.ERROR_SOUND);
    }

    /** Releases background loader resources during application shutdown. */
    public static void shutdown() {
        AUDIO_LOADER.shutdownNow();
    }

    private static void playEffect(String resourcePath) {
        RESOURCE_URIS
                .computeIfAbsent(resourcePath, SoundManager::loadResourceUriAsync)
                .thenAccept(uri -> FxThreadUtil.runOnFxThread(() -> playMedia(uri, resourcePath)))
                .exceptionally(
                        throwable -> {
                            LOGGER.log(Level.FINE, "Cannot load sound effect: " + resourcePath, throwable);
                            return null;
                        });
    }

    private static CompletableFuture<String> loadResourceUriAsync(String resourcePath) {
        return CompletableFuture.supplyAsync(() -> copyClasspathResourceToTempFile(resourcePath), AUDIO_LOADER);
    }

    private static String copyClasspathResourceToTempFile(String resourcePath) {
        Objects.requireNonNull(resourcePath, "resourcePath must not be null");

        try (InputStream inputStream = SoundManager.class.getResourceAsStream(resourcePath)) {
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

    private static void playMedia(String mediaUri, String resourcePath) {
        try {
            Media media = new Media(mediaUri);
            MediaPlayer mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(EFFECT_VOLUME);
            mediaPlayer.setOnEndOfMedia(mediaPlayer::dispose);
            mediaPlayer.setOnStopped(mediaPlayer::dispose);
            mediaPlayer.setOnError(
                    () -> {
                        LOGGER.log(
                                Level.FINE,
                                "Sound player error: " + resourcePath,
                                mediaPlayer.getError());
                        mediaPlayer.dispose();
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

    private static String fileExtension(String resourcePath) {
        int dotIndex = resourcePath.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == resourcePath.length() - 1) {
            return ".tmp";
        }
        return resourcePath.substring(dotIndex);
    }

    private static final class SoundThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "auction-sound-loader");
            thread.setDaemon(true);
            return thread;
        }
    }
}
