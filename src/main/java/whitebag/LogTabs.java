package whitebag;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The Bag log, Chat and DPS pages.
 */
final class LogTabs {
    private LogTabs() {
    }

    static String clock(long ms) {
        return new SimpleDateFormat("h:mm a", Locale.ENGLISH).format(new Date(ms)).toLowerCase(Locale.ROOT);
    }

    /** Swing can't draw colour emoji, so drop them from text shown in the window. */
    static String noEmoji(String s) {
        StringBuilder out = new StringBuilder();
        s.codePoints().filter(cp -> cp < 0x2190 || (cp > 0x2BFF && cp < 0x1F000 && (cp < 0xFE00 || cp > 0xFE0F)))
                .forEach(out::appendCodePoint);
        return out.toString().trim();
    }

    static String shortNum(long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.2fm", n / 1_000_000.0);
        if (n >= 10_000) return String.format(Locale.ROOT, "%.1fk", n / 1000.0);
        return String.format(Locale.ROOT, "%,d", n);
    }

    // ================================================================== shared bits

    /** A panel that tracks the viewport width, so rows wrap instead of scrolling sideways. */
    static class WidthPanel extends JPanel implements Scrollable {
        WidthPanel(LayoutManager l) {
            super(l);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 30; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Empty page: the sleeping pup and one line saying what will appear here. */
    static class Empty extends JComponent {
        private final String line;

        Empty(String line) {
            this.line = line;
            setPreferredSize(new Dimension(400, 220));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Cute.smooth(g);
            int s = 80, x = (getWidth() - s) / 2, y = 40;
            Cute.drawPuppy(g, x, y, s, new Color(0xFFFDFE), false);
            Pixel.text(g, "z", x + s, y + 4, 2, Pixel.MUTED);
            Pixel.text(g, "z", x + s + 12, y - 8, 3, Pixel.LINE);
            Cute.smooth(g);
            g.setFont(Cute.font(Font.PLAIN, 13));
            g.setColor(Pixel.MUTED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(line, (getWidth() - fm.stringWidth(line)) / 2, y + s + 34);
            g.dispose();
        }
    }

    /** One-of-several filter: small square segments, the chosen one in ink. */
    static class Segments extends JPanel {
        interface Pick {
            void picked(int i);
        }

        Segments(String[] labels, Pick pick) {
            this(labels, 0, pick);
        }

        /** With {@code selected} picked to start with. */
        Segments(String[] labels, int selected, Pick pick) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setOpaque(false);
            ButtonGroup group = new ButtonGroup();
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                JToggleButton b = new JToggleButton(labels[i], i == selected) {
                    @Override
                    protected void paintComponent(Graphics g0) {
                        Graphics2D g = (Graphics2D) g0.create();
                        boolean on = isSelected();
                        Pixel.frame(g, 0, 0, getWidth(), getHeight(), on ? Pixel.INK : Pixel.SHEET, on ? Pixel.INK : Pixel.LINE, 2);
                        if (isFocusOwner()) {
                            g.setColor(Pixel.PINK);
                            g.fillRect(6, getHeight() - 5, getWidth() - 12, 2);
                        }
                        Cute.smooth(g);
                        g.setFont(getFont());
                        FontMetrics fm = g.getFontMetrics();
                        g.setColor(on ? Color.WHITE : Pixel.INK);
                        g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                        g.dispose();
                    }
                };
                b.setFont(Cute.font(Font.BOLD, 12));
                b.setContentAreaFilled(false);
                b.setBorderPainted(false);
                b.setFocusPainted(false);
                b.setOpaque(false);
                b.setBorder(new EmptyBorder(7, 11, 7, 11));
                b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                b.addActionListener(e -> { Squeak.boop(); pick.picked(idx); });
                group.add(b);
                add(b);
            }
        }
    }

    // ================================================================== Bag log

    static class BagLogPanel extends JPanel {
        private final WidthPanel list = new WidthPanel(null);
        private final JLabel count = Pixel.note("");
        private final List<BagDrop> bags = new ArrayList<>();
        private final CardLayout cards = new CardLayout();
        private final JPanel body = new JPanel(cards);

