package com.group13.auction;

import com.group13.auction.config.UiConstants;
import com.group13.auction.config.ViewPath;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.navigation.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Entry point of the JavaFX client application.
 */
public final class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        SceneManager sceneManager = new SceneManager(primaryStage);
        Navigator navigator = new Navigator(sceneManager);

        primaryStage.setTitle(UiConstants.APP_TITLE);
        primaryStage.setMinWidth(UiConstants.MIN_WIDTH);
        primaryStage.setMinHeight(UiConstants.MIN_HEIGHT);
        primaryStage.setWidth(UiConstants.DEFAULT_WIDTH);
        primaryStage.setHeight(UiConstants.DEFAULT_HEIGHT);

        navigator.goTo(ViewPath.HOME_LANDING_VIEW);
        primaryStage.show();
    }

    /**
     * Launches the JavaFX application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}