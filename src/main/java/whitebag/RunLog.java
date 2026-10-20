package whitebag;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every dungeon run (each visit to a dungeon or the realm), kept in runs-log.txt next to the app,
 * so the Loot tab can work out drop rates: "1 white bag per 12 Shatters runs".
 */
final class RunLog {
    private RunLog() {
    }

    static final class Run {
        long at;        // Unix seconds, when you entered
        int seconds;    // how long you stayed
        String map = "";
        String account = "";

        String line() {
            return at + "|" + seconds + "|" + map.replace('|', '/') + "|" + account.replace('|', '/');
        }

        static Run parse(String l) {
            String[] f = l.split("\\|", -1);
            if (f.length < 3) return null;
            try {
                Run r = new Run();
                r.at = Long.parseLong(f[0]);
                r.seconds = Integer.parseInt(f[1]);
                r.map = f[2];
                if (f.length >= 4) r.account = f[3];
                return r;
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** Places that aren't runs. */
    static final Set<String> HUBS = new HashSet<>(Arrays.asList(
            "nexus", "vault", "guild hall", "pet yard", "daily quest room", "daily login room", "cloth bazaar",
            "nexus explanation", "vault explanation", "guild explanation", "kitchen", "exalted kitchen", "random realm",
            "interregnum daily quest room", "secluded thicket", ""));
    private static final int MIN_SECONDS = 20;
    private static final String FILE = "runs-log.txt";
    private static List<Run> runs;

    /** The realm itself (not a dungeon), which dry streaks leave out. */
    static boolean isRealm(String map) {
        String m = map == null ? "" : map.trim().toLowerCase(java.util.Locale.ROOT);
        return m.contains("realm of the mad god") || m.equals("realm") || m.equals("the realm") || m.equals("rotmg");
    }

    static boolean isRun(String map) {
        String m = map == null ? "" : map.trim().toLowerCase(java.util.Locale.ROOT);
        return !HUBS.contains(m) && !m.contains("guild hall");
    }

    static synchronized List<Run> all() {
        if (runs == null) {
            runs = new ArrayList<>();
            try {
                for (String l : Files.readAllLines(Paths.get(FILE), StandardCharsets.UTF_8)) {
                    Run r = Run.parse(l);
                    if (r != null) runs.add(r);
                }
            } catch (Exception ignored) {
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(runs));
    }

    /** Notes a finished visit (ignored for hubs and very short visits). */
    static synchronized void add(String map, String account, long enteredMs, long leftMs) {
        if (!isRun(map)) return;
        int secs = (int) ((leftMs - enteredMs) / 1000);
        if (secs < MIN_SECONDS) return;
        all();
        Run r = new Run();
        r.at = enteredMs / 1000;
        r.seconds = secs;
        r.map = map;
        r.account = account == null ? "" : account;
        runs.add(r);
        try {
            Files.write(Paths.get(FILE), Collections.singletonList(r.line()), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
