package com.group13.auction;

import com.group13.auction.config.UiConstants;
import com.group13.auction.config.ViewPath;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.navigation.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Entry point của ứng dụng JavaFX client.
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

        AppContext.getInstance().getNetworkGateway().ensureInitialized();
        navigator.goTo(ViewPath.LANDING_VIEW);
        primaryStage.show();
    }

    @Override
    public void stop() {
        AppContext.getInstance().getNetworkGateway().shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
