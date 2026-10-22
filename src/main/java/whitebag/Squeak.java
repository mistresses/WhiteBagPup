package whitebag;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.awt.Toolkit;
import java.util.Random;

/**
 * Little sounds, generated in code (no sound files): a squeaky-toy "arf!" when a bag drops,
 * pitched a bit differently each time, and a soft "boop" for clicks.
 */
final class Squeak {
    private static final float RATE = 44100f;
    private static final Random RANDOM = new Random();
    /** Turned off from the Home page. */
    static volatile boolean enabled = true;
    /** Bags whose built-in sound is turned off (the boxes under the bags on Home). Your own sound file still plays. */
    static final java.util.Set<BagType> muted = java.util.Collections.synchronizedSet(java.util.EnumSet.noneOf(BagType.class));

    private Squeak() {
    }

    /** The squeak, somewhere between a bit lower and a bit higher than normal. */
    static void play() {
        if (!enabled) return;
        double pitch = 0.9 + RANDOM.nextDouble() * 0.22;
        playAsync(squeak(pitch), true);
    }

    /**
     * A different squeak for each bag colour, so you can tell what dropped without looking:
     * white = a rising three-squeak fanfare, red = two quick high squeaks, orange = squeak-squeak down,
     * gold = squeak plus a bell, egg basket = tiny chick peeps, blue = one low squeak.
     * Boosted bags add a little sparkle at the end.
     */
    static void play(BagType type) {
        if (!enabled) return;
        if (type == null) {
            play();
            return;
        }
        java.io.File custom = customSound(type);
        if (custom != null && playFile(custom)) return;
        if (muted.contains(type)) return;
        playAsync(bagSound(type), true);
    }

    /** The built-in sound for this bag, even if it's turned off (to preview it). */
    static void playDefault(BagType type) {
        if (!enabled) return;
        playAsync(bagSound(type), true);
    }

    /** A realm event: your own sound (cube-god.wav, else event.wav), or a little two-bell chime and a squeak. */
    static void playEvent(String eventName) {
        playEvent(eventName, "event");
    }

    /**
     * An event from the Events tab: sounds/&lt;its name&gt;.wav (like cube-god.wav or the-shatters.wav), else
     * sounds/&lt;kind&gt;.wav (event.wav, key-pop.wav or realm-closed.wav), else sounds/event.wav, else the built-in chime.
     */
    static void playEvent(String eventName, String kind) {
        if (!enabled) return;
        String slug = slug(eventName);
        for (String n : new String[]{slug, kind, "event"}) {
            for (String ext : EXTENSIONS) {
                java.io.File f = new java.io.File(SOUND_DIR, n + ext);
                if (f.isFile() && playFile(f)) return;
            }
        }
        playAsync(pcm(bell(1318, 0.28), bell(1760, 0.32), silence(0.04), squeakPcm(1.05)), true);
    }

