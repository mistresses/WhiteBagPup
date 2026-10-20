package whitebag;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static whitebag.AboutMe.heading;
import static whitebag.AboutMe.left;
import static whitebag.AboutMe.note;

/** The Badges tab: the pup's achievements, unlocked ones in colour with the date, the rest with progress. */
final class BadgesPage extends MoreTabs.Page {
    private final Supplier<String> account;
    private final Predicate<String> mine;
    /** Whether new badges get posted to Discord (setting badges.post), and a way to change it. */
    static volatile java.util.function.BooleanSupplier posting = () -> true;
    static volatile java.util.function.Consumer<Boolean> setPosting = b -> { };

    BadgesPage(Supplier<String> account, Predicate<String> mine) {
        this.account = account;
        this.mine = mine;
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) changed();
        });
    }

    @Override
    void fill() {
        String key = account.get();
        Map<String, Badges.Unlock> got = key == null ? java.util.Collections.emptyMap() : Badges.unlocked(key);
        int n = 0;
        for (Badges.Badge b : Badges.ALL) if (got.containsKey(b.id)) n++;
        list.add(heading("badges", n + " of " + Badges.ALL.size()));
        gap(8);
        if (key == null || key.isEmpty()) {
            list.add(note("Start watching and load into the game once, and your badges show up here."));
            return;
        }
        Badges.Stats s = Badges.stats(key, mine);
        list.add(new AboutMe.AbilityBar("Badges unlocked", n + " / " + Badges.ALL.size(), n / (double) Badges.ALL.size()));
        gap(8);
        Pixel.Check post = new Pixel.Check("Post new badges to Discord", posting.getAsBoolean());
        post.addActionListener(e -> setPosting.accept(post.isSelected()));
        list.add(left(post));
        gap(12);
        JPanel grid = new JPanel(new GridLayout(0, 2, 12, 12));
        grid.setOpaque(false);
        // unlocked first (newest first), then the closest to unlocking
        java.util.List<Badges.Badge> order = new java.util.ArrayList<>(Badges.ALL);
        order.sort((a, b) -> {
            Badges.Unlock ua = got.get(a.id), ub = got.get(b.id);
            if ((ua != null) != (ub != null)) return ua != null ? -1 : 1;
            if (ua != null) return Long.compare(ub.at, ua.at);
            double pa = Math.min(1, a.count.of(s) / (double) a.need), pb = Math.min(1, b.count.of(s) / (double) b.need);
            return Double.compare(pb, pa);
        });
        for (Badges.Badge b : order) grid.add(new Card(b, got.get(b.id), Math.min(b.need, b.count.of(s))));
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        list.add(left(grid));
        gap(10);
        list.add(note("Badges are worked out from what the pup has logged (bags, runs, kills, fame, play time, your collection "
                + "and trades). Ones you'd already earned before badges existed are unlocked quietly."));
    }

    /** One badge: a medal, its name and what it's for, and the date or how close you are. */
    static final class Card extends JComponent {
        private final Badges.Badge b;
        private final Badges.Unlock u;
        private final long have;

        Card(Badges.Badge b, Badges.Unlock u, long have) {
            this.b = b;
            this.u = u;
            this.have = have;
            setPreferredSize(new Dimension(300, 74));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Cute.smooth(g);
            int w = getWidth(), h = getHeight();
            boolean on = u != null;
            g.setColor(on ? Cute.mix(Color.WHITE, b.tier.color, 0.10) : new Color(0xF7F5FA));
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, 16, 16));
            g.setColor(on ? b.tier.color : new Color(0xE2DCEA));
            g.setStroke(new BasicStroke(on ? 2.2f : 1.4f));
            g.draw(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 15, 15));
            medal(g, 12, 10, 50, b, on);
            int tx = 74;
            g.setFont(Cute.font(Font.BOLD, 14));
            g.setColor(on ? Pixel.INK : new Color(0x8A7F99));
            g.drawString(b.name, tx, 24);
            g.setFont(Cute.font(Font.PLAIN, 12));
            String d = b.desc;
            FontMetrics fm = g.getFontMetrics();
            while (fm.stringWidth(d) > w - tx - 10 && d.length() > 6) d = d.substring(0, d.length() - 2);
            g.drawString(d.equals(b.desc) ? d : d + "…", tx, 42);
            if (on) {
                g.setFont(Cute.font(Font.BOLD, 11));
                g.setColor(Cute.mix(b.tier.color, Color.BLACK, 0.25));
                g.drawString(u.quiet ? "Unlocked (earned before badges)" : "Unlocked " + new SimpleDateFormat("d MMM yyyy", Locale.ROOT).format(new Date(u.at * 1000L)), tx, 60);
            } else {
                int bw = Math.max(40, w - tx - 70);
                g.setColor(new Color(0xEEE8F3));
                g.fill(new RoundRectangle2D.Double(tx, 52, bw, 7, 7, 7));
                g.setColor(Cute.PINK);
                int fw = (int) Math.round(bw * Math.min(1, have / (double) b.need));
                if (fw > 0) g.fill(new RoundRectangle2D.Double(tx, 52, Math.max(7, fw), 7, 7, 7));
                g.setFont(Cute.font(Font.BOLD, 11));
                g.setColor(new Color(0x8A7F99));
                g.drawString(String.format(Locale.ROOT, "%,d / %,d", have, b.need), tx + bw + 8, 60);
            }
            g.dispose();
            if (!on) setToolTipText(b.desc);
            else setToolTipText(b.desc);
        }

        static void medal(Graphics2D g, int x, int y, int s, Badges.Badge b, boolean on) {
            Color c = on ? b.tier.color : new Color(0xD3CCDD);
            // ribbon
            g.setColor(on ? Cute.mix(c, Pixel.PINK, 0.5) : new Color(0xE4DEEC));
            GeneralPath rib = new GeneralPath();
            rib.moveTo(x + s * 0.30, y + s * 0.55);
            rib.lineTo(x + s * 0.18, y + s * 1.02);
            rib.lineTo(x + s * 0.34, y + s * 0.92);
            rib.lineTo(x + s * 0.44, y + s * 1.05);
            rib.lineTo(x + s * 0.50, y + s * 0.62);
            rib.lineTo(x + s * 0.56, y + s * 1.05);
            rib.lineTo(x + s * 0.66, y + s * 0.92);
            rib.lineTo(x + s * 0.82, y + s * 1.02);
            rib.lineTo(x + s * 0.70, y + s * 0.55);
            rib.closePath();
            g.fill(rib);
            // coin
            double r = s * 0.40;
            double cx = x + s / 2.0, cy = y + s * 0.42;
            g.setColor(Cute.mix(c, Color.BLACK, 0.22));
            g.fill(new Ellipse2D.Double(cx - r, cy - r + 2, r * 2, r * 2));
            g.setColor(c);
            g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g.setColor(Cute.mix(c, Color.WHITE, 0.45));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Double(cx - r + 4, cy - r + 4, r * 2 - 8, r * 2 - 8));
            g.setColor(on ? Color.WHITE : new Color(0xF4F1F8));
            Font f = Cute.font(Font.BOLD, (int) (s * 0.36));
            if (f.canDisplayUpTo(b.glyph) != -1) f = new Font("Dialog", Font.BOLD, (int) (s * 0.36)); // ♥ and ★ aren't in Nunito
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(b.glyph, (float) (cx - fm.stringWidth(b.glyph) / 2.0), (float) (cy + fm.getAscent() / 2.0 - 3));
        }
    }

    /** A medal picture for the Discord post. */
    static byte[] medalPng(Badges.Badge b) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(120, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Cute.smooth(g);
        Card.medal(g, 10, 6, 100, b, true);
        g.dispose();
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
