package com.bittorrent;

import java.util.List;

public class TorrentInfo {
    private final byte[] infoHash;
    private final List<byte[]> pieceHashes;
    private final long fileLength;
    private final int pieceLength;
    private final String announceUrl;

    public TorrentInfo(byte[] infoHash, List<byte[]> pieceHashes, long fileLength, int pieceLength, String announceUrl) {
        this.infoHash = infoHash;
        this.pieceHashes = pieceHashes;
        this.fileLength = fileLength;
        this.pieceLength = pieceLength;
        this.announceUrl = announceUrl;
    }

    public static TorrentInfo parse(byte[] fileBytes) {
        // TODO: Implement metadata extraction and accurate info_hash calculation (Milestone 2)
        // Ensure you reject multi-file torrents if "files" key exists.
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public byte[] getInfoHash() { return infoHash; }
    public List<byte[]> getPieceHashes() { return pieceHashes; }
    public long getFileLength() { return fileLength; }
    public int getPieceLength() { return pieceLength; }
    public String getAnnounceUrl() { return announceUrl; }
}
