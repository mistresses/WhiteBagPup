package whitebag;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * The mascot as a real pixel-art sprite (like a RotMG pet), built from layers so moods can swap
 * the face, ears and tail, and the season can add a hat.
 */
final class PixelPup {
    private PixelPup() {
    }

    enum Face { OPEN, CLOSED, HAPPY, SAD }
    enum Ears { UP, DOWN }
    enum Tail { UP, SIDE, DOWN }
    enum Hat { NONE, WITCH, SANTA }

    /** Canvas size in sprite pixels. The body sits 8 rows down to leave room for ears and hats. */
    static final int W = 22, H = 28;
    private static final int OX = 1, OY = 8;

    private static final Map<Character, Integer> PAL = new HashMap<>();

    static {
        PAL.put('k', 0xFF3A2B4E); // outline (same plum as the UI ink)
        PAL.put('w', 0xFFFFFFFF); // fur
        PAL.put('g', 0xFFE6DDF0); // fur shade
        PAL.put('b', 0xFFC98F64); // ear
        PAL.put('p', 0xFFFFA9C6); // pink: inner ear, blush, tongue
        PAL.put('r', 0xFFFF6FA8); // collar
        PAL.put('y', 0xFFFFCF5C); // tag
        PAL.put('e', 0xFF3A2B4E); // eye
        PAL.put('h', 0xFFFFFFFF); // eye shine
        PAL.put('t', 0xFF9CCBFF); // tear
        PAL.put('v', 0xFF7A55D9); // witch hat
        PAL.put('V', 0xFF5C3EB5); // witch hat shade
        PAL.put('o', 0xFFFF9A3C); // hat band
        PAL.put('R', 0xFFE04848); // santa red
        PAL.put('W', 0xFFFFFFFF); // santa trim
    }

    // Sitting pup, 20 x 18, drawn at (OX, OY + 2) so rows 0-1 of the body layer are free for perked ears.
    private static final String[] BODY = {
            "......kkkkkkkk......",
            ".....kwwwwwwwwk.....",
            "....kwwwwwwwwwwk....",
            "...kwwwwwwwwwwwgk...",
            "...kwwwwwwwwwwwgk...",
            "...kwwwwwwwwwwwgk...",
            "...kwwwwwwwwwwwgk...",
            "...kwwwwwwwwwwwgk...",
            "....kwwwwwwwwwgk....",
            "....krrrryyrrrrk....",
            "....kwwwwyywwwwk....",
            "...kwwwwwwwwwwwgk...",
            "...kwwwwwwwwwwwgk...",
            "...kwwwwwwwwwwwgk...",
            "...kwwwkwwwwkwwgk...",
            "...kwwwkwwwwkwwgk...",
            "....kkk.kkkk.kkk....",
    };

    private static final String[] EAR_DOWN = {
            ".kkkk",
            "kbbbk",
            "kbpbk",
            "kbpbk",
            "kbpbk",
            "kbbbk",
            ".kbk.",
            "..k..",
    };

    // Raised floppy ear: lifted higher and further out than the resting ear, tip still folded.
    private static final String[] EAR_UP = {
            ".kkkk",
            "kbbbk",
            "kbpbk",
            "kbpbk",
            "kbbk.",
            ".kk..",
    };

    private static final String[] TAIL_UP = {
            "..kk",
            ".kwk",
            "kwk.",
            "kk..",
    };
    private static final String[] TAIL_SIDE = {
            "kkkk",
            "wwwk",
            "kkkk",
    };
    private static final String[] TAIL_DOWN = {
            "k...",
            "wk..",
            "kwk.",
            ".kk.",
    };

    private static final String[] WITCH = {
            ".......kk...",
            "......kvk...",
            ".....kvVk...",
            ".....kvvVk..",
            "....kvvvvVk.",
            "...kooooooko",
            "kvvvvvvvvvVk",
            "kkkkkkkkkkkk",
    };
    private static final String[] SANTA = {
            ".....kkk....",
            "....kWWWk...",
            "....kkRkk...",
            "....kRRRk...",
            "...kRRRRRk..",
            "..kRRRRRRRk.",
            "kkkkkkkkkkkk",
            "kWWWWWWWWWWk",
            "kkkkkkkkkkkk",
    };

