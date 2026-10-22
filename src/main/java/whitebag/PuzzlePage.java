package whitebag;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

import static whitebag.AboutMe.heading;
import static whitebag.AboutMe.left;
import static whitebag.AboutMe.note;

/** The Puzzle tab: the Shatters' Puzzle Room (nine towers, make them all green), with the towers to shoot marked. */
final class PuzzlePage extends MoreTabs.Page {
    /** This page's account, and where badge-worthy moments go ("puzzle", "puzzle-perfect", "practice-perfect"). */
    java.util.function.Supplier<String> account = () -> "";
    java.util.function.Consumer<String> onEvent = e -> { };
    /** Counting shots in game: the fewest needed when the board was first seen, and shots since. */
    private int gameMinShots = -1, gameShots = 0;
    /** Counting shots in practice. */
    private int practiceMin = -1, practiceShots = 0;

    /** Bit i set = tower i is green. */
    private int board = 0;
    /** 0: clicking sets a tower's colour; 1: clicking shoots it (it and its neighbours flip), to follow along. */
    private int mode = 0;
    private boolean fromGame = false;
    private long seenAt;

    /** Called (from the packet thread) when the pup reads the board in game. */
    void boardSeen(int green) {
        SwingUtilities.invokeLater(() -> {
            if (green != 0x1FF) {
                if (!fromGame || gameMinShots < 0 || board == 0x1FF) {
                    gameMinShots = Integer.bitCount(ShattersPuzzle.solve(green));
                    gameShots = 0;
                } else {
                    gameShots++;
                }
            } else if (fromGame && board != 0x1FF && gameMinShots >= 0) {
                gameShots++;
                onEvent.accept("puzzle");
                if (gameShots <= gameMinShots) onEvent.accept("puzzle-perfect");
                gameMinShots = -1;
            }
            board = green;
            fromGame = true;
            seenAt = System.currentTimeMillis();
            changed();
        });
    }

