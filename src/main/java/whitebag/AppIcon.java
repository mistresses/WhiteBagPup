package whitebag;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The app's icon (window title bar and taskbar): a few pixel-art presets to pick from on Home. Kept as "app.icon" in
 * the settings.
 */
final class AppIcon {
    private AppIcon() {
    }

    enum Preset {
        CAT("Golden cat"), PUP("Your pup"), PAW("Pink paw"),
        HEART("Heart"), BONE("Bone"), STAR("Shiny star");
        final String label;

        Preset(String label) {
            this.label = label;
        }
    }

    static Preset of(String name) {
        try {
            return Preset.valueOf(name == null ? "CAT" : name.trim());
        } catch (IllegalArgumentException e) {
            return Preset.CAT;
        }
    }

    private static final Map<Character, Integer> PAL = new HashMap<>();

    static {
        PAL.put('O', 0xFF3A2B4E); // outline
        PAL.put('W', 0xFFFFFFFF); // white
        PAL.put('G', 0xFFD9CFE6); // white shade
        PAL.put('P', 0xFFFF8DBA); // pink
        PAL.put('Q', 0xFFE0609A); // pink shade
        PAL.put('Y', 0xFFFFCF4D); // gold
        PAL.put('D', 0xFFD69A1F); // gold shade
        PAL.put('R', 0xFFFF6FA8); // ribbon
    }

    private static final String[] PAW = {
            "................",
            "....OO....OO....",
            "...OPPO..OPPO...",
            "...OPPO..OPPO...",
            ".OO.OO....OO.OO.",
            "OPPO........OPPO",
            "OPPO..OOOO..OPPO",
            ".OO..OPPPPO..OO.",
            "....OPPPPPPO....",
            "...OPPPPPPPPO...",
            "...OPPPPPPPQO...",
            "...OPPPPPPQQO...",
            "....OPPOOPQO....",
            ".....OO..OO.....",
            "................",
            "................",
    };
    private static final String[] HEART = {
            "................",
            "................",
            "..OOOO....OOOO..",
            ".OPPPPO..OPPPPO.",
            "OPPWWPPOOPPPPPPO",
            "OPWWPPPPPPPPPPPO",
            "OPWPPPPPPPPPPPPO",
            "OPPPPPPPPPPPPPQO",
            ".OPPPPPPPPPPPQO.",
            "..OPPPPPPPPPQO..",
            "...OPPPPPPPQO...",
            "....OPPPPPQO....",
            ".....OPPPQO.....",
            "......OPQO......",
            ".......OO.......",
            "................",
    };
    private static final String[] BONE = {
            "................",
            "................",
            "................",
            "..OO........OO..",
            ".OWWO......OWWO.",
            "OWWWWOOOOOOWWWWO",
            "OWWWWWWWWWWWWWGO",
            ".OWWWWWWWWWWWGO.",
            "OWWWWWWWWWWWWWGO",
            "OWWGGOOOOOOWWGGO",
            ".OGGO......OGGO.",
            "..OO........OO..",
            "................",
            "................",
            "................",
            "................",
    };
    private static final String[] STAR = {
            ".......OO.......",
            "......OYYO......",
            "......OYYO......",
            ".....OYYYYO.....",
            "OOOOOOYWYYOOOOOO",
            "OYYYYYWYYYYYYYYO",
            ".OYYYYYYYYYYYYO.",
            "..OYYYYYYYYYDO..",
            "...OYYYYYYYYO...",
            "...OYYYYYYYDO...",
            "..OYYYYOOYYYDO..",
            "..OYYYO..OYYDO..",
            ".OYYYO....OYYDO.",
            ".OYYO......OYDO.",
            ".OOO........OOO.",
            "................",
    };

    private static BufferedImage sprite(String[] rows) {
        BufferedImage img = new BufferedImage(rows[0].length(), rows.length, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < rows[y].length(); x++) {
                Integer c = PAL.get(rows[y].charAt(x));
                img.setRGB(x, y, c == null ? 0 : c);
            }
        return img;
    }

    /** The preset's pixel art at its own size (16 x 16, or the pup's frame), on a transparent background. */
    static BufferedImage art(Preset p) {
        switch (p) {
            case PUP: {
                BufferedImage f = PixelPup.frame(PixelPup.Face.HAPPY, PixelPup.Ears.UP, PixelPup.Tail.UP,
                        PixelPup.hatFor(java.time.LocalDate.now()), PixelPup.outfitNow());
                // trim the empty edges, then put it in a square (the pup is taller than wide)
                int x0 = f.getWidth(), y0 = f.getHeight(), x1 = -1, y1 = -1;
                for (int y = 0; y < f.getHeight(); y++)
                    for (int x = 0; x < f.getWidth(); x++)
                        if ((f.getRGB(x, y) >>> 24) != 0) {
                            x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
                        }
                if (x1 < 0) return sprite(PAW);
                int w = x1 - x0 + 1, h = y1 - y0 + 1, s = Math.max(w, h);
                BufferedImage sq = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = sq.createGraphics();
                g.drawImage(f.getSubimage(x0, y0, w, h), (s - w) / 2, s - h, null);
                g.dispose();
                return sq;
            }
            case PAW: return sprite(PAW);
            case HEART: return sprite(HEART);
            case BONE: return sprite(BONE);
            case STAR: return sprite(STAR);
            default: return null; // the cat is Cute.catIcon
        }
    }

    /** The preset drawn at this size with crisp pixels. */
    static Image at(Preset p, int size) {
        if (p == Preset.CAT) return Cute.catIcon(size);
        BufferedImage a = art(p);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int scale = size / a.getWidth();
        if (scale >= 2 || a.getWidth() == size) { // whole pixels, centred
            int w = a.getWidth() * scale;
            g.drawImage(a, (size - w) / 2, (size - w) / 2, w, w, null);
        } else {
            g.drawImage(a, 0, 0, size, size, null); // otherwise fill the square
        }
        g.dispose();
        return img;
    }

    /** Every size the window and taskbar might ask for. */
    static List<Image> images(Preset p) {
        List<Image> out = new ArrayList<>();
        for (int s : new int[]{16, 32, 48, 64, 128, 256}) out.add(at(p, s));
        return out;
    }

    /** A row of the presets to click, with the chosen one marked. */
    static JComponent picker(Preset chosen, Consumer<Preset> pick) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        Preset[] now = {chosen};
        List<Tile> tiles = new ArrayList<>();
        for (Preset p : Preset.values()) {
            Tile t = new Tile(p, () -> now[0] == p);
            t.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    now[0] = p;
                    pick.accept(p);
                    for (Tile x : tiles) x.repaint();
                }
            });
            tiles.add(t);
            row.add(t);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static final class Tile extends JComponent {
        private final Preset p;
        private final java.util.function.BooleanSupplier on;
        private boolean over;

        Tile(Preset p, java.util.function.BooleanSupplier on) {
            this.p = p;
            this.on = on;
            setPreferredSize(new Dimension(46, 46));
            setToolTipText(p.label + (p == Preset.PUP ? " (dressed like it is on the Wardrobe page)" : ""));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    over = true;
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    over = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            boolean sel = on.getAsBoolean();
            Pixel.frame(g, 0, 0, getWidth(), getHeight(), sel ? Pixel.PINK_SOFT : over ? Theme.c(0xF7F3FB) : Pixel.SHEET,
                    sel ? Pixel.PINK : over ? Pixel.INK : Pixel.LINE, sel ? 3 : 2);
            g.drawImage(at(p, 32), (getWidth() - 32) / 2, (getHeight() - 32) / 2, null);
            g.dispose();
        }
    }
}
