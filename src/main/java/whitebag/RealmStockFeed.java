package whitebag;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Realm events and dungeon key pops on every server, from RealmStock's event notifier
 * (https://realmstock.com/pages/event-notifier), used with their permission. Off until turned on in the Events tab.
 * <p>
 * The feed is plain text, one event per line:
 * {@code type|realm|server|players|events left|realm score %|time (UTC)|id|realm closed|enemy tier}.
 * The type is an event from their event type list, or a dungeon (a key popped in that server's Nexus).
 */
final class RealmStockFeed {
    private RealmStockFeed() {
    }

    static final String FEED_URL = System.getProperty("realmstock.feed", "https://realmstock.network/Notifier/EventHistory");
    static final String EVENT_TYPES_URL = System.getProperty("realmstock.eventTypes", "https://realm.wiki/RealmStock/EventTypes");
    static final String DUNGEON_TYPES_URL = System.getProperty("realmstock.dungeonTypes", "https://realm.wiki/RealmStock/DungeonTypes");
    static final String PAGE_URL = "https://realmstock.com/pages/event-notifier";
    private static final long POLL_MS = Long.getLong("realmstock.pollMs", 15_000L);
    /** Type 3 with the "closed" flag is a realm closing. */
    private static final int REALM_CLOSED_TYPE = 3;

    enum Kind { EVENT, KEY_POP, REALM_CLOSED, OTHER }

    static final class Event {
        String id;
        int type;
        Kind kind;
        String name;
        String realm, server;
        int players = -1, eventsLeft = -1, score = -1, tier;
        long atMs;

        String region() {
            return regionOf(server);
        }
    }

    /** "US", "EU", "AS" or "AU" for a server name like USEast2 or EUWest. */
    static String regionOf(String server) {
        if (server == null) return "";
        if (server.startsWith("US")) return "US";
        if (server.startsWith("EU")) return "EU";
        if (server.startsWith("Asia")) return "AS";
        if (server.startsWith("Australia")) return "AU";
        return "";
    }

    // ---------------------------------------------------------------- settings

    static boolean enabled() {
        return Boolean.parseBoolean(EventTracker.setting("realmstock.on", "false"));
    }

    static void setEnabled(boolean on) {
        EventTracker.setSetting("realmstock.on", String.valueOf(on));
        if (on) start();
        else status = "Off.";
        changed();
    }

    /** "ALL", "US", "EU", "AS" or "AU". */
    static String region() {
        return EventTracker.setting("realmstock.region", "ALL");
    }

    static void setRegion(String r) {
        EventTracker.setSetting("realmstock.region", r);
        changed();
    }

    /** Play a sound for ticked events (they still go in the pup diary when this is off). */
    static boolean sound() {
        return Boolean.parseBoolean(EventTracker.setting("realmstock.sound", "true"));
    }

    static void setSound(boolean on) {
        EventTracker.setSetting("realmstock.sound", String.valueOf(on));
        changed();
    }

    /** The sound file names this event looks for, best first (without .wav). */
    static String[] soundNames(Event e) {
        String kind = e.kind == Kind.KEY_POP ? "key-pop" : e.kind == Kind.REALM_CLOSED ? "realm-closed" : "event";
        return new String[]{Squeak.slug(e.name), kind};
    }

    static boolean pingClosed() {
        return Boolean.parseBoolean(EventTracker.setting("realmstock.ping.closed", "false"));
    }

    static void setPing(String what, boolean on) {
        EventTracker.setSetting("realmstock.ping." + what, String.valueOf(on));
        changed();
    }

    /** Show only what you're watching (ticked events, key pops and closings), or everything. */
    static boolean showAll() {
        return Boolean.parseBoolean(EventTracker.setting("realmstock.showAll", "false"));
    }

    static void setShowAll(boolean all) {
        EventTracker.setSetting("realmstock.showAll", String.valueOf(all));
        changed();
    }

    // ---------------------------------------------------------------- tick boxes

    /** The exaltation dungeons: ticked to start with. */
    static final List<String> EXALT_DUNGEONS = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
            "Oryx's Sanctuary", "The Shatters", "Lost Halls", "The Void", "Cultist Hideout", "The Nest",
            "Fungal Cavern", "Crystal Cavern", "Moonlight Village", "Advanced Kogbold Steamworks"));

    /** Rooms in RealmStock's dungeon list that aren't dungeons you'd pop a key for. */
    private static final java.util.regex.Pattern NOT_A_DUNGEON = java.util.regex.Pattern.compile(
            "(?i)test|explanation|arena|rehearsal|^nexus$|^vault$|guild hall|kitchen|pet yard|daily|bazaar|random realm|mosaic"
                    + "|treasure room|mgm2|^chess$|encounter|^time chamber$|snowball rolling|interregnum daily|secluded thicket");

    private static Set<String> splitSet(String s) {
        Set<String> out = new java.util.LinkedHashSet<>();
        if (s == null) return out;
        for (String p : s.split("\\|")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Every realm event on RealmStock's list, tidied and sorted. */
    static synchronized List<String> eventNames() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String n : eventTypes.values()) {
            String t = tidy(n);
            if (!t.isEmpty()) names.add(t);
        }
        return new ArrayList<>(names);
    }

    /** Every dungeon on RealmStock's list, sorted. */
    static synchronized List<String> dungeonNames() {
        java.util.TreeSet<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String n : dungeonTypes.values()) if (!NOT_A_DUNGEON.matcher(n).find()) names.add(n.trim());
        return new ArrayList<>(names);
    }

    static boolean eventTicked(String name) {
        String saved = EventTracker.setting("realmstock.eventTicks", null);
        if (saved != null) return splitSet(saved).contains(name);
        // until you change anything: the realm bosses ticked before (Cube God, Skull Shrine, ...)
        String alert = EventTracker.alertFor(name);
        return alert != null && EventTracker.pings(alert);
    }

    static synchronized void setEventTicked(String name, boolean on) {
        Set<String> ticks = new java.util.LinkedHashSet<>();
        for (String n : eventNames()) if (n.equals(name) ? on : eventTicked(n)) ticks.add(n);
        EventTracker.setSetting("realmstock.eventTicks", String.join("|", ticks));
        changed();
    }

    static synchronized void setAllEvents(boolean on) {
        EventTracker.setSetting("realmstock.eventTicks", on ? String.join("|", eventNames()) : "");
        changed();
    }

    static boolean keyTicked(String dungeon) {
        String saved = EventTracker.setting("realmstock.dungeonTicks", null);
        if (saved != null) return splitSet(saved).contains(dungeon);
        return EXALT_DUNGEONS.contains(dungeon);
    }

    static synchronized void setKeyTicked(String dungeon, boolean on) {
        Set<String> ticks = new java.util.LinkedHashSet<>();
        for (String n : dungeonNames()) if (n.equals(dungeon) ? on : keyTicked(n)) ticks.add(n);
        EventTracker.setSetting("realmstock.dungeonTicks", String.join("|", ticks));
        changed();
    }

    static synchronized void setAllKeys(boolean on) {
        EventTracker.setSetting("realmstock.dungeonTicks", on ? String.join("|", dungeonNames()) : "");
        changed();
    }

    static synchronized void resetKeyDungeons() {
        EventTracker.setSetting("realmstock.dungeonTicks", String.join("|", EXALT_DUNGEONS));
        changed();
    }

    // ---------------------------------------------------------------- state

    private static final Map<Integer, String> eventTypes = new HashMap<>();
    private static final Map<Integer, String> dungeonTypes = new HashMap<>();
    private static final LinkedHashMap<String, Event> events = new LinkedHashMap<>();
    private static final Set<String> seen = new HashSet<>();
    private static volatile boolean firstPollDone = false;
    private static volatile String status = "Off.";
    private static volatile long lastOk = 0;
    private static Thread poller;
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    /** Gets each new event that should ping you. */
    static volatile Consumer<Event> onPing;

    static void addListener(Runnable r) {
        listeners.add(r);
    }

    /** Redraws the feed lists (when the feed updates or a tick box changes). */
    static void changed() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    static String status() {
        return status;
    }

    static long lastOk() {
        return lastOk;
    }

    /** The events in the feed, newest first. */
    static synchronized List<Event> events() {
        List<Event> out = new ArrayList<>(events.values());
        out.sort((a, b) -> Long.compare(b.atMs, a.atMs));
        return out;
    }

    /** Starts checking the feed in the background (if it's turned on). Safe to call more than once. */
    static synchronized void start() {
        if (!enabled() || (poller != null && poller.isAlive())) return;
        status = "Connecting...";
        poller = new Thread(RealmStockFeed::run, "realmstock-feed");
        poller.setDaemon(true);
        poller.start();
    }

    private static void run() {
        loadTypes();
        while (enabled()) {
            try {
                String body = get(FEED_URL);
                JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                if (o.has("success") && !o.get("success").getAsBoolean()) throw new Exception("the feed said it couldn't send events");
                List<Event> fresh = parse(o.get("value").getAsString(), System.currentTimeMillis());
                accept(fresh);
                lastOk = System.currentTimeMillis();
                status = "Live";
            } catch (Exception e) {
                status = "Can't reach RealmStock right now (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()) + "). Trying again shortly.";
                System.out.println("RealmStock feed: " + e);
            }
            changed();
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
        status = "Off.";
        changed();
    }

    private static synchronized void accept(List<Event> fresh) {
        List<Event> pings = new ArrayList<>();
        for (Event e : fresh) {
            events.put(e.id, e); // later polls update players / score
            if (seen.add(e.id) && firstPollDone && shouldPing(e)) pings.add(e);
        }
        // keep the last ~1500 events
        while (events.size() > 1500) {
            String oldest = events.keySet().iterator().next();
            events.remove(oldest);
        }
        if (seen.size() > 20000) seen.retainAll(events.keySet());
        firstPollDone = true; // what's already in the feed when you turn it on doesn't ping
        Consumer<Event> c = onPing;
        if (c != null) for (Event e : pings) c.accept(e);
    }

    static boolean inRegion(Event e) {
        String r = region();
        return r.equals("ALL") || r.equals(e.region());
    }

    static boolean shouldPing(Event e) {
        if (!inRegion(e)) return false;
        switch (e.kind) {
            case EVENT:
                return eventTicked(e.name);
            case KEY_POP:
                return keyTicked(e.name);
            case REALM_CLOSED:
                return pingClosed();
            default:
                return false;
        }
    }

    // ---------------------------------------------------------------- parsing

    static List<Event> parse(String value, long nowMs) {
        List<Event> out = new ArrayList<>();
        for (String line : value.split("\n")) {
            String[] f = line.trim().split("\\|", -1);
            if (f.length < 8) continue;
            try {
                Event e = new Event();
                e.type = Integer.parseInt(f[0].trim());
                e.realm = f[1].trim();
                e.server = f[2].trim();
                e.players = num(f[3]);
                e.eventsLeft = num(f[4]);
                e.score = num(f[5]);
                e.atMs = timeToday(f[6].trim(), nowMs);
                e.id = f[7].trim();
                boolean closed = f.length > 8 && f[8].trim().equalsIgnoreCase("true");
                e.tier = f.length > 9 ? Math.max(0, num(f[9])) : 0;
                if (closed || e.type == REALM_CLOSED_TYPE) {
                    e.kind = Kind.REALM_CLOSED;
                    e.name = "Realm closed";
                } else if (eventTypes.containsKey(e.type) && !"Nexus".equalsIgnoreCase(e.realm)) {
                    e.kind = Kind.EVENT;
                    e.name = tidy(eventTypes.get(e.type));
                } else if (dungeonTypes.containsKey(e.type)) {
                    e.kind = Kind.KEY_POP;
                    e.name = dungeonTypes.get(e.type);
                } else if (eventTypes.containsKey(e.type)) {
                    e.kind = Kind.EVENT;
                    e.name = tidy(eventTypes.get(e.type));
                } else {
                    e.kind = Kind.OTHER;
                    e.name = "Event #" + e.type;
                }
                if (e.id.isEmpty()) e.id = line.trim();
                out.add(e);
            } catch (Exception ignored) {
                // one odd line doesn't stop the rest
            }
        }
        return out;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /** "23:26" (UTC) as a time today, or yesterday if that would be in the future. */
    static long timeToday(String hhmm, long nowMs) {
        try {
            LocalTime t = LocalTime.parse(hhmm.length() == 4 ? "0" + hhmm : hhmm);
            LocalDate today = java.time.Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate();
            long ms = today.atTime(t).toInstant(ZoneOffset.UTC).toEpochMilli();
            if (ms > nowMs + 5 * 60_000L) ms -= 24 * 3600_000L;
            return ms;
        } catch (Exception e) {
            return nowMs;
        }
    }

    /** Spawner names into event names: "Snowball Stash Realm Spawner Beach" -> "Snowball Stash", "Lord of the Lol 0" fixed. */
    static String tidy(String name) {
        String n = name.trim();
        if (n.equalsIgnoreCase("Lord of the Lol 0")) return "Lord of the Lost Lands";
        n = n.replaceFirst("^New ", "");
        n = n.replaceFirst("(?i)\\s*(Realm )?(Spawner|Initiator|Event Initiator)\\b.*$", "");
        n = n.replaceFirst("(?i)\\s+Realm$", "");
        n = n.trim();
        return n.isEmpty() ? name.trim() : n;
    }

    // ---------------------------------------------------------------- name lists

    private static void loadTypes() {
        readTypes(EVENT_TYPES_URL, "realmstock-event-types.txt", eventTypes, true);
        readTypes(DUNGEON_TYPES_URL, "realmstock-dungeon-types.txt", dungeonTypes, false);
    }

    /** From realm.wiki, else the copy kept from last time, else the copy inside the app. */
    private static void readTypes(String url, String file, Map<Integer, String> into, boolean threeFields) {
        String text = null;
        try {
            text = get(url);
            java.nio.file.Files.write(java.nio.file.Paths.get(file), text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            try {
                text = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file)), StandardCharsets.UTF_8);
            } catch (Exception e2) {
                try (InputStream in = RealmStockFeed.class.getResourceAsStream(file)) {
                    if (in != null) text = read(in);
                } catch (Exception ignored) {
                }
            }
        }
        if (text == null) return;
        Map<Integer, String> m = new HashMap<>();
        for (String line : text.split("\n")) {
            String[] f = line.trim().split("\\|");
            if (f.length < 2) continue;
            try {
                m.put(Integer.parseInt(f[0].trim()), f[f.length - 1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        synchronized (RealmStockFeed.class) {
            if (!m.isEmpty()) {
                into.clear();
                into.putAll(m);
            }
        }
    }

    static synchronized void loadTypesFrom(String events, String dungeons) {
        for (String[] pair : new String[][]{{events, "e"}, {dungeons, "d"}}) {
            Map<Integer, String> into = pair[1].equals("e") ? eventTypes : dungeonTypes;
            for (String line : pair[0].split("\n")) {
                String[] f = line.trim().split("\\|");
                if (f.length < 2) continue;
                try {
                    into.put(Integer.parseInt(f[0].trim()), f[f.length - 1].trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "WhiteBagPup/1.0 (event tracker)");
        c.setRequestProperty("Accept", "application/json, text/plain, */*");
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        try (InputStream in = c.getInputStream()) {
            return read(in);
        }
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** "12 players · realm 63%" (whatever the feed knows). */
    static String details(Event e) {
        List<String> parts = new ArrayList<>();
        if (e.players >= 0) parts.add(e.players + (e.players == 1 ? " player" : " players"));
        if (e.kind != Kind.KEY_POP) {
            if (e.score >= 0) parts.add("realm " + e.score + "%");
        }
        return String.join("  ·  ", parts);
    }

    static String headline(Event e) {
        switch (e.kind) {
            case KEY_POP:
                return e.name + " key popped";
            case REALM_CLOSED:
                return "Realm closed: " + e.realm;
            default:
                return e.name;
        }
    }

    static String where(Event e) {
        String s = e.server == null ? "" : e.server;
        if (e.kind == Kind.KEY_POP) return "Nexus, " + s;
        return (e.realm == null || e.realm.isEmpty() ? "" : e.realm + ", ") + s;
    }

    static {
        // the copies inside the app, until the live lists load
        try (InputStream a = RealmStockFeed.class.getResourceAsStream("realmstock-event-types.txt");
             InputStream b = RealmStockFeed.class.getResourceAsStream("realmstock-dungeon-types.txt")) {
            if (a != null && b != null) loadTypesFrom(read(a), read(b));
        } catch (Exception ignored) {
        }
    }
}
