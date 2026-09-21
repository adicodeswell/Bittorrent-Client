package com.bittorrent.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.io.File;

public class MainWindowController {

    // Layout Panes
    @FXML private BorderPane dashboardPane;
    @FXML private VBox welcomePane;

    // Buttons
    @FXML private Button btnWelcomeAdd;
    @FXML private Button btnToolbarAdd;
    @FXML private Button btnPause;
    @FXML private Button btnResume;
    
    // Table
    @FXML private TableView<TorrentModel> torrentTable;
    @FXML private TableColumn<TorrentModel, String> colName;
    @FXML private TableColumn<TorrentModel, String> colSize;
    @FXML private TableColumn<TorrentModel, String> colStatus;
    @FXML private TableColumn<TorrentModel, Double> colProgress;
    @FXML private TableColumn<TorrentModel, String> colSpeed;
    
    // Status
    @FXML private Label lblGlobalStatus;

    @FXML
    public void initialize() {
        lblGlobalStatus.setText("Ready");

        // 1. Tell the columns which data to read from TorrentModel
        colName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        colSize.setCellValueFactory(cellData -> cellData.getValue().sizeProperty());
        colStatus.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        colSpeed.setCellValueFactory(cellData -> cellData.getValue().speedProperty());
        
        colProgress.setCellValueFactory(cellData -> cellData.getValue().progressProperty().asObject());
        colProgress.setCellFactory(javafx.scene.control.cell.ProgressBarTableCell.forTableColumn());

        // Modernize the Table Look (Taller rows, hide empty rows)
        torrentTable.setFixedCellSize(50);
        torrentTable.setStyle("-fx-control-inner-background: white; -fx-background-color: white; -fx-table-cell-border-color: transparent;");

        // Handle Row Selection for Pause/Resume buttons
        torrentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                btnPause.setDisable(newSel.isPaused() || newSel.progressProperty().get() >= 1.0);
                btnResume.setDisable(!newSel.isPaused() || newSel.progressProperty().get() >= 1.0);
            } else {
                btnPause.setDisable(true);
                btnResume.setDisable(true);
            }
        });

        btnPause.setOnAction(e -> {
            TorrentModel selected = torrentTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selected.setPaused(true);
                btnPause.setDisable(true);
                btnResume.setDisable(false);
            }
        });

        btnResume.setOnAction(e -> {
            TorrentModel selected = torrentTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selected.setPaused(false);
                btnPause.setDisable(false);
                btnResume.setDisable(true);
            }
        });

        // Set action for both "Add" buttons
        btnWelcomeAdd.setOnAction(e -> addTorrent());
        btnToolbarAdd.setOnAction(e -> addTorrent());
    }

    private void addTorrent() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            // Linux: Try to force pure native KDE (Dolphin) or GNOME file pickers via CLI, fallback to AWT
            Thread.ofVirtual().start(() -> {
                File selectedFile = null;
                boolean dialogAttempted = false;
                
                try {
                    Process p = new ProcessBuilder("kdialog", "--getopenfilename", ".", "*.torrent").start();
                    dialogAttempted = true; // Successfully launched!
                    if (p.waitFor() == 0) {
                        String path = new String(p.getInputStream().readAllBytes()).trim();
                        if (!path.isEmpty()) selectedFile = new File(path);
                    }
                } catch (Exception ignored) {}

                if (!dialogAttempted) {
                    try {
                        Process p = new ProcessBuilder("zenity", "--file-selection", "--file-filter=*.torrent").start();
                        dialogAttempted = true;
                        if (p.waitFor() == 0) {
                            String path = new String(p.getInputStream().readAllBytes()).trim();
                            if (!path.isEmpty()) selectedFile = new File(path);
                        }
                    } catch (Exception ignored) {}
                }

                if (!dialogAttempted) {
                    java.awt.FileDialog dialog = new java.awt.FileDialog((java.awt.Frame) null, "Select a Torrent File", java.awt.FileDialog.LOAD);
                    dialog.setFile("*.torrent");
                    dialog.setVisible(true);
                    String file = dialog.getFile();
                    String dir = dialog.getDirectory();
                    if (file != null && dir != null) selectedFile = new File(dir, file);
                }
                
                if (selectedFile != null) {
                    final File finalFile = selectedFile;
                    javafx.application.Platform.runLater(() -> launchTorrentUI(finalFile));
                }
            });
        } else {
            // Windows & Mac: JavaFX FileChooser is perfectly native and safe.
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select a Torrent File");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Torrent Files", "*.torrent"));
            File file = fileChooser.showOpenDialog(null);
            
            if (file != null) {
                launchTorrentUI(file);
            }
        }
    }

    private void launchTorrentUI(File file) {
        welcomePane.setVisible(false);
        dashboardPane.setVisible(true);

        TorrentModel newTorrent = new TorrentModel();
        newTorrent.setName(file.getName());
        torrentTable.getItems().add(newTorrent);
        
        Thread.ofVirtual().start(() -> {
            try {
                com.bittorrent.Main.runTorrent(file.getAbsolutePath(), newTorrent);
            } catch(Exception ex) {
                newTorrent.setStatus("Error: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }
}