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
        int gained;

        String line() {
            return start + "|" + end + "|" + clean(account) + "|" + clean(character) + "|" + clean(area) + "|" + gained;
        }

        static Fame parse(String l) {
            String[] f = l.split("\\|", -1);
            if (f.length < 6) return null;
            try {
                Fame x = new Fame();
                x.start = Long.parseLong(f[0]);
                x.end = Long.parseLong(f[1]);
                x.account = f[2];
                x.character = f[3];
                x.area = f[4];
                x.gained = Integer.parseInt(f[5]);
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
    static synchronized void addFame(String account, String character, String area, long startMs, long endMs, int gained) {
        if (endMs - startMs < 5_000 && gained <= 0) return;
        fame();
        Fame f = new Fame();
        f.start = startMs / 1000;
        f.end = endMs / 1000;
        f.account = account == null ? "" : account;
        f.character = character == null ? "" : character;
        f.area = area == null ? "" : area;
        f.gained = Math.max(0, gained);
        fame.add(f);
        append(FAME_FILE, f.line());
    }

    // ---------------------------------------------------------------- dps

    static final class Dps {
        long at;               // Unix seconds
        String account = "", map = "", boss = "";
        long damage;
        double share;          // your % of the boss's damage
        int rank, players;

        String line() {
            return at + "|" + clean(account) + "|" + clean(map) + "|" + clean(boss) + "|" + damage + "|"
                    + String.format(java.util.Locale.ROOT, "%.1f", share) + "|" + rank + "|" + players;
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
            if (b.kind == DpsTracker.Kind.WHOLE_RUN) continue;
            for (int i = 0; i < b.rows.size(); i++) {
                DpsTracker.Row r = b.rows.get(i);
                if (!r.you) continue;
                Dps d = new Dps();
                d.at = run.startMs / 1000;
                d.account = account == null ? "" : account;
                d.map = run.mapName;
                d.boss = b.title;
                d.damage = r.damage;
                d.share = r.percent;
                d.rank = i + 1;
                d.players = b.rows.size();
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
