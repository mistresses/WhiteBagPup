package whitebag;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static whitebag.AboutMe.heading;
import static whitebag.AboutMe.left;
import static whitebag.AboutMe.note;

/** The Boss Kills tab: every boss you helped kill, with totals per boss (from dps-log.txt). */
final class KillsPage extends MoreTabs.Page {
    private final Predicate<History.Dps> mine;
    private boolean everyAccount = false;
    private int show = 0; // 0 bosses, 1 minibosses, 2 everything
    private final JTextField search = Pixel.field("", 18, "Find a boss or dungeon");
    private boolean searching = false;

    KillsPage(Predicate<History.Dps> mine) {
        this.mine = mine;
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { searching = true; changed(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { searching = true; changed(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
    }

    private static final class Tally {
        final String boss;
        int kills;
        final Set<String> maps = new LinkedHashSet<>();
        double bestShare, fastest = Double.MAX_VALUE;
        int firsts; // times you did the most damage
        long last;

        Tally(String boss) {
            this.boss = boss;
        }
    }

    private boolean shows(History.Dps d) {
        boolean boss = d.kind.isEmpty() || d.kind.equals("BOSS");
        boolean mini = d.kind.equals("MINIBOSS");
        return show == 2 || (show == 0 && boss) || (show == 1 && mini);
    }

    @Override
    void fill() {
        List<History.Dps> kills = new ArrayList<>();
        boolean several = false;
        String first = null;
        for (History.Dps d : History.dps()) {
            if (!d.account.isEmpty()) {
                if (first == null) first = d.account;
                else if (!first.equals(d.account)) several = true;
            }
            if (!d.killed) continue;
            if (mine == null || everyAccount || mine.test(d)) kills.add(d);
        }
        long now = System.currentTimeMillis() / 1000;
        long today = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond();
        int total = 0, session = 0, day = 0, week = 0;
        Map<String, Tally> by = new LinkedHashMap<>();
        String q = LootBook.norm(search.getText());
        for (History.Dps d : kills) {
            if (!shows(d)) continue;
            total++;
            if (d.at * 1000L >= LootStats.SESSION_START * 1000L) session++;
            if (d.at >= today) day++;
            if (d.at >= now - 7 * 86_400L) week++;
            if (!q.isEmpty() && !LootBook.norm(d.boss).contains(q) && !LootBook.norm(d.map).contains(q)) continue;
            Tally t = by.computeIfAbsent(d.boss, Tally::new);
            t.kills++;
            if (!d.map.isEmpty()) t.maps.add(d.map);
            t.bestShare = Math.max(t.bestShare, d.share);
            if (d.seconds >= 1) t.fastest = Math.min(t.fastest, d.seconds);
            if (d.rank == 1 && d.players > 1) t.firsts++;
            t.last = Math.max(t.last, d.at);
        }

        String what = show == 0 ? "boss kills" : show == 1 ? "miniboss kills" : "kills";
        list.add(heading("boss kills", total == 0 ? null : String.format(Locale.ROOT, "%,d %s", total, what)));
        gap(10);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        controls.setOpaque(false);
        controls.add(new LogTabs.Segments(new String[]{"Bosses", "Minibosses", "Everything"}, show, i -> {
            show = i;
            changed();
        }));
        if (mine != null && several) {
            controls.add(Box.createHorizontalStrut(12));
            controls.add(new LogTabs.Segments(new String[]{"This account", "All accounts"}, everyAccount ? 1 : 0, i -> {
                everyAccount = i == 1;
                changed();
            }));
        }
        list.add(fit(controls));
        gap(10);

        if (kills.isEmpty()) {
            list.add(note("Every boss you help kill is counted here (you need to have hit it), with how often, where, your best "
                    + "share of the damage and your fastest kill. Kills are saved when you leave the dungeon."));
            return;
        }
        JLabel big = new JLabel(String.format(Locale.ROOT, "%,d %s", total, what));
        big.setFont(Cute.font(Font.BOLD, 20));
        big.setForeground(Pixel.INK);
        list.add(left(big));
        gap(2);
        list.add(note(String.format(Locale.ROOT, "%,d this session  ·  %,d today  ·  %,d in the last 7 days", session, day, week)));
        gap(12);
        JPanel find = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        find.setOpaque(false);
        find.add(search);
        list.add(fit(find));
        if (searching) SwingUtilities.invokeLater(() -> {
            search.requestFocusInWindow();
            searching = false;
        });
        gap(10);

        List<Tally> rows = new ArrayList<>(by.values());
        rows.sort((a, b) -> a.kills != b.kills ? b.kills - a.kills : a.boss.compareToIgnoreCase(b.boss));
        if (rows.isEmpty()) {
            list.add(note(q.isEmpty() ? "None of these yet." : "Nothing matches \"" + search.getText().trim() + "\"."));
            return;
        }
        List<String[]> table = new ArrayList<>();
        table.add(new String[]{"Boss", "Kills", "Where", "Best share", "Top dmg", "Fastest", "Last"});
        for (int i = 0; i < Math.min(60, rows.size()); i++) {
            Tally t = rows.get(i);
            String where = t.maps.isEmpty() ? "-" : t.maps.iterator().next() + (t.maps.size() > 1 ? " +" + (t.maps.size() - 1) : "");
            table.add(new String[]{t.boss, String.format(Locale.ROOT, "%,d", t.kills), where,
                    String.format(Locale.ROOT, "%.0f%%", t.bestShare), t.firsts == 0 ? "-" : t.firsts + "×",
                    t.fastest == Double.MAX_VALUE ? "-" : time(t.fastest),
                    new SimpleDateFormat("d MMM", Locale.ROOT).format(new Date(t.last * 1000L))});
        }
        list.add(grid(table));
        if (rows.size() > 60) {
            gap(4);
            list.add(note("And " + (rows.size() - 60) + " more. Search to find one."));
        }
        gap(10);
        list.add(note("Counts bosses you hit that died. \"Top dmg\" is how often you did the most damage of the group, "
                + "and \"Fastest\" is the shortest fight from first hit to kill. Saved in dps-log.txt."));
    }

    private static String time(double secs) {
        long s = Math.round(secs);
        return s >= 60 ? (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60) : s + "s";
    }

    /** A table with each column only as wide as it needs (long boss names don't push the rest off the page). */
    private static JComponent grid(List<String[]> rows) {
        JPanel g = new JPanel(new GridBagLayout());
        g.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        for (int r = 0; r < rows.size(); r++) {
            String[] cells = rows.get(r);
            for (int k = 0; k < cells.length; k++) {
                JLabel l = new JLabel(cells[k]);
                l.setFont(Cute.font(r == 0 ? Font.BOLD : Font.PLAIN, 13));
                l.setForeground(Pixel.INK);
                c.gridx = k;
                c.gridy = r;
                c.insets = new Insets(0, 0, 4, k == cells.length - 1 ? 0 : 16);
                g.add(l, c);
            }
        }
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrap.setOpaque(false);
        wrap.add(g);
        return fit(wrap);
    }

    private static JComponent fit(JComponent c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return left(c);
    }
}
