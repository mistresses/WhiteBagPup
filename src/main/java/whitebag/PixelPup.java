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
    enum Hat {
        NONE("No hat"), WITCH("Witch hat"), SANTA("Santa hat"), PARTY("Party hat"), BOW("Pink bow"), FLOWERS("Flower crown"),
        TOPHAT("Top hat"), VIKING("Viking helmet"), HALO("Halo"), CROWN("Crown");
        final String label;

        Hat(String label) {
            this.label = label;
        }
    }

    enum Outfit {
        NONE("Just the collar"), BOWTIE("Bow tie"), BANDANA("Red bandana"), SCARF("Cosy scarf"), CAPE("Hero cape"),
        GOLD_COLLAR("Gold collar"), ROYAL_CAPE("Royal cape");
        final String label;

        Outfit(String label) {
            this.label = label;
        }
    }

    /** What the pup is wearing (picked on the Wardrobe page). A null hat means the seasonal one. */
    static volatile Hat wornHat = null;
    static volatile Outfit wornOutfit = Outfit.NONE;

    /** The hat to show today: the one picked, or the seasonal one. */
    static Hat hatFor(java.time.LocalDate day) {
        Hat h = wornHat;
        return h != null ? h : seasonalHat(day);
    }

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
        PAL.put('P', 0xFF6EC6FF); // party blue
        PAL.put('G', 0xFFFFC93C); // gold
        PAL.put('D', 0xFFD69A1F); // gold shade
        PAL.put('j', 0xFFE84A6A); // ruby
        PAL.put('J', 0xFF4AA3E8); // sapphire
        PAL.put('m', 0xFFC9D2E0); // steel
        PAL.put('M', 0xFF8E9AB0); // steel shade
        PAL.put('f', 0xFFFF8DBA); // flower / bow pink
        PAL.put('l', 0xFF6CC56B); // leaf green
        PAL.put('c', 0xFFFFF09A); // halo glow
        PAL.put('n', 0xFF2E2A3A); // top hat
        PAL.put('N', 0xFF55506A); // top hat sheen
        PAL.put('x', 0xFFF3E6C8); // horn
        PAL.put('q', 0xFFE5484D); // bandana / cape red
        PAL.put('Q', 0xFFB02E3E); // red shade
        PAL.put('s', 0xFF2FB8A8); // scarf teal
        PAL.put('S', 0xFFFFE08A); // scarf stripe
        PAL.put('u', 0xFF7A3FD0); // royal purple
        PAL.put('U', 0xFF5A2BA0); // royal purple shade
    }

    // ---- hats: 12 wide, drawn over the top of the head (the bottom row sits on the head's outline)
    private static final String[] PARTY = {
            ".....yy.....",
            ".....kk.....",
            "....kPPk....",
            "....kwwk....",
            "...kPPPPk...",
            "...kwwwwk...",
            "..kPPPPPPk..",
            "..kkkkkkkk..",
    };
    private static final String[] BOW = {
            ".kkk....kkk.",
            "kfffk..kfffk",
            "kffffkkffffk",
            "kfffkrrkfffk",
            "kffffkkffffk",
            ".kkk....kkk.",
            "............",
    };
    private static final String[] FLOWERS = {
            "..f...y...f.",
            ".fyf.yfy.fyf",
            "..fl.lyl.lf.",
            ".llllllllll.",
    };
    private static final String[] TOPHAT = {
            "...kkkkkk...",
            "...knnnnk...",
            "...knNnnk...",
            "...knNnnk...",
            "...kqqqqk...",
            "..kkkkkkkk..",
            ".knnnnnnnnk.",
            ".kkkkkkkkkk.",
    };
    private static final String[] VIKING = {
            "x..........x",
            "kx........xk",
            "kxk.kkkk.kxk",
            ".kxkmmmmkxk.",
            "..kmmMmmmk..",
            ".kmmmmmmmmk.",
            ".kGGGGGGGGk.",
            ".kkkkkkkkkk.",
    };
    private static final String[] HALO = {
            "..kkkkkkkk..",
            ".kcccccccck.",
            "..kkkkkkkk..",
            "............",
            "............",
    };
    private static final String[] CROWN = {
            ".k...kk...k.",
            "kGk.kGGk.kGk",
            "kGGkGGGGkGGk",
            "kGjGGJJGGjGk",
            "kDDDDDDDDDDk",
            "kkkkkkkkkkkk",
    };

    // ---- outfits
    /** A cape behind the body (q/Q are swapped for the cape's colours). */
    private static final String[] CAPE = {
            "..kqqqqqqqqqqqqqqk..",
            "..kqqqqqqqqqqqqQQk..",
            "..kqqqqqqqqqqqqqQk..",
            ".kqqqqqqqqqqqqqqQQk.",
            ".kqqqqqqqqqqqqqqqQk.",
            ".kqqqqqqqqqqqqqqqQk.",
            "kqqqqqqqqqqqqqqqqqQk",
            "kkkkkkkkkkkkkkkkkkkk",
    };
    private static final String[] BOWTIE = {
            "kk..kk",
            "kPkkPk",
            "kPPPPk",
            "kPkkPk",
            "kk..kk",
    };
    private static final String[] BANDANA = {
            "kqqqqqqqqqqk",
            ".kqqwqqqwqk.",
            "..kqqqqqqk..",
            "...kqwqqk...",
            "....kqqk....",
            ".....kk.....",
    };
    private static final String[] SCARF = {
            "kssSssSssSsk",
            "kssSssSssSsk",
            ".kkkkkkkskSk",
            "........kssk",
            "........kSsk",
            "........kssk",
            "........kkkk",
    };
    private static final String[] GOLD_COLLAR = {
            "kGGGGjjGGGGk",
            ".....kGGk...",
            ".....kjjk...",
            "......kk....",
    };
    private static final String[] ERMINE = {
            "kwwkwwwwwwkwwk",
            "kwwwwwkwwwwwwk",
            ".kkkkkkkkkkkk.",
    };

    private static String[] recolour(String[] rows, char from, char to, char from2, char to2) {
        String[] out = new String[rows.length];
        for (int i = 0; i < rows.length; i++) out[i] = rows[i].replace(from, to).replace(from2, to2);
        return out;
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
        return frame(face, ears, tail, hat, Outfit.NONE);
    }

    static BufferedImage frame(Face face, Ears ears, Tail tail, Hat hat, Outfit outfit) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        int bx = OX, by = OY + 2;          // top-left of the body layer
        // capes hang behind everything
        if (outfit == Outfit.CAPE) layer(img, CAPE, bx, by + 9);
        if (outfit == Outfit.ROYAL_CAPE) layer(img, recolour(CAPE, 'q', 'u', 'Q', 'U'), bx, by + 9);
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
        // things worn around the neck go over the collar
        switch (outfit) {
            case BOWTIE: layer(img, BOWTIE, bx + 7, by + 9); break;
            case BANDANA: layer(img, BANDANA, bx + 4, by + 9); break;
            case SCARF: layer(img, SCARF, bx + 4, by + 9); break;
            case GOLD_COLLAR: layer(img, GOLD_COLLAR, bx + 4, by + 9); break;
            case CAPE: layer(img, new String[]{"kGGk"}, bx + 8, by + 9); break;
            case ROYAL_CAPE:
                layer(img, ERMINE, bx + 3, by + 9);
                layer(img, new String[]{"kGGk", ".kk."}, bx + 8, by + 11);
                break;
            default:
        }
        String[] h = hatRows(hat);
        if (h != null) layer(img, h, bx + 4, by - h.length + 1);
        return img;
    }

    private static String[] hatRows(Hat hat) {
        if (hat == null) return null;
        switch (hat) {
            case WITCH: return WITCH;
            case SANTA: return SANTA;
            case PARTY: return PARTY;
            case BOW: return BOW;
            case FLOWERS: return FLOWERS;
            case TOPHAT: return TOPHAT;
            case VIKING: return VIKING;
            case HALO: return HALO;
            case CROWN: return CROWN;
            default: return null;
        }
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