        BagLogPanel() {
            super(new BorderLayout(0, 14));
            setOpaque(false);
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            list.setBackground(Pixel.SHEET);
            list.setBorder(new EmptyBorder(4, 14, 10, 14));
            body.setOpaque(false);
            JPanel empty = new JPanel(new BorderLayout());
            empty.setBackground(Pixel.SHEET);
            empty.add(new Empty("No bags yet. Each one the pup barks about lands here."), BorderLayout.CENTER);
            body.add(Pixel.scroll(empty), "empty");
            body.add(Pixel.scroll(list), "list");
            add(count, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
            refresh();
        }

        void add(BagDrop drop) {
            bags.add(drop);
            BagRow row = new BagRow(drop, false);
            row.divider = list.getComponentCount() > 0; // newest goes on top, so it sits above an older row
            list.add(row, 0);
            refresh();
            list.revalidate();
            list.repaint();
        }

        private void refresh() {
            int n = bags.size();
            count.setText(n == 0 ? "Bags the pup barks about this session." : n + (n == 1 ? " bag" : " bags") + " this session, newest first.");
            cards.show(body, n == 0 ? "empty" : "list");
        }
    }

    /** One bag: an inventory slot with the bag, where and when, then its items as slots. */
    static class BagRow extends JComponent {
        private static final int SLOT = 44;
        private final BagDrop drop;
        private final List<ItemInfo> items;
        private final BufferedImage bagIcon;
        private final List<BufferedImage> icons = new ArrayList<>();
        boolean divider;

        BagRow(BagDrop drop, boolean unused) {
            this.drop = drop;
            this.items = drop.items();
            BufferedImage bi = null;
            try {
                bi = LootImage.pixelIcon(LootImage.sprites.sprite(drop.bagObjectType()), 44);
            } catch (Throwable ignored) {
            }
            bagIcon = bi;
            for (ItemInfo i : items) {
                BufferedImage ic = null;
                try {
                    ic = LootImage.pixelIcon(LootImage.sprites.sprite(i.id), 32);
                } catch (Throwable ignored) {
                }
                icons.add(ic);
            }
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(400, (drop.note == null ? 124 : 142));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            int w = getWidth(), h = getHeight();
            if (divider) {
                g.setColor(Pixel.TRACK);
                g.fillRect(0, h - 2, w, 2);
            }
            int top = 14;

            // the bag, in a slot
            Pixel.frame(g, 0, top, 60, 60, Pixel.PINK_SOFT, Pixel.LINE, 2);
            if (bagIcon != null) g.drawImage(bagIcon, (60 - bagIcon.getWidth()) / 2, top + (60 - bagIcon.getHeight()) / 2, null);
            else Cute.drawBag(g, 10, top + 8, 40, new Color(0xFFFDFE), false, false);

            int tx = 76;
            Cute.smooth(g);
            g.setColor(Pixel.INK);
            g.setFont(Cute.font(Font.BOLD, 15));
            g.drawString(drop.bagName + " bag", tx, top + 15);
            g.setFont(Cute.font(Font.PLAIN, 12));
            g.setColor(Pixel.MUTED);
            String where = (drop.mapName.isEmpty() ? "Somewhere" : drop.mapName) + " at " + clock(drop.droppedAt * 1000L)
                    + (drop.playerName.isEmpty() ? "" : ", on " + drop.playerName);
            g.drawString(where, tx, top + 33);
            int y = top + 44;
            if (drop.note != null) {
                g.setColor(Pixel.PINK_INK);
                g.setFont(Cute.font(Font.BOLD, 12));
                g.drawString(noEmoji(drop.note), tx, top + 50);
                y = top + 60;
            }

            int x = tx;
            for (int i = 0; i < items.size(); i++) {
                ItemInfo item = items.get(i);
                boolean special = !item.rarity.isEmpty();
                boolean enchanted = item.enchant != Enchant.NONE;
                Color edge = enchanted ? item.enchant.color : special ? item.rarityColor : Pixel.LINE;
                Pixel.frame(g, x, y, SLOT, SLOT, enchanted || special ? Cute.mix(Color.WHITE, edge, 0.14) : Pixel.SHEET, edge, 2);
                BufferedImage ic = icons.get(i);
                if (ic != null) {
                    g.drawImage(ic, x + (SLOT - ic.getWidth()) / 2, y + (SLOT - ic.getHeight()) / 2, null);
                } else {
                    Cute.smooth(g);
                    g.setFont(Cute.font(Font.BOLD, 9));
                    g.setColor(Pixel.INK);
                    g.drawString(item.name.length() > 7 ? item.name.substring(0, 7) : item.name, x + 4, y + SLOT / 2 + 3);
                }
                if (enchanted) {
                    // one dot per enchantment slot, in the rarity's colour (like the game's slot icon)
                    for (int k = 0; k < item.enchant.ordinal(); k++) {
                        g.setColor(Color.WHITE);
                        g.fillRect(x + 3 + k * 7, y + 3, 6, 6);
                        g.setColor(item.enchant.color);
                        g.fillRect(x + 4 + k * 7, y + 4, 4, 4);
                    }
                }
                if (special) {
                    // rarity tag hanging off the slot's bottom edge
                    Cute.smooth(g);
                    g.setFont(Cute.font(Font.BOLD, 9));
                    FontMetrics fm = g.getFontMetrics();
                    int bw = fm.stringWidth(item.rarity) + 8;
                    Pixel.frame(g, x + SLOT - bw + 3, y + SLOT - 9, bw, 14, item.rarityColor, null, 0);
                    g.setColor(Color.WHITE);
                    g.drawString(item.rarity, x + SLOT - bw + 7, y + SLOT + 1);
                }
                x += SLOT + 6;
            }
            Cute.smooth(g);
            if (items.isEmpty()) {
                g.setFont(Cute.font(Font.PLAIN, 12));
                g.setColor(Pixel.MUTED);
                g.drawString("It left view before its contents arrived.", tx, y + 18);
            } else {
                g.setFont(Cute.font(Font.PLAIN, 12));
                FontMetrics fm = g.getFontMetrics();
                StringBuilder names = new StringBuilder();
                for (ItemInfo i : items) {
                    if (names.length() > 0) names.append(", ");
                    String tags = (i.rarity.isEmpty() ? "" : i.rarity)
                            + (i.enchant == Enchant.NONE ? "" : (i.rarity.isEmpty() ? "" : ", ") + i.enchant.label);
                    names.append(i.name).append(tags.isEmpty() ? "" : " (" + tags + ")");
                }
                String line = names.toString();
                int max = w - tx - 8;
                while (fm.stringWidth(line) > max && line.length() > 4) line = line.substring(0, line.length() - 2);
                if (!line.equals(names.toString())) line = line.trim() + "…";
                g.setColor(Pixel.INK);
                g.drawString(line, tx, y + SLOT + 20);
            }
            g.dispose();
        }
    }

