package whitebag;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The collection book: every UT and ST item that can drop in a bag, by where it drops (from the RealmEye wiki,
 * bundled as collection-items.txt), and which of them the pup has seen you loot.
 */
final class LootBook {
    private LootBook() {
    }

    static final class Item {
        final String name, tier;
        /** Every place it drops. */
        final List<String> places = new ArrayList<>();
        /** The game's item id (for its picture), or -1 if the game files don't have it by that name. */
        int id = -1;

        Item(String name, String tier) {
            this.name = name;
            this.tier = tier;
        }
    }

    static final class Place {
        final String name;
        final List<Item> items = new ArrayList<>();

        Place(String name) {
            this.name = name;
        }
    }

    /** What you've found of an item: how many times, and when first. */
    static final class Found {
        int times;
        long first = Long.MAX_VALUE; // Unix seconds
        String where = "";
    }

    private static Map<String, Place> places;
    private static Map<String, Item> items;
    private static boolean idsMatched;

    /** Places in file order (alphabetical), each with its items. */
    static synchronized List<Place> places() {
        load();
        matchIds();
        return new ArrayList<>(places.values());
    }

    static synchronized int itemCount() {
        load();
        return items.size();
    }

    private static void load() {
        if (places != null) return;
        places = new LinkedHashMap<>();
        items = new LinkedHashMap<>();
        try (InputStream in = LootBook.class.getResourceAsStream("/whitebag/collection-items.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                String[] f = line.split("\\|", 3);
                if (f.length < 3) continue;
                String key = norm(f[2]);
                Item it = items.get(key);
                if (it == null) {
                    it = new Item(f[2].trim(), f[1].trim());
                    items.put(key, it);
                }
                it.places.add(f[0].trim());
                places.computeIfAbsent(f[0].trim(), Place::new).items.add(it);
            }
        } catch (Exception e) {
            System.out.println("Collection book: couldn't read its item list: " + e);
        }
    }

    /** Matches item names to the game's again (after the game files have been read). */
    static synchronized void reset() {
        idsMatched = false;
    }

    /** Finds each item's game id by name, once the game files are read. */
    private static void matchIds() {
        if (idsMatched || !LootImage.gameSpritesReady) return;
        Map<String, Integer> byName = new HashMap<>();
        for (int id : ItemXml.ids()) {
            String n = ItemInfo.of(id).name;
            if (n != null && !n.isEmpty()) byName.putIfAbsent(norm(n), id);
        }
        if (byName.isEmpty()) return;
        for (Item it : items.values()) {
            Integer id = byName.get(norm(it.name));
            if (id != null) it.id = id;
        }
        idsMatched = true;
    }

    /** Which collection items appear in these bags, by item. */
    static synchronized Map<Item, Found> found(List<LootStats.Entry> bags) {
        load();
        Map<Item, Found> out = new HashMap<>();
        Map<Integer, Item> cache = new HashMap<>();
        for (LootStats.Entry e : bags) {
            for (int id : e.items) {
                Item it = cache.computeIfAbsent(id, k -> items.get(norm(ItemInfo.of(k).name)));
                if (it == null) continue;
                if (it.id < 0) it.id = id;
                Found f = out.computeIfAbsent(it, k -> new Found());
                f.times++;
                if (e.at < f.first) {
                    f.first = e.at;
                    f.where = e.map;
                }
            }
        }
        return out;
    }

    /** The collection items in {@code items} that never showed up in {@code before} (names, once each). */
    static synchronized List<String> newItems(List<ItemInfo> items, List<LootStats.Entry> before) {
        load();
        java.util.Set<String> had = new java.util.HashSet<>();
        Map<Integer, String> names = new HashMap<>();
        for (LootStats.Entry e : before) {
            for (int id : e.items) had.add(names.computeIfAbsent(id, k -> norm(ItemInfo.of(k).name)));
        }
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ItemInfo i : items) {
            String k = norm(i.name);
            Item it = items().get(k);
            if (it != null && !had.contains(k) && seen.add(k)) out.add(it.name);
        }
        return out;
    }

    private static Map<String, Item> items() {
        load();
        return items;
    }

    /** True if a dungeon you ran (game map name) is this place of the book. */
    static boolean samePlace(String map, String place) {
        String a = norm(map), b = norm(place);
        if (a.equals(b)) return true;
        return (a.equals("realm of the mad god") || a.equals("realm")) && b.equals("realm");
    }

    /** Lower case, no "the", apostrophes or punctuation, single spaces. */
    static String norm(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT).replace('’', '\'').replace("'", "");
        t = t.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (t.startsWith("the ")) t = t.substring(4);
        return t;
    }

    static List<Item> sorted(List<Item> list) {
        List<Item> out = new ArrayList<>(list);
        out.sort((a, b) -> a.tier.equals(b.tier) ? a.name.compareToIgnoreCase(b.name) : a.tier.equals("UT") ? -1 : 1);
        return Collections.unmodifiableList(out);
    }
}
