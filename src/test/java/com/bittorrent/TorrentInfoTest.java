package com.bittorrent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TorrentInfoTest {

    private byte[] createDummyTorrent(boolean isMultiFile) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", "test_file.txt".getBytes(StandardCharsets.UTF_8));
        info.put("piece length", 256L);
        
        // 4 pieces = 80 bytes of SHA-1 hashes
        byte[] dummyPieces = new byte[80];
        for (int i = 0; i < dummyPieces.length; i++) {
            dummyPieces[i] = (byte) (i % 256);
        }
        info.put("pieces", dummyPieces);

        if (isMultiFile) {
            // Mocking a multi-file structure
            info.put("files", java.util.List.of(
                new LinkedHashMap<String, Object>() {{
                    put("length", 512L);
                    put("path", java.util.List.of("folder".getBytes(), "file1.txt".getBytes()));
                }}
            ));
        } else {
            info.put("length", 1024L);
        }

        Map<String, Object> torrent = new LinkedHashMap<>();
        torrent.put("announce", "http://tracker.example.com/announce".getBytes(StandardCharsets.UTF_8));
        torrent.put("info", info);

        return Bencoder.encode(torrent);
    }

    private byte[] calculateExpectedInfoHash(byte[] fullTorrentBytes) throws NoSuchAlgorithmException {
        // Since Bencoder parses into a map, for our dummy torrent, re-encoding the info map gives the exact bytes.
        // NOTE: In the wild, you must slice the original byte array instead of re-encoding.
        Map<?, ?> decoded = (Map<?, ?>) Bencoder.decode(fullTorrentBytes);
        byte[] infoBytes = Bencoder.encode(decoded.get("info"));
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return digest.digest(infoBytes);
    }

    @Test
    public void testParseSingleFileTorrent() throws NoSuchAlgorithmException {
        byte[] torrentBytes = createDummyTorrent(false);
        TorrentInfo torrentInfo = TorrentInfo.parse(torrentBytes);

        assertNotNull(torrentInfo);
        assertEquals("http://tracker.example.com/announce", torrentInfo.getAnnounceUrl());
        assertEquals(1024L, torrentInfo.getFileLength());
        assertEquals(256, torrentInfo.getPieceLength());
        
        // Assert we got 4 piece hashes correctly
        assertNotNull(torrentInfo.getPieceHashes());
        assertEquals(4, torrentInfo.getPieceHashes().size());
        assertEquals(20, torrentInfo.getPieceHashes().get(0).length);

        // Assert info_hash correctness
        byte[] expectedHash = calculateExpectedInfoHash(torrentBytes);
        assertArrayEquals(expectedHash, torrentInfo.getInfoHash(), "The info_hash must match the SHA-1 of the info dictionary");
    }

    @Test
    public void testRejectMultiFileTorrent() {
        byte[] torrentBytes = createDummyTorrent(true);
        
        Exception exception = assertThrows(UnsupportedOperationException.class, () -> {
            TorrentInfo.parse(torrentBytes);
        });

        assertTrue(exception.getMessage().toLowerCase().contains("multi-file"), 
                "Exception message should mention that multi-file torrents are unsupported");
    }

    @Test
    public void testMissingInfoDictionary() {
        Map<String, Object> torrent = new LinkedHashMap<>();
        torrent.put("announce", "http://tracker.example.com/announce".getBytes(StandardCharsets.UTF_8));
        byte[] torrentBytes = Bencoder.encode(torrent);

        assertThrows(IllegalArgumentException.class, () -> {
            TorrentInfo.parse(torrentBytes);
        }, "Should throw IllegalArgumentException if 'info' dictionary is missing");
    }
}