    // ================================================================== Chat

    static class ChatPanel extends JPanel {
        private static final String[] FILTERS = {"All", "Guild", "Party", "Whispers", "NPCs"};
        private final ChatLog log;
        private final JTextPane pane = new JTextPane();
        private final JTextField search = Pixel.field("", 12, "Search chat");
        private int filter = 0;
        private boolean placeholder = false;
        private final SimpleAttributeSet time = style(Pixel.MUTED, false, false);
        private final SimpleAttributeSet body = style(Pixel.INK, false, false);
        private final SimpleAttributeSet nameStyle = style(Pixel.INK, true, false);
        private final SimpleAttributeSet mine = style(Pixel.PINK_INK, true, false);
        private final SimpleAttributeSet npc = style(new Color(0xB4642A), true, false);
        private final SimpleAttributeSet npcText = style(new Color(0x8A6650), false, true);
        private final SimpleAttributeSet guild = style(new Color(0x1F8A72), true, false);
        private final SimpleAttributeSet party = style(new Color(0x6A4BD6), true, false);
        private final SimpleAttributeSet whisper = style(Pixel.PINK_INK, true, false);
        private final SimpleAttributeSet highlight = style(Pixel.INK, false, false);

        ChatPanel(ChatLog log) {
            super(new BorderLayout(0, 12));
            this.log = log;
            setOpaque(false);
            StyleConstants.setBackground(highlight, Pixel.PINK_SOFT);

            pane.setEditable(false);
            pane.setBackground(Pixel.SHEET);
            pane.setBorder(new EmptyBorder(10, 12, 10, 12));
            pane.setFont(Cute.font(Font.PLAIN, 13));

            JPanel top = new JPanel(new BorderLayout(10, 0));
            top.setOpaque(false);
            top.add(new Segments(FILTERS, i -> {
                filter = i;
                rebuild();
            }), BorderLayout.CENTER);
            top.add(search, BorderLayout.EAST);
            search.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { rebuild(); }
                public void removeUpdate(DocumentEvent e) { rebuild(); }
                public void changedUpdate(DocumentEvent e) { rebuild(); }
            });
            add(top, BorderLayout.NORTH);
            add(Pixel.scroll(pane), BorderLayout.CENTER);
            rebuild();
        }

