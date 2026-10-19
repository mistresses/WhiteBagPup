package whitebag;

import assets.IdToAsset;
import packets.data.QuestData;
import packets.data.enums.StatType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The "about me" tab: your character, your pet and your Tinkerer quests. */
final class AboutMe {
    private AboutMe() {
    }

    static class Panel extends JPanel {
        private final MeTracker tracker;
        private final LogTabs.WidthPanel list = new LogTabs.WidthPanel(null);
        private final Timer refresh;

        Panel(MeTracker tracker) {
            super(new BorderLayout());
            this.tracker = tracker;
            setOpaque(false);
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            list.setBackground(Pixel.SHEET);
            list.setBorder(new EmptyBorder(14, 16, 16, 16));
            add(Pixel.scroll(list), BorderLayout.CENTER);
            refresh = new Timer(1200, e -> rebuild());
            refresh.setRepeats(false);
            // keep the play time ticking along
            Timer minute = new Timer(60_000, e -> changed());
            minute.start();
            // catch up when the tab is opened
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing() && stale) refresh.restart();
            });
            rebuild();
        }

        private boolean stale = false;

        /** Called from the tracker (any thread). Only redraws while the tab is on screen. */
        void changed() {
            SwingUtilities.invokeLater(() -> {
                stale = true;
                if (isShowing() && !refresh.isRunning()) refresh.restart();
            });
        }


        private void rebuild() {
            if (feedField.isFocusOwner()) { // don't pull the box away while you're typing in it
                refresh.restart();
                return;
            }
            stale = false;
            list.removeAll();
            MeTracker.Thing me = tracker.character();
            MeTracker.Thing pet = tracker.pet();
            List<QuestData> quests = tracker.quests();
            List<Integer> carried = tracker.carried();

            // ---- character
            list.add(heading("character", null));
            gap(10);
            if (me == null) {
                list.add(note("Start watching, then load into the game (or change area once) and your character shows up here."));
            } else {
                String cls = objectName(me.objectType, "Character");
                String name = me.s(StatType.NAME_STAT);
                int fame = me.i(StatType.CURR_FAME_STAT, -1);
                String guild = me.s(StatType.GUILD_NAME_STAT);
                String line2 = "Level " + me.i(StatType.LEVEL_STAT, 1) + " " + cls;
                String line3 = (fame >= 0 ? String.format(Locale.ROOT, "%,d fame", fame) : "")
                        + (guild.isEmpty() ? "" : (fame >= 0 ? "  ·  " : "") + "Guild: " + guild);
                list.add(new Card(me.objectType, name.isEmpty() ? cls : name, line2, line3));
                long[] fs = tracker.fameSession();
                if (fs != null && fs[1] > 60_000) {
                    double perHour = fs[0] / (fs[1] / 3_600_000.0);
                    gap(6);
                    list.add(small(String.format(Locale.ROOT, "%+,d fame this session  ·  about %,d per hour  (%s playing, more on the Fame tab)",
                            fs[0], Math.round(perHour), duration(fs[1]))));
                }
                long[] pt = tracker.playTime();
                {
                    gap(6);
                    list.add(small("Play time:  " + duration(pt[0]) + " this session  ·  " + duration(pt[1]) + " today  ·  "
                            + duration(pt[2]) + " this week  ·  " + duration(pt[3]) + " in total"));
                }
                gap(10);
                int[] max = ClassMax.of(me.objectType);
                list.add(statsGrid(me, max));
                if (max != null) {
                    gap(4);
                    list.add(note(maxSummary(me, max)));
                }
                int[] ex = tracker.exalts(me.objectType);
                if (ex != null) {
                    gap(12);
                    list.add(small("Exaltations for " + cls));
                    gap(4);
                    list.add(exaltGrid(ex));
                }
                gap(12);
                list.add(small("Equipped"));
                gap(4);
                List<Integer> gear = new ArrayList<>();
                for (StatType t : new StatType[]{StatType.INVENTORY_0_STAT, StatType.INVENTORY_1_STAT, StatType.INVENTORY_2_STAT, StatType.INVENTORY_3_STAT}) {
                    gear.add(me.i(t, -1));
                }
                ItemStrip gearStrip = new ItemStrip(gear, null);
                // enchantments: the character's item data has one entry per slot (weapon, ability, armour, ring)
                String[] data = me.s(StatType.UNIQUE_DATA_STRING).split(",", -1);
                gearStrip.enchants = new Enchant[gear.size()];
                gearStrip.enchantText = new String[gear.size()];
                StringBuilder enchLines = new StringBuilder();
                for (int k = 0; k < gear.size(); k++) {
                    if (gear.get(k) <= 0) continue;
                    String entry = k < data.length ? data[k] : null;
                    java.util.List<Integer> slots = Enchant.slots(entry);
                    Enchant rarity = slots == null ? Enchant.NONE : slots.size() >= 1 && slots.size() <= 4 ? Enchant.values()[slots.size()] : Enchant.NONE;
                    gearStrip.enchants[k] = rarity;
                    if (slots == null || slots.isEmpty()) continue;
                    StringBuilder t = new StringBuilder();
                    for (int id : slots) {
                        if (t.length() > 0) t.append("\n");
                        t.append(id < 0 ? "(empty slot)" : EnchantNames.line(id));
                    }
                    gearStrip.enchantText[k] = t.toString();
                    ItemInfo it = ItemInfo.of(gear.get(k));
                    if (enchLines.length() > 0) enchLines.append("\n");
                    enchLines.append(it.name).append(" (").append(rarity.label.isEmpty() ? slots.size() + " slots" : rarity.label).append("):");
                    for (int id : slots) enchLines.append("\n   - ").append(id < 0 ? "(empty slot)" : EnchantNames.line(id));
                }
                list.add(gearStrip);
                gap(4);
                list.add(wrap(names(gear)));
                if (enchLines.length() > 0) {
                    gap(8);
                    list.add(small("Enchantments"));
                    gap(4);
                    list.add(wrap(enchLines.toString()));
                }
            }

            // ---- pet
            gap(22);
            list.add(heading("pet", null));
            gap(10);
            PetInfo info = tracker.petInfo();
            if (pet == null && info == null) {
                list.add(note(me == null ? "Your pet shows up here once your character has loaded."
                        : "Your pet shows up here when it's out and in view next to you."));
            } else {
                // what the pet looks like (its skin, e.g. "Lil' Forest Harpy"); fall back to its base type
                int skin = info != null && info.skin > 0 ? info.skin : pet != null ? pet.i(StatType.SKIN_ID, -1) : -1;
                int base = info != null && info.type > 0 ? info.type : pet != null ? pet.objectType : -1;
                int look = skin > 0 && !objectName(skin, "").isEmpty() ? skin : base;
                String kind = objectName(look, "Pet");
                // like the game's pet window: the skin's name (the stored pet name is just its base type, e.g. "Gummy Bear")
                String name = kind;
                int rarityNum = info != null && info.rarity >= 0 ? info.rarity : pet != null ? pet.i(StatType.PET_RARITY_STAT, -1) : -1;
                String rarity = rarity(rarityNum);
                String line2 = rarity.isEmpty() ? (name.equals(kind) ? "Pet" : kind) : rarity + (name.equals(kind) ? "" : " " + kind);
                int max = info != null && info.maxLevel > 0 ? info.maxLevel : pet != null ? pet.i(StatType.PET_MAX_ABILITY_POWER_STAT, -1) : -1;
                list.add(new Card(look, name, line2, max > 0 ? "Max ability level " + max : ""));

                boolean anyAbility = false;
                if (info != null && info.hasAbilities()) {
                    for (int k = 0; k < 3; k++) {
                        int lvl = info.abilityLevel[k];
                        if (lvl < 0) continue;
                        if (!anyAbility) gap(10);
                        anyAbility = true;
                        String lvlText = max > 0 && lvl >= max ? "Lvl Max" : "Lvl " + lvl + (max > 0 ? "/" + max : "");
                        list.add(new AbilityBar(abilityName(info.abilityType[k], k), lvlText, max > 0 ? Math.min(1.0, lvl / (double) max) : 0));
                        gap(6);
                    }
                    addFeedPlanner(info);
                    gap(2);
                    list.add(note("Abilities as of " + new SimpleDateFormat("d MMM, h:mm a", Locale.ROOT).format(new Date(info.learnedAt))
                            + ". They refresh when you visit the Daily Quest Room or the Pet Yard."));
                }
                if (!anyAbility) {
                    gap(8);
                    list.add(note("The game doesn't send your own pet's abilities while you play. Visit the Daily Quest Room "
                            + "(where the Tinkerer is) or the Pet Yard once and they'll show up here, and be remembered after that."));
                }
                String why = tracker.petNote();
                if (!why.isEmpty()) {
                    gap(4);
                    list.add(note(why));
                }
            }

            // ---- tinker quests
            gap(22);
            long at = tracker.questsAt();
            list.add(heading("tinker quests", at == 0 ? null : "Updated " + new SimpleDateFormat("h:mm a", Locale.ROOT).format(new Date(at))));
            gap(10);
            if (at == 0) {
                list.add(note("Open the Tinkerer's quest window in game and your quests show up here. "
                        + "The game only sends them when you open that window."));
            } else if (quests.isEmpty()) {
                list.add(note("The Tinkerer has no quests for you right now."));
            } else {
                boolean first = true;
                for (QuestData q : quests) {
                    if (!first) {
                        gap(10);
                        list.add(new Divider());
                        gap(10);
                    }
                    first = false;
                    addQuest(q, carried);
                }
            }
            list.add(Box.createVerticalGlue());
            list.revalidate();
            list.repaint();
        }

        private final JTextField feedField = Pixel.field(String.valueOf(PetInfo.feedPower), 5, "500");

        {
            feedField.setToolTipText("The feed power of the food you give your pet (it shows when you hover the item in game)");
            feedField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { feedChanged(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { feedChanged(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { feedChanged(); }
            });
        }

        private void feedChanged() {
            try {
                int v = Integer.parseInt(feedField.getText().trim());
                if (v > 0 && v != PetInfo.feedPower) {
                    PetInfo.setFeedPower(v);
                    changed();
                }
            } catch (NumberFormatException ignored) {
            }
        }

        /** How many feeds (and how much fame) to max each ability, like Puppy's pet tab. */
        private void addFeedPlanner(PetInfo info) {
            boolean header = false;
            int fameEach = PetInfo.feedFame(info.maxLevel);
            for (int k = 0; k < 3; k++) {
                if (!info.unlocked(k) || info.abilityLevel[k] < 0 || info.abilityLevel[k] >= info.maxLevel) continue;
                int need = info.feedPointsToMax(k);
                if (need < 0) continue;
                if (!header) {
                    gap(6);
                    list.add(small("Feeding planner"));
                    gap(4);
                    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                    row.setOpaque(false);
                    row.add(Pixel.label("Feed power of your food "));
                    row.add(Box.createHorizontalStrut(8));
                    row.add(feedField);
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
                    list.add(left(row));
                    gap(6);
                    header = true;
                }
                int feeds = (int) Math.ceil(need / (double) Math.max(1, PetInfo.feedPower));
                String fame = fameEach > 0 ? String.format(Locale.ROOT, ", %,d fame to feed", (long) feeds * fameEach) : "";
                list.add(wrap(String.format(Locale.ROOT, "%s to %d: %,d feed points, about %,d feeds%s.",
                        abilityName(info.abilityType[k], k), info.maxLevel, need, feeds, fame)));
                gap(2);
            }
        }

        private void addQuest(QuestData q, List<Integer> carried) {
            String title = q.name == null || q.name.isEmpty() ? "Quest" : q.name;
            JLabel name = new JLabel(title + (q.completed ? "   (done)" : ""));
            name.setFont(Cute.font(Font.BOLD, 14));
            name.setForeground(q.completed ? Pixel.TEAL : Pixel.INK);
            left(name);
            list.add(name);
            if (q.description != null && !q.description.trim().isEmpty()) {
                gap(2);
                list.add(wrap(q.description.trim()));
            }
            List<Integer> req = ids(q.requirements);
            if (!req.isEmpty()) {
                // which requirements you're already carrying (each carried item counts once)
                Map<Integer, Integer> pool = new HashMap<>();
                for (int id : carried) pool.merge(id, 1, Integer::sum);
                boolean[] have = new boolean[req.size()];
                boolean[] crossed = new boolean[req.size()];
                String key = tracker.accountId() + "/" + questKey(q);
                int done = 0;
                for (int i = 0; i < req.size(); i++) {
                    Integer n = pool.get(req.get(i));
                    if (n != null && n > 0) {
                        have[i] = true;
                        pool.put(req.get(i), n - 1);
                    }
                    crossed[i] = Ticks.has(key, i);
                    if (have[i] || crossed[i]) done++;
                }
                gap(8);
                list.add(small("Bring  (" + done + " of " + req.size() + " done, click a box to cross it off)"));
                gap(4);
                ItemStrip strip = new ItemStrip(req, have);
                strip.crossed = crossed;
                strip.onClick = i -> {
                    Ticks.toggle(key, i);
                    rebuild();
                };
                list.add(strip);
                gap(4);
                list.add(wrap(names(req)));
            }
            List<Integer> rew = ids(q.rewards);
            if (!rew.isEmpty()) {
                gap(8);
                list.add(small(q.itemOfChoice ? "Reward  (pick one)" : "Reward"));
                gap(4);
                list.add(new ItemStrip(rew, null));
                gap(4);
                list.add(wrap(names(rew)));
            }
        }

        private void gap(int h) {
            list.add(Box.createVerticalStrut(h));
        }
    }

    // ------------------------------------------------------------------ crossed-off boxes

    private static String questKey(QuestData q) {
        String id = q.id != null && !q.id.isEmpty() ? q.id : q.name;
        return id == null ? "quest" : id.replace('\n', ' ').replace('|', '/');
    }

    /** Requirement boxes you've crossed off, kept in tinker-ticks.txt next to the jar so they survive restarts. */
    static final class Ticks {
        private static final String FILE = "tinker-ticks.txt";
        private static java.util.Set<String> set;

        private static synchronized java.util.Set<String> set() {
            if (set == null) {
                set = new java.util.LinkedHashSet<>();
                try {
                    for (String l : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(FILE), java.nio.charset.StandardCharsets.UTF_8)) {
                        if (!l.trim().isEmpty()) set.add(l.trim());
                    }
                } catch (Exception ignored) {
                    // no file yet
                }
            }
            return set;
        }

        static synchronized boolean has(String quest, int index) {
            return set().contains(quest + "|" + index);
        }

        static synchronized void toggle(String quest, int index) {
            String k = quest + "|" + index;
            if (!set().remove(k)) set().add(k);
            try {
                java.nio.file.Files.write(java.nio.file.Paths.get(FILE), set(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A small table; the first row is bold. */
    static JComponent fameTable(java.util.List<String[]> rows) {
        JPanel grid = new JPanel(new GridLayout(rows.size(), rows.get(0).length, 18, 2));
        grid.setOpaque(false);
        for (int r = 0; r < rows.size(); r++) {
            for (String cell : rows.get(r)) {
                JLabel l = new JLabel(cell);
                l.setFont(Cute.font(r == 0 ? Font.BOLD : Font.PLAIN, 12));
                l.setForeground(r > 0 && cell.startsWith("+") ? Pixel.TEAL.darker() : Pixel.INK);
                grid.add(l);
            }
        }
        JPanel wrapPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapPanel.setOpaque(false);
        wrapPanel.add(grid);
        wrapPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        return left(wrapPanel);
    }

    static Pixel.Heading heading(String text, String right) {
        Pixel.Heading h = new Pixel.Heading(text, 2);
        if (right != null) h.setRight(right);
        return h;
    }

    static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    static JLabel small(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Cute.font(Font.BOLD, 12));
        l.setForeground(Pixel.INK);
        return left(l);
    }

    static JComponent note(String text) {
        JTextArea a = wrap(text);
        a.setForeground(Pixel.MUTED);
        return a;
    }

    /** Wrapping text that sits on the card. */
    static JTextArea wrap(String text) {
        JTextArea a = new JTextArea(text);
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setBorder(null);
        a.setFont(Cute.font(Font.PLAIN, 12));
        a.setForeground(Pixel.INK);
        a.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        // plain text, never a cursor: don't let it scroll the page to itself
        if (a.getCaret() instanceof javax.swing.text.DefaultCaret) {
            ((javax.swing.text.DefaultCaret) a.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        }
        return left(a);
    }

    private static final StatType[][] STATS = {
            {StatType.MAX_HP_STAT, StatType.MAX_HP_BOOST_STAT}, {StatType.MAX_MP_STAT, StatType.MAX_MP_BOOST_STAT},
            {StatType.ATTACK_STAT, StatType.ATTACK_BOOST_STAT}, {StatType.DEFENSE_STAT, StatType.DEFENSE_BOOST_STAT},
            {StatType.SPEED_STAT, StatType.SPEED_BOOST_STAT}, {StatType.DEXTERITY_STAT, StatType.DEXTERITY_BOOST_STAT},
            {StatType.VITALITY_STAT, StatType.VITALITY_BOOST_STAT}, {StatType.WISDOM_STAT, StatType.WISDOM_BOOST_STAT}};
    private static final String[] STAT_NAMES = {"HP", "MP", "ATT", "DEF", "SPD", "DEX", "VIT", "WIS"};

    /** Base stat (without gear boosts), like Puppy: total minus boost. */
    private static int base(MeTracker.Thing me, int k) {
        int total = me.i(STATS[k][0], -1);
        return total < 0 ? -1 : total - me.i(STATS[k][1], 0);
    }

    private static JComponent statsGrid(MeTracker.Thing me, int[] max) {
        JPanel grid = new JPanel(new GridLayout(2, 4, 18, 4));
        grid.setOpaque(false);
        for (int k = 0; k < 8; k++) grid.add(stat(STAT_NAMES[k], base(me, k), me.i(STATS[k][1], 0), max == null ? 0 : max[k]));
        JPanel wrapPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapPanel.setOpaque(false);
        wrapPanel.add(grid);
        wrapPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        return left(wrapPanel);
    }

    /** e.g. "DEX 60/75 (+4)", or "ATT 75 MAX" in green when maxed. */
    private static JLabel stat(String name, int base, int boost, int max) {
        String b = boost != 0 ? " <span style='color:#1F8A72'>(" + (boost > 0 ? "+" : "") + boost + ")</span>" : "";
        String v;
        if (base < 0) v = "?";
        else if (max <= 0) v = String.valueOf(base);
        else if (base >= max) v = "<span style='color:#1F8A72'><b>" + base + " MAX</b></span>";
        else v = base + "/" + max;
        JLabel l = new JLabel("<html><b>" + name + "</b>&nbsp; " + v + b + "</html>");
        l.setFont(Cute.font(Font.PLAIN, 13));
        l.setForeground(Pixel.INK);
        if (max > 0 && base >= 0 && base < max) {
            int pots = k(name) < 2 ? (int) Math.ceil((max - base) / 5.0) : max - base;
            l.setToolTipText(pots + (pots == 1 ? " potion" : " potions") + " to max");
        }
        return l;
    }

    private static int k(String name) {
        for (int i = 0; i < STAT_NAMES.length; i++) if (STAT_NAMES[i].equals(name)) return i;
        return -1;
    }

    /** "6/8 stats maxed. Still to drink: 3 DEX, 2 WIS" (HP and MP potions give 5 each). */
    private static String maxSummary(MeTracker.Thing me, int[] max) {
        int maxed = 0;
        StringBuilder need = new StringBuilder();
        for (int k = 0; k < 8; k++) {
            int b = base(me, k);
            if (b < 0 || max[k] <= 0) continue;
            if (b >= max[k]) {
                maxed++;
                continue;
            }
            int pots = k < 2 ? (int) Math.ceil((max[k] - b) / 5.0) : max[k] - b;
            if (need.length() > 0) need.append(", ");
            need.append(pots).append(' ').append(STAT_NAMES[k]);
        }
        return maxed + "/8 stats maxed" + (need.length() == 0 ? ". Fully maxed!" : ". Potions still to drink: " + need + ".");
    }

    /** Exaltation progress per stat (counts up to 75); each level gives +1 to the stat, or +5 for HP and MP. */
    private static JComponent exaltGrid(int[] ex) {
        JPanel grid = new JPanel(new GridLayout(2, 4, 18, 4));
        grid.setOpaque(false);
        for (int k = 0; k < 8; k++) {
            int v = Math.max(0, ex[k]);
            int lvl = exaltLevel(v);
            int bonus = k < 2 ? lvl * 5 : lvl;
            String col = v >= 75 ? "#1F8A72" : "#000000";
            JLabel l = new JLabel("<html><b>" + STAT_NAMES[k] + "</b>&nbsp; <span style='color:" + col + "'>+" + bonus + "</span>"
                    + "&nbsp;<span style='font-size:90%'>(" + Math.min(v, 75) + "/75)</span></html>");
            l.setFont(Cute.font(Font.PLAIN, 13));
            l.setForeground(Pixel.INK);
            l.setToolTipText("Exaltation level " + lvl + " of 5" + (v < 75 ? ", " + (nextExalt(v) - v) + " more to the next level" : ", fully exalted"));
            grid.add(l);
        }
        JPanel wrapPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapPanel.setOpaque(false);
        wrapPanel.add(grid);
        wrapPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        return left(wrapPanel);
    }

    private static final int[] EXALT_STEPS = {5, 15, 30, 50, 75};

    private static int exaltLevel(int v) {
        int l = 0;
        for (int step : EXALT_STEPS) if (v >= step) l++;
        return l;
    }

    private static int nextExalt(int v) {
        for (int step : EXALT_STEPS) if (v < step) return step;
        return 75;
    }

    static String duration(long ms) {
        long m = ms / 60_000;
        return m < 60 ? m + " min" : (m / 60) + " h " + (m % 60) + " min";
    }

    private static List<Integer> ids(int[] a) {
        List<Integer> out = new ArrayList<>();
        if (a != null) for (int i : a) if (i > 0) out.add(i);
        return out;
    }

    static String names(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int id : ids) {
            if (sb.length() > 0) sb.append(", ");
            if (id <= 0) {
                sb.append("(empty)");
            } else {
                ItemInfo i = ItemInfo.of(id);
                sb.append(i.name).append(i.rarity.isEmpty() ? "" : " (" + i.rarity + ")");
            }
        }
        return sb.toString();
    }

    static String objectName(int type, String fallback) {
        if (type < 0) return fallback;
        try {
            String n = IdToAsset.objectName(type);
            if (n != null && !n.isEmpty()) return n;
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    /** Pet ability names by id (ids as used by Puppy). */
    private static String abilityName(int type, int slot) {
        switch (type) {
            case 402: return "Attack Close";
            case 404: return "Attack Mid";
            case 405: return "Attack Far";
            case 406: return "Electric";
            case 407: return "Heal";
            case 408: return "Magic Heal";
            case 409: return "Savage";
            case 410: return "Decoy";
            case 411: return "Rising Fury";
            default: return "Ability " + (slot + 1);
        }
    }

    private static String rarity(int r) {
        switch (r) {
            case 0: return "Common";
            case 1: return "Uncommon";
            case 2: return "Rare";
            case 3: return "Legendary";
            case 4: return "Divine";
            default: return "";
        }
    }

    static BufferedImage sprite(int type, int box) {
        if (type <= 0) return null;
        try {
            return LootImage.pixelIcon(LootImage.sprites.sprite(type), box);
        } catch (Throwable e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ painted bits

    /** A picture in a slot with up to three lines of text beside it. */
    static class Card extends JComponent {
        private final BufferedImage icon;
        private final String title, line2, line3;

        Card(int objectType, String title, String line2, String line3) {
            this.icon = sprite(objectType, 48);
            this.title = title;
            this.line2 = line2;
            this.line3 = line3;
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(300, 64); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 64); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Pixel.frame(g, 0, 0, 64, 64, Pixel.PINK_SOFT, Pixel.LINE, 2);
            if (icon != null) g.drawImage(icon, (64 - icon.getWidth()) / 2, (64 - icon.getHeight()) / 2, null);
            Cute.smooth(g);
            int tx = 80;
            g.setColor(Pixel.INK);
            g.setFont(Cute.font(Font.BOLD, 16));
            g.drawString(title, tx, 18);
            g.setFont(Cute.font(Font.PLAIN, 13));
            g.drawString(line2, tx, 38);
            if (line3 != null && !line3.isEmpty()) g.drawString(line3, tx, 56);
            g.dispose();
        }
    }

    /** A row of item slots; with {@code have}, each slot gets a tick or a cross. */
    static class ItemStrip extends JComponent {
        private static final int SLOT = 44, GAP = 6;
        private final List<Integer> ids;
        private final boolean[] have;
        private final List<BufferedImage> icons = new ArrayList<>();
        private final List<ItemInfo> infos = new ArrayList<>();
        /** Boxes you've crossed off by clicking (optional). */
        boolean[] crossed;
        /** Called with the box index when clicked (optional; makes the boxes clickable). */
        java.util.function.IntConsumer onClick;
        /** Each item's rarity from its enchantment slots (optional). */
        Enchant[] enchants;
        /** Tooltip lines for each item's enchantments (optional). */
        String[] enchantText;

        ItemStrip(List<Integer> ids, boolean[] have) {
            this.ids = ids;
            this.have = have;
            for (int id : ids) {
                icons.add(sprite(id, 32));
                infos.add(id > 0 ? ItemInfo.of(id) : null);
            }
            setAlignmentX(LEFT_ALIGNMENT);
            setToolTipText(""); // per-slot tooltips below
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int i = hit(e);
                    if (onClick != null && i >= 0) {
                        Squeak.boop();
                        onClick.accept(i);
                    }
                }
            });
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    setCursor(onClick != null && hit(e) >= 0 ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                }
            });
        }

        private int hit(MouseEvent e) {
            int i = e.getX() / (SLOT + GAP);
            boolean inSlot = e.getX() - i * (SLOT + GAP) < SLOT && e.getY() < SLOT;
            return inSlot && i >= 0 && i < ids.size() ? i : -1;
        }

        private boolean isCrossed(int i) {
            return crossed != null && i < crossed.length && crossed[i];
        }

        @Override public Dimension getPreferredSize() { return new Dimension(Math.max(1, ids.size() * (SLOT + GAP)), SLOT + 4); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, SLOT + 4); }

        @Override
        public String getToolTipText(MouseEvent e) {
            int i = e.getX() / (SLOT + GAP);
            if (i < 0 || i >= infos.size() || infos.get(i) == null) return null;
            ItemInfo info = infos.get(i);
            Enchant en = enchants != null && i < enchants.length ? enchants[i] : null;
            String extra = enchantText != null && i < enchantText.length && enchantText[i] != null && !enchantText[i].isEmpty()
                    ? "<br>" + enchantText[i].replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") : "";
            if (en != null && en != Enchant.NONE || !extra.isEmpty()) {
                return "<html><b>" + info.name.replace("<", "&lt;") + "</b>" + (info.rarity.isEmpty() ? "" : " (" + info.rarity + ")")
                        + (en != null && en != Enchant.NONE ? " &middot; " + en.label : "") + extra + "</html>";
            }
            return info.name + (info.rarity.isEmpty() ? "" : " (" + info.rarity + ")")
                    + (have == null ? "" : have[i] ? " - you're carrying it" : " - not in your inventory")
                    + (onClick == null ? "" : isCrossed(i) ? " (crossed off, click to undo)" : " (click to cross off)");
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            int x = 0;
            for (int i = 0; i < ids.size(); i++) {
                ItemInfo item = infos.get(i);
                boolean special = item != null && !item.rarity.isEmpty();
                boolean cross = isCrossed(i);
                boolean done = cross || (have != null && have[i]);
                Color fill = done ? new Color(0xDDF5EC) : Pixel.SHEET;
                Enchant en = enchants != null && i < enchants.length && enchants[i] != null ? enchants[i] : Enchant.NONE;
                Color edge = en != Enchant.NONE ? en.color : item != null ? item.rarityColor : Pixel.LINE;
                boolean tinted = (special || en != Enchant.NONE) && !done;
                Pixel.frame(g, x, 0, SLOT, SLOT, tinted ? Cute.mix(Color.WHITE, edge, 0.14) : fill,
                        (special || en != Enchant.NONE) && !cross ? edge : Pixel.LINE, 2);
                if (en != Enchant.NONE) {
                    // one dot per enchantment slot, like the game's slot icon
                    for (int k = 0; k < en.ordinal(); k++) {
                        g.setColor(Color.WHITE);
                        g.fillRect(x + 3 + k * 7, SLOT - 9, 6, 6);
                        g.setColor(en.color);
                        g.fillRect(x + 4 + k * 7, SLOT - 8, 4, 4);
                    }
                }
                BufferedImage ic = icons.get(i);
                if (ic != null) {
                    Composite old = g.getComposite();
                    if (cross) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f)); // faded when crossed off
                    g.drawImage(ic, x + (SLOT - ic.getWidth()) / 2, (SLOT - ic.getHeight()) / 2, null);
                    g.setComposite(old);
                } else if (item != null) {
                    Cute.smooth(g);
                    g.setFont(Cute.font(Font.BOLD, 9));
                    g.setColor(Pixel.INK);
                    g.drawString(item.name.length() > 7 ? item.name.substring(0, 7) : item.name, x + 4, SLOT / 2 + 3);
                }
                if (cross) {
                    // a thick pixel line through the box
                    g.setColor(Pixel.INK);
                    for (int k = 4; k < SLOT - 4; k += 2) g.fillRect(x + k, SLOT - 2 - k, 3, 3);
                }
                if (have != null) {
                    // tick or cross in the corner
                    Pixel.frame(g, x + SLOT - 16, 2, 14, 14, done ? Pixel.TEAL : Pixel.PINK, null, 0);
                    g.setColor(Color.WHITE);
                    if (done) {
                        int[][] tick = {{4, 7}, {5, 8}, {6, 9}, {7, 8}, {8, 7}, {9, 6}, {10, 5}};
                        for (int[] t : tick) g.fillRect(x + SLOT - 16 + t[0] - 1, 2 + t[1] - 1, 2, 2);
                    } else {
                        for (int k = 0; k < 6; k++) {
                            g.fillRect(x + SLOT - 16 + 4 + k, 2 + 4 + k, 2, 2);
                            g.fillRect(x + SLOT - 16 + 9 - k, 2 + 4 + k, 2, 2);
                        }
                    }
                }
                x += SLOT + GAP;
            }
            g.dispose();
        }
    }

    /** One pet ability: name and level, with a stepped bar like the game's pet window. */
    static class AbilityBar extends JComponent {
        private final String name, level;
        private final double fill;

        AbilityBar(String name, String level, double fill) {
            this.name = name;
            this.level = level;
            this.fill = fill;
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(300, 30); }
        @Override public Dimension getMaximumSize() { return new Dimension(420, 30); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            int w = getWidth();
            Cute.smooth(g);
            g.setFont(Cute.font(Font.BOLD, 13));
            g.setColor(Pixel.INK);
            g.drawString(name, 0, 13);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(level, w - fm.stringWidth(level), 13);
            Pixel.frame(g, 0, 18, w, 10, Pixel.TRACK, Pixel.LINE, 2);
            int fw = (int) Math.round((w - 4) * fill);
            if (fw > 0) {
                g.setColor(fill >= 1 ? Pixel.PINK : new Color(0xFF9EC4));
                g.fillRect(2, 20, fw, 6);
            }
            g.dispose();
        }
    }

    static class Divider extends JComponent {
        Divider() {
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(10, 2); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 2); }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Pixel.LINE);
            g.fillRect(0, 0, getWidth(), 2);
        }
    }
}
