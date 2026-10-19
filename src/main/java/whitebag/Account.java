package whitebag;

import packets.data.enums.StatType;

import javax.swing.*;
import java.util.Set;

/**
 * One game account: its own packet dispatcher, trackers (bags, chat, DPS, character/pet/quests, vault)
 * and the pages that show them. With two game clients open there are two of these.
 */
final class Account {
    interface Host {
        void bag(Account a, BagDrop drop);

        void chat(Account a, ChatLog.Line line);

        /** A realm event this account hasn't seen yet. */
        void event(Account a, EventTracker.Entry e);

        void log(String text);

        /** Something about the account list changed (a new account, or a new name). */
        void accountsChanged();

        /** The account that owns files saved before accounts were tracked (assigned to the first one seen). */
        String legacyOwner(String claimingKey);

        Set<BagType> watched();
    }

    final int index;
    private final Host host;
    private volatile String key;   // the game's account id; null until this account has been identified
    private volatile String label = "";
    final Bus bus = new Bus();
    /** New-thing counts on the tabs while another account is shown. */
    final int[] unread = new int[16];

    final BagTracker bags;
    final ChatLog chat;
    final DpsTracker dps;
    final MeTracker me;
    final VaultStore vault;
    final EventTracker events;
    final TradeLog trades;
    private String visitMap;
    private long visitStart;

    LogTabs.BagLogPanel bagLog;
    LogTabs.ChatPanel chatPanel;
    LogTabs.DpsPanel dpsPanel;
    AboutMe.Panel aboutPanel;
    MoreTabs.VaultPage vaultPage;
    MoreTabs.LootPage lootPage;
    BookPage bookPage;
    KillsPage killsPage;
    MoreTabs.EventsPage eventsPage;
    MoreTabs.FamePage famePage;
    MoreTabs.TradesPage tradesPage;

    Account(int index, Host host) {
        this.index = index;
        this.host = host;
        bags = new BagTracker(d -> host.bag(this, d));
        chat = new ChatLog(l -> host.chat(this, l));
        dps = new DpsTracker(() -> {
            if (dpsPanel != null) dpsPanel.changed();
        });
        me = new MeTracker(this::meChanged);
        vault = new VaultStore(() -> {
            if (vaultPage != null) vaultPage.changed();
        }, t -> host.log(prefix() + t));
        bags.onAnyBag = d -> {
            LootStats.add(d, key);
            if (lootPage != null) lootPage.changed();
            if (bookPage != null) bookPage.changed();
        };
        events = new EventTracker(e -> host.event(this, e), () -> {
            if (eventsPage != null) eventsPage.changed();
        });
        trades = new TradeLog(() -> key, () -> {
            if (tradesPage != null) tradesPage.changed();
        });
        trades.register(bus);
        // each visit to a dungeon is a run, for drop rates
        bus.register(packets.PacketType.MAPINFO, p -> {
            packets.incoming.MapInfoPacket m = (packets.incoming.MapInfoPacket) p;
            long now = System.currentTimeMillis();
            synchronized (this) {
                if (visitMap != null) RunLog.add(visitMap, key, visitStart, now);
                visitMap = BagTracker.cleanName(m.name, m.displayName);
                visitStart = now;
            }
            if (lootPage != null) lootPage.changed();
            if (bookPage != null) bookPage.changed();
        });
        dps.onRunEnd = (run, boards) -> {
            History.addRun(run, boards, key);
            if (killsPage != null) killsPage.changed();
        };
        bags.setWatched(host.watched());
        bags.register(bus);
        chat.register(bus);
        dps.register(bus);
        me.register(bus);
        vault.register(bus);
        // realm events now come from the RealmStock feed (Events tab), not from this client
    }

    /** Builds this account's pages (call on the Swing thread). */
    void buildPanels() {
        bagLog = new LogTabs.BagLogPanel();
        chatPanel = new LogTabs.ChatPanel(chat);
        dpsPanel = new LogTabs.DpsPanel(dps);
        aboutPanel = new AboutMe.Panel(me);
        vaultPage = new MoreTabs.VaultPage(vault);
        eventsPage = new MoreTabs.EventsPage(events);
        famePage = new MoreTabs.FamePage(me);
        tradesPage = new MoreTabs.TradesPage(t -> key != null && (key.equals(t.account) || (t.account.isEmpty() && key.equals(host.legacyOwner(null)))));
        killsPage = new KillsPage(d -> key != null && (key.equals(d.account) || (d.account.isEmpty() && key.equals(host.legacyOwner(null)))));
        bookPage = new BookPage(e -> key != null && (key.equals(e.account) || (e.account.isEmpty() && key.equals(host.legacyOwner(null)))),
                r -> key != null && (key.equals(r.account) || (r.account.isEmpty() && key.equals(host.legacyOwner(null)))));
        lootPage = new MoreTabs.LootPage(e -> key != null && (key.equals(e.account) || (e.account.isEmpty() && key.equals(host.legacyOwner(null)))),
                r -> key != null && (key.equals(r.account) || (r.account.isEmpty() && key.equals(host.legacyOwner(null)))));
    }

    String key() {
        return key;
    }

    boolean claimed() {
        return key != null;
    }

    /** Ties this account to the game's account id, and loads its saved vault. */
    synchronized void claim(String accountKey) {
        if (key != null) return;
        key = accountKey;
        String owner = host.legacyOwner(accountKey);
        vault.useFile(accountKey.equals(owner) ? "vault.txt" : "vault-" + Integer.toHexString(accountKey.hashCode()) + ".txt");
        if (lootPage != null) lootPage.changed();
        host.accountsChanged();
    }

    String label() {
        return label.isEmpty() ? "Account " + (index + 1) : label;
    }

    void setLabel(String name) {
        if (name == null || name.isEmpty() || name.equals(label)) return;
        label = name;
        host.accountsChanged();
    }

    /** "[flushed] " in the diary when more than one account is open. */
    String prefix() {
        return label.isEmpty() ? "" : "[" + label + "] ";
    }

    private void meChanged() {
        if (aboutPanel != null) aboutPanel.changed();
        MeTracker.Thing t = me.character();
        if (t != null) {
            String n = DpsTracker.cleanName(t.s(StatType.NAME_STAT));
            if (n != null && !n.isEmpty()) SwingUtilities.invokeLater(() -> setLabel(n));
        }
    }
}
