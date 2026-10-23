package whitebag;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Pup badges: achievements worked out from the logs next to the app (bags, runs, kills, fame, play time, the
 * collection, trades and the Shatters puzzle). Unlocked badges are kept in badges.txt; things the logs don't record
 * (solving the puzzle) are noted in badge-events.txt.
 */
final class Badges {
    private Badges() {
    }

    enum Tier {
        BRONZE(0xCD8B5A), SILVER(0x9FA8B8), GOLD(0xE8B630), PINK(0xFF6FA8),
        /** The hardest badges: drawn with a rainbow medal. */
        PRISMATIC(0x9B5CFF);
        final Color color;

        Tier(int rgb) {
            this.color = new Color(rgb);
        }
    }

    /** What the badges are counted from, for one account. */
    static final class Stats {
        long whites, legendaries, divines, runs, mostRunsOneDungeon, brokeStreak, luckyRuns;
        long bossKills, topDamage, soloKills, eventKills;
        long fame, playSeconds, collection, completedDungeons, trades;
        long puzzleSolved, puzzlePerfect, practicePerfect, practiceSolved;
        long pets, bags, shinies, busiestDay, nightRuns, dungeonsVisited, playDays, mostWhitesOneRun;
        long mostWhitesOneDay, topDamageTotal;
        String mostRunDungeon = "";
    }

    interface Counter {
        long of(Stats s);
    }

    static final class Badge {
        final String id, name, desc, glyph;
        final Tier tier;
        final long need;
        final Counter count;

