package whitebag;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/**
 * Installs an update by itself: downloads the new WhiteBagPup.jar from the GitHub release, checks it really is the pup,
 * then hands over to a tiny script that waits for this app to close, swaps the jar (keeping the old one as
 * WhiteBagPup.jar.old) and opens the new version.
 */
final class Updater {
    private Updater() {
    }

    /** The program arguments the app was started with (passed on to the new version). */
    static volatile String[] launchArgs = new String[0];

    /** The jar this app is running from, or null (started from VS Code or the like: nothing to replace). */
    static File runningJar() {
        try {
            File f = new File(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar") ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Downloads {@code url} next to {@code jar} as jar.download, and checks it's a White Bag Pup jar. */
    static File download(String url, File jar) throws IOException {
        File out = new File(jar.getParentFile(), jar.getName() + ".download");
        HttpURLConnection c = open(url);
        int redirects = 0;
        while (c.getResponseCode() / 100 == 3 && redirects++ < 5) { // GitHub hands the file over from another address
            String next = c.getHeaderField("Location");
            c.disconnect();
            c = open(next);
        }
        if (c.getResponseCode() != 200) throw new IOException("GitHub answered HTTP " + c.getResponseCode());
        try (InputStream in = c.getInputStream(); OutputStream o = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int r;
            long total = 0;
            while ((r = in.read(buf)) > 0) {
                o.write(buf, 0, r);
                total += r;
                if (total > 100L * 1024 * 1024) throw new IOException("the download is far too big");
            }
        }
        try (ZipFile z = new ZipFile(out)) {
            if (z.getEntry("whitebag/WhiteBagNotifier.class") == null) throw new IOException("that file isn't White Bag Pup");
        } catch (IOException e) {
            out.delete();
            throw new IOException("the download isn't a working White Bag Pup (" + e.getMessage() + ")");
        }
        return out;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "WhiteBagPup");
        c.setRequestProperty("Accept", "application/octet-stream");
        return c;
    }

    private static String pid() {
        String n = java.lang.management.ManagementFactory.getRuntimeMXBean().getName(); // "1234@host"
        int at = n.indexOf('@');
        return at > 0 ? n.substring(0, at) : n;
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** The Java that runs the new version: javaw on Windows (no black window), else java. */
    private static String javaExe() {
        File home = new File(System.getProperty("java.home"), "bin");
        File w = new File(home, windows() ? "javaw.exe" : "java");
        return w.isFile() ? w.getAbsolutePath() : (windows() ? "javaw" : "java");
    }

    private static String ps(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    private static String sh(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /**
     * Writes and starts the script that swaps {@code downloaded} in for {@code jar} once this app has closed and opens
     * the new version. The caller then closes the app.
     */
    static void handOver(File jar, File downloaded) throws IOException {
        handOver(jar, downloaded, true);
    }

    /** As above; with {@code relaunch} false the new version is just put in place, for next time the pup is opened. */
    static void handOver(File jar, File downloaded, boolean relaunch) throws IOException {
        File dir = jar.getAbsoluteFile().getParentFile();
        String work = new File(".").getAbsoluteFile().getParent();
        List<String> args = new ArrayList<>();
        for (String a : launchArgs) args.add(a);
        if (windows()) {
            StringBuilder s = new StringBuilder();
            s.append("$ErrorActionPreference = 'SilentlyContinue'\r\n");
            s.append("Wait-Process -Id ").append(pid()).append(" -Timeout 60\r\n");
            s.append("Start-Sleep -Milliseconds 700\r\n");
            s.append("$jar = ").append(ps(jar.getAbsolutePath())).append("\r\n");
            s.append("$new = ").append(ps(downloaded.getAbsolutePath())).append("\r\n");
            s.append("for ($i = 0; $i -lt 30; $i++) {\r\n");
            s.append("  try { Copy-Item -LiteralPath $jar -Destination ($jar + '.old') -Force -ErrorAction Stop;"
                    + " Move-Item -LiteralPath $new -Destination $jar -Force -ErrorAction Stop; break } catch { Start-Sleep -Milliseconds 500 }\r\n");
            s.append("}\r\n");
            StringBuilder argList = new StringBuilder("@('-jar', ('\"' + $jar + '\"')");
            for (String a : args) argList.append(", ").append(ps("\"" + a.replace("\"", "") + "\""));
            argList.append(")");
            if (relaunch) s.append("Start-Process -FilePath ").append(ps(javaExe())).append(" -ArgumentList ").append(argList)
                    .append(" -WorkingDirectory ").append(ps(work)).append("\r\n");
            s.append("Remove-Item -LiteralPath $MyInvocation.MyCommand.Path -Force\r\n");
            File script = new File(dir, "update-pup.ps1");
            Files.write(script.toPath(), s.toString().getBytes(StandardCharsets.UTF_8));
            new ProcessBuilder("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                    "-File", script.getAbsolutePath()).directory(dir).start();
        } else {
            StringBuilder s = new StringBuilder("#!/bin/sh\n");
            s.append("while kill -0 ").append(pid()).append(" 2>/dev/null; do sleep 1; done\n");
            s.append("cp -f ").append(sh(jar.getAbsolutePath())).append(' ').append(sh(jar.getAbsolutePath() + ".old")).append('\n');
            s.append("mv -f ").append(sh(downloaded.getAbsolutePath())).append(' ').append(sh(jar.getAbsolutePath())).append('\n');
            if (relaunch) {
                s.append("cd ").append(sh(work)).append(" && nohup ").append(sh(javaExe())).append(" -jar ").append(sh(jar.getAbsolutePath()));
                for (String a : args) s.append(' ').append(sh(a));
                s.append(" >/dev/null 2>&1 &\n");
            }
            s.append("rm -f \"$0\"\n");
            File script = new File(dir, "update-pup.sh");
            Files.write(script.toPath(), s.toString().getBytes(StandardCharsets.UTF_8));
            new ProcessBuilder("sh", script.getAbsolutePath()).directory(dir).start();
        }
    }
}
