package whitebag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backups of everything the pup keeps next to itself (logs, badges, settings, your sounds and background) as zip files
 * in the backups folder. The newest {@link #KEEP} are kept.
 */
final class Backup {
    private Backup() {
    }

    static final File DIR = new File("backups");
    static final int KEEP = 8;
    private static final long WEEK = 7L * 24 * 3600 * 1000;

    /** Files next to the app that are worth keeping (not the app itself or its downloads). */
    private static boolean worthKeeping(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (!f.isFile()) return false;
        return n.endsWith(".txt") || n.endsWith(".properties") || n.endsWith(".png") || n.endsWith(".jpg")
                || n.endsWith(".wav") || n.endsWith(".aif") || n.endsWith(".aiff") || n.endsWith(".au");
    }

    /** Zips the pup's files into the backups folder; {@code why} goes in the name ("weekly", "before-update"...). */
    static synchronized File backupNow(String why) throws IOException {
        if (!DIR.isDirectory() && !DIR.mkdirs()) throw new IOException("couldn't make the backups folder");
        String stamp = new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(new Date());
        File out = new File(DIR, "WhiteBagPup-backup-" + stamp + (why == null || why.isEmpty() ? "" : "-" + why) + ".zip");
        File tmp = new File(DIR, out.getName() + ".part");
        int n = 0;
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(tmp))) {
            File[] here = new File(".").listFiles();
            if (here != null) {
                Arrays.sort(here);
                for (File f : here) if (worthKeeping(f)) { add(zip, f, f.getName()); n++; }
            }
            File[] sounds = Squeak.SOUND_DIR.listFiles();
            if (sounds != null) for (File f : sounds) if (worthKeeping(f)) { add(zip, f, "sounds/" + f.getName()); n++; }
        }
        if (n == 0) {
            tmp.delete();
            throw new IOException("there's nothing to back up yet");
        }
        if (out.exists()) out.delete();
        if (!tmp.renameTo(out)) throw new IOException("couldn't save " + out.getName());
        prune();
        return out;
    }

    private static void add(ZipOutputStream zip, File f, String name) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(f.lastModified());
        zip.putNextEntry(e);
        try (InputStream in = new FileInputStream(f)) {
            copy(in, zip);
        }
        zip.closeEntry();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16384];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
    }

    /** The backups, newest first. */
    static List<File> all() {
        File[] f = DIR.listFiles((d, n) -> n.startsWith("WhiteBagPup-backup-") && n.endsWith(".zip"));
        List<File> out = new ArrayList<>(f == null ? new ArrayList<>() : Arrays.asList(f));
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    private static void prune() {
        List<File> l = all();
        for (int i = KEEP; i < l.size(); i++) l.get(i).delete();
    }

    /** True when the weekly backup is due (settings key backup.last, in milliseconds). */
    static boolean weeklyDue(java.util.Properties settings) {
        if ("false".equals(settings.getProperty("backup.weekly"))) return false;
        long last;
        try {
            last = Long.parseLong(settings.getProperty("backup.last", "0").trim());
        } catch (NumberFormatException e) {
            last = 0;
        }
        return System.currentTimeMillis() - last >= WEEK;
    }

    /**
     * Puts a backup's files back next to the app (after backing up what's there now). Returns how many files were
     * restored. Only plain file names (and sounds/...) are written, nothing outside the app's folder.
     */
    static synchronized int restore(File zipFile) throws IOException {
        try {
            backupNow("before-restore");
        } catch (IOException ignored) {
            // nothing there yet: nothing to lose
        }
        int n = 0;
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (e.isDirectory() || name.contains("..") || name.startsWith("/") || name.contains(":")) continue;
                String[] parts = name.split("/");
                if (parts.length > 2 || (parts.length == 2 && !parts[0].equals("sounds"))) continue;
                File target = parts.length == 2 ? new File(Squeak.SOUND_DIR, parts[1]) : new File(parts[0]);
                if (parts.length == 2 && !Squeak.SOUND_DIR.isDirectory()) Squeak.SOUND_DIR.mkdirs();
                File tmp = new File(target.getPath() + ".restoring");
                try (OutputStream out = new FileOutputStream(tmp)) {
                    copy(zip, out);
                }
                if (target.exists() && !target.delete()) {
                    tmp.delete();
                    throw new IOException("couldn't replace " + target.getName() + " (is it open somewhere?)");
                }
                if (!tmp.renameTo(target)) throw new IOException("couldn't write " + target.getName());
                n++;
            }
        }
        return n;
    }
}
