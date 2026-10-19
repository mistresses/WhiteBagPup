package whitebag;

import assets.ImageBuffer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the pictures attached to a Discord post: the bag's own sprite, and a cute strip of the
 * bag's items with rarity badges. Sprites come from the player's game install via Puppy.
 */
public final class LootImage {

    /** Where sprites come from; swappable for tests. */
    public interface SpriteSource {
        BufferedImage sprite(int objectType);
    }

    /**
     * Set once the game's sprite sheets have been extracted. Puppy's sprite loader reads its files
     * only once, so touching it earlier (e.g. a bag dropping during the first-run extraction) would
     * leave it empty until restart.
     */
    static volatile boolean gameSpritesReady = false;

    static SpriteSource sprites = id -> {
        if (!gameSpritesReady) return null;
        try {
            return ImageBuffer.getImage(id);
        } catch (Throwable e) {
            return null;
        }
    };

    private LootImage() {
    }

    /**
     * Bag sprite for the embed thumbnail, or null if the sprite isn't available.
     */
    public static byte[] bagPng(int bagObjectType) {
        BufferedImage icon = pixelIcon(sprites.sprite(bagObjectType), 96); // plain bag, no puppy ears
        if (icon == null) return null;
        BufferedImage out = new BufferedImage(112, 112, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        Cute.smooth(g);
        g.drawImage(icon, (112 - icon.getWidth()) / 2, (112 - icon.getHeight()) / 2 + 2, null);
        Cute.drawPaw(g, 100, 102, 8, new Color(0xFF9EBB));
        Cute.drawHeart(g, 96, 8, 13, new Color(0xFF9EBB));
        g.dispose();
        return png(out);
    }

    /**
     * Small square bag sprite (no extras) for the embed's author line, where Discord shows it as a
     * little round icon next to the bag's name. Null if the sprite isn't available.
     */
    public static byte[] bagIconPng(int bagObjectType) {
        BufferedImage icon = pixelIcon(sprites.sprite(bagObjectType), 44);
        if (icon == null) return null;
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(icon, (64 - icon.getWidth()) / 2, (64 - icon.getHeight()) / 2, null);
        g.dispose();
        return png(out);
    }

    // Floppy pixel ears, drawn on the sprite's own pixel grid so they match the game art.
    private static final String[] EAR = {
            ".ob",
            "bpb",
            "bpb",
            "bb.",
            "b..",
    };
    private static final int EAR_BROWN = 0xFFC99A74;
    private static final int EAR_PINK = 0xFFFFB3C7;

    /**
     * Returns a copy of the sprite with puppy ears hanging off the top corners of its shape.
     */
    static BufferedImage withPuppyEars(BufferedImage src) {
        if (src == null || isBlank(src)) return src;
        int minX = src.getWidth(), maxX = -1, minY = src.getHeight();
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++)
                if ((src.getRGB(x, y) >>> 24) > 0) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                }
        int earW = EAR[0].length(), m = earW;
        BufferedImage out = new BufferedImage(src.getWidth() + m * 2, src.getHeight() + 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, m, 1, null);
        g.dispose();
        int top = minY + 1 + 1;  // just below the bag's tied top
        int left = m + minX - earW + 1, right = m + maxX;
        for (int y = 0; y < EAR.length; y++) {
            for (int x = 0; x < earW; x++) {
                char c = EAR[y].charAt(x);
                // 'o' only fills where the bag has no pixel, so the ear tucks behind it
                put(out, left + x, top + y, c, true);
                put(out, right + (earW - 1 - x), top + y, c, true);
            }
        }
        return out;
    }

    private static void put(BufferedImage img, int x, int y, char c, boolean behind) {
        if (!inside(img, x, y) || c == '.') return;
        if (c == 'o' && (img.getRGB(x, y) >>> 24) > 0) return;
        img.setRGB(x, y, c == 'p' ? EAR_PINK : EAR_BROWN);
    }

    /**
     * Strip of item slots with rarity badges, or null if no item sprites are available.
     */
    public static byte[] lootPng(List<ItemInfo> items) {
        if (items.isEmpty()) return null;
        List<BufferedImage> icons = new ArrayList<>();
        boolean any = false;
        for (ItemInfo i : items) {
            BufferedImage icon = pixelIcon(sprites.sprite(i.id), SLOT - 30);
            icons.add(icon);
            any |= icon != null;
        }
        if (!any) return null;

        int cols = Math.min(PER_ROW, items.size());
        int rows = (items.size() + PER_ROW - 1) / PER_ROW;
        int w = PAD * 2 + cols * SLOT + (cols - 1) * GAP;
        int h = PAD * 2 + rows * SLOT + (rows - 1) * GAP;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        Cute.smooth(g);

        // card
        g.setColor(Cute.BG);
        g.fill(new RoundRectangle2D.Double(0, 0, w, h, 28, 28));
        g.setColor(Cute.CARD_EDGE);
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 28, 28));

        Cute.drawPaw(g, w - 9, 13, 5, new Color(0xFFC2D4));
        Cute.drawPaw(g, 8, h - 6, 5, new Color(0xFFC2D4));

        for (int n = 0; n < items.size(); n++) {
            ItemInfo item = items.get(n);
            int x = PAD + (n % PER_ROW) * (SLOT + GAP);
            int y = PAD + (n / PER_ROW) * (SLOT + GAP);
            drawSlot(g, x, y, item, icons.get(n));
        }
        g.dispose();
        return png(out);
    }

    private static final int SLOT = 88;
    private static final int GAP = 16;
    private static final int PAD = 20;
    private static final int PER_ROW = 4;

    private static void drawSlot(Graphics2D g, int x, int y, ItemInfo item, BufferedImage icon) {
        Color accent = item.rarityColor;
        Enchant ench = item.enchant;
        if (ench != Enchant.NONE) {
            // the game gives enchanted items a glow in their rarity's colour
            for (int r = 7; r >= 1; r -= 2) {
                g.setColor(new Color(ench.color.getRed(), ench.color.getGreen(), ench.color.getBlue(), 34));
                g.fill(new RoundRectangle2D.Double(x - r, y - r, SLOT + 2 * r, SLOT + 2 * r, 22 + 2 * r, 22 + 2 * r));
            }
        }
        // slot
        g.setColor(Cute.SHADOW);
        g.fill(new RoundRectangle2D.Double(x, y + 3, SLOT, SLOT, 22, 22));
        g.setColor(Cute.mix(Color.WHITE, accent, 0.16));
        g.fill(new RoundRectangle2D.Double(x, y, SLOT, SLOT, 22, 22));
        g.setColor(ench != Enchant.NONE ? ench.color : accent);
        g.setStroke(new BasicStroke(3f));
        g.draw(new RoundRectangle2D.Double(x + 1.5, y + 1.5, SLOT - 3, SLOT - 3, 20, 20));
        Cute.drawPaw(g, x + 13, y + SLOT - 11, 7, Cute.mix(accent, Color.WHITE, 0.45));
        // shine
        g.setColor(new Color(255, 255, 255, 150));
        g.fill(new RoundRectangle2D.Double(x + 8, y + 7, SLOT - 34, 7, 7, 7));

        if (icon != null) {
            g.drawImage(icon, x + (SLOT - icon.getWidth()) / 2, y + (SLOT - icon.getHeight()) / 2 - 2, null);
        } else {
            g.setColor(Cute.MUTED);
            g.setFont(Cute.font(Font.BOLD, 30));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("?", x + (SLOT - fm.stringWidth("?")) / 2, y + (SLOT - fm.getHeight()) / 2 + fm.getAscent());
        }

        if (ench != Enchant.NONE) {
            // rarity name tag on the top edge
            g.setFont(Cute.font(Font.BOLD, 11));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(ench.label) + 12, th = 18;
            int tx = x - 4, ty = y - 7;
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Double(tx - 2, ty - 2, tw + 4, th + 4, th + 4, th + 4));
            g.setColor(ench.color);
            g.fill(new RoundRectangle2D.Double(tx, ty, tw, th, th, th));
            g.setColor(Color.WHITE);
            g.drawString(ench.label, tx + 6, ty + (th - fm.getHeight()) / 2 + fm.getAscent());
        }

        if (!item.rarity.isEmpty()) {
            g.setFont(Cute.font(Font.BOLD, 13));
            FontMetrics fm = g.getFontMetrics();
            int bw = fm.stringWidth(item.rarity) + 16, bh = 22;
            int bx = x + SLOT - bw + 6, by = y + SLOT - bh + 6;
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Double(bx - 2, by - 2, bw + 4, bh + 4, bh + 4, bh + 4));
            g.setColor(accent);
            g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, bh, bh));
            g.setColor(Color.WHITE);
            g.drawString(item.rarity, bx + 8, by + (bh - fm.getHeight()) / 2 + fm.getAscent());
            if (item.rarity.equals("UT") || item.rarity.equals("ST") || item.rarityColor == ItemInfo.Rarity.LEGENDARY) {
                Cute.drawHeart(g, x + SLOT - 17, y + 7, 11, Cute.mix(accent, Color.WHITE, 0.15));
            }
        }
    }

    /**
     * Game-style icon: 1px black outline at native size, then scaled up with crisp pixels.
     */
    static BufferedImage pixelIcon(BufferedImage src, int box) {
        if (src == null || isBlank(src)) return null;
        int w = src.getWidth() + 2, h = src.getHeight() + 2;
        BufferedImage outlined = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sx = x - 1, sy = y - 1;
                int rgb = inside(src, sx, sy) ? src.getRGB(sx, sy) : 0;
                if ((rgb >>> 24) > 0) {
                    outlined.setRGB(x, y, rgb);
                } else if (opaque(src, sx + 1, sy) || opaque(src, sx - 1, sy) || opaque(src, sx, sy + 1) || opaque(src, sx, sy - 1)) {
                    outlined.setRGB(x, y, 0xFF000000);
                }
            }
        }
        int scale = Math.max(1, box / Math.max(w, h));
        BufferedImage out = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(outlined, 0, 0, w * scale, h * scale, null);
        g.dispose();
        return out;
    }

    private static boolean inside(BufferedImage i, int x, int y) {
        return x >= 0 && y >= 0 && x < i.getWidth() && y < i.getHeight();
    }

    private static boolean opaque(BufferedImage i, int x, int y) {
        return inside(i, x, y) && (i.getRGB(x, y) >>> 24) > 0;
    }

    private static boolean isBlank(BufferedImage i) {
        for (int y = 0; y < i.getHeight(); y++)
            for (int x = 0; x < i.getWidth(); x++)
                if ((i.getRGB(x, y) >>> 24) > 0) return false;
        return true;
    }

    private static byte[] png(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
