package com.bittorrent;

import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class PeerConnection implements Runnable {

    private final TrackerClient.PeerAddress peerAddress;
    private final byte[] infoHash;
    private final byte[] peerId;
    
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    
    // State variables (Milestone 6)
    private boolean amChoking = true;
    private boolean amInterested = false;
    private boolean peerChoking = true;
    private boolean peerInterested = false;

    public PeerConnection(TrackerClient.PeerAddress peerAddress, byte[] infoHash, byte[] peerId) {
        this.peerAddress = peerAddress;
        this.infoHash = infoHash;
        this.peerId = peerId;
    }

    @Override
    public void run() {
        // TODO: Milestone 4 - Open TCP connection, send Handshake, verify incoming Handshake
        // TODO: Milestone 5 - Enter framing loop: read 4-byte length, read payload, decode PeerMessage
        // TODO: Milestone 6 & 8 - Handle message routing and timeouts
    }

    public void sendMessage(PeerMessage message) {
        // TODO: Encode and write length-prefixed message to DataOutputStream
    }
}
