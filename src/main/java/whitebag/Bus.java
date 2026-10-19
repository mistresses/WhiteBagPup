package whitebag;

import packets.Packet;
import packets.PacketType;
import packets.packetcapture.register.IPacketListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One account's packet dispatcher: the same idea as Puppy's Register, but one per game account,
 * so two game clients' packets go to their own trackers.
 */
final class Bus {
    private final Map<Class<? extends Packet>, List<IPacketListener<Packet>>> listeners = new HashMap<>();

    synchronized void register(PacketType type, IPacketListener<Packet> listener) {
        listeners.computeIfAbsent(type.getPacketClass(), k -> new ArrayList<>()).add(listener);
    }

    void emit(Packet packet) {
        List<IPacketListener<Packet>> list;
        synchronized (this) {
            List<IPacketListener<Packet>> l = listeners.get(packet.getClass());
            if (l == null) return;
            list = new ArrayList<>(l);
        }
        for (IPacketListener<Packet> h : list) {
            try {
                h.process(packet);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
