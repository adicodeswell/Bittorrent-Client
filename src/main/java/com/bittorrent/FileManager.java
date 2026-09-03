package com.bittorrent;

import java.io.RandomAccessFile;
import java.io.IOException;

public class FileManager {

    private final RandomAccessFile file;
    private final TorrentInfo torrentInfo;

    public FileManager(String filePath, TorrentInfo torrentInfo) throws IOException {
        this.torrentInfo = torrentInfo;
        this.file = new RandomAccessFile(filePath, "rw");
        
        // Pre-allocate the file size
        this.file.setLength(torrentInfo.getFileLength());
    }

    public synchronized void writePiece(int pieceIndex, byte[] pieceData) throws IOException {
        // TODO: Milestone 7 - Calculate exact offset and write pieceData safely
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
