# BitTorrent Client

![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![UI](https://img.shields.io/badge/UI-JavaFX-brightgreen)

A fully functional, cross-platform BitTorrent client built from scratch in Java 21 and JavaFX. 

This project was created to understand and implement the core mechanics of the BitTorrent protocol. It is not just a UI wrapper; it is a from-scratch implementation of peer-to-peer TCP networking, Bencode parsing, SHA-1 piece verification, and the famous Tit-for-Tat choking algorithm.

---

## 📖 Detailed Overview: How the Protocol Works

BitTorrent is a **Peer-to-Peer (P2P)** file-sharing protocol. Instead of downloading a file from a single central server, the workload is distributed.

1. **Bencoding:** The entire protocol relies on a custom binary format called Bencoding. Our `Bencoder.java` recursively parses these byte streams into standard Java Maps and Lists.
2. **The `.torrent` file:** Contains metadata about the file and cryptographic SHA-1 hashes for every small "piece" (usually 256KB) of the file to ensure data integrity.
3. **The Tracker:** A server URL inside the `.torrent` file. Our client sends an HTTP GET request to the tracker containing our unique Info Hash. The tracker responds with a Bencoded list of IP addresses of other people currently downloading the file.
4. **The Swarm:** We connect directly to those IP addresses via TCP sockets. We exchange a handshake, ask them for pieces we are missing, verify the incoming data against the SHA-1 hash, and simultaneously upload pieces we have already verified to others.

---

## 🏗️ System Architecture & Core Components

This client is built with a highly concurrent, thread-safe architecture utilizing **Java 21 Virtual Threads**. This allows us to manage dozens of simultaneous socket connections with virtually zero memory overhead, without ever blocking the JavaFX UI.

* **`Main.java`**: The backend engine orchestrator. It manages the background loops for Tracker Replenishment (asking for more peers if active socket connections drop below 40) and the Tit-for-Tat algorithm.
* **`PeerConnection.java`**: The TCP socket worker thread. It handles the 68-byte handshake, parses bitfields, and implements a **20-block network pipeline**. Instead of waiting for a block to arrive before requesting the next one, we fire 20 requests at once, aggressively saturating the peer's upload pipe to defeat ping latency.
* **`PieceManager.java`**: A thread-safe global registry. It uses an `AtomicLong` to track total bytes downloaded. The UI thread reads this value once per second to calculate exact global download speeds. It also ensures that two peer threads are never assigned to download the exact same piece.
* **`FileManager.java`**: Maps virtual piece indexes to physical bytes on your hard drive using `RandomAccessFile`. It pre-allocates the file and uses `seek()` to write blocks directly into the physical file structure the instant they arrive, handling pieces entirely out of order.

---

## ⚡ Tit-for-Tat Choking Algorithm

BitTorrent survives because it forces users to share. In `Main.java`, a loop runs every 10 active seconds to evaluate our network relationships:
1. It calculates exactly how many bytes each peer sent us in the last 10 seconds.
2. It sorts the peers from fastest to slowest.
3. The top 3 fastest peers are explicitly sent an `UNCHOKE` message (allowing them to download from us as a reward).
4. All other peers are sent a `CHOKE` message (cutting off their download access).

---

## 🖥️ UI & Native OS Integration

By default, Java desktop applications can feel clunky. We bypassed this by utilizing intelligent OS detection in `MainWindowController.java`:
* On **Windows and Mac**, we use the secure `javafx.stage.FileChooser` which hooks perfectly into the native file explorers.
* On **Linux**, JavaFX FileChoosers often fall back to generic, ugly popups. Our client dynamically spawns CLI processes to check for `kdialog` (launching a pure KDE Dolphin picker) or `zenity` (launching a pure GNOME GTK picker). If neither exist, it falls back to Java's AWT `FileDialog`.
* **Write Protection Bypass:** To prevent Windows Administrator protocols from blocking the client from saving a 2GB movie to `C:\Program Files\`, the client dynamically routes all downloads directly to your OS's native `Downloads` folder.

---

## 📦 CI/CD Pipeline & Native Packaging

Users do not need to install Java or run Maven commands to use this application. The repository includes an enterprise-grade GitHub Actions pipeline.

Every time a Git tag is pushed, it spins up a matrix of Windows, macOS, and Ubuntu servers. It compiles a "Fat JAR", explicitly stripping cryptographic signature files (`.SF`, `.DSA`) to prevent `SecurityExceptions`. It then feeds the JAR into Java's built-in `jpackage` utility, stripping down a minimal copy of the Java 21 Runtime (JRE) and bundling them into standalone native installers:
* **Windows:** `.exe` Installer and `.zip` Portable
* **macOS:** `.dmg` Installer
* **Linux:** `.deb` Installer and `.tar.gz` Portable (for Arch/Fedora)

---

## 🚀 How to Run and Build

### Option 1: Native Installers (Recommended)
Go to the **[Releases](../../releases)** tab to download the native installer for your Operating System.

### Option 2: Compile from Source
Ensure you have **Java 21** and **Maven** installed.
```bash
git clone https://github.com/adicodeswell/Bittorrent-Client.git
cd Bittorrent-Client
mvn clean javafx:run
```

---

## 🛠️ Contribution Roadmap & Deep Dive Docs

For contributors who want to deeply understand the mechanics of this codebase, check out our **[readme/](readme/)** directory for individual, topic-specific markdown files.

If you are looking to contribute code, pull requests are extremely welcome for the following missing features:
- [ ] **UDP Trackers (BEP 15):** The client currently only supports HTTP/HTTPS trackers. If a torrent relies strictly on UDP trackers, it will fail to find peers.
- [ ] **IPv6 Support:** Upgrading the Bencode parser to safely extract 18-byte IPv6 addresses.
- [ ] **DHT & Magnet Links (BEP 5):** Allowing trackerless peer discovery via Magnet links.
- [ ] **Peers UI Tab:** A secondary UI table to visualize active connected IP addresses and individual speeds.
- [ ] **Multi-File UI Selection:** A TreeView allowing the user to uncheck specific files they don't want to download.
