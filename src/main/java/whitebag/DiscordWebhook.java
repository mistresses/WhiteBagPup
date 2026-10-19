package whitebag;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Sends Discord webhook messages on a background thread so the packet sniffer is never blocked
 * by network calls. Messages are sent one at a time and Discord rate limits (HTTP 429) are honoured.
 */
public class DiscordWebhook {

    private static final Pattern WEBHOOK_URL = Pattern.compile(
            "^https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api(?:/v\\d+)?/webhooks/\\d+/[\\w-]+/?$");
    private static final Pattern DISCORD_ID = Pattern.compile("^\\d{15,22}$");

    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "discord-webhook");
        t.setDaemon(true);
        return t;
    });
    private final Log log;

    public interface Log {
        void log(String message);
    }

    public DiscordWebhook(Log log) {
        this.log = log;
    }

    public static boolean isValidUrl(String url) {
        return url != null && WEBHOOK_URL.matcher(url.trim()).matches();
    }

    public static boolean isValidDiscordId(String id) {
        return id != null && DISCORD_ID.matcher(id.trim()).matches();
    }

    /**
     * Queues a loot bag notification.
     */
    public void sendBag(String url, String mentionUserId, BagDrop drop) {
        sender.submit(() -> {
            // Pictures are drawn here, off the sniffer thread. If sprites aren't available the post is text only.
            List<ItemInfo> items = drop.items();
            byte[] bag = null, loot = null, icon = null;
            try {
                bag = LootImage.bagPng(drop.bagObjectType());
                icon = LootImage.bagIconPng(drop.bagObjectType());
                loot = LootImage.lootPng(items);
            } catch (Throwable e) {
                log.log("*whimper* couldn't draw the item pictures (" + e + "), sending text only.");
            }
            Map<String, byte[]> files = new LinkedHashMap<>();
            if (bag != null) files.put("bag.png", bag);
            if (loot != null) files.put("loot.png", loot);
            if (icon != null) files.put("bagicon.png", icon);
            byte[] shot = drop.gameShot;
            if (shot != null) files.put("game.jpg", shot);
            String json = bagJson(mentionUserId, drop, items, bag != null, loot != null, icon != null);
            if (shot != null) {
                // a second picture under the post: the game when the bag dropped
                json = json.substring(0, json.length() - 2)
                        + ",{\"color\":" + drop.color + ",\"image\":{\"url\":\"attachment://game.jpg\"}}]}";
            }
            post(url, json, files, drop.bagName + " bag");
        });
    }

    /** Queues any ready-made post (the recaps). */
    public void sendJson(String url, String json, Map<String, byte[]> files, String what) {
        Map<String, byte[]> copy = new LinkedHashMap<>(files);
        sender.submit(() -> post(url, json, copy, what));
    }

    /**
     * Queues a test message so the user can check their webhook works.
     */
    public void sendTest(String url, String mentionUserId) {
        sendTest(url, mentionUserId, null);
    }

    /** A test post, with a game picture if there is one (to check screenshots work). */
    public void sendTest(String url, String mentionUserId, byte[] gameShot) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"username\":").append(str(USERNAME)).append(",");
        appendMention(sb, mentionUserId);
        sb.append("\"embeds\":[{");
        sb.append("\"title\":").append(str("\uD83D\uDC36 *arf arf!*")).append(",");
        sb.append("\"description\":").append(str("your webhook works~ i'll come bark right here whenever a white bag drops \uD83D\uDC3E\uD83E\uDDB4")).append(",");
        sb.append("\"color\":").append(0xFF9EBB);
        if (gameShot != null) sb.append(",\"image\":{\"url\":\"attachment://game.jpg\"}");
        sb.append("}]}");
        String json = sb.toString();
        Map<String, byte[]> files = gameShot == null ? Collections.emptyMap() : Collections.singletonMap("game.jpg", gameShot);
        sender.submit(() -> post(url, json, files, "test message"));
    }

    private static final String USERNAME = "White Bag Pup";
    private static final String PAW = "\uD83D\uDC3E";
    private static final String[] BARKS = {
            "*woof woof!* look what i fetched for you~ \uD83E\uDDB4",
            "*tail wagging intensifies* \uD83D\uDC36\uD83D\uDC95",
            "*arf!* good pup, good pup!! \uD83D\uDC3E",
            "*happy puppy noises* \uD83E\uDDB4\u2728",
            "*sniff sniff* ...i smell loot!! \uD83D\uDC36",
            "*does a little zoomie* \uD83D\uDC3E\uD83D\uDC95",
    };

    static String bagJson(String mentionUserId, BagDrop drop, List<ItemInfo> list, boolean hasBagIcon, boolean hasLootImage) {
        return bagJson(mentionUserId, drop, list, hasBagIcon, hasLootImage, false);
    }

    static String bagJson(String mentionUserId, BagDrop drop, List<ItemInfo> list, boolean hasBagIcon, boolean hasLootImage, boolean hasSmallIcon) {
        StringBuilder desc = new StringBuilder();
        desc.append(BARKS[(int) Math.floorMod(drop.droppedAt + drop.objectId, (long) BARKS.length)]).append("\n");
        if (drop.note != null) desc.append("**").append(drop.note).append("**\n");
        desc.append("\n");
        if (list.isEmpty()) {
            desc.append("_still sniffing out what's inside~_");
        } else {
            for (ItemInfo i : list) {
                desc.append(PAW).append(" **").append(i.name).append("**");
                if (!i.rarity.isEmpty()) desc.append("  `").append(i.rarity).append("`");
                if (i.enchant != Enchant.NONE) desc.append("  ").append(i.enchant.label);
                desc.append("\n");
            }
        }

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"username\":").append(str(USERNAME)).append(",");
        appendMention(sb, mentionUserId);
        sb.append("\"embeds\":[{");
        if (hasSmallIcon) {
            // The real bag sprite as a little icon in front of the bag's name.
            sb.append("\"author\":{\"name\":").append(str(drop.bagName + " bag!! " + PAW))
                    .append(",\"icon_url\":\"attachment://bagicon.png\"},");
        } else {
            sb.append("\"title\":").append(str(drop.emoji + " " + drop.bagName + " bag!! " + PAW)).append(",");
        }
        sb.append("\"description\":").append(str(desc.toString().trim())).append(",");
        sb.append("\"color\":").append(drop.color).append(",");
        sb.append("\"fields\":[");
        sb.append(field("\uD83D\uDCCD Where", nonEmpty(drop.mapName, "somewhere~"), true)).append(",");
        sb.append(field("\uD83D\uDC36 Good pup", nonEmpty(drop.playerName, "you!"), true)).append(",");
        // Discord timestamp markup: shown in each viewer's own time zone, plus a live "x minutes ago".
        sb.append(field("\u23F0 When", "<t:" + drop.droppedAt + ":f> (<t:" + drop.droppedAt + ":R>)", false));
        sb.append("]");
        if (hasBagIcon) sb.append(",\"thumbnail\":{\"url\":\"attachment://bag.png\"}");
        if (hasLootImage) sb.append(",\"image\":{\"url\":\"attachment://loot.png\"}");
        sb.append("}]}");
        return sb.toString();
    }

    private static void appendMention(StringBuilder sb, String mentionUserId) {
        if (isValidDiscordId(mentionUserId)) {
            String id = mentionUserId.trim();
            sb.append("\"content\":\"<@").append(id).append(">\",");
            sb.append("\"allowed_mentions\":{\"users\":[\"").append(id).append("\"]},");
        } else {
            // Never let item names or map names trigger @everyone/@here pings.
            sb.append("\"allowed_mentions\":{\"parse\":[]},");
        }
    }

    private void post(String url, String json, Map<String, byte[]> files, String what) {
        String boundary = "----WhiteBag" + Long.toHexString(System.nanoTime());
        byte[] body = files.isEmpty() ? json.getBytes(StandardCharsets.UTF_8) : multipart(boundary, json, files);
        String contentType = files.isEmpty() ? "application/json; charset=utf-8" : "multipart/form-data; boundary=" + boundary;
        for (int attempt = 1; attempt <= 4; attempt++) {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(url.trim()).openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(10_000);
                con.setReadTimeout(15_000);
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", contentType);
                con.setRequestProperty("User-Agent", "WhiteBagPup/1.0");
                try (OutputStream os = con.getOutputStream()) {
                    os.write(body);
                }
                int code = con.getResponseCode();
                if (code >= 200 && code < 300) {
                    log.log("*drops it at Discord's feet* sent the " + what + "! *proud tail wag*");
                    return;
                }
                String response = readBody(code >= 400 ? con.getErrorStream() : con.getInputStream());
                if (code == 429) {
                    long waitMs = retryAfterMs(con.getHeaderField("Retry-After"));
                    log.log("*waits patiently* Discord says slow down, trying again in " + (waitMs / 1000.0) + "s.");
                    Thread.sleep(waitMs);
                    continue;
                }
                if (code == 401 || code == 404) {
                    log.log("*whimper* Discord didn't accept the webhook (HTTP " + code + "). Please check the webhook URL. " + response);
                    return;
                }
                log.log("*whimper* Discord answered HTTP " + code + " for the " + what + ": " + response);
                if (code < 500) return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.log("*whimper* couldn't reach Discord (try " + attempt + "): " + e);
            } finally {
                if (con != null) con.disconnect();
            }
            try {
                Thread.sleep(2000L * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.log("*sad puppy eyes* gave up sending the " + what + " after a few tries.");
    }

    /**
     * Discord's file upload format: the JSON goes in "payload_json", each picture in "files[n]".
     */
    static byte[] multipart(String boundary, String json, Map<String, byte[]> files) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String crlf = "\r\n";
            out.write(("--" + boundary + crlf
                    + "Content-Disposition: form-data; name=\"payload_json\"" + crlf
                    + "Content-Type: application/json; charset=utf-8" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.write(crlf.getBytes(StandardCharsets.UTF_8));
            int n = 0;
            for (Map.Entry<String, byte[]> f : files.entrySet()) {
                out.write(("--" + boundary + crlf
                        + "Content-Disposition: form-data; name=\"files[" + (n++) + "]\"; filename=\"" + f.getKey() + "\"" + crlf
                        + "Content-Type: " + (f.getKey().endsWith(".jpg") ? "image/jpeg" : "image/png") + crlf + crlf).getBytes(StandardCharsets.UTF_8));
                out.write(f.getValue());
                out.write(crlf.getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long retryAfterMs(String header) {
        try {
            double seconds = Double.parseDouble(header);
            return Math.max(500L, (long) (seconds * 1000) + 250L);
        } catch (Exception e) {
            return 2000L;
        }
    }

    private static String readBody(InputStream in) {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = is.read(buf)) != -1 && out.size() < 4096) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String field(String name, String value, boolean inline) {
        return "{\"name\":" + str(name) + ",\"value\":" + str(value) + ",\"inline\":" + inline + "}";
    }

    private static String nonEmpty(String s, String fallback) {
        return s == null || s.trim().isEmpty() ? fallback : s;
    }

    /**
     * JSON string literal with escaping.
     */
    static String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
