package io.freemytv;

import android.content.Context;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fork extra: "Ringankan Stick". Through the TV's own ADB (as Recent Apps does): stops the apps left running in the
 * background, frees cached memory and trims the apps' caches, then says how much memory came back. It keeps what
 * Recent Apps keeps: Free My TV itself, the enabled accessibility services and the keyboard, the protected system
 * pieces (HomeMode.mayStop) and whatever the TV's inputs or remote need (HomeMode.neededByTv). Never on the UI thread.
 */
final class Lighten {
    private Lighten() {}

    interface Done { void done(String message, int closed, int freedMb); }

    private static final Pattern PACKAGE = Pattern.compile("(?:A=|I=|affinity=)(?:\\d+:)?([a-zA-Z][\\w.]*)");

    static void run(Context c, Done done) {
        Context app = c.getApplicationContext();
        new Thread(() -> {
            String msg;
            int closed = 0, freed = 0;
            try (Adb adb = Adb.connect(app, 10_000, null)) {
                int before = memAvailableMb(adb);
                Set<String> keep = new HashSet<>();
                keep.add(app.getPackageName());
                for (String s : adb.shell("settings get secure enabled_accessibility_services").split(":")) keep.add(owner(s));
                keep.add(owner(adb.shell("settings get secure default_input_method")));
                Set<String> alive = new HashSet<>();
                for (String line : adb.shell("ps -A -o NAME").split("\n")) {
                    String n = line.trim();
                    int colon = n.indexOf(':');
                    alive.add(colon > 0 ? n.substring(0, colon) : n);
                }
                // the apps a person opened (launchable), still running
                Set<String> candidates = new HashSet<>();
                for (String[] a : HomeApps.launchable(app)) {
                    String p = a[0];
                    if (alive.contains(p) && !keep.contains(p) && HomeMode.mayStop(p)) candidates.add(p);
                }
                // and the ones in the recent tasks
                Matcher m = PACKAGE.matcher(adb.shell("dumpsys activity recents"));
                List<String> launchable = new ArrayList<>();
                for (String[] a : HomeApps.launchable(app)) launchable.add(a[0]);
                while (m.find()) {
                    String p = m.group(1);
                    while (!launchable.contains(p) && p.indexOf('.') > 0) p = p.substring(0, p.lastIndexOf('.'));
                    if (launchable.contains(p) && alive.contains(p) && !keep.contains(p) && HomeMode.mayStop(p)) candidates.add(p);
                }
                Set<String> needed;
                try { needed = candidates.isEmpty() ? candidates : HomeMode.neededByTv(adb, candidates); } catch (IOException e) { needed = null; }
                if (needed == null) needed = candidates;   // couldn't check: close nothing rather than risk the TV's inputs
                for (String p : candidates) {
                    if (needed.contains(p)) continue;
                    adb.shell("am force-stop " + p);
                    closed++;
                }
                adb.shell("am kill-all");
                adb.shell("pm trim-caches 64G");
                try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
                int after = memAvailableMb(adb);
                freed = Math.max(0, after - before);
                msg = "Siap! " + closed + " aplikasi ditutup · RAM kosong " + after + " MB (+" + freed + " MB)";
            } catch (IOException | RuntimeException e) {
                AppLog.w("lighten: " + e);
                msg = "Tidak dapat meringankan. Benarkan \"USB debugging\" untuk Free My TV bila TV bertanya.";
            }
            final String text = msg;
            final int n = closed, f = freed;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> done.done(text, n, f));
        }, "lighten").start();
    }

    private static int memAvailableMb(Adb adb) throws IOException {
        Matcher m = Pattern.compile("MemAvailable:\\s+(\\d+)").matcher(adb.shell("cat /proc/meminfo"));
        return m.find() ? Integer.parseInt(m.group(1)) / 1024 : 0;
    }

    private static String owner(String component) {
        String s = component.trim();
        int slash = s.indexOf('/');
        return slash > 0 ? s.substring(0, slash) : "";
    }
}
