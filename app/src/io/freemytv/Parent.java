package io.freemytv;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Calendar;

/**
 * Fork extra: the parents' rules for the fork's games, set from the parents' phone page (ParentPage). A daily limit
 * on game time (Papan Permainan and Retro Arked), a bedtime after which games stay closed until 6 in the morning,
 * and "learn first": a number of right answers in Kelas Bijak today before games open. Kelas Bijak itself is never
 * locked. Also a message from the parents shown on the TV, and an optional PIN for the page. Kept in a private file.
 */
final class Parent {
    int limitMin;        // 0: no limit
    int bedtime = -1;    // hour (0-23), -1: none
    int learnFirst;      // right answers needed today, 0: off
    String pin = "";
    // today's counters
    int day;
    long playedMs;
    int rightsToday;
    // a message for the TV
    String message = "";
    long messageAt;

    private static File file(Context c) { return new File(c.getFilesDir(), "parent.json"); }

    static int today() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }

    static synchronized Parent load(Context c) {
        Parent p = new Parent();
        try (FileInputStream in = new FileInputStream(file(c))) {
            byte[] b = new byte[(int) Math.min(65536, file(c).length())];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            JSONObject o = new JSONObject(new String(b, 0, n, "UTF-8"));
            p.limitMin = o.optInt("limitMin", 0);
            p.bedtime = o.optInt("bedtime", -1);
            p.learnFirst = o.optInt("learnFirst", 0);
            p.pin = o.optString("pin", "");
            p.day = o.optInt("day", 0);
            p.playedMs = o.optLong("playedMs", 0);
            p.rightsToday = o.optInt("rightsToday", 0);
            p.message = o.optString("message", "");
            p.messageAt = o.optLong("messageAt", 0);
        } catch (Exception ignored) {
            // no file yet: no rules
        }
        if (p.day != today()) { p.day = today(); p.playedMs = 0; p.rightsToday = 0; }
        return p;
    }

    synchronized void save(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("limitMin", limitMin).put("bedtime", bedtime).put("learnFirst", learnFirst).put("pin", pin)
                    .put("day", day).put("playedMs", playedMs).put("rightsToday", rightsToday)
                    .put("message", message).put("messageAt", messageAt);
            File tmp = new File(c.getFilesDir(), "parent.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(o.toString().getBytes("UTF-8")); }
            if (!tmp.renameTo(file(c))) AppLog.w("parent: could not save");
        } catch (Exception e) {
            AppLog.w("parent: " + e);
        }
    }

    /** Null when games may be played now; otherwise why not, for the screen. */
    String blocked() {
        if (bedtime >= 0) {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            boolean late = bedtime >= 6 ? h >= bedtime || h < 6 : h >= bedtime && h < 6;
            if (late) return "Sudah waktu tidur 🌙  Permainan dibuka semula pukul 6 pagi.";
        }
        if (learnFirst > 0 && rightsToday < learnFirst)
            return "Belajar dulu! Jawab " + (learnFirst - rightsToday) + " soalan lagi dengan betul dalam Kelas Bijak, kemudian boleh main. 📚";
        if (limitMin > 0 && playedMs >= limitMin * 60_000L) return "Masa main hari ini sudah habis ⏰  Jumpa esok!";
        return null;
    }

    /** Minutes of game time left today, or -1 without a limit. */
    int minutesLeft() {
        return limitMin <= 0 ? -1 : (int) Math.max(0, (limitMin * 60_000L - playedMs) / 60_000L);
    }

    static void addPlayed(Context c, long ms) {
        if (ms <= 0) return;
        Parent p = load(c);
        p.playedMs += Math.min(ms, 6 * 3_600_000L);
        p.save(c);
    }

    static void noteRight(Context c) {
        Parent p = load(c);
        p.rightsToday++;
        p.save(c);
    }
}
