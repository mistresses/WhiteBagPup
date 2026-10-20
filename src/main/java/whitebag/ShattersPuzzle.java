package whitebag;

import assets.IdToAsset;
import packets.Packet;
import packets.PacketType;
import packets.data.ObjectData;
import packets.data.ObjectStatusData;
import packets.data.StatData;
import packets.incoming.MapInfoPacket;
import packets.incoming.NewTickPacket;
import packets.incoming.UpdatePacket;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Shatters' Puzzle Room: nine towers in a 3x3 grid, each red or green. Shooting a tower flips it and the towers
 * straight above, below, left and right of it; the door opens when all nine are green ("Lights Out").
 * <p>
 * The solver works on any board. The pup also tries to read the board from the game while you're in The Shatters
 * (towers it sees in a 3x3 grid, red or green by their name); everything it notices about the towers goes in
 * puzzle-debug.txt, so the reading can be fixed if the game names them differently.
 */
final class ShattersPuzzle {

    // ---------------------------------------------------------------- solving

    /** Which towers flip when you shoot tower i (0..8, left to right, top to bottom). */
    static int effect(int i) {
        int r = i / 3, c = i % 3, m = 1 << i;
        if (r > 0) m |= 1 << (i - 3);
        if (r < 2) m |= 1 << (i + 3);
        if (c > 0) m |= 1 << (i - 1);
        if (c < 2) m |= 1 << (i + 1);
        return m;
    }

    /**
     * The towers to shoot (bit i = tower i) to turn the board (bit set = green) all green, using as few shots as
     * possible. Every 3x3 board has exactly one answer (shooting a tower twice undoes it).
     */
    static int solve(int green) {
        int want = ~green & 0x1FF; // the towers that need to change
        int best = -1;
        for (int shots = 0; shots < 512; shots++) {
            int flip = 0;
            for (int i = 0; i < 9; i++) if ((shots >> i & 1) != 0) flip ^= effect(i);
            if (flip == want && (best < 0 || Integer.bitCount(shots) < Integer.bitCount(best))) best = shots;
        }
        return best;
    }

    static int shoot(int green, int tower) {
        return green ^ effect(tower);
    }

    private static final String[] PLACES = {"top left", "top middle", "top right", "middle left", "centre", "middle right",
            "bottom left", "bottom middle", "bottom right"};

    static String place(int i) {
        return PLACES[i];
    }

    // ---------------------------------------------------------------- the towers' pictures from the game files

    private static volatile int greenType = -1, redType = -1;
    private static volatile boolean searched;
    private static final String TYPES_FILE = "puzzle-towers.txt";

    static {
        try {
            for (String l : Files.readAllLines(Paths.get(TYPES_FILE), StandardCharsets.UTF_8)) {
                String[] f = l.split("=", 2);
                if (f.length < 2) continue;
                if (f[0].trim().equals("green")) greenType = Integer.parseInt(f[1].trim());
                if (f[0].trim().equals("red")) redType = Integer.parseInt(f[1].trim());
            }
        } catch (Exception ignored) {
        }
    }

    /** The game's object type for a green (or red) tower, or -1 if not known (yet). */
    static int towerType(boolean green) {
        if ((greenType < 0 || redType < 0) && !searched && LootImage.gameSpritesReady) search();
        return green ? greenType : redType;
    }

    /** Looks through the game files' objects for the puzzle towers, by name. */
    private static synchronized void search() {
        if (searched) return;
        searched = true;
        try {
            java.lang.reflect.Field f = IdToAsset.class.getDeclaredField("objectID");
            f.setAccessible(true);
            Map<?, ?> all = (Map<?, ?>) f.get(null);
            int g = -1, r = -1, gs = 0, rs = 0;
            for (Object k : new ArrayList<>(all.keySet())) {
                int type = (Integer) k;
                String name = nameOf(type);
                String n = name.toLowerCase(Locale.ROOT);
                if (!candidate(name)) continue;
                // the best match: a "puzzle" tower, then a Shatters one; other dungeons' coloured towers don't count
                int score = (n.contains("puzzle") ? 2 : 0) + (n.contains("shatter") ? 1 : 0);
                if (score == 0) continue;
                Boolean colour = colourOf(name);
                if (colour == null) continue;
                if (colour && score > gs) { g = type; gs = score; }
                if (!colour && score > rs) { r = type; rs = score; }
            }
            if (greenType < 0 && g >= 0) greenType = g;
            if (redType < 0 && r >= 0) redType = r;
            System.out.println("Puzzle: tower pictures " + (greenType >= 0 && redType >= 0 ? "found (" + greenType + ", " + redType + ")" : "not found in the game files"));
        } catch (Throwable e) {
            System.out.println("Puzzle: couldn't look for the tower pictures: " + e);
        }
    }

