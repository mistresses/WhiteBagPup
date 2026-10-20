package whitebag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        if (drop.newToCollection != null && !drop.newToCollection.isEmpty()) {
            desc.append("\n\u2728 **NEW to your collection:** ").append(String.join(", ", drop.newToCollection)).append(" \u2728\n");
        }
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

    /** Tries per post: one retry, and only when the other end couldn't be reached or had a hiccup. */
    private static final int TRIES = 2;

    private void post(String url, String json, Map<String, byte[]> files, String what) {
        String boundary = "----WhiteBag" + Long.toHexString(System.nanoTime());
        byte[] body = files.isEmpty() ? json.getBytes(StandardCharsets.UTF_8) : multipart(boundary, json, files);
        String contentType = files.isEmpty() ? "application/json; charset=utf-8" : "multipart/form-data; boundary=" + boundary;
        boolean discord = isValidUrl(url);
        String who = discord ? "Discord" : "the bot (" + host(url) + ")";
        for (int attempt = 1; attempt <= TRIES; attempt++) {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(url.trim()).openConnection();
                con.setRequestMethod("POST");
                con.setConnectTimeout(10_000);
                con.setReadTimeout(30_000);
                con.setDoOutput(true);
                con.setFixedLengthStreamingMode(body.length);
                con.setRequestProperty("Content-Type", contentType);
                con.setRequestProperty("User-Agent", "WhiteBagPup/" + UpdateCheck.VERSION);
                IOException writeError = null;
                try (OutputStream os = con.getOutputStream()) {
                    os.write(body);
                } catch (IOException e) {
                    writeError = e; // the other end may have answered early (e.g. a wrong key): read that answer below
                }
                int code;
                try {
                    code = con.getResponseCode();
                } catch (IOException e) {
                    throw writeError != null ? writeError : e;
                }
                if (code >= 200 && code < 300 && writeError == null) {
                    log.log("*drops it at " + (discord ? "Discord's" : "the bot's") + " feet* sent the " + what + "! *proud tail wag*");
                    return;
                }
                String response = readBody(code >= 400 ? con.getErrorStream() : con.getInputStream());
                if (response.isEmpty() && code >= 400) {
                    try {
                        response = readBody(con.getInputStream());
                    } catch (IOException ignored) {
                    }
                }
                if (code == 429 && attempt < TRIES) {
                    long waitMs = Math.min(retryAfterMs(con.getHeaderField("Retry-After")), 30_000);
                    log.log("*waits patiently* " + who + " says slow down, trying once more in " + (waitMs / 1000) + "s.");
                    Thread.sleep(waitMs);
                    continue;
                }
                log.log("*whimper* couldn't send the " + what + ": " + who + " answered HTTP " + code
                        + (response.isEmpty() ? "" : " (" + response + ")") + ". " + hint(discord, code, body.length));
                if (code < 500 || attempt == TRIES) return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (attempt == TRIES) {
                    log.log("*whimper* couldn't send the " + what + " to " + who + ": " + e.getMessage() + ". "
                            + hint(discord, -1, body.length));
                    return;
                }
            } finally {
                if (con != null) con.disconnect();
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static String host(String url) {
        try {
            return new URL(url.trim()).getHost();
        } catch (Exception e) {
            return "?";
        }
    }

    /** What probably went wrong, in plain words. code -1: no answer at all. */
    static String hint(boolean discord, int code, int bytes) {
        String size = String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0);
        if (discord) {
            if (code == 401 || code == 404) return "Please check the webhook URL (the webhook may have been deleted).";
            if (code == 413 || code == -1) return "The post was " + size + "; if that's big, Discord may have refused it. Otherwise check your internet.";
            if (code == 400) return "Discord didn't like the post's format.";
            return "Discord may be having trouble; it'll try again next time.";
        }
        switch (code) {
            case 401: return "The key in the recap link doesn't match the bot's RECAP_KEY.";
            case 403: return "The &channel= in the link isn't in the bot's RECAP_CHANNEL_ID.";
            case 404: return "That address isn't the recap inbox: check the link ends in /recap?key=..., and that the bot was redeployed with the new code and has RECAP_KEY set.";
            case 413: return "The pictures were too big for the bot (" + size + ").";
            case 429: return "Too many recaps this hour; try later.";
            case 500: return "The bot isn't set up right yet (see its message in brackets).";
            case 422: return "The bot got it, but Discord refused its post: check the bot can send messages and attach files in that channel.";
            case 502: case 503: case 504:
                return "The bot isn't answering: check it's running on Railway, has RECAP_KEY set, and that its domain's port matches (8080).";
            case -1:
                return "The connection was cut off. Usually the bot isn't answering on that address: check RECAP_KEY is set on Railway, the bot redeployed, and the domain's port is 8080.";
            default: return "";
        }
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
