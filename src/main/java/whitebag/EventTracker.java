package whitebag;

import assets.IdToAsset;
import packets.Packet;
import packets.PacketType;
import packets.data.ObjectData;
import packets.incoming.GlobalNotificationPacket;
import packets.incoming.MapInfoPacket;
import packets.incoming.NotificationPacket;
import packets.incoming.QuestObjectIdPacket;
import packets.incoming.TextPacket;
import packets.incoming.UpdatePacket;

import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Notices realm events (Cube God, Grand Sphinx, Lord of the Lost Lands, ...) from what the game already tells you:
 * <ul>
 *   <li>your quest arrow pointing at an event boss (the earliest sign of a fresh spawn nearby),</li>
 *   <li>an event boss coming into view,</li>
 *   <li>Oryx or the server announcing it in chat or a notification.</li>
 * </ul>
 * Only reads packets, like everything else. One tracker per account.
 */
final class EventTracker {

    /** Realm events, by the boss's name in the game files. Editable in the Events tab. */
    static final List<String> DEFAULT_EVENTS = Collections.unmodifiableList(Arrays.asList(
            "Cube God", "Skull Shrine", "Pentaract Tower", "Grand Sphinx", "Lord of the Lost Lands", "Hermit God",
            "Ghost Ship", "Dragon Head", "Lucky Ent God", "Lucky Djinn", "Avatar of the Forgotten King",
            "Killer Bee Nest", "Jade Statue", "Garnet Statue", "Crystal Prisoner", "Legion General"));

    static final class Entry {
        final String name;
        final long firstMs;
        volatile long lastMs;
        final String map;
        final Set<String> how = new LinkedHashSet<>();
        volatile boolean defeated;
        volatile boolean pinged;

        Entry(String name, String map, long now) {
            this.name = name;
            this.map = map;
            this.firstMs = now;
            this.lastMs = now;
        }
    }

    // ---------------------------------------------------------------- shared settings (all accounts)

    private static final String FILE = "events.properties";
    private static final Properties settings = new Properties();
    private static volatile List<String> events;
    private static volatile Set<String> pinged;

