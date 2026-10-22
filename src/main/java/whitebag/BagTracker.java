package whitebag;

import packets.Packet;
import packets.PacketType;
import packets.data.ObjectData;
import packets.data.ObjectStatusData;
import packets.data.StatData;
import packets.incoming.CreateSuccessPacket;
import packets.incoming.MapInfoPacket;
import packets.incoming.NewTickPacket;
import packets.incoming.UpdatePacket;
import packets.packetcapture.register.Register;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Watches Update / NewTick packets for loot bags appearing and hands each new bag to a listener.
 * <p>
 * A bag's contents can arrive a moment after the bag itself, so each bag is held for a short
 * window collecting item stats before it is reported (or reported straight away if it leaves view).
 */
public class BagTracker {
    private static final long COLLECT_MS = 900;
    private static final int NAME_STAT = 31;

    public interface Listener {
        void bagDropped(BagDrop drop);
    }

    private final Listener listener;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "bag-timer");
        t.setDaemon(true);
        return t;
    });

    private final Set<Integer> seenBags = new HashSet<>();
    /**
     * Bags already reported in the last moment. When a bag gets more than 8 items the game spills the rest into
     * a second bag right next to it; those two are reported as one post (see {@link #flush}).
     */
    private final java.util.List<BagDrop> recentBags = new java.util.ArrayList<>();
    private static final long OVERFLOW_MS = 2500;
    private static final double OVERFLOW_TILES = 2.5;
    private final Map<Integer, BagDrop> pending = new HashMap<>();
    /** Gets every bag, watched or not (for the Loot stats tab). */
    volatile java.util.function.Consumer<BagDrop> onAnyBag;
    private volatile Set<BagType> watched = EnumSet.of(BagType.WHITE, BagType.BOOSTED_WHITE);
    private String mapName = "";
    private String playerName = "";
    private int myObjectId = -1;

    public BagTracker(Listener listener) {
        this.listener = listener;
    }

    /** Set while the Discord filter is "Shiny only": then shinies are posted from every bag colour. */
    static volatile boolean shinyAnyBag;

    private static boolean hasShiny(BagDrop drop) {
        for (ItemInfo i : drop.items()) if (LootBook.isShiny(i.id)) return true;
        return false;
    }

    public void setWatched(Set<BagType> types) {
        watched = types.isEmpty() ? EnumSet.noneOf(BagType.class) : EnumSet.copyOf(types);
    }

    /**
     * Subscribes to the packets this tracker needs. Call once.
     */
    public void register(Bus bus) {
        bus.register(PacketType.MAPINFO, this::onMapInfo);
        bus.register(PacketType.CREATE_SUCCESS, this::onCreateSuccess);
        bus.register(PacketType.UPDATE, this::onUpdate);
        bus.register(PacketType.NEWTICK, this::onNewTick);
    }

    private synchronized void onMapInfo(Packet packet) {
        MapInfoPacket p = (MapInfoPacket) packet;
        mapName = cleanName(p.name, p.displayName);
        seenBags.clear();
        pending.clear();
        recentBags.clear();
        myObjectId = -1;
    }

    private synchronized void onCreateSuccess(Packet packet) {
        myObjectId = ((CreateSuccessPacket) packet).objectId;
    }

    private synchronized void onUpdate(Packet packet) {
        UpdatePacket p = (UpdatePacket) packet;
        if (p.newObjects != null) {
            for (ObjectData o : p.newObjects) {
                ObjectStatusData status = o.status;
                if (status == null) continue;
                if (status.objectId == myObjectId) readPlayerName(status.stats);

                BagType type = BagType.byObjectType(o.objectType);
                if (type == null) continue;
                if (!seenBags.add(status.objectId)) continue;

                BagDrop drop = new BagDrop(status.objectId, type, mapName, playerName);
                drop.applyStats(status.stats);
                if (status.pos != null) {
                    drop.x = status.pos.x;
                    drop.y = status.pos.y;
                }
                pending.put(status.objectId, drop);
                timer.schedule(() -> flush(drop.objectId), COLLECT_MS, TimeUnit.MILLISECONDS);
            }
        }
        if (p.drops != null) {
            for (int id : p.drops) {
                if (pending.containsKey(id)) flush(id); // bag left view, report what we have
            }
        }
    }

    /** Two bags that appeared together side by side, at least one of them full: a bag and its overflow. */
    private static boolean overflowPair(BagDrop a, BagDrop b) {
        if (Double.isNaN(a.x) || Double.isNaN(b.x)) return false;
        if (Math.abs(a.seenMs - b.seenMs) > OVERFLOW_MS) return false;
        if (Math.hypot(a.x - b.x, a.y - b.y) > OVERFLOW_TILES) return false;
        return a.filledSlots() >= 8 || b.filledSlots() >= 8;
    }

    /** Which bag of an overflow pair the post is for: the rarer colour, then the fuller bag. */
    private static int rank(BagDrop d) {
        switch (LootStats.colour(d.type)) {
            case WHITE: return 60;
            case RED: return 50;
            case ORANGE: return 40;
            case GOLD: return 30;
            case CYAN: return 25;
            case BLUE: return 20;
            case EGG: return 10;
            default: return 0;
        }
    }

    private synchronized void onNewTick(Packet packet) {
        NewTickPacket p = (NewTickPacket) packet;
        if (p.status == null || pending.isEmpty()) return;
        for (ObjectStatusData s : p.status) {
            BagDrop drop = pending.get(s.objectId);
            if (drop != null) drop.applyStats(s.stats);
        }
    }

    private synchronized void flush(int objectId) {
        BagDrop drop = pending.remove(objectId);
        if (drop == null || drop.sent) return;
        long now = System.currentTimeMillis();
        recentBags.removeIf(b -> now - b.seenMs > OVERFLOW_MS * 2);
        // the overflow of a bag that was already posted: nothing more to post
        for (BagDrop b : recentBags) {
            if (overflowPair(b, drop)) {
                drop.sent = true;
                return;
            }
        }
        // a bag and its overflow both still waiting: post them once, as the main bag with every item
        java.util.List<BagDrop> group = new java.util.ArrayList<>();
        group.add(drop);
        for (BagDrop other : new java.util.ArrayList<>(pending.values())) {
            if (overflowPair(drop, other)) {
                group.add(other);
                pending.remove(other.objectId);
            }
        }
        if (group.size() > 1) {
            BagDrop main = drop;
            for (BagDrop b : group) {
                if (rank(b) > rank(main) || (rank(b) == rank(main) && b.filledSlots() > main.filledSlots())) main = b;
            }
            for (BagDrop b : group) {
                b.sent = true;
                if (b != main) main.absorb(b);
            }
            drop = main;
        }
        drop.sent = true;
        recentBags.addAll(group);
        try {
            java.util.function.Consumer<BagDrop> any = onAnyBag;
            if (any != null) any.accept(drop);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // only ping for the bags you picked; with "Shiny only", a bag of any colour with a shiny item counts too
        if (!watched.contains(drop.type) && !(shinyAnyBag && hasShiny(drop))) return;
        try {
            listener.bagDropped(drop);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readPlayerName(StatData[] stats) {
        if (stats == null) return;
        for (StatData s : stats) {
            if (s.statTypeNum == NAME_STAT && s.stringStatValue != null && !s.stringStatValue.isEmpty()) {
                playerName = DpsTracker.cleanName(s.stringStatValue);
            }
        }
    }

    /**
     * Map names sometimes come as localisation keys like "{s.nexus}"; tidy those up.
     */
    static String cleanName(String name, String displayName) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.startsWith("{")) {
            String d = displayName == null ? "" : displayName.trim();
            if (!d.isEmpty() && !d.startsWith("{")) n = d;
        }
        if (n.startsWith("{") && n.endsWith("}")) {
            n = n.substring(1, n.length() - 1);
            int dot = n.lastIndexOf('.');
            if (dot >= 0) n = n.substring(dot + 1);
            n = n.replace('_', ' ');
            StringBuilder sb = new StringBuilder();
            for (String w : n.split(" ")) {
                if (w.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
            n = sb.toString();
        }
        return n;
    }
}
