package com.bittorrent;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class FileManager {

    // Represents one physical file on disk and its byte offset within the torrent's virtual stream
    private record FileSlice(RandomAccessFile raf, long startOffset, long length) {}

    private final List<FileSlice> fileSlices = new ArrayList<>();
    private final TorrentInfo torrentInfo;

    public FileManager(String outputDir, TorrentInfo torrentInfo) throws IOException {
        this.torrentInfo = torrentInfo;

        long offset = 0;
        for (TorrentInfo.FileEntry entry : torrentInfo.getFiles()) {
            File file = new File(outputDir, entry.path());

            // Create parent directories if they don't exist (needed for multi-file torrents)
            file.getParentFile().mkdirs();

            RandomAccessFile raf = new RandomAccessFile(file, "rw");

            // Pre-allocate the exact size so we can seek and write anywhere later
            raf.setLength(entry.length());

            fileSlices.add(new FileSlice(raf, offset, entry.length()));
            offset += entry.length();
        }
    }

    public synchronized byte[] readPiece(int pieceIndex) throws IOException {
        // 1. Determine the size of this piece (the last piece might be smaller)
        long totalPieces = (torrentInfo.getTotalLength() + torrentInfo.getPieceLength() - 1) / torrentInfo.getPieceLength();
        int pieceSize = torrentInfo.getPieceLength();
        if (pieceIndex == totalPieces - 1) {
            long remainder = torrentInfo.getTotalLength() % torrentInfo.getPieceLength();
            if (remainder != 0) pieceSize = (int) remainder;
        }

        byte[] pieceData = new byte[pieceSize];
        long pieceStart = (long) pieceIndex * torrentInfo.getPieceLength();
        int bytesRead = 0;

        // 2. Read the bytes, handling file boundaries exactly like writePiece()
        while (bytesRead < pieceData.length) {
            long globalOffset = pieceStart + bytesRead;
            FileSlice slice = findSlice(globalOffset);
            if (slice == null) break;

            long offsetWithinFile = globalOffset - slice.startOffset();
            long bytesAvailableInFile = slice.length() - offsetWithinFile;
            int toRead = (int) Math.min(bytesAvailableInFile, pieceData.length - bytesRead);

            slice.raf().seek(offsetWithinFile);
            slice.raf().readFully(pieceData, bytesRead, toRead);

            bytesRead += toRead;
        }

        return pieceData;
    }

    public synchronized byte[] readBlock(int pieceIndex, int begin, int length) throws IOException {
        byte[] blockData = new byte[length];
        long globalOffset = (long) pieceIndex * torrentInfo.getPieceLength() + begin;
        int bytesRead = 0;

        while (bytesRead < length) {
            FileSlice slice = findSlice(globalOffset + bytesRead);
            if (slice == null) break;

            long offsetWithinFile = (globalOffset + bytesRead) - slice.startOffset();
            long bytesAvailable = slice.length() - offsetWithinFile;
            int toRead = (int) Math.min(bytesAvailable, length - bytesRead);

            slice.raf().seek(offsetWithinFile);
            slice.raf().readFully(blockData, bytesRead, toRead);
            bytesRead += toRead;
        }
        return blockData;
    }

    public synchronized void writePiece(int pieceIndex, int blockOffset, byte[] blockData) throws IOException {
        // Calculate the byte offset of this block within the virtual concatenated stream
        long pieceStart = (long) pieceIndex * torrentInfo.getPieceLength() + blockOffset;
        int bytesWritten = 0;

        while (bytesWritten < blockData.length) {
            long globalOffset = pieceStart + bytesWritten;

            // Find which physical file this byte offset falls into
            FileSlice slice = findSlice(globalOffset);
            if (slice == null) break;

            // How many bytes into this file does our write begin?
            long offsetWithinFile = globalOffset - slice.startOffset();

            // How many bytes can we write before hitting the end of this file?
            long bytesAvailableInFile = slice.length() - offsetWithinFile;
            int bytesRemaining = blockData.length - bytesWritten;
            int toWrite = (int) Math.min(bytesAvailableInFile, bytesRemaining);

            slice.raf().seek(offsetWithinFile);
            slice.raf().write(blockData, bytesWritten, toWrite);

            bytesWritten += toWrite;
        }
    }

    private FileSlice findSlice(long globalOffset) {
        for (FileSlice slice : fileSlices) {
            if (globalOffset >= slice.startOffset() &&
                globalOffset < slice.startOffset() + slice.length()) {
                return slice;
            }
        }
        return null;
    }

    public void close() throws IOException {
        for (FileSlice slice : fileSlices) {
            slice.raf().close();
        }
    }
}
