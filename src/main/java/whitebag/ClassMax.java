package whitebag;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Each class's max stats, read from the game files the app extracts (assets/xml/players.xml),
 * the same place Puppy gets them. Order: HP, MP, ATT, DEF, SPD, DEX, VIT, WIS.
 */
final class ClassMax {
    private ClassMax() {
    }

    private static final String[] TAGS = {"MaxHitPoints", "MaxMagicPoints", "Attack", "Defense", "Speed", "Dexterity", "HpRegen", "MpRegen"};
    private static volatile Map<Integer, int[]> table;

    /** Max stats for a class object type, or null if unknown (e.g. game files not extracted yet). */
    static int[] of(int classType) {
        Map<Integer, int[]> t = table;
        if (t == null) t = load();
        return t.get(classType);
    }

    private static synchronized Map<Integer, int[]> load() {
        if (table != null) return table;
        Map<Integer, int[]> t = new HashMap<>();
        File preferred = new File("assets/xml/players.xml");
        if (preferred.isFile()) read(preferred, t);
        if (t.isEmpty()) {
            // file names can change between game versions: look for any file describing player classes
            File[] files = new File("assets/xml").listFiles((d, n) -> n.toLowerCase().endsWith(".xml"));
            if (files != null) {
                for (File f : files) {
                    try {
                        if (f.length() > 50_000_000) continue;
                        String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                        if (s.contains("<Class>Player</Class>")) read(f, t);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        table = t;
        return t;
    }

    private static void read(File f, Map<Integer, int[]> out) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {
                dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {
            }
            Document doc = dbf.newDocumentBuilder().parse(f);
            NodeList objects = doc.getElementsByTagName("Object");
            for (int i = 0; i < objects.getLength(); i++) {
                Element o = (Element) objects.item(i);
                if (!"Player".equals(childText(o, "Class"))) continue;
                int type;
                try {
                    type = Integer.decode(o.getAttribute("type").trim());
                } catch (Exception e) {
                    continue;
                }
                int[] max = new int[8];
                for (int k = 0; k < TAGS.length; k++) {
                    Element e = child(o, TAGS[k]);
                    try {
                        max[k] = e == null ? 0 : Integer.parseInt(e.getAttribute("max").trim());
                    } catch (Exception ex) {
                        max[k] = 0;
                    }
                }
                out.put(type, max);
            }
        } catch (Exception ignored) {
        }
    }

    private static Element child(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && ((Element) n).getTagName().equals(tag)) return (Element) n;
        }
        return null;
    }

    private static String childText(Element parent, String tag) {
        Element e = child(parent, tag);
        return e == null ? null : e.getTextContent().trim();
    }
}
