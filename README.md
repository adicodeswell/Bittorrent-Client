# JavaFX BitTorrent Client

![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![UI](https://img.shields.io/badge/UI-JavaFX-brightgreen)

A fully functional, cross-platform BitTorrent client built from scratch in Java 21 and JavaFX. 

This project was created to implement the core mechanics of the BitTorrent protocol, including peer-to-peer TCP networking, Bencode parsing, SHA-1 piece verification, and the famous Tit-for-Tat choking algorithm, all bundled into a beautiful native desktop app.

## 📚 Project Documentation

To make this codebase as easy to understand and contribute to as possible, the documentation has been broken down into a series of detailed deep-dives. 

If you want to understand how this client works under the hood, read these in order:

1. **[BitTorrent Protocol Basics](readme/01-Protocol-Basics.md)** - Learn about Bencoding, `.torrent` metadata, and how P2P swarms actually operate.
2. **[Project Architecture & Core Components](readme/02-Project-Architecture.md)** - A deep dive into the Threading model, Piece Manager, and Disk I/O structures.
3. **[Networking, Pipelining & Tit-for-Tat](readme/03-Networking-and-Choking.md)** - How we achieve high speeds using 20-block socket pipelining and swarm replenishment.
4. **[UI & Native OS Integration](readme/04-User-Interface-and-OS-Integration.md)** - Bridging JavaFX to native KDE Dolphin and GNOME Zenity file pickers on Linux.
5. **[CI/CD Pipeline & Native Packaging](readme/05-CI-CD-Pipeline.md)** - How GitHub Actions magically compiles the Java code into standalone `.exe`, `.dmg`, and `.tar.gz` installers.

## 🚀 How to Run and Build

### Option 1: Native Installers (Recommended)
This repository uses GitHub Actions to automatically compile the application into native, double-clickable installers with an embedded Java Runtime.
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

# Package into a Fat JAR
mvn clean package -DskipTests
```

## 🛠️ Want to Contribute?
We have laid down a rock-solid foundation, but there is still plenty to build! If you are interested in expanding this client, check out our **[Contribution Roadmap](readme/06-Contribution-Roadmap.md)** to see what features are currently missing (UDP Trackers, DHT, IPv6 Support, etc.).

## License
MIT License. Feel free to fork, learn, and contribute!
