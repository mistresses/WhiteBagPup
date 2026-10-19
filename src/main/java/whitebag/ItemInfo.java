package whitebag;

import assets.IdToAsset;

import java.awt.Color;
import java.util.Locale;

/**
 * Name and rarity for an item type, read from the game's own asset labels (UT, ST, T12, ...).
 */
public class ItemInfo {
    public final int id;
    public final String name;
    /** Short rarity badge such as "UT", "ST" or "T12"; empty when the item has none. */
    public final String rarity;
    public final Color rarityColor;
    /** Equipment rarity from enchantment slots (Uncommon ... Divine), for one dropped copy of the item. */
    public final Enchant enchant;

    ItemInfo(int id, String name, String rarity, Color rarityColor) {
        this(id, name, rarity, rarityColor, Enchant.NONE);
    }

    ItemInfo(int id, String name, String rarity, Color rarityColor, Enchant enchant) {
        this.id = id;
        this.name = name;
        this.rarity = rarity;
        this.rarityColor = rarityColor;
        this.enchant = enchant == null ? Enchant.NONE : enchant;
    }

    /** This item with the rarity its dropped copy rolled. */
    ItemInfo withEnchant(Enchant e) {
        return e == enchant ? this : new ItemInfo(id, name, rarity, rarityColor, e);
    }

    public static ItemInfo of(int id) {
        String name = null, labels = null;
        try {
            name = IdToAsset.objectName(id);
            labels = IdToAsset.getIdLabel(id);
        } catch (Throwable ignored) {
            // assets not loaded
        }
        if (isKey(name)) {
            // some items only have a label like "{textiles.Small_Exalted_Oryx_Cloth}": try the other names first
            for (int k = 0; k < 2 && isKey(name); k++) {
                try {
                    String alt = k == 0 ? IdToAsset.getDisplayName(id) : IdToAsset.getObjectIdName(id);
                    if (alt != null && !alt.trim().isEmpty() && !isKey(alt)) name = alt.trim();
                } catch (Throwable ignored) {
                }
            }
            if (isKey(name)) name = tidy(name);
        }
        if (name == null || name.isEmpty()) name = "Item #" + id;
        ItemXml.Info x = null;
        try {
            x = ItemXml.of(id);
        } catch (Throwable ignored) {
        }
        // potions have a "tier" in the game files (greater ones T5, small ones T2), but it isn't an item tier
        if (isPotion(name, x)) return new ItemInfo(id, name, "Potion", Rarity.NONE);
        ItemInfo info = fromLabels(id, name, labels);
        if (!info.rarity.isEmpty()) return info;

        // the quick item list only has labels: look the item up in the game files for its tier and kind
        if (x != null) {
            String more = (labels == null ? "" : labels) + "," + x.labels;
            if (x.tier.matches("\\d{1,2}")) more += ",T" + x.tier;
            else if (!x.tier.isEmpty()) more += "," + x.tier;
            if (!x.rarity.isEmpty()) more += "," + x.rarity;
            info = fromLabels(id, name, more);
            if (!info.rarity.isEmpty()) return info;
            if (!x.rarity.isEmpty()) return new ItemInfo(id, name, title(x.rarity), Rarity.TIERED);
        }
        String kind = kind(name, x);
        if (!kind.isEmpty()) return new ItemInfo(id, name, kind, Rarity.NONE);
        ItemXml.noteUnbadged(id, name, labels);
        return info;
    }

    static boolean isPotion(String name, ItemXml.Info x) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return (x != null && x.potion) || n.contains("potion") || n.contains("elixir") || n.contains("tincture") || n.contains("effusion");
    }

    /** A short "what is it" badge for items with no tier: potions, shards, eggs and so on. */
    static String kind(String name, ItemXml.Info x) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if ((x != null && x.potion) || n.contains("potion") || n.contains("elixir") || n.contains("tincture") || n.contains("effusion")) return "Potion";
        if (n.contains("shard")) return "Shard";
        if (n.contains("egg")) return "Egg";
        if (n.endsWith(" key") || n.contains(" key ")) return "Key";
        if (n.contains("skin")) return "Skin";
        if (n.contains("cloth") || n.contains("essence") || n.contains("fragment") || n.contains("token")) return "Material";
        if (n.contains("rune")) return "Rune";
        if (x != null && x.consumable) return "Misc";
        return "";
    }

    private static String title(String s) {
        String t = s.trim();
        return t.isEmpty() ? t : t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1).toLowerCase(Locale.ROOT);
    }

    private static boolean isKey(String n) {
        if (n == null) return false;
        String t = n.trim();
        return t.startsWith("{") && t.endsWith("}");
    }

    /** "{textiles.Small_Exalted_Oryx_Cloth}" becomes "Small Exalted Oryx Cloth". */
    static String tidy(String key) {
        String t = key.trim();
        if (t.startsWith("{") && t.endsWith("}")) t = t.substring(1, t.length() - 1);
        int dot = t.lastIndexOf('.');
        if (dot >= 0 && dot < t.length() - 1) t = t.substring(dot + 1);
        t = t.replace('_', ' ').replaceAll("\\s+", " ").trim();
        return t;
    }

    static ItemInfo fromLabels(int id, String name, String labels) {
        String rarity = "";
        int bestTier = -1;
        boolean ut = false, st = false;
        String special = null;
        if (labels != null) {
            for (String raw : labels.split("[,;\\s]+")) {
                String l = raw.trim().toUpperCase(Locale.ROOT);
                if (l.isEmpty()) continue;
                if (l.equals("UT")) ut = true;
                else if (l.equals("ST")) st = true;
                else if (l.matches("T\\d{1,2}")) bestTier = Math.max(bestTier, Integer.parseInt(l.substring(1)));
                else if (l.equals("LEGENDARY") || l.equals("MYTHIC") || l.equals("DIVINE")) special = l;
            }
        }
        Color color = Rarity.NONE;
        if (special != null) {
            rarity = special.charAt(0) + special.substring(1).toLowerCase(Locale.ROOT);
            color = Rarity.LEGENDARY;
        } else if (st) {
            rarity = "ST";
            color = Rarity.ST;
        } else if (ut) {
            rarity = "UT";
            color = Rarity.UT;
        } else if (bestTier >= 0) {
            rarity = "T" + bestTier;
            color = Rarity.TIERED;
        }
        return new ItemInfo(id, name, rarity, color);
    }

    /** Pastel badge colours. */
    static final class Rarity {
        static final Color UT = new Color(0xB98CFF);
        static final Color ST = new Color(0xFFAA5C);
        static final Color TIERED = new Color(0x7DB7FF);
        static final Color LEGENDARY = new Color(0xFFC94D);
        static final Color NONE = new Color(0xC9BBC3);
    }
}
