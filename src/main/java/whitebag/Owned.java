package whitebag;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Items you own: every item the pup has seen in your vault, your seasonal vault, your gift chest, your seasonal spoils, or on one of your
 * characters (gear, inventory and backpack). They count towards the Collection even if you looted them before the pup
 * was watching. Kept in owned-items.txt (account|where|item id|first seen).
 */
final class Owned {
    private Owned() {
    }

    static final String VAULT = "vault", SEASONAL_VAULT = "seasonal vault", CHARACTER = "character", SEASONAL_CHARACTER = "seasonal character",
            GIFT_CHEST = "gift chest", SEASONAL_SPOILS = "seasonal spoils";
    private static final String FILE = "owned-items.txt";

    static final class Sighting {
        final String account, where;
        final int item;
        final long first; // Unix seconds

        Sighting(String account, String where, int item, long first) {
            this.account = account;
            this.where = where;
            this.item = item;
            this.first = first;
        }
    }

    private static Map<String, Sighting> seen; // account|where|item -> first sighting

    private static Map<String, Sighting> seen() {
        if (seen == null) {
            seen = new LinkedHashMap<>();
            try {
                for (String l : Files.readAllLines(Paths.get(FILE), StandardCharsets.UTF_8)) {
                    String[] f = l.split("\\|");
                    if (f.length < 4) continue;
                    try {
                        Sighting s = new Sighting(f[0], f[1], Integer.parseInt(f[2]), Long.parseLong(f[3]));
                        seen.putIfAbsent(s.account + "|" + s.where + "|" + s.item, s);
                    } catch (NumberFormatException ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return seen;
    }

    /** Notes these items as seen in this place; returns how many weren't known there before. */
    static synchronized int note(String account, String where, int[] items) {
        if (account == null || account.isEmpty() || items == null) return 0;
        long now = System.currentTimeMillis() / 1000;
        List<String> fresh = new ArrayList<>();
        for (int id : items) {
            if (id <= 0) continue;
            String k = account + "|" + where + "|" + id;
            if (seen().containsKey(k)) continue;
            seen.put(k, new Sighting(account, where, id, now));
            fresh.add(k + "|" + now);
        }
        if (!fresh.isEmpty()) {
            try {
                Files.write(Paths.get(FILE), fresh, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {
            }
        }
        return fresh.size();
    }

    /** Everything these accounts have been seen owning. */
    static synchronized List<Sighting> of(Predicate<String> mine) {
        List<Sighting> out = new ArrayList<>();
        for (Sighting s : seen().values()) if (mine == null || mine.test(s.account)) out.add(s);
        return Collections.unmodifiableList(out);
    }

    /** "your vault", "your seasonal vault", "a character" for tooltips. */
    static String describe(String where) {
        switch (where) {
            case VAULT: return "in your vault";
            case SEASONAL_VAULT: return "in your seasonal vault";
            case GIFT_CHEST: return "in your gift chest";
            case SEASONAL_SPOILS: return "in your seasonal spoils";
            case SEASONAL_CHARACTER: return "on a seasonal character";
            default: return "on a character";
        }
    }

    /** Item ids on a character: equipped gear, inventory and backpack. */
    static int[] onCharacter(MeTracker.Thing c) {
        if (c == null) return new int[0];
        List<Integer> out = new ArrayList<>();
        for (packets.data.enums.StatType t : packets.data.enums.StatType.values()) {
            String n = t.name();
            if (!(n.startsWith("INVENTORY_") || (n.startsWith("BACKPACK_") && !n.equals("BACKPACK_SLOTS_STAT")))) continue;
            int v = c.i(t, -1);
            if (v > 0) out.add(v);
        }
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    /** True if this character is a seasonal one. */
    static boolean seasonal(MeTracker.Thing c) {
        return c != null && c.i(packets.data.enums.StatType.SEASONAL, 0) != 0;
    }
}
