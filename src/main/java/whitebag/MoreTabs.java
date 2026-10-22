package whitebag;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static whitebag.AboutMe.heading;
import static whitebag.AboutMe.left;
import static whitebag.AboutMe.names;
import static whitebag.AboutMe.note;
import static whitebag.AboutMe.small;
import static whitebag.AboutMe.wrap;

/** The Vault and Loot tabs. */
final class MoreTabs {
    private MoreTabs() {
    }

    private static String when(long ms) {
        return new SimpleDateFormat("d MMM, h:mm a", Locale.ROOT).format(new Date(ms));
    }

    /** A scrolling page that redraws itself (at most a few times a second) when told something changed. */
    abstract static class Page extends JPanel {
        final LogTabs.WidthPanel list = new LogTabs.WidthPanel(null);
        private final Timer refresh;

        Page() {
            super(new BorderLayout());
            setOpaque(false);
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            list.setBackground(Pixel.SHEET);
            list.setBorder(new EmptyBorder(14, 16, 16, 16));
            add(Pixel.scroll(list), BorderLayout.CENTER);
            refresh = new Timer(600, e -> rebuild());
            refresh.setRepeats(false);
            SwingUtilities.invokeLater(this::rebuild);
        }

        void changed() {
            SwingUtilities.invokeLater(() -> {
                if (!refresh.isRunning()) refresh.restart();
            });
        }

        private void rebuild() {
            list.removeAll();
            fill();
            list.add(Box.createVerticalGlue());
            list.revalidate();
            list.repaint();
        }

        abstract void fill();

        void gap(int h) {
            list.add(Box.createVerticalStrut(h));
        }

        /** Item slots in rows of {@code perRow}. */
        void slots(List<Integer> ids, int perRow) {
            for (int i = 0; i < ids.size(); i += perRow) {
                list.add(new AboutMe.ItemStrip(ids.subList(i, Math.min(ids.size(), i + perRow)), null));
                gap(4);
            }
        }

        /** A simple table of labels; the first row is bold. */
        JComponent table(List<String[]> rows) {
            // each column only as wide as it needs, so wide tables still fit the page
            JPanel grid = new JPanel(new GridBagLayout());
            grid.setOpaque(false);
            GridBagConstraints c = new GridBagConstraints();
            c.anchor = GridBagConstraints.WEST;
            for (int r = 0; r < rows.size(); r++) {
                String[] cells = rows.get(r);
                for (int k = 0; k < cells.length; k++) {
                    JLabel l = new JLabel(cells[k]);
                    l.setFont(Cute.font(r == 0 ? Font.BOLD : Font.PLAIN, 13));
                    l.setForeground(Pixel.INK);
                    c.gridx = k;
                    c.gridy = r;
                    c.insets = new Insets(0, 0, 4, k == cells.length - 1 ? 0 : 20);
                    grid.add(l, c);
                }
            }
            JPanel wrapPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            wrapPanel.setOpaque(false);
            wrapPanel.add(grid);
            wrapPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
            return left(wrapPanel);
        }
    }

    // ================================================================== Vault

    static class VaultPage extends Page {
        private static final String[] POTION_STATS = {"Life", "Mana", "Attack", "Defense", "Speed", "Dexterity", "Vitality", "Wisdom"};
        private final VaultStore store;

        VaultPage(VaultStore store) {
            this.store = store;
        }