        private static SimpleAttributeSet style(Color c, boolean bold, boolean italic) {
            SimpleAttributeSet s = new SimpleAttributeSet();
            StyleConstants.setForeground(s, c);
            StyleConstants.setBold(s, bold);
            StyleConstants.setItalic(s, italic);
            StyleConstants.setFontFamily(s, Cute.FONT);
            StyleConstants.setFontSize(s, 13);
            StyleConstants.setLineSpacing(s, 0.15f);
            return s;
        }

        /** Called on the Swing thread for each new line. */
        void added(ChatLog.Line line) {
            if (!matches(line)) return;
            JScrollBar bar = ((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, pane)).getVerticalScrollBar();
            boolean atBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 30;
            append(line);
            trim();
            if (atBottom) pane.setCaretPosition(pane.getDocument().getLength());
        }

        private boolean matches(ChatLog.Line l) {
            String me = log.myName();
            switch (filter) {
                case 1: if (l.kind != ChatLog.Kind.GUILD) return false; break;
                case 2: if (l.kind != ChatLog.Kind.PARTY) return false; break;
                case 3: if (l.kind != ChatLog.Kind.WHISPER) return false; break;
                case 4: if (l.kind != ChatLog.Kind.SYSTEM) return false; break;
                default:
            }
            String q = search.getText().trim().toLowerCase(Locale.ROOT);
            return q.isEmpty() || l.text.toLowerCase(Locale.ROOT).contains(q) || l.name.toLowerCase(Locale.ROOT).contains(q)
                    || l.other.toLowerCase(Locale.ROOT).contains(q);
        }

        private void rebuild() {
            pane.setText("");
            placeholder = false;
            List<ChatLog.Line> shown = new ArrayList<>();
            for (ChatLog.Line l : log.lines()) if (matches(l)) shown.add(l);
            for (ChatLog.Line l : shown.subList(Math.max(0, shown.size() - 1500), shown.size())) append(l);
            if (shown.isEmpty()) {
                boolean filtered = filter != 0 || !search.getText().trim().isEmpty();
                put(filtered ? "No messages match this filter yet." : "Chat shows up here once you're in game and watching.", time);
                placeholder = true;
            }
            pane.setCaretPosition(pane.getDocument().getLength());
        }

        private void append(ChatLog.Line l) {
            if (placeholder) {
                pane.setText("");
                placeholder = false;
            }
            boolean aboutMe = !l.fromMe && l.aboutMe(log.myName());
            put(clock(l.timeMs) + "   ", time);
            switch (l.kind) {
                case GUILD: put("Guild  ", guild); break;
                case PARTY: put("Party  ", party); break;
                case WHISPER: put(l.fromMe ? "To " + l.other + "  " : "From " + l.other + "  ", whisper); break;
                default:
            }
            if (l.kind == ChatLog.Kind.SYSTEM) {
                if (!l.name.isEmpty()) put(l.name + ": ", npc);
                put(l.text, npcText);
            } else {
                if (l.kind != ChatLog.Kind.WHISPER || l.fromMe) put(l.name + ": ", l.fromMe ? mine : nameStyle);
                put(l.text, aboutMe ? highlight : body);
            }
            put("\n", body);
        }

        private void put(String s, SimpleAttributeSet a) {
            try {
                StyledDocument d = pane.getStyledDocument();
                d.insertString(d.getLength(), s, a);
            } catch (BadLocationException ignored) {
            }
        }

        private void trim() {
            StyledDocument d = pane.getStyledDocument();
            javax.swing.text.Element root = d.getDefaultRootElement();
            int extra = root.getElementCount() - 1600;
            if (extra > 0) {
                try {
                    d.remove(0, root.getElement(extra - 1).getEndOffset());
                } catch (BadLocationException ignored) {
                }
            }
        }
    }

