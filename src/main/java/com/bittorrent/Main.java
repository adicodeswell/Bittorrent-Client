package com.bittorrent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    // Removed the old main() string[] args to convert this into a reusable backend engine method!
    public static void runTorrent(String torrentPath, com.bittorrent.ui.TorrentModel uiModel) throws Exception {
        try {
            // Parse the .torrent file
            TorrentInfo torrent = loadTorrent(torrentPath);
            printTorrentInfo(torrent);
            
            uiModel.setName(new java.io.File(torrentPath).getName());
            uiModel.setSize((torrent.getTotalLength() / (1024 * 1024)) + " MB");
            uiModel.setStatus("Connecting to tracker...");

            // Ask the tracker for peers
            TrackerClient tracker = new TrackerClient();
            List<TrackerClient.PeerAddress> peers = tracker.getPeers(
                    torrent, 6881, 0, 0, torrent.getFileLength()
            );
            System.out.println("Found " + peers.size() + " peers from tracker.");

            if (peers.isEmpty()) {
                System.out.println("No peers available.");
                uiModel.setStatus("No peers available");
                return;
            }

            PieceManager pieceManager = new PieceManager(torrent.getPieceHashes().size());
            FileManager fileManager = new FileManager(".", torrent);

            // 1. Scan the disk to see what we already have
            checkExistingFiles(torrent, fileManager, pieceManager, uiModel);

            // 2. If the scan proves we have 100% of the file, just exit
            if (pieceManager.isFinished()) {
                System.out.println("🎉 File is already fully downloaded! 🎉");
                uiModel.setProgress(1.0);
                uiModel.setStatus("Completed / Seeding");
                uiModel.setSpeed("0 KB/s");
                return;
            }
            
            System.out.println("Launching concurrent connections to " + peers.size() + " peers...");
            uiModel.setStatus("Downloading...");

            java.util.List<PeerConnection> activeConnections = new java.util.concurrent.CopyOnWriteArrayList<>();

            for (TrackerClient.PeerAddress peer : peers) {
                PeerConnection connection = new PeerConnection(
                        peer, tracker.getPeerId(), torrent, fileManager, pieceManager
                );
                activeConnections.add(connection);
                Thread.ofVirtual().start(connection);
            }

            // Keep track of peers we've already tried
            java.util.Set<String> knownIps = java.util.concurrent.ConcurrentHashMap.newKeySet();
            for (TrackerClient.PeerAddress p : peers) knownIps.add(p.ip());
            
            // Choking algorithm and peer replenishment thread
            Thread.ofVirtual().start(() -> {
                long lastTrackerUpdate = System.currentTimeMillis();
                int tick = 0;
                
                while (true) {
                    try {
                        Thread.sleep(1000); 
                        if (uiModel != null && uiModel.isPaused()) continue;
                        
                        tick++;
                        if (tick < 10) continue;
                        tick = 0; // Run every 10 active seconds
                        
                        // -- 1. Replenish Peers if we drop too low! --
                        long aliveCount = activeConnections.stream().filter(p -> !p.isClosed()).count();
                        if (aliveCount < 15 && (System.currentTimeMillis() - lastTrackerUpdate > 30000)) {
                            lastTrackerUpdate = System.currentTimeMillis();
                            try {
                                if (uiModel != null) uiModel.setStatus("Asking tracker for more peers...");
                                List<TrackerClient.PeerAddress> newPeers = tracker.getPeers(torrent, 6881, 0, 0, torrent.getFileLength());
                                for (TrackerClient.PeerAddress peer : newPeers) {
                                    if (knownIps.add(peer.ip())) {
                                        PeerConnection connection = new PeerConnection(peer, tracker.getPeerId(), torrent, fileManager, pieceManager);
                                        activeConnections.add(connection);
                                        Thread.ofVirtual().start(connection);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

                        // -- 2. Tit-for-Tat Choking Algorithm --
                        java.util.List<PeerConnection> interestedPeers = new java.util.ArrayList<>();
                        for (PeerConnection p : activeConnections) {
                            if (!p.isClosed() && p.isPeerInterested()) {
                                interestedPeers.add(p);
                            }
                        }

                        interestedPeers.sort((a, b) ->
                                Integer.compare(b.getAndResetDownloadedBytes(), a.getAndResetDownloadedBytes())
                        );

                        for (int i = 0; i < interestedPeers.size(); i++) {
                            PeerConnection peer = interestedPeers.get(i);
                            try {
                                if (i < 3) {
                                    peer.unchoke(); 
                                } else if (i == 3) {
                                    peer.unchoke(); 
                                } else {
                                    peer.choke(); 
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });

            // Keep the main thread alive and update UI metrics
            long lastTotalBytes = pieceManager.getTotalBytesDownloaded();
            int totalPiecesForUi = torrent.getPieceHashes().size();
            boolean wasPaused = false;

            while (!pieceManager.isFinished()) {
                Thread.sleep(1000); 
                
                if (uiModel != null && uiModel.isPaused()) {
                    if (!wasPaused) {
                        uiModel.setStatus("Paused");
                        uiModel.setSpeed("0 KB/s");
                        // Drop all active connections to instantly halt bandwidth
                        for (PeerConnection p : activeConnections) p.close();
                        activeConnections.clear();
                        wasPaused = true;
                    }
                    continue;
                }
                
                if (wasPaused) {
                    uiModel.setStatus("Resuming...");
                    lastTotalBytes = pieceManager.getTotalBytesDownloaded();
                    wasPaused = false;
                }
                
                // Calculate Exact Byte Speed
                long currentTotalBytes = pieceManager.getTotalBytesDownloaded();
                long bytesDownloadedThisSecond = currentTotalBytes - lastTotalBytes;
                lastTotalBytes = currentTotalBytes;
                
                if (bytesDownloadedThisSecond > 1024 * 1024) {
                    uiModel.setSpeed(String.format("%.1f MB/s", bytesDownloadedThisSecond / (1024.0 * 1024.0)));
                } else {
                    uiModel.setSpeed((bytesDownloadedThisSecond / 1024) + " KB/s");
                }
                
                // Calculate Progress 
                int currentlyCompleted = pieceManager.getCompletedPieces().cardinality();
                double progress = (double) currentlyCompleted / totalPiecesForUi;
                uiModel.setProgress(progress);
                uiModel.setStatus(String.format("Downloading (%.1f%%)", progress * 100));
            }

            System.out.println("\n100% DOWNLOAD COMPLETE!");
            uiModel.setProgress(1.0);
            uiModel.setStatus("Completed / Seeding");
            uiModel.setSpeed("0 KB/s");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (uiModel != null) {
                uiModel.setStatus("Error");
            }
            throw e; // rethrow to be caught by UI thread
        }
    }

    private static TorrentInfo loadTorrent(String path) throws Exception {
        Path torrentPath = Path.of(path);
        if (!Files.exists(torrentPath)) {
            throw new IllegalArgumentException("Torrent file not found: " + path);
        }
        if (!Files.isRegularFile(torrentPath)) {
            throw new IllegalArgumentException("Not a file: " + path);
        }
        byte[] data = Files.readAllBytes(torrentPath);
        return TorrentInfo.parse(data);
    }

    private static void checkExistingFiles(TorrentInfo torrent, FileManager fileManager, PieceManager pieceManager, com.bittorrent.ui.TorrentModel uiModel) {
        System.out.println("Scanning existing files to resume download...");
        if (uiModel != null) uiModel.setStatus("Scanning files...");
        
        int validPieces = 0;
        int totalPieces = torrent.getPieceHashes().size();

        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            for (int i = 0; i < totalPieces; i++) {
                byte[] pieceData = fileManager.readPiece(i);
                byte[] calculatedHash = md.digest(pieceData);
                byte[] expectedHash = torrent.getPieceHashes().get(i);

                if (java.util.Arrays.equals(calculatedHash, expectedHash)) {
                    pieceManager.markCompleted(i);
                    validPieces++;
                }

                if (i > 0 && i % (totalPieces / 10) == 0) {
                    double scanProgress = (double) i / totalPieces;
                    if (uiModel != null) uiModel.setProgress(scanProgress);
                }
            }
        } catch (Exception e) {
            System.err.println("Error scanning existing files: " + e.getMessage());
        }
        
        // Final update for scan
        if (uiModel != null) {
            double finalScanProgress = (double) validPieces / totalPieces;
            uiModel.setProgress(finalScanProgress);
        }
    }

    private static void printTorrentInfo(TorrentInfo torrent) {
        System.out.println("========== BitTorrent ==========");
        System.out.println("Announce URL : " + torrent.getAnnounceUrl());
        System.out.println("File Length  : " + torrent.getFileLength() + " bytes");
        System.out.println("Piece Length : " + torrent.getPieceLength() + " bytes");
        System.out.println("Pieces       : " + torrent.getPieceHashes().size());
        System.out.println("Info Hash    : " + toHex(torrent.getInfoHash()));
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