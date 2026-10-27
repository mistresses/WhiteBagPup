package whitebag;

import java.util.EnumMap;
import java.util.Map;

/** Which badge unlocks each of the pup's hats and outfits, and what the pup is wearing. */
final class Wardrobe {
    private Wardrobe() {
    }

    /** Hat -> the badge that unlocks it (none: always there). */
    static final Map<PixelPup.Hat, String> HATS = new EnumMap<>(PixelPup.Hat.class);
    static final Map<PixelPup.Outfit, String> OUTFITS = new EnumMap<>(PixelPup.Outfit.class);

    static {
        HATS.put(PixelPup.Hat.NONE, null);
        HATS.put(PixelPup.Hat.WITCH, null);
        HATS.put(PixelPup.Hat.SANTA, null);
        HATS.put(PixelPup.Hat.PARTY, "runs10");
        HATS.put(PixelPup.Hat.BOW, "bellyrubs");
        HATS.put(PixelPup.Hat.FLOWERS, "walkies");
        HATS.put(PixelPup.Hat.TOPHAT, "fame10k");
        HATS.put(PixelPup.Hat.VIKING, "boss500");
        HATS.put(PixelPup.Hat.HALO, "blessed");
        HATS.put(PixelPup.Hat.CROWN, "dungeonmaster");
        HATS.put(PixelPup.Hat.NIGHTCAP, null);
        OUTFITS.put(PixelPup.Outfit.NONE, null);
        OUTFITS.put(PixelPup.Outfit.BOWTIE, "white10");
        OUTFITS.put(PixelPup.Outfit.BANDANA, "fetch");
        OUTFITS.put(PixelPup.Outfit.SCARF, "loyal");
        OUTFITS.put(PixelPup.Outfit.CAPE, "solo");
        OUTFITS.put(PixelPup.Outfit.GOLD_COLLAR, "white100");
        OUTFITS.put(PixelPup.Outfit.ROYAL_CAPE, "mythic");
        OUTFITS.put(PixelPup.Outfit.PYJAMAS, null);
    }

    static boolean has(PixelPup.Hat h) {
        String b = HATS.get(h);
        return b == null || Badges.anyUnlocked(b);
    }

    static boolean has(PixelPup.Outfit o) {
        String b = OUTFITS.get(o);
        return b == null || Badges.anyUnlocked(b);
    }

    /** "the Crown" / "the Hero cape" this badge unlocks, or null. */
    static String unlockedBy(String badgeId) {
        for (Map.Entry<PixelPup.Hat, String> e : HATS.entrySet()) if (badgeId.equals(e.getValue())) return e.getKey().label;
        for (Map.Entry<PixelPup.Outfit, String> e : OUTFITS.entrySet()) if (badgeId.equals(e.getValue())) return e.getKey().label;
        return null;
    }

    static int total() {
        return HATS.size() + OUTFITS.size();
    }

    static int owned() {
        int n = 0;
        for (PixelPup.Hat h : HATS.keySet()) if (has(h)) n++;
        for (PixelPup.Outfit o : OUTFITS.keySet()) if (has(o)) n++;
        return n;
    }

    /** Saves what the pup wears (set by the main window: key, value). */
    static volatile java.util.function.BiConsumer<String, String> save = (k, v) -> { };

    /** Reads what the pup wears from the settings ("pup.hat" is SEASONAL or a hat; "pup.outfit" an outfit). */
    static void load(java.util.Properties settings) {
        String h = settings.getProperty("pup.hat", "SEASONAL");
        try {
            PixelPup.wornHat = h.equals("SEASONAL") ? null : PixelPup.Hat.valueOf(h);
        } catch (IllegalArgumentException e) {
            PixelPup.wornHat = null;
        }
        try {
            PixelPup.wornOutfit = PixelPup.Outfit.valueOf(settings.getProperty("pup.outfit", "NONE"));
        } catch (IllegalArgumentException e) {
            PixelPup.wornOutfit = PixelPup.Outfit.NONE;
        }
    }

    static void wear(PixelPup.Hat h) {
        PixelPup.wornHat = h;
        save.accept("pup.hat", h == null ? "SEASONAL" : h.name());
    }

    static void wear(PixelPup.Outfit o) {
        PixelPup.wornOutfit = o;
        save.accept("pup.outfit", o.name());
    }
}