    // ================================================================== DPS

    static class DpsPanel extends JPanel {
        private final DpsTracker tracker;
        private final JLabel runLabel = Pixel.label("");
        private final Pixel.Button older = new Pixel.Button("Older run", false);
        private final Pixel.Button newer = new Pixel.Button("Newer run", false);
        private final Pixel.Button copy = new Pixel.Button("Copy results", false);
        private final WidthPanel boards = new WidthPanel(null);
        private final CardLayout cards = new CardLayout();
        private final JPanel body = new JPanel(cards);
        private int index = 0;
        private final Timer refresh;
        /** 0 = everything, 1 = bosses only, 2 = minibosses only */
        private int kindFilter = 0;
        private static final String[] KIND_FILTERS = {"All", "Bosses", "Minibosses"};

        DpsPanel(DpsTracker tracker) {
            super(new BorderLayout(0, 12));
            this.tracker = tracker;
            setOpaque(false);
            boards.setLayout(new BoxLayout(boards, BoxLayout.Y_AXIS));
            boards.setBackground(Pixel.SHEET);
            boards.setBorder(new EmptyBorder(12, 14, 12, 14));
            runLabel.setFont(Cute.font(Font.BOLD, 14));
            for (Pixel.Button b : new Pixel.Button[]{older, newer, copy}) {
                b.setFont(Cute.font(Font.BOLD, 12));
                b.setBorder(new EmptyBorder(6, 11, 9, 11));
            }
            older.addActionListener(e -> { index++; rebuild(); });
            newer.addActionListener(e -> { index--; rebuild(); });
            copy.addActionListener(e -> copyText());

            JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            nav.setOpaque(false);
            nav.add(older);
            nav.add(newer);
            nav.add(copy);
            JPanel bar = new JPanel(new BorderLayout());
            bar.setOpaque(false);
            bar.add(runLabel, BorderLayout.WEST);
            bar.add(nav, BorderLayout.EAST);
            JPanel top = new JPanel(new BorderLayout(0, 8));
            top.setOpaque(false);
            top.add(bar, BorderLayout.NORTH);
            top.add(new Segments(KIND_FILTERS, i -> {
                kindFilter = i;
                rebuild();
            }), BorderLayout.SOUTH);

            body.setOpaque(false);
            JPanel empty = new JPanel(new BorderLayout());
            empty.setBackground(Pixel.SHEET);
            empty.add(new Empty("Damage shows up here after your first fight."), BorderLayout.CENTER);
            body.add(Pixel.scroll(empty), "empty");
            body.add(Pixel.scroll(boards), "boards");
            add(top, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
            add(Pixel.note("DPS = damage per second. Hits taken / Dmg taken = times hit and damage received."), BorderLayout.SOUTH);

            refresh = new Timer(500, e -> rebuild());
            refresh.setRepeats(false);
            rebuild();
        }

        /** Called from the tracker (any thread). */
        void changed() {
            SwingUtilities.invokeLater(() -> {
                if (!refresh.isRunning()) refresh.restart();
            });
        }

        private void rebuild() {
            List<DpsTracker.Run> runs = tracker.runs();
            boolean any = !runs.isEmpty();
            older.setVisible(any);
            newer.setVisible(any);
            copy.setVisible(any);
            if (!any) {
                runLabel.setText("");
                cards.show(body, "empty");
                return;
            }
            index = Math.max(0, Math.min(index, runs.size() - 1));
            DpsTracker.Run run = runs.get(index);
            runLabel.setText(run.mapName + ", started " + clock(run.startMs) + (runs.size() > 1 ? "  (run " + (runs.size() - index) + " of " + runs.size() + ")" : ""));
            older.setEnabled(index < runs.size() - 1);
            newer.setEnabled(index > 0);
            boards.removeAll();
            List<DpsTracker.Board> list = filtered(tracker.boards(run));
            for (int i = 0; i < list.size(); i++) {
                boards.add(new BoardView(list.get(i), i > 0));
            }
            if (list.isEmpty()) {
                JLabel none = Pixel.note(kindFilter == 1 ? "No bosses fought in this run." : "No minibosses fought in this run.");
                none.setBorder(new EmptyBorder(8, 0, 0, 0));
                boards.add(none);
            }
            boards.revalidate();
            boards.repaint();
            cards.show(body, "boards");
        }

        private List<DpsTracker.Board> filtered(List<DpsTracker.Board> all) {
            if (kindFilter == 0) return all;
            DpsTracker.Kind want = kindFilter == 1 ? DpsTracker.Kind.BOSS : DpsTracker.Kind.MINIBOSS;
            List<DpsTracker.Board> out = new ArrayList<>();
            for (DpsTracker.Board b : all) if (b.kind == want) out.add(b);
            return out;
        }

        private void copyText() {
            List<DpsTracker.Run> runs = tracker.runs();
            if (runs.isEmpty()) return;
            DpsTracker.Run run = runs.get(Math.max(0, Math.min(index, runs.size() - 1)));
            StringBuilder sb = new StringBuilder();
            sb.append(run.mapName).append(", ").append(clock(run.startMs)).append('\n');
            for (DpsTracker.Board b : filtered(tracker.boards(run))) {
                sb.append('\n').append(b.title);
                if (b.maxHp > 0) sb.append(" (").append(shortNum(b.maxHp)).append(" HP, ").append((int) b.seconds).append("s").append(b.killed ? ", killed" : "").append(')');
                if (b.takenHits > 0 || b.takenDmg > 0) sb.append(" - you took ").append(shortNum(b.takenDmg)).append(" damage in ").append(b.takenHits).append(" hits");
                sb.append('\n');
                int rank = 1;
                for (DpsTracker.Row r : b.rows) {
                    sb.append(String.format(Locale.ROOT, "%2d. %-18s %9s  %5.1f%%  %s dps%s%n", rank++, r.name, shortNum(r.damage), r.percent, shortNum((long) r.dps),
                            (r.guardHits > 0 ? "  (" + r.guardHits + " guarded hits, " + shortNum(r.guardDmg) + ")" : "")
                                    + (r.takenHits > 0 ? "  took " + shortNum(r.takenDmg) + " in " + r.takenHits + " hits" : "")));
                }
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
            copy.setText("Copied");
            Timer t = new Timer(1400, e -> copy.setText("Copy results"));
            t.setRepeats(false);
            t.start();
        }
    }

    /** One leaderboard: the boss, then a stepped bar per player. */
    static class BoardView extends JComponent {
        private static final int ROW = 26, HEAD = 50, COLS = 20;
        private final DpsTracker.Board b;
        private final boolean divider;

        BoardView(DpsTracker.Board b, boolean divider) {
            this.b = b;
            this.divider = divider;
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(400, HEAD + COLS + b.rows.size() * ROW + 22);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            int w = getWidth();
            boolean whole = b.maxHp == 0;
            int top = divider ? 14 : 0;
            if (divider) {
                g.setColor(Pixel.TRACK);
                g.fillRect(0, 0, w, 2);
            }
            Cute.smooth(g);
            g.setColor(Pixel.INK);
            g.setFont(Cute.font(Font.BOLD, 15));
            g.drawString(b.title, 0, top + 18);
            String meta;
            if (whole) meta = shortNum(b.total) + " damage over " + duration(b.seconds);
            else meta = (b.kind == DpsTracker.Kind.BOSS ? "Boss  ·  " : b.kind == DpsTracker.Kind.MINIBOSS ? "Miniboss  ·  " : "")
                    + shortNum(b.maxHp) + " HP, " + duration(b.seconds) + ", " + (b.killed ? "defeated" : "not defeated");
            if (b.takenHits > 0 || b.takenDmg > 0) {
                meta += "  ·  you were hit " + b.takenHits + (b.takenHits == 1 ? " time" : " times") + " for " + shortNum(b.takenDmg) + " damage";
            }
            g.setFont(Cute.font(Font.PLAIN, 12));
            g.setColor(b.killed ? Pixel.TEAL : Pixel.MUTED);
            g.drawString(meta, 0, top + 36);

            g.setFont(Cute.font(Font.BOLD, 12));
            FontMetrics fm = g.getFontMetrics();
            // columns, right to left: DPS, damage taken, hits taken, share, damage
            // tighter columns and shorter headings when the window is narrow
            boolean compact = w < 680;
            int xDps = w, xTaken = w - (compact ? 60 : 78), xHits = w - (compact ? 116 : 160),
                    xShare = w - (compact ? 168 : 232), xDmg = w - (compact ? 216 : 290);
            int nameW = compact ? 130 : 150;
            int barX = nameW, barW = Math.max(24, xDmg - 56 - barX);

            // column headings
            int hy = top + HEAD + 6;
            g.setFont(Cute.font(Font.BOLD, 11));
            g.setColor(Pixel.MUTED);
            FontMetrics hf = g.getFontMetrics();
            g.drawString("Player", 22, hy);
            if (barW >= hf.stringWidth("Damage dealt") + 12) g.drawString("Damage dealt", barX, hy); // only if it fits
            rightText(g, hf, "Damage", xDmg, hy);
            rightText(g, hf, "Share", xShare, hy);
            rightText(g, hf, compact ? "Hits" : "Hits taken", xHits, hy);
            rightText(g, hf, compact ? "Taken" : "Dmg taken", xTaken, hy);
            rightText(g, hf, "DPS", xDps, hy);

            int y = top + HEAD + COLS - 4;
            int rank = 1;
            long topDmg = b.rows.isEmpty() ? 0 : b.rows.get(0).damage;
            for (DpsTracker.Row r : b.rows) {
                Cute.smooth(g);
                g.setFont(Cute.font(Font.BOLD, 12));
                fm = g.getFontMetrics();
                g.setColor(r.you ? Pixel.PINK_INK : Pixel.MUTED);
                g.drawString(String.valueOf(rank), 0, y + 13);
                String name = r.name;
                while (fm.stringWidth(name) > nameW - 34 && name.length() > 3) name = name.substring(0, name.length() - 2) + "…";
                g.setColor(r.you ? Pixel.PINK_INK : Pixel.INK);
                g.drawString(name, 22, y + 13);
                // stepped bar on a track
                Pixel.frame(g, barX, y + 2, barW, 14, Pixel.TRACK, null, 0);
                double frac = topDmg == 0 ? 0 : (double) r.damage / topDmg;
                int bw = (int) Math.max(9, Math.round(barW * frac / 3.0) * 3);
                Color c = r.you ? Pixel.PINK : (DpsTracker.UNKNOWN.equals(r.name) ? Pixel.LINE : Pixel.OTHERS);
                Pixel.frame(g, barX, y + 2, bw, 14, c, null, 0);
                Cute.smooth(g);
                if (r.guardHits > 0) {
                    // guarded hits (Oryx 3), tagged at the end of the bar's track
                    String tag = barW >= 130 ? r.guardHits + " guarded · " + shortNum(r.guardDmg) : r.guardHits + " grd";
                    g.setFont(Cute.font(Font.BOLD, 10));
                    FontMetrics tf = g.getFontMetrics();
                    int tw = tf.stringWidth(tag) + 10;
                    int tx = barX + barW - tw;
                    Pixel.frame(g, tx, y + 3, tw, 12, Pixel.INK, null, 0);
                    g.setColor(Color.WHITE);
                    g.drawString(tag, tx + 5, y + 13);
                }
                g.setFont(Cute.font(Font.BOLD, 12));
                fm = g.getFontMetrics();
                g.setColor(Pixel.INK);
                rightText(g, fm, shortNum(r.damage), xDmg, y + 13);
                g.setFont(Cute.font(Font.PLAIN, 12));
                fm = g.getFontMetrics();
                rightText(g, fm, String.format(Locale.ROOT, "%.1f%%", r.percent), xShare, y + 13);
                g.setColor(new Color(0xC2185B));
                rightText(g, fm, r.takenHits >= 0 ? String.valueOf(r.takenHits) : "–", xHits, y + 13);
                rightText(g, fm, r.takenHits >= 0 ? shortNum(r.takenDmg) : "–", xTaken, y + 13);
                g.setColor(Pixel.INK);
                rightText(g, fm, shortNum((long) r.dps), xDps, y + 13);
                y += ROW;
                rank++;
            }
            g.dispose();
        }

        private static void rightText(Graphics2D g, FontMetrics fm, String text, int right, int y) {
            g.drawString(text, right - fm.stringWidth(text), y);
        }

        private static String duration(double s) {
            int secs = (int) Math.round(s);
            return secs >= 60 ? (secs / 60) + "m " + (secs % 60) + "s" : secs + "s";
        }
    }
}
