package com.bittorrent;

public record PeerMessage(MessageType type, byte[] payload) {

    public enum MessageType {
        CHOKE(0), UNCHOKE(1), INTERESTED(2), NOT_INTERESTED(3),
        HAVE(4), BITFIELD(5), REQUEST(6), PIECE(7), CANCEL(8);

        private final int id;

        MessageType(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public static MessageType fromId(int id) {
            for (MessageType type : values()) {
                if (type.id == id) return type;
            }
            throw new IllegalArgumentException("Unknown message ID: " + id);
        }
    }

    public static PeerMessage decode(int messageId, byte[] payload) {
        // TODO: Decode raw message bytes into PeerMessage object (Milestone 5)
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
