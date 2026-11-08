package io.freemytv;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fork extras: a video background from a link and the RGB LED glow around the screen's edge.
 * Kept in a small file (extras.json), not SharedPreferences, so the settings screen and the home screen read the same
 * values in any process.
 */
final class Extras {
    /** True while the home screen shows a video behind it: the sky stays transparent and the look stays dark. */
    static volatile boolean videoActive;

    boolean video;
    String link = "";
    boolean led = true;
    int ledStyle, ledColor, ledSpeed = 1, ledWidth = 1;
    /** Minutes between video changes (0: when each one ends). */
    int videoEvery = 5;
    boolean music;
    String musicLink = "";
    int musicVol = 1;          // 0 low, 1 medium, 2 high
    boolean quotes = true;
    /** Where the motivational line goes: 0 big and bold under the clock (in place of the prices), 1 small at the top. */
    int quoteStyle;
    /** The line shown big on the home screen now, and when it changed (HomeView fades it in); null: none. */
    static volatile String motto;
    static volatile long mottoAt;

    static boolean mottoOn() { return motto != null; }
    /** Music in the fork's games: 0 mixed (the link in calm moments, composed upbeat songs in action), 1 composed only, 2 the link only, 3 off. */
    int gameMusic;
    String gameMusicLink = "https://www.youtube.com/watch?v=-Yu4pgqXfso";

    static final int[] EVERY = {0, 1, 3, 5, 10, 30};

    float musicVolume() {
        return musicVol == 0 ? 0.2f : musicVol == 1 ? 0.45f : 0.8f;
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "extras.json");
    }

    static Extras load(Context c) {
        Extras e = new Extras();
        try (FileInputStream in = new FileInputStream(file(c))) {
            byte[] b = new byte[(int) Math.min(65536, file(c).length())];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            JSONObject o = new JSONObject(new String(b, 0, n, "UTF-8"));
            e.video = o.optBoolean("video", false);
            e.link = o.optString("link", "");
            e.led = o.optBoolean("led", true);
            e.ledStyle = clamp(o.optInt("ledStyle", 0), 2);
            e.ledColor = clamp(o.optInt("ledColor", 0), EdgeLight.COLORS.length - 1);
            e.ledSpeed = clamp(o.optInt("ledSpeed", 1), 2);
            e.ledWidth = clamp(o.optInt("ledWidth", 1), 2);
            e.videoEvery = o.optInt("videoEvery", 5);
            e.music = o.optBoolean("music", false);
            e.musicLink = o.optString("musicLink", "");
            e.musicVol = clamp(o.optInt("musicVol", 1), 2);
            e.quotes = o.optBoolean("quotes", true);
            e.quoteStyle = clamp(o.optInt("quoteStyle", 0), 1);
            e.gameMusic = clamp(o.optInt("gameMusic", 0), 3);
            e.gameMusicLink = o.optString("gameMusicLink", e.gameMusicLink);
        } catch (Exception ignored) {
            // no file yet: the defaults
        }
        return e;
    }

    void save(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("video", video);
            o.put("link", link == null ? "" : link);
            o.put("led", led);
            o.put("ledStyle", ledStyle);
            o.put("ledColor", ledColor);
            o.put("ledSpeed", ledSpeed);
            o.put("ledWidth", ledWidth);
            o.put("videoEvery", videoEvery);
            o.put("music", music);
            o.put("musicLink", musicLink == null ? "" : musicLink);
            o.put("musicVol", musicVol);
            o.put("quotes", quotes);
            o.put("quoteStyle", quoteStyle);
            o.put("gameMusic", gameMusic);
            o.put("gameMusicLink", gameMusicLink == null ? "" : gameMusicLink);
            File tmp = new File(c.getFilesDir(), "extras.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(o.toString().getBytes("UTF-8"));
            }
            if (!tmp.renameTo(file(c))) AppLog.w("extras: could not save");
        } catch (Exception e) {
            AppLog.w("extras: " + e);
        }
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    /** What the link points to: YouTube videos/playlists, or direct video URLs. */
    static final class Source {
        final List<String[]> youtube = new ArrayList<>();   // {"v", id} or {"l", playlistId}
        final List<String> direct = new ArrayList<>();
        boolean empty() { return youtube.isEmpty() && direct.isEmpty(); }
    }

    private static final Pattern P_VID = Pattern.compile("(?:v=|youtu\\.be/|shorts/|embed/|live/)([A-Za-z0-9_-]{11})");
    private static final Pattern P_LIST = Pattern.compile("[?&]list=([A-Za-z0-9_-]+)");

    static Source parse(String text) {
        Source s = new Source();
        if (text == null) return s;
        for (String tok : text.split("[\\s,]+")) {
            tok = tok.trim();
            if (tok.isEmpty()) continue;
            String low = tok.toLowerCase();
            boolean yt = low.contains("youtube.com") || low.contains("youtu.be");
            Matcher v = P_VID.matcher(tok), l = P_LIST.matcher(tok);
            if (yt && v.find()) s.youtube.add(new String[] {"v", v.group(1)});
            else if (yt && l.find()) s.youtube.add(new String[] {"l", l.group(1)});
            else if (low.startsWith("https://") || low.startsWith("http://")) s.direct.add(tok);
            else if (tok.matches("[A-Za-z0-9_-]{11}")) s.youtube.add(new String[] {"v", tok});
        }
        return s;
    }
}
