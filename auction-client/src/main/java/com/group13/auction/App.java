package com.group13.auction;

import com.group13.auction.config.UiConstants;
import com.group13.auction.core.bootstrap.AppBootstrap;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.navigation.Route;
import com.group13.auction.core.navigation.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Entry point chính của JavaFX client application.
 */
public final class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle(UiConstants.APP_TITLE);
        primaryStage.setMinWidth(UiConstants.MIN_WIDTH);
        primaryStage.setMinHeight(UiConstants.MIN_HEIGHT);
        primaryStage.setWidth(UiConstants.DEFAULT_WIDTH);
        primaryStage.setHeight(UiConstants.DEFAULT_HEIGHT);

        SceneManager sceneManager = new SceneManager(primaryStage);
        Navigator navigator = new Navigator(sceneManager);
        AppBootstrap.initialize();
        navigator.goTo(Route.LANDING);

        primaryStage.show();
    }

    @Override
    public void stop() {
        AppBootstrap.shutdown();
    }

    /**
     * IDE đôi khi chạy nhầm class này — chuyển sang {@link Launcher}.
     */
    public static void main(String[] args) {
        Launcher.main(args);
    }
}