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
 * fame and time played, one post per game account. Built from the logs next to the app,
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

    /**
     * A recap link to a Discord bot running Femcore's recap inbox (https://.../recap?key=...), which posts the
     * recap with ◀ ▶ buttons instead of a picture gallery.
     */
    static boolean isBotUrl(String url) {
        return url != null && url.trim().matches("(?i)https://[a-z0-9.-]+(:\\d+)?/recap\\?(.*&)?key=[^&\\s]{8,}(&\\S*)?");
    }

    /** A Discord webhook or a bot recap link. */
    static boolean isValidUrl(String url) {
        return DiscordWebhook.isValidUrl(url) || isBotUrl(url);
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
        if (m.equals("off") || !isValidUrl(s.getProperty("recap.url", ""))) return false;
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

    /** True if a recap would be posted on this tick. */
    static boolean isDue(Properties s) {
        String m = mode(s);
        if (m.equals("off") || !isValidUrl(s.getProperty("recap.url", ""))) return false;
        ZonedDateTime now = ZonedDateTime.now();
        for (Period p : Period.values()) {
            if (!(m.equals("both") || m.equals(p.name().toLowerCase(Locale.ROOT)))) continue;
            String last = s.getProperty("recap.last." + p.name().toLowerCase(Locale.ROOT));
            try {
                if (last != null && Long.parseLong(last) < lastDue(p, s, now).toEpochSecond()) return true;
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return false;
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
            Built b = build(p, start, end, account, legacy, bagFilter(s), isBotUrl(s.getProperty("recap.url", "")));
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
        for (History.Play p : History.play()) if (p.within(start, end) > 0) out.add(acct(p.account, legacy));
        for (History.Fame f : History.fame()) if (in(f.start, start, end)) out.add(acct(f.account, legacy));
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

    static final BagType[] COLOURS = {BagType.WHITE, BagType.RED, BagType.ORANGE, BagType.GOLD, BagType.EGG, BagType.BLUE};

    /** Which bag colours go in the recap (setting recap.bags); White, Red and Orange unless changed. */
    static Set<BagType> bagFilter(Properties s) {
        Set<BagType> out = new LinkedHashSet<>();
        String v = s.getProperty("recap.bags", "white,red,orange");
        for (String p : v.split(",")) {
            for (BagType c : COLOURS) if (c.name().equalsIgnoreCase(p.trim())) out.add(c);
        }
        return out;
    }

    static void setBagFilter(Properties s, Set<BagType> on) {
        List<String> names = new ArrayList<>();
        for (BagType c : COLOURS) if (on.contains(c)) names.add(c.name().toLowerCase(Locale.ROOT));
        s.setProperty("recap.bags", String.join(",", names));
    }

    /** At most this many pictures per post (Discord's limit is 10 files). */
    static final int MAX_PAGES = 10;
    /** At most this many bags in the pictures; the rest are only counted. */
    static final int MAX_BAGS = 60;
    /** Total picture size per post. */
    static final long MAX_UPLOAD = 7L * 1024 * 1024;

    static Built build(Period p, long start, long end, String account, String legacy) {
        return build(p, start, end, account, legacy, new LinkedHashSet<>(java.util.Arrays.asList(COLOURS)), false);
    }

    /** {@code bot}: going to the bot's recap inbox (◀ ▶ buttons) rather than a webhook (picture gallery). */
    static Built build(Period p, long start, long end, String account, String legacy, Set<BagType> shown, boolean bot) {
        ZoneId zone = ZoneId.systemDefault();
        // ---- bags
        Map<BagType, Integer> bags = new LinkedHashMap<>();
        for (BagType c : COLOURS) bags.put(c, 0);
        List<LootStats.Entry> picked = new ArrayList<>();
        int[] rarities = new int[5];
        Map<String, Integer> whitesBy = new LinkedHashMap<>();
        int whites = 0;
        for (LootStats.Entry e : LootStats.all()) {
            if (!in(e.at, start, end) || !acct(e.account, legacy).equals(account)) continue;
            BagType c = LootStats.colour(e.type);
            if (c == BagType.WHITE) {
                whites++;
                whitesBy.merge(e.map, 1, Integer::sum);
            }
            if (!shown.contains(c)) continue;
            bags.merge(c, 1, Integer::sum);
            if (!e.items.isEmpty()) picked.add(e);
            for (int i = 0; i < e.items.size(); i++) {
                int r = i < e.rarity.size() ? Math.max(0, Math.min(4, e.rarity.get(i))) : 0;
                if (c == BagType.WHITE) rarities[r]++;
            }
        }
        // best bags first (white, red, orange...), then the rarest items, then oldest first
        List<BagType> order = java.util.Arrays.asList(COLOURS);
        picked.sort((a, b) -> {
            int c = order.indexOf(LootStats.colour(a.type)) - order.indexOf(LootStats.colour(b.type));
            if (c != 0) return c;
            c = maxRarity(b) - maxRarity(a);
            return c != 0 ? c : Long.compare(a.at, b.at);
        });
        // ---- runs
        Map<String, Integer> runsBy = new LinkedHashMap<>();
        int runs = 0;
        for (RunLog.Run r : RunLog.all()) {
            if (!in(r.at, start, end) || !acct(r.account, legacy).equals(account)) continue;
            runs++;
            runsBy.merge(r.map, 1, Integer::sum);
        }
        // ---- fame, time played, who and which classes
        long fame = 0, playedSecs = 0, bestRate = 0;
        String bestArea = null, name = "";
        Map<String, Long> classTime = new LinkedHashMap<>();
        for (History.Fame f : History.fame()) {
            if (!in(f.start, start, end) || !acct(f.account, legacy).equals(account)) continue;
            fame += f.gained;
            long secs = Math.max(0, f.end - f.start);
            playedSecs += secs;
            if (!f.character.isEmpty()) name = f.character;
            if (!f.cls.isEmpty()) classTime.merge(f.cls, secs, Long::sum);
            if (secs >= 300 && f.gained > 0) {
                long rate = Math.round(f.gained / (secs / 3600.0));
                if (rate > bestRate) {
                    bestRate = rate;
                    bestArea = f.area;
                }
            }
        }
        if (name.isEmpty()) name = History.nameOf(account);
        // time in game, from the play log (1.6 on); older weeks fall back to the fame log's visits
        long logged = 0;
        Map<String, Long> loggedClass = new LinkedHashMap<>();
        for (History.Play pl : History.play()) {
            if (!acct(pl.account, legacy).equals(account)) continue;
            long secs = pl.within(start, end);
            if (secs <= 0) continue;
            logged += secs;
            if (!pl.cls.isEmpty()) loggedClass.merge(pl.cls, secs, Long::sum);
        }
        if (logged > 0) {
            playedSecs = logged;
            classTime = loggedClass;
        }
        int bagTotal = 0;
        for (int v : bags.values()) bagTotal += v;
        if (bagTotal == 0 && whites == 0 && runs == 0 && fame == 0 && playedSecs < 60) return null;

        // ---- loot pictures: one bag per picture, or several per picture when there are lots
        Built b = new Built();
        List<LootImage.RecapBag> cards = new ArrayList<>();
        DateTimeFormatter tf = DateTimeFormatter.ofPattern(p == Period.DAILY ? "h:mm a" : "EEE h:mm a", Locale.ENGLISH);
        for (int i = 0; i < Math.min(MAX_BAGS, picked.size()); i++) {
            LootStats.Entry e = picked.get(i);
            List<ItemInfo> items = new ArrayList<>();
            for (int k = 0; k < e.items.size(); k++) {
                int r = k < e.rarity.size() ? Math.max(0, Math.min(4, e.rarity.get(k))) : 0;
                items.add(ItemInfo.of(e.items.get(k)).withEnchant(Enchant.values()[r]));
            }
            String at = java.time.Instant.ofEpochSecond(e.at).atZone(zone).format(tf).toLowerCase(Locale.ROOT);
            cards.add(new LootImage.RecapBag(LootStats.colour(e.type), e.map + "  ·  " + at, items));
        }
        int perPage = Math.max(1, (cards.size() + MAX_PAGES - 1) / MAX_PAGES);
        int pages = (cards.size() + perPage - 1) / perPage;
        try {
            for (int pg = 0; pg < pages; pg++) {
                byte[] img = LootImage.recapPage(cards.subList(pg * perPage, Math.min(cards.size(), (pg + 1) * perPage)), pg + 1, pages);
                if (img == null) {
                    b.files.clear(); // no item pictures available (game files not read yet)
                    break;
                }
                b.files.put("loot-" + (pg + 1) + ".png", img);
            }
        } catch (Throwable ignored) {
            b.files.clear();
        }
        // keep the whole post well under Discord's 10 MB upload limit: drop the last pictures if needed
        long total = 0;
        for (java.util.Iterator<Map.Entry<String, byte[]>> it = b.files.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, byte[]> f = it.next();
            if (total + f.getValue().length > MAX_UPLOAD) it.remove();
            else total += f.getValue().length;
        }

        // ---- the post
        LocalDate from = java.time.Instant.ofEpochSecond(start).atZone(zone).toLocalDate();
        LocalDate to = java.time.Instant.ofEpochSecond(end - 1).atZone(zone).toLocalDate();
        DateTimeFormatter d = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        String when = p == Period.DAILY ? to.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH)) : from.format(d) + " to " + to.format(d);
        StringBuilder sb = new StringBuilder();
        sb.append("## 🐶 ").append(p.label).append(" recap").append(name.isEmpty() ? "" : " · " + name).append("\n");
        sb.append("-# ").append(when).append("\n");
        if (!classTime.isEmpty()) {
            List<Map.Entry<String, Long>> cs = new ArrayList<>(classTime.entrySet());
            cs.sort((x, y) -> Long.compare(y.getValue(), x.getValue()));
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Long> e : cs) parts.add(e.getValue() >= 60 ? e.getKey() + " (" + played(e.getValue()) + ")" : e.getKey());
            sb.append("🧙 **Played as:** ").append(String.join(", ", parts)).append("\n");
        }
        List<String> bagParts = new ArrayList<>();
        for (Map.Entry<BagType, Integer> e : bags.entrySet()) if (e.getValue() > 0) bagParts.add(e.getValue() + " " + e.getKey().displayName.toLowerCase(Locale.ROOT));
        String rar = "";
        List<String> rp = new ArrayList<>();
        for (int r = 4; r >= 1; r--) if (rarities[r] > 0) rp.add(rarities[r] + " " + Enchant.values()[r].label);
        if (!rp.isEmpty()) rar = " (white bag items: " + String.join(", ", rp) + ")";
        sb.append("🎒 **Bags:** ").append(bagParts.isEmpty() ? "none" : String.join(", ", bagParts)).append(rar).append("\n");
        if (runs > 0) {
            List<Map.Entry<String, Integer>> rs = new ArrayList<>(runsBy.entrySet());
            rs.sort((x, y) -> y.getValue() - x.getValue());
            List<String> topRuns = new ArrayList<>();
            for (int i = 0; i < Math.min(3, rs.size()); i++) topRuns.add(rs.get(i).getKey() + " ×" + rs.get(i).getValue());
            sb.append("🏰 **Runs:** ").append(runs).append(" (most played: ").append(String.join(", ", topRuns)).append(")\n");
            String luckyMap = null;
            double lucky = Double.MAX_VALUE;
            for (Map.Entry<String, Integer> e : whitesBy.entrySet()) {
                Integer r = runsBy.get(e.getKey());
                if (r == null || r == 0) continue;
                double per = r / (double) e.getValue();
                if (per >= 1 && per < lucky) { // (more whites than runs means some runs weren't seen)
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
        // ---- new for the collection: items looted for the first time ever in this period
        {
            List<LootStats.Entry> mineAll = new ArrayList<>();
            for (LootStats.Entry e : LootStats.all()) if (acct(e.account, legacy).equals(account) && e.at < end) mineAll.add(e);
            List<String> fresh = new ArrayList<>();
            for (Map.Entry<LootBook.Item, LootBook.Found> e : LootBook.found(mineAll).entrySet()) {
                if (in(e.getValue().first, start, end)) fresh.add(e.getKey().name);
            }
            if (!fresh.isEmpty()) {
                Collections.sort(fresh);
                sb.append("📖 **New for your collection:** ").append(fresh.size()).append(" (")
                        .append(String.join(", ", fresh.subList(0, Math.min(6, fresh.size()))))
                        .append(fresh.size() > 6 ? ", +" + (fresh.size() - 6) + " more" : "").append(")\n");
            }
        }
        // ---- dry streaks (from all of this account's runs, not just this period)
        {
            List<RunLog.Run> allRuns = new ArrayList<>();
            for (RunLog.Run r : RunLog.all()) if (acct(r.account, legacy).equals(account) && r.at < end) allRuns.add(r);
            List<LootStats.Entry> allBags = new ArrayList<>();
            for (LootStats.Entry e : LootStats.all()) if (acct(e.account, legacy).equals(account) && e.at < end) allBags.add(e);
            Map<String, DryStreak.Streak> streaks = DryStreak.compute(allRuns, allBags);
            String brokeMap = null;
            long broke = 0;
            for (DryStreak.Streak st : streaks.values()) {
                if (st.map.isEmpty()) continue;
                for (long[] bk : st.broken) if (in(bk[0], start, end) && bk[1] > broke) {
                    broke = bk[1];
                    brokeMap = st.map;
                }
            }
            if (brokeMap != null && broke >= 5) {
                sb.append("🎉 **Streak broken:** a white after ").append(broke).append(" runs of ").append(brokeMap).append("\n");
            }
            for (DryStreak.Streak st : DryStreak.worst(streaks)) {
                if (!runsBy.containsKey(st.map) || st.current < 5) continue; // only dungeons played this period
                sb.append("🏜️ **Dry streak:** ").append(st.current).append(" runs of ").append(st.map).append(" without a white")
                        .append(st.isRecord() ? " (longest yet!)" : "").append("\n");
                break;
            }
        }
        if (fame > 0) {
            sb.append("🏆 **Fame:** +").append(String.format(Locale.ROOT, "%,d", fame));
            if (playedSecs >= 600) sb.append(String.format(Locale.ROOT, " (about %,d per hour)", Math.round(fame / (playedSecs / 3600.0))));
            if (bestArea != null) sb.append(", best run ").append(bestArea);
            sb.append("\n");
        }
        if (playedSecs >= 60) sb.append("⏱️ **Played:** ").append(played(playedSecs)).append("\n");
        if (!b.files.isEmpty()) {
            int shownBags = cards.size();
            sb.append("📸 **Loot:** ").append(shownBags == 1 ? "1 bag" : shownBags + " bags");
            if (picked.size() > shownBags) sb.append(" (the best ").append(shownBags).append(" of ").append(picked.size()).append(")");
            if (b.files.size() > 1) sb.append(bot ? ". Use ◀ ▶ under the picture to flip through" : ". Click a picture, then use the arrows to flip through");
            sb.append("\n");
        }
        sb.append("\n").append(pick(whites, runs, rarities));

        // Plain message text (not an embed) so the pictures sit underneath it as one gallery.
        String text = sb.toString().trim();
        if (text.length() > 1990) text = text.substring(0, 1990) + "…";
        StringBuilder j = new StringBuilder("{");
        j.append("\"username\":\"White Bag Pup\",\"allowed_mentions\":{\"parse\":[]},");
        j.append("\"content\":").append(DiscordWebhook.str(text));
        j.append("}");
        b.json = j.toString();
        return b;
    }

    private static int maxRarity(LootStats.Entry e) {
        int m = 0;
        for (int r : e.rarity) m = Math.max(m, r);
        return m;
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
