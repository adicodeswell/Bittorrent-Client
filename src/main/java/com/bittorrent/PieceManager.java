package com.bittorrent;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PieceManager {

    private final TorrentInfo torrentInfo;
    private final FileManager fileManager;
    
    // Tracks pieces completed locally
    private final BitSet localPieces = new BitSet();
    // Tracks pieces available on connected peers
    private final Map<PeerConnection, BitSet> peerPieces = new ConcurrentHashMap<>();

    public PieceManager(TorrentInfo torrentInfo, FileManager fileManager) {
        this.torrentInfo = torrentInfo;
        this.fileManager = fileManager;
    }

    public synchronized void blockReceived(PeerConnection peer, int pieceIndex, int begin, byte[] blockData) {
        // TODO: Milestone 7 - Store block in pre-allocated buffer
        // Verify SHA-1 if piece is complete. Write to FileManager if valid.
    }

    public synchronized PeerMessage getNextRequest(PeerConnection peer) {
        // TODO: Milestone 8 & 10 - Evaluate available/in-progress pieces.
        // Return a new REQUEST message (use simplest selection first, rarest-first later).
        return null;
    }

    public synchronized void handlePeerDisconnect(PeerConnection peer) {
        // TODO: Milestone 8 - Re-assign any in-progress pieces from this peer
    }
}
