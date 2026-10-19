package whitebag;

import assets.AssetExtractor;
import assets.IdToAsset;
import packets.packetcapture.PacketProcessor;
import packets.packetcapture.sniff.assembly.TcpStreamErrorHandler;
import puppy.version.Version;
import util.Util;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Small desktop app: sniffs RotMG traffic with Puppy and posts a Discord webhook message
 * whenever a white bag (or any other selected bag) drops for you.
 */
public class WhiteBagNotifier {
    private static final String SETTINGS_FILE = "whitebag-notifier.properties";

    private final Properties settings = new Properties();
    private final DiscordWebhook webhook = new DiscordWebhook(this::log);
    /** One per game account; the first exists from the start and is claimed by the first game seen. */
    private final List<Account> accounts = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile Account selected;
    private AccountRouter router;
    private Pixel.Tabs tabs;
    private String[] tabNames;
    private CardLayout pages;
    private Pixel.Sheet sheet;
    private JPanel accountBar;
    private volatile Set<BagType> watchedBags = EnumSet.of(BagType.WHITE, BagType.BOOSTED_WHITE);
    private final Account.Host host = new Account.Host() {
        @Override public void bag(Account a, BagDrop drop) { onBag(a, drop); }
        @Override public void chat(Account a, ChatLog.Line line) { onChat(a, line); }
        @Override public void event(Account a, EventTracker.Entry e) { onEvent(a, e); }
        @Override public void log(String text) { WhiteBagNotifier.this.log(text); }
        @Override public void accountsChanged() { SwingUtilities.invokeLater(WhiteBagNotifier.this::refreshAccountBar); }
        @Override public synchronized String legacyOwner(String claimingKey) {
            String owner = settings.getProperty("accounts.legacyOwner", "");
            if (owner.isEmpty() && claimingKey != null) {
                owner = claimingKey;
                settings.setProperty("accounts.legacyOwner", owner);
                saveSettings();
            }
            return owner;
        }
        @Override public Set<BagType> watched() { return watchedBags; }
    };
    private final Map<BagType, AbstractButton> bagBoxes = new EnumMap<>(BagType.class);

    private JFrame frame;
    private JTextField urlField;
    private JTextField pingField;
    private Pixel.Check autoStartBox;
    private Pixel.Check soundBox;
    private Pixel.Check shotBox;
    private Pixel.Button updateButton;
    private volatile String updateLink = "";
    private Pixel.Button startButton;
    private Pixel.Status status;
    private JLabel watchingLabel;
    private Cute.Header header;
    private JTextArea logArea;
    private PacketProcessor processor;
    private int bagsToday = 0;

