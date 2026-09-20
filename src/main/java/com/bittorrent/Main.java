package com.bittorrent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Usage: java -jar bittorrent-client.jar <torrent-file>");
            System.exit(1);
        }

            try {
                // Parse the .torrent file (already working from Milestone 2)
                TorrentInfo torrent = loadTorrent(args[0]);
                printTorrentInfo(torrent);

                // Ask the tracker for peers
                TrackerClient tracker = new TrackerClient();
                List<TrackerClient.PeerAddress> peers = tracker.getPeers(
                        torrent, 6881, 0, 0, torrent.getFileLength()
                );
                System.out.println("Found " + peers.size() + " peers from tracker.");

                // For now, trying to handshake with just the first peer
                if (peers.isEmpty()) {
                    System.out.println("No peers available.");
                    return;
                }

                PieceManager pieceManager = new PieceManager(torrent.getPieceHashes().size());

                // Initialize one FileManager for all threads to share
                FileManager fileManager = new FileManager(".", torrent);
                System.out.println("Launching concurrent connections to  " + peers.size() + " peers...");

                // Try peers one by one until we get a successful download
                for (TrackerClient.PeerAddress peer : peers) {
                    PeerConnection connection = new PeerConnection(
                            peer, tracker.getPeerId(), torrent, fileManager, pieceManager
                    );

                    // Launch instantly in the background and move to the next peer
                    Thread.ofVirtual().start(connection);
                }

                // Keep the main thread alive until the download is completely finished
                while (!pieceManager.isFinished()) {
                    Thread.sleep(1000); // Pause for 1 second, then check again
                }

                // Once the loop breaks, we are done!
                System.out.println("\n100% DOWNLOAD COMPLETE!");
                System.out.println("File saved to your project directory.");

            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                System.exit(1);
            }
    }

    private static TorrentInfo loadTorrent(String path) throws Exception {
        Path torrentPath = Path.of(path);

        if (!Files.exists(torrentPath)) {
            throw new IllegalArgumentException(
                    "Torrent file not found: " + path
            );
        }

        if (!Files.isRegularFile(torrentPath)) {
            throw new IllegalArgumentException(
                    "Not a file: " + path
            );
        }

        byte[] data = Files.readAllBytes(torrentPath);

        return TorrentInfo.parse(data);
    }

    private static void printTorrentInfo(TorrentInfo torrent) {

        System.out.println("========== BitTorrent ==========");
        System.out.println();

        System.out.println("Announce URL : " + torrent.getAnnounceUrl());
        System.out.println("File Length  : " + torrent.getFileLength() + " bytes");
        System.out.println("Piece Length : " + torrent.getPieceLength() + " bytes");
        System.out.println("Pieces       : " + torrent.getPieceHashes().size());
        System.out.println("Info Hash    : " + toHex(torrent.getInfoHash()));

        System.out.println();
        System.out.println("================================");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);

        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }

        return result.toString();
    }
}