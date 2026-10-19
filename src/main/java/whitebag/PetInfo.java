package whitebag;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A pet's details, including its abilities. The game doesn't send your own pet's abilities while you play,
 * so (like Puppy) we get them two ways: from pets seen with full details (e.g. in the Pet Yard), and from
 * your character list, which the game server hands out while you're in the Pet Yard or the Daily Quest Room.
 * Everything learned is remembered in pet-cache.txt next to the jar.
 */
final class PetInfo {
    int instanceId = -1, type = -1, skin = -1, rarity = -1, maxLevel = -1;
    String name = "";
    final int[] abilityType = {-1, -1, -1};
    final int[] abilityLevel = {-1, -1, -1};
    /** Feed points put into each ability so far (-1 if unknown). */
    final int[] abilityPoints = {-1, -1, -1};
    /** Feed power of the food you use, for the feeding planner (saved with the pets). */
    static int feedPower = 500;
    long learnedAt;

    boolean hasAbilities() {
        return abilityLevel[0] >= 0 || abilityLevel[1] >= 0 || abilityLevel[2] >= 0;
    }

    // ---------------------------------------------------------------- remembered pets

    private static final String CACHE = "pet-cache.txt";

    /** pets by instance id, and which pet each character has out */
    static final Map<Integer, PetInfo> byInstance = new HashMap<>();
    /** Which pet each character has out, keyed "accountId:charId" (character ids repeat between accounts). */
    static final Map<String, Integer> petOfChar = new HashMap<>();

    static synchronized void load() {
        try {
            for (String line : Files.readAllLines(Paths.get(CACHE), StandardCharsets.UTF_8)) {
                String[] f = line.split("\\|", -1);
                try {
                    if (f[0].equals("feed") && f.length >= 2) {
                        feedPower = Integer.parseInt(f[1]);
                    } else if (f[0].equals("char") && f.length >= 3) {
                        petOfChar.put(f[1], Integer.parseInt(f[2]));
                    } else if (f[0].equals("pet") && f.length >= 14) {
                        PetInfo p = new PetInfo();
                        p.instanceId = Integer.parseInt(f[1]);
                        p.type = Integer.parseInt(f[2]);
                        p.skin = Integer.parseInt(f[3]);
                        p.rarity = Integer.parseInt(f[4]);
                        p.maxLevel = Integer.parseInt(f[5]);
                        for (int i = 0; i < 3; i++) {
                            p.abilityType[i] = Integer.parseInt(f[6 + i * 2]);
                            p.abilityLevel[i] = Integer.parseInt(f[7 + i * 2]);
                        }
                        p.learnedAt = Long.parseLong(f[12]);
                        p.name = f[13];
                        for (int i = 0; i < 3 && 14 + i < f.length; i++) p.abilityPoints[i] = Integer.parseInt(f[14 + i]);
                        byInstance.put(p.instanceId, p);
                    }
                } catch (Exception ignored) {
                    // skip a bad line
                }
            }
        } catch (Exception ignored) {
            // no cache yet
        }
    }

