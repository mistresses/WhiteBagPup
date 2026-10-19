package whitebag;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Daily and weekly recaps posted to their own Discord webhook: bags, best drops, runs, luckiest dungeon,
 * fame, top DPS, trades and time played, one post per game account. Built from the logs next to the app,
 * so only what happened while the app was running counts. If the app was closed when one was due, it's
 * posted the next time the app is open.
 */
final class Recap {
    private Recap() {
    }

    enum Period {
        DAILY("Daily", 1), WEEKLY("Weekly", 7);
        final String label;
        final int days;

        Period(String label, int days) {
            this.label = label;
            this.days = days;
        }
    }

    // ---------------------------------------------------------------- schedule

    /** "off", "daily", "weekly" or "both". */
    static String mode(Properties s) {
        return s.getProperty("recap.mode", "off");
    }

    static int day(Properties s) {
        return clamp(s.getProperty("recap.day", "7"), 1, 7);       // 1 = Monday ... 7 = Sunday
    }

    static int hour(Properties s) {
        return clamp(s.getProperty("recap.hour", "20"), 0, 23);
    }

    private static int clamp(String v, int lo, int hi) {
        try {
            return Math.max(lo, Math.min(hi, Integer.parseInt(v.trim())));
        } catch (Exception e) {
            return lo;
        }
    }

    /** When the latest recap of this kind was due (it covers the day / week before this). */
    static ZonedDateTime lastDue(Period p, Properties s, ZonedDateTime now) {
        ZonedDateTime t = now.withHour(hour(s)).withMinute(0).withSecond(0).withNano(0);
        if (p == Period.DAILY) {
            return t.isAfter(now) ? t.minusDays(1) : t;
        }
        int diff = now.getDayOfWeek().getValue() - day(s);
        t = t.minusDays(diff < 0 ? diff + 7 : diff);
        return t.isAfter(now) ? t.minusDays(7) : t;
    }

    interface Poster {
        void post(String json, Map<String, byte[]> files, String what);
    }

    /**
     * Called every minute: posts any recap that's due and not posted yet. Returns true if the settings changed
     * (so they get saved).
     */
    static boolean tick(Properties s, Poster poster, java.util.function.Consumer<String> log) {
        String m = mode(s);
        if (m.equals("off") || !DiscordWebhook.isValidUrl(s.getProperty("recap.url", ""))) return false;
        boolean changed = false;
        ZonedDateTime now = ZonedDateTime.now();
        for (Period p : Period.values()) {
            if (!(m.equals("both") || m.equals(p.name().toLowerCase(Locale.ROOT)))) continue;
            long due = lastDue(p, s, now).toEpochSecond();
            String key = "recap.last." + p.name().toLowerCase(Locale.ROOT);
            String last = s.getProperty(key);
            if (last == null) {
                // just turned on: the first one goes out at the next due time
                s.setProperty(key, String.valueOf(due));
                changed = true;
                continue;
            }
            long done;
            try {
                done = Long.parseLong(last);
            } catch (NumberFormatException e) {
                done = 0;
            }
            if (done >= due) continue;
            int n = post(p, due, s, poster);
            log.accept(n > 0 ? "*trots off to Discord* posted the " + p.label.toLowerCase(Locale.ROOT) + " recap" + (n > 1 ? "s (" + n + " accounts)" : "") + "!"
                    : "*yawns* nothing to put in the " + p.label.toLowerCase(Locale.ROOT) + " recap this time.");
            s.setProperty(key, String.valueOf(due));
            changed = true;
        }
        return changed;
    }

    /** Posts a recap for the period ending now (the "post now" buttons). Returns how many accounts it covered. */
    static int postNow(Period p, Properties s, Poster poster) {
        return post(p, System.currentTimeMillis() / 1000, s, poster);
    }

