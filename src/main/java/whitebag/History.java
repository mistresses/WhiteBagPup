package whitebag;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fame and DPS that outlive the session (for the daily / weekly recaps), kept next to the app:
 * fame-log.txt (one line per visit to an area) and dps-log.txt (one line per boss you fought).
 */
final class History {
    private History() {
    }

    // ---------------------------------------------------------------- fame

    static final class Fame {
        long start, end;       // Unix seconds
        String account = "", character = "", area = "";
        /** The character's class (Wizard, Priest...), "" for lines saved before 1.3. */
        String cls = "";
        int gained;

        String line() {
            return start + "|" + end + "|" + clean(account) + "|" + clean(character) + "|" + clean(area) + "|" + gained + "|" + clean(cls);
        }

        static Fame parse(String l) {
            String[] f = l.split("\\|", -1);
            if (f.length < 6) return null;
            try {
                Fame x = new Fame();
                x.start = Long.parseLong(f[0]);
                x.end = Long.parseLong(f[1]);
                x.account = f[2];
                x.character = DpsTracker.cleanName(f[3]);
                x.area = f[4];
                x.gained = Integer.parseInt(f[5]);
                if (f.length > 6) x.cls = f[6];
                return x;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static final String FAME_FILE = "fame-log.txt";
    private static List<Fame> fame;

    static synchronized List<Fame> fame() {
        if (fame == null) {
            fame = new ArrayList<>();
            for (String l : read(FAME_FILE)) {
                Fame f = Fame.parse(l);
                if (f != null) fame.add(f);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(fame));
    }

    /** One finished visit (also the time you played there). */
    static synchronized void addFame(String account, String character, String cls, String area, long startMs, long endMs, int gained) {
        if (endMs - startMs < 5_000 && gained <= 0) return;
        fame();
        Fame f = new Fame();
        f.start = startMs / 1000;
        f.end = endMs / 1000;
        f.account = account == null ? "" : account;
        f.character = character == null ? "" : character;
        f.area = area == null ? "" : area;
        f.cls = cls == null ? "" : cls;
        f.gained = Math.max(0, gained);
        fame.add(f);
        append(FAME_FILE, f.line());
    }

    // ---------------------------------------------------------------- play time

    /** One stretch of time in game (play-log.txt: start|end|account|class, Unix seconds). */
    static final class Play {
        long start, end;
        String account = "", cls = "";

        String line() {
            return start + "|" + end + "|" + clean(account) + "|" + clean(cls);
        }

        static Play parse(String l) {
            String[] f = l.split("\\|", -1);
            if (f.length < 4) return null;
            try {
                Play x = new Play();
                x.start = Long.parseLong(f[0]);
                x.end = Long.parseLong(f[1]);
                x.account = f[2];
                x.cls = f[3];
                return x.end > x.start ? x : null;
            } catch (Exception e) {
                return null;
            }
        }

        /** Seconds of this stretch inside [from, to). */
        long within(long from, long to) {
            return Math.max(0, Math.min(end, to) - Math.max(start, from));
        }
    }

    private static final String PLAY_FILE = "play-log.txt";
    private static List<Play> play;

    static synchronized List<Play> play() {
        if (play == null) {
            play = new ArrayList<>();
            for (String l : read(PLAY_FILE)) {
                Play x = Play.parse(l);
                if (x != null) play.add(x);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(play));
    }

    static synchronized void addPlay(String account, String cls, long startMs, long endMs) {
        if (endMs - startMs < 1000) return;
        play();
        Play x = new Play();
        x.start = startMs / 1000;
        x.end = endMs / 1000;
        x.account = account == null ? "" : account;
        x.cls = cls == null ? "" : cls;
        play.add(x);
        append(PLAY_FILE, x.line());
    }

    // ---------------------------------------------------------------- exaltations

    /** A class's exaltation progress at a moment (exalt-log.txt: at|account|class type|8 stats). */
    static final class Exalt {
        long at;
        String account = "";
        int cls;
        final int[] v = new int[8];

        int total() {
            int t = 0;
            for (int x : v) t += Math.min(75, Math.max(0, x));
            return t;
        }
    }

    private static final String EXALT_FILE = "exalt-log.txt";
    private static List<Exalt> exalts;

    static synchronized List<Exalt> exalts() {
        if (exalts == null) {
            exalts = new ArrayList<>();
            for (String l : read(EXALT_FILE)) {
                String[] f = l.split("\\|", -1);
                if (f.length < 11) continue;
                try {
                    Exalt e = new Exalt();
                    e.at = Long.parseLong(f[0]);
                    e.account = f[1];
                    e.cls = Integer.parseInt(f[2]);
                    for (int k = 0; k < 8; k++) e.v[k] = Integer.parseInt(f[3 + k]);
                    exalts.add(e);
                } catch (Exception ignored) {
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(exalts));
    }

    /** Saves a class's exaltation progress if it changed since last time. */
    static synchronized void addExalt(String account, int cls, int[] v) {
        if (account == null || account.isEmpty() || v == null || v.length < 8) return;
        exalts();
        for (int i = exalts.size() - 1; i >= 0; i--) {
            Exalt e = exalts.get(i);
            if (e.account.equals(account) && e.cls == cls) {
                if (java.util.Arrays.equals(e.v, java.util.Arrays.copyOf(v, 8))) return;
                break;
            }
        }
        Exalt e = new Exalt();
        e.at = System.currentTimeMillis() / 1000;
        e.account = account;
        e.cls = cls;
        System.arraycopy(v, 0, e.v, 0, 8);
        exalts.add(e);
        StringBuilder sb = new StringBuilder().append(e.at).append('|').append(clean(account)).append('|').append(cls);
        for (int x : e.v) sb.append('|').append(x);
        append(EXALT_FILE, sb.toString());
    }

    // ---------------------------------------------------------------- names

    /** Each account's in-game name (names.txt: account|name), so recaps can say who played. */
    private static final String NAMES_FILE = "names.txt";
    private static java.util.Map<String, String> names;

    private static java.util.Map<String, String> names() {
        if (names == null) {
            names = new java.util.LinkedHashMap<>();
            for (String l : read(NAMES_FILE)) {
                String[] f = l.split("\\|", -1);
                if (f.length >= 2 && !f[0].isEmpty() && !f[1].isEmpty()) names.put(f[0], f[1]);
            }
        }
        return names;
    }

    /** The account's in-game name, or "". */
    static synchronized String nameOf(String account) {
        String n = names().get(account == null ? "" : account);
        return n == null ? "" : DpsTracker.cleanName(n); // older names.txt lines can have the game's ",a19d,fe3" on the end
    }

    static synchronized void rememberName(String account, String name) {
        if (account == null || account.isEmpty() || name == null || name.trim().isEmpty()) return;
        name = clean(DpsTracker.cleanName(name)); // the game sends "Name,a19d,fe3": just the name
        if (name.equals(names().get(account))) return;
        names.put(account, name);
        List<String> lines = new ArrayList<>();
        for (java.util.Map.Entry<String, String> e : names.entrySet()) lines.add(clean(e.getKey()) + "|" + e.getValue());
        try {
            Files.write(Paths.get(NAMES_FILE), lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- dps

    static final class Dps {
        long at;               // Unix seconds
        String account = "", map = "", boss = "";
        long damage;
        double share;          // your % of the boss's damage
        int rank, players;
        /** Whether it died (lines from before 1.8 don't say, and count as kills). */
        boolean killed = true;
        /** BOSS, MINIBOSS or OTHER (a big enemy); "" for lines from before 1.8. */
        String kind = "";
        /** How long the fight took (0 if unknown). */
        double seconds;

        String line() {
            return at + "|" + clean(account) + "|" + clean(map) + "|" + clean(boss) + "|" + damage + "|"
                    + String.format(java.util.Locale.ROOT, "%.1f", share) + "|" + rank + "|" + players
                    + "|" + (killed ? 1 : 0) + "|" + kind + "|" + String.format(java.util.Locale.ROOT, "%.1f", seconds);
        }

        static Dps parse(String l) {
            String[] f = l.split("\\|", -1);
            if (f.length < 8) return null;
            try {
                Dps d = new Dps();
                d.at = Long.parseLong(f[0]);
                d.account = f[1];
                d.map = f[2];
                d.boss = f[3];
                d.damage = Long.parseLong(f[4]);
                d.share = Double.parseDouble(f[5]);
                d.rank = Integer.parseInt(f[6]);
                d.players = Integer.parseInt(f[7]);
                if (f.length > 10) {
                    d.killed = f[8].equals("1");
                    d.kind = f[9];
                    d.seconds = Double.parseDouble(f[10]);
                }
                return d;
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static final String DPS_FILE = "dps-log.txt";
    private static List<Dps> dps;

    static synchronized List<Dps> dps() {
        if (dps == null) {
            dps = new ArrayList<>();
            for (String l : read(DPS_FILE)) {
                Dps d = Dps.parse(l);
                if (d != null) dps.add(d);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(dps));
    }

    /** Saves your line on each boss board of a finished run. */
    static synchronized void addRun(DpsTracker.Run run, List<DpsTracker.Board> boards, String account) {
        dps();
        for (DpsTracker.Board b : boards) {
            if (b.kind == DpsTracker.Kind.WHOLE_RUN || b.savedEarly) continue;
            for (int i = 0; i < b.rows.size(); i++) {
                DpsTracker.Row r = b.rows.get(i);
                if (!r.you) continue;
                Dps d = new Dps();
                d.at = (b.endMs > 0 ? b.endMs : run.startMs) / 1000;
                d.account = account == null ? "" : account;
                d.map = run.mapName;
                d.boss = b.title;
                d.damage = r.damage;
                d.share = r.percent;
                d.rank = i + 1;
                d.players = b.rows.size();
                d.killed = b.killed;
                d.kind = b.kind.name();
                d.seconds = b.seconds;
                dps.add(d);
                append(DPS_FILE, d.line());
            }
        }
    }

    // ---------------------------------------------------------------- files

    private static String clean(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ');
    }

    private static List<String> read(String file) {
        try {
            return Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static void append(String file, String line) {
        try {
            Files.write(Paths.get(file), Collections.singletonList(line), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
