package whitebag;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player titles ("Sophie the Wanderer"). The game sends a player's name as "Sophie,a19d,fe3": the name, then the
 * title parts as hex ids. Each part is an Object of class Title in the game files the app extracts (assets/xml), with
 * its words in DisplayId and a TitleType of Prefix or Suffix. Like in game, both go after the name: name, prefix,
 * suffix ("Sophie" + "the" + "Wanderer"). An empty DisplayId (Title_Blank_Suffix) means that slot is empty.
 * What was found for each id is written to titles-debug.txt, so a title that comes out wrong can be fixed.
 */
final class Titles {
    private Titles() {
    }

    /** id -> title words ("" if it has none), looked up once. */
    private static final Map<Integer, String> words = new HashMap<>();
    /** id -> "prefix" / "suffix" (from the game's TitleType), or "" if it doesn't say. */
    private static final Map<Integer, String> kinds = new HashMap<>();
    private static final List<String> debug = new ArrayList<>();
    private static final Pattern TYPE = Pattern.compile("<(\\w+)([^>]*?)\\btype\\s*=\\s*\"(0x[0-9a-fA-F]+|\\d+)\"([^>]*)>", Pattern.CASE_INSENSITIVE);

    /** The codes after the name ("a19d,fe3"), or "" if there are none. */
    static String codes(String raw) {
        if (raw == null) return "";
        int c = raw.indexOf(',');
        return c < 0 ? "" : raw.substring(c + 1).trim();
    }

    /** "Sophie the Wanderer" from "Sophie" and "a19d,fe3" (just the name if the titles can't be found). */
    static String titled(String name, String codes) {
        if (name == null || name.isEmpty() || codes == null || codes.isEmpty()) return name == null ? "" : name;
        // like in game: the name, then the prefix, then the suffix ("Sophie the Wanderer")
        StringBuilder prefix = new StringBuilder(), suffix = new StringBuilder(), other = new StringBuilder();
        for (String code : codes.split(",")) {
            String w = word(code);
            if (w.isEmpty() || placeholder(w)) continue;
            String k = kindOf(code);
            (k.equals("prefix") ? prefix : k.equals("suffix") ? suffix : other).append(' ').append(w);
        }
        return name + prefix + other + suffix;
    }

    /** "Sophie,a19d,fe3" -> "Sophie the Wanderer". */
    static String titled(String raw) {
        return titled(DpsTracker.cleanName(raw), codes(raw));
    }

    /** The game's stand-ins for an empty title slot ("the Blank Suffix", "Empty Prefix", "None"): not shown. */
    static boolean placeholder(String title) {
        String t = title.trim().toLowerCase(Locale.ROOT);
        // "blank"/"empty" anywhere ("the Blank Suffix"), or a title that is only "None" (not "Second to None")
        return t.matches(".*\\b(blank|empty|placeholder)\\b.*") || t.matches("(the )?(none|no title|unset)");
    }

    /** "the Wanderer", "of the Realm", "from Beyond": words that come after a name. */
    static boolean goesAfter(String title) {
        String t = title.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("the ") || t.startsWith("of ") || t.startsWith("from ") || t.startsWith("who ") || t.startsWith(",");
    }

    private static synchronized String kindOf(String hex) {
        try {
            String k = kinds.get(Integer.parseInt(hex.trim(), 16));
            return k == null ? "" : k;
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private static synchronized String word(String hex) {
        int id;
        try {
            id = Integer.parseInt(hex.trim(), 16);
        } catch (NumberFormatException e) {
            return "";
        }
        if (id == 0) return "";
        String w = words.get(id);
        if (w != null) return w;
        w = find(id, hex.trim());
        words.put(id, w);
        try {
            Files.write(new File("titles-debug.txt").toPath(), debug, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        return w;
    }

    /** Looks the id up in the extracted game files: an entry with type="0x..." and its display name. */
    private static String find(int id, String hex) {
        File dir = new File("assets/xml");
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".xml"));
        if (files == null) {
            debug.add(hex + ": the game files aren't extracted yet (assets/xml)");
            return "";
        }
        // title files first, so an object with the same number elsewhere doesn't win
        java.util.Arrays.sort(files, (a, b) -> Boolean.compare(!a.getName().toLowerCase(Locale.ROOT).contains("title"),
                !b.getName().toLowerCase(Locale.ROOT).contains("title")));
        for (File f : files) {
            String text;
            try {
                if (f.length() > 64L * 1024 * 1024) continue;
                text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            } catch (Exception e) {
                continue;
            }
            Matcher m = TYPE.matcher(text);
            while (m.find()) {
                String t = m.group(3);
                int got;
                try {
                    got = t.toLowerCase(Locale.ROOT).startsWith("0x") ? Integer.parseInt(t.substring(2), 16) : Integer.parseInt(t);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (got != id) continue;
                String tag = m.group(1);
                // the entry's body (up to its end tag): DisplayId, Class and TitleType
                int end = text.indexOf("</" + tag + ">", m.end());
                String body = m.group(0).endsWith("/>") || end < 0 ? "" : text.substring(m.end(), Math.min(end, m.end() + 2000));
                String attrs = m.group(2) + " " + m.group(4);
                boolean titleLike = child(body, "Class").equalsIgnoreCase("Title") || tag.toLowerCase(Locale.ROOT).contains("title")
                        || f.getName().toLowerCase(Locale.ROOT).contains("title");
                String type = child(body, "TitleType").toLowerCase(Locale.ROOT);
                if (type.startsWith("pre")) kinds.put(id, "prefix");
                else if (type.startsWith("suf")) kinds.put(id, "suffix");
                String shown;
                if (has(body, "DisplayId")) {
                    shown = child(body, "DisplayId"); // empty means no title in this slot
                } else {
                    shown = child(body, "Text");
                    if (shown.isEmpty()) shown = attr(attrs, "displayid");
                    if (shown.isEmpty()) shown = pretty(attr(attrs, "id"));
                }
                String snippet = (m.group(0) + " " + body).replaceAll("\\s+", " ").trim();
                if (snippet.length() > 300) snippet = snippet.substring(0, 300) + "...";
                debug.add(hex + " -> " + (shown.isEmpty() ? "(no name)" : shown) + "  [" + f.getName() + ", <" + tag + ">]  " + snippet);
                if (titleLike) return shown.length() > 40 ? shown.substring(0, 40) : shown;
                // not a title entry (items and enemies share the numbers): noted, but not used
            }
        }
        debug.add(hex + ": no title with that number in the game files");
        return "";
    }

    private static String child(String body, String tag) {
        Matcher m = Pattern.compile("<" + tag + "\\b[^>]*>([^<]*)</" + tag + ">", Pattern.CASE_INSENSITIVE).matcher(body);
        return m.find() ? m.group(1).trim() : "";
    }

    /** True if the body has this tag at all (even empty, like {@code <DisplayId></DisplayId>} or {@code <DisplayId/>}). */
    private static boolean has(String body, String tag) {
        return Pattern.compile("<" + tag + "\\b", Pattern.CASE_INSENSITIVE).matcher(body).find();
    }

    private static String attr(String attrs, String name) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(attrs);
        return m.find() ? m.group(1).trim() : "";
    }

    /** Game ids like "Title_Wanderer" or "the_Wanderer" become "the Wanderer". */
    private static String pretty(String s) {
        String t = s.replaceFirst("(?i)^(title|prefix|suffix)[ _.:-]+", "").replace('_', ' ').trim();
        return t.length() > 40 ? t.substring(0, 40) : t;
    }
}
