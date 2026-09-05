package com.bittorrent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TorrentInfo {

    private final byte[] infoHash;
    private final List<byte[]> pieceHashes;
    private final long fileLength;
    private final int pieceLength;
    private final String announceUrl;

    public TorrentInfo(byte[] infoHash, List<byte[]> pieceHashes,
                       long fileLength, int pieceLength, String announceUrl) {
        this.infoHash = infoHash;
        this.pieceHashes = pieceHashes;
        this.fileLength = fileLength;
        this.pieceLength = pieceLength;
        this.announceUrl = announceUrl;
    }

    public static TorrentInfo parse(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0)
            throw new IllegalArgumentException("Invalid torrent file");

        Object decoded = Bencoder.decode(fileBytes);

        if (!(decoded instanceof Map<?, ?>))
            throw new IllegalArgumentException("Invalid torrent file");

        Map<String, Object> torrent = (Map<String, Object>) decoded;
        Object announce = torrent.get("announce");
        Object infoObject = torrent.get("info");

        if (!(announce instanceof byte[]) ||
                !(infoObject instanceof Map<?, ?>))
            throw new IllegalArgumentException("Invalid torrent metadata");

        Map<String, Object> info = (Map<String, Object>) infoObject;

        if (info.containsKey("files"))
            throw new UnsupportedOperationException("Multi-file torrents are not supported");

        Object length = info.get("length");
        Object pieceLengthObject = info.get("piece length");
        Object piecesObject = info.get("pieces");

        if (!(length instanceof Long) ||
                !(pieceLengthObject instanceof Long) ||
                !(piecesObject instanceof byte[]))
            throw new IllegalArgumentException("Invalid torrent metadata");

        long fileLength = (Long) length;
        int pieceLength = Math.toIntExact((Long) pieceLengthObject);
        byte[] pieces = (byte[]) piecesObject;

        if (fileLength < 0 || pieceLength <= 0 || pieces.length % 20 != 0)
            throw new IllegalArgumentException("Invalid torrent metadata");

        List<byte[]> pieceHashes = new ArrayList<>();

        for (int i = 0; i < pieces.length; i += 20) {
            byte[] hash = new byte[20];
            System.arraycopy(pieces, i, hash, 0, 20);
            pieceHashes.add(hash);
        }

        long expectedPieces = (fileLength + pieceLength - 1) / pieceLength;

        if (pieceHashes.size() != expectedPieces)
            throw new IllegalArgumentException("Invalid number of piece hashes");

        byte[] infoHash = calculateInfoHash(fileBytes);

        return new TorrentInfo(
                infoHash,
                pieceHashes,
                fileLength,
                pieceLength,
                new String((byte[]) announce, StandardCharsets.UTF_8)
        );
    }

    private static byte[] calculateInfoHash(byte[] data) {
        int pos = 1;

        while (pos < data.length && data[pos] != 'e') {
            int keyStart = pos;
            int colon = findColon(data, pos);
            int keyLength = Integer.parseInt(new String(
                    data, keyStart, colon - keyStart, StandardCharsets.US_ASCII
            ));

            pos = colon + 1;

            String key = new String(
                    data, pos, keyLength, StandardCharsets.UTF_8
            );

            pos += keyLength;

            if (key.equals("info")) {
                int end = findEnd(data, pos);
                byte[] info = new byte[end - pos];
                System.arraycopy(data, pos, info, 0, info.length);

                try {
                    return MessageDigest.getInstance("SHA-1").digest(info);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }

            pos = skip(data, pos);
        }

        throw new IllegalArgumentException("Info dictionary not found");
    }

    private static int findEnd(byte[] data, int pos) {
        int depth = 0;

        while (pos < data.length) {
            byte c = data[pos];

            if (c == 'd' || c == 'l') {
                depth++;
                pos++;
            } else if (c == 'e') {
                if (--depth == 0)
                    return pos + 1;
                pos++;
            } else if (c >= '0' && c <= '9') {
                pos = skipString(data, pos);
            } else if (c == 'i') {
                pos = skipInteger(data, pos);
            } else {
                throw new IllegalArgumentException("Invalid Bencode");
            }
        }

        throw new IllegalArgumentException("Invalid Bencode");
    }

    private static int skip(byte[] data, int pos) {
        byte c = data[pos];

        if (c >= '0' && c <= '9')
            return skipString(data, pos);

        if (c == 'i')
            return skipInteger(data, pos);

        if (c == 'd' || c == 'l')
            return findEnd(data, pos);

        throw new IllegalArgumentException("Invalid Bencode");
    }

    private static int skipString(byte[] data, int pos) {
        int colon = findColon(data, pos);
        int length = Integer.parseInt(new String(
                data, pos, colon - pos, StandardCharsets.US_ASCII
        ));

        int end = colon + 1 + length;

        if (end > data.length)
            throw new IllegalArgumentException("Invalid byte string");

        return end;
    }

    private static int skipInteger(byte[] data, int pos) {
        pos++;

        while (pos < data.length && data[pos] != 'e')
            pos++;

        if (pos >= data.length)
            throw new IllegalArgumentException("Invalid integer");

        return pos + 1;
    }

    private static int findColon(byte[] data, int pos) {
        while (pos < data.length && data[pos] != ':') {
            if (data[pos] < '0' || data[pos] > '9')
                throw new IllegalArgumentException("Invalid string length");
            pos++;
        }

        if (pos >= data.length)
            throw new IllegalArgumentException("Invalid string");

        return pos;
    }

    public byte[] getInfoHash() {
        return infoHash;
    }

    public List<byte[]> getPieceHashes() {
        return pieceHashes;
    }

    public long getFileLength() {
        return fileLength;
    }

    public int getPieceLength() {
        return pieceLength;
    }

    public String getAnnounceUrl() {
        return announceUrl;
    }
}