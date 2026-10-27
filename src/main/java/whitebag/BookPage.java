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
    /** Your collection counts what all your accounts have looted. */
    private static final boolean everyAccount = true;
    private int show = 0;           // 0 all, 1 missing, 2 found
    private int book = 0;           // 0 UT and ST items, 1 their shiny versions
    private boolean allPlaces = false;
    private final JTextField search = Pixel.field("", 18, "Find an item or dungeon");
    private boolean searching = false;

    /** Posts a picture to Discord: (png, who it's for). Set by the main window. */
    static volatile java.util.function.BiConsumer<byte[], String> share;
    /** This page's account's in-game name, for the shared picture. */
    java.util.function.Supplier<String> who = () -> "";

    BookPage(Predicate<LootStats.Entry> mine, Predicate<RunLog.Run> myRuns) {
        this.mine = mine;
        this.myRuns = myRuns;
        // catch up each time the tab is opened (item names may have loaded since it was last drawn)
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) changed();
        });
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
        Map<String, Integer> bagsBy = new HashMap<>();
        for (LootStats.Entry e : bags) if (!e.map.isEmpty()) bagsBy.merge(e.map, 1, Integer::sum);
        Map<String, Integer> runsBy = new HashMap<>();
        for (RunLog.Run r : RunLog.all()) {
            if (myRuns == null || everyAccount || myRuns.test(r)) runsBy.merge(r.map, 1, Integer::sum);
        }
        final boolean shiny = book == 1;
        Map<LootBook.Item, LootBook.Found> found = shiny ? LootBook.foundShinies(bags, a -> true) : LootBook.found(bags, a -> true);
        List<LootBook.Place> places = shiny ? LootBook.shinyPlaces() : LootBook.places();
        int total = shiny ? LootBook.shinies().size() : LootBook.itemCount();

        list.add(heading("collection", found.size() + " of " + total + " found"));
        gap(8);
        JPanel books = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        books.setOpaque(false);
        books.add(new LogTabs.Segments(new String[]{"UT and ST items", "Shiny items"}, book, i -> {
            book = i;
            changed();
        }));
        list.add(fit(books));
        gap(10);
        if (total == 0) {
            list.add(note(shiny ? "The shiny items show up here once the pup has read the game files (they're listed "
                    + "there as their own items, labelled shiny)." : "The collection book's item list is missing from the app."));
            return;
        }
        list.add(new AboutMe.AbilityBar(shiny ? "Every shiny UT and ST item" : "Every UT and ST item that drops in bags",
                found.size() + " / " + total, found.size() / (double) total));
        gap(10);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        controls.setOpaque(false);
        controls.add(new LogTabs.Segments(new String[]{"All", "Missing", "Found"}, show, i -> {
            show = i;
            changed();
        }));
        controls.add(Box.createHorizontalStrut(12));
        controls.add(new LogTabs.Segments(new String[]{"My dungeons", "Every dungeon"}, allPlaces ? 1 : 0, i -> {
            allPlaces = i == 1;
            changed();
        }));
        list.add(fit(controls));
        gap(8);
        JPanel find = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        find.setOpaque(false);
        find.add(search);
        find.add(Box.createHorizontalStrut(10));
        Pixel.Button post = new Pixel.Button("Share on Discord", false);
        find.add(post);
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
            int bagsHere = 0;
            for (Map.Entry<String, Integer> e : bagsBy.entrySet()) if (LootBook.samePlace(e.getKey(), p.name)) bagsHere += e.getValue();
            runs.put(p, n);
            if (allPlaces || n > 0 || bagsHere > 0) order.add(p);
        }
        order.sort((a, b) -> {
            int c = runs.get(b) - runs.get(a);
            return c != 0 ? c : a.name.compareToIgnoreCase(b.name);
        });

        final int foundCount = found.size();
        post.addActionListener(e -> {
            java.util.function.BiConsumer<byte[], String> sh = share;
            if (sh == null) return;
            List<ShareRow> rows = new ArrayList<>();
            for (LootBook.Place p : order) {
                ShareRow r = new ShareRow(p.name, p.items.size());
                for (LootBook.Item it : LootBook.sorted(p.items)) if (found.containsKey(it)) {
                    r.have++;
                    r.ids.add(it.id);
                }
                if (r.have > 0 || runs.get(p) > 0) rows.add(r);
                if (rows.size() >= 12) break;
            }
            String name = who.get();
            byte[] png = sharePng(name, shiny ? "shiny collection" : "collection", foundCount, total, rows);
            if (png != null) sh.accept(png, name);
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
        list.add(note("Counted from every bag the pup has seen (the Loot tab's log), plus everything it has seen in your vault, "
                + "your seasonal vault and on your characters (open each vault once, and play each character for a minute). Where items drop comes from the "
                + "RealmEye wiki, so very new items may be missing. Hover an item for details."));
    }

    static final class ShareRow {
        final String place;
        final int of;
        int have;
        final List<Integer> ids = new ArrayList<>();

        ShareRow(String place, int of) {
            this.place = place;
            this.of = of;
        }
    }

    /** A picture of your collection for Discord: the total, then each dungeon with a bar and what you've found. */
    static byte[] sharePng(String who, int found, int total, List<ShareRow> rows) {
        return sharePng(who, "collection", found, total, rows);
    }

    static byte[] sharePng(String who, String what, int found, int total, List<ShareRow> rows) {
        final int W = 640, PAD = 24, ICON = 30, PER = 16;
        int h = PAD + 34 + 22 + 24 + 16;
        for (ShareRow r : rows) h += 22 + 12 + (r.ids.isEmpty() ? 0 : ICON + 6) + 14;
        h += PAD - 10;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(W, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Cute.smooth(g);
        g.setColor(Cute.BG);
        g.fill(new RoundRectangle2D.Double(0, 0, W, h, 28, 28));
        g.setColor(Cute.CARD_EDGE);
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(1, 1, W - 2, h - 2, 28, 28));
        Cute.drawPaw(g, W - 18, 22, 7, new Color(0xFFC2D4));
        int y = PAD;
        g.setColor(Cute.TEXT);
        g.setFont(Cute.font(Font.BOLD, 24));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(who.isEmpty() ? "My " + what : who + "'s " + what, PAD, y + fm.getAscent());
        y += 34;
        g.setFont(Cute.font(Font.BOLD, 15));
        fm = g.getFontMetrics();
        g.drawString(String.format(Locale.ROOT, what.startsWith("shiny") ? "%,d of %,d shiny items found" : "%,d of %,d UT and ST items found",
                found, total), PAD, y + fm.getAscent());
        y += 22;
        bar(g, PAD, y, W - PAD * 2, 14, found / (double) Math.max(1, total));
        y += 24 + 16;
        for (ShareRow r : rows) {
            g.setFont(Cute.font(Font.BOLD, 15));
            fm = g.getFontMetrics();
            g.setColor(Cute.TEXT);
            g.drawString(r.place, PAD, y + fm.getAscent());
            String n = r.have + " / " + r.of;
            g.drawString(n, W - PAD - fm.stringWidth(n), y + fm.getAscent());
            y += 22;
            bar(g, PAD, y, W - PAD * 2, 8, r.have / (double) Math.max(1, r.of));
            y += 12;
            if (!r.ids.isEmpty()) {
                y += 4;
                int x = PAD;
                for (int i = 0; i < Math.min(PER, r.ids.size()); i++) {
                    java.awt.image.BufferedImage pic = r.ids.get(i) > 0 ? AboutMe.sprite(r.ids.get(i), 24) : null;
                    g.setColor(Color.WHITE);
                    g.fill(new RoundRectangle2D.Double(x, y, ICON, ICON, 8, 8));
                    g.setColor(Cute.CARD_EDGE);
                    g.draw(new RoundRectangle2D.Double(x, y, ICON, ICON, 8, 8));
                    if (pic != null) g.drawImage(pic, x + (ICON - pic.getWidth()) / 2, y + (ICON - pic.getHeight()) / 2, null);
                    x += ICON + 4;
                }
                if (r.ids.size() > PER) {
                    g.setColor(Cute.MUTED);
                    g.setFont(Cute.font(Font.BOLD, 13));
                    g.drawString("+" + (r.ids.size() - PER), x + 2, y + ICON / 2 + 5);
                }
                y += ICON + 2;
            }
            y += 14;
        }
        g.dispose();
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static void bar(Graphics2D g, int x, int y, int w, int h, double f) {
        g.setColor(new Color(0xF3E6EE));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        int fw = (int) Math.round(w * Math.max(0, Math.min(1, f)));
        if (fw > 0) {
            g.setColor(Cute.PINK);
            g.fill(new RoundRectangle2D.Double(x, y, Math.max(h, fw), h, h, h));
        }
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
            // a narrow box, and at most a few places (some items drop almost everywhere)
            StringBuilder sb = new StringBuilder("<html><div style='width:260px'><b>").append(esc(it.name)).append("</b> (").append(it.tier).append(")<br>");
            List<String> where = it.places.size() > 6 ? it.places.subList(0, 5) : it.places;
            sb.append("Drops in: ").append(esc(String.join(", ", where)));
            if (it.places.size() > 6) sb.append(" and ").append(it.places.size() - 5).append(" more places");
            sb.append("<br>");
            if (f == null) sb.append("<i>Not found yet</i>");
            else sb.append(LootBook.describe(f));
            return sb.append("</div></html>").toString();
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
            if (c != null || LootImage.gameSpritesReady) ICONS.put(id, out); // keep trying until the game's pictures are in
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
                Color accent = it.tier.equals("ST") ? Theme.c(0xE0A100) : Theme.c(0xA35BFF);
                g.setColor(got ? Cute.mix(Theme.WHITE, accent, 0.14) : Theme.c(0xF4F2F7));
                g.fill(new RoundRectangle2D.Double(x, y, SLOT, SLOT, 12, 12));
                g.setColor(got ? accent : Theme.c(0xDDD8E4));
                g.setStroke(new BasicStroke(got ? 2.4f : 1.4f));
                g.draw(new RoundRectangle2D.Double(x + 1, y + 1, SLOT - 2, SLOT - 2, 11, 11));
                BufferedImage[] pics = icons(it.id);
                BufferedImage pic = got ? pics[0] : pics[1];
                if (pic != null) {
                    g.drawImage(pic, x + (SLOT - pic.getWidth()) / 2, y + (SLOT - pic.getHeight()) / 2, null);
                } else {
                    g.setColor(got ? Pixel.INK : Theme.c(0xB9B1C6));
                    g.setFont(Cute.font(Font.BOLD, 11));
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(it.tier, x + (SLOT - fm.stringWidth(it.tier)) / 2, y + (SLOT + fm.getAscent()) / 2 - 2);
                }
                if (got) Cute.drawHeart(g, x + SLOT - 13, y + 3, 10, Pixel.PINK);
                if (it.name.endsWith("(Shiny)")) {
                    // a little sparkle on shiny items
                    g.setColor(got ? Theme.c(0xF0B10A) : Theme.c(0xD8D2E0));
                    double cx = x + 10, cy = y + 10, r = 6, k = 1.6;
                    java.awt.geom.Path2D star = new java.awt.geom.Path2D.Double();
                    star.moveTo(cx, cy - r);
                    star.lineTo(cx + k, cy - k);
                    star.lineTo(cx + r, cy);
                    star.lineTo(cx + k, cy + k);
                    star.lineTo(cx, cy + r);
                    star.lineTo(cx - k, cy + k);
                    star.lineTo(cx - r, cy);
                    star.lineTo(cx - k, cy - k);
                    star.closePath();
                    g.fill(star);
                }
            }
            g.dispose();
        }
    }
}