    /** "Oryx's Sanctuary" -> "oryxs-sanctuary", used for sound file names. */
    static String slug(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    // ---------------------------------------------------------------- your own sounds

    static final java.io.File SOUND_DIR = new java.io.File("sounds");
    private static final String[] EXTENSIONS = {".wav", ".aif", ".aiff", ".au"};

    /** File name (without extension) for a bag's own sound, e.g. "white" or "boosted-white". */
    static String soundName(BagType type) {
        BagType colour = LootStats.colour(type);
        String base = colour == BagType.EGG ? "egg" : colour.name().toLowerCase(java.util.Locale.ROOT);
        return colour == type ? base : "boosted-" + base;
    }

    /** Your own sound for this bag from the sounds folder: boosted-white.wav, else white.wav, and so on. */
    static java.io.File customSound(BagType type) {
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add(soundName(type));
        if (LootStats.colour(type) != type) names.add(soundName(LootStats.colour(type)));
        for (String n : names) {
            for (String ext : EXTENSIONS) {
                java.io.File f = new java.io.File(SOUND_DIR, n + ext);
                if (f.isFile()) return f;
            }
        }
        return null;
    }

    /** Plays a sound file; returns false if it can't be read (then the built-in sound plays instead). */
    private static boolean playFile(java.io.File f) {
        try {
            javax.sound.sampled.AudioInputStream in = AudioSystem.getAudioInputStream(f);
            javax.sound.sampled.Clip clip = AudioSystem.getClip();
            clip.addLineListener(e -> {
                if (e.getType() == javax.sound.sampled.LineEvent.Type.STOP) clip.close();
            });
            clip.open(in);
            clip.start();
            return true;
        } catch (Throwable e) {
            System.out.println("Couldn't play " + f + ": " + e.getMessage());
            return false;
        }
    }

    /** Makes the sounds folder with a note explaining how to use it, the first time the app runs. */
    static void prepareSoundFolder() {
        try {
            if (!SOUND_DIR.isDirectory() && !SOUND_DIR.mkdirs()) return;
            java.io.File readme = new java.io.File(SOUND_DIR, "README.txt");
            // rewrite our own note when it's from an older version (it's ours: it starts with this line)
            if (readme.exists()) {
                String old = new String(java.nio.file.Files.readAllBytes(readme.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                if (old.contains("key-pop.wav") || !old.startsWith("Put your own bag sounds in this folder")) return;
            }
            String text = "Put your own bag sounds in this folder and the pup plays them instead of its squeaks.\r\n\r\n"
                    + "Name each file after the bag colour:\r\n"
                    + "  white.wav, red.wav, orange.wav, gold.wav, egg.wav, blue.wav, cyan.wav\r\n\r\n"
                    + "Boosted bags use the same sound, unless you add one just for them:\r\n"
                    + "  boosted-white.wav, boosted-red.wav, boosted-orange.wav, boosted-gold.wav, boosted-egg.wav, boosted-blue.wav, boosted-cyan.wav\r\n\r\n"
                    + "Events tab: event.wav for realm events, key-pop.wav for key pops, realm-closed.wav for closing realms,\r\n"
                    + "or one file per event or dungeon named after it, like cube-god.wav or the-shatters.wav.\r\n\r\n"
                    + "Use .wav files (.aiff and .au work too). MP3 doesn't work, so convert it to .wav first,\r\n"
                    + "for example with Audacity or an online converter. Keep them short, a second or two.\r\n"
                    + "Changes work straight away, no restart needed. Turn a bag on in Bags to Watch to hear it.\r\n";
            java.nio.file.Files.write(readme.toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    static byte[] bagSound(BagType type) {
        java.util.List<short[]> parts = new java.util.ArrayList<>();
        BagType colour = LootStats.colour(type);
        switch (colour) {
            case WHITE:
                parts.add(squeakPcm(1.0));
                parts.add(silence(0.05));
                parts.add(squeakPcm(1.2));
                parts.add(silence(0.05));
                parts.add(squeakPcm(1.45));
                break;
            case RED:
                parts.add(shortSqueak(1.3));
                parts.add(silence(0.04));
                parts.add(shortSqueak(1.3));
                break;
            case ORANGE:
                parts.add(squeakPcm(1.12));
                parts.add(silence(0.05));
                parts.add(squeakPcm(0.95));
                break;
            case GOLD:
                parts.add(squeakPcm(1.08));
                parts.add(bell(1760, 0.35));
                break;
            case EGG:
                for (int i = 0; i < 3; i++) {
                    parts.add(chirp(0.06, 2600 + i * 250, 3400 + i * 250, 0.32));
                    parts.add(silence(0.035));
                }
                break;
            default: // blue
                parts.add(squeakPcm(0.72));
        }
        if (colour != type) { // boosted
            parts.add(silence(0.03));
            parts.add(bell(3136, 0.22));
            parts.add(bell(3951, 0.25));
        }
        return pcm(parts.toArray(new short[0][]));
    }

    private static short[] squeakPcm(double pitch) {
        short[] a = chirp(0.11, 1250 * pitch, 2700 * pitch, 0.42);
        short[] gap = new short[(int) (RATE * 0.045)];
        short[] b = chirp(0.15, 2900 * pitch, 1500 * pitch, 0.42);
        short[] out = new short[a.length + gap.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length + gap.length, b.length);
        return out;
    }

    private static short[] shortSqueak(double pitch) {
        return chirp(0.09, 1600 * pitch, 3000 * pitch, 0.4);
    }

    private static short[] silence(double seconds) {
        return new short[(int) (RATE * seconds)];
    }

    /** A soft bell: a sine with a gentle overtone that rings out. */
    private static short[] bell(double freq, double seconds) {
        int n = (int) (RATE * seconds);
        short[] s = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / RATE;
            double env = Math.min(1, i / (RATE * 0.004)) * Math.exp(-t * 9);
            double v = Math.sin(2 * Math.PI * freq * t) + 0.3 * Math.sin(2 * Math.PI * freq * 2.76 * t);
            s[i] = (short) (v / 1.3 * env * 0.28 * Short.MAX_VALUE);
        }
        return s;
    }

    /** A soft, short boop for clicks and petting. */
    static void boop() {
        if (!enabled) return;
        playAsync(boopSound(0.96 + RANDOM.nextDouble() * 0.08), false);
    }

    private static void playAsync(byte[] data, boolean beepIfNoAudio) {
        Thread t = new Thread(() -> {
            try {
                AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
                try (SourceDataLine line = AudioSystem.getSourceDataLine(fmt)) {
                    line.open(fmt, data.length);
                    line.start();
                    line.write(data, 0, data.length);
                    line.drain();
                }
            } catch (Throwable e) {
                if (beepIfNoAudio) Toolkit.getDefaultToolkit().beep();
            }
        }, "squeak");
        t.setDaemon(true);
        t.start();
    }

    /** Kept for tests: the squeak at normal pitch. */
    static byte[] sound() {
        return squeak(1.0);
    }

    static byte[] squeak(double pitch) {
        // "ar-" rising chirp, tiny gap, "-f!" falling chirp
        short[] a = chirp(0.11, 1250 * pitch, 2700 * pitch, 0.42);
        short[] gap = new short[(int) (RATE * 0.045)];
        short[] b = chirp(0.15, 2900 * pitch, 1500 * pitch, 0.42);
        return pcm(a, gap, b);
    }

    static byte[] boopSound(double pitch) {
        // a short, round blip that slides down a little, quiet and soft-edged
        int n = (int) (RATE * 0.07);
        short[] s = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            double f = (720 - 180 * t) * pitch;
            phase += 2 * Math.PI * f / RATE;
            double env = Math.min(1, t / 0.12) * Math.pow(1 - t, 2.2);
            s[i] = (short) ((Math.sin(phase) + 0.15 * Math.sin(2 * phase)) / 1.15 * env * 0.2 * Short.MAX_VALUE);
        }
        return pcm(s);
    }

    private static byte[] pcm(short[]... parts) {
        int len = 0;
        for (short[] p : parts) len += p.length;
        byte[] out = new byte[len * 2];
        int i = 0;
        for (short[] p : parts) {
            for (short v : p) {
                out[i++] = (byte) v;
                out[i++] = (byte) (v >> 8);
            }
        }
        return out;
    }

    /** Frequency sweep with a fast wobble and a rubbery 2nd/3rd harmonic. */
    private static short[] chirp(double seconds, double f0, double f1, double volume) {
        int n = (int) (RATE * seconds);
        short[] s = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) n;
            double ease = 1 - Math.pow(1 - t, 2);
            double f = f0 + (f1 - f0) * ease;
            f *= 1 + 0.035 * Math.sin(2 * Math.PI * 38 * i / RATE); // squeaky wobble
            phase += 2 * Math.PI * f / RATE;
            double v = Math.sin(phase) + 0.35 * Math.sin(2 * phase) + 0.12 * Math.sin(3 * phase);
            double env = Math.min(1, t / 0.08) * Math.pow(1 - t, 0.6);
            s[i] = (short) (v / 1.47 * env * volume * Short.MAX_VALUE);
        }
        return s;
    }
}
