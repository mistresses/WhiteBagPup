package whitebag;

import packets.Packet;
import packets.PacketType;
import packets.data.ObjectData;
import packets.data.StatData;
import packets.incoming.CreateSuccessPacket;
import packets.incoming.TextPacket;
import packets.incoming.UpdatePacket;
import packets.packetcapture.register.Register;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the in-game chat for this session, sorted into kinds the Chat tab can filter on.
 */
public class ChatLog {
    private static final int MAX_LINES = 5000;
    private static final int NAME_STAT = 31;

    public enum Kind { ALL, GUILD, PARTY, WHISPER, SYSTEM }

    public static final class Line {
        public final long timeMs;
        public final Kind kind;
        public final String name;
        /** For whispers: the other person. */
        public final String other;
        public final boolean fromMe;
        public final boolean toMe;
        public final String text;
        public final int stars;

        Line(long timeMs, Kind kind, String name, String other, boolean fromMe, boolean toMe, String text, int stars) {
            this.timeMs = timeMs;
            this.kind = kind;
            this.name = name;
            this.other = other;
            this.fromMe = fromMe;
            this.toMe = toMe;
            this.text = text;
            this.stars = stars;
        }

        /** True when this line is mine, to me, or mentions my name. */
        public boolean aboutMe(String myName) {
            if (fromMe || toMe) return true;
            return myName != null && !myName.isEmpty() && text.toLowerCase().contains(myName.toLowerCase());
        }
    }

    public interface Listener {
        void added(Line line);
    }

    private final Listener listener;
    private final List<Line> lines = new ArrayList<>();
    private int myId = -1;
    private volatile String myName = "";

    public ChatLog(Listener listener) {
        this.listener = listener;
    }

    public void register(Bus bus) {
        bus.register(PacketType.TEXT, this::onText);
        bus.register(PacketType.CREATE_SUCCESS, p -> myId = ((CreateSuccessPacket) p).objectId);
        bus.register(PacketType.UPDATE, this::onUpdate);
    }

    public String myName() {
        return myName;
    }

    public synchronized List<Line> lines() {
        return new ArrayList<>(lines);
    }

    private void onUpdate(Packet packet) {
        UpdatePacket p = (UpdatePacket) packet;
        if (p.newObjects == null || myId == -1) return;
        for (ObjectData o : p.newObjects) {
            if (o.status == null || o.status.objectId != myId || o.status.stats == null) continue;
            for (StatData s : o.status.stats) {
                if (s.statTypeNum == NAME_STAT && s.stringStatValue != null && !s.stringStatValue.isEmpty()) myName = s.stringStatValue;
            }
        }
    }

    void onText(Packet packet) {
        TextPacket p = (TextPacket) packet;
        Line line = classify(p, myName, System.currentTimeMillis());
        if (line == null) return;
        synchronized (this) {
            lines.add(line);
            if (lines.size() > MAX_LINES) lines.subList(0, lines.size() - MAX_LINES).clear();
        }
        try {
            listener.added(line);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static Line classify(TextPacket p, String myName, long now) {
        String text = p.text == null ? "" : p.text;
        if (text.trim().isEmpty()) return null;
        String rawName = p.name == null ? "" : p.name;
        String name = rawName.split(",")[0].trim();
        String recipient = p.recipient == null ? "" : p.recipient.trim();
        boolean fromMe = !myName.isEmpty() && name.equalsIgnoreCase(myName);

        Kind kind;
        String other = "";
        boolean toMe = false;
        if (recipient.contains("*Guild*")) {
            kind = Kind.GUILD;
        } else if (recipient.contains("*Party*")) {
            kind = Kind.PARTY;
        } else if (!recipient.isEmpty()) {
            kind = Kind.WHISPER;
            String to = recipient.split(",")[0].trim();
            if (fromMe) {
                other = to;
            } else {
                other = name;
                toMe = myName.isEmpty() || to.equalsIgnoreCase(myName);
            }
        } else if (name.isEmpty() || name.startsWith("#") || name.startsWith("*")) {
            kind = Kind.SYSTEM;
            if (name.startsWith("#")) name = name.substring(1); // NPCs and bosses talk with a leading #
            if (name.startsWith("*") && name.endsWith("*")) name = "";
        } else {
            kind = Kind.ALL;
        }
        return new Line(now, kind, name, other, fromMe, toMe, text, Math.max(0, p.numStars));
    }
}
