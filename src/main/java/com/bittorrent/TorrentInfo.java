package com.bittorrent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TorrentInfo {

    // Represents a single file entry inside a multi-file torrent
    public record FileEntry(String path, long length) {}

    private final byte[] infoHash;
    private final List<byte[]> pieceHashes;
    private final long totalLength;      // Sum of all file lengths
    private final int pieceLength;
    private final String announceUrl;
    private final String name;           // Top-level name (file name or directory name)
    private final List<FileEntry> files; // Always populated — single file = 1 entry

    public TorrentInfo(byte[] infoHash, List<byte[]> pieceHashes,
                       long totalLength, int pieceLength,
                       String announceUrl, String name, List<FileEntry> files) {
        this.infoHash    = infoHash;
        this.pieceHashes = pieceHashes;
        this.totalLength = totalLength;
        this.pieceLength = pieceLength;
        this.announceUrl = announceUrl;
        this.name        = name;
        this.files       = Collections.unmodifiableList(files);
    }

    // ----------------------------------------------------------------
    // PARSE
    // ----------------------------------------------------------------

    public static TorrentInfo parse(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0)
            throw new IllegalArgumentException("Invalid torrent file");

        Object decoded = Bencoder.decode(fileBytes);

        if (!(decoded instanceof Map<?, ?>))
            throw new IllegalArgumentException("Invalid torrent file");

        Map<String, Object> torrent = (Map<String, Object>) decoded;
        Object announce   = torrent.get("announce");
        Object infoObject = torrent.get("info");

        if (!(announce instanceof byte[]) || !(infoObject instanceof Map<?, ?>))
            throw new IllegalArgumentException("Invalid torrent metadata");

        // Resolve the best usable tracker URL.
        // If the primary announce is a UDP tracker, scan announce-list for
        // the first HTTP/HTTPS tracker we can actually talk to.
        String announceUrl = new String((byte[]) announce, StandardCharsets.UTF_8);

        if (!announceUrl.startsWith("http") && torrent.containsKey("announce-list")) {
            List<Object> announceList = (List<Object>) torrent.get("announce-list");
            outer:
            for (Object tier : announceList) {
                for (Object trackerObj : (List<Object>) tier) {
                    String candidate = new String((byte[]) trackerObj, StandardCharsets.UTF_8).trim();
                    if (candidate.startsWith("http")) {
                        announceUrl = candidate;
                        break outer;
                    }
                }
            }
        }

        if (!announceUrl.startsWith("http"))
            throw new IllegalArgumentException(
                "No HTTP/HTTPS tracker found. Primary tracker was: " + announceUrl +
                ". UDP trackers are not supported."
            );

        Map<String, Object> info = (Map<String, Object>) infoObject;

        // Extract fields common to both single-file and multi-file torrents
        Object nameObject        = info.get("name");
        Object pieceLengthObject = info.get("piece length");
        Object piecesObject      = info.get("pieces");

        if (!(nameObject instanceof byte[]) ||
            !(pieceLengthObject instanceof Long) ||
            !(piecesObject instanceof byte[]))
            throw new IllegalArgumentException("Invalid torrent metadata");

        String name       = new String((byte[]) nameObject, StandardCharsets.UTF_8);
        int pieceLength   = Math.toIntExact((Long) pieceLengthObject);
        byte[] pieces     = (byte[]) piecesObject;

        if (pieceLength <= 0 || pieces.length % 20 != 0)
            throw new IllegalArgumentException("Invalid torrent metadata");

        // ---- Detect mode and extract file list ----
        long totalLength;
        List<FileEntry> files;

        if (info.containsKey("files")) {
            // MULTI-FILE MODE
            List<Object> rawFiles = (List<Object>) info.get("files");
            files = new ArrayList<>();
            long accumulated = 0;

            for (Object rawFile : rawFiles) {
                Map<String, Object> fileMap = (Map<String, Object>) rawFile;
                long fileLen = (Long) fileMap.get("length");

                // Path is a Bencode list of byte[] components e.g. ["folder", "file.txt"]
                List<Object> pathComponents = (List<Object>) fileMap.get("path");
                StringBuilder pathBuilder = new StringBuilder(name);
                for (Object component : pathComponents) {
                    pathBuilder.append("/").append(new String((byte[]) component, StandardCharsets.UTF_8));
                }

                files.add(new FileEntry(pathBuilder.toString(), fileLen));
                accumulated += fileLen;
            }

            totalLength = accumulated;

        } else {
            // SINGLE-FILE MODE
            Object length = info.get("length");
            if (!(length instanceof Long))
                throw new IllegalArgumentException("Invalid torrent metadata");

            totalLength = (Long) length;
            files = List.of(new FileEntry(name, totalLength));
        }

        if (totalLength <= 0)
            throw new IllegalArgumentException("Invalid total file length");

        // ---- Extract piece hashes ----
        List<byte[]> pieceHashes = new ArrayList<>();
        for (int i = 0; i < pieces.length; i += 20) {
            byte[] hash = new byte[20];
            System.arraycopy(pieces, i, hash, 0, 20);
            pieceHashes.add(hash);
        }

        long expectedPieces = (totalLength + pieceLength - 1) / pieceLength;
        if (pieceHashes.size() != expectedPieces)
            throw new IllegalArgumentException("Invalid number of piece hashes");

        byte[] infoHash = calculateInfoHash(fileBytes);

        return new TorrentInfo(infoHash, pieceHashes, totalLength, pieceLength,
                announceUrl, name, files);
    }

    // ----------------------------------------------------------------
    // INFO HASH (unchanged)
    // ----------------------------------------------------------------

    private static byte[] calculateInfoHash(byte[] data) {
        int pos = 1;

        while (pos < data.length && data[pos] != 'e') {
            int keyStart = pos;
            int colon = findColon(data, pos);
            int keyLength = Integer.parseInt(new String(
                    data, keyStart, colon - keyStart, StandardCharsets.US_ASCII));

            pos = colon + 1;
            String key = new String(data, pos, keyLength, StandardCharsets.UTF_8);
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
            if (c == 'd' || c == 'l') { depth++; pos++; }
            else if (c == 'e') { if (--depth == 0) return pos + 1; pos++; }
            else if (c >= '0' && c <= '9') { pos = skipString(data, pos); }
            else if (c == 'i') { pos = skipInteger(data, pos); }
            else throw new IllegalArgumentException("Invalid Bencode");
        }
        throw new IllegalArgumentException("Invalid Bencode");
    }

    private static int skip(byte[] data, int pos) {
        byte c = data[pos];
        if (c >= '0' && c <= '9') return skipString(data, pos);
        if (c == 'i') return skipInteger(data, pos);
        if (c == 'd' || c == 'l') return findEnd(data, pos);
        throw new IllegalArgumentException("Invalid Bencode");
    }

    private static int skipString(byte[] data, int pos) {
        int colon = findColon(data, pos);
        int length = Integer.parseInt(new String(data, pos, colon - pos, StandardCharsets.US_ASCII));
        int end = colon + 1 + length;
        if (end > data.length) throw new IllegalArgumentException("Invalid byte string");
        return end;
    }

    private static int skipInteger(byte[] data, int pos) {
        pos++;
        while (pos < data.length && data[pos] != 'e') pos++;
        if (pos >= data.length) throw new IllegalArgumentException("Invalid integer");
        return pos + 1;
    }

    private static int findColon(byte[] data, int pos) {
        while (pos < data.length && data[pos] != ':') {
            if (data[pos] < '0' || data[pos] > '9')
                throw new IllegalArgumentException("Invalid string length");
            pos++;
        }
        if (pos >= data.length) throw new IllegalArgumentException("Invalid string");
        return pos;
    }

    // ----------------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------------

    public byte[]        getInfoHash()    { return infoHash; }
    public List<byte[]>  getPieceHashes() { return pieceHashes; }
    public long          getFileLength()  { return totalLength; }  // kept for backward compat
    public long          getTotalLength() { return totalLength; }
    public int           getPieceLength() { return pieceLength; }
    public String        getAnnounceUrl() { return announceUrl; }
    public String        getName()        { return name; }
    public List<FileEntry> getFiles()     { return files; }
    public boolean       isMultiFile()    { return files.size() > 1; }
}