    static {
        try (FileReader r = new FileReader(FILE)) {
            settings.load(r);
        } catch (Exception ignored) {
        }
        String list = settings.getProperty("events");
        events = list == null ? DEFAULT_EVENTS : split(list);
        String ping = settings.getProperty("ping");
        pinged = ping == null ? new HashSet<>(events) : new HashSet<>(split(ping));
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String p : s.split("[,\\n]")) {
            String t = p.trim().replaceAll("\\s+", " ");
            if (!t.isEmpty() && t.length() <= 80 && seen.add(norm(t))) out.add(t);
        }
        return out;
    }

    private static synchronized void save() {
        settings.setProperty("events", String.join(",", events));
        List<String> on = new ArrayList<>();
        for (String e : events) if (pinged.contains(e)) on.add(e);
        settings.setProperty("ping", String.join(",", on));
        try (FileWriter w = new FileWriter(FILE)) {
            settings.store(w, "White Bag Pup: realm events to watch, and which ones ping you");
        } catch (Exception ignored) {
        }
    }

    static List<String> events() {
        return events;
    }

    /** Other event settings (the RealmStock feed's), kept in the same file. */
    static synchronized String setting(String key, String fallback) {
        return settings.getProperty(key, fallback);
    }

    static synchronized void setSetting(String key, String value) {
        settings.setProperty(key, value);
        save();
    }

    /** The realm event in your Alerts list that this name is (or contains), or null. */
    static String alertFor(String name) {
        String n = norm(name);
        for (String e : events) {
            String k = norm(e);
            if (n.equals(k) || containsWord(n, k) || containsWord(k, n)) return e;
        }
        return null;
    }

    static boolean pings(String event) {
        return pinged.contains(event);
    }

    static synchronized void setPing(String event, boolean on) {
        Set<String> p = new HashSet<>(pinged);
        if (on) p.add(event);
        else p.remove(event);
        pinged = p;
        save();
        RealmStockFeed.changed();
    }

    /** Replaces the list of events (new ones ping by default). */
    static synchronized void setEvents(String text) {
        List<String> list = split(text);
        if (list.isEmpty()) list = DEFAULT_EVENTS;
        Set<String> p = new HashSet<>(pinged);
        for (String e : list) if (!events.contains(e)) p.add(e);
        events = list;
        pinged = p;
        save();
        RealmStockFeed.changed();
    }

    static synchronized void resetEvents() {
        events = DEFAULT_EVENTS;
        pinged = new HashSet<>(DEFAULT_EVENTS);
        save();
        RealmStockFeed.changed();
    }

    // ---------------------------------------------------------------- per account

    private static final long SAME_EVENT_MS = 3 * 60_000L;
    private static final Pattern DEFEAT = Pattern.compile("\\b(slain|defeated|killed|destroyed|vanquished|dead|fallen|died)\\b");

    private final Consumer<Entry> onNew;
    private final Runnable onChange;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<Integer, Integer> typeOf = new HashMap<>();
    private final Set<Integer> seenObjects = new HashSet<>();
    private int pendingQuest = -1;
    private String map = "";

    EventTracker(Consumer<Entry> onNew, Runnable onChange) {
        this.onNew = onNew;
        this.onChange = onChange;
    }

    void register(Bus bus) {
        bus.register(PacketType.MAPINFO, this::onMapInfo);
        bus.register(PacketType.UPDATE, this::onUpdate);
        bus.register(PacketType.QUESTOBJID, this::onQuest);
        bus.register(PacketType.TEXT, this::onText);
        bus.register(PacketType.NOTIFICATION, this::onNotification);
        bus.register(PacketType.GLOBAL_NOTIFICATION, this::onGlobal);
    }

    synchronized List<Entry> entries() {
        return new ArrayList<>(entries);
    }

    private synchronized void onMapInfo(Packet packet) {
        MapInfoPacket p = (MapInfoPacket) packet;
        map = BagTracker.cleanName(p.name, p.displayName);
        if (p.realmName != null && !p.realmName.trim().isEmpty() && !p.realmName.startsWith("{")) map = map + " (" + p.realmName.trim() + ")";
        typeOf.clear();
        seenObjects.clear();
        pendingQuest = -1;
    }

    private synchronized void onUpdate(Packet packet) {
        UpdatePacket p = (UpdatePacket) packet;
        if (p.newObjects != null) {
            for (ObjectData o : p.newObjects) {
                if (o.status == null) continue;
                int id = o.status.objectId;
                typeOf.put(id, o.objectType);
                if (id == pendingQuest) {
                    pendingQuest = -1;
                    String n = eventNamed(o.objectType);
                    if (n != null) seen(n, "your quest arrow");
                }
                if (seenObjects.add(id)) {
                    String n = eventNamed(o.objectType);
                    if (n != null) seen(n, "came into view");
                }
            }
        }
        if (p.drops != null) for (int id : p.drops) typeOf.remove(id);
    }

    private synchronized void onQuest(Packet packet) {
        int id = ((QuestObjectIdPacket) packet).objectId;
        Integer type = typeOf.get(id);
        if (type == null) {
            pendingQuest = id; // its type arrives when it comes into view
            return;
        }
        String n = eventNamed(type);
        if (n != null) seen(n, "your quest arrow");
    }

    private void onText(Packet packet) {
        TextPacket p = (TextPacket) packet;
        if (p.text == null || p.name == null) return;
        if (p.recipient != null && !p.recipient.trim().isEmpty()) return; // whispers
        String sender = p.name.split(",", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!(sender.isEmpty() || sender.startsWith("#"))) return; // only Oryx / the server, not players
        announcement(p.text, "Oryx announced it");
    }

    private void onNotification(Packet packet) {
        announcement(((NotificationPacket) packet).message, "game notification");
    }

    private void onGlobal(Packet packet) {
        announcement(((GlobalNotificationPacket) packet).text, "game notification");
    }

    private synchronized void announcement(String text, String how) {
        if (text == null || text.isEmpty()) return;
        String t = norm(text);
        boolean defeat = DEFEAT.matcher(t).find();
        for (String e : events) {
            if (!containsWord(t, norm(e))) continue;
            if (defeat) defeated(e);
            else seen(e, how);
        }
    }

    /** The event this object type is, or null. */
    private static String eventNamed(int type) {
        List<String> names = new ArrayList<>(3);
        try {
            names.add(IdToAsset.objectName(type));
        } catch (Throwable ignored) {
        }
        try {
            names.add(IdToAsset.getDisplayName(type));
        } catch (Throwable ignored) {
        }
        try {
            names.add(IdToAsset.getObjectIdName(type));
        } catch (Throwable ignored) {
        }
        for (String n : names) {
            if (n == null || n.isEmpty()) continue;
            String k = norm(n);
            for (String e : events) if (k.equals(norm(e))) return e;
        }
        return null;
    }

    /** Lower case, "{s.Cube_God}" style keys and underscores tidied, single spaces. */
    static String norm(String s) {
        String t = s.toLowerCase(Locale.ROOT).replace('_', ' ').replaceAll("[{}\"]", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static boolean containsWord(String text, String phrase) {
        return !phrase.isEmpty() && Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}])").matcher(text).find();
    }

    private void seen(String name, String how) {
        long now = System.currentTimeMillis();
        Entry recent = recent(name, now);
        if (recent != null && !recent.defeated) {
            recent.lastMs = now;
            if (recent.how.add(how)) onChange.run();
            return;
        }
        Entry e = new Entry(name, map, now);
        e.how.add(how);
        entries.add(e);
        while (entries.size() > 200) entries.remove(0);
        onNew.accept(e);
        onChange.run();
    }

    private void defeated(String name) {
        long now = System.currentTimeMillis();
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            if (e.name.equals(name) && e.map.equals(map) && now - e.lastMs < 30 * 60_000L) {
                if (!e.defeated) {
                    e.defeated = true;
                    onChange.run();
                }
                return;
            }
        }
    }

    private Entry recent(String name, long now) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            if (e.name.equals(name) && e.map.equals(map) && now - e.lastMs < SAME_EVENT_MS) return e;
        }
        return null;
    }
}
