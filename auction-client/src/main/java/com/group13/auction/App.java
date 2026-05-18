package com.group13.auction;

import com.group13.auction.config.ResourcePath;
import com.group13.auction.core.navigation.Navigator;
import com.group13.auction.core.navigation.Route;
import com.group13.auction.core.navigation.SceneManager;
import com.group13.auction.ui.util.StageUtil;
import com.group13.auction.network.client.facade.ClientNetworkFacade;
import com.group13.auction.service.support.ClientNotificationService;
import java.io.InputStream;
import javafx.application.Application;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/** Entry point chính của JavaFX client application. */
public final class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        loadApplicationFonts();
        StageUtil.configurePrimaryStage(primaryStage);

        SceneManager sceneManager = new SceneManager(primaryStage);
        Navigator navigator = new Navigator(sceneManager);

        ClientNotificationService.getInstance().start();

        navigator.goTo(Route.LANDING);

        primaryStage.show();
        StageUtil.centerOnScreen(primaryStage);
    }

    @Override
    public void stop() {
        ClientNotificationService.getInstance().stop();
        ClientNetworkFacade.getDefault().shutdown();
    }

    /**
     * Khởi chạy JavaFX application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    private void loadApplicationFonts() {
        loadFont(ResourcePath.FONT_MONTSERRAT_REGULAR);
        loadFont(ResourcePath.FONT_MONTSERRAT_MEDIUM);
        loadFont(ResourcePath.FONT_MONTSERRAT_SEMIBOLD);
        loadFont(ResourcePath.FONT_MONTSERRAT_BOLD);
        loadFont(ResourcePath.FONT_BRICOLAGE_REGULAR);
        loadFont(ResourcePath.FONT_BRICOLAGE_MEDIUM);
        loadFont(ResourcePath.FONT_BRICOLAGE_SEMIBOLD);
        loadFont(ResourcePath.FONT_BRICOLAGE_BOLD);
    }

    private void loadFont(String fontPath) {
        try (InputStream inputStream = App.class.getResourceAsStream(fontPath)) {
            if (inputStream != null) {
                Font.loadFont(inputStream, 12.0);
            }
        } catch (Exception exception) {
            System.err.println("Không thể load font: " + fontPath + " - " + exception.getMessage());
        }
    }
}