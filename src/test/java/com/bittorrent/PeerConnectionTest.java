package com.bittorrent;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

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
        expectedInfoHash[0] = 1; // Dummy non-zero hash

        byte[] clientPeerId = "CLIENT_ID_1234567890".getBytes(StandardCharsets.US_ASCII);
        byte[] serverPeerId = "SERVER_ID_0987654321".getBytes(StandardCharsets.US_ASCII);

        // Tracks whether the server received a valid handshake from our client
        AtomicBoolean serverReceivedValidHandshake = new AtomicBoolean(false);

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            // Run server logic in a background thread
            Thread serverThread = new Thread(() -> {
                try (Socket clientSocket = serverSocket.accept();
                     DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                     DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                    // Read the 68-byte handshake from our client
                    byte[] received = new byte[68];
                    in.readFully(received);

                    // Validate what our client sent
                    boolean validProtocol   = received[0] == 19;
                    boolean validProtocName = new String(received, 1, 19, StandardCharsets.US_ASCII)
                                                .equals("BitTorrent protocol");
                    byte[] receivedInfoHash = Arrays.copyOfRange(received, 28, 48);
                    boolean validInfoHash   = Arrays.equals(receivedInfoHash, expectedInfoHash);

                    serverReceivedValidHandshake.set(validProtocol && validProtocName && validInfoHash);

                    // Send back a valid matching handshake
                    out.write(generateMockHandshake(expectedInfoHash, serverPeerId));
                    out.flush();

                } catch (Exception e) {
                    // Server closed — expected after client finishes
                }
            });
            serverThread.start();

            // Start our actual client connection
            TrackerClient.PeerAddress address = new TrackerClient.PeerAddress("127.0.0.1", port);
            
            // Dummy TorrentInfo for testing
            TorrentInfo dummyTorrent = new TorrentInfo(expectedInfoHash, java.util.Collections.emptyList(), 0, 0, "", "", java.util.Collections.emptyList());
            PeerConnection connection = new PeerConnection(address, clientPeerId, dummyTorrent, null, null);

            Thread clientThread = Thread.ofVirtual().start(connection);

            // Wait for both sides to finish (max 5 seconds)
            serverThread.join(5000);
            clientThread.join(5000);

            // Assert that the server confirmed our client sent a correct handshake
            assertTrue(serverReceivedValidHandshake.get(),
                    "Client must send a valid 68-byte handshake with correct protocol name and infoHash");
        }
    }

    @Test
    public void testHandshakeInfoHashMismatch() throws Exception {
        byte[] ourInfoHash   = new byte[20];
        ourInfoHash[0] = 1;

        byte[] wrongInfoHash = new byte[20];
        wrongInfoHash[0] = 99; // Different — simulates a wrong torrent

        byte[] clientPeerId = "CLIENT_ID_1234567890".getBytes(StandardCharsets.US_ASCII);
        byte[] serverPeerId = "SERVER_ID_0987654321".getBytes(StandardCharsets.US_ASCII);

        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            // Server replies with a MISMATCHED infoHash
            Thread serverThread = new Thread(() -> {
                try (Socket clientSocket = serverSocket.accept();
                     DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                     DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                    byte[] received = new byte[68];
                    in.readFully(received);

                    // Respond with wrong infoHash to simulate a bad peer
                    out.write(generateMockHandshake(wrongInfoHash, serverPeerId));
                    out.flush();

                } catch (Exception ignored) {}
            });
            serverThread.start();

            TrackerClient.PeerAddress address = new TrackerClient.PeerAddress("127.0.0.1", port);
            TorrentInfo dummyTorrent = new TorrentInfo(ourInfoHash, java.util.Collections.emptyList(), 0, 0, "", "", java.util.Collections.emptyList());
            PeerConnection connection = new PeerConnection(address, clientPeerId, dummyTorrent, null, null);

            Thread clientThread = Thread.ofVirtual().start(connection);

            // Client should detect the mismatch and disconnect quickly
            clientThread.join(5000);
            serverThread.join(5000);

            // If we reach here without hanging, the client correctly rejected the peer
            assertFalse(clientThread.isAlive(),
                    "Client must disconnect cleanly after detecting infoHash mismatch");
        }
    }
}
