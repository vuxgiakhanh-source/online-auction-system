package com.group13.auction.core.navigation;

/**
 * Thin navigation facade used by controllers or the application bootstrap.
 */
public final class Navigator {

    private static Navigator instance;

    private final SceneManager sceneManager;

    /**
     * Creates and registers the global navigator.
     *
     * @param sceneManager scene manager used to switch views
     */
    public Navigator(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        instance = this;
    }

    /**
     * Returns the global navigator instance.
     *
     * @return navigator instance
     */
    public static Navigator getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Navigator has not been initialized.");
        }
        return instance;
    }

    /**
     * Navigates to the target FXML view.
     *
     * @param fxmlPath classpath path to the target FXML file
     */
    public void goTo(String fxmlPath) {
        sceneManager.setRoot(fxmlPath);
    }
}
