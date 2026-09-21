package com.bittorrent.ui;

import javafx.beans.property.*;
import javafx.application.Platform;

public class TorrentModel {
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty size = new SimpleStringProperty("");
    private final StringProperty status = new SimpleStringProperty("Queued");
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
    private final StringProperty speed = new SimpleStringProperty("0 KB/s");

    // Getters for the properties (required by JavaFX TableView)
    public StringProperty nameProperty() { return name; }
    public StringProperty sizeProperty() { return size; }
    public StringProperty statusProperty() { return status; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty speedProperty() { return speed; }

    // Easy setters for our backend
    public void setProgress(double p) { Platform.runLater(() -> progress.set(p)); }
    public void setStatus(String s) { Platform.runLater(() -> status.set(s)); }
    public void setSpeed(String s) { Platform.runLater(() -> speed.set(s)); }
    public void setName(String n) { Platform.runLater(() -> name.set(n)); }
    public void setSize(String s) { Platform.runLater(() -> size.set(s)); }
}