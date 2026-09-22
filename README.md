# BitTorrent Client

![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![UI](https://img.shields.io/badge/UI-JavaFX-brightgreen)

A fully functional, cross-platform BitTorrent client built from scratch in Java 21 and JavaFX. 

This project was created to understand and implement the core mechanics of the BitTorrent protocol, including peer-to-peer TCP networking, Bencode parsing, SHA-1 piece verification, and the famous Tit-for-Tat choking algorithm, all bundled into a beautiful native desktop app.

---

## 📖 Quick Overview: How it Works

BitTorrent is a **Peer-to-Peer (P2P)** file-sharing protocol. Instead of downloading a file from a single central server, you download small "pieces" of the file from dozens of other users (peers) simultaneously. 
1. **The `.torrent` file:** Contains metadata about the file and cryptographic SHA-1 hashes for every small "piece" of the file to ensure data integrity.
2. **The Tracker:** A server URL inside the `.torrent` file. Our client asks the tracker for a list of IP addresses of other people currently downloading the file.
3. **The Swarm:** We connect directly to those IP addresses via TCP sockets, ask them for pieces we are missing, and simultaneously upload pieces we have already verified to others.

---

## 🏗️ Architecture & Core Components

This client is built with a highly concurrent, thread-safe architecture utilizing **Java 21 Virtual Threads** to manage dozens of simultaneous socket connections without blocking the JavaFX UI.

* **`Main.java`**: The backend engine orchestrator. It manages the background loops for Tracker Replenishment (asking for more peers if connections drop) and the Tit-for-Tat Choking algorithm (rewarding peers who upload to us with faster download speeds).
* **`PeerConnection.java`**: The TCP socket worker thread. It handles the handshake, parses bitfields, and implements a **20-block network pipeline** to aggressively request data and defeat ping latency.
* **`PieceManager.java`**: A thread-safe global registry tracking which pieces we have, and calculating exact global download speeds via `AtomicLong`.
* **`FileManager.java`**: Maps virtual piece indexes to physical bytes on your hard drive using `RandomAccessFile`. It seamlessly handles single and multi-file torrents.
* **Native File Pickers**: A custom bridge in the UI that detects your OS and forces the application to use native file managers (like Windows Explorer, KDE Dolphin, or GNOME Zenity) instead of generic Java popups.

---

## 📚 Deep Dive Documentation

For contributors who want to deeply understand the mechanics of this codebase, check out our detailed, heavily visual documentation files located in the `docs/` folder:

1. **[BitTorrent Protocol Basics](readme/01-Protocol-Basics.md)** - Bencoding, `.torrent` metadata, and Swarm mechanics.
2. **[Project Architecture & Core Components](readme/02-Project-Architecture.md)** - Threading model, Piece Manager, and Disk I/O structures.
3. **[Networking, Pipelining & Tit-for-Tat](readme/03-Networking-and-Choking.md)** - 20-block socket pipelining and swarm replenishment.
4. **[UI & Native OS Integration](readme/04-User-Interface-and-OS-Integration.md)** - JavaFX architecture and native Linux file picker hacks.
5. **[CI/CD Pipeline & Native Packaging](readme/05-CI-CD-Pipeline.md)** - Automated Fat JAR compilation and `jpackage` bundling.

---

## 🚀 How to Run and Build

### Option 1: Native Installers (Recommended)
This repository is configured with a fully automated GitHub Actions CI/CD pipeline. Every time a new version tag is pushed, it automatically compiles the application into native, double-clickable installers.
Go to the **[Releases](../../releases)** tab to download:
* `Windows Installer (.exe)` and `Windows Portable (.zip)`
* `macOS Installer (.dmg)`
* `Linux Debian/Ubuntu Installer (.deb)`
* `Linux Portable (.tar.gz)` (Ideal for Arch Linux / Fedora)

### Option 2: Compile from Source
Ensure you have **Java 21** and **Maven** installed.
```bash
# Clone the repository
git clone https://github.com/adicodeswell/Bittorrent-Client.git
cd Bittorrent-Client

# Run directly via Maven
mvn clean javafx:run
```

---

## 🛠️ Contribution Roadmap
We have a rock-solid foundation, but modern torrenting requires a few more features. Pull requests are welcome for the following missing features (see **[06-Contribution-Roadmap.md](readme/06-Contribution-Roadmap.md)** for details):
- [ ] **UDP Trackers (BEP 15):** The client currently only supports HTTP/HTTPS trackers.
- [ ] **IPv6 Support:** Upgrading the Bencode parser to safely extract 18-byte IPv6 addresses.
- [ ] **DHT & Magnet Links (BEP 5):** Allowing trackerless peer discovery via Magnet links.
- [ ] **Peers UI Tab:** A secondary UI table to visualize active connected IP addresses and individual speeds.
- [ ] **Multi-File UI Selection:** Allowing the user to uncheck files they don't want to download.
