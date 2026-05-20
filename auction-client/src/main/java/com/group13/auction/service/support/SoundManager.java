package com.group13.auction.service.support;

/**
 * Compatibility facade for older call sites.
 *
 * <p>New code should call {@link AudioManager} directly so background music and sound effects share
 * one source of truth.
 */
@Deprecated
public final class SoundManager {

    private SoundManager() {
        // Utility class.
    }

    public static void playClickSound() {
        AudioManager.playClickSound();
    }

    public static void playErrorSound() {
        AudioManager.playErrorSound();
    }

    public static void shutdown() {
        AudioManager.shutdown();
    }
}
