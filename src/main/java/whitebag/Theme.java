package whitebag;

import javax.swing.UIManager;
import java.awt.Color;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Light or dark mode. The app's colours are written for light mode; in dark mode {@link #c} turns each one into its
 * night-time version (pale backgrounds go dark, dark text goes light, bright accents stay bright). Chosen once when the
 * pup opens ("theme.dark" in whitebag-notifier.properties), so switching restarts the pup.
 */
final class Theme {
    private Theme() {
    }

    static final boolean DARK = readDark();

    private static boolean readDark() {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream("whitebag-notifier.properties")) {
            p.load(in);
        } catch (Exception ignored) {
        }
        return "true".equalsIgnoreCase(p.getProperty("theme.dark", "false").trim());
    }

    /** Hand-picked night colours for the main surfaces and text, so they look just right. */
    private static final Map<Integer, Integer> NIGHT = new HashMap<>();

    static {
        NIGHT.put(0xFFFFFF, 0x241D2E); // white: cards and sheets
        NIGHT.put(0x000000, 0xEEE7F7); // black text
        NIGHT.put(0xE9E2F3, 0x16121D); // page
        NIGHT.put(0xFFF4F7, 0x1A1521); // cute background
        NIGHT.put(0x3A2B4E, 0xF1EAFA); // ink
        NIGHT.put(0x5B4A5E, 0xE4D9EA); // cute text
        NIGHT.put(0xD5C9E4, 0x4A3F5C); // lines
        NIGHT.put(0xDCD2EA, 0x2C2438); // tabs that aren't open
        NIGHT.put(0xEFE9F7, 0x382E47); // tab under the mouse
        NIGHT.put(0xF1ECF7, 0x2F273B); // bar tracks
        NIGHT.put(0xFFE2EE, 0x4D2440); // soft pink fill
        NIGHT.put(0xF8D7E3, 0x3D3047); // card edge
        NIGHT.put(0xF2C9D8, 0x0E0B13); // card shadow
        NIGHT.put(0xFFF9FB, 0x1E1826); // text fields
        NIGHT.put(0xF4F2F7, 0x2A2334); // locked / empty chips
        NIGHT.put(0xE2DCEA, 0x463C55); // their edges
        NIGHT.put(0x9C92AC, 0x9D93AE); // greyed text stays readable
        NIGHT.put(0x8A7F99, 0xADA2BF); // quiet text
        NIGHT.put(0xA8909F, 0xB4A2AF); // cute muted text
        NIGHT.put(0x84779A, 0xB0A5C2);
        NIGHT.put(0xD63F7E, 0xFF7FB2); // pink text
        NIGHT.put(0x1F7A67, 0x5FD6B8); // teal text
        NIGHT.put(0x1F8A70, 0x5FD6B8);
        NIGHT.put(0x1F8A72, 0x5FD6B8);
        NIGHT.put(0xB0305F, 0xFF7FAE);
    }

    static final Color WHITE = c(0xFFFFFF), BLACK = c(0x000000);

    static Color c(int rgb) {
        return c(new Color(rgb));
    }

    static Color c(int r, int g, int b, int a) {
        return c(new Color(r, g, b, a));
    }

    /** This colour as it should look in the current mode. */
    static Color c(Color col) {
        if (!DARK || col == null) return col;
        int a = col.getAlpha();
        Integer n = NIGHT.get(col.getRGB() & 0xFFFFFF);
        if (n != null) return new Color((n & 0xFFFFFF) | (a << 24), true);
        float[] hsl = hsl(col);
        float h = hsl[0], s = hsl[1], l = hsl[2];
        if (l >= 0.86f || (s < 0.5f && l >= 0.74f)) { // pale backgrounds, fills and borders go dark
            l = 0.12f + (1 - l) * 0.95f;
            s *= 0.55f;
        } else if (l <= 0.40f) { // dark text and outlines go light
            l = 0.93f - l * 0.55f;
            s *= 0.8f;
        } else { // bright accents stay bright, just a touch softer
            l = Math.min(l, 0.72f);
            s *= 0.92f;
        }
        Color out = fromHsl(h, s, l);
        return new Color(out.getRed(), out.getGreen(), out.getBlue(), a);
    }

    /** "#1F8A70" for HTML in labels and tooltips. */
    static String hex(int rgb) {
        Color c = c(rgb);
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static float[] hsl(Color c) {
        float r = c.getRed() / 255f, g = c.getGreen() / 255f, b = c.getBlue() / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2, h = 0, s = 0;
        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d / (2 - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6;
        }
        return new float[]{h, s, l};
    }

    private static Color fromHsl(float h, float s, float l) {
        if (s == 0) {
            int v = Math.round(l * 255);
            return new Color(v, v, v);
        }
        float q = l < 0.5f ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        return new Color(clamp(hue(p, q, h + 1f / 3)), clamp(hue(p, q, h)), clamp(hue(p, q, h - 1f / 3)));
    }

    private static float hue(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6) return p + (q - p) * 6 * t;
        if (t < 1f / 2) return q;
        if (t < 2f / 3) return p + (q - p) * (2f / 3 - t) * 6;
        return p;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255)));
    }

    /** Dark colours for the plain Swing bits (dialogs, file picker, tooltips, scroll bars, menus). */
    static void installUi() {
        if (!DARK) return;
        Color bg = c(0xE9E2F3), card = WHITE, text = BLACK, line = c(0xD5C9E4), field = c(0xFFF9FB), sel = c(0xFFE2EE);
        String[] backs = {"Panel.background", "OptionPane.background", "Viewport.background", "ScrollPane.background",
                "ScrollBar.background", "ScrollBar.track", "TabbedPane.background", "SplitPane.background", "ToolBar.background",
                "Button.background", "CheckBox.background", "RadioButton.background", "ToggleButton.background", "Label.background",
                "MenuBar.background", "PopupMenu.background", "Menu.background", "MenuItem.background", "FileChooser.background"};
        for (String k : backs) UIManager.put(k, bg);
        String[] fores = {"Panel.foreground", "OptionPane.messageForeground", "OptionPane.foreground", "Label.foreground",
                "Button.foreground", "CheckBox.foreground", "RadioButton.foreground", "ToggleButton.foreground", "TitledBorder.titleColor",
                "Menu.foreground", "MenuItem.foreground", "List.foreground", "Table.foreground", "Tree.textForeground",
                "TextField.foreground", "TextArea.foreground", "TextPane.foreground", "EditorPane.foreground", "ComboBox.foreground",
                "FormattedTextField.foreground", "PasswordField.foreground", "Spinner.foreground", "TableHeader.foreground"};
        for (String k : fores) UIManager.put(k, text);
        String[] fields = {"TextField.background", "TextArea.background", "TextPane.background", "EditorPane.background",
                "FormattedTextField.background", "PasswordField.background", "List.background", "Table.background", "Tree.background",
                "Tree.textBackground", "ComboBox.background", "Spinner.background", "TableHeader.background"};
        for (String k : fields) UIManager.put(k, field);
        String[] sels = {"TextField.selectionBackground", "TextArea.selectionBackground", "TextPane.selectionBackground",
                "List.selectionBackground", "Table.selectionBackground", "Tree.selectionBackground", "ComboBox.selectionBackground",
                "MenuItem.selectionBackground", "Menu.selectionBackground", "EditorPane.selectionBackground"};
        for (String k : sels) UIManager.put(k, sel);
        String[] selFore = {"TextField.selectionForeground", "TextArea.selectionForeground", "List.selectionForeground",
                "Table.selectionForeground", "Tree.selectionForeground", "ComboBox.selectionForeground", "MenuItem.selectionForeground"};
        for (String k : selFore) UIManager.put(k, text);
        for (String k : new String[]{"TextField.caretForeground", "TextArea.caretForeground", "TextPane.caretForeground", "EditorPane.caretForeground"})
            UIManager.put(k, c(0xD63F7E));
        UIManager.put("ToolTip.background", card);
        UIManager.put("ToolTip.foreground", text);
        UIManager.put("ToolTip.border", javax.swing.BorderFactory.createLineBorder(line));
        UIManager.put("ScrollBar.thumb", line);
        UIManager.put("ScrollBar.thumbHighlight", line);
        UIManager.put("ScrollBar.thumbShadow", line);
        UIManager.put("ScrollBar.thumbDarkShadow", bg);
        UIManager.put("Separator.foreground", line);
        UIManager.put("control", bg);
        UIManager.put("controlText", text);
        UIManager.put("text", field);
        UIManager.put("textText", text);
        UIManager.put("window", bg);
        UIManager.put("info", card);
        UIManager.put("infoText", text);
        UIManager.put("Table.gridColor", line);
    }
}
