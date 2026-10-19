package whitebag;

import packets.Packet;
import packets.PacketType;
import packets.data.TradeItemData;
import packets.data.enums.TradeResult;
import packets.incoming.TradeAcceptedPacket;
import packets.incoming.TradeChangedPacket;
import packets.incoming.TradeDonePacket;
import packets.incoming.TradeStartPacket;
import packets.outgoing.ChangeTradePacket;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every finished trade: who with, what you gave and what you got (with each item's rarity), kept in
 * trade-log.txt next to the app. Only reads the trade window's packets, like the rest of the app.
 */
final class TradeLog {
    static final class Trade {
        long at;              // Unix seconds
        String account = "";
        String partner = "";
        final List<Integer> gave = new ArrayList<>(), got = new ArrayList<>();
        final List<Enchant> gaveRarity = new ArrayList<>(), gotRarity = new ArrayList<>();

        String line() {
            return at + "|" + account.replace('|', '/') + "|" + partner.replace('|', '/') + "|" + items(gave, gaveRarity) + "|" + items(got, gotRarity);
        }

        private static String items(List<Integer> ids, List<Enchant> r) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) {
                if (sb.length() > 0) sb.append(',');
                sb.append(ids.get(i));
                Enchant e = i < r.size() ? r.get(i) : Enchant.NONE;
                if (e != Enchant.NONE) sb.append(':').append(e.ordinal());
            }
            return sb.toString();
        }

        static Trade parse(String l) {
            String[] f = l.split("\\|", -1);
            if (f.length < 5) return null;
            try {
                Trade t = new Trade();
                t.at = Long.parseLong(f[0]);
                t.account = f[1];
                t.partner = f[2];
                read(f[3], t.gave, t.gaveRarity);
                read(f[4], t.got, t.gotRarity);
                return t;
            } catch (Exception e) {
                return null;
            }
        }

        private static void read(String s, List<Integer> ids, List<Enchant> r) {
            for (String p : s.split(",")) {
                if (p.trim().isEmpty()) continue;
                String[] q = p.split(":");
                ids.add(Integer.parseInt(q[0].trim()));
                int e = q.length > 1 ? Integer.parseInt(q[1].trim()) : 0;
                r.add(e >= 0 && e < Enchant.values().length ? Enchant.values()[e] : Enchant.NONE);
            }
        }
    }

    private static final String FILE = "trade-log.txt";
    private static List<Trade> trades;

    static synchronized List<Trade> all() {
        if (trades == null) {
            trades = new ArrayList<>();
            try {
                for (String l : Files.readAllLines(Paths.get(FILE), StandardCharsets.UTF_8)) {
                    Trade t = Trade.parse(l);
                    if (t != null) trades.add(t);
                }
            } catch (Exception ignored) {
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(trades));
    }

    private static synchronized void add(Trade t) {
        all();
        trades.add(t);
        try {
            Files.write(Paths.get(FILE), Collections.singletonList(t.line()), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    // ---------------------------------------------------------------- one account's trade window

    private final java.util.function.Supplier<String> account;
    private final Runnable changed;
    private TradeItemData[] mine, theirs;
    private boolean[] myOffer, theirOffer;
    private String partner = "";

    TradeLog(java.util.function.Supplier<String> account, Runnable changed) {
        this.account = account;
        this.changed = changed;
    }

    void register(Bus bus) {
        bus.register(PacketType.TRADESTART, this::onStart);
        bus.register(PacketType.TRADECHANGED, p -> theirOffer = ((TradeChangedPacket) p).offer);
        bus.register(PacketType.CHANGETRADE, p -> myOffer = ((ChangeTradePacket) p).offer);
        bus.register(PacketType.TRADEACCEPTED, p -> {
            TradeAcceptedPacket a = (TradeAcceptedPacket) p;
            if (a.clientOffer != null) myOffer = a.clientOffer;
            if (a.partnerOffer != null) theirOffer = a.partnerOffer;
        });
        bus.register(PacketType.TRADEDONE, this::onDone);
    }

    private synchronized void onStart(Packet packet) {
        TradeStartPacket p = (TradeStartPacket) packet;
        mine = p.clientItems;
        theirs = p.partnerItems;
        partner = p.partnerName == null ? "" : DpsTracker.cleanName(p.partnerName);
        myOffer = null;
        theirOffer = null;
    }

    private synchronized void onDone(Packet packet) {
        TradeDonePacket p = (TradeDonePacket) packet;
        if (p.code != TradeResult.Successful || mine == null || theirs == null) {
            mine = theirs = null;
            return;
        }
        Trade t = new Trade();
        t.at = System.currentTimeMillis() / 1000;
        t.account = account.get() == null ? "" : account.get();
        t.partner = partner;
        pick(mine, myOffer, t.gave, t.gaveRarity);
        pick(theirs, theirOffer, t.got, t.gotRarity);
        mine = theirs = null;
        if (t.gave.isEmpty() && t.got.isEmpty()) return;
        add(t);
        changed.run();
    }

    private static void pick(TradeItemData[] items, boolean[] offer, List<Integer> ids, List<Enchant> rarity) {
        if (offer == null) return;
        for (int i = 0; i < items.length && i < offer.length; i++) {
            if (!offer[i] || items[i] == null || items[i].item <= 0) continue;
            ids.add(items[i].item);
            rarity.add(Enchant.of(items[i].uniqueData));
        }
    }
}
