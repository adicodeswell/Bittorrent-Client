package com.bittorrent;

import org.junit.jupiter.api.Test;
import java.util.BitSet;
import static org.junit.jupiter.api.Assertions.*;

public class PieceManagerTest {

    private BitSet createFullBitSet(int size) {
        BitSet bs = new BitSet();
        bs.set(0, size);
        return bs;
    }

    @Test
    public void testGetNextPiece() {
        PieceManager manager = new PieceManager(3);
        BitSet peerPieces = createFullBitSet(3);

        assertEquals(0, manager.getNextPiece(peerPieces), "First piece should be 0");
        assertEquals(1, manager.getNextPiece(peerPieces), "Second piece should be 1");
        assertEquals(2, manager.getNextPiece(peerPieces), "Third piece should be 2");
        assertEquals(-1, manager.getNextPiece(peerPieces), "Should return -1 when all pieces are pending");
    }

    @Test
    public void testMarkCompleted() {
        PieceManager manager = new PieceManager(2);
        BitSet peerPieces = createFullBitSet(2);

        int piece0 = manager.getNextPiece(peerPieces);
        manager.markCompleted(piece0);

        assertFalse(manager.isFinished(), "Not all pieces are finished yet");

        int piece1 = manager.getNextPiece(peerPieces);
        manager.markCompleted(piece1);

        assertTrue(manager.isFinished(), "All pieces should be finished");
        assertEquals(-1, manager.getNextPiece(peerPieces), "No pieces left to download");
    }

    @Test
    public void testMarkMissingRecyclesPiece() {
        PieceManager manager = new PieceManager(2);
        BitSet peerPieces = createFullBitSet(2);

        int piece0 = manager.getNextPiece(peerPieces); // returns 0
        int piece1 = manager.getNextPiece(peerPieces); // returns 1
        assertEquals(-1, manager.getNextPiece(peerPieces), "All pieces pending");

        // Simulate a peer dropping connection while downloading piece 0
        manager.markMissing(piece0);

        // Another peer asks for a piece, should get 0 back
        assertEquals(0, manager.getNextPiece(peerPieces), "Missing piece should be re-assigned");
    }

    @Test
    public void testRarestFirstSelection() {
        PieceManager manager = new PieceManager(3);
        BitSet peerPieces = createFullBitSet(3);

        // Simulate network frequency: Piece 0 is common, Piece 1 is common, Piece 2 is rare
        manager.recordPieceAvailability(0);
        manager.recordPieceAvailability(0);
        manager.recordPieceAvailability(1);
        manager.recordPieceAvailability(1);
        manager.recordPieceAvailability(2);

        // Because Piece 2 has the lowest frequency (1), it should be selected first!
        assertEquals(2, manager.getNextPiece(peerPieces), "Rarest piece (2) should be selected first");
        assertEquals(0, manager.getNextPiece(peerPieces), "Next piece should be 0");
        assertEquals(1, manager.getNextPiece(peerPieces), "Next piece should be 1");
    }
}
