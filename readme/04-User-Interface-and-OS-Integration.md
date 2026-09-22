# UI & OS Integration

This project is built using **JavaFX**, but it avoids the traditional pitfalls of Java desktop applications by aggressively tying into native OS features.

## 1. UI Architecture
The UI layout is defined in `src/main/resources/fxml/MainWindow.fxml` using a `StackPane` to toggle between a "Welcome Screen" and the "Dashboard Table". 

### Data Binding (`TorrentModel.java`)
Because the backend network engine (`Main.java`) runs on Virtual Threads, it is strictly forbidden from directly updating the JavaFX UI elements (which causes immediate thread-safety crashes).
Instead, `Main.java` interacts entirely with a `TorrentModel` object. The `TorrentModel` uses `Platform.runLater()` to safely push UI updates (like speed strings and progress doubles) back to the main Application Thread.

## 2. 100% Native File Pickers
By default, if you launch a JavaFX `FileChooser` on a Linux distribution, it often falls back to a legacy, blocky, generic Java popup that ruins the native feel of the application. 

To solve this, `MainWindowController.java` utilizes a highly customized bridging sequence when you click "Add Torrent File":
1. **OS Detection:** It detects if you are on Windows, macOS, or Linux.
2. **Windows/Mac:** It securely utilizes the default `javafx.stage.FileChooser`, which hooks perfectly into the native Microsoft and Apple file explorers.
3. **Linux - KDE Dolphin:** It dynamically spawns a hidden CLI `ProcessBuilder` to check if `kdialog` is installed. If so, it leverages it to open a pure, native KDE Dolphin dialog.
4. **Linux - GNOME Zenity:** If `kdialog` is missing, it checks for `zenity`, launching a pure GTK native file dialog.
5. **Linux - AWT Fallback:** As a last resort, it falls back to Java's `java.awt.FileDialog`, which generally binds to GTK much better than JavaFX's native implementation.

## 3. Bypassing Write-Protection
On Windows, applications installed via `.exe` are placed in `C:\Program Files\`, which is heavily write-protected by Windows Administrator protocols. 
To prevent the BitTorrent engine from silently crashing when trying to save a 2GB movie to `Program Files`, the client dynamically queries `System.getProperty("user.home") + "/Downloads"` to ensure the data is dumped safely into the user's personal Downloads folder across all OS environments.
