package com.group13.auction.client;

import com.group13.auction.client.config.ViewPath;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX application chính của auction client.
 */
public class App extends Application {

    private static final String APP_TITLE = "Online Auction System";
    private static final double DEFAULT_WIDTH = 1200;
    private static final double DEFAULT_HEIGHT = 800;
    private static final double MIN_WIDTH = 1000;
    private static final double MIN_HEIGHT = 700;

    /**
     * Khởi tạo giao diện chính của ứng dụng.
     *
     * @param primaryStage stage chính do JavaFX cung cấp
     * @throws IOException nếu không load được file FXML
     */
    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
                App.class.getResource(ViewPath.LOGIN_VIEW)
        );

        Scene scene = new Scene(fxmlLoader.load(), DEFAULT_WIDTH, DEFAULT_HEIGHT);

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}