        Badge(String id, String name, String desc, String glyph, Tier tier, long need, Counter count) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.glyph = glyph;
            this.tier = tier;
            this.need = need;
            this.count = count;
        }
    }

    static final List<Badge> ALL = Collections.unmodifiableList(Arrays.asList(
            new Badge("white1", "First White", "Loot your first white bag", "W", Tier.BRONZE, 1, s -> s.whites),
            new Badge("white10", "White Collar", "Loot 10 white bags", "W", Tier.SILVER, 10, s -> s.whites),
            new Badge("white50", "Snow Pile", "Loot 50 white bags", "W", Tier.GOLD, 50, s -> s.whites),
            new Badge("white100", "Blizzard", "Loot 100 white bags", "W", Tier.PINK, 100, s -> s.whites),
            new Badge("legendary", "Ooh, Shiny", "Loot a Legendary item", "L", Tier.SILVER, 1, s -> s.legendaries),
            new Badge("divine", "Divine Intervention", "Loot a Divine item", "D", Tier.PINK, 1, s -> s.divines),
            new Badge("lucky", "Lucky Pup", "Get two white bags in one run", "2", Tier.GOLD, 1, s -> s.luckyRuns),
            new Badge("runs10", "Getting Started", "Finish 10 dungeon runs", "R", Tier.BRONZE, 10, s -> s.runs),
            new Badge("runs100", "Regular", "Finish 100 dungeon runs", "R", Tier.SILVER, 100, s -> s.runs),
            new Badge("runs500", "Dungeon Dweller", "Finish 500 dungeon runs", "R", Tier.GOLD, 500, s -> s.runs),
            new Badge("devoted", "Devoted", "Run the same dungeon 100 times", "♥", Tier.GOLD, 100, s -> s.mostRunsOneDungeon),
            new Badge("boss50", "Boss Hunter", "Help kill 50 bosses", "B", Tier.BRONZE, 50, s -> s.bossKills),
            new Badge("boss500", "Boss Slayer", "Help kill 500 bosses", "B", Tier.GOLD, 500, s -> s.bossKills),
            new Badge("topdog", "Top Dog", "Do the most damage in a group 25 times", "1", Tier.SILVER, 25, s -> s.topDamage),
            new Badge("solo", "Solo Act", "Kill a boss doing all the damage", "!", Tier.GOLD, 1, s -> s.soloKills),
            new Badge("events25", "Event Chaser", "Help kill 25 realm events", "E", Tier.SILVER, 25, s -> s.eventKills),
            new Badge("fame10k", "Famous", "Gain 10,000 fame", "F", Tier.SILVER, 10_000, s -> s.fame),
            new Badge("fame100k", "Legend", "Gain 100,000 fame", "F", Tier.PINK, 100_000, s -> s.fame),
            new Badge("time10", "Dedicated", "Play for 10 hours", "T", Tier.BRONZE, 10, s -> s.playSeconds / 3600),
            new Badge("time100", "Realm Regular", "Play for 100 hours", "T", Tier.GOLD, 100, s -> s.playSeconds / 3600),
            new Badge("collect25", "Collector", "Find 25 items for your collection", "C", Tier.BRONZE, 25, s -> s.collection),
            new Badge("collect100", "Curator", "Find 100 items for your collection", "C", Tier.GOLD, 100, s -> s.collection),
            new Badge("complete", "Completionist", "Find every item from one dungeon", "★", Tier.PINK, 1, s -> s.completedDungeons),
            new Badge("trades10", "Wheeler Dealer", "Finish 10 trades", "$", Tier.BRONZE, 10, s -> s.trades),
            new Badge("puzzle", "Puzzle Solved", "Solve the Shatters puzzle in game", "P", Tier.SILVER, 1, s -> s.puzzleSolved),
            new Badge("bigbrain", "Big Brain", "Solve the Shatters puzzle in the fewest shots", "P", Tier.PINK, 1, s -> s.puzzlePerfect),
            new Badge("practice", "Puzzle Pup", "Solve a practice puzzle perfectly", "P", Tier.BRONZE, 1, s -> s.practicePerfect),
            // ---- puppy badges ("paw" and "bone" are drawn as little pictures)
            new Badge("goodpup", "Good Pup", "Solve 10 practice puzzles", "paw", Tier.BRONZE, 10, s -> s.practiceSolved),
            new Badge("verygoodpup", "Very Good Pup", "Solve 50 practice puzzles", "paw", Tier.SILVER, 50, s -> s.practiceSolved),
            new Badge("bestinshow", "Best in Show", "Solve 25 practice puzzles perfectly", "paw", Tier.GOLD, 25, s -> s.practicePerfect),
            new Badge("bellyrubs", "Belly Rubs", "Pet the pup 25 times", "♥", Tier.BRONZE, 25, s -> s.pets),
            new Badge("spoiled", "Spoiled Rotten", "Pet the pup 500 times", "♥", Tier.PINK, 500, s -> s.pets),
            new Badge("fetch", "Fetch!", "Loot 100 bags of any colour", "bone", Tier.BRONZE, 100, s -> s.bags),
            new Badge("fetchchamp", "Fetch Champion", "Loot 1,000 bags of any colour", "bone", Tier.GOLD, 1000, s -> s.bags),
            new Badge("sniffer", "Sniffer Dog", "Sniff out a shiny item", "★", Tier.SILVER, 1, s -> s.shinies),
            new Badge("bonecollector", "Bone Collector", "Find 10 shiny items", "bone", Tier.PINK, 10, s -> s.shinies),
            new Badge("zoomies", "Zoomies", "Finish 10 dungeon runs in one day", "Z", Tier.SILVER, 10, s -> s.busiestDay),
            new Badge("midnight", "Midnight Walkies", "Start a dungeon run between midnight and 4 am", "M", Tier.BRONZE, 1, s -> s.nightRuns),
            new Badge("walkies", "Walkies", "Run 10 different dungeons", "paw", Tier.SILVER, 10, s -> s.dungeonsVisited),
            new Badge("longwalkies", "Long Walkies", "Run 30 different dungeons", "paw", Tier.GOLD, 30, s -> s.dungeonsVisited),
            new Badge("packleader", "Pack Leader", "Do the most damage in a group 100 times", "1", Tier.PINK, 100, s -> s.topDamage),
            new Badge("loyal", "Loyal Companion", "Play on 30 different days", "♥", Tier.GOLD, 30, s -> s.playDays),
            // ---- the hard ones
            new Badge("white250", "Avalanche", "Loot 250 white bags", "W", Tier.PINK, 250, s -> s.whites),
            new Badge("white500", "Ice Age", "Loot 500 white bags", "W", Tier.PINK, 500, s -> s.whites),
            new Badge("jackpot", "Jackpot", "Get three white bags in one run", "3", Tier.PINK, 3, s -> s.mostWhitesOneRun),
            new Badge("blessed", "Blessed", "Loot 10 Divine items", "D", Tier.PINK, 10, s -> s.divines),
            new Badge("dungeonmaster", "Dungeon Master", "Finish 1,000 dungeon runs", "R", Tier.PINK, 1000, s -> s.runs),
            new Badge("obsessed", "Obsessed", "Run the same dungeon 500 times", "♥", Tier.PINK, 500, s -> s.mostRunsOneDungeon),
            new Badge("traveller", "World Traveller", "Run 60 different dungeons", "paw", Tier.PINK, 60, s -> s.dungeonsVisited),
            new Badge("boss2000", "Boss Exterminator", "Help kill 2,000 bosses", "B", Tier.PINK, 2000, s -> s.bossKills),
            new Badge("onepuparmy", "One Pup Army", "Kill 10 bosses doing all the damage", "!", Tier.PINK, 10, s -> s.soloKills),
            new Badge("events250", "Storm Chaser", "Help kill 250 realm events", "E", Tier.PINK, 250, s -> s.eventKills),
            new Badge("mythic", "Mythic", "Gain 1,000,000 fame", "F", Tier.PINK, 1_000_000, s -> s.fame),
            new Badge("time500", "Realm Resident", "Play for 500 hours", "T", Tier.PINK, 500, s -> s.playSeconds / 3600),
            new Badge("bestfriend", "Best Friends Forever", "Play on 100 different days", "♥", Tier.PINK, 100, s -> s.playDays),
            new Badge("museum", "Museum", "Find 300 items for your collection", "C", Tier.PINK, 300, s -> s.collection),
            new Badge("completionist5", "Master Completionist", "Find every item from 5 dungeons", "★", Tier.PINK, 5, s -> s.completedDungeons),
            new Badge("shinyhunter", "Shiny Hunter", "Find 25 shiny items", "★", Tier.PINK, 25, s -> s.shinies),
            new Badge("puzzlemaster", "Puzzle Master", "Solve the Shatters puzzle in the fewest shots 10 times", "P", Tier.PINK, 10, s -> s.puzzlePerfect),
            new Badge("puzzleaddict", "Puzzle Addict", "Solve 500 practice puzzles", "paw", Tier.PINK, 500, s -> s.practiceSolved),
            new Badge("turbozoomies", "Turbo Zoomies", "Finish 25 dungeon runs in one day", "Z", Tier.PINK, 25, s -> s.busiestDay),
            new Badge("mostloved", "Most Loved Pup", "Pet the pup 2,000 times", "♥", Tier.PINK, 2000, s -> s.pets),
            // ---- prismatic: the hardest of all
            new Badge("white1000", "Eternal Winter", "Loot 1,000 white bags", "W", Tier.PRISMATIC, 1000, s -> s.whites),
            new Badge("royalflush", "Royal Flush", "Get four white bags in one run", "4", Tier.PRISMATIC, 4, s -> s.mostWhitesOneRun),
            new Badge("luckyday", "Lucky Day", "Loot 10 white bags in one day", "W", Tier.PRISMATIC, 10, s -> s.mostWhitesOneDay),
            new Badge("divine50", "Divine Collector", "Loot 50 Divine items", "D", Tier.PRISMATIC, 50, s -> s.divines),
            new Badge("legendary250", "Legendary Hoarder", "Loot 250 Legendary items", "L", Tier.PRISMATIC, 250, s -> s.legendaries),
            new Badge("bags10k", "Bag Hoarder", "Loot 10,000 bags of any colour", "bone", Tier.PRISMATIC, 10_000, s -> s.bags),
            new Badge("runs5000", "Dungeon Legend", "Finish 5,000 dungeon runs", "R", Tier.PRISMATIC, 5000, s -> s.runs),
            new Badge("livingthere", "Living There", "Run the same dungeon 1,000 times", "♥", Tier.PRISMATIC, 1000, s -> s.mostRunsOneDungeon),
            new Badge("beeneverywhere", "Been Everywhere", "Run 75 different dungeons", "paw", Tier.PRISMATIC, 75, s -> s.dungeonsVisited),
            new Badge("speedrunpup", "Speedrun Pup", "Finish 50 dungeon runs in one day", "Z", Tier.PRISMATIC, 50, s -> s.busiestDay),
            new Badge("boss10k", "Boss Nightmare", "Help kill 10,000 bosses", "B", Tier.PRISMATIC, 10_000, s -> s.bossKills),
            new Badge("unstoppable", "Unstoppable", "Kill 50 bosses doing all the damage", "!", Tier.PRISMATIC, 50, s -> s.soloKills),
            new Badge("alpha", "Alpha", "Do the most damage in a group 500 times", "1", Tier.PRISMATIC, 500, s -> s.topDamage),
            new Badge("eventhorizon", "Event Horizon", "Help kill 1,000 realm events", "E", Tier.PRISMATIC, 1000, s -> s.eventKills),
            new Badge("ascended", "Ascended", "Gain 10,000,000 fame", "F", Tier.PRISMATIC, 10_000_000, s -> s.fame),
            new Badge("realmishome", "The Realm Is Home", "Play for 2,000 hours", "T", Tier.PRISMATIC, 2000, s -> s.playSeconds / 3600),
            new Badge("wholeyear", "A Whole Year", "Play on 365 different days", "♥", Tier.PRISMATIC, 365, s -> s.playDays),
            new Badge("grandmuseum", "Grand Museum", "Find 600 items for your collection", "C", Tier.PRISMATIC, 600, s -> s.collection),
            new Badge("archivist", "Archivist", "Find every item from 20 dungeons", "★", Tier.PRISMATIC, 20, s -> s.completedDungeons),
            new Badge("shinylegend", "Shiny Legend", "Find 100 shiny items", "★", Tier.PRISMATIC, 100, s -> s.shinies),
            new Badge("puzzleprodigy", "Puzzle Prodigy", "Solve the Shatters puzzle in the fewest shots 50 times", "P", Tier.PRISMATIC, 50, s -> s.puzzlePerfect),
            new Badge("puzzlemachine", "Puzzle Machine", "Solve 2,000 practice puzzles", "paw", Tier.PRISMATIC, 2000, s -> s.practiceSolved),
            new Badge("merchantprince", "Merchant Prince", "Finish 500 trades", "$", Tier.PRISMATIC, 500, s -> s.trades),
            new Badge("spoiledbeyond", "Spoiled Beyond Belief", "Pet the pup 10,000 times", "♥", Tier.PRISMATIC, 10_000, s -> s.pets)));

    // ---------------------------------------------------------------- unlocked badges

    static final class Unlock {
        final String account, id;
        final long at;
        /** Earned before badges existed (or before this badge did): shown, but never announced. */
        final boolean quiet;

        Unlock(String account, String id, long at, boolean quiet) {
            this.account = account;
            this.id = id;
            this.at = at;
            this.quiet = quiet;
        }
    }

    private static final String FILE = "badges.txt", EVENTS = "badge-events.txt";
    /** Set once the game files are read (item names are needed for the collection badges). */
    static volatile boolean ready;
    private static List<Unlock> unlocks;
    private static Map<String, Map<String, Long>> events; // account -> event -> count

    static synchronized List<Unlock> unlocks() {
        if (unlocks == null) {
            unlocks = new ArrayList<>();
            for (String l : read(FILE)) {
                String[] f = l.split("\\|", -1);
                if (f.length < 3) continue;
                try {
                    unlocks.add(new Unlock(f[0], f[1], Long.parseLong(f[2]), f.length > 3 && f[3].equals("1")));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(unlocks));
    }

    /** Notes something the logs don't keep (e.g. "puzzle", "puzzle-perfect", "practice-perfect"). */
    static synchronized void note(String account, String event) {
        events();
        events.computeIfAbsent(account == null ? "" : account, k -> new HashMap<>()).merge(event, 1L, Long::sum);
        append(EVENTS, (account == null ? "" : account) + "|" + event + "|" + System.currentTimeMillis() / 1000);
    }

    private static Map<String, Map<String, Long>> events() {
        if (events == null) {
            events = new HashMap<>();
            for (String l : read(EVENTS)) {
                String[] f = l.split("\\|", -1);
                if (f.length >= 2) events.computeIfAbsent(f[0], k -> new HashMap<>()).merge(f[1], 1L, Long::sum);
            }
        }
        return events;
    }

    private static synchronized long event(String account, String event) {
        Map<String, Long> m = events().get(account);
        Long n = m == null ? null : m.get(event);
        return n == null ? 0 : n;
    }

    /**
     * How many times this happened: for this account, plus any noted before the game said which account it was.
     * Before the game has said which account this is (after a restart), every account's count is included.
     */
    static synchronized long count(String account, String event) {
        if (account == null || account.isEmpty()) {
            long n = 0;
            for (Map<String, Long> m : events().values()) n += m.getOrDefault(event, 0L);
            return n;
        }
        return event("", event) + event(account, event);
    }

    /**
     * Works out which badges this account has now, saves new ones and returns them (the ones to announce).
     * The very first check for an account saves what it already earned quietly, so old history doesn't flood Discord.
     */
    static synchronized List<Badge> check(String account, Predicate<String> mine) {
        if (account == null || account.isEmpty() || !ready) return Collections.emptyList();
        Stats s = stats(account, mine);
        unlocks();
        boolean first = true;
        java.util.Set<String> have = new java.util.HashSet<>();
        for (Unlock u : unlocks) {
            if (!u.account.equals(account)) continue;
            first = false;
            have.add(u.id);
        }
        List<Badge> fresh = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000;
        for (Badge b : ALL) {
            // a badge this account hasn't been checked for yet (new in this version): if it's already earned, it was
            // earned before the badge existed, so it unlocks quietly instead of being announced
            boolean seen = have.contains("~" + b.id);
            if (!seen) {
                unlocks.add(new Unlock(account, "~" + b.id, now, true));
                append(FILE, account + "|~" + b.id + "|" + now + "|1");
            }
            if (have.contains(b.id) || b.count.of(s) < b.need) continue;
            boolean quiet = first || !seen;
            Unlock u = new Unlock(account, b.id, now, quiet);
            unlocks.add(u);
            append(FILE, account + "|" + b.id + "|" + now + "|" + (quiet ? 1 : 0));
            if (!quiet) fresh.add(b);
        }
        if (first && fresh.isEmpty() && have.isEmpty()) {
            // remember that this account has been checked, even with nothing earned yet
            unlocks.add(new Unlock(account, "-", now, true));
            append(FILE, account + "|-|" + now + "|1");
        }
        return fresh;
    }

    /** True if any account has unlocked this badge (the pup's wardrobe is shared). */
    static synchronized boolean anyUnlocked(String id) {
        for (Unlock u : unlocks()) if (u.id.equals(id)) return true;
        return false;
    }

    static Badge byId(String id) {
        for (Badge b : ALL) if (b.id.equals(id)) return b;
        return null;
    }

    /** When this account unlocked each badge (badge id -> unlock). */
    static synchronized Map<String, Unlock> unlocked(String account) {
        Map<String, Unlock> out = new LinkedHashMap<>();
        for (Unlock u : unlocks()) if (u.account.equals(account) && !u.id.equals("-") && !u.id.startsWith("~")) out.putIfAbsent(u.id, u);
        return out;
    }

    // ---------------------------------------------------------------- counting

    static Stats stats(String account, Predicate<String> mine) {
        Stats s = new Stats();
        List<LootStats.Entry> bags = new ArrayList<>();
        for (LootStats.Entry e : LootStats.all()) if (mine.test(e.account)) bags.add(e);
        List<RunLog.Run> runs = new ArrayList<>();
        for (RunLog.Run r : RunLog.all()) if (mine.test(r.account)) runs.add(r);

        for (LootStats.Entry e : bags) {
            if (LootStats.colour(e.type) == BagType.WHITE) s.whites++;
            for (int r : e.rarity) {
                if (r == 3) s.legendaries++;
                if (r == 4) s.divines++;
            }
        }
        Map<java.time.LocalDate, Integer> whitesPerDay = new HashMap<>();
        for (LootStats.Entry e : bags) {
            if (LootStats.colour(e.type) != BagType.WHITE) continue;
            java.time.LocalDate d = java.time.Instant.ofEpochSecond(e.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            s.mostWhitesOneDay = Math.max(s.mostWhitesOneDay, whitesPerDay.merge(d, 1, Integer::sum));
        }
        Map<String, List<Long>> whitesAt = new HashMap<>();
        for (LootStats.Entry e : bags) {
            if (LootStats.colour(e.type) == BagType.WHITE) whitesAt.computeIfAbsent(e.map, k -> new ArrayList<>()).add(e.at);
        }
        Map<String, Integer> byMap = new HashMap<>();
        for (RunLog.Run r : runs) {
            if (RunLog.isRealm(r.map)) continue;
            s.runs++;
            int n = byMap.merge(r.map, 1, Integer::sum);
            if (n > s.mostRunsOneDungeon) {
                s.mostRunsOneDungeon = n;
                s.mostRunDungeon = r.map;
            }
            // two whites in one run
            int whites = 0;
            for (long at : whitesAt.getOrDefault(r.map, Collections.emptyList())) if (at >= r.at && at <= r.at + r.seconds + 10) whites++;
            if (whites >= 2) s.luckyRuns++;
            s.mostWhitesOneRun = Math.max(s.mostWhitesOneRun, whites);
        }
        for (DryStreak.Streak st : DryStreak.compute(runs, bags).values()) {
            if (st.map.isEmpty()) continue;
            for (long[] b : st.broken) s.brokeStreak = Math.max(s.brokeStreak, b[1]);
        }
        List<History.Dps> myKills = new ArrayList<>();
        for (History.Dps d : History.dps()) if (mine.test(d.account) && d.killed) myKills.add(d);
        for (History.Dps d : KillsPage.oneEach(myKills)) {
            boolean event = KillsPage.isEvent(d);
            if (event) s.eventKills++;
            if (event || d.kind.isEmpty() || d.kind.equals("BOSS")) s.bossKills++;
            if (d.rank == 1 && d.players > 1) s.topDamage++;
            if (d.share >= 99 && (d.kind.isEmpty() || d.kind.equals("BOSS") || event)) s.soloKills++;
        }
        for (History.Fame f : History.fame()) if (mine.test(f.account)) s.fame += f.gained;
        for (History.Play p : History.play()) if (mine.test(p.account)) s.playSeconds += p.end - p.start;
        Map<LootBook.Item, LootBook.Found> found = LootBook.found(bags);
        s.collection = found.size();
        for (LootBook.Place p : LootBook.places()) {
            if (p.name.equals("Elsewhere") || p.items.isEmpty()) continue;
            boolean all = true;
            for (LootBook.Item it : p.items) if (!found.containsKey(it)) { all = false; break; }
            if (all) s.completedDungeons++;
        }
        for (TradeLog.Trade t : TradeLog.all()) if (mine.test(t.account)) s.trades++;
        s.puzzleSolved = event(account, "puzzle");
        s.puzzlePerfect = event(account, "puzzle-perfect");
        s.practicePerfect = count(account, "practice-perfect");
        s.practiceSolved = count(account, "practice");
        s.pets = count(account, "pet");
        s.bags = bags.size();
        s.shinies = LootBook.foundShinies(bags).size();
        s.dungeonsVisited = byMap.size();
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        Map<java.time.LocalDate, Integer> perDay = new HashMap<>();
        for (RunLog.Run r : runs) {
            if (RunLog.isRealm(r.map)) continue;
            java.time.ZonedDateTime t = java.time.Instant.ofEpochSecond(r.at).atZone(zone);
            s.busiestDay = Math.max(s.busiestDay, perDay.merge(t.toLocalDate(), 1, Integer::sum));
            if (t.getHour() < 4) s.nightRuns++;
        }
        java.util.Set<java.time.LocalDate> days = new java.util.HashSet<>();
        for (History.Play p : History.play()) if (mine.test(p.account)) days.add(java.time.Instant.ofEpochSecond(p.start).atZone(zone).toLocalDate());
        s.playDays = days.size();
        return s;
    }

    // ---------------------------------------------------------------- files

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
