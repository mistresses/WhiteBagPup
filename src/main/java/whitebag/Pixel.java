package whitebag;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * The window's look: pixel-stepped frames like the game's own UI, a hand-made lowercase pixel font
 * for titles, and a few widgets built from them.
 * <p>
 * Rule of thumb used throughout: an ink outline with a hard offset shadow means "you can press this";
 * containers get a quiet light frame and no shadow.
 */
final class Pixel {
    private Pixel() {
    }

    // ------------------------------------------------------------------ palette

    static final Color PAGE = Theme.c(0xE9E2F3);
    static final Color SHEET = Theme.WHITE;
    static final Color INK = Theme.c(0x3A2B4E);
    static final Color MUTED = Theme.BLACK; // was grey (0x84779A); black reads better over a background picture
    static final Color LINE = Theme.c(0xD5C9E4);
    static final Color TAB_OFF = Theme.c(0xDCD2EA);
    static final Color PINK = Theme.c(0xFF6FA8);
    static final Color PINK_SOFT = Theme.c(0xFFE2EE);
    static final Color PINK_INK = Theme.c(0xD63F7E);
    static final Color TEAL = Theme.c(0x2FA88F);
    static final Color TRACK = Theme.c(0xF1ECF7);
    static final Color OTHERS = Theme.c(0xBBA8F0);
    /** The picked option in a row of choices: plum with white text by day, soft pink at night. */
    static final Color CHOSEN = Theme.DARK ? new Color(0x5A2A4F) : new Color(0x3A2B4E);
    static final Color CHOSEN_EDGE = Theme.DARK ? PINK : CHOSEN;
    static final Color CHOSEN_TEXT = Theme.DARK ? INK : Color.WHITE;

    /**
     * Fill for the card the tab pages sit on. Plain white normally; see-through white when a
     * background picture is in use (set once by the app before the window is built).
     */
    static Color sheetFill = SHEET;

    static boolean glass() {
        return sheetFill.getAlpha() < 255;
    }

    static void setCardOpacity(int percent) {
        int a = Math.max(0, Math.min(255, Math.round(percent * 2.55f)));
        sheetFill = Theme.c(255, 255, 255, a);
    }

    /** One pixel-art "pixel" in screen pixels. */
    static final int P = 3;

    // ------------------------------------------------------------------ frames

