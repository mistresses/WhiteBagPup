package whitebag;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

import static whitebag.AboutMe.heading;
import static whitebag.AboutMe.left;
import static whitebag.AboutMe.note;

/** The Wardrobe (under Me): dress the pup in hats and outfits unlocked by badges. */
final class WardrobePage extends MoreTabs.Page {
    WardrobePage() {
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) changed();
        });
    }

    @Override
    void fill() {
        list.add(heading("wardrobe", Wardrobe.owned() + " of " + Wardrobe.total() + " unlocked"));
        gap(8);
        list.add(note("Dress up the pup at the top of the app. New hats and outfits unlock with badges: hover over a locked one "
                + "to see which. The witch hat and Santa hat are always here (and the pup wears them by itself in October and "
                + "December if you pick Seasonal)."));
        gap(12);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.setOpaque(false);
        top.add(new Preview());
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, top.getPreferredSize().height));
        list.add(left(top));
        gap(14);

        list.add(heading("hats", null));
        gap(8);
        JPanel hats = grid();
        hats.add(new Choice("Seasonal", null, PixelPup.Hat.NONE, null, PixelPup.wornHat == null, true, () -> Wardrobe.wear((PixelPup.Hat) null)));
        for (PixelPup.Hat h : PixelPup.Hat.values()) {
            String badge = Wardrobe.HATS.get(h);
            hats.add(new Choice(h.label, badge, h, null, PixelPup.wornHat == h, Wardrobe.has(h), () -> Wardrobe.wear(h)));
        }
        hats.setMaximumSize(new Dimension(Integer.MAX_VALUE, hats.getPreferredSize().height));
        list.add(left(hats));
        gap(14);

        list.add(heading("outfits", null));
        gap(8);
        JPanel fits = grid();
        for (PixelPup.Outfit o : PixelPup.Outfit.values()) {
            String badge = Wardrobe.OUTFITS.get(o);
            fits.add(new Choice(o.label, badge, null, o, PixelPup.wornOutfit == o, Wardrobe.has(o), () -> Wardrobe.wear(o)));
        }
        fits.setMaximumSize(new Dimension(Integer.MAX_VALUE, fits.getPreferredSize().height));
        list.add(left(fits));
    }

    private static JPanel grid() {
        JPanel p = new JPanel(new GridLayout(0, 5, 10, 10));
        p.setOpaque(false);
        return p;
    }

    private static PixelPup.Hat shownHat() {
        return PixelPup.hatFor(java.time.LocalDate.now());
    }

    /** The pup as it looks now, big. */
    private static final class Preview extends JComponent {
        Preview() {
            setPreferredSize(new Dimension(PixelPup.W * 6 + 24, PixelPup.H * 6 + 16));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Cute.smooth(g);
            g.setColor(new Color(0xF8F4FC));
            g.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 18, 18));
            BufferedImage f = PixelPup.frame(PixelPup.Face.HAPPY, PixelPup.Ears.UP, PixelPup.Tail.UP, shownHat(), PixelPup.wornOutfit);
            PixelPup.draw(g, f, 12, 8, 6);
            g.dispose();
        }
    }

    /** One hat or outfit: the pup wearing it, its name, and what unlocks it if it's locked. */
    private final class Choice extends JComponent {
        private final String label;
        private final PixelPup.Hat hat;
        private final PixelPup.Outfit outfit;
        private final boolean worn, open;
        private boolean over;

        Choice(String label, String badge, PixelPup.Hat hat, PixelPup.Outfit outfit, boolean worn, boolean open, Runnable wear) {
            this.label = label;
            this.hat = hat;
            this.outfit = outfit;
            this.worn = worn;
            this.open = open;
            setPreferredSize(new Dimension(104, 124));
            Badges.Badge b = badge == null ? null : Badges.byId(badge);
            if (b != null) setToolTipText(open ? "Unlocked with the " + b.name + " badge" : "Unlock with the " + b.name + " badge: " + b.desc);
            setCursor(open ? PixelPup.pawCursor() : Cursor.getDefaultCursor());
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { over = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) {
                    if (!open) return;
                    Squeak.boop();
                    wear.run();
                    changed();
                }
            });
            lock = b == null ? "" : b.name;
        }

        private final String lock;

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Cute.smooth(g);
            int w = getWidth(), h = getHeight();
            g.setColor(worn ? Pixel.PINK_SOFT : open ? (over ? new Color(0xF7F3FB) : Color.WHITE) : new Color(0xF4F2F7));
            g.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 14, 14));
            g.setColor(worn ? Pixel.PINK : open ? Pixel.LINE : new Color(0xE2DCEA));
            g.setStroke(new BasicStroke(worn ? 2.4f : 1.4f));
            g.draw(new RoundRectangle2D.Double(1, 1, w - 3, h - 3, 13, 13));
            PixelPup.Hat hh = hat != null ? hat : (outfit != null ? PixelPup.Hat.NONE : null);
            PixelPup.Outfit oo = outfit != null ? outfit : PixelPup.Outfit.NONE;
            if (label.equals("Seasonal")) hh = PixelPup.seasonalHat(java.time.LocalDate.now());
            BufferedImage f = PixelPup.frame(PixelPup.Face.OPEN, PixelPup.Ears.UP, PixelPup.Tail.SIDE, hh, oo);
            if (!open) f = grey(f);
            PixelPup.draw(g, f, (w - PixelPup.W * 3) / 2, 4, 3);
            g.setFont(Cute.font(Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(open ? Pixel.INK : new Color(0x9C92AC));
            g.drawString(label, (w - fm.stringWidth(label)) / 2, h - (open ? 12 : 22));
            if (!open) {
                g.setFont(Cute.font(Font.PLAIN, 10));
                FontMetrics f2 = g.getFontMetrics();
                String s = lock;
                while (f2.stringWidth(s) > w - 10 && s.length() > 4) s = s.substring(0, s.length() - 1);
                if (!s.equals(lock)) s = s.trim() + "…";
                g.drawString(s, (w - f2.stringWidth(s)) / 2, h - 8);
            } else if (worn) {
                Cute.drawHeart(g, w - 18, 6, 10, Pixel.PINK);
            }
            g.dispose();
        }
    }

    private static BufferedImage grey(BufferedImage c) {
        BufferedImage out = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < c.getHeight(); y++) {
            for (int x = 0; x < c.getWidth(); x++) {
                int argb = c.getRGB(x, y), a = argb >>> 24;
                int l = (int) (0.3 * ((argb >> 16) & 255) + 0.59 * ((argb >> 8) & 255) + 0.11 * (argb & 255));
                l = 160 + l / 3;
                out.setRGB(x, y, (a * 55 / 100) << 24 | l << 16 | l << 8 | l);
            }
        }
        return out;
    }
}
