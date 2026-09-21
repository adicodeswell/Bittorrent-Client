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

    // seeding related
    private int downloadedBytesThisPeriod = 0;

    // Tracks which pieces this specific peer has
    private final BitSet peerPieces = new BitSet();

    // State variables
    private boolean amChoking = true; // We choke them by default!
    private boolean amInterested  = false;
    private boolean peerChoking   = true;
    private boolean peerInterested = false;

    // Block request tracking
    private int currentPieceIndex = -1;
    private int requestedBlockOffset = 0;
    private int receivedBlockOffset = 0;
    private int pendingRequests = 0;
    private final int MAX_PIPELINE = 20;
    private static final int BLOCK_SIZE = 16384; // 16 KB

    public PeerConnection(TrackerClient.PeerAddress peerAddress, byte[] peerId,
                          TorrentInfo torrentInfo, FileManager fileManager, PieceManager pieceManager) {
        this.peerAddress = peerAddress;
        this.peerId = peerId;
        this.torrentInfo = torrentInfo;
        this.fileManager = fileManager;
        this.pieceManager = pieceManager;
    }

    // CONNECTION
    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(peerAddress.ip(), peerAddress.port()), 5_000);
        socket.setSoTimeout(30_000);

        in  = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    // HANDSHAKE  (Milestone 4)
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

    // MESSAGE READING LOOP  (Milestone 5)
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

    // MESSAGE DISPATCHER
    private void handleMessage(PeerMessage message) throws IOException {
        switch (message.type()) {
            case CHOKE          -> peerChoking = true;
            case UNCHOKE        -> {
                peerChoking = false;
                fillPipeline();
            }
            case INTERESTED     -> peerInterested = true;
            case NOT_INTERESTED -> peerInterested = false;
            case HAVE           -> handleHave(message.payload());
            case BITFIELD       -> handleBitfield(message.payload());
            case PIECE          -> handlePiece(message.payload());
            case REQUEST        -> handleRequest(message.payload());
            case CANCEL         -> { /* Future */ }
            default             -> { /* Ignore EXTENDED, PORT, UNKNOWN, etc. */ }
        }
    }

    // Getter and Choke controllers
    public boolean isClosed() { return socket == null || socket.isClosed(); }
    public boolean isPeerInterested() { return peerInterested; }

    public int getAndResetDownloadedBytes() {
        int bytes = downloadedBytesThisPeriod;
        downloadedBytesThisPeriod = 0;
        return bytes;
    }

    public void unchoke() throws IOException {
        if (amChoking) {
            amChoking = false;
            sendMessage(new PeerMessage(PeerMessage.MessageType.UNCHOKE, null));
        }
    }

    public void choke() throws IOException {
        if (!amChoking) {
            amChoking = true;
            sendMessage(new PeerMessage(PeerMessage.MessageType.CHOKE, null));
        }
    }

    private void handleRequest(byte[] payload) throws IOException {
        if(amChoking) return; // if we are choking, ignore theie request
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int index = buffer.getInt();
        int begin = buffer.getInt();
        int length = buffer.getInt();

        // Security check: Don't let malicious peers crash us by asking for 100MB blocks
        if (length > 131072) return;

        // Make sure we actually have the piece they are asking for!
        if (pieceManager != null && !pieceManager.getCompletedPieces().get(index)) return;

        // Fetch the 16KB block from our hard drive
        byte[] blockData = fileManager.readBlock(index, begin, length);

        // Pack it into a PIECE message (8 byte header + data)
        ByteBuffer responseBuffer = ByteBuffer.allocate(8 + blockData.length);
        responseBuffer.putInt(index);
        responseBuffer.putInt(begin);
        responseBuffer.put(blockData);

        System.out.println("Uploading block " + begin + " of piece " + index + " to " + peerAddress.ip());
        sendMessage(new PeerMessage(PeerMessage.MessageType.PIECE, responseBuffer.array()));
    }

    // BITFIELD HANDLER
    private void handleBitfield(byte[] payload) throws IOException {
        for (int byteIndex = 0; byteIndex < payload.length; byteIndex++) {
            for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
                if ((payload[byteIndex] & (0x80 >> bitIndex)) != 0) {
                    int pieceIndex = byteIndex * 8 + bitIndex;
                    peerPieces.set(pieceIndex);

                    // tell the dispatcher this piece exists on the network
                    if (pieceManager != null) {
                        pieceManager.recordPieceAvailability(pieceIndex);
                    }
                }
            }
        }

        // Express interest if we are still downloading and they have pieces
        if (!amInterested && pieceManager != null && !pieceManager.isFinished() && peerPieces.cardinality() > 0) {
            sendMessage(new PeerMessage(PeerMessage.MessageType.INTERESTED, null));
            amInterested = true;
        }
    }


    // HAVE HANDLER
    private void handleHave(byte[] payload) throws IOException {
        if (payload.length != 4) {
            throw new IllegalArgumentException("HAVE payload must be exactly 4 bytes");
        }

        int pieceIndex = ((payload[0] & 0xFF) << 24)
                       | ((payload[1] & 0xFF) << 16)
                       | ((payload[2] & 0xFF) << 8)
                       |  (payload[3] & 0xFF);

        peerPieces.set(pieceIndex);

        // Tell the dispatcher a peer just acquired this piece
        if (pieceManager != null) {
            pieceManager.recordPieceAvailability(pieceIndex);
        }

        if (!amInterested && pieceManager != null && !pieceManager.isFinished()) {
            sendMessage(new PeerMessage(PeerMessage.MessageType.INTERESTED, null));
            amInterested = true;
        }
    }


    // SENDING MESSAGES (Milestone 6)
    public void sendMessage(PeerMessage message) throws IOException {
        int payloadLength = (message.payload() == null) ? 0 : message.payload().length;
        out.writeInt(1 + payloadLength);
        out.writeByte(message.type().getId());

        if (payloadLength > 0) {
            out.write(message.payload());
        }
        out.flush();
    }


    // REQUESTING AND RECEIVING BLOCKS (Milestone 6)
    private void fillPipeline() throws IOException {
        if (peerChoking) return;

        // if we do not have an assignment, ask the dispatcher for one
        if (currentPieceIndex == -1) {
            if (pieceManager != null) {
                currentPieceIndex = pieceManager.getNextPiece(peerPieces);
            }
            requestedBlockOffset = 0;
            receivedBlockOffset = 0;
            pendingRequests = 0;
        }

        // if dispatcher returned -1, we are done downloading the piece
        if (currentPieceIndex == -1) {
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

        while (pendingRequests < MAX_PIPELINE && requestedBlockOffset < pieceSize) {
            int length = Math.min(BLOCK_SIZE, pieceSize - requestedBlockOffset);

            byte[] payload = new byte[12];
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            buffer.putInt(currentPieceIndex);
            buffer.putInt(requestedBlockOffset);
            buffer.putInt(length);

            sendMessage(new PeerMessage(PeerMessage.MessageType.REQUEST, payload));
            
            requestedBlockOffset += length;
            pendingRequests++;
        }
    }

    private void handlePiece(byte[] payload) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int index = buffer.getInt();
        int begin = buffer.getInt();

        byte[] blockData = new byte[payload.length - 8];
        buffer.get(blockData);

        // Save block data to the file manager
        fileManager.writePiece(index, begin, blockData);

        receivedBlockOffset += blockData.length;
        pendingRequests--;
        // checking download speed
        downloadedBytesThisPeriod += blockData.length;
        if (pieceManager != null) {
            pieceManager.recordBytesDownloaded(blockData.length);
        }


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
        if (receivedBlockOffset >= pieceSize) {

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
                
                // Tell the central manager this piece is done!
                if (pieceManager != null) {
                    pieceManager.markCompleted(index);
                    System.out.println("Piece " + index + " downloaded and verified successfully. [" 
                        + pieceManager.getCompletedPieces().cardinality() + "/" + torrentInfo.getPieceHashes().size() + "]");
                }
                
                // Reset our assignment so fillPipeline() will ask for a new one
                currentPieceIndex = -1;
                requestedBlockOffset = 0;
                receivedBlockOffset = 0;
                pendingRequests = 0;
            } else {
                System.err.println("Piece " + index + " FAILED hash check! Dropping peer.");

                // If they send bad data, we cut them off to protect our download
                throw new IOException("Received corrupt piece from peer");
            }
        }

        // Ask for next block immediately
        fillPipeline();
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

            // Send our bitfield so the peer knows what we can upload!
            if (pieceManager != null) {
                java.util.BitSet myPieces = pieceManager.getCompletedPieces();
                if (myPieces.cardinality() > 0) {
                    int bitfieldLength = (torrentInfo.getPieceHashes().size() + 7) / 8;
                    byte[] bitfield = new byte[bitfieldLength];

                    // BitTorrent uses a bizarre Big-Endian bit mapping
                    for (int i = 0; i < myPieces.length(); i++) {
                        if (myPieces.get(i)) {
                            bitfield[i / 8] |= (1 << (7 - (i % 8)));
                        }
                    }
                    sendMessage(new PeerMessage(PeerMessage.MessageType.BITFIELD, bitfield));
                }
            }

            readLoop();

            readLoop();

        } catch (IOException e) {
            // Ignore normal disconnects in console output if preferred, but keeping it for debugging
        } finally {
            close();
        }
    }

    public void close() {
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
