package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Pulau Misteri: a co-op adventure seen from above, over four islands. Each island is nine rooms (3 × 3), made anew
 * every game. Somewhere is the chest with the island's key; with it, the boat in another room takes everyone to the
 * next island. Crabs and bats get in the way (A swings a stick at them). Some rooms have stone plates that only open
 * a treasure chest when players stand on all of them at once: a puzzle for friends. Walking through a doorway
 * takes the whole group to the next room.
 */
final class ModPulau implements Arked4.Mod {
    private static final String[] ISLE = {"Pulau Pasir", "Pulau Hutan", "Pulau Batu", "Pulau Api"};
    private static final int[][] PAL = {{0xFFFDE68A, 0xFFFCD34D, 0xFFA16207}, {0xFF86EFAC, 0xFF4ADE80, 0xFF14532D},
            {0xFFD6D3D1, 0xFFA8A29E, 0xFF44403C}, {0xFFFDBA74, 0xFFF97316, 0xFF7F1D1D}};
    private static final int RC = 15, RR = 9;   // tiles per room

    private final Arked4 a;
    private final byte[][][][] rooms = new byte[3][3][RR][RC];   // 0 floor, 1 wall, 2 plate, 3 chest (key), 4 chest (treasure), 5 boat, 6 open chest
    private final boolean[][] seen = new boolean[3][3];
    private final float[][] en = new float[8][6];   // x, y, hp, kind (0 crab, 1 bat), t, alive (tiles)
    private final float[] swing = new float[4], down = new float[4], hp = new float[4], fx = new float[4], fy = new float[4];
    private int isle, rx, ry;
    private boolean key;
    private float banner, msgT;
    private String msg = "";
    private float ts, ox, oy;

    ModPulau(Arked4 a) { this.a = a; }

    @Override public void build() {
        isle = 0;
        for (Arked4.P pl : a.ps) hp[pl.seat] = 5;
        newIsle();
    }

