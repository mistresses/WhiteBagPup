package whitebag;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

/**
 * Checks GitHub once when the app opens for a newer release of White Bag Pup.
 * <p>
 * It only reads the repository's public "latest release" (its tag, like v53, and its page link) and never downloads
 * or runs anything: if there's a newer version, the app says so and offers a button that opens the release page.
 */
final class UpdateCheck {
    private UpdateCheck() {
    }

    /**
     * This build's version (never shown to users). Bump it for every release, and tag the GitHub release
     * the same: 1.0, 1.1, 1.2...
     */
    static final String VERSION = "2.1";

    /** The GitHub repository releases come from, as "owner/name". Can be changed in the settings file (update.repo). */
    static final String DEFAULT_REPO = "mistresses/WhiteBagPup";

    /** Calls back (on a background thread) with {tag, page link} when a newer release exists. */
    static void check(String repo, BiConsumer<String, String> newer, java.util.function.Consumer<String> problem) {
        if (repo == null || !repo.trim().matches("[\\w.-]+/[\\w.-]+")) return;
        Thread t = new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo.trim() + "/releases/latest").openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "WhiteBagPup");
                int code = c.getResponseCode();
                if (code == 404) return; // no releases yet
                if (code != 200) {
                    // e.g. GitHub's hourly limit: the release page itself redirects to the latest tag
                    String tag = latestFromRedirect(repo.trim());
                    if (tag == null) throw new Exception("GitHub answered HTTP " + code);
                    if (isNewer(tag, VERSION)) newer.accept(tag, "https://github.com/" + repo.trim() + "/releases/tag/" + tag);
                    return;
                }
                String body;
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    body = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
                JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                String tag = o.has("tag_name") ? o.get("tag_name").getAsString() : "";
                String page = o.has("html_url") ? o.get("html_url").getAsString() : "https://github.com/" + repo.trim() + "/releases";
                if (isNewer(tag, VERSION)) newer.accept(tag, page);
            } catch (Exception e) {
                problem.accept(e.getMessage());
            }
        }, "update-check");
        t.setDaemon(true);
        t.start();
    }

    /** github.com/owner/repo/releases/latest redirects to .../releases/tag/v53: read the tag from that. */
    private static String latestFromRedirect(String repo) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://github.com/" + repo + "/releases/latest").openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", "WhiteBagPup");
            String loc = c.getHeaderField("Location");
            int i = loc == null ? -1 : loc.indexOf("/releases/tag/");
            return i < 0 ? null : java.net.URLDecoder.decode(loc.substring(i + "/releases/tag/".length()), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /** "v53" is newer than "v52"; also handles "1.2.3" style tags. */
    static boolean isNewer(String tag, String mine) {
        int[] a = parts(tag), b = parts(mine);
        if (a.length == 0) return false;
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] parts(String v) {
        String s = v == null ? "" : v.replaceAll("[^0-9.]", "");
        if (s.isEmpty()) return new int[0];
        String[] p = s.split("\\.");
        int[] out = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            try {
                out[i] = Integer.parseInt(p[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