    /** A rectangle with each corner cut by one step, like 1px-rounded pixel art. */
    static Shape stepped(double x, double y, double w, double h, double s) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x + s, y);
        p.lineTo(x + w - s, y);
        p.lineTo(x + w - s, y + s);
        p.lineTo(x + w, y + s);
        p.lineTo(x + w, y + h - s);
        p.lineTo(x + w - s, y + h - s);
        p.lineTo(x + w - s, y + h);
        p.lineTo(x + s, y + h);
        p.lineTo(x + s, y + h - s);
        p.lineTo(x, y + h - s);
        p.lineTo(x, y + s);
        p.lineTo(x + s, y + s);
        p.closePath();
        return p;
    }

    /** Filled stepped frame with a crisp outline of thickness t. */
    static void frame(Graphics2D g, int x, int y, int w, int h, Color fill, Color outline, int t) {
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        if (outline != null && t > 0 && fill != null && fill.getAlpha() < 255) {
            // see-through fill: draw only the outline ring, so the outline colour doesn't show through
            Shape inner = stepped(x + t, y + t, w - 2 * t, h - 2 * t, t);
            java.awt.geom.Area ring = new java.awt.geom.Area(stepped(x, y, w, h, t));
            ring.subtract(new java.awt.geom.Area(inner));
            g.setColor(outline);
            g.fill(ring);
            g.setColor(fill);
            g.fill(inner);
        } else if (outline != null && t > 0) {
            g.setColor(outline);
            g.fill(stepped(x, y, w, h, t));
            if (fill != null) {
                g.setColor(fill);
                g.fill(stepped(x + t, y + t, w - 2 * t, h - 2 * t, t));
            }
        } else if (fill != null) {
            g.setColor(fill);
            g.fill(stepped(x, y, w, h, P));
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    /** Border that draws a stepped frame around a component (fields, scroll panes). */
    static class FrameBorder extends AbstractBorder {
        private final int pad;

        FrameBorder(int pad) {
            this.pad = pad;
        }

        @Override
        public void paintBorder(Component c, Graphics g0, int x, int y, int w, int h) {
            Graphics2D g = (Graphics2D) g0.create();
            Color col = c.isFocusOwner() ? PINK : LINE;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(col);
            java.awt.geom.Area ring = new java.awt.geom.Area(stepped(x, y, w, h, 2));
            ring.subtract(new java.awt.geom.Area(stepped(x + 2, y + 2, w - 4, h - 4, 2)));
            g.fill(ring);
            g.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(pad, pad + 4, pad, pad + 4);
        }
    }

    // ------------------------------------------------------------------ pixel font

    private static final Map<Character, String[]> GLYPHS = new HashMap<>();

    private static void glyph(char c, String rows) {
        GLYPHS.put(c, rows.split("\\|"));
    }

    static {
        glyph('a', ".....|.....|.###.|....#|.####|#...#|.####");
        glyph('b', "#....|#....|####.|#...#|#...#|#...#|####.");
        glyph('c', ".....|.....|.###.|#....|#....|#....|.###.");
        glyph('d', "....#|....#|.####|#...#|#...#|#...#|.####");
        glyph('e', ".....|.....|.###.|#...#|#####|#....|.###.");
        glyph('f', "..##|.#..|####|.#..|.#..|.#..|.#..");
        glyph('g', ".....|.....|.####|#...#|#...#|#...#|.####|....#|.###.");
        glyph('h', "#....|#....|####.|#...#|#...#|#...#|#...#");
        glyph('i', "#|.|#|#|#|#|#");
        glyph('j', "..#|...|..#|..#|..#|..#|..#|..#|##.");
        glyph('k', "#...|#...|#..#|#.#.|##..|#.#.|#..#");
        glyph('l', "#.|#.|#.|#.|#.|#.|.#");
        glyph('m', ".....|.....|##.#.|#.#.#|#.#.#|#.#.#|#.#.#");
        glyph('n', ".....|.....|####.|#...#|#...#|#...#|#...#");
        glyph('o', ".....|.....|.###.|#...#|#...#|#...#|.###.");
        glyph('p', ".....|.....|####.|#...#|#...#|#...#|####.|#....|#....");
        glyph('q', ".....|.....|.####|#...#|#...#|#...#|.####|....#|....#");
        glyph('r', "....|....|#.##|##..|#...|#...|#...");
        glyph('s', ".....|.....|.####|#....|.###.|....#|####.");
        glyph('t', ".#..|.#..|####|.#..|.#..|.#..|..##");
        glyph('u', ".....|.....|#...#|#...#|#...#|#...#|.####");
        glyph('v', ".....|.....|#...#|#...#|#...#|.#.#.|..#..");
        glyph('w', ".....|.....|#...#|#...#|#.#.#|#.#.#|.#.#.");
        glyph('x', ".....|.....|#...#|.#.#.|..#..|.#.#.|#...#");
        glyph('y', ".....|.....|#...#|#...#|#...#|#...#|.####|....#|.###.");
        glyph('z', ".....|.....|#####|...#.|..#..|.#...|#####");
        glyph('0', ".###.|#...#|#..##|#.#.#|##..#|#...#|.###.");
        glyph('1', "..#..|.##..|..#..|..#..|..#..|..#..|.###.");
        glyph('2', ".###.|#...#|....#|...#.|..#..|.#...|#####");
        glyph('3', "####.|....#|....#|.###.|....#|....#|####.");
        glyph('4', "...#.|..##.|.#.#.|#..#.|#####|...#.|...#.");
        glyph('5', "#####|#....|####.|....#|....#|#...#|.###.");
        glyph('6', ".###.|#....|#....|####.|#...#|#...#|.###.");
        glyph('7', "#####|....#|...#.|..#..|.#...|.#...|.#...");
        glyph('8', ".###.|#...#|#...#|.###.|#...#|#...#|.###.");
        glyph('9', ".###.|#...#|#...#|.####|....#|....#|.###.");
        glyph(' ', "...|...|...|...|...|...|...");
        glyph('!', "#|#|#|#|#|.|#");
        glyph('?', ".###.|#...#|....#|...#.|..#..|.....|..#..");
        glyph('.', ".|.|.|.|.|.|#");
        glyph(',', ".|.|.|.|.|.|#|#");
        glyph('\'', "#|#|.|.|.|.|.");
        glyph('-', "...|...|...|###|...|...|...");
        glyph(':', ".|.|#|.|.|#|.");
        glyph('/', "....#|...#.|...#.|..#..|.#...|.#...|#....");
        glyph('(', ".#|#.|#.|#.|#.|#.|.#");
        glyph(')', "#.|.#|.#|.#|.#|.#|#.");
        glyph('+', ".....|.....|..#..|..#..|#####|..#..|..#..");
        glyph('~', "....|....|....|.#.#|#.#.|....|....");
    }

    private static String[] g(char c) {
        String[] r = GLYPHS.get(Character.toLowerCase(c));
        return r != null ? r : GLYPHS.get('?');
    }

    // Headings, tabs and the title use the normal bold font, capitalised ("Bags to Watch").
    // Single letters (the pup's sleepy "z"s) still use the little pixel font.

    private static final java.util.Set<String> SMALL_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "a", "an", "and", "of", "to", "the", "in", "on", "for", "or"));
    private static final java.awt.image.BufferedImage MEASURE = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);

    static Font headingFont(int scale) {
        return Cute.font(Font.BOLD, scale * 8.5f);
    }

    static String titleCase(String s) {
        StringBuilder out = new StringBuilder();
        String[] words = s.split(" ", -1);
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (i > 0) out.append(' ');
            if (w.isEmpty()) continue;
            if (w.equalsIgnoreCase("dps")) out.append("DPS");
            else if (i > 0 && SMALL_WORDS.contains(w.toLowerCase(java.util.Locale.ROOT))) out.append(w);
            else out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    private static boolean pixelLook(String s) {
        return s.length() <= 1;
    }

    /** Width in screen pixels of text drawn at this scale. */
    static int textWidth(String s, int scale) {
        if (!pixelLook(s)) {
            Graphics2D g = MEASURE.createGraphics();
            Cute.smooth(g);
            int w = g.getFontMetrics(headingFont(scale)).stringWidth(titleCase(s));
            g.dispose();
            return w;
        }
        int w = 0;
        for (char c : s.toCharArray()) w += (g(c)[0].length() + 1) * scale;
        return Math.max(0, w - scale);
    }

    /** Height of the letters above the baseline (7 rows). */
    static int capHeight(int scale) {
        return 7 * scale;
    }

    /** Draws text with its top-left at (x, top). */
    static void text(Graphics2D g, String s, int x, int top, int scale, Color c) {
        g.setColor(c);
        if (!pixelLook(s)) {
            Graphics2D t = (Graphics2D) g.create();
            Cute.smooth(t);
            t.setFont(headingFont(scale));
            t.drawString(titleCase(s), x, top + capHeight(scale));
            t.dispose();
            return;
        }
        for (char ch : s.toCharArray()) {
            String[] rows = g(ch);
            for (int r = 0; r < rows.length; r++) {
                for (int col = 0; col < rows[r].length(); col++) {
                    if (rows[r].charAt(col) == '#') g.fillRect(x + col * scale, top + r * scale, scale, scale);
                }
            }
            x += (rows[0].length() + 1) * scale;
        }
    }

    /** A section heading in the pixel font. */
    static class Heading extends JComponent {
        private final String text;
        private final int scale;
        private String right;

        Heading(String text, int scale) {
            this.text = text;
            this.scale = scale;
            setAlignmentX(LEFT_ALIGNMENT);
        }

        /** Optional small text on the right of the heading line (e.g. a count). */
        void setRight(String r) {
            right = r;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(textWidth(text, scale) + 4, 9 * scale + 2);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            text(g, text, 0, 1, scale, INK);
            if (right != null) {
                Cute.smooth(g);
                g.setFont(Cute.font(Font.PLAIN, 12));
                g.setColor(MUTED);
                FontMetrics fm = g.getFontMetrics();
                g.drawString(right, getWidth() - fm.stringWidth(right), 1 + capHeight(scale));
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ widgets

    /** Pressable button: ink outline and a hard shadow that disappears while pressed. */
    static class Button extends JButton {
        private boolean primary;
        private boolean over, down;

        /** Pink (on) or plain; used for on/off buttons. */
        void setPrimary(boolean p) {
            primary = p;
            repaint();
        }

        Button(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setFont(Cute.font(Font.BOLD, 13));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(8, 16, 11, 16));
            addActionListener(e -> Squeak.boop());
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { over = false; down = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { down = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e) { down = false; repaint(); }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            int w = getWidth(), h = getHeight() - 3;
            boolean on = isEnabled();
            boolean pressed = down && on;
            int dy = pressed ? 3 : 0;
            Color fill = !on ? TRACK : primary ? (over ? Theme.c(0xFF85B6) : PINK) : (over ? PINK_SOFT : SHEET);
            Color edge = on ? INK : LINE;
            if (on && !pressed) frame(g, 0, 3, w, h, edge, edge, 2);
            frame(g, 0, dy, w, h, fill, edge, 2);
            if (isFocusOwner()) {
                g.setColor(PINK_INK);
                g.fillRect(6, dy + h - 6, w - 12, 2);
            }
            Cute.smooth(g);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(!on ? MUTED : primary ? Theme.WHITE : INK);
            g.drawString(getText(), (w - fm.stringWidth(getText())) / 2, dy + (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    /** Square pixel checkbox with a label. */
    static class Check extends JToggleButton {
        Check(String text, boolean on) {
            super(text, on);
            setFont(Cute.font(Font.PLAIN, 13));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(4, 28, 4, 4));
            addActionListener(e -> Squeak.boop());
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            int h = getHeight(), s = 18, y = (h - s) / 2;
            frame(g, 0, y, s, s, isSelected() ? PINK : SHEET, INK, 2);
            if (isSelected()) {
                g.setColor(Theme.WHITE);
                int[][] tick = {{4, 9}, {6, 11}, {8, 9}, {10, 7}, {12, 5}};
                for (int[] t : tick) g.fillRect(t[0], y + t[1] - 1, 2, 3);
            }
            if (isFocusOwner()) {
                g.setColor(PINK_INK);
                g.fillRect(0, y + s + 2, s, 2);
            }
            Cute.smooth(g);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(INK);
            g.drawString(getText(), 28, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    /** Status text with a small square light: grey when asleep, teal when watching. */
    static class Status extends JComponent {
        private String text = "";
        private boolean active;

        Status() {
            setFont(Cute.font(Font.BOLD, 13));
        }

        void set(String text, boolean active) {
            this.text = text;
            this.active = active;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(getFontMetrics(getFont()).stringWidth(text) + 22, 30);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            int h = getHeight();
            g.setColor(active ? TEAL : LINE);
            g.fillRect(0, h / 2 - 5, 10, 10);
            Cute.smooth(g);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(active ? Theme.c(0x1F7A67) : MUTED);
            g.drawString(text, 18, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    static JTextField field(String text, int cols, String placeholder) {
        JTextField f = new JTextField(text, cols) {
            @Override
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D p = (Graphics2D) g0.create();
                    Cute.smooth(p);
                    p.setColor(Theme.BLACK);
                    p.setFont(getFont().deriveFont(Font.ITALIC)); // italic so example text doesn't look like something you typed
                    Insets in = getInsets();
                    FontMetrics fm = p.getFontMetrics();
                    p.drawString(placeholder, in.left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                    p.dispose();
                }
            }
        };
        f.setBackground(SHEET);
        f.setFont(Cute.font(Font.PLAIN, 13));
        f.setForeground(INK);
        f.setCaretColor(PINK_INK);
        f.setSelectionColor(PINK_SOFT);
        f.setSelectedTextColor(INK);
        f.setBorder(new FrameBorder(7));
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { f.repaint(); }
            @Override public void focusLost(FocusEvent e) { f.repaint(); }
        });
        return f;
    }

    static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Cute.font(Font.BOLD, 13));
        l.setForeground(INK);
        return l;
    }

    static JLabel note(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Cute.font(Font.PLAIN, 12));
        l.setForeground(MUTED);
        return l;
    }

    // ------------------------------------------------------------------ bag slot

    /** An inventory slot holding a bag sprite; toggles whether that bag is watched. */
    static class BagSlot extends JToggleButton {
        static final int SIZE = 56;
        private final BagType type;
        private BufferedImage sprite;
        private boolean over;

        BagSlot(BagType type, boolean on) {
            super("", on);
            this.type = type;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setToolTipText(type.displayName + " bag (turn it on to hear its sound)");
            setCursor(PixelPup.pawCursor());
            addActionListener(e -> { if (!isSelected()) Squeak.boop(); }); // turning on plays the bag's own sound instead
            getAccessibleContext().setAccessibleName(type.displayName + " bag");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { over = false; repaint(); }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(SIZE, SIZE + 3);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            boolean on = isSelected();
            int s = SIZE;
            if (on) frame(g, 0, 3, s, s, INK, INK, 2);
            frame(g, 0, 0, s, s, on ? PINK_SOFT : (over ? Theme.c(0xF7F3FB) : SHEET), on ? INK : (over ? INK : LINE), on ? 3 : 2);
            if (sprite == null) {
                BufferedImage raw = LootImage.sprites.sprite(type.spriteType());
                if (raw != null) sprite = LootImage.pixelIcon(raw, 40);
            }
            if (sprite != null) {
                Composite c = g.getComposite();
                if (!on) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
                g.drawImage(sprite, (s - sprite.getWidth()) / 2, (s - sprite.getHeight()) / 2, null);
                g.setComposite(c);
            } else {
                Graphics2D gb = (Graphics2D) g.create();
                if (!on) gb.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
                Cute.drawBag(gb, 10, 8, 36, Cute.chipColor(type), false, false);
                gb.dispose();
            }
            if (type.name().startsWith("BOOSTED")) {
                // small "+" tab in the corner marks a boosted bag
                frame(g, s - 17, 4, 13, 13, on ? PINK : SHEET, on ? INK : LINE, 2);
                g.setColor(on ? Theme.WHITE : MUTED);
                g.fillRect(s - 12, 7, 3, 7);
                g.fillRect(s - 14, 9, 7, 3);
            }
            if (isFocusOwner()) {
                g.setColor(PINK_INK);
                g.fillRect(8, s - 6, s - 16, 2);
            }
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ tabs and the sheet

    /** Folder tabs in the pixel font; the selected one joins the white sheet below it. */
    static class Tabs extends JComponent {
        interface Select {
            void selected(int index);
        }

        static final int H = 40;
        private final String[] names;
        private final int[] unread;
        private final Rectangle[] boxes;
        private final Select onSelect;
        private int selected = 0, hover = -1;

        Tabs(String[] names, Select onSelect) {
            this.names = names;
            this.onSelect = onSelect;
            unread = new int[names.length];
            boxes = new Rectangle[names.length];
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // tabs that don't fit go onto a second row; re-measure when the window is resized
            addComponentListener(new java.awt.event.ComponentAdapter() {
                private int rowsSeen = 1;
                @Override public void componentResized(java.awt.event.ComponentEvent e) {
                    int r = rows().size();
                    if (r != rowsSeen) {
                        rowsSeen = r;
                        revalidate();
                    }
                    repaint();
                }
            });
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) { int h = hit(e.getPoint()); if (h != hover) { hover = h; repaint(); } }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
                @Override public void mousePressed(MouseEvent e) { int h = hit(e.getPoint()); if (h >= 0 && h != selected) { Squeak.boop(); select(h); } }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        int selected() {
            return selected;
        }

        /** How far each row of tabs sits below the one before (rows never overlap, and never swap places). */
        private static final int ROW_STEP = H;
        private int pad = 18, gap = 4;

        private int tabWidth(int i) {
            return textWidth(names[i], 2) + pad * 2 + (unread[i] > 0 ? 26 : 0);
        }

        /** Tabs in their fixed order: squeezed a little to fit on one row, and onto a second row only if they still don't. */
        private java.util.List<java.util.List<Integer>> rows() {
            int avail = getWidth() > 0 ? getWidth() : 560;
            pad = 18;
            gap = 4;
            java.util.List<java.util.List<Integer>> normal = layout(avail);
            if (normal.size() == 1) return normal;
            // squeezed a little if that saves a row (one row instead of two, two instead of three)
            pad = 10;
            gap = 2;
            java.util.List<java.util.List<Integer>> squeezed = layout(avail);
            if (squeezed.size() < normal.size()) return squeezed;
            pad = 18;
            gap = 4;
            return normal;
        }

        /** Rows at the current padding, evened out. */
        private java.util.List<java.util.List<Integer>> layout(int avail) {
            java.util.List<java.util.List<Integer>> rows = new java.util.ArrayList<>();
            java.util.List<Integer> row = new java.util.ArrayList<>();
            int x = 0;
            for (int i = 0; i < names.length; i++) {
                int w = tabWidth(i);
                if (!row.isEmpty() && x + w > avail) {
                    rows.add(row);
                    row = new java.util.ArrayList<>();
                    x = 0;
                }
                row.add(i);
                x += w + gap;
            }
            rows.add(row);
            if (rows.size() > 1) {
                // even the rows out (5 + 5 rather than 9 + 1), keeping the order
                int n = rows.size();
                int target = (totalWidth() + gap) / n;
                java.util.List<java.util.List<Integer>> even = new java.util.ArrayList<>();
                java.util.List<Integer> cur = new java.util.ArrayList<>();
                int cx = 0;
                for (int i = 0; i < names.length; i++) {
                    int w = tabWidth(i) + gap;
                    boolean lastRow = even.size() == n - 1;
                    if (!cur.isEmpty() && !lastRow && (cx + w / 2 > target || cx + w > avail)) {
                        even.add(cur);
                        cur = new java.util.ArrayList<>();
                        cx = 0;
                    }
                    cur.add(i);
                    cx += w;
                }
                even.add(cur);
                boolean fits = true;
                for (java.util.List<Integer> r : even) {
                    int t = 0;
                    for (int i : r) t += tabWidth(i) + gap;
                    if (t - gap > avail) fits = false;
                }
                if (fits) return even;
            }
            return rows;
        }

        private int totalWidth() {
            int t = 0;
            for (int i = 0; i < names.length; i++) t += tabWidth(i) + gap;
            return t - gap;
        }

        /** True when the selected tab is on the bottom row, where it opens into the sheet. */
        private boolean selectedOnLastRow() {
            java.util.List<java.util.List<Integer>> rows = rows();
            return rows.get(rows.size() - 1).contains(selected);
        }

        @Override
        public Dimension getPreferredSize() {
            int n = rows().size();
            return new Dimension(560, H + (n - 1) * ROW_STEP);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        /** The selected tab's box, or null if it's on an upper row (then the sheet keeps its top edge). */
        Rectangle selectedBox() {
            return selectedOnLastRow() ? boxes[selected] : null;
        }

        void select(int i) {
            selected = i;
            unread[i] = 0;
            repaint();
            if (getParent() != null) getParent().repaint();
            onSelect.selected(i);
        }

        /** The unread counts (copied), so another account's counts can be swapped in. */
        int[] unread() {
            return unread.clone();
        }

        void setUnread(int[] counts) {
            for (int i = 0; i < unread.length; i++) unread[i] = i == selected || i >= counts.length ? 0 : counts[i];
            repaint();
        }

        void bump(int i) {
            if (i != selected) {
                unread[i] = Math.min(99, unread[i] + 1);
                repaint();
            }
        }

        private int hit(Point p) {
            for (int i = 0; i < boxes.length; i++) if (boxes[i] != null && boxes[i].contains(p)) return i;
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            java.util.List<java.util.List<Integer>> rows = rows();
            for (int r = 0; r < rows.size(); r++) {
            int x = 0, rowY = r * ROW_STEP;
            for (int i : rows.get(r)) {
                int tw = textWidth(names[i], 2);
                int w = tabWidth(i);
                boolean on = i == selected;
                int top = rowY + (on ? 0 : 6);
                // the box the Sheet opens under: in the bottom row's coordinates, which is where the sheet starts
                boxes[i] = new Rectangle(x, rowY, w, H);
                // tab: outline on three sides; the selected tab runs into the sheet
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                Color edge = on ? LINE : (i == hover ? INK : TAB_OFF);
                Color fill = on ? sheetFill : (i == hover ? Theme.c(0xEFE9F7) : TAB_OFF);
                Shape inner = stepped(x + 2, top + 2, w - 4, rowY + H - top + 6, 2);
                java.awt.geom.Area ring = new java.awt.geom.Area(stepped(x, top, w, rowY + H - top + 6, 2));
                ring.subtract(new java.awt.geom.Area(inner));
                g.setColor(edge);
                g.fill(ring);
                g.setColor(fill);
                g.fill(inner);
                text(g, names[i], x + pad, top + 11, 2, on ? INK : MUTED);
                if (unread[i] > 0) {
                    String n = unread[i] >= 99 ? "99" : String.valueOf(unread[i]);
                    int bw = Math.max(18, n.length() * 7 + 8), bx = x + pad + tw + 8, by = top + 10;
                    frame(g, bx, by, bw, 16, PINK, null, 0);
                    Cute.smooth(g);
                    g.setFont(Cute.font(Font.BOLD, 11));
                    FontMetrics fm = g.getFontMetrics();
                    g.setColor(Theme.WHITE);
                    g.drawString(n, bx + (bw - fm.stringWidth(n)) / 2, by + 12);
                }
                x += w + gap;
            }
            }
            g.dispose();
        }
    }

    /** The white sheet the tab pages sit on; the selected tab opens into it. */
    static class Sheet extends JPanel {
        Tabs tabs;

        Sheet(LayoutManager l) {
            super(l);
            setOpaque(false);
            setBorder(new EmptyBorder(20, 22, 22, 22));
        }

        /** Height for pages that scroll inside themselves; the sheet then stretches to fill the window. */
        static final int SCROLLING_PAGE_HEIGHT = 300;

        /**
         * Only as tall as the page on show (not the tallest page, which is what a card layout does), so short
         * tabs don't leave the window scrolling through empty space. Pages with their own scrollbar ask for a
         * modest height and fill whatever the window has.
         */
        @Override
        public Dimension getPreferredSize() {
            Component shown = null;
            for (Component c : getComponents()) if (c.isVisible()) shown = c;
            if (shown == null) return super.getPreferredSize();
            Insets in = getInsets();
            Dimension d = shown.getPreferredSize();
            int h = Boolean.TRUE.equals(((JComponent) shown).getClientProperty("pixel.ownScroll"))
                    ? SCROLLING_PAGE_HEIGHT : d.height;
            return new Dimension(d.width + in.left + in.right, h + in.top + in.bottom);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Rectangle r = tabs != null ? tabs.selectedBox() : null;
            Shape oldClip = g.getClip();
            if (r != null) g.clip(gapClip(r)); // leave the top edge open under the selected tab
            frame(g, 0, 0, getWidth(), getHeight(), sheetFill, LINE, 2);
            if (r != null) {
                g.setClip(oldClip);
                g.setColor(sheetFill);
                g.fillRect(r.x + 2, 0, r.width - 4, 2);
            }
            g.dispose();
        }

        private Shape gapClip(Rectangle r) {
            java.awt.geom.Area a = new java.awt.geom.Area(new Rectangle(0, 0, getWidth(), getHeight()));
            a.subtract(new java.awt.geom.Area(new Rectangle(r.x + 2, 0, r.width - 4, 2)));
            return a;
        }
    }

    // ------------------------------------------------------------------ scrolling

    static JScrollPane scroll(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(new FrameBorder(2));
        s.getViewport().setBackground(SHEET);
        s.setBackground(SHEET);
        s.getVerticalScrollBar().setUnitIncrement(18);
        s.getVerticalScrollBar().setUI(new ThinBar());
        s.getVerticalScrollBar().setOpaque(false);
        s.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        wheelPassThrough(s);
        if (glass()) {
            c.setOpaque(false);
            s.getViewport().setOpaque(false);
            s.setOpaque(false);
        }
        return s;
    }

    /**
     * Mouse wheel: scroll this box while it can, then hand the wheel to the window behind it, so the
     * wheel always moves something instead of getting stuck on a box that's already at its end.
     */
    static void wheelPassThrough(JScrollPane s) {
        s.setWheelScrollingEnabled(false);
        s.addMouseWheelListener(e -> {
            JScrollBar bar = s.getVerticalScrollBar();
            int dir = e.getWheelRotation() < 0 ? -1 : 1;
            boolean canScroll = bar.isVisible() && bar.getMaximum() - bar.getMinimum() > bar.getVisibleAmount()
                    && (dir < 0 ? bar.getValue() > bar.getMinimum() : bar.getValue() + bar.getVisibleAmount() < bar.getMaximum());
            if (canScroll) {
                int step = Math.max(1, bar.getUnitIncrement(dir)) * 3 * Math.max(1, Math.abs(e.getWheelRotation()));
                bar.setValue(bar.getValue() + dir * step);
                e.consume();
                return;
            }
            Container outer = SwingUtilities.getAncestorOfClass(JScrollPane.class, s);
            if (outer != null) outer.dispatchEvent(SwingUtilities.convertMouseEvent(s, e, outer));
        });
    }

    /** Thin square scrollbar without arrow buttons. */
    static class ThinBar extends BasicScrollBarUI {
        @Override
        protected JButton createDecreaseButton(int o) {
            return zero();
        }

        @Override
        protected JButton createIncreaseButton(int o) {
            return zero();
        }

        private static JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            if (glass()) return;
            g.setColor(SHEET);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            frame((Graphics2D) g, r.x + 3, r.y + 2, r.width - 6, r.height - 4, isDragging ? MUTED : LINE, null, 0);
        }
    }
}
