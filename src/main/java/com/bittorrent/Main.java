package com.bittorrent;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.err.println("Usage: java -jar bittorrent-client.jar <torrent-file>");
            System.exit(1);
        }

        try {
            TorrentInfo torrent = loadTorrent(args[0]);

            printTorrentInfo(torrent);

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