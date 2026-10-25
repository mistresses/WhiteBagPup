package whitebag;

import packets.Packet;
import packets.PacketType;
import packets.incoming.VaultContentPacket;
import packets.packetcapture.register.Register;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What's in your vault, potion storage and gift chest. The game sends this when you enter your vault;
 * the last copy is kept in vault.txt next to the jar so the tab still shows it later.
 */
final class VaultStore {
    interface Listener {
        void changed();
    }

    private final Listener listener;
    private final java.util.function.Consumer<String> log;
    /** The game sends the vault in several pieces; these collect them until the last one arrives (like Puppy). */
    private final List<Integer> inVault = new ArrayList<>(), inPotions = new ArrayList<>(), inGifts = new ArrayList<>();
    private boolean collecting = false;
    private int pieces = 0;
    private int[] vault = new int[0], potions = new int[0], gifts = new int[0];
    private int potionMax = -1;
    private long at = 0;
    /** vault-<account>.txt once the account is known (see useFile). */
    private volatile String file;
    /** Called with {vault, gifts} once a whole vault has been read (for the Collection). */
    volatile java.util.function.Consumer<int[][]> onRead = v -> { };

    VaultStore(Listener listener, java.util.function.Consumer<String> log) {
        this.listener = listener;
        this.log = log;
    }

    void register(Bus bus) {
        bus.register(PacketType.VAULT_UPDATE, this::onVault);
    }

    synchronized int[] vault() { return vault.clone(); }
    synchronized int[] potions() { return potions.clone(); }
    synchronized int[] gifts() { return gifts.clone(); }
    synchronized int potionMax() { return potionMax; }
    synchronized long updatedAt() { return at; }

    private void onVault(Packet packet) {
        VaultContentPacket p = (VaultContentPacket) packet;
        boolean done;
        int items;
        synchronized (this) {
            if (!collecting) { // first piece of a new vault visit
                inVault.clear();
                inPotions.clear();
                inGifts.clear();
                pieces = 0;
            }
            add(inVault, p.vaultContents);
            add(inPotions, p.potionContents);
            add(inGifts, p.giftContents);
            pieces++;
            if (p.currentPotionMax > 0) potionMax = p.currentPotionMax;
            collecting = !p.lastVaultPacket;
            // show what we have so far, and keep it once the last piece is in
            vault = toArray(inVault);
            potions = toArray(inPotions);
            gifts = toArray(inGifts);
            at = System.currentTimeMillis();
            done = p.lastVaultPacket;
            items = count(vault) + count(potions) + count(gifts);
            if (done) save();
        }
        if (done) {
            try {
                onRead.accept(new int[][]{vault(), gifts()});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (log != null && done) {
            log.accept("*sniff sniff* read your vault (" + items + " items in " + pieces + (pieces == 1 ? " piece" : " pieces") + "). It's in the Vault tab!");
        }
        try {
            listener.changed();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void add(List<Integer> to, int[] from) {
        if (from == null) return;
        for (int v : from) to.add(v);
    }

    private static int[] toArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    private static int count(int[] a) {
        int n = 0;
        for (int v : a) if (v > 0) n++;
        return n;
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        lines.add("at|" + at);
        lines.add("potionMax|" + potionMax);
        lines.add("vault|" + join(vault));
        lines.add("potions|" + join(potions));
        lines.add("gifts|" + join(gifts));
        try {
            if (file != null) Files.write(Paths.get(file), lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    /** Uses this file for the account's vault, loading what's saved in it. */
    synchronized void useFile(String path) {
        file = path;
        load();
        try {
            listener.changed();
        } catch (Exception ignored) {
        }
    }

    private void load() {
        try {
            for (String l : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
                String[] f = l.split("\\|", 2);
                if (f.length < 2) continue;
                switch (f[0]) {
                    case "at": at = Long.parseLong(f[1]); break;
                    case "potionMax": potionMax = Integer.parseInt(f[1]); break;
                    case "vault": vault = split(f[1]); break;
                    case "potions": potions = split(f[1]); break;
                    case "gifts": gifts = split(f[1]); break;
                    default:
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String join(int[] a) {
        StringBuilder sb = new StringBuilder();
        for (int v : a) {
            if (sb.length() > 0) sb.append(',');
            sb.append(v);
        }
        return sb.toString();
    }

    private static int[] split(String s) {
        if (s.trim().isEmpty()) return new int[0];
        return Arrays.stream(s.split(",")).mapToInt(x -> Integer.parseInt(x.trim())).toArray();
    }
}
