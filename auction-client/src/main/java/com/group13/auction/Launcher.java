package com.group13.auction;

/**
 * Delegating launcher used by some IDEs and Maven JavaFX configurations.
 */
public final class Launcher {

    private Launcher() {}

    /**
     * Delegates to {@link App#main(String[])}.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        App.main(args);
    }
}