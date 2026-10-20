package whitebag;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A picture of the game window for the bag post (Windows only, and only when you turn it on).
 * <p>
 * It finds the RotMG Exalt window and asks Windows for that window's own picture, so other windows in front of it
 * never end up in the post. If Windows can't give it, the screen is copied instead, but only while the game is the
 * window in front; otherwise there's no picture. A minimised game is skipped. Nothing is saved; the picture only
 * goes into the Discord post.
 */
final class GameShot {
    private GameShot() {
    }

    /** Windows functions used to find the game window. */
    private interface User32 extends Library {
        interface EnumProc extends Callback {
            boolean callback(Pointer hWnd, Pointer data);
        }

        boolean EnumWindows(EnumProc proc, Pointer data);

        int GetWindowTextW(Pointer hWnd, char[] text, int max);

        int GetClassNameW(Pointer hWnd, char[] text, int max);

        boolean IsWindowVisible(Pointer hWnd);

        boolean IsIconic(Pointer hWnd);

        Pointer GetForegroundWindow();

        boolean GetWindowRect(Pointer hWnd, int[] rect);

        boolean GetClientRect(Pointer hWnd, int[] rect);

        boolean ClientToScreen(Pointer hWnd, int[] point);

        Pointer GetDC(Pointer hWnd);

        int ReleaseDC(Pointer hWnd, Pointer hdc);

        boolean PrintWindow(Pointer hWnd, Pointer hdc, int flags);
    }

    /** Windows drawing functions, to copy the game window's own picture (not whatever is on top of it). */
    private interface Gdi32 extends Library {
        Pointer CreateCompatibleDC(Pointer hdc);

        Pointer CreateCompatibleBitmap(Pointer hdc, int w, int h);

        Pointer SelectObject(Pointer hdc, Pointer obj);

        boolean DeleteObject(Pointer obj);

        boolean DeleteDC(Pointer hdc);

        int GetDIBits(Pointer hdc, Pointer bitmap, int start, int lines, Pointer bits, Pointer info, int usage);
    }

    private static Gdi32 gdi32;

    private static User32 user32;
    private static boolean tried;