    private static int post(Period p, long end, Properties s, Poster poster) {
        long start = end - p.days * 86_400L;
        String legacy = s.getProperty("accounts.legacyOwner", "");
        int n = 0;
        for (String account : accounts(start, end, legacy)) {
            Built b = build(p, start, end, account, legacy);
            if (b == null) continue;
            poster.post(b.json, b.files, p.label.toLowerCase(Locale.ROOT) + " recap");
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- what's in it

    private static boolean in(long t, long start, long end) {
        return t >= start && t < end;
    }

    private static String acct(String a, String legacy) {
        return a == null || a.isEmpty() ? legacy : a;
    }

    /** Every account with something to report in the window. */
    static Set<String> accounts(long start, long end, String legacy) {
        Set<String> out = new LinkedHashSet<>();
        for (LootStats.Entry e : LootStats.all()) if (in(e.at, start, end)) out.add(acct(e.account, legacy));
        for (RunLog.Run r : RunLog.all()) if (in(r.at, start, end)) out.add(acct(r.account, legacy));
        for (History.Fame f : History.fame()) if (in(f.start, start, end)) out.add(acct(f.account, legacy));
        for (History.Dps d : History.dps()) if (in(d.at, start, end)) out.add(acct(d.account, legacy));
        for (TradeLog.Trade t : TradeLog.all()) if (in(t.at, start, end)) out.add(acct(t.account, legacy));
        out.remove("");
        if (out.isEmpty()) {
            // nothing tied to an account (e.g. before accounts were tracked): one recap for everything
            boolean any = false;
            for (LootStats.Entry e : LootStats.all()) any |= in(e.at, start, end);
            for (History.Fame f : History.fame()) any |= in(f.start, start, end);
            if (any) out.add("");
        }
        return out;
    }

    static final class Built {
        String json;
        Map<String, byte[]> files = new LinkedHashMap<>();
    }

    private static final BagType[] COLOURS = {BagType.WHITE, BagType.RED, BagType.ORANGE, BagType.GOLD, BagType.EGG, BagType.BLUE};

    static Built build(Period p, long start, long end, String account, String legacy) {
        // ---- bags
        Map<BagType, Integer> bags = new LinkedHashMap<>();
        for (BagType c : COLOURS) bags.put(c, 0);
        List<ItemInfo> whiteItems = new ArrayList<>();
        int[] rarities = new int[5];
        Map<String, Integer> whitesBy = new LinkedHashMap<>();
        int whites = 0;
        for (LootStats.Entry e : LootStats.all()) {
            if (!in(e.at, start, end) || !acct(e.account, legacy).equals(account)) continue;
            BagType c = LootStats.colour(e.type);
            bags.merge(c, 1, Integer::sum);
            if (c != BagType.WHITE) continue;
            whites++;
            whitesBy.merge(e.map, 1, Integer::sum);
            for (int i = 0; i < e.items.size(); i++) {
                int r = i < e.rarity.size() ? Math.max(0, Math.min(4, e.rarity.get(i))) : 0;
                rarities[r]++;
                ItemInfo info = ItemInfo.of(e.items.get(i)).withEnchant(Enchant.values()[r]);
                if (!info.rarity.isEmpty() && whiteItems.size() < 16) whiteItems.add(info); // UT / ST and the like
            }
        }
        // ---- runs
        Map<String, Integer> runsBy = new LinkedHashMap<>();
        int runs = 0;
        for (RunLog.Run r : RunLog.all()) {
            if (!in(r.at, start, end) || !acct(r.account, legacy).equals(account)) continue;
            runs++;
            runsBy.merge(r.map, 1, Integer::sum);
        }
        // ---- fame and time played
        long fame = 0, playedSecs = 0, bestRate = 0;
        String bestArea = null, name = "";
        for (History.Fame f : History.fame()) {
            if (!in(f.start, start, end) || !acct(f.account, legacy).equals(account)) continue;
            fame += f.gained;
            long secs = Math.max(0, f.end - f.start);
            playedSecs += secs;
            if (!f.character.isEmpty()) name = f.character;
            if (secs >= 300 && f.gained > 0) {
                long rate = Math.round(f.gained / (secs / 3600.0));
                if (rate > bestRate) {
                    bestRate = rate;
                    bestArea = f.area;
                }
            }
        }
        // ---- DPS
        History.Dps top = null;
        int bosses = 0;
        for (History.Dps d : History.dps()) {
            if (!in(d.at, start, end) || !acct(d.account, legacy).equals(account)) continue;
            bosses++;
            if (top == null || d.share > top.share) top = d;
        }
        // ---- trades
        int trades = 0;
        for (TradeLog.Trade t : TradeLog.all()) if (in(t.at, start, end) && acct(t.account, legacy).equals(account)) trades++;

        int bagTotal = 0;
        for (int v : bags.values()) bagTotal += v;
        if (bagTotal == 0 && runs == 0 && fame == 0 && playedSecs < 60 && trades == 0 && bosses == 0) return null;

        // ---- the post
        ZoneId zone = ZoneId.systemDefault();
        LocalDate from = java.time.Instant.ofEpochSecond(start).atZone(zone).toLocalDate();
        LocalDate to = java.time.Instant.ofEpochSecond(end - 1).atZone(zone).toLocalDate();
        DateTimeFormatter d = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        String when = p == Period.DAILY ? to.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH)) : from.format(d) + " to " + to.format(d);
        StringBuilder sb = new StringBuilder();
        List<String> bagParts = new ArrayList<>();
        for (Map.Entry<BagType, Integer> e : bags.entrySet()) if (e.getValue() > 0) bagParts.add(e.getValue() + " " + e.getKey().displayName.toLowerCase(Locale.ROOT));
        String rar = "";
        List<String> rp = new ArrayList<>();
        for (int r = 4; r >= 1; r--) if (rarities[r] > 0) rp.add(rarities[r] + " " + Enchant.values()[r].label);
        if (!rp.isEmpty()) rar = " (white bag items: " + String.join(", ", rp) + ")";
        sb.append("🎒 **Bags:** ").append(bagParts.isEmpty() ? "none" : String.join(", ", bagParts)).append(rar).append("\n");
        if (runs > 0) {
            List<Map.Entry<String, Integer>> rs = new ArrayList<>(runsBy.entrySet());
            rs.sort((a, b) -> b.getValue() - a.getValue());
            List<String> topRuns = new ArrayList<>();
            for (int i = 0; i < Math.min(3, rs.size()); i++) topRuns.add(rs.get(i).getKey() + " ×" + rs.get(i).getValue());
            sb.append("🏰 **Runs:** ").append(runs).append(" (most played: ").append(String.join(", ", topRuns)).append(")\n");
            String luckyMap = null;
            double lucky = Double.MAX_VALUE;
            for (Map.Entry<String, Integer> e : whitesBy.entrySet()) {
                Integer r = runsBy.get(e.getKey());
                if (r == null || r == 0) continue;
                double per = r / (double) e.getValue();
                if (per < lucky) {
                    lucky = per;
                    luckyMap = e.getKey();
                }
            }
            if (luckyMap != null) {
                sb.append("🍀 **Luckiest dungeon:** 1 white per ")
                        .append(lucky == Math.rint(lucky) ? String.valueOf((long) lucky) : String.format(Locale.ROOT, "%.1f", lucky))
                        .append(" ").append(luckyMap).append(lucky == 1 ? " run" : " runs").append("\n");
            }
        }
        if (fame > 0) {
            sb.append("🏆 **Fame:** +").append(String.format(Locale.ROOT, "%,d", fame));
            if (playedSecs >= 600) sb.append(String.format(Locale.ROOT, " (about %,d per hour)", Math.round(fame / (playedSecs / 3600.0))));
            if (bestArea != null) sb.append(", best run ").append(bestArea);
            sb.append("\n");
        }
        if (top != null) {
            sb.append("⚔️ **Top DPS:** ").append(String.format(Locale.ROOT, "%.0f%%", top.share)).append(" on ").append(top.boss)
                    .append(" (#").append(top.rank).append(" of ").append(top.players).append(")\n");
        }
        if (trades > 0) sb.append("🤝 **Trades:** ").append(trades).append("\n");
        if (playedSecs >= 60) sb.append("⏱️ **Played:** ").append(played(playedSecs)).append("\n");
        sb.append("\n").append(pick(whites, runs, rarities));

        Built b = new Built();
        byte[] img = null;
        try {
            if (!whiteItems.isEmpty()) img = LootImage.lootPng(whiteItems);
        } catch (Throwable ignored) {
        }
        if (img != null) b.files.put("loot.png", img);
        String title = "🐶 " + p.label + " recap: " + when + (name.isEmpty() ? "" : " · " + name);
        StringBuilder j = new StringBuilder("{");
        j.append("\"username\":\"White Bag Pup\",\"allowed_mentions\":{\"parse\":[]},\"embeds\":[{");
        j.append("\"title\":").append(DiscordWebhook.str(title)).append(",");
        j.append("\"description\":").append(DiscordWebhook.str(sb.toString().trim())).append(",");
        j.append("\"color\":").append(0xFF9EBB);
        if (img != null) j.append(",\"image\":{\"url\":\"attachment://loot.png\"}");
        j.append("}]}");
        b.json = j.toString();
        return b;
    }

    private static String played(long secs) {
        long h = secs / 3600, m = (secs % 3600) / 60;
        return h > 0 ? h + " h " + m + " m" : m + " m";
    }

    private static String pick(int whites, int runs, int[] rarities) {
        if (rarities[4] > 0) return "*howls at the moon* a DIVINE item!! best pup ever ✨";
        if (whites >= 5) return "*happy howl* " + whites + " white bags, what a haul!! 🐾";
        if (whites >= 1) return "*wags tail* " + (whites == 1 ? "a white bag" : whites + " white bags") + " fetched~ good pup! 🦴";
        if (runs >= 10) return "*sad puppy eyes* no whites this time... next one for sure! 🐶";
        return "*yawns and stretches* a quiet one~ 🐾";
    }
}
