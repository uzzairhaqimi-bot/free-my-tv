package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Panjat Kelapa: a climbing race for four. A cheeky monkey at the top of the coconut grove rolls coconuts down the
 * planks; the players run, climb the ladders (▲▼) and jump the coconuts (OK / A) to reach the durian at the top. Jumping
 * over a coconut scores; being hit leaves you dizzy for a moment. Four levels, each with more and faster coconuts.
 */
final class ModKelapa implements Arked4.Mod {
    private static final int FLOORS = 5;
    private final Arked4 a;
    private final float[] floorY = new float[FLOORS];
    private final float[][] ladder = new float[FLOORS * 3][2];   // x, from floor (to floor + 1)
    private int nLadder, level;
    private final float[][] nut = new float[20][5];   // x, floor, dir, alive, fall (pixels below the floor while dropping)
    private final float[] dizzy = new float[4], climbY = new float[4], vy = new float[4], z = new float[4];
    private final int[] floor = new int[4];
    private final boolean[] climbing = new boolean[4], top = new boolean[4], scored = new boolean[4 * 20];
    private float throwT, levelEnd = -1;
    private int finishedHere;

    ModKelapa(Arked4 a) { this.a = a; }

    @Override public void build() {
        level = 0;
        nextLevel();
    }

    private void nextLevel() {
        level++;
        levelEnd = -1;
        finishedHere = 0;
        for (int i = 0; i < FLOORS; i++) floorY[i] = a.H * (0.92f - i * 0.17f);
        nLadder = 0;
        for (int f = 0; f < FLOORS - 1; f++) {
            int n = Math.max(1, 3 - level / 2);
            for (int k = 0; k < n; k++) { ladder[nLadder][0] = a.W * (0.12f + a.rnd.nextFloat() * 0.76f); ladder[nLadder][1] = f; nLadder++; }
        }
        for (float[] c : nut) c[3] = 0;
        java.util.Arrays.fill(scored, false);
        int k = 0;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            pl.x = a.W * (0.06f + k * 0.05f);
            floor[s] = 0; climbing[s] = false; top[s] = false; dizzy[s] = 0; z[s] = 0; vy[s] = 0;
            k++;
        }
        throwT = 1;
    }

    @Override public void update(float dt) {
        float W = a.W, H = a.H;
        if (levelEnd >= 0) {
            levelEnd += dt;
            if (levelEnd > 2.5f) {
                if (level >= 4) {
                    Arked4.P best = null;
                    for (Arked4.P pl : a.ps) if (pl.on && (best == null || pl.score > best.score)) best = pl;
                    a.finish(a.who(best) + " raja panjat kelapa: " + best.score + " mata! 🥥");
                } else nextLevel();
            }
            return;
        }
        throwT -= dt;
        if (throwT <= 0) {   // the monkey throws
            throwT = Math.max(0.8f, 2.6f - level * 0.4f) * (0.7f + a.rnd.nextFloat() * 0.6f);
            for (float[] c : nut) if (c[3] <= 0) { c[0] = W * 0.15f; c[1] = FLOORS - 1; c[2] = 1; c[3] = 1; c[4] = 0; break; }
        }
        float nutSpeed = W * (0.16f + level * 0.035f);
        for (int ni = 0; ni < nut.length; ni++) {
            float[] c = nut[ni];
            if (c[3] <= 0) continue;
            if (c[4] > 0) {   // dropping to the floor below
                c[4] += H * 0.8f * dt;
                if (c[4] >= H * 0.17f) { c[4] = 0; c[1]--; c[2] = -c[2]; if (c[1] < 0) c[3] = 0; }
                continue;
            }
            c[0] += c[2] * nutSpeed * dt;
            if (c[0] < W * 0.04f || c[0] > W * 0.96f) { c[0] = Math.max(W * 0.04f, Math.min(W * 0.96f, c[0])); c[4] = 1; }
            for (int li = 0; li < nLadder; li++) {   // sometimes a coconut tumbles down a ladder
                if (ladder[li][1] == c[1] - 1 && Math.abs(ladder[li][0] - c[0]) < nutSpeed * dt && a.rnd.nextFloat() < 0.35f) c[4] = 1;
            }
        }
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            if (top[s]) continue;
            if (dizzy[s] > 0) { dizzy[s] -= dt; continue; }
            float mx = a.ix(pl), my = a.iy(pl);
            if (pl.bot) {   // the TV heads for the nearest ladder up, and jumps coconuts that come close
                float bx = -1, bd = Float.MAX_VALUE;
                for (int li = 0; li < nLadder; li++) if (ladder[li][1] == floor[s] && Math.abs(ladder[li][0] - pl.x) < bd) { bd = Math.abs(ladder[li][0] - pl.x); bx = ladder[li][0]; }
                mx = bx < 0 ? 0 : bd < W * 0.01f ? 0 : Math.signum(bx - pl.x);
                my = bd < W * 0.01f || climbing[s] ? -1 : 0;
            }
            boolean jump = pl.bot ? nutNear(pl, W * 0.08f) && a.rnd.nextFloat() < 0.15f : a.pressA(pl);
            if (climbing[s]) {
                climbY[s] -= my * H * 0.35f * dt;
                if (climbY[s] >= H * 0.17f) { climbing[s] = false; floor[s]++; climbY[s] = 0; if (floor[s] >= FLOORS - 1) reachTop(pl); }
                else if (climbY[s] <= 0) { climbing[s] = false; climbY[s] = 0; }
                continue;
            }
            if (my < -0.5f && z[s] == 0) {   // ▲ at a ladder starts climbing
                for (int li = 0; li < nLadder; li++) if (ladder[li][1] == floor[s] && Math.abs(ladder[li][0] - pl.x) < W * 0.02f) { climbing[s] = true; pl.x = ladder[li][0]; climbY[s] = 1; }
                if (climbing[s]) continue;
            }
            if (Math.abs(mx) > 0.3f) { pl.x = Math.max(W * 0.03f, Math.min(W * 0.97f, pl.x + Math.signum(mx) * W * 0.22f * dt)); pl.face = mx > 0; }
            if (jump && z[s] == 0) { vy[s] = H * 0.75f; z[s] = 0.01f; a.sound.sfx(GameMusic.SFX_JUMP); }
            if (z[s] > 0) { z[s] += vy[s] * dt; vy[s] -= H * 2.4f * dt; if (z[s] <= 0) { z[s] = 0; vy[s] = 0; } }
            for (int ni = 0; ni < nut.length; ni++) {
                float[] c = nut[ni];
                if (c[3] <= 0 || c[4] > 0 || c[1] != floor[s]) continue;
                if (Math.abs(c[0] - pl.x) < H * 0.035f) {
                    if (z[s] > H * 0.04f) {
                        if (!scored[s * 20 + ni]) { scored[s * 20 + ni] = true; pl.score += 5; a.sound.sfx(GameMusic.SFX_COIN); }
                    } else {
                        dizzy[s] = 1.3f;
                        c[3] = 0;
                        a.party.buzz(s, 200);
                        a.sound.sfx(GameMusic.SFX_WRONG);
                    }
                }
            }
        }
        int on = 0;
        for (Arked4.P pl : a.ps) if (pl.on) on++;
        if (finishedHere >= on || (finishedHere > 0 && finishedHere >= Math.max(1, on - 1) && on > 1)) levelEnd = 0;
    }

    private boolean nutNear(Arked4.P pl, float d) {
        for (float[] c : nut) if (c[3] > 0 && c[4] == 0 && c[1] == floor[pl.seat] && Math.abs(c[0] - pl.x) < d) return true;
        return false;
    }

    private void reachTop(Arked4.P pl) {
        top[pl.seat] = true;
        finishedHere++;
        pl.score += finishedHere == 1 ? 30 : finishedHere == 2 ? 20 : 10;
        a.sound.sfx(GameMusic.SFX_WIN);
        a.party.buzz(pl.seat, 100);
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        p.setColor(0xFF7DD3FC);
        c.drawRect(0, 0, W, H, p);
        p.setColor(0xFF15803D);   // palm crowns behind
        for (int i = 0; i < 6; i++) c.drawCircle(W * (0.08f + i * 0.17f), H * 0.12f, H * 0.12f, p);
        for (int li = 0; li < nLadder; li++) {
            float x = ladder[li][0], y1 = floorY[(int) ladder[li][1]], y2 = floorY[(int) ladder[li][1] + 1];
            p.setColor(0xFF92400E);
            p.setStrokeWidth(H * 0.008f);
            c.drawLine(x - H * 0.02f, y1, x - H * 0.02f, y2, p);
            c.drawLine(x + H * 0.02f, y1, x + H * 0.02f, y2, p);
            for (float y = y2 + H * 0.02f; y < y1; y += H * 0.03f) c.drawLine(x - H * 0.02f, y, x + H * 0.02f, y, p);
        }
        for (int i = 0; i < FLOORS; i++) {
            p.setColor(0xFFA16207);
            c.drawRect(W * 0.03f, floorY[i], W * 0.97f, floorY[i] + H * 0.018f, p);
            p.setColor(0xFF78350F);
            for (float x = W * 0.03f; x < W * 0.97f; x += H * 0.06f) c.drawLine(x, floorY[i], x, floorY[i] + H * 0.018f, p);
        }
        // the monkey and the durian prize
        float my = floorY[FLOORS - 1];
        p.setColor(0xFF78350F);
        c.drawCircle(W * 0.1f, my - H * 0.05f, H * 0.04f, p);
        c.drawCircle(W * 0.1f, my - H * 0.1f, H * 0.03f, p);
        p.setColor(0xFFFDE7C7);
        c.drawCircle(W * 0.1f, my - H * 0.095f, H * 0.018f, p);
        p.setColor(0xFF84CC16);
        c.drawCircle(W * 0.9f, my - H * 0.04f, H * 0.035f, p);
        p.setColor(0xFF3F6212);
        for (int k = 0; k < 8; k++) { double an = k * Math.PI / 4; c.drawCircle(W * 0.9f + (float) Math.cos(an) * H * 0.03f, my - H * 0.04f + (float) Math.sin(an) * H * 0.03f, H * 0.008f, p); }
        for (float[] n : nut) {
            if (n[3] <= 0) continue;
            float x = n[0], y = floorY[(int) n[1]] - H * 0.025f + n[4];
            p.setColor(0xFF78350F);
            c.drawCircle(x, y, H * 0.025f, p);
            p.setColor(0xFF451A03);
            c.save();
            c.rotate(x * 2, x, y);
            c.drawCircle(x - H * 0.008f, y - H * 0.008f, H * 0.005f, p);
            c.drawCircle(x + H * 0.008f, y - H * 0.008f, H * 0.005f, p);
            c.restore();
        }
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            float x = pl.x, y = top[s] ? floorY[FLOORS - 1] : floorY[floor[s]] - climbY[s] - z[s];
            Badge.ring(c, s, x, floorY[top[s] ? FLOORS - 1 : floor[s]] - climbY[s], H * 0.025f, t);
            p.setColor(dizzy[s] > 0 ? 0xFF94A3B8 : Party.COLORS[s]);
            c.drawRoundRect(x - H * 0.018f, y - H * 0.06f, x + H * 0.018f, y - H * 0.02f, H * 0.008f, H * 0.008f, p);
            p.setColor(0xFF1F2937);
            c.drawRect(x - H * 0.014f, y - H * 0.022f, x - H * 0.004f, y, p);
            c.drawRect(x + H * 0.004f, y - H * 0.022f, x + H * 0.014f, y, p);
            p.setColor(0xFFD6A77A);
            c.drawCircle(x, y - H * 0.075f, H * 0.016f, p);
            if (dizzy[s] > 0) {
                a.tx.setTextAlign(Paint.Align.CENTER);
                a.tx.setTextSize(H * 0.02f);
                a.tx.setColor(0xFFFDE047);
                c.drawText("★", x + (float) Math.cos(t * 8) * H * 0.02f, y - H * 0.1f, a.tx);
            }
            a.label(c, pl, x, y - H * 0.095f);
            a.hudBox(c, s, (pl.bot ? "TV  " : "") + pl.score + (top[s] ? "  ✓" : ""));
        }
        a.tx.setTextAlign(Paint.Align.RIGHT);
        a.tx.setTextSize(H * 0.026f);
        a.tx.setColor(0xFF0F172A);
        c.drawText("Tahap " + level + "/4", W * 0.99f, H * 0.1f, a.tx);
        if (levelEnd >= 0) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.07f);
            a.tx.setColor(0xFF7C2D12);
            c.drawText(level >= 4 ? "Tamat!" : "Tahap " + (level + 1) + "!", W / 2, H * 0.5f, a.tx);
        }
    }
}
