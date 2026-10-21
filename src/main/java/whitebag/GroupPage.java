package whitebag;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** One tab holding a few related pages, with a switch along the top to pick which one shows. */
final class GroupPage extends JPanel {
    private final CardLayout cards = new CardLayout();
    private final JPanel holder = new JPanel(cards);
    private final String[] names;
    private int shown = 0;

    GroupPage(String[] names, JComponent... pages) {
        super(new BorderLayout());
        this.names = names;
        setOpaque(false);
        holder.setOpaque(false);
        for (int i = 0; i < pages.length; i++) holder.add(pages[i], names[i]);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(0, 0, 10, 0));
        top.add(new LogTabs.Segments(names, 0, i -> {
            Squeak.boop();
            show(i);
        }));
        add(top, BorderLayout.NORTH);
        add(holder, BorderLayout.CENTER);
    }

    void show(int i) {
        shown = i;
        cards.show(holder, names[i]);
    }

    int shown() {
        return shown;
    }
}
