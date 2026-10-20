package whitebag;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The puppy mascot, little drawn shapes, and the header. Painted in code (no emoji or image
 * files) so they look the same on every PC. Colours here are also used for the Discord pictures.
 */
final class Cute {
    private Cute() {
    }

    // Palette
    static final Color BG = new Color(0xFFF4F7);
    static final Color CARD = Color.WHITE;
    static final Color CARD_EDGE = new Color(0xF8D7E3);
    static final Color SHADOW = new Color(0xF2C9D8);
    static final Color TEXT = new Color(0x5B4A5E);
    static final Color MUTED = new Color(0xA8909F);
    static final Color PINK = new Color(0xFF9EBB);
    static final Color PINK_DARK = new Color(0xF0789C);
    static final Color LAVENDER = new Color(0xC7B8F5);
    static final Color LAVENDER_DARK = new Color(0xA996EC);
    static final Color MINT = new Color(0x8FDDBF);
    static final Color MINT_DARK = new Color(0x6CCBA7);
    static final Color PEACH = new Color(0xFFC3A0);
    static final Color PEACH_DARK = new Color(0xF7A57A);
    static final Color FIELD = new Color(0xFFF9FB);
    static final Color BLUSH = new Color(0xFFB3C7);

    /**
     * The app's own font, bundled in the jar (fonts/), so text looks the same on every PC. Windows' own Segoe UI is
     * drawn unevenly by Java on Windows 11 (some letters, like W, come out in another weight). Falls back to the
     * system fonts if the bundled ones can't be read. The font is Nunito (SIL Open Font License, see licenses/).
     */
    private static final Font[] BUNDLED = loadBundled();

    static final String FONT = BUNDLED != null ? BUNDLED[0].getFamily() : pickFont("Segoe UI", "Segoe UI Variable Text", "Helvetica Neue", "SansSerif");

    /** A heavier face for everyday text, so it reads easily over a background picture. */
    static final String SEMIBOLD = BUNDLED != null ? BUNDLED[0].getFamily()
            : pickFont("Segoe UI Semibold", "Segoe UI Variable Text Semibold", "Helvetica Neue Medium", "");

    /** {semibold, bold, extra bold} from the jar, or null. */
    private static Font[] loadBundled() {
        String base = "Nunito";
        String[] files = {base + "_600SemiBold.ttf", base + "_700Bold.ttf", base + "_800ExtraBold.ttf"};
        Font[] out = new Font[3];
        try {
            for (int i = 0; i < 3; i++) {
                try (java.io.InputStream in = Cute.class.getResourceAsStream("fonts/" + files[i])) {
                    if (in == null) return null;
                    out[i] = Font.createFont(Font.TRUETYPE_FONT, in);
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(out[i]);
                }
            }
            return out;
        } catch (Exception e) {
            System.out.println("Couldn't load the app's font, using the system's: " + e);
            return null;
        }
    }

    static Font font(int style, float size) {
        if (BUNDLED != null) {
            // plain text is semibold, bold is bold (headings and titles use the extra bold at bigger sizes)
            Font f = style == Font.PLAIN ? BUNDLED[0] : size >= 18 ? BUNDLED[2] : BUNDLED[1];
            return f.deriveFont(Font.PLAIN, (float) Math.round(size));
        }
        if (style == Font.PLAIN) {
            // plain text is drawn semibold (or bold where there's no semibold font)
            return SEMIBOLD.isEmpty() || SEMIBOLD.equals("SansSerif")
                    ? new Font(FONT, Font.BOLD, Math.round(size))
                    : new Font(SEMIBOLD, Font.PLAIN, Math.round(size));
        }
        return new Font(FONT, style, Math.round(size));
    }