    private void newIsle() {
        key = false;
        for (boolean[] r : seen) java.util.Arrays.fill(r, false);
        int keyRoom = 1 + a.rnd.nextInt(8), boatRoom;
        do { boatRoom = 1 + a.rnd.nextInt(8); } while (boatRoom == keyRoom);
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
            byte[][] m = rooms[y][x];
            for (int j = 0; j < RR; j++) for (int i = 0; i < RC; i++) {
                boolean edge = i == 0 || j == 0 || i == RC - 1 || j == RR - 1;
                boolean door = (i == 0 && x > 0 || i == RC - 1 && x < 2) && j == RR / 2 || (j == 0 && y > 0 || j == RR - 1 && y < 2) && i == RC / 2;
                m[j][i] = (byte) (edge && !door ? 1 : 0);
            }
            for (int k = 0; k < 10; k++) {   // rocks and bushes, kept off the paths between doors
                int i = 2 + a.rnd.nextInt(RC - 4), j = 2 + a.rnd.nextInt(RR - 4);
                if (i == RC / 2 || j == RR / 2) continue;
                m[j][i] = 1;
            }
            int id = y * 3 + x;
            if (id == keyRoom) m[RR / 2 - 2][RC / 2 + 3] = 3;
            if (id == boatRoom) m[RR / 2 + 2][RC / 2 - 4] = 5;
            if (id != 0 && id != keyRoom && a.rnd.nextFloat() < 0.4f) {   // a plate puzzle
                int players = 0;
                for (Arked4.P pl : a.ps) if (pl.on) players++;
                int plates = Math.min(3, Math.max(1, players));
                for (int k = 0; k < plates; k++) m[2 + (k % 2) * (RR - 5)][3 + k * 4] = 2;
                m[RR / 2][RC - 3] = 4;
            }
        }
        rx = 0; ry = 0;
        enter(-1);
        banner = 2.5f;
    }

    /** Puts everyone in room (rx, ry), next to the doorway they came through (from: 0 left, 1 right, 2 top, 3 bottom). */
    private void enter(int from) {
        seen[ry][rx] = true;
        float sx = RC / 2f, sy = RR / 2f;
        if (from == 0) sx = 1.5f; else if (from == 1) sx = RC - 2.5f; else if (from == 2) sy = 1.5f; else if (from == 3) sy = RR - 2.5f;
        int k = 0;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            pl.x = sx + (from >= 2 ? (k - 1.5f) * 0.8f : 0);
            pl.y = sy + (from < 2 ? (k - 1.5f) * 0.8f : 0);
            if (from < 0) { pl.x = 3 + k; pl.y = RR / 2f; }
            k++;
        }
        for (float[] e : en) e[5] = 0;
        int n = 2 + isle + a.rnd.nextInt(2);
        for (int i = 0; i < n && i < en.length; i++) {
            float[] e = en[i];
            for (int tries = 0; tries < 20; tries++) {
                e[0] = 2 + a.rnd.nextFloat() * (RC - 4);
                e[1] = 1.5f + a.rnd.nextFloat() * (RR - 3);
                if (!wall(e[0], e[1]) && Math.abs(e[0] - sx) > 3) break;
            }
            e[3] = a.rnd.nextFloat() < 0.35f + isle * 0.1f ? 1 : 0;
            e[2] = 1 + isle / 2;
            e[4] = a.rnd.nextFloat() * 6;
            e[5] = from < 0 ? 0 : 1;   // the first room is calm
        }
    }

    private boolean wall(float x, float y) {
        int i = (int) Math.floor(x), j = (int) Math.floor(y);
        if (i < 0 || j < 0 || i >= RC || j >= RR) return false;
        byte t = rooms[ry][rx][j][i];
        return t == 1 || t == 3 || t == 4 || t == 6;
    }

    private void say(String s) { msg = s; msgT = 3; }

    @Override public void update(float dt) {
        if (banner > 0) banner -= dt;
        if (msgT > 0) msgT -= dt;
        byte[][] m = rooms[ry][rx];
        float rad = 0.35f;
        int onPlates = 0, plates = 0;
        for (int j = 0; j < RR; j++) for (int i = 0; i < RC; i++) if (m[j][i] == 2) plates++;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            if (down[s] > 0) { down[s] -= dt; if (down[s] <= 0) { hp[s] = 3; pl.inv = 2; } continue; }
            if (pl.inv > 0) pl.inv -= dt;
            if (swing[s] > 0) swing[s] -= dt;
            pl.cool -= dt;
            float mx = a.ix(pl), my = a.iy(pl), l = (float) Math.hypot(mx, my);
            if (l > 0.3f) {
                fx[s] = mx / l; fy[s] = my / l;
                float sp = 4.2f * dt * Math.min(1, l);
                float nx = pl.x + fx[s] * sp, ny = pl.y + fy[s] * sp;
                if (!wall(nx + Math.signum(fx[s]) * rad, pl.y)) pl.x = nx;
                if (!wall(pl.x, ny + Math.signum(fy[s]) * rad)) pl.y = ny;
            }
            if (a.pressA(pl) && pl.cool <= 0) {
                pl.cool = 0.35f;
                swing[s] = 0.2f;
                float hx = pl.x + fx[s] * 0.8f, hy = pl.y + fy[s] * 0.8f;
                for (float[] e : en) {
                    if (e[5] <= 0 || Math.hypot(e[0] - hx, e[1] - hy) > 0.8f) continue;
                    e[0] += fx[s] * 0.8f; e[1] += fy[s] * 0.8f;
                    if (--e[2] <= 0) { e[5] = 0; pl.score += 10; a.sound.sfx(GameMusic.SFX_COIN); }
                }
                // opening a chest next to you
                int ci = (int) Math.floor(hx), cj = (int) Math.floor(hy);
                if (ci >= 0 && cj >= 0 && ci < RC && cj < RR) {
                    if (m[cj][ci] == 3) { m[cj][ci] = 6; key = true; pl.score += 50; say("Kunci dijumpai! Cari bot untuk ke pulau seterusnya."); a.sound.sfx(GameMusic.SFX_WIN); }
                    else if (m[cj][ci] == 4 && plates == 0) { m[cj][ci] = 6; pl.score += 80; for (Arked4.P o : a.ps) hp[o.seat] = 5; say("Harta karun! Semua tenaga penuh."); a.sound.sfx(GameMusic.SFX_WIN); }
                    else if (m[cj][ci] == 4) say("Peti berkunci. Semua batu leper mesti dipijak serentak!");
                }
            }
            int pi = (int) Math.floor(pl.x), pj = (int) Math.floor(pl.y);
            if (pi >= 0 && pj >= 0 && pi < RC && pj < RR) {
                if (m[pj][pi] == 2) onPlates++;
                if (m[pj][pi] == 5) {
                    if (key) { nextIsle(); return; }
                    else if (msgT <= 0) say("Bot memerlukan kunci pulau ini.");
                }
            }
            // through a doorway: everyone follows
            if (pl.x < 0.2f && rx > 0) { rx--; enter(1); return; }
            if (pl.x > RC - 0.2f && rx < 2) { rx++; enter(0); return; }
            if (pl.y < 0.2f && ry > 0) { ry--; enter(3); return; }
            if (pl.y > RR - 0.2f && ry < 2) { ry++; enter(2); return; }
        }
        if (plates > 0 && onPlates >= plates) {   // the plate puzzle is solved: the chest opens up
            for (int j = 0; j < RR; j++) for (int i = 0; i < RC; i++) if (m[j][i] == 2) m[j][i] = 0;
            a.sound.sfx(GameMusic.SFX_OK);
            say("Klik! Peti harta terbuka. Pukul peti untuk ambil.");
        }
        for (float[] e : en) {
            if (e[5] <= 0) continue;
            e[4] += dt;
            Arked4.P tgt = null;
            float bd = Float.MAX_VALUE;
            for (Arked4.P pl : a.ps) if (pl.on && down[pl.seat] <= 0) { float d = (float) Math.hypot(pl.x - e[0], pl.y - e[1]); if (d < bd) { bd = d; tgt = pl; } }
            if (tgt == null) continue;
            float sp = (e[3] == 1 ? 2.2f : 1.4f + isle * 0.2f) * dt;
            float dx = tgt.x - e[0], dy = tgt.y - e[1];
            if (e[3] == 0) { if (Math.abs(dx) > Math.abs(dy)) dy = 0; else dx = 0; }   // crabs walk straight lines
            float l = (float) Math.hypot(dx, dy);
            if (l > 0.01f) {
                float nx = e[0] + dx / l * sp, ny = e[1] + dy / l * sp + (e[3] == 1 ? (float) Math.sin(e[4] * 6) * dt : 0);
                if (e[3] == 1 || !wall(nx, ny)) { e[0] = nx; e[1] = ny; }
            }
            if (bd < 0.6f && tgt.inv <= 0) {
                hp[tgt.seat]--;
                tgt.inv = 1.2f;
                a.party.buzz(tgt.seat, 150);
                a.sound.sfx(GameMusic.SFX_WRONG);
                if (hp[tgt.seat] <= 0) { down[tgt.seat] = 3; tgt.score = Math.max(0, tgt.score - 20); }
            }
        }
    }

    private void nextIsle() {
        isle++;
        a.sound.sfx(GameMusic.SFX_WIN);
        if (isle >= ISLE.length) {
            int total = 0;
            for (Arked4.P pl : a.ps) if (pl.on) total += pl.score;
            a.finish("Semua 4 pulau diteroka! 🏝  Markah pasukan " + total);
            return;
        }
        newIsle();
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        int[] pal = PAL[isle];
        p.setColor(0xFF0369A1);   // the sea around
        c.drawRect(0, 0, W, H, p);
        ts = Math.min(W * 0.96f / RC, H * 0.84f / RR);
        ox = (W - ts * RC) / 2;
        oy = H * 0.12f + (H * 0.86f - ts * RR) / 2;
        byte[][] m = rooms[ry][rx];
        for (int j = 0; j < RR; j++) for (int i = 0; i < RC; i++) {
            float x = ox + i * ts, y = oy + j * ts;
            byte k = m[j][i];
            p.setColor((i + j) % 2 == 0 ? pal[0] : pal[1]);
            c.drawRect(x, y, x + ts + 1, y + ts + 1, p);
            if (k == 1) {
                p.setColor(pal[2]);
                c.drawRoundRect(x + ts * 0.05f, y + ts * 0.05f, x + ts * 0.95f, y + ts * 0.95f, ts * 0.3f, ts * 0.3f, p);
                p.setColor(0x33FFFFFF);
                c.drawCircle(x + ts * 0.35f, y + ts * 0.35f, ts * 0.15f, p);
            } else if (k == 2) {
                p.setColor(0xFF64748B);
                c.drawRoundRect(x + ts * 0.15f, y + ts * 0.15f, x + ts * 0.85f, y + ts * 0.85f, ts * 0.1f, ts * 0.1f, p);
                p.setColor(Color.argb((int) (120 + 100 * Math.sin(t * 4)), 250, 204, 21));
                c.drawCircle(x + ts / 2, y + ts / 2, ts * 0.12f, p);
            } else if (k == 3 || k == 4 || k == 6) {
                p.setColor(k == 6 ? 0xFF78350F : 0xFFA16207);
                c.drawRoundRect(x + ts * 0.1f, y + ts * 0.25f, x + ts * 0.9f, y + ts * 0.85f, ts * 0.08f, ts * 0.08f, p);
                p.setColor(k == 3 ? 0xFFFDE047 : k == 4 ? 0xFFE879F9 : 0xFF451A03);
                c.drawRect(x + ts * 0.42f, y + ts * 0.45f, x + ts * 0.58f, y + ts * 0.62f, p);
            } else if (k == 5) {
                p.setColor(0xFF7C2D12);
                c.drawRoundRect(x - ts * 0.2f, y + ts * 0.3f, x + ts * 1.2f, y + ts * 0.8f, ts * 0.25f, ts * 0.25f, p);
                p.setColor(0xFFF5F5F4);
                a.path.reset();
                a.path.moveTo(x + ts * 0.5f, y - ts * 0.4f); a.path.lineTo(x + ts * 1.0f, y + ts * 0.25f); a.path.lineTo(x + ts * 0.5f, y + ts * 0.25f); a.path.close();
                c.drawPath(a.path, p);
            }
        }
        for (float[] e : en) {
            if (e[5] <= 0) continue;
            float x = ox + e[0] * ts, y = oy + e[1] * ts;
            if (e[3] == 0) {   // crab
                p.setColor(0xFFDC2626);
                c.drawOval(x - ts * 0.35f, y - ts * 0.2f, x + ts * 0.35f, y + ts * 0.2f, p);
                float cl = (float) Math.sin(e[4] * 8) * ts * 0.05f;
                c.drawCircle(x - ts * 0.42f, y - ts * 0.15f + cl, ts * 0.12f, p);
                c.drawCircle(x + ts * 0.42f, y - ts * 0.15f - cl, ts * 0.12f, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(x - ts * 0.1f, y - ts * 0.18f, ts * 0.06f, p);
                c.drawCircle(x + ts * 0.1f, y - ts * 0.18f, ts * 0.06f, p);
            } else {   // bat
                float f = (float) Math.sin(e[4] * 16) * ts * 0.15f;
                p.setColor(0xFF4C1D95);
                a.path.reset();
                a.path.moveTo(x, y); a.path.lineTo(x - ts * 0.5f, y - ts * 0.15f + f); a.path.lineTo(x - ts * 0.25f, y + ts * 0.15f); a.path.close();
                a.path.moveTo(x, y); a.path.lineTo(x + ts * 0.5f, y - ts * 0.15f + f); a.path.lineTo(x + ts * 0.25f, y + ts * 0.15f); a.path.close();
                c.drawPath(a.path, p);
                c.drawCircle(x, y, ts * 0.16f, p);
            }
        }
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            float x = ox + pl.x * ts, y = oy + pl.y * ts;
            a.hudBox(c, s, (down[s] > 0 ? "pengsan…" : hearts(hp[s])) + "  " + pl.score);
            if (pl.inv > 0 && ((int) (pl.inv * 10)) % 2 == 0) continue;
            Badge.ring(c, s, x, y, ts * 0.4f, t);
            p.setColor(down[s] > 0 ? 0xFF94A3B8 : Party.COLORS[s]);
            c.drawCircle(x, y, ts * 0.3f, p);
            p.setColor(0xFFFDE7C7);
            c.drawCircle(x + fx[s] * ts * 0.15f, y + fy[s] * ts * 0.15f - ts * 0.05f, ts * 0.17f, p);
            if (swing[s] > 0) {   // the stick's swing
                p.setColor(0xFF92400E);
                p.setStrokeWidth(ts * 0.08f);
                float an = (float) Math.atan2(fy[s], fx[s]) + (swing[s] - 0.1f) * 8;
                c.drawLine(x, y, x + (float) Math.cos(an) * ts * 0.8f, y + (float) Math.sin(an) * ts * 0.8f, p);
            }
            a.label(c, pl, x, y - ts * 0.35f);
        }
        // the island map: rooms seen, where you are
        float ms = H * 0.025f, mx0 = W - ms * 3.6f, my0 = H * 0.075f;
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
            p.setColor(x == rx && y == ry ? 0xFFFDE047 : seen[y][x] ? 0xCCFFFFFF : 0x44FFFFFF);
            c.drawRect(mx0 + x * ms * 1.1f, my0 + y * ms * 1.1f, mx0 + x * ms * 1.1f + ms, my0 + y * ms * 1.1f + ms, p);
        }
        a.tx.setTextAlign(Paint.Align.RIGHT);
        a.tx.setTextSize(H * 0.026f);
        a.tx.setColor(0xFFFFFFFF);
        c.drawText(ISLE[isle] + (key ? "  🔑" : ""), mx0 - ms, H * 0.045f, a.tx);
        if (msgT > 0) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.032f);
            p.setColor(0xCC000000);
            float w = a.tx.measureText(msg) + H * 0.05f;
            c.drawRoundRect(W / 2 - w / 2, H * 0.9f, W / 2 + w / 2, H * 0.97f, H * 0.02f, H * 0.02f, p);
            a.tx.setColor(0xFFFFFFFF);
            c.drawText(msg, W / 2, H * 0.945f, a.tx);
        }
        if (banner > 0) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.07f);
            a.tx.setColor(Color.argb((int) (255 * Math.min(1, banner)), 253, 224, 71));
            c.drawText(ISLE[isle].toUpperCase(), W / 2, H * 0.45f, a.tx);
        }
    }

    private static String hearts(float n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < (int) n; i++) b.append('♥');
        return b.toString();
    }
}
