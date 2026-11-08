package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Sumo Gelembung: bouncy bubble players on a round island that shrinks. Roll into the others to knock them into the
 * sea; OK / A dashes. The last one on the island wins the round; first to three rounds wins. From the second round a
 * whirlpool in the middle spins everyone round, and from the third the island tilts.
 */
final class ModSumo implements Arked4.Mod {
    private final Arked4 a;
    private final float[] vx = new float[4], vy = new float[4], dash = new float[4], fallT = new float[4];
    private final int[] wins = new int[4];
    private final boolean[] out = new boolean[4];
    private float radius, roundEnd = -1, roundT;
    private int round;

    ModSumo(Arked4 a) { this.a = a; }

    @Override public void build() {
        round = 0;
        java.util.Arrays.fill(wins, 0);
        newRound();
    }

    private void newRound() {
        round++;
        roundEnd = -1;
        roundT = 0;
        radius = a.H * 0.42f;
        float cx = a.W / 2, cy = a.H * 0.55f;
        for (Arked4.P pl : a.ps) {
            int s = pl.seat;
            double an = s * Math.PI / 2 + Math.PI / 4;
            pl.x = cx + (float) Math.cos(an) * radius * 0.6f;
            pl.y = cy + (float) Math.sin(an) * radius * 0.6f;
            vx[s] = vy[s] = 0; dash[s] = 0; fallT[s] = 0;
            out[s] = !pl.on;
        }
    }

