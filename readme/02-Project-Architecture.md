# System Architecture & Core Components

This project is built using Java 21 Virtual Threads, avoiding standard thread pools to achieve massive, lightweight concurrency without bogging down the JavaFX Application Thread.

## 1. Engine Entry Point (`Main.java`)
`Main.java` is the orchestrator. When the user selects a `.torrent` file, `Main.runTorrent(path, uiModel)` is triggered.
It initializes the core data structures (`TorrentInfo`, `PieceManager`, `FileManager`) and starts a background thread that serves as the **Choke & Replenish Loop**.

## 2. Central State (`PieceManager.java`)
Because dozens of peers are downloading blocks of data concurrently, we need a thread-safe brain to track what we have and what we need.
* **`peerPieces` (BitSet):** A fast array of booleans tracking which pieces have been fully verified.
* **`AtomicLong totalBytesDownloaded`:** A thread-safe counter. Every time any peer thread receives a block of data, it adds to this counter. The UI thread reads this exact value once per second to calculate the exact `KB/s` or `MB/s` display.
* **`getPieceAssignment()`:** Ensures that if two peers ask for work, they are not assigned the same piece, preventing redundant bandwidth usage.

## 3. Disk I/O (`FileManager.java`)
BitTorrent allows downloading pieces entirely out of order. You might download the very last byte of a movie before you download the first byte.
* `FileManager` uses `java.io.RandomAccessFile`. 
* On startup, it pre-allocates the exact file size on the hard drive (e.g., creating a 700MB empty file).
* When a peer finishes downloading a block, `FileManager.writePiece(pieceIndex, blockOffset, data)` calculates the exact global byte offset (`pieceIndex * pieceLength + blockOffset`) and uses `seek()` to write the bytes directly into the physical file structure.
* **Multi-file support:** If a torrent contains a folder of multiple files, `FileManager` maps the virtual 1D byte array across multiple physical files on the disk, completely abstracting the directory structure away from the network layer.

## 4. The Metadata (`TorrentInfo.java`)
This class parses the `.torrent` file bytes, decodes the Bencode structure, and resolves the best usable HTTP tracker URL from the `announce-list` tiers. It also calculates the critical `info_hash` required to identify the swarm.
