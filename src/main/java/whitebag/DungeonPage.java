package whitebag;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The Dungeons page (under Loot): every dungeon side by side, scrolling sideways. Down each column: how hard it is,
 * what drops its portal, and the UT and ST items you can get there. Hover over an item to see if it's in your collection.
 */
final class DungeonPage extends JPanel {
    static final class Dungeon {
        final String name, type;
        final double difficulty;
        final List<String> drops = new ArrayList<>();
        final List<Boolean> always = new ArrayList<>();
        final List<String> ut = new ArrayList<>(), st = new ArrayList<>();

        Dungeon(String name, String type, double difficulty) {
            this.name = name;
            this.type = type;
            this.difficulty = difficulty;
        }
    }

    private static final String[] TYPE_ORDER = {"Realm Dungeons", "Oryx's Castle", "Advanced Dungeons", "Heroic Dungeons",
            "Wormholes", "Advanced Wormholes", "Realm Event Dungeons", "Special Event Dungeons"};
    private static List<Dungeon> all;

    static synchronized List<Dungeon> dungeons() {
        if (all != null) return all;
        all = new ArrayList<>();
        try (InputStream in = DungeonPage.class.getResourceAsStream("/whitebag/dungeon-atlas.txt")) {
            if (in == null) return all;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            Dungeon d = null;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                String[] f = line.split("\\|");
                switch (f[0]) {
                    case "D":
                        d = new Dungeon(f[1], f[2], Double.parseDouble(f[3]));
                        all.add(d);
                        break;
                    case "P":
                        if (d != null) {
                            d.drops.add(f[1]);
                            d.always.add(f.length > 2 && f[2].equals("1"));
                        }
                        break;
                    case "U":
                        if (d != null) d.ut.add(f[1]);
                        break;
                    case "S":
                        if (d != null) d.st.add(f[1]);
                        break;
                    default:
                }
            }
        } catch (Exception e) {
            System.out.println("Dungeons: couldn't read the dungeon list: " + e);
        }
        return all;
    }

    // ---------------------------------------------------------------- layout

    private static final int LABEL_W = 132, COL_W = 214, HEAD_H = 74, LINE = 24, PAD = 10, CHIP = 24, DIFF_H = 38;
    private static final Color UT = new Color(0xA35BFF), ST = new Color(0xE0A100);
    private static final Color GREY = new Color(0x9C92AC), SOFT = new Color(0xF8F4FC), MARK = new Color(0xFFE28A);
    private static final String[] ROWS = {"Difficulty", "Portal drops from", "UT items", "ST items", "Shiny items"};

    private final Predicate<LootStats.Entry> mine;
    private boolean easiestFirst = false;
    private final JTextField search = Pixel.field("", 18, "Find a dungeon, enemy or item");
    private final JLabel count = new JLabel();

    private List<Dungeon> shown = new ArrayList<>();
    private Map<LootBook.Item, LootBook.Found> found = new HashMap<>();
    private final Map<String, LootBook.Item> book = new HashMap<>();
    /** Shiny versions: which you've found, and each dungeon's (the shiny versions of its UTs and STs). */
    private Map<LootBook.Item, LootBook.Found> foundShiny = new HashMap<>();
    private final Map<Dungeon, List<LootBook.Item>> shinyOf = new HashMap<>();
    private boolean shinyReady;
    private final int[] rowH = new int[ROWS.length];
    private String q = "";

    private final Body body = new Body();
    private final Header header = new Header();
    private final Labels labels = new Labels();
    private final Timer refresh;

    DungeonPage(Predicate<LootStats.Entry> mine) {
        super(new BorderLayout(0, 10));
        this.mine = mine;
        setOpaque(false);

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bar.setOpaque(false);
        bar.add(search);
        bar.add(Box.createHorizontalStrut(12));
        bar.add(new LogTabs.Segments(new String[]{"Group by type", "Easiest first"}, 0, i -> {
            easiestFirst = i == 1;
            rebuild();
        }));
        bar.add(Box.createHorizontalStrut(12));
        count.setFont(Cute.font(Font.BOLD, 12));
        count.setForeground(Pixel.INK);
        bar.add(count);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        top.add(bar);
        top.add(Box.createVerticalStrut(6));
        JLabel tip = new JLabel("Hover over an item to see if it's in your collection. Items you have are in colour with a heart.");
        tip.setFont(Cute.font(Font.PLAIN, 12));
        tip.setForeground(Pixel.INK);
        tip.setAlignmentX(LEFT_ALIGNMENT);
        top.add(tip);
        add(top, BorderLayout.NORTH);

        JScrollPane s = new JScrollPane(body);
        s.setColumnHeaderView(header);
        s.setRowHeaderView(labels);
        JComponent corner = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(SOFT);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Pixel.LINE);
                g.fillRect(getWidth() - 1, 0, 1, getHeight());
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        s.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, corner);
        s.setBorder(new Pixel.FrameBorder(2));
        s.getViewport().setBackground(Pixel.SHEET);
        s.getVerticalScrollBar().setUnitIncrement(24);
        s.getHorizontalScrollBar().setUnitIncrement(40);
        s.getVerticalScrollBar().setUI(new Pixel.ThinBar());
        s.getHorizontalScrollBar().setUI(new Pixel.ThinBar());
        s.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        s.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 12));
        // shift + wheel (or a sideways wheel) scrolls across
        s.addMouseWheelListener(e -> {
            if (!e.isShiftDown()) return;
            JScrollBar h = s.getHorizontalScrollBar();
            h.setValue(h.getValue() + e.getWheelRotation() * 120);
            e.consume();
        });
        add(s, BorderLayout.CENTER);
        ToolTipManager.sharedInstance().registerComponent(body);

        refresh = new Timer(300, e -> rebuild());
        refresh.setRepeats(false);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) changed();
        });
        SwingUtilities.invokeLater(this::rebuild);
    }

    void changed() {
        SwingUtilities.invokeLater(() -> {
            if (!refresh.isRunning()) refresh.restart();
        });
    }

    private void rebuild() {
        q = LootBook.norm(search.getText());
        List<LootStats.Entry> bags = new ArrayList<>();
        bags.addAll(LootStats.all()); // every account's loot counts
        found = LootBook.found(bags);
        foundShiny = LootBook.foundShinies(bags);
        Map<String, LootBook.Item> byBase = new HashMap<>();
        for (LootBook.Item sh : LootBook.shinies()) byBase.put(LootBook.norm(sh.name.replace(" (Shiny)", "")), sh);
        shinyReady = !byBase.isEmpty();
        shinyOf.clear();
        for (Dungeon d : dungeons()) {
            List<LootBook.Item> l = new ArrayList<>();
            for (List<String> src : java.util.Arrays.asList(d.ut, d.st)) {
                for (String n : src) {
                    LootBook.Item sh = byBase.get(LootBook.norm(n));
                    if (sh != null) l.add(sh);
                }
            }
            shinyOf.put(d, l);
        }
        List<Dungeon> list = new ArrayList<>();
        for (Dungeon d : dungeons()) if (matches(d)) list.add(d);
        java.util.Comparator<Dungeon> byDiff = (a, b) -> a.difficulty != b.difficulty ? Double.compare(a.difficulty, b.difficulty) : a.name.compareToIgnoreCase(b.name);
        if (easiestFirst) list.sort(byDiff);
        else list.sort((a, b) -> {
            int ta = typeIndex(a.type), tb = typeIndex(b.type);
            return ta != tb ? Integer.compare(ta, tb) : byDiff.compare(a, b);
        });
        shown = list;
        int drops = 0, ut = 0, st = 0, shiny = 0;
        for (Dungeon d : list) {
            drops = Math.max(drops, d.drops.size());
            ut = Math.max(ut, d.ut.size());
            st = Math.max(st, d.st.size());
            shiny = Math.max(shiny, shinyOf.get(d).size());
        }
        rowH[0] = DIFF_H;
        rowH[1] = PAD * 2 + Math.max(1, drops) * LINE;
        rowH[2] = PAD * 2 + CHIP + Math.max(1, ut) * LINE;
        rowH[3] = PAD * 2 + CHIP + Math.max(1, st) * LINE;
        rowH[4] = shinyReady ? PAD * 2 + CHIP + Math.max(1, shiny) * LINE : PAD * 2 + 44;
        count.setText(list.size() + (list.size() == 1 ? " dungeon" : " dungeons"));
        for (JComponent c : new JComponent[]{body, header, labels}) {
            c.revalidate();
            c.repaint();
        }
    }

    private boolean matches(Dungeon d) {
        if (q.isEmpty()) return true;
        if (LootBook.norm(d.name).contains(q) || LootBook.norm(d.type).contains(q)) return true;
        for (List<String> l : java.util.Arrays.asList(d.drops, d.ut, d.st)) for (String s : l) if (LootBook.norm(s).contains(q)) return true;
        return false;
    }

    private static int typeIndex(String t) {
        for (int i = 0; i < TYPE_ORDER.length; i++) if (TYPE_ORDER[i].equals(t)) return i;
        return TYPE_ORDER.length;
    }

    private LootBook.Item item(String name) {
        return book.computeIfAbsent(LootBook.norm(name), k -> LootBook.byName(name));
    }

    private boolean have(String name) {
        LootBook.Item it = item(name);
        return it != null && found.containsKey(it);
    }

    private int totalHeight() {
        int h = 0;
        for (int r : rowH) h += r;
        return h;
    }

    // ---------------------------------------------------------------- drawing

    private static String fit(FontMetrics fm, String s, int w) {
        if (fm.stringWidth(s) <= w) return s;
        while (s.length() > 3 && fm.stringWidth(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static final Map<Integer, BufferedImage[]> ICONS = new HashMap<>();

    /** {colour, greyed} 20px pictures of an item. */
    private static BufferedImage[] icons(int id) {
        BufferedImage[] got = ICONS.get(id);
        if (got != null) return got;
        BufferedImage c = id > 0 ? AboutMe.sprite(id, 20) : null;
        BufferedImage grey = null;
        if (c != null) {
            grey = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < c.getHeight(); y++) {
                for (int x = 0; x < c.getWidth(); x++) {
                    int argb = c.getRGB(x, y), a = argb >>> 24;
                    int l = (int) (0.3 * ((argb >> 16) & 255) + 0.59 * ((argb >> 8) & 255) + 0.11 * (argb & 255));
                    l = 150 + l / 3;
                    grey.setRGB(x, y, (a * 45 / 100) << 24 | l << 16 | l << 8 | l);
                }
            }
        }
        BufferedImage[] out = {c, grey};
        if (c != null || LootImage.gameSpritesReady) ICONS.put(id, out);
        return out;
    }

    /** Dungeon names across the top (stays put when scrolling down). */
    private final class Header extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Math.max(1, shown.size()) * COL_W, HEAD_H);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Cute.smooth(g);
            g.setColor(Pixel.SHEET);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(SOFT);
            g.fillRect(0, 0, getWidth(), 24);
            for (int i = 0; i < shown.size(); i++) {
                Dungeon d = shown.get(i);
                int x = i * COL_W;
                // the type, once per group (or on every column when sorted by difficulty)
                boolean first = easiestFirst || i == 0 || !shown.get(i - 1).type.equals(d.type);
                if (first) {
                    g.setColor(Pixel.LINE);
                    g.fillRect(x, 0, 1, 24);
                    g.setFont(Cute.font(Font.BOLD, 10));
                    g.setColor(GREY);
                    g.drawString(fit(g.getFontMetrics(), d.type.toUpperCase(Locale.ROOT), COL_W - 20), x + 12, 16);
                }
                g.setColor(Pixel.LINE);
                g.fillRect(x + COL_W - 1, 24, 1, HEAD_H - 24);
                g.setFont(Cute.font(Font.BOLD, 15));
                g.setColor(Pixel.INK);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(fit(fm, d.name, COL_W - 22), x + 12, 46);
                int n = 0, have = 0;
                for (List<String> l : java.util.Arrays.asList(d.ut, d.st)) for (String s : l) {
                    n++;
                    if (have(s)) have++;
                }
                g.setFont(Cute.font(Font.BOLD, 11));
                g.setColor(have > 0 && have == n ? Pixel.TEAL : GREY);
                g.drawString(n == 0 ? "No UT or ST items" : "Collected " + have + " of " + n, x + 12, 64);
            }
            g.setColor(Pixel.LINE);
            g.fillRect(0, 24, getWidth(), 1);
            g.fillRect(0, getHeight() - 1, getWidth(), 1);
            g.dispose();
        }
    }

    /** Row names down the left (stays put when scrolling across). */
    private final class Labels extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(LABEL_W, totalHeight());
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Cute.smooth(g);
            g.setColor(SOFT);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setFont(Cute.font(Font.BOLD, 12));
            int y = 0;
            for (int r = 0; r < ROWS.length; r++) {
                g.setColor(Pixel.INK);
                g.drawString(ROWS[r], 12, y + (r == 0 ? 24 : PAD + 16));
                y += rowH[r];
                g.setColor(Pixel.LINE);
                g.fillRect(0, y - 1, getWidth(), 1);
            }
            g.setColor(Pixel.LINE);
            g.fillRect(getWidth() - 1, 0, 1, getHeight());
            g.dispose();
        }
    }

    /** The table itself. */
    private final class Body extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Math.max(1, shown.size()) * COL_W, totalHeight());
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Cute.smooth(g);
            g.setColor(Pixel.SHEET);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (shown.isEmpty()) {
                g.setFont(Cute.font(Font.PLAIN, 14));
                g.setColor(Pixel.INK);
                g.drawString(dungeons().isEmpty() ? "The dungeon list is missing from the app." : "Nothing matches that.", 16, 30);
                g.dispose();
                return;
            }
            Rectangle clip = g.getClipBounds();
            int from = clip == null ? 0 : Math.max(0, clip.x / COL_W), to = clip == null ? shown.size() : Math.min(shown.size(), (clip.x + clip.width) / COL_W + 1);
            for (int i = from; i < to; i++) {
                Dungeon d = shown.get(i);
                int x = i * COL_W, y = 0;
                // difficulty: ten little pips and the number
                if (d.difficulty > 0) {
                    for (int p = 1; p <= 10; p++) {
                        int px = x + 12 + (p - 1) * 10;
                        g.setColor(Pixel.LINE);
                        g.fillRect(px, 14, 8, 11);
                        g.setColor(Pixel.PINK);
                        if (d.difficulty >= p) g.fillRect(px, 14, 8, 11);
                        else if (d.difficulty >= p - 0.5) g.fillRect(px, 14, 4, 11);
                    }
                    g.setFont(Cute.font(Font.BOLD, 13));
                    g.setColor(Pixel.INK);
                    String n = d.difficulty == Math.floor(d.difficulty) ? String.valueOf((int) d.difficulty) : String.valueOf(d.difficulty);
                    g.drawString(n, x + 120, 24);
                } else {
                    muted(g, "Not rated", x + 12, 24);
                }
                y += rowH[0];
                // portal drops
                g.setFont(Cute.font(Font.PLAIN, 13));
                if (d.drops.isEmpty()) muted(g, "Not listed", x + 12, y + PAD + 16);
                for (int k = 0; k < d.drops.size(); k++) {
                    int ly = y + PAD + k * LINE + 16;
                    boolean always = d.always.get(k);
                    g.setFont(Cute.font(Font.PLAIN, 13));
                    FontMetrics fm = g.getFontMetrics();
                    String s = fit(fm, d.drops.get(k), COL_W - 24 - (always ? 48 : 0));
                    text(g, s, x + 12, ly, Pixel.INK);
                    if (always) {
                        g.setFont(Cute.font(Font.BOLD, 10));
                        g.setColor(Pixel.PINK_INK);
                        g.drawString("always", x + 18 + fm.stringWidth(s), ly);
                    }
                }
                y += rowH[1];
                items(g, d.ut, "UT", UT, x, y);
                y += rowH[2];
                items(g, d.st, "ST", ST, x, y);
                y += rowH[3];
                shinies(g, d, x, y);
                g.setColor(Pixel.LINE);
                g.fillRect(x + COL_W - 1, 0, 1, getHeight());
            }
            int y = 0;
            for (int r : rowH) {
                y += r;
                g.setColor(Pixel.LINE);
                g.fillRect(0, y - 1, getWidth(), 1);
            }
            g.dispose();
        }

        private void muted(Graphics2D g, String s, int x, int y) {
            g.setFont(Cute.font(Font.PLAIN, 12));
            g.setColor(GREY);
            g.drawString(s, x, y);
        }

        /** Text, with the part matching the search highlighted. */
        private void text(Graphics2D g, String s, int x, int y, Color c) {
            FontMetrics fm = g.getFontMetrics();
            if (!q.isEmpty()) {
                String lower = s.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "");
                int at = lower.indexOf(q);
                if (at >= 0 && LootBook.norm(s).contains(q)) {
                    g.setColor(MARK);
                    g.fillRect(x - 2, y - fm.getAscent() + 1, fm.stringWidth(s) + 4, fm.getAscent() + 3);
                }
            }
            g.setColor(c);
            g.drawString(s, x, y);
        }

        private void items(Graphics2D g, List<String> list, String tier, Color accent, int x, int y) {
            if (list.isEmpty()) {
                muted(g, "None", x + 12, y + PAD + 16);
                return;
            }
            int have = 0;
            for (String s : list) if (have(s)) have++;
            String chip = list.size() + " " + tier + "  ·  " + have + " collected";
            g.setFont(Cute.font(Font.BOLD, 11));
            FontMetrics cf = g.getFontMetrics();
            g.setColor(Cute.mix(Color.WHITE, accent, 0.16));
            g.fill(new RoundRectangle2D.Double(x + 12, y + PAD, cf.stringWidth(chip) + 12, 18, 8, 8));
            g.setColor(Cute.mix(accent, Color.BLACK, 0.25));
            g.drawString(chip, x + 18, y + PAD + 13);
            for (int k = 0; k < list.size(); k++) {
                String name = list.get(k);
                LootBook.Item it = item(name);
                boolean got = it != null && found.containsKey(it);
                int top = y + PAD + CHIP + k * LINE;
                g.setColor(got ? accent : Pixel.LINE);
                g.fillRect(x + 12, top + 3, 3, LINE - 6);
                BufferedImage[] pics = icons(it == null ? -1 : it.id);
                BufferedImage pic = got ? pics[0] : pics[1];
                int tx = x + 20;
                if (pic != null) {
                    g.drawImage(pic, tx, top + (LINE - pic.getHeight()) / 2, null);
                    tx += pic.getWidth() + 5;
                }
                g.setFont(Cute.font(got ? Font.BOLD : Font.PLAIN, 13));
                String s = fit(g.getFontMetrics(), name, x + COL_W - tx - 22);
                text(g, s, tx, top + 17, got ? Pixel.INK : GREY);
                if (got) Cute.drawHeart(g, x + COL_W - 20, top + 7, 10, Pixel.PINK);
            }
        }

        /** The shiny versions of this dungeon's items. */
        private void shinies(Graphics2D g, Dungeon d, int x, int y) {
            if (!shinyReady) {
                g.setFont(Cute.font(Font.PLAIN, 12));
                g.setColor(GREY);
                g.drawString("Shows up once the pup has", x + 12, y + PAD + 16);
                g.drawString("read the game files", x + 12, y + PAD + 32);
                return;
            }
            List<LootBook.Item> list = shinyOf.get(d);
            if (list == null || list.isEmpty()) {
                muted(g, "None", x + 12, y + PAD + 16);
                return;
            }
            int have = 0;
            for (LootBook.Item it : list) if (foundShiny.containsKey(it)) have++;
            String chip = list.size() + " shiny  ·  " + have + " collected";
            g.setFont(Cute.font(Font.BOLD, 11));
            FontMetrics cf = g.getFontMetrics();
            Color gold = new Color(0xF0B10A);
            g.setColor(Cute.mix(Color.WHITE, gold, 0.18));
            g.fill(new RoundRectangle2D.Double(x + 12, y + PAD, cf.stringWidth(chip) + 12, 18, 8, 8));
            g.setColor(Cute.mix(gold, Color.BLACK, 0.35));
            g.drawString(chip, x + 18, y + PAD + 13);
            for (int k = 0; k < list.size(); k++) {
                LootBook.Item it = list.get(k);
                boolean got = foundShiny.containsKey(it);
                Color accent = it.tier.equals("ST") ? ST : UT;
                int top = y + PAD + CHIP + k * LINE;
                g.setColor(got ? accent : Pixel.LINE);
                g.fillRect(x + 12, top + 3, 3, LINE - 6);
                BufferedImage[] pics = icons(it.id);
                BufferedImage pic = got ? pics[0] : pics[1];
                int tx = x + 20;
                if (pic != null) {
                    g.drawImage(pic, tx, top + (LINE - pic.getHeight()) / 2, null);
                    tx += pic.getWidth() + 5;
                }
                g.setFont(Cute.font(got ? Font.BOLD : Font.PLAIN, 13));
                String name = it.name.replace(" (Shiny)", "");
                String s = fit(g.getFontMetrics(), name, x + COL_W - tx - 22);
                text(g, s, tx, top + 17, got ? Pixel.INK : GREY);
                if (got) Cute.drawHeart(g, x + COL_W - 20, top + 7, 10, Pixel.PINK);
            }
        }

        /** The shiny item under the mouse, or null. */
        private LootBook.Item shinyAt(Point p) {
            int i = p.x / COL_W;
            if (i < 0 || i >= shown.size() || !shinyReady) return null;
            List<LootBook.Item> list = shinyOf.get(shown.get(i));
            int y = rowH[0] + rowH[1] + rowH[2] + rowH[3];
            int k = (p.y - y - PAD - CHIP) / LINE;
            if (list == null || p.y < y + PAD + CHIP || k < 0 || k >= list.size()) return null;
            return list.get(k);
        }

        /** The item under the mouse, or null. */
        private String itemAt(Point p) {
            int i = p.x / COL_W;
            if (i < 0 || i >= shown.size()) return null;
            Dungeon d = shown.get(i);
            int y = rowH[0] + rowH[1];
            for (int r = 2; r < 4; r++) {
                List<String> list = r == 2 ? d.ut : d.st;
                int k = (p.y - y - PAD - CHIP) / LINE;
                if (p.y >= y + PAD + CHIP && k >= 0 && k < list.size()) return list.get(k);
                y += rowH[r];
            }
            return null;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            LootBook.Item sh = shinyAt(e.getPoint());
            if (sh != null) {
                LootBook.Found f = foundShiny.get(sh);
                StringBuilder sb = new StringBuilder("<html><div style='width:240px'><b>").append(esc(sh.name.replace(" (Shiny)", "")))
                        .append("</b> (shiny ").append(sh.tier).append(")<br>");
                if (f == null) sb.append("<span style='color:#B0305F'><b>Not in your collection yet</b></span>");
                else sb.append("<span style='color:#1F8A70'><b>In your collection</b></span><br>Found ")
                        .append(f.times == 1 ? "once" : f.times + " times").append(", first on ")
                        .append(new SimpleDateFormat("d MMM yyyy", Locale.ROOT).format(new Date(f.first * 1000L)))
                        .append(f.where.isEmpty() ? "" : " in " + esc(f.where));
                return sb.append("</div></html>").toString();
            }
            String name = itemAt(e.getPoint());
            if (name == null) return null;
            LootBook.Item it = item(name);
            LootBook.Found f = it == null ? null : found.get(it);
            StringBuilder sb = new StringBuilder("<html><div style='width:240px'><b>").append(esc(name)).append("</b>");
            if (it != null) sb.append(" (").append(it.tier).append(")");
            sb.append("<br>");
            if (it == null) {
                sb.append("<i>Not in the collection log</i>");
            } else if (f == null) {
                sb.append("<span style='color:#B0305F'><b>Not in your collection yet</b></span>");
            } else {
                sb.append("<span style='color:#1F8A70'><b>In your collection</b></span><br>Found ")
                        .append(f.times == 1 ? "once" : f.times + " times").append(", first on ")
                        .append(new SimpleDateFormat("d MMM yyyy", Locale.ROOT).format(new Date(f.first * 1000L)))
                        .append(f.where.isEmpty() ? "" : " in " + esc(f.where));
            }
            return sb.append("</div></html>").toString();
        }

        @Override
        public Point getToolTipLocation(MouseEvent e) {
            return new Point(e.getX() + 14, e.getY() + 16);
        }

        private String esc(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
