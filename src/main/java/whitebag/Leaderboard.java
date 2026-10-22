package whitebag;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The friends leaderboard: sends this account's totals (per day for the last week, plus a few all-time numbers) to the
 * Femcore bot, which keeps a leaderboard message in Discord. It goes to the same bot as the recap link, with the same
 * key. Only your in-game name and counts are sent: no item names, webhooks or anything else.
 */
final class Leaderboard {
    private Leaderboard() {
    }

    /** The bot address for stats, made from the bot recap link (…/recap?key=… becomes …/pup/stats?key=…), or null. */
    static String statsUrl(String recapUrl) {
        if (!Recap.isBotUrl(recapUrl)) return null;
        return recapUrl.trim().replaceFirst("(?i)/recap\\?", "/pup/stats?");
    }

    /** A stable id for the account that doesn't give its real id away. */
    static String idOf(String account) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(("whitebagpup:" + account).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(account.hashCode());
        }
    }

    private static String day(long unixSeconds) {
        return Instant.ofEpochSecond(unixSeconds).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    /** One day's numbers. */
    static final class Day {
        long whites, bags, runs, bosses, events, fame, seconds;
    }

    /** The JSON the bot gets for this account, or null if there's nothing to send yet. */
    static String payload(String account, Predicate<String> mine) {
        if (account == null || account.isEmpty()) return null;
        String name = History.nameOf(account);
        if (name == null || name.trim().isEmpty()) return null;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<String, Day> days = new LinkedHashMap<>();
        for (int i = 8; i >= 0; i--) days.put(today.minusDays(i).toString(), new Day());
        long firstDay = today.minusDays(8).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        long allWhites = 0, allRuns = 0;
        List<LootStats.Entry> bags = new ArrayList<>();
        for (LootStats.Entry e : LootStats.all()) {
            if (!mine.test(e.account)) continue;
            bags.add(e);
            boolean white = LootStats.colour(e.type) == BagType.WHITE;
            if (white) allWhites++;
            Day d = e.at >= firstDay ? days.get(day(e.at)) : null;
            if (d != null) {
                d.bags++;
                if (white) d.whites++;
            }
        }
        for (RunLog.Run r : RunLog.all()) {
            if (!mine.test(r.account) || RunLog.isRealm(r.map)) continue;
            allRuns++;
            Day d = r.at >= firstDay ? days.get(day(r.at)) : null;
            if (d != null) d.runs++;
        }
        List<History.Dps> kills = new ArrayList<>();
        for (History.Dps k : History.dps()) if (mine.test(k.account) && k.killed && k.at >= firstDay) kills.add(k);
        for (History.Dps k : KillsPage.oneEach(kills)) {
            Day d = days.get(day(k.at));
            if (d == null) continue;
            boolean event = KillsPage.isEvent(k);
            if (event) d.events++;
            if (event || k.kind.isEmpty() || k.kind.equals("BOSS")) d.bosses++;
        }
        for (History.Fame f : History.fame()) {
            Day d = mine.test(f.account) && f.end >= firstDay ? days.get(day(f.end)) : null;
            if (d != null) d.fame += f.gained;
        }
        for (History.Play p : History.play()) {
            Day d = mine.test(p.account) && p.end >= firstDay ? days.get(day(p.start)) : null;
            if (d != null) d.seconds += Math.max(0, p.end - p.start);
        }
        int badges = Badges.unlocked(account).size();
        int collection = LootBook.found(bags).size();

        StringBuilder j = new StringBuilder("{");
        j.append("\"id\":").append(DiscordWebhook.str(idOf(account)));
        j.append(",\"name\":").append(DiscordWebhook.str(name.trim()));
        j.append(",\"version\":").append(DiscordWebhook.str(UpdateCheck.VERSION));
        j.append(",\"days\":{");
        boolean first = true;
        for (Map.Entry<String, Day> e : days.entrySet()) {
            Day d = e.getValue();
            if (!first) j.append(',');
            first = false;
            j.append('"').append(e.getKey()).append("\":{\"whites\":").append(d.whites).append(",\"bags\":").append(d.bags)
                    .append(",\"runs\":").append(d.runs).append(",\"bosses\":").append(d.bosses).append(",\"events\":").append(d.events)
                    .append(",\"fame\":").append(d.fame).append(",\"minutes\":").append(d.seconds / 60).append('}');
        }
        j.append("},\"all\":{\"whites\":").append(allWhites).append(",\"runs\":").append(allRuns)
                .append(",\"badges\":").append(badges).append(",\"collection\":").append(collection).append("}}");
        return j.toString();
    }

    private static final Map<String, String> lastSent = new java.util.HashMap<>();
    private static final Map<String, Long> lastAt = new java.util.HashMap<>();

    /**
     * Sends this account's numbers if they changed (or an hour has passed). Returns a short problem description, or
     * null if all went well (or nothing needed sending).
     */
    static synchronized String send(String recapUrl, String account, Predicate<String> mine, boolean force) {
        String url = statsUrl(recapUrl);
        if (url == null) return "the recap link isn't a bot link";
        String body = payload(account, mine);
        if (body == null) return null;
        long now = System.currentTimeMillis();
        if (!force && body.equals(lastSent.get(account)) && now - lastAt.getOrDefault(account, 0L) < 3600_000) return null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("User-Agent", "WhiteBagPup/" + UpdateCheck.VERSION);
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(data.length);
            try (OutputStream o = c.getOutputStream()) {
                o.write(data);
            }
            int code = c.getResponseCode();
            if (code == 404) return "the bot doesn't have the leaderboard yet (update the Femcore bot)";
            if (code == 401) return "the bot didn't accept the recap key";
            if (code / 100 != 2) return "the bot answered HTTP " + code;
            lastSent.put(account, body);
            lastAt.put(account, now);
            return null;
        } catch (Exception e) {
            return "couldn't reach the bot (" + e.getMessage() + ")";
        }
    }
}
