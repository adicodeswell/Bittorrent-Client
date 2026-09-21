package com.bittorrent.ui;

public class AppLauncher {
    public static void main(String[] args) {
        // This bypasses module path issues in Java 21
        MainApp.launch(MainApp.class, args);
    }
}