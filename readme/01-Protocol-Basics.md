# BitTorrent Protocol Basics

To understand this codebase, you must first understand how the BitTorrent protocol distributes files.

## 1. Peer-to-Peer vs Client-Server
In a traditional client-server model (like downloading a file from a website), your browser connects to a single server and downloads the file from start to finish. If the server goes offline or has slow upload speeds, your download suffers.

BitTorrent is a **Peer-to-Peer (P2P)** protocol. A large file is mathematically chopped up into hundreds of small chunks called **Pieces** (usually 256 KB or 512 KB each). Instead of downloading from a central server, you connect to dozens of other ordinary computer users (called **Peers**) who are downloading the same file. 
* You download Piece #1 from Peer A.
* You download Piece #2 from Peer B.
* Simultaneously, you **upload** Piece #1 to Peer C.

This creates a highly resilient **Swarm**. The more people downloading the file, the faster the download speeds become.

## 2. Bencoding
BitTorrent uses a custom binary serialization format called **Bencoding** to transmit data. Everything from the `.torrent` file to the tracker's HTTP response is Bencoded.
* **Strings:** `<length>:<string>` (e.g., `4:spam` represents "spam")
* **Integers:** `i<integer>e` (e.g., `i3e` represents 3)
* **Lists:** `l<contents>e` (e.g., `l4:spami42ee` represents `["spam", 42]`)
* **Dictionaries:** `d<contents>e` (e.g., `d3:bar4:spam3:fooi42ee` represents `{"bar": "spam", "foo": 42}`)

*In this project, the `Bencoder` class handles all Bencode parsing recursively.*

## 3. The `.torrent` File
A `.torrent` file contains no actual file data. It is a Bencoded dictionary containing metadata:
* `announce`: The URL of the Tracker server.
* `info`: A dictionary containing the file's name, size, piece length, and a single concatenated string of 20-byte **SHA-1 Hashes**. 
Every time you download a 256 KB piece from a stranger on the internet, your client calculates its SHA-1 hash. If it matches the hash in the `.torrent` file, you keep the data. If it doesn't match, the data was corrupted (or malicious) and your client drops the peer.

## 4. The Tracker
The tracker is a centralized server (e.g., `http://bttracker.debian.org:6969/announce`). Its only job is to introduce peers to each other.
When our client starts, it sends an HTTP GET request to the tracker containing our **Info Hash** (the unique ID of the torrent) and our IP/Port. The tracker responds with a Bencoded dictionary containing a list of other IPs and Ports currently in the swarm.
