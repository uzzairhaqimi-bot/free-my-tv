package io.freemytv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fork extra (Kelas Bijak): up to four children's progress (level per subject, stars, streak, badges), the questions
 * each one saw lately (so they don't come back too soon) and the ones they got wrong (to try again later), plus the
 * weekly question packs fetched from a GitHub repository. Kept in a private file of the app.
 */
final class LearnStore {
    static final int KIDS = 4;
    static final int[] COLORS = {0xFF22D3EE, 0xFFFF4FD8, 0xFFFACC15, 0xFF4ADE80};
    /** Where new question packs come from (a JSON file in a GitHub repo, raw). Empty: no online packs. */
    static String packUrl = "";

    static final class Kid {
        String name;
        int age = 9;
        final int[] level = new int[4];
        final int[] streakRight = new int[4];
        int stars, days, lastDay;
        final LinkedHashSet<String> recent = new LinkedHashSet<>();
        final List<String> mistakes = new ArrayList<>();
        final Set<String> badges = new LinkedHashSet<>();

        int startLevel() { return Math.max(1, Math.min(LearnBank.LEVELS, (age - 6))); }
    }

    final Kid[] kids = new Kid[KIDS];
    private final File file, packFile;

    LearnStore(Context c) {
        file = new File(c.getFilesDir(), "kelas-bijak.json");
        packFile = new File(c.getFilesDir(), "kelas-bijak-pack.json");
        for (int i = 0; i < KIDS; i++) {
            kids[i] = new Kid();
            kids[i].name = "Pemain " + (i + 1);
        }
        load();
    }

    void noteSeen(Kid k, String key) {
        k.recent.remove(key);
        k.recent.add(key);
        while (k.recent.size() > 400) k.recent.remove(k.recent.iterator().next());
    }

    /** Right: stars, and the level climbs after 4 in a row. Wrong: the question comes back later, level eases. */
    void answer(Kid k, LearnBank.Q q, boolean right) {
        noteSeen(k, q.key);
        int s = q.subject;
        if (k.level[s] == 0) k.level[s] = k.startLevel();
        if (right) {
            k.stars += 1 + (q.level >= 5 ? 1 : 0);
            k.streakRight[s]++;
            if (k.streakRight[s] >= 4 && k.level[s] < LearnBank.LEVELS) { k.level[s]++; k.streakRight[s] = 0; }
            k.mistakes.remove(q.key);
        } else {
            k.streakRight[s] = 0;
            if (k.level[s] > 1 && Math.random() < 0.35) k.level[s]--;
            if (!q.key.startsWith("m:") && !k.mistakes.contains(q.key)) k.mistakes.add(q.key);
            while (k.mistakes.size() > 60) k.mistakes.remove(0);
        }
        int today = (int) (System.currentTimeMillis() / 86_400_000L);
        if (k.lastDay != today) { k.days = k.lastDay == today - 1 ? k.days + 1 : 1; k.lastDay = today; }
        if (k.stars >= 10) k.badges.add("Bintang Pertama");
        if (k.stars >= 100) k.badges.add("Seratus Bintang");
        if (k.stars >= 500) k.badges.add("Juara Bijak");
        if (k.days >= 3) k.badges.add("3 Hari Berturut");
        if (k.days >= 7) k.badges.add("Seminggu Rajin");
        for (int i = 0; i < 4; i++) if (k.level[i] >= 8) k.badges.add("Pakar " + LearnBank.SUBJECTS[i]);
        save();
    }

    int level(Kid k, int subject) {
        if (k.level[subject] == 0) k.level[subject] = k.startLevel();
        return k.level[subject];
    }

    // ------------------------------------------------------------------ file

    private void load() {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] b = new byte[(int) file.length()];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            JSONArray a = new JSONObject(new String(b, 0, n, "UTF-8")).getJSONArray("kids");
            for (int i = 0; i < Math.min(KIDS, a.length()); i++) {
                JSONObject o = a.getJSONObject(i);
                Kid k = kids[i];
                k.name = o.optString("name", k.name);
                k.age = o.optInt("age", 9);
                k.stars = o.optInt("stars", 0);
                k.days = o.optInt("days", 0);
                k.lastDay = o.optInt("lastDay", 0);
                JSONArray lv = o.optJSONArray("level");
                for (int s = 0; lv != null && s < 4 && s < lv.length(); s++) k.level[s] = lv.getInt(s);
                JSONArray rc = o.optJSONArray("recent");
                for (int j = 0; rc != null && j < rc.length(); j++) k.recent.add(rc.getString(j));
                JSONArray ms = o.optJSONArray("mistakes");
                for (int j = 0; ms != null && j < ms.length(); j++) k.mistakes.add(ms.getString(j));
                JSONArray bd = o.optJSONArray("badges");
                for (int j = 0; bd != null && j < bd.length(); j++) k.badges.add(bd.getString(j));
            }
        } catch (Exception ignored) { }
    }

    void save() {
        try {
            JSONArray a = new JSONArray();
            for (Kid k : kids) {
                JSONObject o = new JSONObject();
                o.put("name", k.name);
                o.put("age", k.age);
                o.put("stars", k.stars);
                o.put("days", k.days);
                o.put("lastDay", k.lastDay);
                JSONArray lv = new JSONArray();
                for (int v : k.level) lv.put(v);
                o.put("level", lv);
                o.put("recent", new JSONArray(k.recent));
                o.put("mistakes", new JSONArray(k.mistakes));
                o.put("badges", new JSONArray(k.badges));
                a.put(o);
            }
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(new JSONObject().put("kids", a).toString().getBytes("UTF-8"));
            }
            tmp.renameTo(file);
        } catch (Exception e) {
            AppLog.w("kelas bijak: " + e);
        }
    }

    // ------------------------------------------------------------------ weekly packs

    /**
     * Adds the saved pack's questions to the bank. The pack is JSON:
     * {"version":"2026-11-09","items":[{"s":0-3,"l":1-8,"q":"…","a":["right","wrong",…],"e":"why"}]}
     */
    int applyPack(LearnBank bank) {
        int n = 0;
        try (InputStream in = new FileInputStream(packFile)) {
            byte[] b = new byte[(int) packFile.length()];
            int got = 0, r;
            while (got < b.length && (r = in.read(b, got, b.length - got)) > 0) got += r;
            JSONArray a = new JSONObject(new String(b, 0, got, "UTF-8")).getJSONArray("items");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                JSONArray ans = o.getJSONArray("a");
                if (ans.length() < 2) continue;
                String[] s = new String[Math.min(5, ans.length())];
                for (int j = 0; j < s.length; j++) s[j] = ans.getString(j);
                int subject = Math.max(1, Math.min(3, o.optInt("s", 1)));
                bank.add(new LearnBank.Item(subject, Math.max(1, Math.min(8, o.optInt("l", 3))),
                        o.getString("q"), o.optString("e", "Jawapan: " + s[0]), s));
                n++;
            }
        } catch (Exception ignored) { }
        return n;
    }

    /** Fetches the pack in the background, at most once a day. The next session uses it. */
    void refreshPack(Context c) {
        if (packUrl.isEmpty() || !packUrl.startsWith("https://")) return;
        if (packFile.exists() && System.currentTimeMillis() - packFile.lastModified() < 20 * 3_600_000L) return;
        Thread t = new Thread(() -> {
            try {
                HttpURLConnection h = (HttpURLConnection) new URL(packUrl).openConnection();
                h.setInstanceFollowRedirects(false);
                h.setConnectTimeout(8000);
                h.setReadTimeout(10000);
                if (h.getResponseCode() != 200) return;
                byte[] body;
                try (InputStream in = h.getInputStream()) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = in.read(buf)) > 0 && out.size() < 2_000_000) out.write(buf, 0, r);
                    body = out.toByteArray();
                }
                new JSONObject(new String(body, "UTF-8")).getJSONArray("items");   // must parse before it replaces the old one
                File tmp = new File(packFile.getPath() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(body); }
                tmp.renameTo(packFile);
            } catch (Throwable e) {
                AppLog.w("kelas bijak pack: " + e);
            }
        }, "kelas-bijak-pack");
        t.setDaemon(true);
        t.start();
    }
}