    /** Renders one frame at 1 sprite pixel = 1 image pixel. */
    static BufferedImage frame(Face face, Ears ears, Tail tail, Hat hat) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int bx = OX, by = OY + 2;          // top-left of the body layer
        // tail goes behind the body
        if (tail == Tail.UP) layer(img, TAIL_UP, bx + 15, by + 9);
        else if (tail == Tail.SIDE) layer(img, TAIL_SIDE, bx + 15, by + 12);
        else layer(img, TAIL_DOWN, bx + 15, by + 13);
        // perked ears sit behind the head
        layer(img, BODY, bx, by);
        face(img, face, bx, by);
        if (ears == Ears.UP) {
            layer(img, EAR_UP, bx, by - 1);
            layerMirrored(img, EAR_UP, bx + 15, by - 1);
        }
        if (ears == Ears.DOWN) {
            layer(img, EAR_DOWN, bx + 1, by + 1);
            layerMirrored(img, EAR_DOWN, bx + 14, by + 1);
        }
        if (hat == Hat.WITCH) layer(img, WITCH, bx + 4, by - WITCH.length + 1);
        if (hat == Hat.SANTA) layer(img, SANTA, bx + 4, by - SANTA.length + 1);
        return img;
    }

    private static void face(BufferedImage img, Face f, int bx, int by) {
        int ey = by + 4; // eye row
        switch (f) {
            case OPEN:
                // bean eyes, 2 x 3, with a shine in the top-left
                for (int dy = -1; dy <= 1; dy++) {
                    put(img, bx + 6, ey + dy, 'e'); put(img, bx + 7, ey + dy, 'e');
                    put(img, bx + 12, ey + dy, 'e'); put(img, bx + 13, ey + dy, 'e');
                }
                put(img, bx + 6, ey - 1, 'h');
                put(img, bx + 12, ey - 1, 'h');
                smileMouth(img, bx, by);
                break;
            case CLOSED:
                put(img, bx + 6, ey + 1, 'k'); put(img, bx + 7, ey + 1, 'k');
                put(img, bx + 12, ey + 1, 'k'); put(img, bx + 13, ey + 1, 'k');
                put(img, bx + 9, ey + 2, 'k'); put(img, bx + 10, ey + 2, 'k');
                break;
            case HAPPY:
                put(img, bx + 6, ey, 'k'); put(img, bx + 5, ey + 1, 'k'); put(img, bx + 7, ey + 1, 'k');
                put(img, bx + 13, ey, 'k'); put(img, bx + 12, ey + 1, 'k'); put(img, bx + 14, ey + 1, 'k');
                put(img, bx + 9, ey + 2, 'k'); put(img, bx + 10, ey + 2, 'k');
                put(img, bx + 8, ey + 3, 'k'); put(img, bx + 9, ey + 3, 'p'); put(img, bx + 10, ey + 3, 'p'); put(img, bx + 11, ey + 3, 'k');
                put(img, bx + 9, ey + 4, 'k'); put(img, bx + 10, ey + 4, 'k');
                break;
            case SAD:
                put(img, bx + 7, ey - 1, 'k'); put(img, bx + 6, ey - 2, 'k');   // worried brows, inner ends up
                put(img, bx + 12, ey - 1, 'k'); put(img, bx + 13, ey - 2, 'k');
                put(img, bx + 6, ey + 1, 'e'); put(img, bx + 7, ey + 1, 'e');
                put(img, bx + 12, ey + 1, 'e'); put(img, bx + 13, ey + 1, 'e');
                put(img, bx + 6, ey + 2, 't'); put(img, bx + 6, ey + 3, 't');   // little tear
                put(img, bx + 9, ey + 2, 'k'); put(img, bx + 10, ey + 2, 'k'); // nose
                put(img, bx + 9, ey + 4, 'k'); put(img, bx + 10, ey + 4, 'k'); // small wobbly mouth
                put(img, bx + 8, ey + 4, 'g'); put(img, bx + 11, ey + 4, 'g');
                break;
        }
        if (f != Face.SAD) {
            put(img, bx + 4, ey + 2, 'p'); put(img, bx + 5, ey + 2, 'p');
            put(img, bx + 14, ey + 2, 'p'); put(img, bx + 15, ey + 2, 'p');
        }
    }

    private static void smileMouth(BufferedImage img, int bx, int by) {
        int ey = by + 4;
        put(img, bx + 9, ey + 2, 'k'); put(img, bx + 10, ey + 2, 'k');      // nose
        put(img, bx + 8, ey + 3, 'k'); put(img, bx + 11, ey + 3, 'k');      // smile corners
        put(img, bx + 9, ey + 4, 'k'); put(img, bx + 10, ey + 4, 'k');
    }

    private static void layer(BufferedImage img, String[] rows, int x, int y) {
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length(); c++) put(img, x + c, y + r, rows[r].charAt(c));
    }

    private static void layerMirrored(BufferedImage img, String[] rows, int x, int y) {
        for (int r = 0; r < rows.length; r++) {
            int n = rows[r].length();
            for (int c = 0; c < n; c++) put(img, x + (n - 1 - c), y + r, rows[r].charAt(c));
        }
    }

    private static void put(BufferedImage img, int x, int y, char c) {
        Integer col = PAL.get(c);
        if (col == null || x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return;
        img.setRGB(x, y, col);
    }

    /** Draws a frame scaled up with crisp pixels. */
    static void draw(Graphics2D g, BufferedImage frame, int x, int y, int scale) {
        Object hint = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(frame, x, y, frame.getWidth() * scale, frame.getHeight() * scale, null);
        if (hint != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
    }

    /** The hat for this month: witch hat in October, Santa hat in December. */
    static Hat seasonalHat(java.time.LocalDate day) {
        switch (day.getMonth()) {
            case OCTOBER: return Hat.WITCH;
            case DECEMBER: return Hat.SANTA;
            default: return Hat.NONE;
        }
    }

    /** Pixel heart, 7 x 6 sprite pixels. */
    static void heart(Graphics2D g, int x, int y, int scale, Color c) {
        String[] h = {".kk.kk.", "krrkrrk", "krrrrrk", ".krrrk.", "..krk..", "...k..."};
        for (int r = 0; r < h.length; r++)
            for (int col = 0; col < h[r].length(); col++) {
                char ch = h[r].charAt(col);
                if (ch == '.') continue;
                g.setColor(ch == 'k' ? Pixel.INK : c);
                g.fillRect(x + col * scale, y + r * scale, scale, scale);
            }
    }

    /** 32 x 32 paw cursor image: pink pads with an ink outline. */
    static BufferedImage pawCursorImage() {
        String[] paw = {
                "..kk....kk..",
                ".kppk..kppk.",
                ".kppk..kppk.",
                "kk.kk..kk.kk",
                "kpk..kk..kpk",
                "kpk.kppk.kpk",
                ".k.kppppk.k.",
                "...kppppk...",
                "..kppppppk..",
                "..kppppppk..",
                "...kkkkkk...",
        };
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int r = 0; r < paw.length; r++)
            for (int c = 0; c < paw[r].length(); c++) {
                char ch = paw[r].charAt(c);
                if (ch == '.') continue;
                int col = ch == 'k' ? 0xFF3A2B4E : 0xFFFF8DBA;
                for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) img.setRGB(c * 2 + dx + 2, r * 2 + dy + 2, col);
            }
        return img;
    }

    private static Cursor paw;

    static Cursor pawCursor() {
        if (paw == null) {
            try {
                paw = Toolkit.getDefaultToolkit().createCustomCursor(pawCursorImage(), new Point(10, 10), "paw");
            } catch (Throwable e) {
                paw = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
            }
        }
        return paw;
    }
}
