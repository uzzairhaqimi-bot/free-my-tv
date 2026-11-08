package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

/**
 * Terbang Wau: a flying course for everyone at once, each in their own strip. The kite flies on by itself; ▲▼ (or the
 * phone's stick) take it up and down through rings (the middle scores more), past birds, while gusts of wind push it
 * up or down. Three courses are picked from six each game, each windier than the last.
 */
final class ModTerbang implements Arked4.Mod {
    private static final String[] COURSE = {"Sawah Padi", "Pantai Timur", "Bukit Fraser", "Tasik Kenyir", "Gunung Kinabalu", "Langit Senja"};
    private static final int[][] SKY = {{0xFF7DD3FC, 0xFFDCFCE7}, {0xFF38BDF8, 0xFFFEF3C7}, {0xFF86EFAC, 0xFFE0F2FE}, {0xFF0EA5E9, 0xFFBAE6FD},
            {0xFF64748B, 0xFFE2E8F0}, {0xFFF97316, 0xFF7C3AED}};
    private static final float TIME = 35, SPEED = 6;

    private final Arked4 a;
    private final int[] order = new int[3];
    private int round;
    private float roundStart, banner;
    private final float[] ringX = new float[40], ringY = new float[40], birdX = new float[30], birdY = new float[30], gustX = new float[20], gustDir = new float[20];
    private int nRing, nBird, nGust;
    private final float[] wx = new float[4], wy = new float[4], vy = new float[4], hitT = new float[4], pop = new float[4];
    private final boolean[] ringTaken = new boolean[4 * 40];
    private Shader sky;
    private int skyFor = -1;

    ModTerbang(Arked4 a) { this.a = a; }

    @Override public void build() {
        int[] all = {0, 1, 2, 3, 4, 5};
        for (int i = 5; i > 0; i--) { int j = a.rnd.nextInt(i + 1); int tt = all[i]; all[i] = all[j]; all[j] = tt; }
        for (int i = 0; i < 3; i++) order[i] = all[i];
        java.util.Arrays.sort(order);
        round = 0;
        newCourse();
    }

    private void newCourse() {
        roundStart = a.time;
        banner = 2;
        nRing = nBird = nGust = 0;
        float x = 6;
        float wind = 0.5f + order[round] * 0.25f;
        while (x < SPEED * TIME + 10) {
            if (nRing < ringX.length) { ringX[nRing] = x; ringY[nRing] = 0.15f + a.rnd.nextFloat() * 0.7f; nRing++; }
            if (a.rnd.nextFloat() < 0.4f + round * 0.1f && nBird < birdX.length) { birdX[nBird] = x + 3; birdY[nBird] = 0.1f + a.rnd.nextFloat() * 0.8f; nBird++; }
            if (a.rnd.nextFloat() < 0.25f * wind && nGust < gustX.length) { gustX[nGust] = x + 1.5f; gustDir[nGust] = (a.rnd.nextBoolean() ? 1 : -1) * wind; nGust++; }
            x += 5 + a.rnd.nextFloat() * 2;
        }
        java.util.Arrays.fill(ringTaken, false);
        for (Arked4.P pl : a.ps) { wx[pl.seat] = 0; wy[pl.seat] = 0.5f; vy[pl.seat] = 0; hitT[pl.seat] = 0; }
    }

