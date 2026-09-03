package com.bittorrent;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar bittorrent-client.jar <path_to_torrent_file>");
            System.exit(1);
        }

        String torrentFilePath = args[0];
        
        try {
            // TODO: Milestone 2 - Parse .torrent file using TorrentInfo.parse()
            
            // TODO: Milestone 3 - Generate Peer ID, call TrackerClient to get peers
            
            // TODO: Milestone 7 - Initialize FileManager and PieceManager
            
            // TODO: Milestone 9 - Start Virtual Threads for each PeerConnection
            // Example: Thread.ofVirtual().start(new PeerConnection(...));
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