    private static String pickFont(String... names) {
        java.util.List<String> have = java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String n : names) if (have.contains(n)) return n;
        return "SansSerif";
    }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    // ------------------------------------------------------------------ the bag mascot

    /**
     * Draws a little loot bag. With a face it becomes the mascot.
     */
    static void drawBag(Graphics2D g, double x, double y, double size, Color fill, boolean face, boolean sparkles) {
        Graphics2D g2 = (Graphics2D) g.create();
        smooth(g2);
        double s = size;
        Color edge = darker(fill, 0.22);
        if (fill.getRed() > 230 && fill.getGreen() > 230 && fill.getBlue() > 230) edge = new Color(0xD9C9D3);
        float stroke = (float) Math.max(1.2, s / 22);

        // body
        GeneralPath body = new GeneralPath();
        body.moveTo(x + s * 0.36, y + s * 0.34);
        body.curveTo(x + s * 0.08, y + s * 0.46, x + s * 0.04, y + s * 0.95, x + s * 0.5, y + s * 0.95);
        body.curveTo(x + s * 0.96, y + s * 0.95, x + s * 0.92, y + s * 0.46, x + s * 0.64, y + s * 0.34);
        body.closePath();
        g2.setColor(fill);
        g2.fill(body);
        // soft shine
        g2.setColor(new Color(255, 255, 255, 120));
        g2.fill(new Ellipse2D.Double(x + s * 0.22, y + s * 0.5, s * 0.14, s * 0.22));
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(edge);
        g2.draw(body);

        // ruffled top
        GeneralPath top = new GeneralPath();
        top.moveTo(x + s * 0.36, y + s * 0.33);
        top.curveTo(x + s * 0.22, y + s * 0.18, x + s * 0.34, y + s * 0.08, x + s * 0.42, y + s * 0.2);
        top.curveTo(x + s * 0.46, y + s * 0.06, x + s * 0.56, y + s * 0.06, x + s * 0.58, y + s * 0.2);
        top.curveTo(x + s * 0.66, y + s * 0.08, x + s * 0.78, y + s * 0.18, x + s * 0.64, y + s * 0.33);
        top.closePath();
        g2.setColor(fill);
        g2.fill(top);
        g2.setColor(edge);
        g2.draw(top);

        // ribbon
        g2.setColor(PINK);
        g2.setStroke(new BasicStroke((float) (s / 14), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine((int) (x + s * 0.37), (int) (y + s * 0.34), (int) (x + s * 0.63), (int) (y + s * 0.34));
        g2.fill(new Ellipse2D.Double(x + s * 0.44, y + s * 0.3, s * 0.12, s * 0.09));

        if (face) {
            double ey = y + s * 0.62;
            g2.setColor(TEXT);
            g2.fill(new Ellipse2D.Double(x + s * 0.34, ey, s * 0.07, s * 0.09));
            g2.fill(new Ellipse2D.Double(x + s * 0.59, ey, s * 0.07, s * 0.09));
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(x + s * 0.355, ey + s * 0.012, s * 0.025, s * 0.03));
            g2.fill(new Ellipse2D.Double(x + s * 0.605, ey + s * 0.012, s * 0.025, s * 0.03));
            g2.setColor(new Color(BLUSH.getRed(), BLUSH.getGreen(), BLUSH.getBlue(), 170));
            g2.fill(new Ellipse2D.Double(x + s * 0.24, ey + s * 0.1, s * 0.12, s * 0.07));
            g2.fill(new Ellipse2D.Double(x + s * 0.64, ey + s * 0.1, s * 0.12, s * 0.07));
            g2.setColor(TEXT);
            g2.setStroke(new BasicStroke((float) Math.max(1.2, s / 40), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            GeneralPath mouth = new GeneralPath();
            mouth.moveTo(x + s * 0.45, ey + s * 0.12);
            mouth.quadTo(x + s * 0.475, ey + s * 0.16, x + s * 0.5, ey + s * 0.12);
            mouth.quadTo(x + s * 0.525, ey + s * 0.16, x + s * 0.55, ey + s * 0.12);
            g2.draw(mouth);
        }
        if (sparkles) {
            drawSparkle(g2, x + s * 0.92, y + s * 0.2, s * 0.13, PEACH_DARK);
            drawSparkle(g2, x + s * 0.06, y + s * 0.36, s * 0.09, LAVENDER_DARK);
            drawSparkle(g2, x + s * 0.98, y + s * 0.62, s * 0.07, PINK);
        }
        g2.dispose();
    }

    static final Color EAR = new Color(0xD9A57E);
    static final Color EAR_EDGE = new Color(0xB98560);
    static final Color TAG = new Color(0xFFCF5C);

    /**
     * The puppy mascot: the cute bag with floppy ears, a button nose and a collar with a heart tag.
     */
    static void drawPuppy(Graphics2D g, double x, double y, double s, Color fill, boolean sparkles) {
        drawBag(g, x, y, s, fill, true, sparkles);
        Graphics2D g2 = (Graphics2D) g.create();
        smooth(g2);
        float stroke = (float) Math.max(1.2, s / 22);

        // collar over the ribbon, with a little heart tag
        g2.setColor(PINK_DARK);
        g2.setStroke(new BasicStroke((float) (s / 11), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        GeneralPath collar = new GeneralPath();
        collar.moveTo(x + s * 0.35, y + s * 0.355);
        collar.quadTo(x + s * 0.5, y + s * 0.41, x + s * 0.65, y + s * 0.355);
        g2.draw(collar);
        drawHeart(g2, x + s * 0.455, y + s * 0.395, s * 0.09, TAG);

        // floppy ears
        for (int side = 0; side < 2; side++) {
            GeneralPath ear = new GeneralPath();
            double[][] pts = {
                    {0.38, 0.36},
                    {0.20, 0.33}, {0.07, 0.50}, {0.11, 0.68},
                    {0.15, 0.79}, {0.29, 0.75}, {0.31, 0.60},
                    {0.32, 0.50}, {0.35, 0.43}, {0.38, 0.36}};
            double[][] p = new double[pts.length][2];
            for (int i = 0; i < pts.length; i++) {
                p[i][0] = x + s * (side == 0 ? pts[i][0] : 1 - pts[i][0]);
                p[i][1] = y + s * pts[i][1];
            }
            ear.moveTo(p[0][0], p[0][1]);
            ear.curveTo(p[1][0], p[1][1], p[2][0], p[2][1], p[3][0], p[3][1]);
            ear.curveTo(p[4][0], p[4][1], p[5][0], p[5][1], p[6][0], p[6][1]);
            ear.curveTo(p[7][0], p[7][1], p[8][0], p[8][1], p[9][0], p[9][1]);
            ear.closePath();
            g2.setColor(EAR);
            g2.fill(ear);
            g2.setColor(EAR_EDGE);
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(ear);
            // pink inside
            double cx = x + s * (side == 0 ? 0.205 : 0.795), cy = y + s * 0.585;
            Graphics2D gi = (Graphics2D) g2.create();
            gi.rotate(Math.toRadians(side == 0 ? 18 : -18), cx, cy);
            gi.setColor(new Color(0xFFC4D3));
            gi.fill(new Ellipse2D.Double(cx - s * 0.04, cy - s * 0.1, s * 0.08, s * 0.2));
            gi.dispose();
        }

        // button nose
        double ey = y + s * 0.62;
        g2.setColor(TEXT);
        g2.fill(new Ellipse2D.Double(x + s * 0.47, ey + s * 0.075, s * 0.06, s * 0.042));
        g2.dispose();
    }

    static void drawSparkle(Graphics2D g, double cx, double cy, double r, Color c) {
        GeneralPath p = new GeneralPath();
        p.moveTo(cx, cy - r);
        p.quadTo(cx, cy, cx + r, cy);
        p.quadTo(cx, cy, cx, cy + r);
        p.quadTo(cx, cy, cx - r, cy);
        p.quadTo(cx, cy, cx, cy - r);
        g.setColor(c);
        g.fill(p);
    }

    /** Little paw print: one big pad and four toe beans. (cx, cy) is the centre of the big pad. */
    static void drawPaw(Graphics2D g, double cx, double cy, double s, Color c) {
        g.setColor(c);
        g.fill(new Ellipse2D.Double(cx - s * 0.5, cy - s * 0.32, s, s * 0.78));
        g.fill(new Ellipse2D.Double(cx - s * 0.78, cy - s * 0.86, s * 0.36, s * 0.44));
        g.fill(new Ellipse2D.Double(cx - s * 0.42, cy - s * 1.18, s * 0.36, s * 0.44));
        g.fill(new Ellipse2D.Double(cx + s * 0.06, cy - s * 1.18, s * 0.36, s * 0.44));
        g.fill(new Ellipse2D.Double(cx + s * 0.42, cy - s * 0.86, s * 0.36, s * 0.44));
    }

    static void drawHeart(Graphics2D g, double x, double y, double s, Color c) {
        GeneralPath p = new GeneralPath();
        p.moveTo(x + s * 0.5, y + s * 0.9);
        p.curveTo(x - s * 0.1, y + s * 0.45, x + s * 0.15, y - s * 0.05, x + s * 0.5, y + s * 0.25);
        p.curveTo(x + s * 0.85, y - s * 0.05, x + s * 1.1, y + s * 0.45, x + s * 0.5, y + s * 0.9);
        g.setColor(c);
        g.fill(p);
    }

    static Image windowIcon(int size) {
        // the pixel pup's head and ears, cropped square
        BufferedImage f = PixelPup.frame(PixelPup.Face.OPEN, PixelPup.Ears.UP, PixelPup.Tail.DOWN, PixelPup.Hat.NONE);
        BufferedImage head = f.getSubimage(0, 7, PixelPup.W, PixelPup.W - 2);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(head, 0, 0, size, size, null);
        g.dispose();
        return img;
    }

    /** A little pixel cat face (like the cat emoji) for the window and taskbar. */
    private static final String[] CAT = {
            "................",
            ".OO..........OO.",
            ".OYO........OYO.",
            ".OPYO......OYPO.",
            ".OPPYOOOOOOYPPO.",
            ".OYYYYYYYYYYYYO.",
            "OYYYYYYYYYYYYYYO",
            "OYYKKYYYYYYKKYYO",
            "OYYKWYYYYYYKWYYO",
            "OYYKKYYYYYYKKYYO",
            "OYYYYYYNNYYYYYYO",
            "OYPYYYOYYOYYYPYO",
            "OYYYYYYOOYYYYYYO",
            ".OYYYYYYYYYYYYO.",
            "..OOYYYYYYYYOO..",
            "....OOOOOOOO...."
    };

    static Image catIcon(int size) {
        BufferedImage cat = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int argb;
                switch (CAT[y].charAt(x)) {
                    case 'O': argb = 0xFF5A3A1E; break; // outline
                    case 'Y': argb = 0xFFFFC83D; break; // golden fur
                    case 'P': argb = 0xFFFF9EBB; break; // pink ears and cheeks
                    case 'K': argb = 0xFF2B1B12; break; // eyes
                    case 'W': argb = 0xFFFFFFFF; break; // eye shine
                    case 'N': argb = 0xFFF0789C; break; // nose
                    default: argb = 0;
                }
                cat.setRGB(x, y, argb);
            }
        }
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(cat, 0, 0, size, size, null);
        g.dispose();
        return img;
    }

    static Color darker(Color c, double amount) {
        return new Color((int) (c.getRed() * (1 - amount)), (int) (c.getGreen() * (1 - amount)), (int) (c.getBlue() * (1 - amount)));
    }

    static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ------------------------------------------------------------------ components

    /**
     * Header with the pixel pup, the title and a subtitle. The pup's mood follows the app:
     * asleep when not watching, alert with a wagging tail while watching, sad after a long white bag
     * drought, a happy hop when a bag drops, and a heart when you click (pet) it.
     */
    static class Header extends JComponent {
        static final int SCALE = 4;
        private static final int PUP_W = PixelPup.W * SCALE, PUP_H = PixelPup.H * SCALE;
        private static final long CELEBRATE_MS = 1700, PET_MS = 1300;
        /** No white bag for this long while watching makes the pup sad. */
        static final long DROUGHT_SECONDS = 2 * 86_400L;
        /** Swappable clock for tests. */
        static java.util.function.LongSupplier clock = System::currentTimeMillis;

        private boolean watching;
        private long lastWhiteSec;
        private long celebrateStart = -1, petStart = -1;
        private Runnable onPet = () -> { };
        private final Timer timer;
        private final double[][] hearts = new double[6][3]; // x offset, delay, drift

        Header() {
            setPreferredSize(new Dimension(560, PUP_H + 4));
            timer = new Timer(90, e -> repaint());
            timer.start();
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (overPup(e.getPoint())) pet();
                }
            });
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseMoved(java.awt.event.MouseEvent e) {
                    setCursor(overPup(e.getPoint()) ? PixelPup.pawCursor() : Cursor.getDefaultCursor());
                }
            });
            setToolTipText(null);
        }

        private boolean overPup(Point p) {
            return p.x < PUP_W && p.y > PUP_H * 0.25;
        }

        void setWatching(boolean w) {
            watching = w;
        }

        /** When the last white bag dropped (Unix seconds), or 0 if never. */
        void setLastWhite(long unixSeconds) {
            lastWhiteSec = unixSeconds;
        }

        void setOnPet(Runnable r) {
            onPet = r;
        }

        boolean isSad() {
            return watching && lastWhiteSec > 0 && clock.getAsLong() / 1000 - lastWhiteSec >= DROUGHT_SECONDS;
        }

        void pet() {
            petStart = clock.getAsLong();
            onPet.run();
        }

        /** The pup hops while little pixel hearts float up. */
        void celebrate() {
            java.util.Random r = new java.util.Random();
            for (double[] h : hearts) {
                h[0] = -30 + r.nextDouble() * 60;
                h[1] = r.nextDouble() * 0.35;
                h[2] = -10 + r.nextDouble() * 20;
            }
            celebrateStart = clock.getAsLong();
        }

        /** Which sprite to show right now. */
        BufferedImage currentFrame() {
            long now = clock.getAsLong();
            PixelPup.Hat hat = PixelPup.seasonalHat(java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate());
            boolean wagFast = (now / 160) % 2 == 0, wagSlow = (now / 380) % 2 == 0;
            if (celebrateStart >= 0 && now - celebrateStart < CELEBRATE_MS)
                return PixelPup.frame(PixelPup.Face.HAPPY, PixelPup.Ears.UP, wagFast ? PixelPup.Tail.UP : PixelPup.Tail.SIDE, hat);
            if (petStart >= 0 && now - petStart < PET_MS)
                return PixelPup.frame(PixelPup.Face.HAPPY, PixelPup.Ears.UP, wagFast ? PixelPup.Tail.UP : PixelPup.Tail.SIDE, hat);
            if (!watching)
                return PixelPup.frame(PixelPup.Face.CLOSED, PixelPup.Ears.DOWN, PixelPup.Tail.DOWN, hat);
            if (isSad())
                return PixelPup.frame(PixelPup.Face.SAD, PixelPup.Ears.DOWN, PixelPup.Tail.DOWN, hat);
            boolean blink = now % 4300 < 140;
            return PixelPup.frame(blink ? PixelPup.Face.CLOSED : PixelPup.Face.OPEN, PixelPup.Ears.UP,
                    wagSlow ? PixelPup.Tail.UP : PixelPup.Tail.SIDE, hat);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            long now = clock.getAsLong();
            double t = celebrateStart < 0 ? 1 : Math.min(1, (now - celebrateStart) / (double) CELEBRATE_MS);
            if (t >= 1) celebrateStart = -1;
            int hop = (int) Math.round(-Math.abs(Math.sin(t * Math.PI * 3)) * 14 * (1 - t));
            hop -= hop % SCALE; // move in whole sprite pixels
            int px = 0, py = getHeight() - PUP_H + hop;
            PixelPup.draw(g, currentFrame(), px, py, SCALE);

            // sleepy z's drifting up
            if (!watching && celebrateStart < 0 && (petStart < 0 || now - petStart >= PET_MS)) {
                for (int i = 0; i < 3; i++) {
                    double ph = ((now / 1000.0) + i * 0.66) % 2.0 / 2.0;
                    int zx = px + PUP_W - 2 + (int) (ph * 14), zy = py + PUP_H / 3 + 6 - (int) (ph * 30);
                    Pixel.text(g, "z", zx, zy, 2 + (ph > 0.5 ? 1 : 0), new Color(0x84, 0x77, 0x9A, (int) (255 * (1 - ph))));
                }
            }
            // heart above the head while being petted
            if (petStart >= 0 && now - petStart < PET_MS) {
                double pt = (now - petStart) / (double) PET_MS;
                int hy = py + 4 * SCALE - (int) (pt * 22);
                PixelPup.heart(g, px + PUP_W / 2 - 10, hy, 3, Pixel.PINK);
            } else {
                petStart = -1;
            }
            // celebration hearts
            if (celebrateStart >= 0) {
                for (double[] h : hearts) {
                    double ht = (t - h[1]) / (1 - h[1]);
                    if (ht <= 0 || ht >= 1) continue;
                    int hx = (int) (px + PUP_W / 2.0 + h[0] + h[2] * ht);
                    int hy = (int) (py + PUP_H * 0.45 - ht * 70);
                    PixelPup.heart(g, hx, hy, 2, Pixel.PINK);
                }
            }

            int tx = PUP_W + 18;
            Pixel.text(g, "white bag pup", tx, getHeight() - 88, 4, Pixel.INK);
            smooth(g);
            g.setColor(Color.BLACK); // black so it stands out over the background picture
            g.setFont(font(Font.PLAIN, 14));
            g.drawString(watching ? (isSad() ? "Still looking for a white bag..." : "Watching for white bags.")
                    : "Asleep. Press Start watching to wake the pup.", tx + 1, getHeight() - 32);
            g.dispose();
        }
    }

    /** Pastel version of each bag's colour for the chips. */
    static Color chipColor(BagType t) {
        switch (t) {
            case WHITE:
            case BOOSTED_WHITE: return new Color(0xFFFDFE);
            case RED:
            case BOOSTED_RED: return new Color(0xFF8A8A);
            case ORANGE:
            case BOOSTED_ORANGE: return new Color(0xFFB570);
            case GOLD:
            case BOOSTED_GOLD: return new Color(0xFFE071);
            case EGG:
            case BOOSTED_EGG: return new Color(0xFFD1E1);
            case BLUE:
            case BOOSTED_BLUE: return new Color(0x8EC5FF);
            default: return Color.WHITE;
        }
    }
}
