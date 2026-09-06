package com.bittorrent;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class PeerConnectionTest {

    // Helper to create a valid handshake byte array
    private byte[] generateMockHandshake(byte[] infoHash, byte[] peerId) {
        byte[] handshake = new byte[68];
        handshake[0] = 19;
        System.arraycopy("BitTorrent protocol".getBytes(StandardCharsets.US_ASCII), 0, handshake, 1, 19);
        // 8 reserved bytes are already 0
        System.arraycopy(infoHash, 0, handshake, 28, 20);
        System.arraycopy(peerId, 0, handshake, 48, 20);
        return handshake;
    }

    @Test
    public void testSuccessfulHandshake() throws Exception {
        byte[] expectedInfoHash = new byte[20];
        expectedInfoHash[0] = 1; // Dummy hash

        byte[] clientPeerId = "CLIENT_ID_1234567890".getBytes(StandardCharsets.US_ASCII);
        byte[] serverPeerId = "SERVER_ID_0987654321".getBytes(StandardCharsets.US_ASCII);

        // Spin up a local dummy server to act as a Peer
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            
            // Run server logic in a background thread
            Thread serverThread = new Thread(() -> {
                try (Socket clientSocket = serverSocket.accept();
                     DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                     DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
                    
                    // 1. Read handshake from our client
                    byte[] receivedHandshake = new byte[68];
                    in.readFully(receivedHandshake);
                    
                    assertEquals(19, receivedHandshake[0]);
                    
                    // 2. Send back a valid handshake (matching info hash)
                    out.write(generateMockHandshake(expectedInfoHash, serverPeerId));
                    out.flush();

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            serverThread.start();

            // Run our actual client logic
            TrackerClient.PeerAddress address = new TrackerClient.PeerAddress("127.0.0.1", port);
            PeerConnection connection = new PeerConnection(address, expectedInfoHash, clientPeerId);
            
            // For testing, we just want to ensure run() doesn't throw an exception 
            // when it receives a valid handshake.
            assertDoesNotThrow(() -> {
                // In a real scenario, run() will loop infinitely reading messages.
                // For this test, you might need to structure your run() to be testable, 
                // or have a separate performHandshake() method.
                // Assuming run() connects, handshakes, and then blocks reading.
                // We'll interrupt the thread after 1 second if it blocks successfully.
                
                Thread clientThread = new Thread(connection);
                clientThread.start();
                Thread.sleep(1000);
                assertTrue(clientThread.isAlive(), "Client should be blocking on read after successful handshake");
                clientThread.interrupt();
            });
        }
    }
}