        @Override
        void fill() {
            long at = store == null ? 0 : store.updatedAt();
            list.add(heading("vault", at == 0 ? null : "Updated " + when(at)));
            gap(10);
            if (at == 0) {
                list.add(note("Go into your vault in game and its contents show up here. The game only sends them when you enter it, "
                        + "and the last copy is remembered after that."));
                return;
            }
            int[] vault = store.vault(), potions = store.potions(), gifts = store.gifts();

            // ---- potion counts across vault, potion storage and gift chest
            int[] normal = new int[8], greater = new int[8];
            for (int[] where : new int[][]{vault, potions, gifts}) {
                for (int id : where) {
                    if (id <= 0) continue;
                    String n = ItemInfo.of(id).name;
                    for (int k = 0; k < 8; k++) {
                        if (n.equalsIgnoreCase("Potion of " + POTION_STATS[k])) normal[k]++;
                        else if (n.equalsIgnoreCase("Greater Potion of " + POTION_STATS[k])) greater[k]++;
                    }
                }
            }
            list.add(small("Stat potions (vault, potion storage and gift chest)"));
            gap(6);
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Potion", "Normal", "Greater"});
            for (int k = 0; k < 8; k++) rows.add(new String[]{POTION_STATS[k], String.valueOf(normal[k]), String.valueOf(greater[k])});
            list.add(table(rows));

            int used = count(potions);
            if (potions.length > 0 || store.potionMax() > 0) {
                gap(14);
                list.add(small("Potion storage  (" + used + (store.potionMax() > 0 ? " / " + store.potionMax() : "") + ")"));
                gap(4);
                slots(filled(potions), 10);
            }

            gap(14);
            List<Integer> items = filled(vault);
            list.add(small("Vault  (" + items.size() + " items, " + (vault.length - items.size()) + " empty slots)"));
            gap(4);
            if (items.isEmpty()) list.add(note("Empty."));
            else slots(items, 10);

            List<Integer> gift = filled(gifts);
            if (!gift.isEmpty()) {
                gap(14);
                list.add(small("Gift chest  (" + gift.size() + " items)"));
                gap(4);
                slots(gift, 10);
            }
        }

        private static List<Integer> filled(int[] a) {
            List<Integer> out = new ArrayList<>();
            for (int id : a) if (id > 0) out.add(id);
            return out;
        }

        private static int count(int[] a) {
            int n = 0;
            for (int id : a) if (id > 0) n++;
            return n;
        }
    }

    // ================================================================== Loot stats

    static class LootPage extends Page {
        private static final BagType[] COLOURS = {BagType.WHITE, BagType.RED, BagType.ORANGE, BagType.GOLD, BagType.EGG, BagType.BLUE, BagType.CYAN};
        /** Which bags belong to this page's account (null = every bag). */
        private final java.util.function.Predicate<LootStats.Entry> mine;
        private final java.util.function.Predicate<RunLog.Run> myRuns;
        private static final boolean everyAccount = false;

        LootPage() {
            this(null, null);
        }

        LootPage(java.util.function.Predicate<LootStats.Entry> mine) {
            this(mine, null);
        }

        LootPage(java.util.function.Predicate<LootStats.Entry> mine, java.util.function.Predicate<RunLog.Run> myRuns) {
            this.mine = mine;
            this.myRuns = myRuns;
        }

