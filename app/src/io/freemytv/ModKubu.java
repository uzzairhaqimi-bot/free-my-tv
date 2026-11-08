package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Kubu Pasir: co-op tower defence on a beach. Crabs march along the sand path towards the sandcastle; the players move
 * their own cursor and press OK / A on the sand to build a water-pistol tower (10 shells), or on a tower to make it
 * stronger (15 shells, up to level 3). Shells are shared and come from every crab stopped. Ten waves, a giant crab
 * on the fifth and the tenth. The castle has 20 flags; it falls when they're gone.
 */
final class ModKubu implements Arked4.Mod {
    private static final int GC = 16, GR = 9, WAVES = 10;
    private final Arked4 a;
    private final boolean[][] path = new boolean[GR][GC];
    private final int[][] tower = new int[GR][GC];      // 0 none, 1..3 level
    private final float[][] towerCool = new float[GR][GC];
    private final int[] wpX = new int[40], wpY = new int[40];
    private int nWp;
    private final float[][] crab = new float[40][6];   // progress along the path (cells), hp, max, speed, alive, giant
    private final float[][] drop = new float[40][5];   // x, y, tx, ty, life
    private final int[] cx = new int[4], cy = new int[4];
    private final float[] moveT = new float[4];
    private int shells, castle, wave, toSpawn;
    private float spawnT, banner, gs, ox, oy, waitT;

    ModKubu(Arked4 a) { this.a = a; }

    @Override public void build() {
        for (boolean[] r : path) java.util.Arrays.fill(r, false);
        for (int[] r : tower) java.util.Arrays.fill(r, 0);
        // a winding path from the right edge to the castle on the left
        nWp = 0;
        int x = GC - 1, y = 1 + a.rnd.nextInt(GR - 2);
        addWp(x, y);
        while (x > 1) {
            int run = 2 + a.rnd.nextInt(3);
            for (int k = 0; k < run && x > 1; k++) { x--; path[y][x] = true; }
            addWp(x, y);
            if (x <= 1) break;
            int ny = 1 + a.rnd.nextInt(GR - 2);
            while (y != ny) { y += ny > y ? 1 : -1; path[y][x] = true; }
            addWp(x, y);
        }
        path[y][0] = true;
        addWp(0, y);
        path[wpY[0]][wpX[0]] = true;
        shells = 40;
        castle = 20;
        wave = 0;
        int k = 0;
        for (Arked4.P pl : a.ps) { cx[pl.seat] = 4 + k * 3; cy[pl.seat] = k % 2 == 0 ? 0 : GR - 1; if (pl.on) k++; }
        nextWave();
    }

    private void addWp(int x, int y) { if (nWp < wpX.length) { wpX[nWp] = x; wpY[nWp] = y; nWp++; } }

    private void nextWave() {
        wave++;
        toSpawn = wave % 5 == 0 ? 1 : 5 + wave * 2;
        spawnT = 1;
        banner = 2;
        waitT = 0;
        for (float[] cr : crab) cr[4] = 0;
    }