    @Override public void update(float dt) {
        if (banner > 0) banner -= dt;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            if (pop[s] > 0) pop[s] -= dt;
            if (hitT[s] > 0) hitT[s] -= dt;
            float before = wx[s];
            wx[s] += SPEED * dt * (hitT[s] > 0 ? 0.6f : 1);
            vy[s] += (a.iy(pl) * 0.9f - vy[s]) * Math.min(1, dt * 5);   // the stick sets how fast it climbs or dives
            for (int i = 0; i < nGust; i++) if (Math.abs(wx[s] - gustX[i]) < 1.2f) vy[s] += gustDir[i] * dt * 2.5f;
            wy[s] = Math.max(0.05f, Math.min(0.95f, wy[s] + vy[s] * dt));
            for (int i = 0; i < nRing; i++) {
                if (ringTaken[s * 40 + i] || before > ringX[i] || wx[s] < ringX[i]) continue;
                float d = Math.abs(wy[s] - ringY[i]);
                if (d < 0.09f) {
                    ringTaken[s * 40 + i] = true;
                    int pts = d < 0.03f ? 30 : 15;
                    pl.score += pts;
                    pop[s] = 0.6f;
                    a.sound.sfx(d < 0.03f ? GameMusic.SFX_OK : GameMusic.SFX_COIN);
                }
            }
            for (int i = 0; i < nBird; i++) {
                if (hitT[s] <= 0 && Math.abs(wx[s] - birdX[i]) < 0.5f && Math.abs(wy[s] - birdY[i]) < 0.07f) {
                    hitT[s] = 1.2f;
                    a.party.buzz(s, 150);
                    a.sound.sfx(GameMusic.SFX_WRONG);
                }
            }
        }
        if (a.time - roundStart > TIME) {
            round++;
            if (round >= 3) {
                Arked4.P best = null;
                for (Arked4.P pl : a.ps) if (pl.on && (best == null || pl.score > best.score)) best = pl;
                a.finish(a.who(best) + " juruterbang wau terbaik: " + best.score + " mata! 🪁");
                return;
            }
            newCourse();
        }
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        int crs = order[Math.min(round, 2)];
        int n = 0;
        for (Arked4.P pl : a.ps) if (pl.on) n++;
        float top = H * 0.08f, lane = (H - top) / Math.max(1, n);
        if (skyFor != crs * 10 + n) { skyFor = crs * 10 + n; sky = new LinearGradient(0, 0, 0, lane, SKY[crs][0], SKY[crs][1], Shader.TileMode.MIRROR); }
        int k = 0;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            float y0 = top + k * lane, u = lane * 0.14f, cam = wx[s] - W * 0.2f / u;
            c.save();
            c.clipRect(0, y0, W, y0 + lane);
            c.translate(0, y0);
            p.setShader(sky);
            c.drawRect(0, 0, W, lane, p);
            p.setShader(null);
            p.setColor(0x55FFFFFF);
            for (int i = 0; i < 5; i++) {
                float x = ((i * W * 0.3f - cam * u * 0.3f) % (W * 1.5f) + W * 1.5f) % (W * 1.5f) - W * 0.2f;
                c.drawCircle(x, lane * (0.2f + (i % 3) * 0.25f), lane * 0.08f, p);
                c.drawCircle(x + lane * 0.1f, lane * (0.2f + (i % 3) * 0.25f), lane * 0.06f, p);
            }
            p.setColor(Party.COLORS[s]);
            c.drawRect(0, 0, W * 0.006f, lane, p);
            for (int i = 0; i < nGust; i++) {
                float x = (gustX[i] - cam) * u;
                if (x < -u * 2 || x > W) continue;
                a.tx.setTextAlign(Paint.Align.CENTER);
                a.tx.setTextSize(lane * 0.12f);
                a.tx.setColor(0x88FFFFFF);
                for (int r = 0; r < 3; r++) c.drawText(gustDir[i] > 0 ? "↓" : "↑", x, lane * (0.3f + r * 0.25f) + (float) Math.sin(t * 6 + r) * lane * 0.03f, a.tx);
            }
            for (int i = 0; i < nRing; i++) {
                float x = (ringX[i] - cam) * u;
                if (x < -u || x > W) continue;
                boolean got = ringTaken[s * 40 + i];
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(lane * 0.02f);
                p.setColor(got ? 0x5522C55E : 0xFFFACC15);
                c.drawOval(x - u * 0.25f, ringY[i] * lane - lane * 0.09f, x + u * 0.25f, ringY[i] * lane + lane * 0.09f, p);
                p.setStyle(Paint.Style.FILL);
            }
            for (int i = 0; i < nBird; i++) {
                float x = (birdX[i] - cam) * u, y = birdY[i] * lane, f = (float) Math.sin(t * 14 + i) * lane * 0.03f;
                if (x < -u || x > W) continue;
                p.setColor(0xFF1F2937);
                p.setStrokeWidth(lane * 0.015f);
                c.drawLine(x, y, x - lane * 0.05f, y - f, p);
                c.drawLine(x, y, x + lane * 0.05f, y - f, p);
            }
            float kx = (wx[s] - cam) * u, ky = wy[s] * lane;
            p.setStrokeWidth(lane * 0.008f);
            p.setColor(0x88FFFFFF);
            c.drawLine(kx - lane * 0.05f, ky + lane * 0.05f, 0, lane, p);   // the string
            if (!(hitT[s] > 0 && ((int) (hitT[s] * 10)) % 2 == 0)) {
                p.setColor(Party.COLORS[s]);
                c.drawArc(kx - lane * 0.09f, ky - lane * 0.08f, kx + lane * 0.09f, ky + lane * 0.03f, 200, 140, true, p);
                c.drawCircle(kx, ky + lane * 0.02f, lane * 0.03f, p);
                c.drawArc(kx - lane * 0.06f, ky + lane * 0.03f, kx + lane * 0.06f, ky + lane * 0.09f, 200, 140, true, p);
            }
            Badge.pin(c, s, false, kx, ky - lane * 0.08f, Math.min(H * 0.028f, lane * 0.07f), t);
            if (pop[s] > 0) {
                a.tx.setTextAlign(Paint.Align.CENTER);
                a.tx.setTextSize(lane * 0.1f);
                a.tx.setColor(0xFFFFFFFF);
                c.drawText("+", kx + lane * 0.15f, ky, a.tx);
            }
            a.tx.setTextAlign(Paint.Align.RIGHT);
            a.tx.setTextSize(lane * 0.1f);
            a.tx.setColor(0xFF0F172A);
            c.drawText(pl.score + " mata", W * 0.98f, lane * 0.14f, a.tx);
            c.restore();
            k++;
        }
        p.setColor(0xFF0F172A);
        c.drawRect(0, 0, W, top, p);
        long left = Math.max(0, (long) (TIME - (a.time - roundStart)));
        a.tx.setTextAlign(Paint.Align.CENTER);
        a.tx.setTextSize(H * 0.04f);
        a.tx.setColor(0xFFFFFFFF);
        c.drawText("Laluan " + (round + 1) + "/3: " + COURSE[crs] + "   ·   " + left + "s   ·   ▲▼ naik turun, lalu dalam gelang", W / 2, top * 0.7f, a.tx);
        if (banner > 0) {
            a.tx.setTextSize(H * 0.07f);
            a.tx.setColor(Color.argb((int) (255 * Math.min(1, banner)), 253, 224, 71));
            c.drawText(COURSE[crs].toUpperCase(), W / 2, H * 0.5f, a.tx);
        }
    }
}