    /** Remembers the tower types the pup saw in game (so the pictures are right from then on). */
    private static void learn(int type, boolean green) {
        if ((green ? greenType : redType) == type) return;
        if (green) greenType = type;
        else redType = type;
        try {
            Files.write(Paths.get(TYPES_FILE), java.util.Arrays.asList("green=" + greenType, "red=" + redType), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- reading it from the game (one per account)

    interface Listener {
        void boardSeen(int green);
    }

    private static final class Tower {
        final int id;
        int type;
        float x, y;
        Boolean green;

        Tower(int id) {
            this.id = id;
        }
    }

    private final Listener listener;
    private final Map<Integer, Tower> towers = new HashMap<>();
    private final Set<Integer> logged = new HashSet<>();
    private boolean inShatters;
    private int lastBoard = -1;

    ShattersPuzzle(Listener listener) {
        this.listener = listener;
    }

    void register(Bus bus) {
        bus.register(PacketType.MAPINFO, this::onMapInfo);
        bus.register(PacketType.UPDATE, this::onUpdate);
        bus.register(PacketType.NEWTICK, this::onNewTick);
    }

    private synchronized void onMapInfo(Packet packet) {
        MapInfoPacket p = (MapInfoPacket) packet;
        String map = BagTracker.cleanName(p.name, p.displayName);
        inShatters = map.toLowerCase(Locale.ROOT).contains("shatters");
        towers.clear();
        lastBoard = -1;
    }

    /** True for an object that looks like a puzzle tower. */
    private static boolean candidate(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("puzzle") || (n.contains("tower") && !n.contains("spawner") && !n.contains("power"));
    }

    /** Green / red from the tower's name, or null if the name doesn't say. */
    private static Boolean colourOf(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.matches(".*\\b(green|on|lit|active|activated|solved|open)\\b.*")) return Boolean.TRUE;
        if (n.matches(".*\\b(red|off|unlit|inactive|deactivated|closed)\\b.*")) return Boolean.FALSE;
        return null;
    }

    private static String nameOf(int type) {
        try {
            String a = IdToAsset.objectName(type), b = IdToAsset.getObjectIdName(type);
            if (b != null && !b.isEmpty() && (a == null || !a.equals(b))) return (a == null ? "" : a + " / ") + b;
            return a == null ? "" : a;
        } catch (Throwable e) {
            return "";
        }
    }

    private synchronized void onUpdate(Packet packet) {
        if (!inShatters) return;
        UpdatePacket p = (UpdatePacket) packet;
        boolean changed = false;
        if (p.newObjects != null) {
            for (ObjectData o : p.newObjects) {
                if (o.status == null) continue;
                String name = nameOf(o.objectType);
                if (name.isEmpty() || !candidate(name)) continue;
                Tower t = new Tower(o.status.objectId);
                t.type = o.objectType;
                if (o.status.pos != null) {
                    t.x = o.status.pos.x;
                    t.y = o.status.pos.y;
                }
                t.green = colourOf(name);
                if (t.green != null) learn(t.type, t.green);
                towers.put(t.id, t);
                changed = true;
                note("seen", t, name, o.status.stats);
            }
        }
        if (p.drops != null) for (int id : p.drops) if (towers.remove(id) != null) changed = true;
        if (changed) check();
    }

    private synchronized void onNewTick(Packet packet) {
        if (!inShatters || towers.isEmpty()) return;
        NewTickPacket p = (NewTickPacket) packet;
        if (p.status == null) return;
        for (ObjectStatusData s : p.status) {
            Tower t = towers.get(s.objectId);
            if (t != null && s.stats != null && s.stats.length > 0) note("changed", t, nameOf(t.type), s.stats);
        }
    }

    /** Looks for nine towers in a 3x3 grid, and reports the board if it can tell their colours. */
    private void check() {
        List<Tower> list = new ArrayList<>(towers.values());
        if (list.size() < 9) return;
        // the nine towers closest together: try each tower as the centre
        for (Tower centre : list) {
            List<Tower> near = new ArrayList<>(list);
            near.sort((a, b) -> Double.compare(dist(a, centre), dist(b, centre)));
            List<Tower> nine = near.subList(0, 9);
            int[] cell = grid(nine);
            if (cell == null) continue;
            int green = 0;
            for (int k = 0; k < 9; k++) {
                Boolean g = nine.get(k).green;
                if (g == null) return; // can't tell the colours from the names (see puzzle-debug.txt)
                if (g) green |= 1 << cell[k];
            }
            if (green != lastBoard) {
                lastBoard = green;
                listener.boardSeen(green);
            }
            return;
        }
    }

    private static double dist(Tower a, Tower b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    /** Each tower's place (0..8) if the nine sit on 3 columns and 3 rows, else null. */
    private static int[] grid(List<Tower> nine) {
        List<Float> xs = clusters(nine, true), ys = clusters(nine, false);
        if (xs.size() != 3 || ys.size() != 3) return null;
        int[] out = new int[9];
        boolean[] used = new boolean[9];
        for (int k = 0; k < 9; k++) {
            Tower t = nine.get(k);
            int c = nearest(xs, t.x), r = nearest(ys, t.y);
            int i = r * 3 + c;
            if (used[i]) return null;
            used[i] = true;
            out[k] = i;
        }
        return out;
    }

    private static List<Float> clusters(List<Tower> ts, boolean x) {
        List<Float> v = new ArrayList<>();
        for (Tower t : ts) v.add(x ? t.x : t.y);
        Collections.sort(v);
        List<Float> centres = new ArrayList<>();
        float start = v.get(0), sum = 0;
        int n = 0;
        for (float f : v) {
            if (f - start > 1.2f) {
                centres.add(sum / n);
                start = f;
                sum = 0;
                n = 0;
            }
            sum += f;
            n++;
        }
        centres.add(sum / n);
        return centres;
    }

    private static int nearest(List<Float> centres, float v) {
        int best = 0;
        for (int i = 1; i < centres.size(); i++) if (Math.abs(centres.get(i) - v) < Math.abs(centres.get(best) - v)) best = i;
        return best;
    }

    private final Map<Integer, String> lastStats = new HashMap<>();
    private int lines;

    /** Notes a tower (once per kind) and each change to its stats, in puzzle-debug.txt (at most 400 lines a session). */
    private void note(String what, Tower t, String name, StatData[] stats) {
        if (what.equals("seen") && !logged.add(t.type)) return;
        StringBuilder st = new StringBuilder();
        if (stats != null) for (StatData s : stats) {
            st.append(' ').append(s.statTypeNum).append('=').append(s.stringStatValue != null && !s.stringStatValue.isEmpty() ? s.stringStatValue : String.valueOf(s.statValue));
        }
        if (what.equals("changed") && st.toString().equals(lastStats.get(t.id))) return;
        lastStats.put(t.id, st.toString());
        if (++lines > 400) return;
        String line = new java.text.SimpleDateFormat("d MMM HH:mm:ss", Locale.ROOT).format(new java.util.Date()) + "  " + what
                + " type=" + t.type + " \"" + name + "\" id=" + t.id + String.format(Locale.ROOT, " at %.1f,%.1f", t.x, t.y) + " stats:" + st;
        try {
            Files.write(Paths.get("puzzle-debug.txt"), Collections.singletonList(line), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