        @Override
        void fill() {
            List<LootStats.Entry> every = LootStats.all();
            boolean severalAccounts = false;
            String firstAccount = null;
            for (LootStats.Entry e : every) {
                if (e.account.isEmpty()) continue;
                if (firstAccount == null) firstAccount = e.account;
                else if (!firstAccount.equals(e.account)) severalAccounts = true;
            }
            List<LootStats.Entry> all = new ArrayList<>();
            for (LootStats.Entry e : every) if (mine == null || everyAccount || mine.test(e)) all.add(e);
            list.add(heading("loot", null));
            gap(10);
            if (all.isEmpty()) {
                list.add(note("Every coloured bag the pup sees is counted here (all colours, not just the ones you get pinged for), "
                        + "this session and all time."));
                return;
            }

            // ---- per colour
            Map<BagType, int[]> counts = new EnumMap<>(BagType.class); // {session, all time, boosted all time}
            for (BagType c : COLOURS) counts.put(c, new int[3]);
            for (LootStats.Entry e : all) {
                int[] c = counts.get(LootStats.colour(e.type));
                if (c == null) continue;
                c[1]++;
                if (e.type != LootStats.colour(e.type)) c[2]++;
                if (e.at >= LootStats.SESSION_START) c[0]++;
            }
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Bag", "This session", "All time", "Boosted"});
            int s = 0, t = 0;
            for (BagType c : COLOURS) {
                int[] n = counts.get(c);
                rows.add(new String[]{c.displayName, String.valueOf(n[0]), String.valueOf(n[1]), String.valueOf(n[2])});
                s += n[0];
                t += n[1];
            }
            rows.add(new String[]{"Total", String.valueOf(s), String.valueOf(t), ""});
            list.add(table(rows));

            // ---- dry streaks: runs since the last white bag
            List<RunLog.Run> myRunList = new ArrayList<>();
            for (RunLog.Run r : RunLog.all()) if (myRuns == null || everyAccount || myRuns.test(r)) myRunList.add(r);
            Map<String, DryStreak.Streak> streaks = DryStreak.compute(myRunList, all);
            gap(18);
            list.add(heading("dry streaks", null));
            gap(8);
            DryStreak.Streak overall = streaks.get("");
            if (overall.runs == 0) {
                list.add(note("Counts the dungeon runs since your last white bag, in each dungeon and overall, from your next run on (the realm isn't counted)."));
            } else {
                String big = overall.current == 0 ? "Your last dungeon run had a white bag! Streak: 0 runs"
                        : overall.current + (overall.current == 1 ? " dungeon run" : " dungeon runs") + " since your last white bag"
                        + (overall.lastWhite > 0 ? " (" + when(overall.lastWhite * 1000L) + ")" : "");
                JLabel head = new JLabel(big);
                head.setFont(Cute.font(Font.BOLD, 16));
                head.setForeground(overall.current >= 20 ? Pixel.PINK_INK : Pixel.INK);
                list.add(left(head));
                gap(2);
                list.add(note("Longest dry streak overall: " + overall.longest + (overall.longest == 1 ? " run" : " runs")
                        + (overall.isRecord() ? "  (that's this one, a new record!)" : "")));
                List<DryStreak.Streak> worst = DryStreak.worst(streaks);
                if (!worst.isEmpty()) {
                    gap(8);
                    List<String[]> dr = new ArrayList<>();
                    dr.add(new String[]{"Dungeon", "Runs without a white", "Longest ever", "Last white"});
                    for (int i = 0; i < Math.min(10, worst.size()); i++) {
                        DryStreak.Streak w = worst.get(i);
                        dr.add(new String[]{w.map, w.current + (w.isRecord() ? "  (record!)" : ""), String.valueOf(w.longest),
                                w.lastWhite > 0 ? when(w.lastWhite * 1000L) : "not yet"});
                    }
                    list.add(table(dr));
                }
            }

            // ---- by dungeon
            Map<String, int[]> byMap = new LinkedHashMap<>(); // {bags, whites}
            for (LootStats.Entry e : all) {
                String m = e.map.isEmpty() ? "Unknown" : e.map;
                int[] n = byMap.computeIfAbsent(m, k -> new int[2]);
                n[0]++;
                if (LootStats.colour(e.type) == BagType.WHITE) n[1]++;
            }
            List<Map.Entry<String, int[]>> maps = new ArrayList<>(byMap.entrySet());
            maps.sort((a, b) -> b.getValue()[1] != a.getValue()[1] ? b.getValue()[1] - a.getValue()[1] : b.getValue()[0] - a.getValue()[0]);
            gap(18);
            list.add(heading("by dungeon", null));
            gap(8);
            List<String[]> mrows = new ArrayList<>();
            mrows.add(new String[]{"Dungeon", "Bags", "White bags"});
            for (int i = 0; i < Math.min(12, maps.size()); i++) {
                Map.Entry<String, int[]> m = maps.get(i);
                mrows.add(new String[]{m.getKey(), String.valueOf(m.getValue()[0]), String.valueOf(m.getValue()[1])});
            }
            list.add(table(mrows));

            // ---- drop rates: white bags per run, from the runs the pup has seen
            List<RunLog.Run> runs = new ArrayList<>();
            for (RunLog.Run r : RunLog.all()) if (myRuns == null || everyAccount || myRuns.test(r)) runs.add(r);
            gap(18);
            list.add(heading("drop rates", null));
            gap(8);
            if (runs.isEmpty()) {
                list.add(note("Every dungeon run gets counted from now on (each visit of 20 seconds or more), so this shows "
                        + "how many runs a white bag takes in each dungeon, and how many runs it's been since your last one."));
            } else {
                long since = Long.MAX_VALUE;
                for (RunLog.Run r : runs) since = Math.min(since, r.at);
                Map<String, long[]> rate = new LinkedHashMap<>(); // {runs, whites, last white at, runs since last white}
                for (RunLog.Run r : runs) rate.computeIfAbsent(r.map, k -> new long[4])[0]++;
                for (LootStats.Entry e : all) {
                    if (e.at < since || LootStats.colour(e.type) != BagType.WHITE) continue;
                    long[] n = rate.get(e.map);
                    if (n == null) continue;
                    n[1]++;
                    n[2] = Math.max(n[2], e.at);
                }
                for (Map.Entry<String, long[]> e : rate.entrySet()) {
                    DryStreak.Streak st = streaks.get(e.getKey());
                    if (st != null) e.getValue()[3] = st.current;
                }
                List<Map.Entry<String, long[]>> rs = new ArrayList<>(rate.entrySet());
                rs.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
                List<String[]> rr = new ArrayList<>();
                rr.add(new String[]{"Dungeon", "Runs", "White bags", "1 white per", "Runs since last"});
                for (int i = 0; i < Math.min(15, rs.size()); i++) {
                    long[] n = rs.get(i).getValue();
                    String per = n[1] == 0 ? "-" : String.format(Locale.ROOT, "%.1f runs", n[0] / (double) n[1]);
                    String sinceLast = !streaks.containsKey(rs.get(i).getKey()) ? "-" // the realm: no dry streak
                            : n[1] == 0 ? n[3] + " (none yet)" : String.valueOf(n[3]);
                    rr.add(new String[]{rs.get(i).getKey(), String.valueOf(n[0]), String.valueOf(n[1]), per, sinceLast});
                }
                list.add(table(rr));
                gap(4);
                list.add(note("Counted since " + when(since * 1000L) + ". White bags count when they drop in that dungeon."));
            }

            // ---- best drops: white bags, newest first
            gap(18);
            list.add(heading("best drops", null));
            gap(8);
            int shown = 0;
            for (int i = all.size() - 1; i >= 0 && shown < 10; i--) {
                LootStats.Entry e = all.get(i);
                if (LootStats.colour(e.type) != BagType.WHITE) continue;
                shown++;
                JLabel l = small(e.type.displayName + " bag, " + (e.map.isEmpty() ? "somewhere" : e.map) + ", " + when(e.at * 1000L));
                list.add(l);
                gap(4);
                if (!e.items.isEmpty()) {
                    list.add(new AboutMe.ItemStrip(e.items, null));
                    gap(2);
                    list.add(wrap(names(e.items)));
                }
                gap(10);
            }
            if (shown == 0) list.add(note("No white bags yet. They'll be listed here when they drop."));
        }
    }

