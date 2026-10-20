package whitebag;

import packets.Packet;
import packets.PacketType;
import packets.data.ObjectData;
import packets.data.ObjectStatusData;
import packets.data.QuestData;
import packets.data.StatData;
import packets.data.enums.StatType;
import packets.incoming.CreateSuccessPacket;
import packets.incoming.NewTickPacket;
import packets.incoming.QuestFetchResponsePacket;
import packets.incoming.UpdatePacket;
import packets.incoming.pets.ActivePetPacket;
import packets.packetcapture.register.Register;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps track of "you": your current character's stats and gear, your active pet, and the
 * Tinkerer quests the game last sent (it sends them when you open the Tinkerer's quest window).
 */
public class MeTracker {
    public interface Listener {
        void changed();
    }

    /** A snapshot of one object's stats. */
    public static final class Thing {
        public int objectType = -1;
        float x = Float.NaN, y = Float.NaN;
        /** Smoothed distance to you in tiles, and how many times we've measured it. */
        double near = Double.NaN;
        int samples = 0;
        public final Map<StatType, Integer> ints = new EnumMap<>(StatType.class);
        public final Map<StatType, String> strings = new EnumMap<>(StatType.class);

        public int i(StatType t, int fallback) {
            Integer v = ints.get(t);
            return v == null ? fallback : v;
        }

        public String s(StatType t) {
            String v = strings.get(t);
            return v == null ? "" : v;
        }

        boolean hasPetStats() {
            for (StatType t : ints.keySet()) if (t.name().startsWith("PET_")) return true;
            return false;
        }

        Thing copy() {
            Thing c = new Thing();
            c.objectType = objectType;
            c.x = x;
            c.y = y;
            c.near = near;
            c.samples = samples;
            c.ints.putAll(ints);
            c.strings.putAll(strings);
            return c;
        }
    }

    /** Stats that change every few moments (health, effects, timers): kept, but they don't trigger a redraw. */
    private static final java.util.Set<String> NOISY = new java.util.HashSet<>(java.util.Arrays.asList(
            "HP_STAT", "MP_STAT", "EXP_STAT", "CONDITION_STAT", "NEW_CON_STAT", "BREATH_STAT", "XP_TIMER_STAT", "LD_TIMER_STAT",
            "LT_TIMER_STAT", "ANIMATION_STAT", "ANIMATION_TIMESTAMP_STAT", "SIZE_STAT", "TEX1_STAT", "TEX2_STAT"));

    private final Listener listener;
    private int myId = -1;
    private Thing me;
    private final Map<Integer, Thing> others = new HashMap<>(); // objects that might be your pet
    private int activePetInstance = -1;
    private int charId = -1;
    /** Your account id (kept across map changes; the character snapshot is reset on each one). */
    private volatile String accountId = "";
    private volatile String mapName = "";
    private volatile String mapTitle = "";
    private volatile String accessToken; // memory only; used for the character-list request, like Puppy
    private volatile String petNote = "";
    /** Exaltation progress per class: HP, MP, ATT, DEF, SPD, DEX, VIT, WIS (each counts up to 75). */
    private final Map<Integer, int[]> exalts = new HashMap<>();
    /** Fame when we first saw each character this session, and when. */
    private final Map<Integer, long[]> fameStart = new HashMap<>();
    private List<QuestData> quests = Collections.emptyList();
    private long questsAt = 0;

    public MeTracker(Listener listener) {
        this.listener = listener;
    }

    public void register(Bus bus) {
        bus.register(PacketType.CREATE_SUCCESS, this::onCreateSuccess);
        bus.register(PacketType.UPDATE, this::onUpdate);
        bus.register(PacketType.NEWTICK, this::onNewTick);
        bus.register(PacketType.ACTIVEPETUPDATE, this::onActivePet);
        bus.register(PacketType.QUEST_FETCH_RESPONSE, this::onQuests);
        // The server never sends your own position back to you, so read it from your game's movement messages.
        bus.register(PacketType.MOVE, this::onMove);
        bus.register(PacketType.MAPINFO, p -> {
            packets.incoming.MapInfoPacket m = (packets.incoming.MapInfoPacket) p;
            mapName = m.displayName;
            mapTitle = BagTracker.cleanName(m.name, m.displayName);
            newVisit();
        });
        bus.register(PacketType.EXALTATION_BONUS_CHANGED, this::onExalt);
        bus.register(PacketType.HELLO, p -> accessToken = ((packets.outgoing.HelloPacket) p).accessToken);
        PetInfo.load();
        bus.register(PacketType.GOTO, this::onGoto);
    }

    private void onMove(Packet packet) {
        packets.outgoing.MovePacket p = (packets.outgoing.MovePacket) packet;
        if (p.records == null || p.records.length == 0) return;
        packets.data.MoveRecord last = p.records[p.records.length - 1];
        if (last == null || last.pos == null) return;
        synchronized (this) {
            if (me == null) return;
            me.x = last.pos.x;
            me.y = last.pos.y;
        }
    }

    private void onGoto(Packet packet) {
        packets.incoming.GotoPacket p = (packets.incoming.GotoPacket) packet;
        if (p.position == null) return;
        synchronized (this) {
            if (me == null || p.objectId != myId) return;
            me.x = p.position.x;
            me.y = p.position.y;
        }
    }

    // ---------------------------------------------------------------- snapshots for the UI

    public synchronized Thing character() {
        return me == null ? null : me.copy();
    }

    /**
     * Your pet if it's in view. Best match: the game's active-pet id, then your account as owner,
     * then the pet that has stayed closest to you (your pet follows you around; other people's don't).
     */
    public synchronized Thing pet() {
        if (me == null) return null;
        String account = me.s(StatType.ACCOUNT_ID_STAT);
        Thing best = null;
        double bestScore = 0;
        for (Thing t : others.values()) {
            double score = 0;
            if (activePetInstance != -1 && t.i(StatType.PET_INSTANCE_ID_STAT, -2) == activePetInstance) score = 1000;
            else if (!account.isEmpty() && account.equals(t.s(StatType.OWNER_ACCOUNT_ID_STAT))) score = 500;
            else if (isPetClass(t.objectType) && t.samples >= 8 && t.near < 3.0) score = 100 - t.near; // closer wins
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best == null ? null : best.copy();
    }

    static boolean isPetClass(int type) {
        try {
            return "Pet".equalsIgnoreCase(assets.IdToAsset.getClazz(type));
        } catch (Throwable e) {
            return false;
        }
    }

    public synchronized List<QuestData> quests() {
        return new ArrayList<>(quests);
    }

    public synchronized long questsAt() {
        return questsAt;
    }

    /** Item ids currently in your inventory and backpack (not equipped gear). */
    public synchronized List<Integer> carried() {
        List<Integer> out = new ArrayList<>();
        if (me == null) return out;
        for (StatType t : StatType.values()) {
            String n = t.name();
            boolean bag = (n.startsWith("INVENTORY_") && !n.equals("INVENTORY_0_STAT") && !n.equals("INVENTORY_1_STAT")
                    && !n.equals("INVENTORY_2_STAT") && !n.equals("INVENTORY_3_STAT")) || n.startsWith("BACKPACK_") && !n.equals("BACKPACK_SLOTS_STAT");
            if (!bag) continue;
            int v = me.i(t, -1);
            if (v > 0) out.add(v);
        }
        return out;
    }

    // ---------------------------------------------------------------- packets

    private void onCreateSuccess(Packet packet) {
        synchronized (this) {
            myId = ((CreateSuccessPacket) packet).objectId;
            charId = ((CreateSuccessPacket) packet).charId;
            me = null;
            others.clear();
        }
        fire();
        // The game server only hands out the character list (with your pet's abilities) in these two places.
        String map = mapName;
        String token = accessToken;
        if (token != null && !token.isEmpty() && ("Pet Yard".equals(map) || "Daily Quest Room".equals(map))) {
            Thread t = new Thread(() -> {
                int n = PetInfo.fetchFromAccount(token, () -> accountId);
                petNote = n > 0 ? "" : "Couldn't get your pet's abilities from the game server just now.";
                fire();
            }, "pet-abilities");
            t.setDaemon(true);
            t.start();
        }
    }

    public synchronized int[] exalts(int classType) {
        int[] e = exalts.get(classType);
        return e == null ? null : e.clone();
    }

    /** Fame gained in one visit to an area (a dungeon run, a trip to the realm...). */
    public static final class FameVisit {
        public final String area;
        public final int charId;
        public final long startMs;
        public volatile long endMs;
        public volatile int startFame = -1, endFame = -1;

        FameVisit(String area, int charId, long startMs) {
            this.area = area;
            this.charId = charId;
            this.startMs = startMs;
            this.endMs = startMs;
        }

        public int gained() {
            return startFame < 0 || endFame < 0 ? 0 : endFame - startFame;
        }
    }

    private final java.util.List<FameVisit> visits = new java.util.ArrayList<>();
    /** {time ms, fame} each time the current character's fame changes this session. */
    private final java.util.List<long[]> fameSamples = new java.util.ArrayList<>();
    private int samplesChar = -1;

    /** Fame over this session for the current character, oldest first. */
    public synchronized java.util.List<long[]> fameSamples() {
        java.util.List<long[]> out = new java.util.ArrayList<>();
        if (samplesChar == charId) for (long[] s : fameSamples) out.add(s.clone());
        return out;
    }

    /** The current character's fame right now, or -1. */
    public synchronized int fameNow() {
        return me == null ? -1 : me.i(StatType.CURR_FAME_STAT, -1);
    }
    private FameVisit visit;

    /** This session's visits for the current character, newest first. */
    public synchronized java.util.List<FameVisit> fameVisits() {
        java.util.List<FameVisit> out = new java.util.ArrayList<>();
        for (int i = visits.size() - 1; i >= 0; i--) if (visits.get(i).charId == charId) out.add(visits.get(i));
        return out;
    }

    private synchronized void newVisit() {
        if (visit != null) {
            visit.endMs = Math.max(visit.endMs, System.currentTimeMillis());
            save(visit);
        }
        visit = null; // starts once the character's fame is known in the new area
    }

    /** Saves the visit in progress (when the app closes). */
    public synchronized void flush() {
        newVisit();
        closePlay();
    }

    // ---------------------------------------------------------------- play time

    /** Time in game counts while the game sends updates; a gap of 2 minutes or more ends a stretch. */
    private static final long PLAY_GAP_MS = 120_000L;
    /** Stretches are saved at least this often, so a crash loses little. */
    private static final long PLAY_SAVE_MS = 10 * 60_000L;
    private long playStart = -1, playLast = -1, sessionPlayMs;
    private String playCls = "";

    private void notePlay() {
        long now = System.currentTimeMillis();
        String cls = AboutMe.objectName(me.objectType, "");
        if (playStart >= 0 && (now - playLast > PLAY_GAP_MS || !cls.equals(playCls) || now - playStart > PLAY_SAVE_MS)) {
            boolean cont = now - playLast <= PLAY_GAP_MS;
            closePlay();
            if (cont && cls.equals(playCls)) playStart = playLast; // carry on without losing the moment in between
        }
        if (playStart < 0) {
            playStart = now;
            playCls = cls;
        }
        playLast = now;
    }

    private void closePlay() {
        if (playStart >= 0 && playLast > playStart) {
            History.addPlay(accountId, playCls, playStart, playLast);
            sessionPlayMs += playLast - playStart;
        }
        playStart = -1;
    }

    /** Saves the stretch in progress (without ending it), so a recap posted now includes it. */
    public synchronized void checkpointPlay() {
        boolean open = playStart >= 0 && System.currentTimeMillis() - playLast <= PLAY_GAP_MS;
        long last = playLast;
        closePlay();
        if (open) playStart = last;
    }

    /**
     * Play time in milliseconds for this account: {this session, today, last 7 days, all time (since the
     * app started keeping track)}.
     */
    public synchronized long[] playTime() {
        long now = System.currentTimeMillis();
        long open = playStart >= 0 && now - playLast <= PLAY_GAP_MS ? playLast - playStart : 0;
        long today = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond();
        long week = now / 1000 - 7 * 86_400L;
        long[] out = {sessionPlayMs + open, open, open, open};
        for (History.Play p : History.play()) {
            if (!p.account.equals(accountId)) continue;
            out[1] += p.within(today, Long.MAX_VALUE) * 1000;
            out[2] += p.within(week, Long.MAX_VALUE) * 1000;
            out[3] += (p.end - p.start) * 1000;
        }
        return out;
    }

    private void save(FameVisit v) {
        History.addFame(accountId, me == null ? "" : me.s(StatType.NAME_STAT), me == null ? "" : AboutMe.objectName(me.objectType, ""), v.area, v.startMs, v.endMs, v.gained());
    }

    /** {fame gained, milliseconds since first seen} for the current character this session, or null. */
    public synchronized long[] fameSession() {
        long[] start = fameStart.get(charId);
        if (start == null || me == null) return null;
        int now = me.i(StatType.CURR_FAME_STAT, -1);
        if (now < 0) return null;
        return new long[]{now - start[0], System.currentTimeMillis() - start[1]};
    }

    private void onExalt(Packet packet) {
        packets.incoming.ExaltationUpdatePacket p = (packets.incoming.ExaltationUpdatePacket) packet;
        synchronized (this) {
            int[] v = {p.healthProgress, p.manaProgress, p.attackProgress, p.defenseProgress,
                    p.speedProgress, p.dexterityProgress, p.vitalityProgress, p.wisdomProgress};
            exalts.put((int) p.objType, v);
            History.addExalt(accountId, (int) p.objType, v);
        }
        fire();
    }

    public String petNote() {
        return petNote;
    }

    /** What we know about your current character's pet: from your character list or a pet seen with full details. */
    public synchronized PetInfo petInfo() {
        synchronized (PetInfo.class) {
            Integer inst = PetInfo.petOfChar.get(accountId + ":" + charId);
            if (inst != null && PetInfo.byInstance.containsKey(inst)) return PetInfo.byInstance.get(inst);
            Thing seen = pet();
            if (seen != null) return PetInfo.byInstance.get(seen.i(StatType.PET_INSTANCE_ID_STAT, -2));
            return null;
        }
    }

    private void onActivePet(Packet packet) {
        synchronized (this) {
            activePetInstance = ((ActivePetPacket) packet).instanceId;
        }
        fire();
    }

    private void onQuests(Packet packet) {
        QuestFetchResponsePacket p = (QuestFetchResponsePacket) packet;
        synchronized (this) {
            List<QuestData> list = new ArrayList<>();
            if (p.quests != null) Collections.addAll(list, p.quests);
            quests = list;
            questsAt = System.currentTimeMillis();
        }
        fire();
    }

    private void onUpdate(Packet packet) {
        UpdatePacket p = (UpdatePacket) packet;
        boolean changed = false;
        synchronized (this) {
            if (p.newObjects != null && myId != -1) {
                for (ObjectData o : p.newObjects) {
                    if (o.status == null) continue;
                    if (o.status.objectId == myId) {
                        if (me == null) me = new Thing();
                        me.objectType = o.objectType;
                        apply(me, o.status);
                        noteFame();
                        changed = true;
                    } else if (isPetClass(o.objectType) || mightBePet(o.status)) {
                        Thing t = new Thing();
                        t.objectType = o.objectType;
                        apply(t, o.status);
                        others.put(o.status.objectId, t);
                        changed = true;
                        if ("Pet Yard".equals(mapName)) changed |= rememberPet(t); // every pet there is yours
                    }
                }
            }
            if (p.drops != null) for (int id : p.drops) if (others.remove(id) != null) changed = true;
        }
        if (changed) fire();
    }

    private void onNewTick(Packet packet) {
        NewTickPacket p = (NewTickPacket) packet;
        if (p.status == null) return;
        boolean changed = false;
        synchronized (this) {
            if (me != null) notePlay(); // every game tick counts, even standing still
            for (ObjectStatusData s : p.status) {
                if (s == null || s.stats == null || s.stats.length == 0) continue;
                if (s.objectId == myId && me != null) {
                    changed |= apply(me, s);
                    noteFame();
                } else {
                    Thing t = others.get(s.objectId);
                    if (t != null) changed |= apply(t, s);
                }
            }
            // how close each pet is to you, smoothed over time
            if (me != null && !Float.isNaN(me.x)) {
                Thing before = pet();
                for (Thing t : others.values()) {
                    if (Float.isNaN(t.x)) continue;
                    double d = Math.hypot(t.x - me.x, t.y - me.y);
                    t.near = Double.isNaN(t.near) ? d : t.near * 0.85 + d * 0.15;
                    t.samples++;
                }
                Thing after = pet();
                if ((before == null) != (after == null) || (before != null && before.objectType != after.objectType)) changed = true;
                debugDump();
            }
        }
        if (changed) fire();
    }

    /** This account's id from the game ("" until your character has loaded once). */
    public String accountId() {
        return accountId;
    }

    private void noteFame() {
        String acc = me.s(StatType.ACCOUNT_ID_STAT);
        if (!acc.isEmpty()) accountId = acc;
        History.rememberName(accountId, me.s(StatType.NAME_STAT));
        int f = me.i(StatType.CURR_FAME_STAT, -1);
        if (f >= 0 && charId != -1 && !fameStart.containsKey(charId)) fameStart.put(charId, new long[]{f, System.currentTimeMillis()});
        if (f >= 0 && charId != -1) {
            long now = System.currentTimeMillis();
            synchronized (this) {
                if (visit == null || visit.charId != charId) {
                    if (visit != null) {
                        visit.endMs = now;
                        save(visit);
                    }
                    visit = new FameVisit(mapTitle.isEmpty() ? "Somewhere" : mapTitle, charId, now);
                    visit.startFame = f;
                    visits.add(visit);
                    while (visits.size() > 200) visits.remove(0);
                }
                visit.endFame = f;
                visit.endMs = now;
                if (samplesChar != charId) {
                    fameSamples.clear();
                    samplesChar = charId;
                }
                if (fameSamples.isEmpty() || fameSamples.get(fameSamples.size() - 1)[1] != f) {
                    fameSamples.add(new long[]{now, f});
                    if (fameSamples.size() > 5000) fameSamples.remove(1);
                }
            }
        }
    }

    /** Pets that come with full details (other people's, or yours in the Pet Yard) are remembered by id. */
    private static boolean rememberPet(Thing t) {
        if (t.i(StatType.PET_FIRST_ABILITY_POWER_STAT, -1) < 0) return false;
        PetInfo p = new PetInfo();
        p.instanceId = t.i(StatType.PET_INSTANCE_ID_STAT, -1);
        p.type = t.objectType;
        p.skin = t.i(StatType.SKIN_ID, -1);
        p.rarity = t.i(StatType.PET_RARITY_STAT, -1);
        p.maxLevel = t.i(StatType.PET_MAX_ABILITY_POWER_STAT, -1);
        StatType[][] ab = {
                {StatType.PET_FIRST_ABILITY_TYPE_STAT, StatType.PET_FIRST_ABILITY_POWER_STAT},
                {StatType.PET_SECOND_ABILITY_TYPE_STAT, StatType.PET_SECOND_ABILITY_POWER_STAT},
                {StatType.PET_THIRD_ABILITY_TYPE_STAT, StatType.PET_THIRD_ABILITY_POWER_STAT}};
        StatType[] points = {StatType.PET_FIRST_ABILITY_POINT_STAT, StatType.PET_SECOND_ABILITY_POINT_STAT, StatType.PET_THIRD_ABILITY_POINT_STAT};
        for (int i = 0; i < 3; i++) {
            p.abilityType[i] = t.i(ab[i][0], -1);
            p.abilityLevel[i] = t.i(ab[i][1], -1);
            p.abilityPoints[i] = t.i(points[i], -1);
        }
        p.learnedAt = System.currentTimeMillis();
        return PetInfo.remember(p);
    }

    private static boolean mightBePet(ObjectStatusData s) {
        if (s.stats == null) return false;
        for (StatData d : s.stats) {
            if (d.statType == null) continue;
            if (d.statType == StatType.OWNER_ACCOUNT_ID_STAT || d.statType.name().startsWith("PET_")) return true;
        }
        return false;
    }

    /** Copies stats in; returns true if anything the About me tab shows changed. */
    private static boolean apply(Thing t, ObjectStatusData s) {
        boolean changed = false;
        if (s.pos != null && (s.pos.x != 0 || s.pos.y != 0)) {
            t.x = s.pos.x;
            t.y = s.pos.y;
        }
        if (s.stats == null) return false;
        for (StatData d : s.stats) {
            if (d.statType == null) continue;
            if (NOISY.contains(d.statType.name())) { // stored, but not worth a redraw on its own
                if (d.stringStatValue != null) t.strings.put(d.statType, d.stringStatValue);
                else t.ints.put(d.statType, d.statValue);
                continue;
            }
            if (d.stringStatValue != null) {
                if (!d.stringStatValue.equals(t.strings.put(d.statType, d.stringStatValue))) changed = true;
            } else {
                Integer old = t.ints.put(d.statType, d.statValue);
                if (old == null || old != d.statValue) changed = true;
            }
        }
        return changed;
    }

    private long lastDump = 0;

    /** Writes pet-debug.txt (next to the jar) every 15 s: the pets in view and how close they are, to help fix pet detection. */
    private void debugDump() {
        long now = System.currentTimeMillis();
        if (now - lastDump < 15000) return;
        lastDump = now;
        StringBuilder sb = new StringBuilder();
        sb.append("White Bag Pup pet check, ").append(new java.util.Date(now)).append('\n');
        sb.append("active pet id from game: ").append(activePetInstance).append('\n');
        sb.append("my position: ").append(me.x).append(", ").append(me.y).append('\n');
        Thing chosen = pet();
        sb.append("chosen pet: ").append(chosen == null ? "none" : chosen.objectType + " " + AboutMe.objectName(chosen.objectType, "?")).append("\n\n");
        List<Map.Entry<Integer, Thing>> sorted = new ArrayList<>(others.entrySet());
        sorted.sort((a, b) -> Double.compare(Double.isNaN(a.getValue().near) ? 1e9 : a.getValue().near, Double.isNaN(b.getValue().near) ? 1e9 : b.getValue().near));
        for (Map.Entry<Integer, Thing> e : sorted) {
            Thing t = e.getValue();
            StringBuilder pet = new StringBuilder();
            for (Map.Entry<StatType, Integer> st : t.ints.entrySet()) {
                if (st.getKey().name().startsWith("PET_")) pet.append(st.getKey().name().replace("PET_", "").replace("_STAT", "")).append('=').append(st.getValue()).append(' ');
            }
            int skin = t.i(StatType.SKIN_ID, -1);
            sb.append(String.format(java.util.Locale.ROOT, "id %d  type %d  %s  skin %d %s  near=%.2f tiles  samples=%d  %s%n",
                    e.getKey(), t.objectType, AboutMe.objectName(t.objectType, "?"), skin, skin > 0 ? AboutMe.objectName(skin, "?") : "",
                    t.near, t.samples, pet.toString().trim()));
        }
        try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream("pet-debug.txt"), "UTF-8")) {
            w.write(sb.toString());
        } catch (Exception ignored) {
        }
    }

    private static String clazz(int type) {
        try {
            return String.valueOf(assets.IdToAsset.getClazz(type));
        } catch (Throwable e) {
            return "?";
        }
    }

    private void fire() {
        try {
            listener.changed();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
