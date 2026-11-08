package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Papan Luncur Pantai: everyone skates at once, each in their own strip of the screen. The board rolls on by itself;
 * ▲ / B jumps (over cones, up ramps), and in the air OK / A spins a trick. Land with the spin finished for points
 * (more for chains, and twice as much off a ramp); land halfway through and you fall. Three parks, 40 seconds each,
 * each with its own ramps. Highest total wins.
 */
final class ModLuncur implements Arked4.Mod {
    private static final String[] PARK = {"Pantai", "Bandar", "Taman"};
    private static final int[][] PAL = {{0xFF7DD3FC, 0xFFFDE68A, 0xFFF59E0B}, {0xFF94A3B8, 0xFF9CA3AF, 0xFF475569}, {0xFF86EFAC, 0xFF65A30D, 0xFF3F6212}};
    private static final float PARK_TIME = 40, SPEED = 7;   // world units per second

    private final Arked4 a;
    private final float[] wx = new float[4], h = new float[4], vh = new float[4], spin = new float[4], fallT = new float[4], combo = new float[4], pop = new float[4];
    private final boolean[] air = new boolean[4], ramped = new boolean[4], tricking = new boolean[4];
    private final String[] popText = new String[4];
    private final float[] ramps = new float[40], cones = new float[60], coins = new float[60], coinH = new float[60];
    private final boolean[] coinTaken = new boolean[4 * 60];
    private int nr, nc, nk, park;
    private float parkStart, banner;

    ModLuncur(Arked4 a) { this.a = a; }

    @Override public void build() {
        park = 0;
        newPark();
    }

    private void newPark() {
        parkStart = a.time;
        banner = 2;
        nr = nc = nk = 0;
        float x = 8;
        while (x < SPEED * PARK_TIME + 20) {   // a course: ramps, cones and coins, closer together in later parks
            float r = a.rnd.nextFloat();
            if (r < 0.35f && nr < ramps.length) ramps[nr++] = x;
            else if (r < 0.75f && nc < cones.length) cones[nc++] = x;
            if (nk < coins.length) { coins[nk] = x + 2; coinH[nk] = 1 + a.rnd.nextFloat() * 2.5f; nk++; }
            x += 6 + a.rnd.nextFloat() * (6 - park * 1.5f);
        }
        java.util.Arrays.fill(coinTaken, false);
        for (Arked4.P pl : a.ps) {
            int s = pl.seat;
            wx[s] = 0; h[s] = 0; vh[s] = 0; spin[s] = 0; fallT[s] = 0; combo[s] = 0; air[s] = false; tricking[s] = false;
        }
    }

