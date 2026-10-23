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
 * prefix and suffix titles as hex ids. The words for those ids are in the game files the app extracts (assets/xml).
 * What was found for each id is written to titles-debug.txt, so a title that comes out wrong can be fixed.
 */
final class Titles {
    private Titles() {
    }

    /** id -> title words ("" if it has none), looked up once. */
    private static final Map<Integer, String> words = new HashMap<>();
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
        String[] parts = codes.split(",");
        String prefix = parts.length > 0 ? word(parts[0]) : "";
        String suffix = parts.length > 1 ? word(parts[1]) : "";
        StringBuilder sb = new StringBuilder();
        if (!prefix.isEmpty()) sb.append(prefix).append(' ');
        sb.append(name);
        if (!suffix.isEmpty()) sb.append(' ').append(suffix);
        return sb.toString();
    }

    /** "Sophie,a19d,fe3" -> "Sophie the Wanderer". */
    static String titled(String raw) {
        return titled(DpsTracker.cleanName(raw), codes(raw));
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
                boolean titleLike = tag.toLowerCase(Locale.ROOT).contains("title") || f.getName().toLowerCase(Locale.ROOT).contains("title");
                // the entry's body (up to its end tag) for a DisplayId / Text child
                int end = text.indexOf("</" + tag + ">", m.end());
                String body = m.group(0).endsWith("/>") || end < 0 ? "" : text.substring(m.end(), Math.min(end, m.end() + 2000));
                String attrs = m.group(2) + " " + m.group(4);
                String shown = child(body, "DisplayId");
                if (shown.isEmpty()) shown = child(body, "Title");
                if (shown.isEmpty()) shown = child(body, "Text");
                if (shown.isEmpty()) shown = attr(attrs, "displayid");
                if (shown.isEmpty()) shown = attr(attrs, "id");
                String snippet = (m.group(0) + " " + body).replaceAll("\\s+", " ").trim();
                if (snippet.length() > 300) snippet = snippet.substring(0, 300) + "...";
                debug.add(hex + " -> " + (shown.isEmpty() ? "(no name)" : shown) + "  [" + f.getName() + ", <" + tag + ">]  " + snippet);
                if (titleLike) return pretty(shown);
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
