package com.bittorrent;

import java.util.BitSet;

public class PieceManager {
    private final int totalPieces;
    private final BitSet completedPieces = new BitSet();
    private final BitSet pendingPieces = new BitSet();

    public PieceManager(int totalPieces) {
        this.totalPieces = totalPieces;
    }

    // Thread-safe method to get the next missing piece
    public synchronized int getNextPiece() {
        for (int i = 0; i < totalPieces; i++) {
            if (!completedPieces.get(i) && !pendingPieces.get(i)) {
                pendingPieces.set(i); // Mark it as "Currently being downloaded"
                return i;
            }
        }
        return -1; // -1 means all pieces are either completed or currently being downloaded by someone else
    }

    public synchronized void markCompleted(int index) {
        pendingPieces.clear(index);
        completedPieces.set(index);
    }

    // If a peer disconnects or sends bad data, we put the piece back in the pool
    public synchronized void markMissing(int index) {
        if (index != -1) {
            pendingPieces.clear(index);
        }
    }

    public synchronized boolean isFinished() {
        return completedPieces.cardinality() == totalPieces;
    }
}