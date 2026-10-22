package whitebag;

/**
 * Loot bag object types. IDs match Puppy's LootBags enum.
 */
public enum BagType {
    WHITE(1292, "White", "⚪", 0xF5F5F5, true),
    BOOSTED_WHITE(1296, "Boosted White", "⚪", 0xF5F5F5, true),
    RED(1708, "Red", "🔴", 0xE53935, false),
    BOOSTED_RED(1728, "Boosted Red", "🔴", 0xE53935, false),
    ORANGE(1295, "Orange", "🟠", 0xFB8C00, false),
    BOOSTED_ORANGE(1727, "Boosted Orange", "🟠", 0xFB8C00, false),
    GOLD(1294, "Gold", "🟡", 0xFDD835, false),
    BOOSTED_GOLD(1724, "Boosted Gold", "🟡", 0xFDD835, false),
    EGG(1288, "Egg Basket", "🥚", 0xF8BBD0, false),
    BOOSTED_EGG(1723, "Boosted Egg Basket", "🥚", 0xF8BBD0, false),
    BLUE(1291, "Blue", "🔵", 0x1E88E5, false),
    BOOSTED_BLUE(1726, "Boosted Blue", "🔵", 0x1E88E5, false),
    // the cyan bag (RealmShark calls it "Teal")
    CYAN(1289, "Cyan", "🩵", 0x26C6DA, false),
    BOOSTED_CYAN(1725, "Boosted Cyan", "🩵", 0x26C6DA, false);

    public final int objectType;
    public final String displayName;
    public final String emoji;
    public final int color;
    public final boolean defaultOn;

    BagType(int objectType, String displayName, String emoji, int color, boolean defaultOn) {
        this.objectType = objectType;
        this.displayName = displayName;
        this.emoji = emoji;
        this.color = color;
        this.defaultOn = defaultOn;
    }

    public String settingKey() {
        return "notify." + name().toLowerCase();
    }

    /** Setting for whether this bag's built-in sound plays. */
    public String soundKey() {
        return "sound." + name().toLowerCase();
    }

    public static BagType byObjectType(int objectType) {
        for (BagType b : values()) if (b.objectType == objectType) return b;
        return null;
    }

    /** The game object to draw this bag with. */
    public int spriteType() {
        return objectType;
    }
}
