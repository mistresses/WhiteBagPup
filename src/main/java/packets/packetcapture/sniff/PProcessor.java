package packets.packetcapture.sniff;

/**
 * Packet processing class calling the sniffer.
 */
public interface PProcessor {

    /**
     * Reset called when a TCP reset packet is received on incoming packets.
     */
    void resetIncoming();

    /**
     * Reset called when a TCP reset packet is received on outgoing packets.
     */
    void resetOutgoing();

    /**
     * Incoming stream from the TCP payload.
     *
     * @param data    TCP packet payload byte data containing the stream.
     * @param srcAddr Source of the incoming packets.
     */
    void incomingStream(byte[] data, byte[] srcAddr);

    /**
     * Outgoing stream from the TCP payload.
     *
     * @param data    TCP packet payload byte data containing the stream.
     * @param srcAddr Source of the outgoing packets.
     */
    void outgoingStream(byte[] data, byte[] srcAddr);

    /**
     * White Bag Pup: data from one game connection (one game client). Defaults to the single-stream methods.
     *
     * @param connectionId Id of the connection (one per game client connection).
     * @param incoming     True for server-to-client data.
     * @param fresh        True if the connection was seen from its very start.
     */
    default void connectionStream(int connectionId, boolean incoming, boolean fresh, byte[] data, byte[] srcAddr) {
        if (incoming) incomingStream(data, srcAddr);
        else outgoingStream(data, srcAddr);
    }

    /** White Bag Pup: a TCP reset on one connection. */
    default void connectionReset(int connectionId, boolean incoming) {
        if (incoming) resetIncoming();
        else resetOutgoing();
    }

    /** White Bag Pup: a connection closed. */
    default void connectionClosed(int connectionId) {
    }
}
