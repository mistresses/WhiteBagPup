package whitebag;

import packets.data.StatData;

import java.util.ArrayList;
import java.util.List;

/**
 * One loot bag seen in game, with the items it holds.
 */
public class BagDrop {
    /** Inventory stat types 8..15 are the 8 slots of a loot bag. */
    private static final int FIRST_SLOT_STAT = 8;
    private static final int SLOTS = 8;

    public final int objectId;
    public final BagType type;
    public final String bagName;
    public final String emoji;
    public final int color;
    public final String mapName;
    public final String playerName;
    /** When the bag was seen, in Unix seconds. */
    public final long droppedAt = System.currentTimeMillis() / 1000L;
    private final int bagObjectType;
    private final int[] slots = new int[SLOTS];
    boolean sent = false;
    /** A picture of the game window when the bag dropped (JPEG), if screenshots are on. */
    volatile byte[] gameShot;
    /** Optional extra line for the post, e.g. the drought tracker's celebration. */
    volatile String note;
    /** Collection items in this bag you'd never looted before (names), worked out when the bag is logged. */
    volatile java.util.List<String> newToCollection = java.util.Collections.emptyList();

    BagDrop(int objectId, BagType type, String mapName, String playerName) {
        this.objectId = objectId;
        this.type = type;
        this.bagName = type.displayName;
        this.bagObjectType = type.spriteType();
        this.emoji = type.emoji;
        this.color = type.color;
        this.mapName = mapName;
        this.playerName = playerName;
        for (int i = 0; i < SLOTS; i++) slots[i] = -1;
    }

    /**
     * Applies inventory stats from an update or new tick packet.
     */
    private static final int UNIQUE_DATA_STAT = 80;
    private final Enchant[] enchants = {Enchant.NONE, Enchant.NONE, Enchant.NONE, Enchant.NONE,
            Enchant.NONE, Enchant.NONE, Enchant.NONE, Enchant.NONE};

    void applyStats(StatData[] stats) {
        if (stats == null) return;
        for (StatData s : stats) {
            int slot = s.statTypeNum - FIRST_SLOT_STAT;
            if (slot >= 0 && slot < SLOTS) slots[slot] = s.statValue;
            else if (s.statTypeNum == UNIQUE_DATA_STAT && s.stringStatValue != null) {
                // one entry per bag slot: the item's enchantment slots, which set its rarity
                String[] parts = s.stringStatValue.split(",", -1);
                for (int i = 0; i < SLOTS; i++) enchants[i] = i < parts.length ? Enchant.of(parts[i]) : Enchant.NONE;
            }
        }
    }

    /** The bag's object type, used to draw its icon. */
    public int bagObjectType() {
        return bagObjectType;
    }

    /** Items from an overflow bag (the spill-over when a bag gets more than 8 items), shown with this one. */
    private final List<Integer> extra = new ArrayList<>();
    private final List<Enchant> extraEnchants = new ArrayList<>();
    /** Where the bag landed, or NaN if unknown. */
    double x = Double.NaN, y = Double.NaN;
    /** When the bag appeared, in milliseconds. */
    final long seenMs = System.currentTimeMillis();

    /** How many items are in the bag itself (not counting overflow). */
    int filledSlots() {
        int n = 0;
        for (int id : slots) if (id > 0) n++;
        return n;
    }

    /** Takes in the items of this bag's overflow bag. */
    void absorb(BagDrop other) {
        for (int i = 0; i < SLOTS; i++) {
            if (other.slots[i] > 0) {
                extra.add(other.slots[i]);
                extraEnchants.add(other.enchants[i]);
            }
        }
        extra.addAll(other.extra);
        extraEnchants.addAll(other.extraEnchants);
    }

    public List<ItemInfo> items() {
        List<ItemInfo> items = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] > 0) items.add(ItemInfo.of(slots[i]).withEnchant(enchants[i])); // -1 is an empty slot
        }
        for (int i = 0; i < extra.size(); i++) items.add(ItemInfo.of(extra.get(i)).withEnchant(extraEnchants.get(i)));
        return items;
    }

    public List<String> itemNames() {
        List<String> names = new ArrayList<>();
        for (ItemInfo i : items()) names.add(i.name);
        return names;
    }
}
