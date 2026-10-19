package whitebag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dry streaks: how many runs in a row went by without a white bag, per dungeon and overall, from the run log and
 * the loot log. A white bag counts for the run it dropped in (it dropped between entering and leaving).
 */
final class DryStreak {
    private DryStreak() {
    }

    static final class Streak {
        final String map;
        /** Runs since the last white bag (the run in progress isn't counted until you leave). */
        int current;
        /** The longest stretch without a white bag the pup has seen. */
        int longest;
        int runs, whites;
        /** When the last white bag dropped (Unix seconds), or 0. */
        long lastWhite;
        /** Dry streaks that ended with a white: {when it ended (Unix seconds), how many runs it lasted}. */
        final List<long[]> broken = new ArrayList<>();

        Streak(String map) {
            this.map = map;
        }

        /** True while the current streak is the longest one yet (and long enough to mention). */
        boolean isRecord() {
            return current >= 5 && current >= longest;
        }
    }

    /** Leeway for a white bag logged just after you left (the bag list arrives a moment later). */
    private static final long LATE_SECONDS = 10;

    /** Per dungeon, plus "" for all runs together. Runs and bags should already be one account's (or everyone's). */
    static Map<String, Streak> compute(List<RunLog.Run> runs, List<LootStats.Entry> bags) {
        List<RunLog.Run> rs = new ArrayList<>(runs);
        rs.sort((a, b) -> Long.compare(a.at, b.at));
        List<long[]> whites = new ArrayList<>(); // {at, index of map in names}
        List<String> whiteMaps = new ArrayList<>();
        for (LootStats.Entry e : bags) {
            if (LootStats.colour(e.type) != BagType.WHITE) continue;
            whites.add(new long[]{e.at});
            whiteMaps.add(e.map);
        }
        Map<String, Streak> out = new LinkedHashMap<>();
        Streak all = new Streak("");
        out.put("", all);
        for (RunLog.Run r : rs) out.computeIfAbsent(r.map, Streak::new);
        for (int i = 0; i < whites.size(); i++) {
            Streak s = out.get(whiteMaps.get(i));
            if (s != null) {
                s.whites++;
                s.lastWhite = Math.max(s.lastWhite, whites.get(i)[0]);
            }
            all.whites++;
            all.lastWhite = Math.max(all.lastWhite, whites.get(i)[0]);
        }
        for (RunLog.Run r : rs) {
            Streak s = out.get(r.map);
            boolean white = false;
            long end = r.at + r.seconds + LATE_SECONDS;
            for (int i = 0; i < whites.size(); i++) {
                long at = whites.get(i)[0];
                if (at >= r.at && at <= end && whiteMaps.get(i).equals(r.map)) {
                    white = true;
                    break;
                }
            }
            for (Streak x : new Streak[]{s, all}) {
                x.runs++;
                if (white) {
                    if (x.current > 0) x.broken.add(new long[]{r.at + r.seconds, x.current});
                    x.longest = Math.max(x.longest, x.current);
                    x.current = 0;
                } else {
                    x.current++;
                    x.longest = Math.max(x.longest, x.current);
                }
            }
        }
        // a white in a run that's still going (or wasn't logged) ends the streak too
        long lastRunEnd = rs.isEmpty() ? 0 : rs.get(rs.size() - 1).at + rs.get(rs.size() - 1).seconds + LATE_SECONDS;
        for (Streak s : out.values()) {
            long lastRunHere = 0;
            for (RunLog.Run r : rs) if (s.map.isEmpty() || r.map.equals(s.map)) lastRunHere = r.at + r.seconds + LATE_SECONDS;
            if (s.lastWhite > (s.map.isEmpty() ? lastRunEnd : lastRunHere)) s.current = 0;
        }
        return out;
    }

    /** The dungeons, longest current dry streak first (only ones that have dropped a white or been run 5+ times). */
    static List<Streak> worst(Map<String, Streak> all) {
        List<Streak> out = new ArrayList<>();
        for (Streak s : all.values()) if (!s.map.isEmpty() && s.current > 0 && (s.whites > 0 || s.runs >= 5)) out.add(s);
        out.sort((a, b) -> a.current != b.current ? b.current - a.current : b.longest - a.longest);
        return Collections.unmodifiableList(out);
    }
}