    static synchronized void save() {
        List<String> lines = new ArrayList<>();
        lines.add("feed|" + feedPower);
        for (Map.Entry<String, Integer> e : petOfChar.entrySet()) lines.add("char|" + e.getKey().replace('|', '/') + "|" + e.getValue());
        for (PetInfo p : byInstance.values()) {
            StringBuilder sb = new StringBuilder("pet|").append(p.instanceId).append('|').append(p.type).append('|').append(p.skin)
                    .append('|').append(p.rarity).append('|').append(p.maxLevel);
            for (int i = 0; i < 3; i++) sb.append('|').append(p.abilityType[i]).append('|').append(p.abilityLevel[i]);
            sb.append('|').append(p.learnedAt).append('|').append(p.name.replace('|', '/').replace('\n', ' '));
            for (int i = 0; i < 3; i++) sb.append('|').append(p.abilityPoints[i]);
            lines.add(sb.toString());
        }
        try {
            Files.write(Paths.get(CACHE), lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    /** Remembers a pet; returns true if it changed what we knew. */
    static synchronized boolean remember(PetInfo p) {
        if (p.instanceId < 0 || !p.hasAbilities()) return false;
        PetInfo old = byInstance.get(p.instanceId);
        if (old != null && old.same(p)) return false;
        byInstance.put(p.instanceId, p);
        save();
        return true;
    }

    private boolean same(PetInfo o) {
        for (int i = 0; i < 3; i++) if (abilityPoints[i] != o.abilityPoints[i]) return false;
        if (skin != o.skin || rarity != o.rarity || maxLevel != o.maxLevel) return false;
        for (int i = 0; i < 3; i++) if (abilityType[i] != o.abilityType[i] || abilityLevel[i] != o.abilityLevel[i]) return false;
        return true;
    }

    // ---------------------------------------------------------------- feeding planner (maths from Puppy)

    private static final double[] FEED_MULT = {1.0, 0.65, 0.3};

    /** Fame cost of one feed, by the pet's max level. */
    static int feedFame(int maxLevel) {
        switch (maxLevel) {
            case 30: return 15;
            case 50: return 50;
            case 70: return 175;
            case 90: return 625;
            case 100: return 1750;
            default: return -1;
        }
    }

    /** Whether the ability slot is unlocked: the 2nd at max level 50+, the 3rd at 90+. */
    boolean unlocked(int slot) {
        return slot == 0 || (slot == 1 && maxLevel >= 50) || (slot == 2 && maxLevel >= 90);
    }

    /** Feed points still needed to take this ability to the pet's max level, or -1 if unknown. */
    int feedPointsToMax(int slot) {
        if (abilityPoints[slot] < 0 || maxLevel <= 0) return -1;
        double m = FEED_MULT[slot];
        double have = abilityPoints[slot] / m;
        double need = (20 / m) * (Math.pow(1.08, maxLevel - 1) - 1) / 0.08;
        return (int) Math.max(0, Math.ceil(need - have));
    }

    static synchronized void setFeedPower(int power) {
        feedPower = power;
        save();
    }

    // ---------------------------------------------------------------- character list from the game server

    /**
     * Asks the game server for your character list using the login token your game client already uses
     * (the same request Puppy makes). The token is only kept in memory and only sent to the game's own server.
     *
     * @return number of characters whose pet was read, or -1 on failure
     */
    static int fetchFromAccount(String accessToken, java.util.function.Supplier<String> accountId) {
        try {
            String params = "do_login=true&accessToken=" + URLEncoder.encode(accessToken, "UTF-8")
                    + "&game_net=Unity&play_platform=Unity&game_net_user_id";
            URL url = new URL("https://www.realmofthemadgod.com/char/list?" + params);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(10000);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            con.setDoOutput(true);
            try (OutputStream os = con.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }
            if (con.getResponseCode() != HttpURLConnection.HTTP_OK) return -1;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = con.getInputStream()) {
                byte[] b = new byte[8192];
                for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
            }
            return parseCharList(buf.toByteArray(), accountId.get());
        } catch (Exception e) {
            return -1;
        }
    }

    static int parseCharList(byte[] xml, String accountId) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setExpandEntityReferences(false);
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
        }
        Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        NodeList chars = doc.getElementsByTagName("Char");
        int found = 0;
        long now = System.currentTimeMillis();
        synchronized (PetInfo.class) {
            for (int c = 0; c < chars.getLength(); c++) {
                Element ch = (Element) chars.item(c);
                int charId = num(ch.getAttribute("id"));
                NodeList pets = ch.getElementsByTagName("Pet");
                if (charId < 0 || pets.getLength() == 0) continue;
                Element pe = (Element) pets.item(0);
                PetInfo p = new PetInfo();
                p.instanceId = num(pe.getAttribute("instanceId"));
                p.type = num(pe.getAttribute("type"));
                p.skin = num(pe.getAttribute("skin"));
                p.rarity = num(pe.getAttribute("rarity"));
                p.maxLevel = num(pe.getAttribute("maxAbilityPower"));
                p.name = pe.getAttribute("name");
                NodeList abs = pe.getElementsByTagName("Ability");
                for (int i = 0; i < Math.min(3, abs.getLength()); i++) {
                    Element a = (Element) abs.item(i);
                    p.abilityType[i] = num(a.getAttribute("type"));
                    p.abilityLevel[i] = num(a.getAttribute("power"));
                    p.abilityPoints[i] = num(a.getAttribute("points"));
                }
                p.learnedAt = now;
                if (p.instanceId < 0) continue;
                petOfChar.put(accountId + ":" + charId, p.instanceId);
                byInstance.put(p.instanceId, p);
                found++;
            }
            save();
        }
        return found;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
