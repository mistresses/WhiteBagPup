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

    // ---------------------------------------------------------------- shiny versions

    private static List<Item> shinies;
    private static Map<Integer, Item> shinyById;
    private static final java.util.regex.Pattern INTERNAL =
            java.util.regex.Pattern.compile("(?i)\\b(proc[a-z0-9]*|proj(ectile)?|effect|test)\\b|proc\\d|procs?[a-d]$|test$");

    /**
     * Every shiny item in the game files: the game lists each shiny version as its own item, labelled SHINY, and the
     * collectable ones are UT or ST equipment (not the internal effects that carry the label too). Each is filed
     * under the places its normal version drops. Empty until the game files are read.
     */
    static synchronized List<Item> shinies() {
        load();
        if (shinies != null) return shinies;
        if (!LootImage.gameSpritesReady && ItemXml.ids().isEmpty()) return Collections.emptyList();
        List<Item> out = new ArrayList<>();
        Map<Integer, Item> byId = new HashMap<>();
        for (int id : ItemXml.ids()) {
            String labels;
            try {
                labels = assets.IdToAsset.getIdLabel(id);
            } catch (Throwable e) {
                continue;
            }
            if (labels == null) continue;
            java.util.Set<String> l = new java.util.HashSet<>();
            for (String raw : labels.toUpperCase(Locale.ROOT).split("[,;\\s]+")) l.add(raw.trim());
            if (!l.contains("SHINY") || l.contains("CONSUMABLE") || l.contains("SIGIL")) continue;
            boolean ut = l.contains("UT") || l.contains("TAB_UT"), st = l.contains("ST") || l.contains("TAB_ST");
            if (!ut && !st) continue;
            String name = ItemInfo.of(id).name;
            String base = name.endsWith(" Shiny") ? name.substring(0, name.length() - 6).trim() : name;
            if (base.isEmpty() || INTERNAL.matcher(base).find()) continue;
            Item it = new Item(base + " (Shiny)", st && !ut ? "ST" : "UT");
            it.id = id;
            Item normal = items.get(norm(base));
            if (normal != null) it.places.addAll(normal.places);
            else it.places.add("Other");
            out.add(it);
            byId.put(id, it);
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        if (out.isEmpty() && !idsMatched) return out; // game files not read yet: try again later
        shinies = out;
        shinyById = byId;
        return out;
    }

    private static final Map<Integer, Boolean> shinyCache = new HashMap<>();

    /** True if the game files label this item SHINY (a shiny version of an item). */
    static synchronized boolean isShiny(int id) {
        Boolean b = shinyCache.get(id);
        if (b != null) return b;
        boolean yes = false;
        try {
            String labels = assets.IdToAsset.getIdLabel(id);
            if (labels != null) for (String raw : labels.toUpperCase(Locale.ROOT).split("[,;\\s]+")) if (raw.trim().equals("SHINY")) yes = true;
        } catch (Throwable ignored) {
        }
        if (!yes) {
            String n = ItemInfo.of(id).name;
            yes = n != null && n.endsWith(" Shiny");
        }
        shinyCache.put(id, yes);
        return yes;
    }

    /** The shinies as places (the same places as the normal items, plus "Other"). */
    static synchronized List<Place> shinyPlaces() {
        Map<String, Place> out = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Item it : shinies()) for (String p : it.places) out.computeIfAbsent(p, Place::new).items.add(it);
        return new ArrayList<>(out.values());
    }

    /** Which shinies appear in these bags. */
    static synchronized Map<Item, Found> foundShinies(List<LootStats.Entry> bags) {
        shinies();
        Map<Item, Found> out = new HashMap<>();
        if (shinyById == null) return out;
        for (LootStats.Entry e : bags) {
            for (int id : e.items) {
                Item it = shinyById.get(id);
                if (it == null) continue;
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

    /** What you've found of an item: how many times, and when first. */
    static final class Found {
        int times;
        long first = Long.MAX_VALUE; // Unix seconds
        String where = "";
        /** Also (or only) seen owned: "your vault", "a character"... ("" if not). */
        String owned = "";
    }

    private static Map<String, Place> places;
    private static Map<String, Item> items;
    private static boolean idsMatched;

    /** Places in file order (alphabetical), each with its items. */
    /** Places with their items; once the game files are read, items the game doesn't have are left out. */
    static synchronized List<Place> places() {
        load();
        matchIds();
        if (!idsMatched) return new ArrayList<>(places.values());
        List<Place> out = new ArrayList<>();
        for (Place p : places.values()) {
            Place q = new Place(p.name);
            for (Item it : p.items) if (it.id >= 0) q.items.add(it);
            if (!q.items.isEmpty()) out.add(q);
        }
        return out;
    }

    static synchronized int itemCount() {
        load();
        matchIds();
        if (!idsMatched) return items.size();
        int n = 0;
        for (Item it : items.values()) if (it.id >= 0) n++;
        return n;
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
        shinies = null;
        shinyById = null;
        shinyCache.clear();
    }

    /** Finds each item's game id by name, once the game files are read. */
    private static void matchIds() {
        if (idsMatched || !LootImage.gameSpritesReady) return;
        Map<String, Integer> byName = new HashMap<>();
        for (int id : ItemXml.ids()) {
            if (isShiny(id)) continue; // a shiny version can share its item's name: it's not the item itself
            String n = ItemInfo.of(id).name;
            if (n != null && !n.isEmpty()) byName.putIfAbsent(norm(n), id);
            try {
                // the game's internal name too (some items show a different name in game than on the wiki)
                String inner = assets.IdToAsset.getObjectIdName(id);
                if (inner != null && !inner.isEmpty()) byName.putIfAbsent(norm(inner), id);
            } catch (Throwable ignored) {
            }
        }
        if (byName.isEmpty()) return;
        List<String> missing = new ArrayList<>();
        for (Item it : items.values()) {
            Integer id = byName.get(norm(it.name));
            if (id != null) it.id = id;
            else missing.add(it.tier + " " + it.name + "  (" + String.join(", ", it.places) + ")");
        }
        idsMatched = true;
        // items the game files don't have (removed, or named differently): left out of the book, and listed here
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("collection-debug.txt"), missing, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        System.out.println("Collection book: " + (items.size() - missing.size()) + " items matched the game, " + missing.size() + " not (collection-debug.txt)");
    }

    /**
     * Which collection items these accounts have: looted in a bag, or seen in their vault, seasonal vault or on a
     * character (see Owned).
     */
    static synchronized Map<Item, Found> found(List<LootStats.Entry> bags, java.util.function.Predicate<String> mine) {
        Map<Item, Found> out = found(bags);
        Map<Integer, Item> cache = new HashMap<>();
        for (Owned.Sighting s : Owned.of(mine)) {
            if (isShiny(s.item)) continue;
            Item it = cache.computeIfAbsent(s.item, k -> items.get(norm(ItemInfo.of(k).name)));
            if (it == null) continue;
            if (it.id < 0) it.id = s.item;
            Found f = out.computeIfAbsent(it, k -> new Found());
            if (f.owned.isEmpty()) f.owned = Owned.describe(s.where);
            if (f.times == 0 && s.first < f.first) f.first = s.first;
        }
        return out;
    }

    /** Which shinies these accounts have: looted, or seen in a vault or on a character. */
    static synchronized Map<Item, Found> foundShinies(List<LootStats.Entry> bags, java.util.function.Predicate<String> mine) {
        Map<Item, Found> out = foundShinies(bags);
        if (shinyById == null) return out;
        for (Owned.Sighting s : Owned.of(mine)) {
            Item it = shinyById.get(s.item);
            if (it == null) continue;
            Found f = out.computeIfAbsent(it, k -> new Found());
            if (f.owned.isEmpty()) f.owned = Owned.describe(s.where);
            if (f.times == 0 && s.first < f.first) f.first = s.first;
        }
        return out;
    }

    /** "Found 3 times, first on 9 Oct 2026 in Lost Halls" or "In your vault (seen 9 Oct 2026)", for tooltips (HTML-safe). */
    static String describe(Found f) {
        java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("d MMM yyyy", Locale.ROOT);
        String when = day.format(new java.util.Date(f.first * 1000L));
        String where = f.where.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        if (f.times == 0) return Character.toUpperCase(f.owned.charAt(0)) + f.owned.substring(1) + " (seen " + when + ")";
        return "Found " + (f.times == 1 ? "once" : f.times + " times") + ", first on " + when + (where.isEmpty() ? "" : " in " + where)
                + (f.owned.isEmpty() ? "" : ". Also " + f.owned);
    }

    /** Which collection items appear in these bags, by item. */
    static synchronized Map<Item, Found> found(List<LootStats.Entry> bags) {
        load();
        Map<Item, Found> out = new HashMap<>();
        Map<Integer, Item> cache = new HashMap<>();
        for (LootStats.Entry e : bags) {
            for (int id : e.items) {
                if (isShiny(id)) continue; // counted on the shiny page, not as the normal item
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
            for (int id : e.items) if (!isShiny(id)) had.add(names.computeIfAbsent(id, k -> norm(ItemInfo.of(k).name)));
        }
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        shinies();
        java.util.Set<Integer> hadIds = new java.util.HashSet<>();
        for (LootStats.Entry e : before) hadIds.addAll(e.items);
        for (ItemInfo i : items) {
            if (isShiny(i.id)) {
                Item shiny = shinyById == null ? null : shinyById.get(i.id);
                if (shiny != null && !hadIds.contains(i.id) && seen.add("shiny:" + i.id)) out.add(shiny.name);
                continue;
            }
            String k = norm(i.name);
            Item it = items().get(k);
            if (it != null && !had.contains(k) && seen.add(k)) out.add(it.name);
        }
        return out;
    }

    /** The collection item with this name (as the wiki writes it), or null. */
    static synchronized Item byName(String name) {
        return items().get(norm(name));
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
