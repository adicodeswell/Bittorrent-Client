package com.bittorrent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TorrentInfoTest {

    private byte[] createSingleFileTorrent() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "test_file.txt".getBytes(StandardCharsets.UTF_8));
        info.put("piece length", 256L);
        info.put("length", 1024L);
        byte[] dummyPieces = new byte[80]; // 4 pieces × 20 bytes
        for (int i = 0; i < dummyPieces.length; i++) dummyPieces[i] = (byte) (i % 256);
        info.put("pieces", dummyPieces);

        Map<String, Object> torrent = new LinkedHashMap<>();
        torrent.put("announce", "http://tracker.example.com/announce".getBytes(StandardCharsets.UTF_8));
        torrent.put("info", info);
        return Bencoder.encode(torrent);
    }

    private byte[] createMultiFileTorrent() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "my_album".getBytes(StandardCharsets.UTF_8));
        info.put("piece length", 256L);

        // Two files totalling 1024 bytes → 4 pieces
        byte[] dummyPieces = new byte[80];
        for (int i = 0; i < dummyPieces.length; i++) dummyPieces[i] = (byte) (i % 256);
        info.put("pieces", dummyPieces);

        info.put("files", java.util.List.of(
            new LinkedHashMap<String, Object>() {{
                put("length", 512L);
                put("path", java.util.List.of("track1.mp3".getBytes(StandardCharsets.UTF_8)));
            }},
            new LinkedHashMap<String, Object>() {{
                put("length", 512L);
                put("path", java.util.List.of("track2.mp3".getBytes(StandardCharsets.UTF_8)));
            }}
        ));

        Map<String, Object> torrent = new LinkedHashMap<>();
        torrent.put("announce", "http://tracker.example.com/announce".getBytes(StandardCharsets.UTF_8));
        torrent.put("info", info);
        return Bencoder.encode(torrent);
    }

    private byte[] calculateExpectedInfoHash(byte[] fullTorrentBytes) throws NoSuchAlgorithmException {
        Map<?, ?> decoded = (Map<?, ?>) Bencoder.decode(fullTorrentBytes);
        byte[] infoBytes = Bencoder.encode(decoded.get("info"));
        return MessageDigest.getInstance("SHA-1").digest(infoBytes);
    }

    @Test
    public void testParseSingleFileTorrent() throws NoSuchAlgorithmException {
        byte[] torrentBytes = createSingleFileTorrent();
        TorrentInfo torrentInfo = TorrentInfo.parse(torrentBytes);

        assertNotNull(torrentInfo);
        assertEquals("http://tracker.example.com/announce", torrentInfo.getAnnounceUrl());
        assertEquals(1024L, torrentInfo.getFileLength());
        assertEquals(256, torrentInfo.getPieceLength());
        assertFalse(torrentInfo.isMultiFile());
        assertEquals(1, torrentInfo.getFiles().size());
        assertEquals("test_file.txt", torrentInfo.getFiles().get(0).path());

        assertNotNull(torrentInfo.getPieceHashes());
        assertEquals(4, torrentInfo.getPieceHashes().size());
        assertEquals(20, torrentInfo.getPieceHashes().get(0).length);

        byte[] expectedHash = calculateExpectedInfoHash(torrentBytes);
        assertArrayEquals(expectedHash, torrentInfo.getInfoHash(),
                "info_hash must match SHA-1 of the info dictionary");
    }

    @Test
    public void testParseMultiFileTorrent() throws NoSuchAlgorithmException {
        byte[] torrentBytes = createMultiFileTorrent();
        TorrentInfo torrentInfo = TorrentInfo.parse(torrentBytes);

        assertNotNull(torrentInfo);
        assertTrue(torrentInfo.isMultiFile(), "Should be detected as multi-file");
        assertEquals(2, torrentInfo.getFiles().size());
        assertEquals(1024L, torrentInfo.getTotalLength(), "Total length must be sum of all files");

        // Verify file paths include the top-level directory name
        assertTrue(torrentInfo.getFiles().get(0).path().contains("track1.mp3"));
        assertTrue(torrentInfo.getFiles().get(1).path().contains("track2.mp3"));
        assertEquals(512L, torrentInfo.getFiles().get(0).length());
        assertEquals(512L, torrentInfo.getFiles().get(1).length());
    }

    @Test
    public void testMissingInfoDictionary() {
        Map<String, Object> torrent = new LinkedHashMap<>();
        torrent.put("announce", "http://tracker.example.com/announce".getBytes(StandardCharsets.UTF_8));
        byte[] torrentBytes = Bencoder.encode(torrent);

        assertThrows(IllegalArgumentException.class, () -> TorrentInfo.parse(torrentBytes),
                "Should throw IllegalArgumentException if 'info' dictionary is missing");
    }

    @Test
    public void testUdpPrimaryFallsBackToHttpAnnounceList() {
        // Build a torrent where primary announce is UDP but announce-list has an HTTP tracker
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "test.txt".getBytes(StandardCharsets.UTF_8));
        info.put("piece length", 256L);
        info.put("length", 1024L);
        byte[] pieces = new byte[80];
        info.put("pieces", pieces);

        Map<String, Object> torrent = new LinkedHashMap<>();
        torrent.put("announce", "udp://tracker.example.com:6969".getBytes(StandardCharsets.UTF_8));
        torrent.put("announce-list", java.util.List.of(
            java.util.List.of("udp://tracker.example.com:6969".getBytes(StandardCharsets.UTF_8)),
            java.util.List.of("http://backup-tracker.example.com/announce".getBytes(StandardCharsets.UTF_8))
        ));
        torrent.put("info", info);

        byte[] torrentBytes = Bencoder.encode(torrent);
        TorrentInfo result = TorrentInfo.parse(torrentBytes);

        assertEquals("http://backup-tracker.example.com/announce", result.getAnnounceUrl(),
                "Should fall back to HTTP tracker from announce-list when primary is UDP");
    }

    @Test
    public void testUdpOnlyTorrentThrows() {
        // Build a torrent with only UDP trackers — should throw
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "test.txt".getBytes(StandardCharsets.UTF_8));
        info.put("piece length", 256L);
        info.put("length", 1024L);
        byte[] pieces = new byte[80];
        info.put("pieces", pieces);

        Map<String, Object> torrent = new LinkedHashMap<>();
        torrent.put("announce", "udp://tracker.example.com:6969".getBytes(StandardCharsets.UTF_8));
        torrent.put("info", info);

        byte[] torrentBytes = Bencoder.encode(torrent);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> TorrentInfo.parse(torrentBytes));
        assertTrue(ex.getMessage().contains("UDP trackers are not supported"));
    }
}
