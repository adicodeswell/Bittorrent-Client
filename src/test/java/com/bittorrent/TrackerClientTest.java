package com.bittorrent;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class TrackerClientTest {

    // A dummy TrackerClient subclass that overrides the actual HTTP call 
    // to return mock Bencoded responses, allowing us to test the parsing logic.
    static class MockTrackerClient extends TrackerClient {
        private final byte[] mockResponse;

        public MockTrackerClient(byte[] mockResponse) {
            super();
            this.mockResponse = mockResponse;
        }

        // We assume the user creates a protected/package-private method to parse the response,
        // OR we can just test their main method if we provide a mock HTTP client. 
        // For simplicity, let's create a helper method in TrackerClient they should implement:
        // public List<PeerAddress> parseTrackerResponse(byte[] responseBytes)
        
        // Wait, the interface requested in the implementation guide was just getPeers().
        // To avoid forcing them to refactor their architecture for testing, 
        // I will write a test that directly invokes their `parseTrackerResponse` 
        // assuming they will create it. 
    }

    @Test
    public void testParseCompactPeersSuccess() {
        // Mocking a successful tracker response with 2 peers
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("interval", 900L);
        
        // Peer 1: 192.168.1.1:6881
        // Peer 2: 10.0.0.5:8080
        byte[] compactPeers = new byte[] {
            (byte) 192, (byte) 168, 1, 1, (byte) (6881 >> 8), (byte) (6881 & 0xFF),
            10, 0, 0, 5, (byte) (8080 >> 8), (byte) (8080 & 0xFF)
        };
        response.put("peers", compactPeers);
        
        byte[] bencodedResponse = Bencoder.encode(response);
        
        // We instantiate TrackerClient. 
        // To test just the parsing logic without making a real HTTP request, 
        // we'll assume the student creates a public method: `parseTrackerResponse(byte[])`
        TrackerClient client = new TrackerClient();
        
        List<TrackerClient.PeerAddress> peers = client.parseTrackerResponse(bencodedResponse);
        
        assertNotNull(peers);
        assertEquals(2, peers.size());
        
        assertEquals("192.168.1.1", peers.get(0).ip());
        assertEquals(6881, peers.get(0).port());
        
        assertEquals("10.0.0.5", peers.get(1).ip());
        assertEquals(8080, peers.get(1).port());
    }

    @Test
    public void testTrackerFailureResponse() {
        // Mocking a tracker failure response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("failure reason", "Unregistered torrent".getBytes(StandardCharsets.UTF_8));
        
        byte[] bencodedResponse = Bencoder.encode(response);
        
        TrackerClient client = new TrackerClient();
        
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            client.parseTrackerResponse(bencodedResponse);
        });
        
        assertTrue(exception.getMessage().contains("Unregistered torrent"), 
            "The exception message should contain the tracker's failure reason");
    }
}