    // ================================================================== Events (RealmStock)

    static class EventsPage extends Page {
        /** Kept between redraws so typing isn't lost. */
        private final JTextField search = Pixel.field("", 18, "Find an event or dungeon");
        private boolean searching = false;

        EventsPage(EventTracker unused) {
            search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent ev) { searching = true; changed(); }
                public void removeUpdate(javax.swing.event.DocumentEvent ev) { searching = true; changed(); }
                public void changedUpdate(javax.swing.event.DocumentEvent ev) { }
            });
            RealmStockFeed.addListener(this::changed);
        }

        private static String time(long ms) {
            return new SimpleDateFormat("h:mm a", Locale.ROOT).format(new Date(ms));
        }

        private JComponent row(java.awt.Component... cs) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            p.setOpaque(false);
            for (java.awt.Component c : cs) p.add(c);
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
            return left(p);
        }

        private Pixel.Button button(String text, Runnable r) {
            Pixel.Button b = new Pixel.Button(text, false);
            b.addActionListener(e -> r.run());
            return b;
        }

        /** Tick boxes in two columns, for the names matching the search box. */
        private void ticks(List<String> names, java.util.function.Predicate<String> on, java.util.function.BiConsumer<String, Boolean> set) {
            String q = EventTracker.norm(search.getText());
            JPanel grid = new JPanel(new GridLayout(0, 2, 12, 0));
            grid.setOpaque(false);
            int n = 0;
            for (String name : names) {
                if (!q.isEmpty() && !EventTracker.norm(name).contains(q)) continue;
                Pixel.Check c = new Pixel.Check(name, on.test(name));
                c.addActionListener(x -> set.accept(name, c.isSelected()));
                grid.add(c);
                n++;
            }
            if (n == 0) {
                list.add(note(q.isEmpty() ? "The list hasn't loaded yet." : "Nothing matches \"" + search.getText().trim() + "\"."));
                return;
            }
            grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
            list.add(left(grid));
        }

        @Override
        void fill() {
            boolean on = RealmStockFeed.enabled();
            long ok = RealmStockFeed.lastOk();
            list.add(heading("events", on ? (ok > 0 ? "Updated " + time(ok) : RealmStockFeed.status()) : null));
            gap(6);
            list.add(note("Realm events, key pops and closing realms on every server, from RealmStock's event notifier "
                    + "(realmstock.com). It checks every 15 seconds while turned on. Ticked ones play a sound and go in the pup diary."));
            gap(8);
            Pixel.Check onBox = new Pixel.Check("Show the RealmStock feed", on);
            onBox.addActionListener(ev -> RealmStockFeed.setEnabled(onBox.isSelected()));
            Pixel.Button open = button("Open their page", () -> {
                try {
                    Desktop.getDesktop().browse(new java.net.URI(RealmStockFeed.PAGE_URL));
                } catch (Exception ignored) {
                }
            });
            list.add(row(onBox, Box.createHorizontalStrut(16), open));
            if (!on) return;
            if (ok == 0 || !RealmStockFeed.status().equals("Live")) {
                gap(6);
                list.add(note(RealmStockFeed.status()));
            }

            gap(12);
            String[] regions = {"ALL", "US", "EU", "AS", "AU"};
            int sel = java.util.Arrays.asList(regions).indexOf(RealmStockFeed.region());
            LogTabs.Segments reg = new LogTabs.Segments(new String[]{"All servers", "US", "EU", "Asia", "Australia"}, Math.max(0, sel),
                    i -> RealmStockFeed.setRegion(regions[i]));
            reg.setAlignmentX(Component.LEFT_ALIGNMENT);
            reg.setMaximumSize(new Dimension(Integer.MAX_VALUE, reg.getPreferredSize().height));
            list.add(reg);

            // ---- the feed
            gap(14);
            list.add(heading("latest", null));
            gap(6);
            boolean everything = RealmStockFeed.showAll();
            LogTabs.Segments show = new LogTabs.Segments(new String[]{"Only what I'm watching", "Everything"}, everything ? 1 : 0,
                    i -> RealmStockFeed.setShowAll(i == 1));
            show.setAlignmentX(Component.LEFT_ALIGNMENT);
            show.setMaximumSize(new Dimension(Integer.MAX_VALUE, show.getPreferredSize().height));
            list.add(show);
            gap(10);
            int shown = 0;
            for (RealmStockFeed.Event e : RealmStockFeed.events()) {
                if (!RealmStockFeed.inRegion(e)) continue;
                if (!everything && !RealmStockFeed.shouldPing(e)) continue;
                if (shown++ >= 40) break;
                JLabel title = small(time(e.atMs) + "   " + RealmStockFeed.headline(e));
                if (RealmStockFeed.shouldPing(e)) title.setForeground(Pixel.PINK_INK);
                list.add(title);
                gap(2);
                String d = RealmStockFeed.details(e);
                list.add(note(RealmStockFeed.where(e) + (d.isEmpty() ? "" : "  ·  " + d)));
                gap(8);
            }
            if (shown == 0) list.add(note(ok == 0 ? "Waiting for the first update..."
                    : everything ? "Nothing in this region right now."
                    : "None of the events you're watching are up right now. They'll show here (and ping you) as soon as one appears."));

            // ---- what to watch
            gap(16);
            list.add(heading("watching", null));
            gap(6);
            list.add(row(search));
            if (searching) SwingUtilities.invokeLater(() -> {
                search.requestFocusInWindow();
                searching = false;
            });
            gap(10);
            Pixel.Check snd = new Pixel.Check("Play a sound for ticked events", RealmStockFeed.sound());
            snd.addActionListener(x -> RealmStockFeed.setSound(snd.isSelected()));
            list.add(row(snd));
            list.add(note("The sound is a little two-bell chime and a squeak. To use your own, put a .wav file in the sounds "
                    + "folder next to the app: event.wav for realm events, key-pop.wav for key pops, realm-closed.wav for closing "
                    + "realms, or name it after one event or dungeon to give just that one its own sound, like cube-god.wav, "
                    + "the-shatters.wav or lost-halls.wav (lower case, dashes instead of spaces). Changes work straight away. "
                    + "Sounds also need Squeaks and boops ticked on the Home tab."));
            gap(10);
            Pixel.Check closed = new Pixel.Check("Realms closing", RealmStockFeed.pingClosed());
            closed.addActionListener(x -> RealmStockFeed.setPing("closed", closed.isSelected()));
            list.add(row(closed));

            gap(12);
            list.add(small("Realm events"));
            gap(4);
            list.add(row(button("Tick all", () -> RealmStockFeed.setAllEvents(true)), Box.createHorizontalStrut(8),
                    button("Untick all", () -> RealmStockFeed.setAllEvents(false))));
            gap(6);
            ticks(RealmStockFeed.eventNames(), RealmStockFeed::eventTicked, RealmStockFeed::setEventTicked);

            gap(14);
            list.add(small("Dungeon key pops"));
            gap(4);
            list.add(row(button("Exalt dungeons only", RealmStockFeed::resetKeyDungeons), Box.createHorizontalStrut(8),
                    button("Untick all", () -> RealmStockFeed.setAllKeys(false))));
            gap(6);
            ticks(RealmStockFeed.dungeonNames(), RealmStockFeed::keyTicked, RealmStockFeed::setKeyTicked);
        }
    }

    // ================================================================== Fame

    static class FamePage extends Page {
        private final MeTracker tracker;

        FamePage(MeTracker tracker) {
            this.tracker = tracker;
            // fame changes come with the character's updates; redraw now and then
            new Timer(5000, e -> changed()).start();
        }

        @Override
        void fill() {
            int now = tracker.fameNow();
            list.add(heading("fame", now >= 0 ? String.format(Locale.ROOT, "%,d fame now", now) : null));
            gap(10);
            long[] fs = tracker.fameSession();
            if (fs == null) {
                list.add(note("Fame you gain shows up here once your character has loaded: the total this session, "
                        + "fame per hour, and how much each dungeon or area gave you."));
                return;
            }
            double hours = fs[1] / 3_600_000.0;
            String perHour = fs[1] >= 60_000 ? String.format(Locale.ROOT, "%,d", Math.round(fs[0] / hours)) : "-";
            List<String[]> top = new ArrayList<>();
            top.add(new String[]{"This session", "Per hour", "Playing"});
            top.add(new String[]{String.format(Locale.ROOT, "%+,d", fs[0]), perHour, AboutMe.duration(fs[1])});
            list.add(table(top));

            List<long[]> samples = tracker.fameSamples();
            if (samples.size() >= 2) {
                gap(14);
                list.add(small("Fame over this session"));
                gap(4);
                list.add(left(new FameChart(samples)));
            }

            gap(16);
            list.add(heading("by area", null));
            gap(8);
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Area", "Fame", "Time", "Per hour"});
            long best = 0;
            String bestArea = null;
            Map<String, long[]> byArea = new LinkedHashMap<>(); // {fame, ms, visits}
            for (MeTracker.FameVisit v : tracker.fameVisits()) {
                int g = v.gained();
                if (g <= 0) continue;
                long ms = Math.max(1, v.endMs - v.startMs);
                long ph = ms >= 60_000 ? Math.round(g / (ms / 3_600_000.0)) : 0;
                if (ph > best) {
                    best = ph;
                    bestArea = v.area;
                }
                long[] t = byArea.computeIfAbsent(v.area, k -> new long[3]);
                t[0] += g;
                t[1] += ms;
                t[2]++;
                if (rows.size() <= 15) rows.add(new String[]{v.area, String.format(Locale.ROOT, "+%,d", g), AboutMe.duration(ms),
                        ph > 0 ? String.format(Locale.ROOT, "%,d", ph) : "-"});
            }
            if (rows.size() == 1) {
                list.add(note("No fame gained yet this session. Each dungeon run or trip to the realm gets a line here, newest first."));
                return;
            }
            if (bestArea != null) {
                list.add(small(String.format(Locale.ROOT, "Best run: %s, %,d fame per hour", bestArea, best)));
                gap(6);
            }
            list.add(table(rows));

            if (byArea.size() > 1) {
                gap(16);
                list.add(heading("totals by area", null));
                gap(8);
                List<Map.Entry<String, long[]>> es = new ArrayList<>(byArea.entrySet());
                es.sort((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));
                List<String[]> tr = new ArrayList<>();
                tr.add(new String[]{"Area", "Visits", "Fame", "Per hour"});
                for (Map.Entry<String, long[]> e : es) {
                    long[] t = e.getValue();
                    tr.add(new String[]{e.getKey(), String.valueOf(t[2]), String.format(Locale.ROOT, "+%,d", t[0]),
                            t[1] >= 60_000 ? String.format(Locale.ROOT, "%,d", Math.round(t[0] / (t[1] / 3_600_000.0))) : "-"});
                }
                list.add(table(tr));
            }
        }
    }

    /** A small step chart of fame over the session. */
    static class FameChart extends JComponent {
        private final List<long[]> s;

        FameChart(List<long[]> samples) {
            this.s = samples;
        }

        @Override public Dimension getPreferredSize() { return new Dimension(420, 120); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 120); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Cute.smooth(g);
            int w = Math.min(getWidth(), 640), h = getHeight();
            int left = 6, right = w - 70, top = 8, bottom = h - 22;
            long t0 = s.get(0)[0], t1 = Math.max(t0 + 1, s.get(s.size() - 1)[0]);
            long f0 = s.get(0)[1], f1 = Math.max(f0 + 1, s.get(s.size() - 1)[1]);
            Pixel.frame(g, 0, 0, right + 8, h - 14, Pixel.SHEET, Pixel.LINE, 2);
            java.awt.geom.Path2D line = new java.awt.geom.Path2D.Double();
            java.awt.geom.Path2D area = new java.awt.geom.Path2D.Double();
            double px = left, py = bottom;
            for (int i = 0; i < s.size(); i++) {
                double x = left + (right - left) * (double) (s.get(i)[0] - t0) / (t1 - t0);
                double y = bottom - (bottom - top) * (double) (s.get(i)[1] - f0) / (f1 - f0);
                if (i == 0) {
                    line.moveTo(x, y);
                    area.moveTo(x, bottom);
                    area.lineTo(x, y);
                } else {
                    line.lineTo(x, py); // steps: fame jumps when you earn it
                    line.lineTo(x, y);
                    area.lineTo(x, py);
                    area.lineTo(x, y);
                }
                px = x;
                py = y;
            }
            area.lineTo(px, bottom);
            area.closePath();
            g.setColor(new Color(Pixel.PINK.getRed(), Pixel.PINK.getGreen(), Pixel.PINK.getBlue(), 60));
            g.fill(area);
            g.setColor(Pixel.PINK_INK);
            g.setStroke(new BasicStroke(2f));
            g.draw(line);
            g.setFont(Cute.font(Font.BOLD, 11));
            g.setColor(Pixel.INK);
            g.drawString(String.format(Locale.ROOT, "%,d", f1), right + 12, top + 8);
            g.drawString(String.format(Locale.ROOT, "%,d", f0), right + 12, bottom);
            g.drawString(new SimpleDateFormat("h:mm a", Locale.ROOT).format(new Date(t0)), left, h - 2);
            String end = new SimpleDateFormat("h:mm a", Locale.ROOT).format(new Date(t1));
            g.drawString(end, right - g.getFontMetrics().stringWidth(end), h - 2);
            g.dispose();
        }
    }

    // ================================================================== Trades

    static class TradesPage extends Page {
        private final java.util.function.Predicate<TradeLog.Trade> mine;

        TradesPage(java.util.function.Predicate<TradeLog.Trade> mine) {
            this.mine = mine;
        }

        private static AboutMe.ItemStrip strip(List<Integer> ids, List<Enchant> rarity) {
            AboutMe.ItemStrip s = new AboutMe.ItemStrip(ids, null);
            s.enchants = rarity.toArray(new Enchant[0]);
            return s;
        }

        @Override
        void fill() {
            List<TradeLog.Trade> all = new ArrayList<>();
            for (TradeLog.Trade t : TradeLog.all()) if (mine == null || mine.test(t)) all.add(t);
            list.add(heading("trades", all.isEmpty() ? null : all.size() + (all.size() == 1 ? " trade" : " trades")));
            gap(10);
            if (all.isEmpty()) {
                list.add(note("Every trade you finish is written down here: who with, what you gave and what you got "
                        + "(with each item's rarity). It's kept in trade-log.txt next to the app."));
                return;
            }
            int shown = 0;
            for (int i = all.size() - 1; i >= 0 && shown < 40; i--, shown++) {
                TradeLog.Trade t = all.get(i);
                list.add(small(when(t.at * 1000L) + "   with " + (t.partner.isEmpty() ? "someone" : t.partner)));
                gap(4);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                row.setOpaque(false);
                JLabel gave = Pixel.label("Gave ");
                row.add(gave);
                if (t.gave.isEmpty()) row.add(Pixel.label("nothing"));
                else for (int k = 0; k < t.gave.size(); k += 8)
                    row.add(strip(t.gave.subList(k, Math.min(t.gave.size(), k + 8)), t.gaveRarity.subList(k, Math.min(t.gave.size(), k + 8))));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
                list.add(left(row));
                gap(2);
                if (!t.gave.isEmpty()) list.add(wrap(names(t.gave)));
                gap(4);
                JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                row2.setOpaque(false);
                row2.add(Pixel.label("Got "));
                if (t.got.isEmpty()) row2.add(Pixel.label("nothing"));
                else for (int k = 0; k < t.got.size(); k += 8)
                    row2.add(strip(t.got.subList(k, Math.min(t.got.size(), k + 8)), t.gotRarity.subList(k, Math.min(t.got.size(), k + 8))));
                row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, row2.getPreferredSize().height));
                list.add(left(row2));
                gap(2);
                if (!t.got.isEmpty()) list.add(wrap(names(t.got)));
                gap(14);
            }
        }
    }
}
