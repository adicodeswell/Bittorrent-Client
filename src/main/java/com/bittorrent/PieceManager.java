package com.bittorrent;

import java.util.BitSet;

public class PieceManager {
    private final int totalPieces;
    private final BitSet completedPieces = new BitSet();
    private final BitSet pendingPieces = new BitSet();
    private final int[] pieceFrequency;

    public PieceManager(int totalPieces) {
        this.totalPieces = totalPieces;
        this.pieceFrequency = new int[totalPieces];
    }

    // Called every time a peer tells us they have a piece
    public synchronized void recordPieceAvailability(int index) {
        if (index >= 0 && index < totalPieces) {
            pieceFrequency[index]++;
        }
    }

    // Thread-safe method to get the next missing piece
    public synchronized int getNextPiece(BitSet peerPieces) {
        int rarestPiece = -1;
        int minAvailability = Integer.MAX_VALUE;

        for (int i = 0; i < totalPieces; i++) {
            // 1. Do we need it?
            // 2. Is nobody else currently downloading it?
            // 3. Does THIS specific peer actually have it?
            if (!completedPieces.get(i) && !pendingPieces.get(i) && peerPieces.get(i)) {

                // 4. Is it the rarest one we've seen so far?
                if (pieceFrequency[i] < minAvailability) {
                    minAvailability = pieceFrequency[i];
                    rarestPiece = i;
                }
            }
        }

        if (rarestPiece != -1) {
            pendingPieces.set(rarestPiece);
        }
        return rarestPiece; // Returns -1 if no pieces match
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