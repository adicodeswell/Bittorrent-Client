package com.bittorrent.ui;

public class AppLauncher {
    public static void main(String[] args) {
        // Force JavaFX to use the native Linux GTK3 file picker instead of the ugly generic one
        System.setProperty("jdk.gtk.version", "3");
        MainApp.launch(MainApp.class, args);
    }
}