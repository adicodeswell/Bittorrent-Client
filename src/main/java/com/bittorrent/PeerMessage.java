package com.bittorrent;

public record PeerMessage(MessageType type, byte[] payload) {

    public enum MessageType {
        CHOKE(0), UNCHOKE(1), INTERESTED(2), NOT_INTERESTED(3),
        HAVE(4), BITFIELD(5), REQUEST(6), PIECE(7), CANCEL(8),
        PORT(9), EXTENDED(20), UNKNOWN(-1);

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
            return UNKNOWN;
        }
    }

    public static PeerMessage decode(int messageId, byte[] payload) {
        MessageType type = MessageType.fromId(messageId);
        return new PeerMessage(type, payload);
    }
}
