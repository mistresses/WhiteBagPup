package whitebag;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import static whitebag.AboutMe.heading;
import static whitebag.AboutMe.left;
import static whitebag.AboutMe.note;

/** The Collection tab: every UT and ST item, greyed out until you've looted it, by where it drops. */
final class BookPage extends MoreTabs.Page {
    private final Predicate<LootStats.Entry> mine;
    private final Predicate<RunLog.Run> myRuns;
    private boolean everyAccount = false;
    private int show = 0;           // 0 all, 1 missing, 2 found
    private boolean allPlaces = false;
    private final JTextField search = Pixel.field("", 18, "Find an item or dungeon");
    private boolean searching = false;

    BookPage(Predicate<LootStats.Entry> mine, Predicate<RunLog.Run> myRuns) {
        this.mine = mine;
        this.myRuns = myRuns;
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { searching = true; changed(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { searching = true; changed(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
    }

    @Override
    void fill() {
        List<LootStats.Entry> bags = new ArrayList<>();
        boolean several = false;
        String first = null;
        for (LootStats.Entry e : LootStats.all()) {
            if (!e.account.isEmpty()) {
                if (first == null) first = e.account;
                else if (!first.equals(e.account)) several = true;
            }
            if (mine == null || everyAccount || mine.test(e)) bags.add(e);
        }
        Map<String, Integer> runsBy = new HashMap<>();
        for (RunLog.Run r : RunLog.all()) {
            if (myRuns == null || everyAccount || myRuns.test(r)) runsBy.merge(r.map, 1, Integer::sum);
        }
        Map<LootBook.Item, LootBook.Found> found = LootBook.found(bags);
        List<LootBook.Place> places = LootBook.places();
        int total = LootBook.itemCount();

        list.add(heading("collection", found.size() + " of " + total + " found"));
        gap(8);
        if (total == 0) {
            list.add(note("The collection book's item list is missing from the app."));
            return;
        }
        list.add(new AboutMe.AbilityBar("Every UT and ST item that drops in bags", found.size() + " / " + total, found.size() / (double) total));
        gap(10);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        controls.setOpaque(false);
        controls.add(new LogTabs.Segments(new String[]{"All", "Missing", "Found"}, show, i -> {
            show = i;
            changed();
        }));
        controls.add(Box.createHorizontalStrut(12));
        controls.add(new LogTabs.Segments(new String[]{"Dungeons I've run", "Every dungeon"}, allPlaces ? 1 : 0, i -> {
            allPlaces = i == 1;
            changed();
        }));
        if (mine != null && several) {
            controls.add(Box.createHorizontalStrut(12));
            controls.add(new LogTabs.Segments(new String[]{"This account", "All accounts"}, everyAccount ? 1 : 0, i -> {
                everyAccount = i == 1;
                changed();
            }));
        }
        list.add(fit(controls));
        gap(8);
        JPanel find = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        find.setOpaque(false);
        find.add(search);
        list.add(fit(find));
        if (searching) SwingUtilities.invokeLater(() -> {
            search.requestFocusInWindow();
            searching = false;
        });
        gap(14);

        // dungeons you've run first (most runs first), then the rest by name
        List<LootBook.Place> order = new ArrayList<>();
        Map<LootBook.Place, Integer> runs = new HashMap<>();
        for (LootBook.Place p : places) {
            int n = 0;
            for (Map.Entry<String, Integer> e : runsBy.entrySet()) if (LootBook.samePlace(e.getKey(), p.name)) n += e.getValue();
            boolean anyFound = false;
            for (LootBook.Item it : p.items) anyFound |= found.containsKey(it);
            runs.put(p, n);
            if (allPlaces || n > 0) order.add(p);
        }
        order.sort((a, b) -> {
            int c = runs.get(b) - runs.get(a);
            return c != 0 ? c : a.name.compareToIgnoreCase(b.name);
        });

        String q = LootBook.norm(search.getText());
        int shownPlaces = 0;
        for (LootBook.Place p : order) {
            boolean placeHit = !q.isEmpty() && LootBook.norm(p.name).contains(q);
            List<LootBook.Item> items = new ArrayList<>();
            int have = 0;
            for (LootBook.Item it : LootBook.sorted(p.items)) {
                boolean got = found.containsKey(it);
                if (got) have++;
                if (show == 1 && got || show == 2 && !got) continue;
                if (!q.isEmpty() && !placeHit && !LootBook.norm(it.name).contains(q)) continue;
                items.add(it);
            }
            if (items.isEmpty()) continue;
            shownPlaces++;
            int n = runs.get(p);
            list.add(new AboutMe.AbilityBar(p.name + (n > 0 ? "  ·  " + n + (n == 1 ? " run" : " runs") : ""),
                    have + " / " + p.items.size(), have / (double) p.items.size()));
            gap(6);
            list.add(new Grid(items, found));
            gap(14);
        }
        if (shownPlaces == 0) {
            list.add(note(!q.isEmpty() ? "Nothing matches \"" + search.getText().trim() + "\"."
                    : show == 2 ? "Nothing found yet. Items you loot from now on get ticked off here."
                    : allPlaces ? "Nothing to show." : "Run a dungeon (or pick Every dungeon) and its items show up here."));
        }
        gap(6);
        list.add(note("Counted from every bag the pup has seen (the Loot tab's log). Where items drop comes from the "
                + "RealmEye wiki, so very new items may be missing. Hover an item for details."));
    }

    private static JComponent fit(JComponent c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return left(c);
    }

    /** The items of one place as a wrapping grid of slots; missing ones are greyed out. */
    static final class Grid extends JComponent {
        private static final int SLOT = 44, GAP = 6;
        private static final Map<Integer, BufferedImage[]> ICONS = new HashMap<>();
        private final List<LootBook.Item> items;
        private final Map<LootBook.Item, LootBook.Found> found;

        Grid(List<LootBook.Item> items, Map<LootBook.Item, LootBook.Found> found) {
            this.items = items;
            this.found = found;
            setAlignmentX(LEFT_ALIGNMENT);
            setToolTipText("");
        }

        private int perRow() {
            int w = getWidth();
            if (w <= 0 && getParent() != null) {
                Insets in = getParent().getInsets();
                w = getParent().getWidth() - in.left - in.right;
            }
            if (w <= 0) w = 560;
            return Math.max(1, (w + GAP) / (SLOT + GAP));
        }

        @Override
        public Dimension getPreferredSize() {
            int rows = (items.size() + perRow() - 1) / perRow();
            return new Dimension(SLOT + GAP, Math.max(1, rows) * (SLOT + GAP));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public void setBounds(int x, int y, int w, int h) {
            int before = getWidth();
            super.setBounds(x, y, w, h);
            if (before != w) SwingUtilities.invokeLater(this::revalidate); // rows change with the width
        }

        private int hit(MouseEvent e) {
            int per = perRow();
            int col = e.getX() / (SLOT + GAP), row = e.getY() / (SLOT + GAP);
            if (col >= per || e.getX() % (SLOT + GAP) >= SLOT || e.getY() % (SLOT + GAP) >= SLOT) return -1;
            int i = row * per + col;
            return i < items.size() ? i : -1;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int i = hit(e);
            if (i < 0) return null;
            LootBook.Item it = items.get(i);
            LootBook.Found f = found.get(it);
            StringBuilder sb = new StringBuilder("<html><b>").append(esc(it.name)).append("</b> (").append(it.tier).append(")<br>");
            sb.append("Drops in: ").append(esc(String.join(", ", it.places))).append("<br>");
            if (f == null) sb.append("<i>Not found yet</i>");
            else sb.append("Found ").append(f.times == 1 ? "once" : f.times + " times").append(", first on ")
                    .append(new SimpleDateFormat("d MMM yyyy", Locale.ROOT).format(new Date(f.first * 1000L)))
                    .append(f.where.isEmpty() ? "" : " in " + esc(f.where));
            return sb.append("</html>").toString();
        }

        private static String esc(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        /** {colour, greyed} pictures of an item, made once. */
        private static synchronized BufferedImage[] icons(int id) {
            BufferedImage[] got = ICONS.get(id);
            if (got != null) return got;
            BufferedImage c = id > 0 ? AboutMe.sprite(id, 32) : null;
            BufferedImage grey = null;
            if (c != null) {
                grey = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < c.getHeight(); y++) {
                    for (int x = 0; x < c.getWidth(); x++) {
                        int argb = c.getRGB(x, y), a = argb >>> 24;
                        int l = (int) (0.3 * ((argb >> 16) & 255) + 0.59 * ((argb >> 8) & 255) + 0.11 * (argb & 255));
                        l = 150 + l / 3; // light grey
                        grey.setRGB(x, y, (a * 45 / 100) << 24 | l << 16 | l << 8 | l);
                    }
                }
            }
            BufferedImage[] out = {c, grey};
            if (c != null || !LootImage.gameSpritesReady) ICONS.put(id, out);
            return out;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Cute.smooth(g);
            int per = perRow();
            for (int i = 0; i < items.size(); i++) {
                LootBook.Item it = items.get(i);
                boolean got = found.containsKey(it);
                int x = (i % per) * (SLOT + GAP), y = (i / per) * (SLOT + GAP);
                Color accent = it.tier.equals("ST") ? new Color(0xE0A100) : new Color(0xA35BFF);
                g.setColor(got ? Cute.mix(Color.WHITE, accent, 0.14) : new Color(0xF4F2F7));
                g.fill(new RoundRectangle2D.Double(x, y, SLOT, SLOT, 12, 12));
                g.setColor(got ? accent : new Color(0xDDD8E4));
                g.setStroke(new BasicStroke(got ? 2.4f : 1.4f));
                g.draw(new RoundRectangle2D.Double(x + 1, y + 1, SLOT - 2, SLOT - 2, 11, 11));
                BufferedImage[] pics = icons(it.id);
                BufferedImage pic = got ? pics[0] : pics[1];
                if (pic != null) {
                    g.drawImage(pic, x + (SLOT - pic.getWidth()) / 2, y + (SLOT - pic.getHeight()) / 2, null);
                } else {
                    g.setColor(got ? Pixel.INK : new Color(0xB9B1C6));
                    g.setFont(Cute.font(Font.BOLD, 11));
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(it.tier, x + (SLOT - fm.stringWidth(it.tier)) / 2, y + (SLOT + fm.getAscent()) / 2 - 2);
                }
                if (got) Cute.drawHeart(g, x + SLOT - 13, y + 3, 10, Pixel.PINK);
            }
            g.dispose();
        }
    }
}