    @Override public void update(float dt) {
        float W = a.W, H = a.H, cx = W / 2, cy = H * 0.55f, r = H * 0.045f;
        if (roundEnd >= 0) {
            roundEnd += dt;
            if (roundEnd > 2.5f) {
                Arked4.P best = null;
                for (Arked4.P pl : a.ps) if (pl.on && (best == null || wins[pl.seat] > wins[best.seat])) best = pl;
                if (best != null && wins[best.seat] >= 3 || round >= 5) a.finish(a.who(best) + " juara Sumo Gelembung!");
                else newRound();
            }
            return;
        }
        roundT += dt;
        radius = Math.max(H * 0.15f, H * 0.42f - roundT * H * 0.008f);
        for (Arked4.P pl : a.ps) {
            int s = pl.seat;
            if (!pl.on || out[s]) continue;
            if (fallT[s] > 0) { fallT[s] += dt; if (fallT[s] > 0.6f) { out[s] = true; a.sound.sfx(GameMusic.SFX_WRONG); a.party.buzz(s, 300); } continue; }
            dash[s] -= dt;
            float mx, my;
            boolean go;
            if (pl.bot) {
                Arked4.P tgt = null;
                float bd = Float.MAX_VALUE;
                for (Arked4.P o : a.ps) if (o != pl && o.on && !out[o.seat] && fallT[o.seat] == 0) { float d = (float) Math.hypot(o.x - pl.x, o.y - pl.y); if (d < bd) { bd = d; tgt = o; } }
                float edge = (float) Math.hypot(pl.x - cx, pl.y - cy) / radius;
                if (edge > 0.75f || tgt == null) { mx = cx - pl.x; my = cy - pl.y; }
                else { mx = tgt.x - pl.x; my = tgt.y - pl.y; }
                float l = (float) Math.hypot(mx, my);
                if (l > 1) { mx /= l; my /= l; }
                go = tgt != null && bd < H * 0.15f && edge < 0.7f && a.rnd.nextFloat() < dt * 2;
            } else { mx = a.ix(pl); my = a.iy(pl); go = a.pressA(pl); }
            vx[s] += mx * H * 1.6f * dt;
            vy[s] += my * H * 1.6f * dt;
            if (go && dash[s] <= 0) { dash[s] = 1.2f; vx[s] += mx * H * 0.9f; vy[s] += my * H * 0.9f; a.sound.sfx(GameMusic.SFX_JUMP); }
            if (round >= 2) {   // the whirlpool turns everyone
                float dx = pl.x - cx, dy = pl.y - cy;
                vx[s] += -dy * 0.6f * dt;
                vy[s] += dx * 0.6f * dt;
            }
            if (round >= 3) vx[s] += (float) Math.sin(roundT * 0.8f) * H * 0.25f * dt;   // the island tilts
            vx[s] -= vx[s] * Math.min(1, dt * 1.8f);
            vy[s] -= vy[s] * Math.min(1, dt * 1.8f);
            pl.x += vx[s] * dt;
            pl.y += vy[s] * dt;
            if (Math.hypot(pl.x - cx, pl.y - cy) > radius + r * 0.3f) fallT[s] = 0.01f;
        }
        for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) {   // bouncy bumps
            Arked4.P p1 = a.ps[i], p2 = a.ps[j];
            if (!p1.on || !p2.on || out[i] || out[j] || fallT[i] > 0 || fallT[j] > 0) continue;
            float dx = p2.x - p1.x, dy = p2.y - p1.y, d = (float) Math.hypot(dx, dy);
            if (d > 0 && d < r * 2) {
                float nx = dx / d, ny = dy / d;
                float rel = (vx[i] - vx[j]) * nx + (vy[i] - vy[j]) * ny;
                if (rel > 0) {
                    float k = rel * 1.6f + H * 0.2f;
                    vx[i] -= nx * k; vy[i] -= ny * k;
                    vx[j] += nx * k; vy[j] += ny * k;
                    a.sound.sfx(GameMusic.SFX_COIN);
                }
                float push = (r * 2 - d) / 2;
                p1.x -= nx * push; p1.y -= ny * push; p2.x += nx * push; p2.y += ny * push;
            }
        }
        int alive = 0, last = -1, on = 0;
        for (Arked4.P pl : a.ps) if (pl.on) { on++; if (!out[pl.seat]) { alive++; last = pl.seat; } }
        if (alive <= (on > 1 ? 1 : 0)) {
            if (last >= 0) { wins[last]++; a.ps[last].score = wins[last]; }
            roundEnd = 0;
            a.sound.sfx(GameMusic.SFX_WIN);
        }
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H, cx = W / 2, cy = H * 0.55f, r = H * 0.045f;
        Paint p = a.p;
        p.setColor(0xFF0369A1);
        c.drawRect(0, 0, W, H, p);
        p.setColor(0x3338BDF8);
        for (int i = 0; i < 12; i++) c.drawCircle(((i * 173 + t * 30) % W), (i * 97) % H, H * 0.05f, p);
        p.setColor(0xFFFDE68A);
        c.drawCircle(cx, cy, radius + H * 0.015f, p);
        p.setColor(0xFF65A30D);
        c.drawCircle(cx, cy, radius, p);
        p.setColor(0x22FFFFFF);
        c.drawCircle(cx, cy, radius * 0.7f, p);
        if (round >= 2) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(H * 0.006f);
            p.setColor(0x6638BDF8);
            for (int k = 0; k < 3; k++) c.drawArc(cx - H * (0.04f + k * 0.03f), cy - H * (0.04f + k * 0.03f), cx + H * (0.04f + k * 0.03f), cy + H * (0.04f + k * 0.03f), t * 120 + k * 60, 240, false, p);
            p.setStyle(Paint.Style.FILL);
        }
        for (Arked4.P pl : a.ps) {
            int s = pl.seat;
            if (!pl.on || out[s]) continue;
            float sc = fallT[s] > 0 ? Math.max(0.1f, 1 - fallT[s] * 1.5f) : 1;
            p.setColor(0x44000000);
            c.drawCircle(pl.x + 6, pl.y + 8, r * sc, p);
            int col = Party.COLORS[s];
            p.setColor(Color.argb(220, Color.red(col), Color.green(col), Color.blue(col)));
            c.drawCircle(pl.x, pl.y, r * sc, p);
            p.setColor(0x99FFFFFF);
            c.drawCircle(pl.x - r * 0.35f * sc, pl.y - r * 0.35f * sc, r * 0.25f * sc, p);
            p.setColor(0xFF111827);
            c.drawCircle(pl.x - r * 0.25f * sc, pl.y, r * 0.1f * sc, p);
            c.drawCircle(pl.x + r * 0.25f * sc, pl.y, r * 0.1f * sc, p);
            if (dash[s] > 0.9f) { p.setColor(0x66FFFFFF); c.drawCircle(pl.x, pl.y, r * 1.4f, p); }
            if (fallT[s] == 0) a.label(c, pl, pl.x, pl.y - r);
        }
        for (Arked4.P pl : a.ps) if (pl.on) a.hudBox(c, pl.seat, (pl.bot ? "TV " : "") + wins[pl.seat] + " menang" + (out[pl.seat] ? "  ✖" : ""));
        a.tx.setTextAlign(Paint.Align.RIGHT);
        a.tx.setTextSize(H * 0.026f);
        a.tx.setColor(0xFFFFFFFF);
        c.drawText("Pusingan " + round + (round >= 3 ? " · pulau senget!" : round >= 2 ? " · pusaran air!" : ""), W * 0.99f, H * 0.1f, a.tx);
        if (roundEnd >= 0) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.07f);
            a.tx.setColor(0xFFFDE047);
            c.drawText("Pusingan " + round + " tamat!", W / 2, H * 0.3f, a.tx);
        }
    }
}