    @Override public void update(float dt) {
        if (banner > 0) banner -= dt;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            float mx = a.ix(pl), my = a.iy(pl);
            moveT[s] -= dt;
            if ((Math.abs(mx) > 0.4f || Math.abs(my) > 0.4f) && moveT[s] <= 0) {
                if (Math.abs(mx) > Math.abs(my)) cx[s] = Math.max(0, Math.min(GC - 1, cx[s] + (mx > 0 ? 1 : -1)));
                else cy[s] = Math.max(0, Math.min(GR - 1, cy[s] + (my > 0 ? 1 : -1)));
                moveT[s] = 0.16f;
            } else if (Math.abs(mx) <= 0.4f && Math.abs(my) <= 0.4f) moveT[s] = 0;
            if (a.pressA(pl)) {
                int x = cx[s], y = cy[s];
                if (path[y][x] || x == 0) a.sound.sfx(GameMusic.SFX_WRONG);
                else if (tower[y][x] == 0 && shells >= 10) { tower[y][x] = 1; shells -= 10; a.sound.sfx(GameMusic.SFX_OK); pl.score += 5; }
                else if (tower[y][x] > 0 && tower[y][x] < 3 && shells >= 15) { tower[y][x]++; shells -= 15; a.sound.sfx(GameMusic.SFX_WIN); pl.score += 5; }
                else a.sound.sfx(GameMusic.SFX_TICK);
            }
        }
        if (toSpawn > 0) {
            spawnT -= dt;
            if (spawnT <= 0) {
                spawnT = Math.max(0.5f, 1.4f - wave * 0.08f);
                toSpawn--;
                for (float[] cr : crab) {
                    if (cr[4] > 0) continue;
                    boolean giant = wave % 5 == 0;
                    cr[0] = 0; cr[1] = cr[2] = giant ? 40 + wave * 8 : 2 + wave * 0.8f; cr[3] = giant ? 0.6f : 1.1f + wave * 0.05f; cr[4] = 1; cr[5] = giant ? 1 : 0;
                    break;
                }
            }
        }
        int alive = 0;
        for (float[] cr : crab) {
            if (cr[4] <= 0) continue;
            alive++;
            cr[0] += cr[3] * dt;
            if (cr[0] >= pathLen()) {   // reached the castle
                cr[4] = 0;
                castle -= cr[5] > 0 ? 6 : 1;
                a.sound.sfx(GameMusic.SFX_WRONG);
                for (Arked4.P pl : a.ps) if (pl.on) a.party.buzz(pl.seat, 120);
            }
        }
        // towers shoot at the crab furthest along within reach
        for (int y = 0; y < GR; y++) for (int x = 0; x < GC; x++) {
            int lv = tower[y][x];
            if (lv == 0) continue;
            towerCool[y][x] -= dt;
            if (towerCool[y][x] > 0) continue;
            float range = 2.2f + lv * 0.4f, best = -1;
            float[] tgt = null;
            for (float[] cr : crab) {
                if (cr[4] <= 0) continue;
                pos(cr[0]);
                if (Math.hypot(px - x, py - y) <= range && cr[0] > best) { best = cr[0]; tgt = cr; }
            }
            if (tgt == null) continue;
            towerCool[y][x] = 0.7f - lv * 0.12f;
            tgt[1] -= lv;
            pos(tgt[0]);
            for (float[] d : drop) if (d[4] <= 0) { d[0] = x; d[1] = y; d[2] = px; d[3] = py; d[4] = 0.15f; break; }
            if (tgt[1] <= 0) {
                tgt[4] = 0;
                shells += tgt[5] > 0 ? 30 : 3;
                a.sound.sfx(tgt[5] > 0 ? GameMusic.SFX_WIN : GameMusic.SFX_COIN);
            }
        }
        for (float[] d : drop) if (d[4] > 0) d[4] -= dt;
        if (castle <= 0) { done(false); return; }
        if (toSpawn == 0 && alive == 0) {
            waitT += dt;
            if (waitT > 1.5f) {
                if (wave >= WAVES) { done(true); return; }
                nextWave();
            }
        }
    }

    private void done(boolean won) {
        int total = 0;
        for (Arked4.P pl : a.ps) if (pl.on) total += pl.score;
        a.finish(won ? "Istana pasir selamat! 🏰 Semua " + WAVES + " gelombang ketam berjaya dihalang." : "Istana pasir runtuh pada gelombang " + wave + ". Cuba lagi!");
    }

    private float pathLen() {
        float l = 0;
        for (int i = 1; i < nWp; i++) l += Math.abs(wpX[i] - wpX[i - 1]) + Math.abs(wpY[i] - wpY[i - 1]);
        return l;
    }

    private float px, py;

    /** Where along the path (in cells) is (px, py). */
    private void pos(float d) {
        for (int i = 1; i < nWp; i++) {
            float seg = Math.abs(wpX[i] - wpX[i - 1]) + Math.abs(wpY[i] - wpY[i - 1]);
            if (d <= seg || i == nWp - 1) {
                float f = seg == 0 ? 0 : Math.min(1, d / seg);
                px = wpX[i - 1] + (wpX[i] - wpX[i - 1]) * f;
                py = wpY[i - 1] + (wpY[i] - wpY[i - 1]) * f;
                return;
            }
            d -= seg;
        }
        px = wpX[0]; py = wpY[0];
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        gs = Math.min(W * 0.98f / GC, H * 0.84f / GR);
        ox = (W - gs * GC) / 2;
        oy = H * 0.12f + (H * 0.86f - gs * GR) / 2;
        p.setColor(0xFF0EA5E9);   // the sea above the beach
        c.drawRect(0, 0, W, H, p);
        for (int y = 0; y < GR; y++) for (int x = 0; x < GC; x++) {
            float X = ox + x * gs, Y = oy + y * gs;
            p.setColor(path[y][x] ? 0xFFD6B26E : (x + y) % 2 == 0 ? 0xFFFDE68A : 0xFFFCD34D);
            c.drawRect(X, Y, X + gs + 1, Y + gs + 1, p);
            int lv = tower[y][x];
            if (lv > 0) {
                p.setColor(0xFF0369A1);
                c.drawRoundRect(X + gs * 0.2f, Y + gs * 0.25f, X + gs * 0.8f, Y + gs * 0.9f, gs * 0.1f, gs * 0.1f, p);
                p.setColor(0xFF7DD3FC);
                c.drawCircle(X + gs * 0.5f, Y + gs * 0.3f, gs * 0.2f, p);
                a.tx.setTextAlign(Paint.Align.CENTER);
                a.tx.setTextSize(gs * 0.3f);
                a.tx.setColor(0xFFFFFFFF);
                c.drawText(lv == 1 ? "I" : lv == 2 ? "II" : "III", X + gs * 0.5f, Y + gs * 0.8f, a.tx);
            }
        }
        // the sandcastle on the left
        float cX = ox + gs * 0.5f, cY = oy + wpY[nWp - 1] * gs + gs * 0.5f;
        p.setColor(0xFFCA8A04);
        c.drawRect(cX - gs * 0.6f, cY - gs * 0.6f, cX + gs * 0.6f, cY + gs * 0.5f, p);
        for (int k = -1; k <= 1; k++) c.drawRect(cX + k * gs * 0.4f - gs * 0.15f, cY - gs * 0.9f, cX + k * gs * 0.4f + gs * 0.15f, cY - gs * 0.55f, p);
        p.setColor(0xFFDC2626);
        c.drawRect(cX, cY - gs * 1.3f, cX + gs * 0.3f, cY - gs * 1.1f, p);
        for (float[] d : drop) {
            if (d[4] <= 0) continue;
            float f = 1 - d[4] / 0.15f;
            p.setColor(0xFF38BDF8);
            c.drawCircle(ox + (d[0] + (d[2] - d[0]) * f + 0.5f) * gs, oy + (d[1] + (d[3] - d[1]) * f + 0.5f) * gs, gs * 0.08f, p);
        }
        for (float[] cr : crab) {
            if (cr[4] <= 0) continue;
            pos(cr[0]);
            float X = ox + (px + 0.5f) * gs, Y = oy + (py + 0.5f) * gs, s = gs * (cr[5] > 0 ? 0.55f : 0.28f);
            p.setColor(cr[5] > 0 ? 0xFF7F1D1D : 0xFFDC2626);
            c.drawOval(X - s, Y - s * 0.6f, X + s, Y + s * 0.6f, p);
            float cl = (float) Math.sin(t * 10 + cr[0]) * s * 0.15f;
            c.drawCircle(X - s * 1.15f, Y - s * 0.4f + cl, s * 0.35f, p);
            c.drawCircle(X + s * 1.15f, Y - s * 0.4f - cl, s * 0.35f, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(X - s * 0.3f, Y - s * 0.5f, s * 0.18f, p);
            c.drawCircle(X + s * 0.3f, Y - s * 0.5f, s * 0.18f, p);
            if (cr[2] > 3) {
                p.setColor(0xFF22C55E);
                c.drawRect(X - s, Y - s * 1.1f, X - s + 2 * s * cr[1] / cr[2], Y - s * 0.95f, p);
            }
        }
        for (Arked4.P pl : a.ps) {   // each player's cursor: a frame in their colour, and their pin
            if (!pl.on) continue;
            int s = pl.seat;
            float X = ox + cx[s] * gs, Y = oy + cy[s] * gs;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(gs * 0.08f);
            p.setColor(Party.COLORS[s]);
            float in = gs * (0.06f + 0.03f * (float) Math.sin(t * 6 + s));
            c.drawRoundRect(X + in, Y + in, X + gs - in, Y + gs - in, gs * 0.15f, gs * 0.15f, p);
            p.setStyle(Paint.Style.FILL);
            a.label(c, pl, X + gs / 2, Y + gs * 0.1f);
            a.hudBox(c, s, pl.score + " mata");
        }
        a.tx.setTextAlign(Paint.Align.RIGHT);
        a.tx.setTextSize(H * 0.028f);
        a.tx.setColor(0xFFFFFFFF);
        c.drawText("🐚 " + shells + "   🏰 " + castle + "   Gelombang " + wave + "/" + WAVES, W * 0.99f, H * 0.1f, a.tx);
        a.tx.setTextAlign(Paint.Align.LEFT);
        a.tx.setTextSize(H * 0.024f);
        c.drawText("OK/A atas pasir: bina menara (10 🐚) · atas menara: naik taraf (15 🐚)", W * 0.01f, H * 0.1f, a.tx);
        if (banner > 0) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.07f);
            a.tx.setColor(Color.argb((int) (255 * Math.min(1, banner)), 253, 224, 71));
            c.drawText(wave % 5 == 0 ? "KETAM GERGASI!" : "GELOMBANG " + wave, W / 2, H * 0.5f, a.tx);
        }
    }
}
