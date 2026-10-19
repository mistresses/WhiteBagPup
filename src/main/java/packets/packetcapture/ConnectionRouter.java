package packets.packetcapture;

import packets.Packet;
import packets.packetcapture.register.Register;

/**
 * White Bag Pup: each game client has its own connection. Every decoded packet is handed here with the id
 * of the connection it came from, so an app can tell several game clients (accounts) apart. By default it
 * just emits the packet as before.
 */
public final class ConnectionRouter {
    public interface Handler {
        void packet(int connectionId, Packet packet);
    }

    public interface Closed {
        void closed(int connectionId);
    }

    private static volatile Handler handler;
    private static volatile Closed closedHandler;
    private static final ThreadLocal<Integer> CURRENT = ThreadLocal.withInitial(() -> 0);

    private ConnectionRouter() {
    }

    public static void setHandler(Handler h) {
        handler = h;
    }

    public static void setClosedHandler(Closed c) {
        closedHandler = c;
    }

    static void setCurrent(int connectionId) {
        CURRENT.set(connectionId);
    }

    public static int current() {
        return CURRENT.get();
    }

    static void emit(Packet packet) {
        Handler h = handler;
        if (h != null) h.packet(CURRENT.get(), packet);
        else Register.INSTANCE.emitPacketLogs(packet);
    }

    static void closed(int connectionId) {
        Closed c = closedHandler;
        if (c != null) c.closed(connectionId);
    }
}
