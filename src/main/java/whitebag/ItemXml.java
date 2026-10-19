package whitebag;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Extra item details read straight from the game files the app extracts (assets/xml): the item's tier,
 * labels and what kind of item it is. The quick item list only keeps the labels, so items whose tier is
 * only in the XML (or that have no tier at all) would otherwise get no badge.
 */
final class ItemXml {
    private ItemXml() {
    }

    static final class Info {
        String tier = "";
        String labels = "";
        String rarity = "";
        int slotType = -1;
        boolean potion, consumable, soulbound;
        Element element;
    }

    private static volatile Map<Integer, Info> table;
    private static final Set<Integer> logged = new HashSet<>();

    /** Every item id in the game files (empty if they aren't there yet). */
    static java.util.Set<Integer> ids() {
        Map<Integer, Info> t = table;
        if (t == null) t = load();
        return java.util.Collections.unmodifiableSet(t.keySet());
    }

    /** Details for an item type, or null if the game files aren't there or don't list it. */
    static Info of(int id) {
        Map<Integer, Info> t = table;
        if (t == null) t = load();
        return t.get(id);
    }

    private static synchronized Map<Integer, Info> load() {
        if (table != null) return table;
        Map<Integer, Info> t = new HashMap<>();
        File[] files = new File("assets/xml").listFiles((d, n) -> n.toLowerCase().endsWith(".xml"));
        if (files != null) {
            for (File f : files) {
                try {
                    if (f.length() > 80_000_000) continue;
                    String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    if (s.contains("<SlotType>")) read(f, t);
                } catch (Throwable ignored) {
                }
            }
        }
        table = t;
        return t;
    }

    private static void read(File f, Map<Integer, Info> out) {
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
                if (child(o, "SlotType") == null) continue; // not an item
                int type;
                try {
                    type = Integer.decode(o.getAttribute("type").trim());
                } catch (Exception e) {
                    continue;
                }
                Info info = new Info();
                info.tier = text(o, "Tier");
                info.labels = text(o, "Labels");
                info.rarity = text(o, "Rarity");
                try {
                    info.slotType = Integer.parseInt(text(o, "SlotType"));
                } catch (Exception ignored) {
                }
                info.potion = child(o, "Potion") != null;
                info.consumable = child(o, "Consumable") != null;
                info.soulbound = child(o, "Soulbound") != null;
                info.element = o;
                out.put(type, info);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Notes an item that still has no badge in item-debug.txt (once per item), with its game data,
     * so it's easy to see what the game says about it.
     */
    static void noteUnbadged(int id, String name, String libraryLabels) {
        synchronized (logged) {
            if (!logged.add(id)) return;
        }
        Info x = of(id);
        StringBuilder sb = new StringBuilder();
        sb.append(id).append(" | ").append(name).append(" | labels: ").append(libraryLabels == null ? "-" : libraryLabels);
        if (x == null) {
            sb.append(" | not in assets/xml");
        } else {
            String xml = xml(x.element).replaceAll("\\s+", " ");
            if (xml.length() > 1500) xml = xml.substring(0, 1500) + "...";
            sb.append(" | ").append(xml);
        }
        try {
            Files.write(Paths.get("item-debug.txt"), Collections.singletonList(sb.toString()), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private static String xml(Element e) {
        try {
            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter w = new StringWriter();
            tf.transform(new DOMSource(e), new StreamResult(w));
            return w.toString();
        } catch (Exception ex) {
            return "?";
        }
    }

    private static Element child(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && ((Element) n).getTagName().equals(tag)) return (Element) n;
        }
        return null;
    }

    private static String text(Element parent, String tag) {
        Element e = child(parent, tag);
        return e == null ? "" : e.getTextContent().trim();
    }
}