    @Override public void update(float dt) {
        if (banner > 0) banner -= dt;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            if (pop[s] > 0) pop[s] -= dt;
            boolean jump = a.pressJump(pl), trick = a.pressA(pl);
            if (fallT[s] > 0) { fallT[s] -= dt; continue; }
            float before = wx[s];
            wx[s] += SPEED * dt * (air[s] ? 1.05f : 1);
            if (!air[s]) {
                for (int i = 0; i < nr; i++) if (before < ramps[i] + 1.5f && wx[s] >= ramps[i] + 1.5f) { air[s] = true; ramped[s] = true; vh[s] = 9; }
                if (jump && !air[s]) { air[s] = true; ramped[s] = false; vh[s] = 7; a.sound.sfx(GameMusic.SFX_JUMP); }
                for (int i = 0; i < nc; i++) if (!air[s] && Math.abs(wx[s] - cones[i]) < 0.4f) crash(pl, "Langgar kon!");
            } else {
                if (trick && !tricking[s]) { tricking[s] = true; spin[s] = 0; }
                if (tricking[s]) {
                    spin[s] += dt * 720;   // degrees: a spin takes half a second
                    if (spin[s] >= 360) {   // a spin done: another can start straight away
                        spin[s] = 0;
                        tricking[s] = false;
                        combo[s]++;
                    }
                }
                vh[s] -= 18 * dt;
                h[s] += vh[s] * dt;
                if (h[s] <= 0) {   // landing
                    h[s] = 0;
                    air[s] = false;
                    if (tricking[s] && spin[s] > 40 && spin[s] < 320) crash(pl, "Jatuh!");
                    else if (combo[s] > 0) {
                        int pts = (int) (100 * combo[s] * combo[s] * (ramped[s] ? 2 : 1));
                        pl.score += pts;
                        popText[s] = (combo[s] > 1 ? combo[s] + "× putaran! " : "Putaran! ") + "+" + pts;
                        pop[s] = 1.2f;
                        a.sound.sfx(combo[s] > 1 ? GameMusic.SFX_WIN : GameMusic.SFX_OK);
                        a.party.buzz(s, 40);
                    }
                    tricking[s] = false;
                    spin[s] = 0;
                    combo[s] = 0;
                }
            }
            for (int i = 0; i < nk; i++) {
                if (coinTaken[s * 60 + i]) continue;
                if (Math.abs(wx[s] - coins[i]) < 0.6f && Math.abs(h[s] - coinH[i]) < 0.9f) { coinTaken[s * 60 + i] = true; pl.score += 10; a.sound.sfx(GameMusic.SFX_COIN); }
            }
        }
        if (a.time - parkStart > PARK_TIME) {
            park++;
            if (park >= PARK.length) {
                Arked4.P best = null;
                for (Arked4.P pl : a.ps) if (pl.on && (best == null || pl.score > best.score)) best = pl;
                a.finish(a.who(best) + " raja papan luncur: " + best.score + " mata! 🛹");
                return;
            }
            newPark();
        }
    }

    private void crash(Arked4.P pl, String why) {
        int s = pl.seat;
        fallT[s] = 1.5f;
        tricking[s] = false;
        spin[s] = 0;
        combo[s] = 0;
        air[s] = false;
        h[s] = 0;
        popText[s] = why;
        pop[s] = 1.2f;
        a.sound.sfx(GameMusic.SFX_WRONG);
        a.party.buzz(s, 200);
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        int[] pal = PAL[Math.min(park, PARK.length - 1)];
        int n = 0;
        for (Arked4.P pl : a.ps) if (pl.on) n++;
        float top = H * 0.08f, lane = (H - top) / Math.max(1, n);
        int k = 0;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            float y0 = top + k * lane, ground = y0 + lane * 0.82f, u = lane * 0.12f;   // u: one world unit in pixels
            float cam = wx[s] - W * 0.25f / u;
            c.save();
            c.clipRect(0, y0, W, y0 + lane);
            p.setColor(pal[0]);
            c.drawRect(0, y0, W, ground, p);
            p.setColor(pal[1]);
            c.drawRect(0, ground, W, y0 + lane, p);
            p.setColor(Party.COLORS[s]);
            c.drawRect(0, y0, W * 0.006f, y0 + lane, p);
            for (int i = 0; i < nr; i++) {
                float x = (ramps[i] - cam) * u;
                if (x < -u * 3 || x > W) continue;
                a.path.reset();
                a.path.moveTo(x, ground); a.path.lineTo(x + u * 1.5f, ground - u * 0.9f); a.path.lineTo(x + u * 1.5f, ground); a.path.close();
                p.setColor(pal[2]);
                c.drawPath(a.path, p);
            }
            for (int i = 0; i < nc; i++) {
                float x = (cones[i] - cam) * u;
                if (x < -u || x > W) continue;
                p.setColor(0xFFF97316);
                a.path.reset();
                a.path.moveTo(x - u * 0.25f, ground); a.path.lineTo(x, ground - u * 0.6f); a.path.lineTo(x + u * 0.25f, ground); a.path.close();
                c.drawPath(a.path, p);
                p.setColor(0xFFFFFFFF);
                c.drawRect(x - u * 0.12f, ground - u * 0.32f, x + u * 0.12f, ground - u * 0.24f, p);
            }
            for (int i = 0; i < nk; i++) {
                if (coinTaken[s * 60 + i]) continue;
                float x = (coins[i] - cam) * u;
                if (x < -u || x > W) continue;
                p.setColor(0xFFFACC15);
                c.drawCircle(x, ground - coinH[i] * u, u * 0.22f, p);
            }
            float sx = (wx[s] - cam) * u, sy = ground - h[s] * u;
            Badge.ring(c, s, sx, ground, u * 0.5f, t);
            skater(c, p, sx, sy, u, Party.COLORS[s], tricking[s] ? spin[s] : 0, fallT[s] > 0);
            Badge.pin(c, s, false, sx, sy - u * 1.9f, Math.min(H * 0.03f, lane * 0.08f), t);
            if (pop[s] > 0 && popText[s] != null) {
                a.tx.setTextAlign(Paint.Align.CENTER);
                a.tx.setTextSize(lane * 0.12f);
                a.tx.setColor(Color.argb((int) (255 * Math.min(1, pop[s])), 255, 255, 255));
                c.drawText(popText[s], sx + u * 3, y0 + lane * 0.3f, a.tx);
            }
            a.tx.setTextAlign(Paint.Align.RIGHT);
            a.tx.setTextSize(lane * 0.11f);
            a.tx.setColor(0xFF0F172A);
            c.drawText(pl.score + " mata", W * 0.98f, y0 + lane * 0.16f, a.tx);
            c.restore();
            k++;
        }
        long left = Math.max(0, (long) (PARK_TIME - (a.time - parkStart)));
        a.tx.setTextAlign(Paint.Align.CENTER);
        a.tx.setTextSize(H * 0.04f);
        a.tx.setColor(0xFFFFFFFF);
        p.setColor(0xFF0F172A);
        c.drawRect(0, 0, W, top, p);
        c.drawText("Taman " + (park + 1) + "/3: " + PARK[Math.min(park, 2)] + "   ·   " + left + "s   ·   ▲/B lompat, OK/A putar di udara", W / 2, top * 0.7f, a.tx);
        if (banner > 0) {
            a.tx.setTextSize(H * 0.07f);
            a.tx.setColor(Color.argb((int) (255 * Math.min(1, banner)), 253, 224, 71));
            c.drawText("TAMAN " + PARK[Math.min(park, 2)].toUpperCase(), W / 2, H * 0.5f, a.tx);
        }
    }

    private void skater(Canvas c, Paint p, float x, float y, float u, int col, float spin, boolean fallen) {
        c.save();
        c.rotate(fallen ? 80 : spin, x, y - u * 0.7f);
        p.setColor(0xFF111827);   // the board
        c.drawRoundRect(x - u * 0.5f, y - u * 0.12f, x + u * 0.5f, y, u * 0.06f, u * 0.06f, p);
        p.setColor(0xFFE5E7EB);
        c.drawCircle(x - u * 0.32f, y + u * 0.02f, u * 0.07f, p);
        c.drawCircle(x + u * 0.32f, y + u * 0.02f, u * 0.07f, p);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(u * 0.13f);
        p.setColor(0xFF1E3A8A);
        c.drawLine(x - u * 0.15f, y - u * 0.12f, x, y - u * 0.6f, p);
        c.drawLine(x + u * 0.15f, y - u * 0.12f, x, y - u * 0.6f, p);
        p.setColor(col);
        c.drawLine(x, y - u * 0.6f, x + u * 0.05f, y - u * 1.1f, p);
        p.setColor(0xFFD6A77A);
        c.drawLine(x, y - u, x - u * 0.4f, y - u * 0.8f, p);
        c.drawLine(x, y - u, x + u * 0.4f, y - u * 1.0f, p);
        c.drawCircle(x + u * 0.08f, y - u * 1.3f, u * 0.2f, p);
        p.setColor(col);   // a cap
        c.drawArc(x - u * 0.13f, y - u * 1.52f, x + u * 0.3f, y - u * 1.15f, 180, 180, true, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        c.restore();
    }
}
