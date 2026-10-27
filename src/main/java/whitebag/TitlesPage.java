package whitebag;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static whitebag.AboutMe.heading;
import static whitebag.AboutMe.left;
import static whitebag.AboutMe.note;

/** The Titles page (under Me): every title in the game, grouped by tier, with the ones you've worn ticked off. */
final class TitlesPage extends MoreTabs.Page {
    private final Supplier<String> account;
    private final Predicate<String> mine;
    private int show = 0; // 0 all, 1 worn, 2 not worn yet

    TitlesPage(Supplier<String> account, Predicate<String> mine) {
        this.account = account;
        this.mine = mine;
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) changed();
        });
    }

    @Override
    void fill() {
        List<Titles.Info> all = Titles.all();
        Map<Integer, Long> worn = Titles.wornBy(a -> mine == null || mine.test(a));
        int got = 0;
        for (Titles.Info t : all) if (worn.containsKey(t.id)) got++;
        list.add(heading("titles", all.isEmpty() ? null : got + " of " + all.size() + " worn"));
        gap(8);
        if (all.isEmpty()) {
            list.add(note("Every title in the game shows up here once the pup has read the game files."));
            return;
        }
        String key = account.get();
        String wearing = key == null ? "" : History.titledNameOf(key);
        if (!wearing.isEmpty()) {
            JLabel now = new JLabel("Wearing now:  " + wearing);
            now.setFont(Cute.font(Font.BOLD, 14));
            now.setForeground(Pixel.INK);
            list.add(left(now));
            gap(8);
        }
        list.add(new AboutMe.AbilityBar("Titles worn", got + " / " + all.size(), got / (double) all.size()));
        gap(10);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        controls.setOpaque(false);
        controls.add(new LogTabs.Segments(new String[]{"All", "Worn", "Not worn yet"}, show, i -> {
            show = i;
            changed();
        }));
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, controls.getPreferredSize().height));
        list.add(left(controls));
        gap(6);
        list.add(note("A title counts once the pup has seen you wearing it in game. Change titles in game to tick them off."));
        gap(12);

        // prefixes, then suffixes; each by tier
        String[][] kinds = {{"prefix", "Prefixes"}, {"suffix", "Suffixes"}, {"", "Other titles"}};
        for (String[] k : kinds) {
            Map<Integer, List<Titles.Info>> byTier = new LinkedHashMap<>();
            for (Titles.Info t : all) {
                if (!t.kind.equals(k[0])) continue;
                boolean w = worn.containsKey(t.id);
                if ((show == 1 && !w) || (show == 2 && w)) continue;
                byTier.computeIfAbsent(t.tier, x -> new ArrayList<>()).add(t);
            }
            if (byTier.isEmpty()) continue;
            int n = 0, have = 0;
            for (Titles.Info t : all) if (t.kind.equals(k[0])) { n++; if (worn.containsKey(t.id)) have++; }
            list.add(heading(k[1].toLowerCase(Locale.ROOT), have + " of " + n));
            gap(6);
            for (Map.Entry<Integer, List<Titles.Info>> e : byTier.entrySet()) {
                JLabel tier = new JLabel(e.getKey() > 0 ? "Tier " + e.getKey() : "No tier");
                tier.setFont(Cute.font(Font.BOLD, 12));
                tier.setForeground(Theme.c(0x8A7F99));
                list.add(left(tier));
                gap(4);
                JPanel grid = new JPanel(new GridLayout(0, 4, 8, 8));
                grid.setOpaque(false);
                for (Titles.Info t : e.getValue()) grid.add(new Chip(t, worn.get(t.id), wearing));
                grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
                list.add(left(grid));
                gap(10);
            }
            gap(6);
        }
    }

    /** One title: in colour with a tick once worn, a heart on the one you're wearing now. */
    private static final class Chip extends JComponent {
        private final Titles.Info t;
        private final Long since;
        private final boolean now;

        Chip(Titles.Info t, Long since, String wearing) {
            this.t = t;
            this.since = since;
            String w = " " + wearing + " ";
            this.now = since != null && w.contains(" " + t.words + " ");
            setPreferredSize(new Dimension(130, 34));
            String kind = t.kind.isEmpty() ? "Title" : Character.toUpperCase(t.kind.charAt(0)) + t.kind.substring(1);
            setToolTipText(kind + (t.tier > 0 ? ", tier " + t.tier : "") + (since == null ? ". Not worn yet"
                    : ". First worn " + new SimpleDateFormat("d MMM yyyy", Locale.ROOT).format(new Date(since * 1000L))));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Cute.smooth(g);
            int w = getWidth(), h = getHeight();
            boolean on = since != null;
            Color accent = t.kind.equals("prefix") ? Theme.c(0x6EC6FF) : t.kind.equals("suffix") ? Theme.c(0xB388FF) : Pixel.PINK;
            g.setColor(now ? Pixel.PINK_SOFT : on ? Cute.mix(Theme.WHITE, accent, 0.18) : Theme.c(0xF4F2F7));
            g.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 12, 12));
            g.setColor(now ? Pixel.PINK : on ? accent : Theme.c(0xE2DCEA));
            g.setStroke(new BasicStroke(on ? 2f : 1.2f));
            g.draw(new RoundRectangle2D.Double(1, 1, w - 3, h - 3, 11, 11));
            g.setFont(Cute.font(on ? Font.BOLD : Font.PLAIN, 12));
            FontMetrics fm = g.getFontMetrics();
            String s = t.words;
            int room = w - (on ? 34 : 16);
            while (fm.stringWidth(s) > room && s.length() > 3) s = s.substring(0, s.length() - 1);
            if (!s.equals(t.words)) s = s.trim() + "…";
            g.setColor(on ? Pixel.INK : Theme.c(0x9C92AC));
            g.drawString(s, 10, (h + fm.getAscent()) / 2 - 2);
            if (now) {
                Cute.drawHeart(g, w - 20, (h - 10) / 2, 10, Pixel.PINK);
            } else if (on) {
                // a little tick
                g.setColor(Cute.mix(accent, Theme.BLACK, 0.3));
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int x = w - 20, y = h / 2;
                g.drawLine(x, y, x + 3, y + 3);
                g.drawLine(x + 3, y + 3, x + 9, y - 4);
            }
            g.dispose();
        }
    }
}
