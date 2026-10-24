package whitebag;

import packets.Packet;
import packets.data.ObjectData;
import packets.data.StatData;
import packets.incoming.CreateSuccessPacket;
import packets.incoming.UpdatePacket;
import packets.outgoing.HelloPacket;
import packets.packetcapture.ConnectionRouter;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sends each game connection's packets to the right account.
 * <ul>
 *   <li>A connection is identified by the account id on your own character, which the server sends when you
 *       load into an area (or straight away by its login token, if that account has been seen before).</li>
 *   <li>Until then its packets go to the first account that isn't taken yet, so with one game open nothing waits.
 *       If every account is taken, the packets are held and replayed once the connection is identified.</li>
 * </ul>
 */
final class AccountRouter implements ConnectionRouter.Handler, ConnectionRouter.Closed {
    private static final int ACCOUNT_ID_STAT = 38, NAME_STAT = 31, MAX_HELD = 4000;

    private static final class Conn {
        Account account;
        boolean confirmed;
        int myId = -1;
        String token;
        final ArrayDeque<Packet> held = new ArrayDeque<>();
    }

    private final List<Account> accounts;
    private final Supplier<Account> newAccount;
    private final Map<Integer, Conn> conns = new HashMap<>();
    private final Map<String, Account> byKey = new HashMap<>();
    private final Map<String, Account> byToken = new HashMap<>();
    /** "I only play one account" (Home): every connection goes to the first account and no others are made. */
    static volatile boolean singleAccount = false;

    /**
     * @param accounts   the app's account list (the first one exists from the start, unclaimed)
     * @param newAccount makes, registers and shows another account
     */
    AccountRouter(List<Account> accounts, Supplier<Account> newAccount) {
        this.accounts = accounts;
        this.newAccount = newAccount;
    }

    /** Forget connections (the sniffer restarts its connection numbers when it restarts). */
    synchronized void reset() {
        conns.clear();
    }

    @Override
    public synchronized void packet(int connectionId, Packet p) {
        Conn c = conns.computeIfAbsent(connectionId, k -> new Conn());

        if (p instanceof HelloPacket) {
            c.token = ((HelloPacket) p).accessToken;
            Account a = c.token == null ? null : byToken.get(c.token);
            if (a != null) bind(c, a, true);
        } else if (p instanceof CreateSuccessPacket) {
            c.myId = ((CreateSuccessPacket) p).objectId;
        } else if (p instanceof UpdatePacket && c.myId != -1 && !c.confirmed) {
            identify(c, (UpdatePacket) p);
        }

        if (c.account == null) {
            Account free = freeAccount(c);
            if (free != null) bind(c, free, false); // with one game open, go straight to the first account
        }
        if (c.account != null) {
            c.account.bus.emit(p);
        } else {
            c.held.add(p);
            while (c.held.size() > MAX_HELD) c.held.poll();
        }
    }

    @Override
    public synchronized void closed(int connectionId) {
        conns.remove(connectionId);
    }

    /** Finds your own character in the update and reads its account id. */
    private void identify(Conn c, UpdatePacket u) {
        if (u.newObjects == null) return;
        for (ObjectData o : u.newObjects) {
            if (o.status == null || o.status.objectId != c.myId || o.status.stats == null) continue;
            String acc = null, name = null;
            for (StatData s : o.status.stats) {
                if (s.statTypeNum == ACCOUNT_ID_STAT && s.stringStatValue != null && !s.stringStatValue.isEmpty()) acc = s.stringStatValue;
                if (s.statTypeNum == NAME_STAT && s.stringStatValue != null) name = DpsTracker.cleanName(s.stringStatValue);
            }
            if (acc == null) return;
            Account a = accountFor(acc, c);
            if (name != null) a.setLabel(name);
            if (c.token != null) byToken.put(c.token, a);
            bind(c, a, true);
            return;
        }
    }

    private Account accountFor(String key, Conn asking) {
        if (singleAccount) {
            Account first = accounts.get(0);
            if (!first.claimed()) first.claim(key);
            byKey.put(key, first);
            return first;
        }
        Account a = byKey.get(key);
        if (a != null) return a;
        // an account nobody has claimed, and no other connection is using
        for (Account x : accounts) {
            if (!x.claimed() && !usedByOther(x, asking)) {
                a = x;
                break;
            }
        }
        if (a == null) a = newAccount.get();
        a.claim(key);
        byKey.put(key, a);
        return a;
    }

    /** An unclaimed account no other connection is using yet. */
    private Account freeAccount(Conn asking) {
        if (singleAccount) return accounts.get(0);
        for (Account x : accounts) if (!x.claimed() && !usedByOther(x, asking)) return x;
        return null;
    }

    private boolean usedByOther(Account a, Conn asking) {
        for (Conn o : conns.values()) if (o != asking && o.account == a) return true;
        return false;
    }

    private void bind(Conn c, Account a, boolean confirmed) {
        c.confirmed = c.confirmed || confirmed;
        if (c.account == a) return;
        c.account = a;
        while (!c.held.isEmpty()) a.bus.emit(c.held.poll()); // replay what arrived before we knew the account
    }
}
