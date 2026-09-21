package com.bittorrent.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import java.io.File;

public class MainWindowController {

    @FXML private Button btnAddTorrent;
    @FXML private Button btnPause;
    @FXML private Button btnResume;
    
    @FXML private TableView<TorrentModel> torrentTable;
    @FXML private TableColumn<TorrentModel, String> colName;
    @FXML private TableColumn<TorrentModel, String> colSize;
    @FXML private TableColumn<TorrentModel, String> colStatus;
    @FXML private TableColumn<TorrentModel, Double> colProgress;
    @FXML private TableColumn<TorrentModel, String> colSpeed;
    
    @FXML private Label lblGlobalStatus;

    @FXML
    public void initialize() {
        lblGlobalStatus.setText("Ready");

        // 1. Tell the columns which data to read from TorrentModel
        colName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        colSize.setCellValueFactory(cellData -> cellData.getValue().sizeProperty());
        colStatus.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        colSpeed.setCellValueFactory(cellData -> cellData.getValue().speedProperty());
        
        // Render the progress as a beautiful graphical progress bar!
        colProgress.setCellValueFactory(cellData -> cellData.getValue().progressProperty().asObject());
        colProgress.setCellFactory(javafx.scene.control.cell.ProgressBarTableCell.forTableColumn());

        // 2. Open File Explorer when clicked
        btnAddTorrent.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Torrent Files", "*.torrent"));
            File file = fileChooser.showOpenDialog(null);
            
            if (file != null) {
                TorrentModel newTorrent = new TorrentModel();
                newTorrent.setName(file.getName());
                torrentTable.getItems().add(newTorrent); // Adds it to the UI
                
                // 3. Launch the engine in a background thread!
                Thread.ofVirtual().start(() -> {
                    try {
                        com.bittorrent.Main.runTorrent(file.getAbsolutePath(), newTorrent);
                    } catch(Exception ex) {
                        newTorrent.setStatus("Error: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                });
            }
        });
    }
}