    private static synchronized User32 user32() {
        if (!tried) {
            tried = true;
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
                try {
                    user32 = Native.load("user32", User32.class, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);
                    gdi32 = Native.load("gdi32", Gdi32.class);
                } catch (Throwable e) {
                    System.out.println("Game screenshot: couldn't load user32: " + e);
                }
            }
        }
        return user32;
    }

    /** Why the last capture didn't happen (for the diary), or null. */
    static volatile String lastProblem;

    /** The game window's picture as a JPEG, or null (not Windows, game not found, or minimised). */
    static byte[] capture() {
        lastProblem = null;
        try {
            User32 u = user32();
            if (u == null) {
                lastProblem = "screenshots only work on Windows";
                return null;
            }
            Pointer game = findGame(u);
            if (game == null) {
                lastProblem = "couldn't find the game window";
                return null;
            }
            if (u.IsIconic(game)) {
                lastProblem = "the game is minimised";
                return null;
            }
            // the inside of the window (no title bar), in real screen pixels
            int[] client = new int[4];
            int[] origin = new int[2];
            if (!u.GetClientRect(game, client) || !u.ClientToScreen(game, origin)) {
                lastProblem = "couldn't read the game window's position";
                return null;
            }
            Rectangle px = new Rectangle(origin[0], origin[1], client[2] - client[0], client[3] - client[1]);
            if (px.width < 50 || px.height < 50) {
                lastProblem = "the game window is too small";
                return null;
            }
            // 1. ask the game window for its own picture: correct even when other windows are in front of it
            BufferedImage shot = printWindow(u, game, px.width, px.height);
            if (shot == null) {
                // 2. otherwise copy the screen, but only when the game is the window in front, so the picture can
                //    never show another window (a chat, a browser...) that happens to be on top of it
                Pointer front = u.GetForegroundWindow();
                if (front == null || !front.equals(game)) {
                    lastProblem = "the game was behind another window, so no picture (keep the game in front)";
                    return null;
                }
                shot = grab(px);
            }
            if (shot == null) {
                lastProblem = "the game window is off screen";
                return null;
            }
            return jpeg(fit(shot, 1280));
        } catch (Throwable e) {
            lastProblem = e.toString();
            return null;
        }
    }

    /**
     * The game window's own picture (Windows PrintWindow, client area, full content), or null if Windows couldn't
     * give it (or gave back a blank picture, which some graphics modes do).
     */
    private static BufferedImage printWindow(User32 u, Pointer game, int w, int h) {
        Gdi32 g = gdi32;
        if (g == null) return null;
        Pointer screen = null, mem = null, bmp = null, old = null;
        try {
            screen = u.GetDC(game);
            if (screen == null) return null;
            mem = g.CreateCompatibleDC(screen);
            bmp = g.CreateCompatibleBitmap(screen, w, h);
            if (mem == null || bmp == null) return null;
            old = g.SelectObject(mem, bmp);
            // PW_CLIENTONLY (1) | PW_RENDERFULLCONTENT (2): just the inside of the window, including what the
            // graphics card draws
            if (!u.PrintWindow(game, mem, 3)) return null;
            if (old != null) g.SelectObject(mem, old);
            old = null;
            com.sun.jna.Memory info = new com.sun.jna.Memory(44);
            info.clear();
            info.setInt(0, 40);          // biSize
            info.setInt(4, w);           // biWidth
            info.setInt(8, -h);          // biHeight (negative: top row first)
            info.setShort(12, (short) 1);  // biPlanes
            info.setShort(14, (short) 32); // biBitCount
            com.sun.jna.Memory bits = new com.sun.jna.Memory((long) w * h * 4);
            if (g.GetDIBits(mem, bmp, 0, h, bits, info, 0) == 0) return null;
            int[] px = bits.getIntArray(0, w * h);
            // a blank (all black) picture means Windows couldn't read the game's graphics
            int lit = 0;
            for (int i = 0; i < px.length; i += 97) if ((px[i] & 0xFFFFFF) != 0) lit++;
            if (lit < (px.length / 97) / 50) return null;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            img.setRGB(0, 0, w, h, px, 0, w);
            return img;
        } catch (Throwable e) {
            return null;
        } finally {
            try {
                if (old != null && mem != null) g.SelectObject(mem, old);
                if (bmp != null) g.DeleteObject(bmp);
                if (mem != null) g.DeleteDC(mem);
                if (screen != null) u.ReleaseDC(game, screen);
            } catch (Throwable ignored) {
            }
        }
    }

    /** The RotMG window: the one in front if it's the game (handy with two clients open), else the first one found. */
    private static Pointer findGame(User32 u) {
        List<Pointer> found = new ArrayList<>();
        u.EnumWindows((hWnd, data) -> {
            if (u.IsWindowVisible(hWnd) && isGame(u, hWnd)) found.add(hWnd);
            return true;
        }, null);
        if (found.isEmpty()) return null;
        Pointer front = u.GetForegroundWindow();
        if (front != null) for (Pointer p : found) if (p.equals(front)) return p;
        return found.get(0);
    }

    private static boolean isGame(User32 u, Pointer hWnd) {
        char[] buf = new char[256];
        int n = u.GetWindowTextW(hWnd, buf, buf.length);
        String title = new String(buf, 0, Math.max(0, n)).toLowerCase(Locale.ROOT);
        if (title.contains("rotmg") || title.contains("realm of the mad god")) {
            // not this app's own window, or a browser tab about the game
            if (title.contains("white bag pup") || title.contains("chrome") || title.contains("firefox") || title.contains("edge")) return false;
            char[] cls = new char[128];
            int c = u.GetClassNameW(hWnd, cls, cls.length);
            String k = new String(cls, 0, Math.max(0, c));
            return k.equals("UnityWndClass") || title.startsWith("rotmg");
        }
        return false;
    }

    /** Copies a rectangle of real screen pixels, working out the screen's scaling (125%, 150%...). */
    private static BufferedImage grab(Rectangle px) throws Exception {
        for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration gc = d.getDefaultConfiguration();
            Rectangle b = gc.getBounds();
            AffineTransform t = gc.getDefaultTransform();
            double sx = t.getScaleX(), sy = t.getScaleY();
            // this screen in real pixels (Java uses scaled coordinates)
            Rectangle real = new Rectangle((int) Math.round(b.x * sx), (int) Math.round(b.y * sy),
                    (int) Math.round(b.width * sx), (int) Math.round(b.height * sy));
            int cx = px.x + px.width / 2, cy = px.y + px.height / 2;
            if (!real.contains(cx, cy)) continue;
            Rectangle logical = new Rectangle((int) Math.round(px.x / sx), (int) Math.round(px.y / sy),
                    (int) Math.round(px.width / sx), (int) Math.round(px.height / sy));
            return new Robot(d).createScreenCapture(logical);
        }
        return null;
    }

    private static BufferedImage fit(BufferedImage src, int maxWidth) {
        if (src.getWidth() <= maxWidth) return toRgb(src);
        int w = maxWidth, h = (int) Math.round(src.getHeight() * (maxWidth / (double) src.getWidth()));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    static byte[] jpeg(BufferedImage img) throws Exception {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.85f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            w.setOutput(ios);
            w.write(null, new IIOImage(img, null, null), p);
        } finally {
            w.dispose();
        }
        return out.toByteArray();
    }
}
