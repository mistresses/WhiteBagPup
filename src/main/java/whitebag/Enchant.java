package whitebag;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;

/**
 * An item's equipment rarity (Uncommon, Rare, Legendary, Divine), which is how many enchantment slots it rolled.
 * <p>
 * The game sends a loot bag's extra item data as one comma-separated string (one entry per bag slot). Each entry is
 * URL-safe Base64: a 3-byte header (0, 2, 4), then little-endian shorts, one per slot: an enchantment id,
 * -1 for an unlocked empty slot, -2 for a locked slot, and -3 to end. The rarity is the number of unlocked slots.
 * (Format as decoded by the RealmShark community fork by DaAwesomePwner, MIT licence.)
 */
enum Enchant {
    NONE("", new Color(0xC9BBC3)),
    UNCOMMON("Uncommon", new Color(0x4FC46A)),
    RARE("Rare", new Color(0x4D9BFF)),
    LEGENDARY("Legendary", new Color(0xB063FF)),
    DIVINE("Divine", new Color(0xF0B10A));

    final String label;
    final Color color;

    Enchant(String label, Color color) {
        this.label = label;
        this.color = color;
    }

    /** The rarity for one slot's data entry; NONE when the item has no slots or the data is missing or unreadable. */
    static Enchant of(String entry) {
        int slots = unlockedSlots(entry);
        return slots >= 1 && slots <= 4 ? values()[slots] : NONE;
    }

    /**
     * The unlocked slots in order: an enchantment type, or -1 for an empty slot. Null if the data is missing
     * or unreadable; empty if the item has no slots.
     */
    static java.util.List<Integer> slots(String entry) {
        if (entry == null) return null;
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (entry.trim().isEmpty()) return out;
        byte[] b;
        try {
            b = Base64.getUrlDecoder().decode(entry.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (b.length < 3 || b[0] != 0 || b[1] != 2 || b[2] != 4) return null;
        int size = Math.min(b.length, 3 + 8);
        ByteBuffer buf = ByteBuffer.wrap(b, 3, size - 3).order(ByteOrder.LITTLE_ENDIAN);
        while (buf.remaining() >= 2) {
            short id = buf.getShort();
            if (id == -3) break;
            if (id == -2) continue;
            if (id < -3) return null;
            out.add((int) id);
        }
        return out;
    }

    /** Unlocked enchantment slots, or -1 if the data is missing or unreadable. */
    static int unlockedSlots(String entry) {
        if (entry == null || entry.isEmpty() || entry.length() > 4096) return entry != null && entry.isEmpty() ? 0 : -1;
        byte[] b;
        try {
            b = Base64.getUrlDecoder().decode(entry.trim());
        } catch (IllegalArgumentException e) {
            return -1;
        }
        if (b.length < 3 || b[0] != 0 || b[1] != 2 || b[2] != 4) return -1;
        int size = Math.min(b.length, 3 + 8);
        if ((size - 3) % 2 != 0) return -1;
        ByteBuffer buf = ByteBuffer.wrap(b, 3, size - 3).order(ByteOrder.LITTLE_ENDIAN);
        int slots = 0;
        while (buf.remaining() >= 2) {
            short id = buf.getShort();
            if (id == -3) break;
            if (id == -2) continue;
            if (id < -3) return -1;
            slots++;
        }
        return slots;
    }
}
