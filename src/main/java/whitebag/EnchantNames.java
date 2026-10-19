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

/** Enchantment names and descriptions from the game files (assets/xml/enchantments.xml). */
final class EnchantNames {
    private EnchantNames() {
    }

    private static volatile Map<Integer, String[]> table;

    /** {name, description} for an enchantment type, or null if unknown. */
    static String[] of(int type) {
        Map<Integer, String[]> t = table;
        if (t == null) t = load();
        return t.get(type);
    }

    /** "Attack Bonus I: Increases Attack by 1.4" (or just the name, or "Unknown enchant (0x1a)"). */
    static String line(int type) {
        String[] d = of(type);
        if (d == null) return String.format("Unknown enchant (0x%x)", type);
        return d[1].isEmpty() ? d[0] : d[0] + ": " + d[1];
    }

    private static synchronized Map<Integer, String[]> load() {
        if (table != null) return table;
        Map<Integer, String[]> t = new HashMap<>();
        File preferred = new File("assets/xml/enchantments.xml");
        if (preferred.isFile()) read(preferred, t);
        if (t.isEmpty()) {
            File[] files = new File("assets/xml").listFiles((d, n) -> n.toLowerCase().endsWith(".xml"));
            if (files != null) {
                for (File f : files) {
                    try {
                        if (f.length() > 50_000_000) continue;
                        String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                        if (s.contains("<Enchantment")) read(f, t);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        table = t;
        return t;
    }

    private static void read(File f, Map<Integer, String[]> out) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            try {
                dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {
            }
            Document doc = dbf.newDocumentBuilder().parse(f);
            NodeList list = doc.getElementsByTagName("Enchantment");
            for (int i = 0; i < list.getLength(); i++) {
                Element e = (Element) list.item(i);
                String typeText = attrOrChild(e, "type");
                if (typeText.isEmpty()) continue;
                int type;
                try {
                    type = Integer.decode(typeText.trim());
                } catch (Exception ex) {
                    continue;
                }
                String name = attrOrChild(e, "DisplayId");
                if (name.isEmpty() || name.startsWith("{")) name = tidy(attrOrChild(e, "id"));
                String desc = attrOrChild(e, "Description");
                if (desc.startsWith("{")) desc = "";
                out.put(type, new String[]{name.isEmpty() ? String.format("Enchant 0x%x", type) : name, desc});
            }
        } catch (Exception ignored) {
        }
    }

    private static String attrOrChild(Element e, String name) {
        String a = e.getAttribute(name);
        if (a != null && !a.isEmpty()) return a.trim();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && ((Element) n).getTagName().equals(name)) return n.getTextContent().trim();
        }
        return "";
    }

    private static String tidy(String id) {
        return id.replace('_', ' ').replaceAll("\\s+", " ").trim();
    }
}