    public static void main(String[] args) {
        System.out.println("Java " + System.getProperty("java.version") + " (" + System.getProperty("sun.arch.data.model") + "-bit)");
        Util.setSaveLogs(false);
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--path")) AssetExtractor.setRealmResPath(args[i + 1]);
        }
        System.setProperty("awt.useSystemAAFontSettings", "on");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        WhiteBagNotifier app = new WhiteBagNotifier();
        SwingUtilities.invokeLater(app::buildGui);
        app.loadAssets();
        SwingUtilities.invokeLater(app::checkForUpdate); // after the window (and settings) are ready
        SwingUtilities.invokeLater(app::startRecapTimer);
    }

    // ---------------------------------------------------------------- recaps

    /** The Recaps section on Home: their own webhook, how often, when, and post-now buttons. */
    private void buildRecaps(JPanel home) {
        section(home, new Pixel.Heading("recaps", 2), 22);
        JLabel intro = new JLabel("<html><div style='width:470px'>A daily or weekly summary for each account (your name and classes, bags, runs, fame "
                + "and time played), posted to its own channel, with pictures of the loot in one post. Only what happened while the app was running counts; "
                + "if it's closed when one is due, it's posted next time you open it.</div></html>");
        intro.setFont(Cute.font(Font.PLAIN, 12));
        intro.setForeground(Color.BLACK);
        section(home, intro, 6);

        JPanel urlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        urlRow.setOpaque(false);
        JLabel ul = Pixel.label("Recap webhook URL  ");
        JTextField recapUrl = Pixel.field(settings.getProperty("recap.url", ""), 26, "a webhook or bot recap link");
        Pixel.Button saveUrl = new Pixel.Button("Save", false);
        saveUrl.addActionListener(e -> {
            String u = recapUrl.getText().trim();
            if (!u.isEmpty() && !Recap.isValidUrl(u)) {
                log("*tilts head* that doesn't look like a Discord webhook URL (https://discord.com/api/webhooks/...) or a bot recap link (https://.../recap?key=...).");
                return;
            }
            settings.setProperty("recap.url", u);
            saveSettings();
            log(u.isEmpty() ? "*boop* recap webhook cleared." : Recap.isBotUrl(u) ? "*boop* bot recap link saved! Recaps get ◀ ▶ buttons." : "*boop* recap webhook saved!");
        });
        urlRow.add(ul);
        urlRow.add(recapUrl);
        urlRow.add(Box.createHorizontalStrut(8));
        urlRow.add(saveUrl);
        section(home, urlRow, 10);

        String[] modes = {"off", "daily", "weekly", "both"};
        int sel = Math.max(0, java.util.Arrays.asList(modes).indexOf(Recap.mode(settings)));
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modeRow.setOpaque(false);
        modeRow.add(Pixel.label("Post  "));
        modeRow.add(new LogTabs.Segments(new String[]{"Off", "Daily", "Weekly", "Daily and weekly"}, sel, i -> {
            settings.setProperty("recap.mode", modes[i]);
            settings.remove("recap.last.daily");
            settings.remove("recap.last.weekly");
            saveSettings();
            log(i == 0 ? "*curls up* no more recaps." : "*wags* recaps on! The first one goes out at the next time it's due.");
        }));
        section(home, modeRow, 8);

        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        timeRow.setOpaque(false);
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        JComboBox<String> day = new JComboBox<>(days);
        day.setSelectedIndex(Recap.day(settings) - 1);
        String[] hours = new String[24];
        for (int h = 0; h < 24; h++) hours[h] = (h % 12 == 0 ? 12 : h % 12) + (h < 12 ? " am" : " pm");
        JComboBox<String> hour = new JComboBox<>(hours);
        hour.setSelectedIndex(Recap.hour(settings));
        for (JComboBox<String> c : java.util.Arrays.asList(day, hour)) {
            c.setFont(Cute.font(Font.PLAIN, 13));
            c.addActionListener(e -> {
                settings.setProperty("recap.day", String.valueOf(day.getSelectedIndex() + 1));
                settings.setProperty("recap.hour", String.valueOf(hour.getSelectedIndex()));
                settings.remove("recap.last.daily");
                settings.remove("recap.last.weekly");
                saveSettings();
            });
        }
        timeRow.add(Pixel.label("Weekly on  "));
        timeRow.add(day);
        timeRow.add(Pixel.label("  at  "));
        timeRow.add(hour);
        timeRow.add(Pixel.label("   (daily: same time)"));
        section(home, timeRow, 8);

        // which bags go in the recap (counted and pictured)
        JPanel bagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bagRow.setOpaque(false);
        bagRow.add(Pixel.label("Bags in recap  "));
        java.util.Set<BagType> recapBags = Recap.bagFilter(settings);
        for (BagType c : Recap.COLOURS) {
            String label = c == BagType.EGG ? "Egg" : c.displayName;
            Pixel.Check box = new Pixel.Check(label, recapBags.contains(c));
            box.addActionListener(e -> {
                java.util.Set<BagType> now = Recap.bagFilter(settings);
                if (box.isSelected()) now.add(c);
                else now.remove(c);
                Recap.setBagFilter(settings, now);
                saveSettings();
            });
            bagRow.add(box);
            bagRow.add(Box.createHorizontalStrut(4));
        }
        section(home, bagRow, 8);

        JPanel nowRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nowRow.setOpaque(false);
        for (Recap.Period per : Recap.Period.values()) {
            Pixel.Button b = new Pixel.Button("Post " + per.label.toLowerCase(java.util.Locale.ROOT) + " recap now", false);
            b.addActionListener(e -> {
                String u = settings.getProperty("recap.url", "");
                if (!Recap.isValidUrl(u)) {
                    log("*tilts head* paste a recap webhook URL and press Save first.");
                    return;
                }
                for (Account a : accounts) a.me.checkpointPlay();
                int n = Recap.postNow(per, settings, (json, files, what) -> webhook.sendJson(u, json, files, what));
                log(n == 0 ? "*sniffs around* nothing to recap for the last " + (per == Recap.Period.DAILY ? "day" : "week") + " yet."
                        : "*runs off to Discord* sending the " + per.label.toLowerCase(java.util.Locale.ROOT) + " recap" + (n > 1 ? "s (" + n + " accounts)" : "") + "~");
            });
            nowRow.add(b);
            nowRow.add(Box.createHorizontalStrut(8));
        }
        section(home, nowRow, 10);
    }

    /** Checks every minute whether a recap is due. */
    private void startRecapTimer() {
        java.util.concurrent.ScheduledExecutorService t = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread th = new Thread(r, "recaps");
            th.setDaemon(true);
            return th;
        });
        t.scheduleWithFixedDelay(() -> {
            try {
                String u = settings.getProperty("recap.url", "");
                if (Recap.isDue(settings)) for (Account a : accounts) a.me.checkpointPlay();
                boolean changed = Recap.tick(settings, (json, files, what) -> webhook.sendJson(u, json, files, what), this::log);
                if (changed) SwingUtilities.invokeLater(this::saveSettings);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }, 20, 60, java.util.concurrent.TimeUnit.SECONDS);
        // save fame and DPS in progress when the app closes, so recaps include them
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (Account a : accounts) {
                try {
                    a.me.flush();
                    a.dps.flush();
                } catch (Throwable ignored) {
                }
            }
        }, "save-on-exit"));
    }

    // ---------------------------------------------------------------- startup

    /** Asks GitHub (once) whether there's a newer White Bag Pup, and shows a button if so. */
    private void checkForUpdate() {
        String repo = settings.getProperty("update.repo", UpdateCheck.DEFAULT_REPO);
        if (Boolean.parseBoolean(settings.getProperty("update.off", "false"))) return;
        java.util.function.BiConsumer<String, String> found = (tag, page) -> SwingUtilities.invokeLater(() -> {
            updateLink = page;
            log("*ears perk up* a new version of White Bag Pup is out! Press the button at the top of Home to get it.");
            if (updateButton != null) {
                updateButton.setText("A new version is out! Get the update");
                updateButton.setVisible(true);
                updateButton.revalidate();
            }
        });
        String fake = System.getProperty("whitebag.fakeUpdate"); // for testing the notice
        if (fake != null) found.accept(fake, "https://github.com/");
        else UpdateCheck.check(repo, found, problem -> System.out.println("Update check: " + problem));
    }

    private void loadAssets() {
        // the game files are saved next to the app; check Windows lets us write here first
        java.io.File here = new java.io.File(".").getAbsoluteFile().getParentFile();
        try {
            java.io.File probe = new java.io.File("assets");
            if (!probe.isDirectory() && !probe.mkdirs()) throw new java.io.IOException("can't create the assets folder");
            java.io.File t = java.io.File.createTempFile("write-test", ".tmp", probe);
            if (!t.delete()) t.deleteOnExit();
        } catch (Exception e) {
            log("*whimper* Windows won't let me save files in " + here + " (" + e.getMessage() + "). "
                    + "Item names and pictures need that. Fix: move the app to a plain folder like C:\\WhiteBagPup "
                    + "(not inside a zip file, OneDrive, Program Files or a protected Desktop/Documents folder), "
                    + "or allow Java under Windows Security > Virus & threat protection > Ransomware protection > Controlled folder access.");
        }
        try {
            AssetExtractor.checkForExtraction(Version.VERSION);
            IdToAsset.reloadAssets();
            LootImage.gameSpritesReady = new java.io.File("assets/flatbuffer/spritesheetf").exists();
            if (!LootImage.gameSpritesReady) log("*tilts head* found item names but no sprite sheets, so posts will be text only.");
            log("*sniffs the game files* ...got all the item names and pictures!");
        } catch (Throwable e) {
            log("*whimper* couldn't read the game files (" + e.getClass().getSimpleName() + "), so posts will show item numbers instead of names and pictures. "
                    + "If the game is installed somewhere unusual, start with: java -jar WhiteBagPup.jar --path \"<path to resources.assets>\"");
        }
    }

    private void buildGui() {
        loadSettings();
        Squeak.prepareSoundFolder();
        // Each game client gets its own connection; the router sends its packets to the right account.
        router = new AccountRouter(accounts, this::addAccount);
        packets.packetcapture.ConnectionRouter.setHandler(router);
        packets.packetcapture.ConnectionRouter.setClosedHandler(router);
        // realm events on every server from RealmStock (only if turned on in the Events tab)
        RealmStockFeed.onPing = e -> {
            if (RealmStockFeed.sound()) Squeak.playEvent(e.name, RealmStockFeed.soundNames(e)[1]);
            String d = RealmStockFeed.details(e);
            log("*ears up* [RealmStock] " + RealmStockFeed.headline(e) + " in " + RealmStockFeed.where(e) + (d.isEmpty() ? "" : " (" + d.replace("  ·  ", ", ") + ")"));
        };
        RealmStockFeed.start();
        TcpStreamErrorHandler.INSTANCE.setErrorMessageHandler((error, dump) -> log("*whimper* sniffer error: " + error));
        TcpStreamErrorHandler.INSTANCE.setErrorStopHandler(() -> SwingUtilities.invokeLater(this::stopSniffer));

        frame = new JFrame("White Bag Pup");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setIconImages(java.util.Arrays.asList(Cute.catIcon(16), Cute.catIcon(32), Cute.catIcon(48), Cute.catIcon(64), Cute.catIcon(128), Cute.catIcon(256)));

        // Background picture: put "background.png" next to the jar. Falls back to the plain colour if it's missing.
        final java.awt.image.BufferedImage bg = loadBackground();
        if (bg != null) {
            // How solid the white card is over the picture: 0 = fully see-through, 100 = solid white.
            int opacity = 45;
            try {
                opacity = Integer.parseInt(settings.getProperty("card.opacity", "45").trim());
            } catch (NumberFormatException ignored) {
            }
            settings.setProperty("card.opacity", String.valueOf(opacity));
            Pixel.setCardOpacity(opacity);
        }
        // How the picture fits the window (whitebag-notifier.properties):
        //   background.fit      = fill (cover the window, trim the overflow) | fit (show the whole picture) | stretch
        //   background.position = center | top | bottom | left | right   (which part stays in view)
        final String fit = bg == null ? "" : settings.getProperty("background.fit", "fill").trim().toLowerCase(java.util.Locale.ROOT);
        final String pos = bg == null ? "" : settings.getProperty("background.position", "center").trim().toLowerCase(java.util.Locale.ROOT);
        if (bg != null) {
            settings.setProperty("background.fit", fit);
            settings.setProperty("background.position", pos);
        }
        // The backdrop paints the picture and stays still; the window's contents scroll over it.
        JPanel backdrop = new JPanel(new BorderLayout()) {
            private java.awt.image.BufferedImage scaled; // picture already resized for the current window size

            @Override
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0); // paints Pixel.PAGE first (shows around the picture in "fit" mode)
                if (bg == null) return;
                int W = getWidth(), H = getHeight();
                if (W <= 0 || H <= 0) return;
                int w, h;
                if (fit.equals("stretch")) {
                    w = W;
                    h = H;
                } else {
                    double sx = W / (double) bg.getWidth(), sy = H / (double) bg.getHeight();
                    double s = fit.equals("fit") ? Math.min(sx, sy) : Math.max(sx, sy);
                    w = (int) Math.ceil(bg.getWidth() * s);
                    h = (int) Math.ceil(bg.getHeight() * s);
                }
                if (scaled == null || scaled.getWidth() != w || scaled.getHeight() != h) scaled = scalePicture(bg, w, h);
                int x = (W - w) / 2, y = (H - h) / 2;
                if (pos.equals("top")) y = 0;
                else if (pos.equals("bottom")) y = H - h;
                else if (pos.equals("left")) x = 0;
                else if (pos.equals("right")) x = W - w;
                g0.drawImage(scaled, x, y, null);
            }
        };
        backdrop.setBackground(Pixel.PAGE);
        JPanel root = new FitPanel();
        root.setOpaque(false);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(12, 20, 20, 20));

        header = new Cute.Header();
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setOnPet(Squeak::boop);
        header.setLastWhite(lastDrop("white"));
        root.add(header);
        root.add(Box.createVerticalStrut(6));

        // Folder tabs on top of one white sheet; each tab shows a page on the sheet.
        pages = new CardLayout();
        sheet = new Pixel.Sheet(pages);
        sheet.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabNames = new String[]{"home", "bag log", "chat", "dps", "about me", "fame", "vault", "loot", "collection", "trades", "events"};
        tabs = new Pixel.Tabs(tabNames, i -> showPage());
        // account switcher (only shown once a second game account turns up)
        accountBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        accountBar.setOpaque(false);
        accountBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        accountBar.setVisible(false);
        root.add(accountBar);
        sheet.tabs = tabs;
        tabs.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(tabs);
        root.add(sheet);

        JPanel home = new JPanel();
        home.setLayout(new BoxLayout(home, BoxLayout.Y_AXIS));
        home.setOpaque(false);
        sheet.add(home, "home");
        selected = addAccount(); // the first account's pages, ready before any game is seen

        // ---- update notice (hidden until GitHub has a newer release)
        updateButton = new Pixel.Button("", true);
        updateButton.setVisible(false);
        updateButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new java.net.URI(updateLink));
            } catch (Exception ex) {
                log("*tilts head* couldn't open the browser. The new version is at " + updateLink);
            }
        });
        section(home, updateButton, 0);

        // ---- Discord
        section(home, new Pixel.Heading("discord", 2), 0);
        JPanel discord = new JPanel(new GridBagLayout());
        discord.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        urlField = Pixel.field(settings.getProperty("webhook.url", ""), 34, "https://discord.com/api/webhooks/...");
        pingField = Pixel.field(settings.getProperty("ping.userId", ""), 16, "e.g. 1234567890");
        pingField.setToolTipText("Discord: Settings > Advanced > Developer Mode, then right-click your name > Copy User ID");
        c.gridx = 0; c.gridy = 0; c.insets = new Insets(0, 0, 8, 14); discord.add(Pixel.label("Webhook URL"), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; c.insets = new Insets(0, 0, 8, 0);
        discord.add(urlField, c);
        c.gridx = 0; c.gridy = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0; c.insets = new Insets(0, 0, 8, 14);
        discord.add(Pixel.label("Ping me"), c);
        JPanel pingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        pingRow.setOpaque(false);
        pingRow.add(pingField);
        pingRow.add(Box.createHorizontalStrut(10));
        pingRow.add(Pixel.note("Your Discord user ID. Leave empty for no ping."));
        c.gridx = 1; c.insets = new Insets(0, 0, 8, 0); discord.add(pingRow, c);
        JPanel discordButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        discordButtons.setOpaque(false);
        Pixel.Button save = new Pixel.Button("Save", false);
        save.addActionListener(e -> { if (saveSettings()) log("*boop* saved!"); });
        Pixel.Button test = new Pixel.Button("Send test post", false);
        test.addActionListener(e -> sendTest());
        // on/off button for adding a picture of the game to bag posts (pink when on)
        boolean picsOn = Boolean.parseBoolean(settings.getProperty("screenshot", "false"));
        shotBox = new Pixel.Check("", picsOn);
        Pixel.Button pics = new Pixel.Button(picsOn ? "Game pictures: On" : "Game pictures: Off", picsOn);
        pics.setToolTipText("When a bag you're watching drops, add a picture of the RotMG window to the Discord post.");
        pics.addActionListener(e -> {
            boolean on = !shotBox.isSelected();
            shotBox.setSelected(on);
            pics.setText(on ? "Game pictures: On" : "Game pictures: Off");
            pics.setPrimary(on);
            saveSettings();
            log(on ? "*grabs the camera* bag posts will include a picture of the game. Send a test post to try it."
                    : "*puts the camera away* no more game pictures in bag posts.");
        });
        discordButtons.add(save);
        discordButtons.add(Box.createHorizontalStrut(8));
        discordButtons.add(test);
        discordButtons.add(Box.createHorizontalStrut(8));
        discordButtons.add(pics);
        c.gridx = 1; c.gridy = 2; c.insets = new Insets(4, 0, 0, 0); discord.add(discordButtons, c);
        section(home, discord, 12);
        buildRecaps(home);

        // ---- Bags to watch: an inventory grid of the real bag sprites
        section(home, new Pixel.Heading("bags to watch", 2), 26);
        JPanel grid = new JPanel(new GridLayout(2, 6, 8, 8));
        grid.setOpaque(false);
        BagType[] order = {BagType.WHITE, BagType.RED, BagType.ORANGE, BagType.GOLD, BagType.EGG, BagType.BLUE,
                BagType.BOOSTED_WHITE, BagType.BOOSTED_RED, BagType.BOOSTED_ORANGE, BagType.BOOSTED_GOLD, BagType.BOOSTED_EGG, BagType.BOOSTED_BLUE};
        for (BagType type : order) {
            boolean on = Boolean.parseBoolean(settings.getProperty(type.settingKey(), String.valueOf(type.defaultOn)));
            Pixel.BagSlot slot = new Pixel.BagSlot(type, on);
            slot.addActionListener(e -> {
                applyWatched();
                saveSettings();
                if (slot.isSelected()) Squeak.play(type); // preview this bag's sound
            });
            bagBoxes.put(type, slot);
            grid.add(slot);
        }
        JPanel gridWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        gridWrap.setOpaque(false);
        gridWrap.add(grid);
        section(home, gridWrap, 12);
        watchingLabel = Pixel.note("");
        watchingLabel.setForeground(Color.BLACK); // black so it stands out over the background picture
        section(home, watchingLabel, 8);
        applyWatched();
        // only post bags to Discord with an item of at least this rarity (enchantment slots)
        JLabel rarityLabel = Pixel.label("Post to Discord only when an item is at least");
        rarityLabel.setForeground(Color.BLACK);
        section(home, rarityLabel, 12);
        JPanel rarityRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rarityRow.setOpaque(false);
        int minR = minRarity();
        rarityRow.add(new LogTabs.Segments(new String[]{"Any", "Uncommon", "Rare", "Legendary", "Divine"}, minR, i -> {
            settings.setProperty("discord.minRarity", String.valueOf(i));
            saveSettings();
            log(i == 0 ? "*wags* every watched bag goes to Discord again."
                    : "*sniffs carefully* only bags with a " + Enchant.values()[i].label + " or better item go to Discord now (the rest still show here).");
        }));
        section(home, rarityRow, 4);

        // ---- Start / stop
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        controls.setOpaque(false);
        startButton = new Pixel.Button("Start watching", true);
        startButton.setFont(Cute.font(Font.BOLD, 14));
        startButton.setBorder(new EmptyBorder(10, 22, 13, 22));
        startButton.addActionListener(e -> { if (processor == null) startSniffer(); else stopSniffer(); });
        autoStartBox = new Pixel.Check("Start watching when the app opens", Boolean.parseBoolean(settings.getProperty("autostart", "false")));
        autoStartBox.addActionListener(e -> saveSettings());
        status = new Pixel.Status();
        status.set("Asleep", false);
        Squeak.enabled = Boolean.parseBoolean(settings.getProperty("sounds", "true"));
        soundBox = new Pixel.Check("Squeaks and boops", Squeak.enabled);
        soundBox.addActionListener(e -> { Squeak.enabled = soundBox.isSelected(); saveSettings(); if (Squeak.enabled) Squeak.boop(); });
        JPanel options = new JPanel();
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        options.setOpaque(false);
        autoStartBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        soundBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        options.add(autoStartBox);
        options.add(soundBox);
        controls.add(startButton);
        controls.add(Box.createHorizontalStrut(18));
        controls.add(status);
        controls.add(Box.createHorizontalStrut(24));
        controls.add(options);
        section(home, controls, 26);

        // ---- Diary
        section(home, new Pixel.Heading("pup diary", 2), 26);
        logArea = new JTextArea(7, 50);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(Cute.font(Font.PLAIN, 12));
        logArea.setForeground(Pixel.INK);
        logArea.setBackground(Pixel.SHEET);
        logArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = Pixel.scroll(logArea);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        home.add(Box.createVerticalStrut(12));
        home.add(scroll);

        // Scroll the whole window when it's smaller than its contents (small screens, display scaling).
        JScrollPane windowScroll = new JScrollPane(root);
        windowScroll.setBorder(null);
        windowScroll.setOpaque(false);
        windowScroll.getViewport().setOpaque(false);
        windowScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE); // keeps the still background clean while scrolling
        windowScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        windowScroll.getVerticalScrollBar().setUnitIncrement(24);
        windowScroll.getHorizontalScrollBar().setUnitIncrement(24);
        windowScroll.getVerticalScrollBar().setUI(new Pixel.ThinBar());
        windowScroll.getHorizontalScrollBar().setUI(new Pixel.ThinBar());
        windowScroll.getVerticalScrollBar().setOpaque(false);
        windowScroll.getHorizontalScrollBar().setOpaque(false);
        windowScroll.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        windowScroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 12));
        backdrop.add(windowScroll, BorderLayout.CENTER);

        frame.setContentPane(backdrop);
        frame.pack();
        // Never open bigger than the screen; the contents scroll instead of getting squashed.
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        boolean tooTall = frame.getHeight() > screen.height;
        int extra = tooTall ? windowScroll.getVerticalScrollBar().getPreferredSize().width : 0; // room for the scrollbar
        frame.setSize(Math.min(frame.getWidth() + extra, screen.width), Math.min(frame.getHeight(), screen.height));
        frame.setMinimumSize(new Dimension(Math.min(frame.getWidth(), 640), 280));
        frame.setLocationRelativeTo(null);
        root.setFocusable(true);
        frame.setVisible(true);
        root.requestFocusInWindow();

        flushEarlyLog();
        if (bg != null) {
            log("*sniff* found background.png in " + new File("").getAbsolutePath() + " (fit: " + settings.getProperty("background.fit") + ", position: " + settings.getProperty("background.position") + ", card opacity " + settings.getProperty("card.opacity") + "%)");
        } else {
            log("*tilts head* no background.png in " + new File("").getAbsolutePath() + ", so using the plain colour.");
        }
        log("*wags tail* hi! paste your webhook URL, send a test post, then press Start watching.");
        if (autoStartBox.isSelected()) startSniffer();
    }

    /** Holds the window's contents: fills the window when there's room, scrolls when there isn't. */
    private static class FitPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return o == SwingConstants.VERTICAL ? r.height - 40 : r.width - 40; }
        // Always fill the window's width (no sideways scrolling). Switching between the two made the
        // scrollbars appear and disappear in a loop, which flashed.
        @Override public boolean getScrollableTracksViewportWidth() {
            return true;
        }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getParent().getHeight() >= getPreferredSize().height;
        }
    }

    /** Smoothly resizes the picture (in steps when shrinking a lot, so it stays sharp instead of grainy). */
    private static java.awt.image.BufferedImage scalePicture(java.awt.image.BufferedImage src, int w, int h) {
        java.awt.image.BufferedImage cur = src;
        int cw = src.getWidth(), ch = src.getHeight();
        do {
            cw = Math.max(w, cw / 2);
            ch = Math.max(h, ch / 2);
            if (cw < w) cw = w;
            if (ch < h) ch = h;
            if (cur.getWidth() <= w * 2 && cur.getHeight() <= h * 2) { cw = w; ch = h; }
            java.awt.image.BufferedImage next = new java.awt.image.BufferedImage(cw, ch, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = next.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(cur, 0, 0, cw, ch, null);
            g.dispose();
            cur = next;
        } while (cw != w || ch != h);
        return cur;
    }

    /** Loads background.png from the folder the app runs in, or returns null if there isn't one. */
    private static java.awt.image.BufferedImage loadBackground() {
        try {
            File f = new File("background.png");
            if (f.exists()) return javax.imageio.ImageIO.read(f);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void section(JPanel page, JComponent c, int gapAbove) {
        if (gapAbove > 0) page.add(Box.createVerticalStrut(gapAbove));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        page.add(c);
    }

    // ---------------------------------------------------------------- actions

    private void applyWatched() {
        Set<BagType> on = EnumSet.noneOf(BagType.class);
        for (Map.Entry<BagType, AbstractButton> e : bagBoxes.entrySet()) if (e.getValue().isSelected()) on.add(e.getKey());
        watchedBags = on;
        for (Account a : accounts) a.bags.setWatched(on);
        if (watchingLabel != null) {
            List<String> names = new java.util.ArrayList<>();
            for (BagType t : BagType.values()) if (on.contains(t)) names.add(t.displayName);
            String list = names.size() <= 1 ? String.join("", names)
                    : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
            watchingLabel.setText(names.isEmpty() ? "Not watching any bags. Pick at least one." : "Watching for " + list + " bags.");
        }
    }

    private void sendTest() {
        String url = urlField.getText().trim();
        if (!checkUrl(url)) return;
        saveSettings();
        log("*runs off to Discord with a test post*");
        byte[] shot = null;
        if (shotBox != null && shotBox.isSelected()) {
            shot = GameShot.capture();
            if (shot == null) log("*tilts head* no game picture in the test post: " + GameShot.lastProblem + ". Open the game and try again.");
        }
        webhook.sendTest(url, pingField.getText().trim(), shot);
    }

    private boolean checkUrl(String url) {
        if (DiscordWebhook.isValidUrl(url)) return true;
        JOptionPane.showMessageDialog(frame,
                "That doesn't look like a Discord webhook URL.\n\n"
                        + "In Discord: Server Settings > Integrations > Webhooks > New Webhook > Copy Webhook URL.\n"
                        + "It should start with https://discord.com/api/webhooks/",
                "Webhook URL", JOptionPane.WARNING_MESSAGE);
        return false;
    }

    private void startSniffer() {
        if (processor != null) return;
        saveSettings();
        if (!DiscordWebhook.isValidUrl(urlField.getText().trim())) {
            log("*tilts head* no webhook URL yet, so i'll only bark in here, not in Discord.");
        }
        router.reset();
        processor = new PacketProcessor();
        processor.setDaemon(true);
        processor.start();
        startButton.setText("Stop watching");
        header.setWatching(true);
        status.set(bagsToday == 0 ? "Watching" : foundText(), true);
        log("*sniff sniff* watching for bags. If you're already in game, change area once (the Nexus works) so I can catch up.");
    }

    private void stopSniffer() {
        if (processor == null) return;
        try {
            processor.stopSniffer();
        } catch (Exception e) {
            log("*whimper* trouble stopping: " + e);
        }
        processor = null;
        startButton.setText("Start watching");
        header.setWatching(false);
        status.set("Asleep", false);
        log("*curls up* nap time~ zzz");
    }

    private String foundText() {
        return "Watching, " + bagsToday + (bagsToday == 1 ? " bag" : " bags") + " found";
    }

    private void onBag(Account account, BagDrop drop) {
        drop.note = droughtNote(drop);
        SwingUtilities.invokeLater(() -> header.setLastWhite(lastDrop("white")));
        String items = String.join(", ", drop.itemNames());
        log((accounts.size() > 1 ? account.prefix() : "") + "*BARK BARK!* " + drop.bagName + " bag in " + (drop.mapName.isEmpty() ? "?" : drop.mapName) + "!!" + (items.isEmpty() ? "" : " " + items)
                + (drop.note == null ? "" : "\n    " + drop.note));
        SwingUtilities.invokeLater(() -> {
            bagsToday++;
            if (processor != null) status.set(foundText(), true);
            if (header != null) header.celebrate();
            if (account.bagLog != null) {
                account.bagLog.add(drop);
                bumpTab(account, 1);
            }
        });
        String url = settings.getProperty("webhook.url", "");
        int min = minRarity();
        boolean rareEnough = min == 0;
        for (ItemInfo it : drop.items()) if (it.enchant.ordinal() >= min && it.enchant != Enchant.NONE) rareEnough = true;
        if (!rareEnough && DiscordWebhook.isValidUrl(url)) {
            log("*sniffs* nothing " + Enchant.values()[min].label + " or better in it, so it stays off Discord.");
        }
        if (rareEnough && DiscordWebhook.isValidUrl(url)) {
            if (shotBox != null && shotBox.isSelected()) {
                drop.gameShot = GameShot.capture();
                if (drop.gameShot == null && GameShot.lastProblem != null) {
                    log("*tilts head* no game picture this time: " + GameShot.lastProblem + ".");
                }
            }
            webhook.sendBag(url, settings.getProperty("ping.userId", ""), drop);
        }
        Squeak.play(drop.type);
    }

    private int minRarity() {
        try {
            int v = Integer.parseInt(settings.getProperty("discord.minRarity", "0"));
            return v >= 0 && v <= 4 ? v : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void onChat(Account account, ChatLog.Line line) {
        SwingUtilities.invokeLater(() -> {
            if (account.chatPanel == null) return;
            account.chatPanel.added(line);
            if (line.kind != ChatLog.Kind.SYSTEM) bumpTab(account, 2);
        });
    }

    /** A realm event: listed on the Events tab; the ones you ticked also squeak and go in the diary. */
    private void onEvent(Account account, EventTracker.Entry e) {
        if (!EventTracker.pings(e.name)) return;
        e.pinged = true;
        Squeak.playEvent(e.name);
        log((accounts.size() > 1 ? account.prefix() : "") + "*ears up* " + e.name + "!" + (e.map.isEmpty() ? "" : " in " + e.map)
                + " (" + String.join(", ", e.how) + ")");
        SwingUtilities.invokeLater(() -> bumpTab(account, java.util.Arrays.asList(tabNames).indexOf("events")));
    }

    // ---------------------------------------------------------------- accounts

    /** Makes another account with its own pages (from any thread). */
    private Account addAccount() {
        Account a = new Account(accounts.size(), host);
        Runnable ui = () -> {
            a.buildPanels();
            for (JComponent page : new JComponent[]{a.bagLog, a.chatPanel, a.dpsPanel, a.aboutPanel, a.vaultPage, a.lootPage, a.bookPage, a.eventsPage, a.famePage, a.tradesPage}) {
                page.putClientProperty("pixel.ownScroll", Boolean.TRUE); // these scroll inside themselves
            }
            sheet.add(a.bagLog, "bag log@" + a.index);
            sheet.add(a.chatPanel, "chat@" + a.index);
            sheet.add(a.dpsPanel, "dps@" + a.index);
            sheet.add(a.aboutPanel, "about me@" + a.index);
            sheet.add(a.vaultPage, "vault@" + a.index);
            sheet.add(a.lootPage, "loot@" + a.index);
            sheet.add(a.bookPage, "collection@" + a.index);
            sheet.add(a.eventsPage, "events@" + a.index);
            sheet.add(a.famePage, "fame@" + a.index);
            sheet.add(a.tradesPage, "trades@" + a.index);
        };
        if (SwingUtilities.isEventDispatchThread()) ui.run();
        else {
            try {
                SwingUtilities.invokeAndWait(ui);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        accounts.add(a);
        if (a.index > 0) log("*ears perk up* another game account! You can switch between them above the tabs.");
        SwingUtilities.invokeLater(this::refreshAccountBar);
        return a;
    }

    /** Counts something new on a tab, on the account's own badges if another account is showing (Swing thread). */
    private void bumpTab(Account account, int tab) {
        if (account == selected) {
            tabs.bump(tab);
        } else {
            account.unread[tab] = Math.min(99, account.unread[tab] + 1);
            refreshAccountBar();
        }
    }

    /** Shows the current tab for the selected account. */
    private void showPage() {
        String name = tabNames[tabs.selected()];
        pages.show(sheet, name.equals("home") ? "home" : name + "@" + selected.index);
        sheet.revalidate(); // the sheet is as tall as the page on show
        sheet.repaint();
    }

    /** The row of account buttons above the tabs (hidden while there's only one account). */
    private void refreshAccountBar() {
        if (accountBar == null) return;
        accountBar.removeAll();
        boolean several = accounts.size() > 1;
        accountBar.setVisible(several);
        if (several) {
            String[] labels = new String[accounts.size()];
            for (int i = 0; i < labels.length; i++) {
                Account a = accounts.get(i);
                int news = 0;
                if (a != selected) for (int n : a.unread) news += n;
                labels[i] = a.label() + (news > 0 ? " (" + news + ")" : "");
            }
            JLabel l = Pixel.label("Account ");
            accountBar.add(l);
            accountBar.add(Box.createHorizontalStrut(6));
            accountBar.add(new LogTabs.Segments(labels, selected.index, i -> {
                int[] mine = tabs.unread();
                System.arraycopy(mine, 0, selected.unread, 0, Math.min(mine.length, selected.unread.length));
                selected = accounts.get(i);
                tabs.setUnread(selected.unread);
                java.util.Arrays.fill(selected.unread, 0);
                showPage();
                refreshAccountBar();
            }));
            accountBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, accountBar.getPreferredSize().height));
            accountBar.setBorder(new EmptyBorder(0, 0, 8, 0));
        }
        accountBar.revalidate();
        accountBar.repaint();
    }

    /**
     * Drought tracker: remembers when each bag colour last dropped (saved in the settings file, so it
     * survives restarts) and returns a celebration line after a long gap, or for the very first one.
     */
    private long lastDrop(String colour) {
        try {
            return Long.parseLong(settings.getProperty("lastDrop." + colour, "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String droughtNote(BagDrop drop) {
        String colour = drop.bagName.replace("Boosted ", "").toLowerCase(java.util.Locale.ROOT);
        String key = "lastDrop." + colour.replace(' ', '_');
        String prev = settings.getProperty(key);
        settings.setProperty(key, String.valueOf(drop.droppedAt));
        SwingUtilities.invokeLater(this::saveSettings);
        if (prev == null) return "*happy howl* your very first " + colour + " bag with me!! \uD83C\uDF89";
        long gap;
        try {
            gap = drop.droppedAt - Long.parseLong(prev.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return droughtLine(colour, gap);
    }

    static String droughtLine(String colour, long gapSeconds) {
        long day = 86_400;
        if (gapSeconds >= 2 * day) return "*happy howl* first " + colour + " bag in " + (gapSeconds / day) + " days!!";
        if (gapSeconds >= day) return "*happy howl* first " + colour + " bag in " + (gapSeconds / 3600) + " hours!!";
        return null;
    }


    // ---------------------------------------------------------------- settings

    private void loadSettings() {
        File f = new File(SETTINGS_FILE);
        if (!f.exists()) return;
        try (InputStream in = new FileInputStream(f)) {
            settings.load(in);
        } catch (Exception e) {
            log("*whimper* couldn't read " + SETTINGS_FILE + ": " + e);
        }
    }

    private synchronized boolean saveSettings() {
        String url = urlField.getText().trim();
        String ping = pingField.getText().trim();
        if (!ping.isEmpty() && !DiscordWebhook.isValidDiscordId(ping)) {
            log("*tilts head* the ping user ID should be a number like 123456789012345678. i won't use it until it's fixed.");
        }
        settings.setProperty("webhook.url", url);
        settings.setProperty("ping.userId", ping);
        settings.setProperty("autostart", String.valueOf(autoStartBox != null && autoStartBox.isSelected()));
        if (soundBox != null) settings.setProperty("sounds", String.valueOf(soundBox.isSelected()));
        if (shotBox != null) settings.setProperty("screenshot", String.valueOf(shotBox.isSelected()));
        for (Map.Entry<BagType, AbstractButton> e : bagBoxes.entrySet()) {
            settings.setProperty(e.getKey().settingKey(), String.valueOf(e.getValue().isSelected()));
        }
        try (OutputStream out = new FileOutputStream(SETTINGS_FILE)) {
            settings.store(out, "White Bag Notifier settings (keep your webhook URL private)");
            return true;
        } catch (Exception e) {
            log("*whimper* couldn't save settings: " + e);
            return false;
        }
    }

    // ---------------------------------------------------------------- logging

    private final StringBuilder earlyLog = new StringBuilder();

    private void log(String msg) {
        String line = "[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + LogTabs.noEmoji(msg) + "\n";
        System.out.print(line);
        SwingUtilities.invokeLater(() -> {
            if (logArea == null) {
                earlyLog.append(line);
                return;
            }
            logArea.append(line);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void flushEarlyLog() {
        if (earlyLog.length() > 0) {
            logArea.append(earlyLog.toString());
            earlyLog.setLength(0);
        }
    }
}
