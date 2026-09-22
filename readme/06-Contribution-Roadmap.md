# Contribution Roadmap

This project successfully implements all major foundations of a desktop BitTorrent client. However, modern torrenting has evolved significantly. If you wish to contribute to the codebase, here are the major missing features that would bring this client up to production standards:

## 1. UDP Tracker Protocol (BEP 15)
**The Problem:** The `TrackerClient.java` currently only knows how to speak HTTP/HTTPS. Over the last decade, the BitTorrent community has largely deprecated HTTP trackers due to server load, favoring lightweight UDP trackers. If a torrent file *only* provides `udp://` trackers, this client will fail to find any peers.
**The Fix:** Implement the UDP Tracker protocol specification, managing UDP socket datagrams to request connection IDs and announce payloads.

## 2. IPv6 Peer Parsing
**The Problem:** Some modern trackers respond with IPv6 addresses. Currently, `TrackerClient.java` parses the "compact" peer list rigidly by slicing the byte array into 6-byte chunks (4 bytes for IPv4, 2 for port). If an 18-byte IPv6 address is injected into the response, the parser will misalign, generating fake IP addresses and resulting in `0 KB/s` download speeds.
**The Fix:** Read the `peers6` key from the tracker Bencode response if available, and implement a dedicated IPv6 parsing loop.

## 3. The "Peers" UI Tab
**The Problem:** The JavaFX UI currently only shows the main Dashboard (Name, Size, Progress, Speed). It does not show you *who* you are connected to.
**The Fix:** Create a new tab in the UI. Map the active `PeerConnection` array in `Main.java` to a new `ObservableList`. Bind it to a TableView that displays the IP Address, Download Speed, Upload Speed, and Client Software of every active peer in the swarm.

## 4. DHT & Magnet Links (BEP 5 & BEP 9)
**The Problem:** Users must manually download a physical `.torrent` file to use this app.
**The Fix:** Allow users to paste a `magnet:?xt=urn:btih:...` link. Implement a Distributed Hash Table (DHT) node that can query the global DHT network to magically resolve the info hash into the actual torrent metadata without needing a centralized tracker.

## 5. Multi-File UI Selection
**The Problem:** While `FileManager.java` is fully capable of allocating multiple files and folders on the disk, the UI does not allow the user to see the contents of a torrent before downloading, nor does it allow them to "skip" certain files in a large batch.
**The Fix:** Add a popup window immediately after selecting a `.torrent` file containing a TreeView of the files, allowing the user to uncheck boxes for files they don't want. Update `PieceManager` to ignore pieces mapped exclusively to skipped files.
