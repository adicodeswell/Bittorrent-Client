package com.bittorrent;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.nio.ByteBuffer;

public class PeerConnection implements Runnable {

    private final TrackerClient.PeerAddress peerAddress;
    private final byte[] peerId;
    private final TorrentInfo torrentInfo;
    private final FileManager fileManager;
    private final PieceManager pieceManager;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    // Tracks which pieces this specific peer has
    private final BitSet peerPieces = new BitSet();

    // State variables
    private boolean amChoking     = true;
    private boolean amInterested  = false;
    private boolean peerChoking   = true;
    private boolean peerInterested = false;

    // Block request tracking
    private int currentPieceIndex = -1;
    private int currentBlockOffset = 0;
    private static final int BLOCK_SIZE = 16384; // 16 KB

    public PeerConnection(TrackerClient.PeerAddress peerAddress, byte[] peerId,
                          TorrentInfo torrentInfo, FileManager fileManager, PieceManager pieceManager) {
        this.peerAddress = peerAddress;
        this.peerId = peerId;
        this.torrentInfo = torrentInfo;
        this.fileManager = fileManager;
        this.pieceManager = pieceManager;
    }

    // ----------------------------------------------------------------
    // CONNECTION
    // ----------------------------------------------------------------

    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(peerAddress.ip(), peerAddress.port()), 5_000);
        socket.setSoTimeout(30_000);

        in  = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    // ----------------------------------------------------------------
    // HANDSHAKE  (Milestone 4)
    // ----------------------------------------------------------------

    private byte[] buildHandshake() {
        byte[] handshake = new byte[68];
        int offset = 0;

        handshake[offset++] = 19;

        byte[] protocol = "BitTorrent protocol".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(protocol, 0, handshake, offset, protocol.length);
        offset += protocol.length;

        offset += 8; // 8 reserved zero bytes

        System.arraycopy(torrentInfo.getInfoHash(), 0, handshake, offset, 20);
        offset += 20;

        System.arraycopy(peerId, 0, handshake, offset, 20);

        return handshake;
    }

    private boolean performHandshake() throws IOException {
        out.write(buildHandshake());
        out.flush();

        byte[] theirHandshake = new byte[68];
        in.readFully(theirHandshake);

        if (theirHandshake[0] != 19) return false;

        byte[] theirInfoHash = new byte[20];
        System.arraycopy(theirHandshake, 28, theirInfoHash, 0, 20);

        return Arrays.equals(theirInfoHash, torrentInfo.getInfoHash());
    }

    // ----------------------------------------------------------------
    // MESSAGE READING LOOP  (Milestone 5)
    // ----------------------------------------------------------------

    private void readLoop() throws IOException {
        while (!Thread.currentThread().isInterrupted()) {

            int length = in.readInt();

            if (length < 0 || length > 32 * 1024) {
                System.err.println("Invalid message length " + length + " from "
                        + peerAddress.ip() + " — disconnecting");
                return;
            }

            if (length == 0) continue; // Keep-alive

            int messageId = in.readUnsignedByte();

            byte[] payload = new byte[length - 1];
            if (payload.length > 0) {
                in.readFully(payload);
            }

            PeerMessage message = PeerMessage.decode(messageId, payload);
            handleMessage(message);
        }
    }

    // ----------------------------------------------------------------
    // MESSAGE DISPATCHER
    // ----------------------------------------------------------------

    private void handleMessage(PeerMessage message) throws IOException {
        switch (message.type()) {
            case CHOKE          -> peerChoking    = true;
            case UNCHOKE        -> {
                peerChoking = false;
                System.out.println("Unchoked by " + peerAddress.ip() + " - Requesting data!");
                requestNextBlock();
            }
            case INTERESTED     -> peerInterested = true;
            case NOT_INTERESTED -> peerInterested = false;
            case HAVE           -> handleHave(message.payload());
            case BITFIELD       -> handleBitfield(message.payload());
            case PIECE          -> handlePiece(message.payload());
            case REQUEST        -> { /* Seeding — future */ }
            case CANCEL         -> { /* Future */ }
            default             -> { /* Ignore EXTENDED, PORT, UNKNOWN, etc. */ }
        }
    }

    // ----------------------------------------------------------------
    // BITFIELD HANDLER
    // ----------------------------------------------------------------

    private void handleBitfield(byte[] payload) throws IOException {
        for (int byteIndex = 0; byteIndex < payload.length; byteIndex++) {
            for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
                if ((payload[byteIndex] & (0x80 >> bitIndex)) != 0) {
                    peerPieces.set(byteIndex * 8 + bitIndex);
                }
            }
        }
        System.out.println("peer has " + peerPieces.cardinality() + " pieces");

        // Express interest if the peer has any pieces we want (for now, any piece)
        if (!amInterested && peerPieces.cardinality() > 0) {
            System.out.println("Sending INTERESTED to " + peerAddress.ip());
            sendMessage(new PeerMessage(PeerMessage.MessageType.INTERESTED, null));
            amInterested = true;
        }
    }

    // ----------------------------------------------------------------
    // HAVE HANDLER
    // ----------------------------------------------------------------

    private void handleHave(byte[] payload) throws IOException {
        if (payload.length != 4) {
            throw new IllegalArgumentException("HAVE payload must be exactly 4 bytes");
        }

        int pieceIndex = ((payload[0] & 0xFF) << 24)
                       | ((payload[1] & 0xFF) << 16)
                       | ((payload[2] & 0xFF) << 8)
                       |  (payload[3] & 0xFF);

        peerPieces.set(pieceIndex);

        if (!amInterested) {
            System.out.println("Sending INTERESTED to " + peerAddress.ip());
            sendMessage(new PeerMessage(PeerMessage.MessageType.INTERESTED, null));
            amInterested = true;
        }
    }

    // ----------------------------------------------------------------
    // SENDING MESSAGES (Milestone 6)
    // ----------------------------------------------------------------

    public void sendMessage(PeerMessage message) throws IOException {
        int payloadLength = (message.payload() == null) ? 0 : message.payload().length;
        out.writeInt(1 + payloadLength);
        out.writeByte(message.type().getId());

        if (payloadLength > 0) {
            out.write(message.payload());
        }
        out.flush();
    }

    // ----------------------------------------------------------------
    // REQUESTING AND RECEIVING BLOCKS (Milestone 6)
    // ----------------------------------------------------------------

    private void requestNextBlock() throws IOException {
        if (peerChoking) return;

        // if we do not have an assignment, ask the dispatcher for one
        if (currentPieceIndex == -1) {
            currentPieceIndex = pieceManager.getNextPiece();
            currentBlockOffset = 0;
        }

        // if dispatcher returned -1, we are done downloading the piece
        if (currentPieceIndex == -1) {
            System.out.println("No more pieces to download. We are finished with this peer!");
            return;
        }
        
        long totalPieces = (torrentInfo.getTotalLength() + torrentInfo.getPieceLength() - 1) / torrentInfo.getPieceLength();
        if (currentPieceIndex >= totalPieces) {
            System.out.println("Download complete from this peer!");
            return;
        }

        int pieceSize = torrentInfo.getPieceLength();
        if (currentPieceIndex == totalPieces - 1) {
            long remainder = torrentInfo.getTotalLength() % torrentInfo.getPieceLength();
            if (remainder != 0) {
                pieceSize = (int) remainder;
            }
        }

        int length = Math.min(BLOCK_SIZE, pieceSize - currentBlockOffset);

        byte[] payload = new byte[12];
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.putInt(currentPieceIndex);
        buffer.putInt(currentBlockOffset);
        buffer.putInt(length);

        sendMessage(new PeerMessage(PeerMessage.MessageType.REQUEST, payload));
    }

    private void handlePiece(byte[] payload) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int index = buffer.getInt();
        int begin = buffer.getInt();

        byte[] blockData = new byte[payload.length - 8];
        buffer.get(blockData);

        // Save block data to the file manager
        fileManager.writePiece(index, begin, blockData);

        currentBlockOffset += blockData.length;

        // Calculate piece size for the current piece (last piece might be smaller)
        long totalPieces = (torrentInfo.getTotalLength() + torrentInfo.getPieceLength() - 1) / torrentInfo.getPieceLength();
        int pieceSize = torrentInfo.getPieceLength();
        if (index == totalPieces - 1) {
            long remainder = torrentInfo.getTotalLength() % torrentInfo.getPieceLength();
            if (remainder != 0) {
                pieceSize = (int) remainder;
            }
        }

        // Check if the piece is fully downloaded
        if (currentBlockOffset >= pieceSize) {
            System.out.println("Finished downloading piece " + index + " - Verifying hash...");

            // 1. Read the completed piece back from disk
            byte[] downloadedBytes = fileManager.readPiece(index);

            // 2. Calculate its SHA-1 hash
            byte[] calculatedHash;
            try {
                calculatedHash = java.security.MessageDigest.getInstance("SHA-1").digest(downloadedBytes);
            } catch (Exception e) {
                throw new RuntimeException("SHA-1 not supported", e);
            }

            // 3. Get the expected hash from the TorrentInfo
            byte[] expectedHash = torrentInfo.getPieceHashes().get(index);

            // 4. Compare them
            if (java.util.Arrays.equals(calculatedHash, expectedHash)) {
                System.out.println(" Piece " + index + " verified successfully!");
                
                // Tell the central manager this piece is done!
                pieceManager.markCompleted(currentPieceIndex);
                
                // Reset our assignment so requestNextBlock() will ask for a new one
                currentPieceIndex = -1;
                currentBlockOffset = 0;
            } else {
                System.err.println("Piece " + index + " FAILED hash check! Dropping peer.");

                // If they send bad data, we cut them off to protect our download
                throw new IOException("Received corrupt piece from peer");
            }
        }

        // Ask for next block immediately
        requestNextBlock();
    }

    // ----------------------------------------------------------------
    // RUN
    // ----------------------------------------------------------------

    @Override
    public void run() {
        try {
            connect();

            if (!performHandshake()) {
                close();
                return;
            }

            readLoop();

        } catch (IOException e) {
            // Ignore normal disconnects in console output if preferred, but keeping it for debugging
        } finally {
            close();
        }
    }

    private void close() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        
        // Put assigned piece back to the pool for another peer to grab
        if (currentPieceIndex != -1) {
            pieceManager.markMissing(currentPieceIndex);
        }
    }

    public BitSet getPeerPieces() { return peerPieces; }
}
