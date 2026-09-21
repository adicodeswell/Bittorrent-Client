package com.bittorrent.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainWindow.fxml"));
        BorderPane root = loader.load();

        Scene scene = new Scene(root);
        primaryStage.setTitle("BitTorrent Client");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}