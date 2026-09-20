package com.bittorrent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PieceManagerTest {

    @Test
    public void testGetNextPiece() {
        PieceManager manager = new PieceManager(3);

        assertEquals(0, manager.getNextPiece(), "First piece should be 0");
        assertEquals(1, manager.getNextPiece(), "Second piece should be 1");
        assertEquals(2, manager.getNextPiece(), "Third piece should be 2");
        assertEquals(-1, manager.getNextPiece(), "Should return -1 when all pieces are pending");
    }

    @Test
    public void testMarkCompleted() {
        PieceManager manager = new PieceManager(2);

        int piece0 = manager.getNextPiece();
        manager.markCompleted(piece0);

        assertFalse(manager.isFinished(), "Not all pieces are finished yet");

        int piece1 = manager.getNextPiece();
        manager.markCompleted(piece1);

        assertTrue(manager.isFinished(), "All pieces should be finished");
        assertEquals(-1, manager.getNextPiece(), "No pieces left to download");
    }

    @Test
    public void testMarkMissingRecyclesPiece() {
        PieceManager manager = new PieceManager(2);

        int piece0 = manager.getNextPiece(); // returns 0
        int piece1 = manager.getNextPiece(); // returns 1
        assertEquals(-1, manager.getNextPiece(), "All pieces pending");

        // Simulate a peer dropping connection while downloading piece 0
        manager.markMissing(piece0);

        // Another peer asks for a piece, should get 0 back
        assertEquals(0, manager.getNextPiece(), "Missing piece should be re-assigned");
    }
}