    @Override
    void fill() {
        list.add(heading("shatters puzzle", fromGame ? "Read from the game" : null));
        gap(8);
        list.add(note("In The Shatters' Puzzle Room, shooting a tower flips it and the towers next to it (above, below, left "
                + "and right). Make all nine green to open the way. Set the towers below to match the game, and the list "
                + "beside them says which to shoot."));
        gap(10);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        controls.setOpaque(false);
        controls.add(new LogTabs.Segments(new String[]{"Click to set colours", "Click to shoot"}, mode, i -> {
            mode = i;
            changed();
        }));
        controls.add(Box.createHorizontalStrut(12));
        Pixel.Button allRed = new Pixel.Button("All red", false);
        allRed.addActionListener(e -> {
            board = 0;
            fromGame = false;
            changed();
        });
        controls.add(allRed);
        controls.add(Box.createHorizontalStrut(8));
        Pixel.Button random = new Pixel.Button("Random practice", false);
        random.addActionListener(e -> {
            // a random reachable board: shoot a few random towers from all green
            int b = 0x1FF;
            java.util.Random r = new java.util.Random();
            do {
                b = 0x1FF;
                for (int i = 0; i < 9; i++) if (r.nextBoolean()) b = ShattersPuzzle.shoot(b, i);
            } while (b == 0x1FF);
            board = b;
            fromGame = false;
            mode = 1;
            practiceMin = Integer.bitCount(ShattersPuzzle.solve(b));
            practiceShots = 0;
            changed();
        });
        controls.add(random);
        controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, controls.getPreferredSize().height));
        list.add(left(controls));
        gap(14);

        int shots = ShattersPuzzle.solve(board);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.add(new Board(shots));
        row.add(Box.createHorizontalStrut(24));
        row.add(answer(shots));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        list.add(left(row));
        gap(14);
        puzzleLog();
        gap(14);
        if (fromGame) {
            list.add(note("The pup read this board from the game at " + new java.text.SimpleDateFormat("h:mm:ss a", java.util.Locale.ROOT)
                    .format(new java.util.Date(seenAt)) + ". It follows along as you shoot. Click a tower to correct it if it's wrong."));
        } else {
            list.add(note("When you're in the Puzzle Room, the pup tries to read the towers from the game by itself. If the board "
                    + "doesn't fill in, set it by hand. Anything it noticed is written to puzzle-debug.txt next to the app; send "
                    + "that file over and it can be taught to read it."));
        }
    }

    /** How many puzzles you've solved, practice and in game. */
    private void puzzleLog() {
        String key = account.get();
        long practice = Badges.count(key, "practice"), practicePerfect = Badges.count(key, "practice-perfect");
        long game = Badges.count(key, "puzzle"), gamePerfect = Badges.count(key, "puzzle-perfect");
        list.add(heading("puzzle log", null));
        gap(8);
        list.add(new AboutMe.AbilityBar("Practice puzzles solved", practice + " solved, " + practicePerfect + " perfect",
                practice == 0 ? 0 : practicePerfect / (double) practice));
        gap(8);
        list.add(new AboutMe.AbilityBar("Solved in game", game + " solved, " + gamePerfect + " perfect",
                game == 0 ? 0 : gamePerfect / (double) game));
        gap(6);
        list.add(note("A perfect solve uses the fewest shots possible. The bars show how many of your solves were perfect."));
    }

    private JComponent answer(int shots) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel head = new JLabel();
        head.setFont(Cute.font(Font.BOLD, 18));
        head.setForeground(Pixel.INK);
        if (board == 0x1FF) {
            head.setText("All green! The way is open.");
            p.add(head);
            return p;
        }
        int n = Integer.bitCount(shots);
        head.setText("Shoot " + n + (n == 1 ? " tower:" : " towers:"));
        p.add(head);
        p.add(Box.createVerticalStrut(6));
        List<String> where = new ArrayList<>();
        for (int i = 0; i < 9; i++) if ((shots >> i & 1) != 0) where.add(ShattersPuzzle.place(i));
        for (String w : where) {
            JLabel l = new JLabel("•  " + w);
            l.setFont(Cute.font(Font.PLAIN, 14));
            l.setForeground(Pixel.INK);
            p.add(l);
        }
        p.add(Box.createVerticalStrut(8));
        JLabel tip = new JLabel("<html><div style='width:220px'>Any order works. Each tower once.</div></html>");
        tip.setFont(Cute.font(Font.PLAIN, 12));
        tip.setForeground(Pixel.MUTED);
        p.add(tip);
        return p;
    }

    private static final java.awt.image.BufferedImage[] PICS = new java.awt.image.BufferedImage[2];
    private static final int[] PIC_TYPES = {-1, -1};

    /** The game's sprite for a green / red tower, scaled up crisply, or null. */
    static java.awt.image.BufferedImage towerPicture(boolean green) {
        int k = green ? 1 : 0;
        int type = ShattersPuzzle.towerType(green);
        if (type < 0) return null;
        if (PIC_TYPES[k] != type) {
            PIC_TYPES[k] = type;
            PICS[k] = AboutMe.sprite(type, 64);
        }
        return PICS[k];
    }

    /** The nine towers; click to set or shoot. */
    private final class Board extends JComponent {
        private static final int CELL = 84, GAP = 12;
        private final int shots;

        Board(int shots) {
            this.shots = shots;
            setPreferredSize(new Dimension(CELL * 3 + GAP * 2, CELL * 3 + GAP * 2));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int c = e.getX() / (CELL + GAP), r = e.getY() / (CELL + GAP);
                    if (c > 2 || r > 2 || e.getX() % (CELL + GAP) >= CELL || e.getY() % (CELL + GAP) >= CELL) return;
                    int i = r * 3 + c;
                    Squeak.boop();
                    board = mode == 0 ? board ^ (1 << i) : ShattersPuzzle.shoot(board, i);
                    if (mode == 0) {
                        fromGame = false;
                        practiceMin = -1; // changed by hand: not a practice run any more
                    } else if (practiceMin >= 0) {
                        practiceShots++;
                        if (board == 0x1FF) {
                            onEvent.accept("practice");
                            if (practiceShots <= practiceMin) onEvent.accept("practice-perfect");
                            practiceMin = -1;
                        }
                    }
                    changed();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Cute.smooth(g);
            for (int i = 0; i < 9; i++) {
                int x = (i % 3) * (CELL + GAP), y = (i / 3) * (CELL + GAP);
                boolean green = (board >> i & 1) != 0;
                Color fill = green ? new Color(0x5FD08A) : new Color(0xF0707A);
                Color edge = green ? new Color(0x2F9E5E) : new Color(0xC2434F);
                g.setColor(new Color(0, 0, 0, 30));
                g.fill(new RoundRectangle2D.Double(x, y + 4, CELL, CELL, 18, 18));
                g.setColor(Cute.mix(Color.WHITE, fill, 0.25));
                g.fill(new RoundRectangle2D.Double(x, y, CELL, CELL, 18, 18));
                g.setColor(edge);
                g.setStroke(new BasicStroke(3f));
                g.draw(new RoundRectangle2D.Double(x + 1.5, y + 1.5, CELL - 3, CELL - 3, 16, 16));
                // the game's own tower picture if the pup knows it, else a little drawn tower
                java.awt.image.BufferedImage pic = towerPicture(green);
                int cx = x + CELL / 2;
                if (pic != null) {
                    g.drawImage(pic, cx - pic.getWidth() / 2, y + (CELL - pic.getHeight()) / 2, null);
                } else {
                g.setColor(edge);
                g.fillRoundRect(cx - 18, y + CELL - 22, 36, 10, 4, 4);
                g.setColor(fill);
                g.fillRoundRect(cx - 11, y + 24, 22, CELL - 44, 6, 6);
                g.setColor(edge);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(cx - 11, y + 24, 22, CELL - 44, 6, 6);
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(cx - 8, y + 12, 16, 16));
                g.setColor(edge);
                g.draw(new Ellipse2D.Double(cx - 8, y + 12, 16, 16));
                g.setColor(fill);
                g.fill(new Ellipse2D.Double(cx - 4, y + 16, 8, 8));
                }
            }
            g.dispose();
        }
    }
}
