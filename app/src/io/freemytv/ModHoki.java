package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Hoki Udara 4 Penjuru: air hockey with a goal on every side. Each player guards one side (P1 left, P2 right, P3 top,
 * P4 bottom) and moves along it; empty sides are guarded by the TV, or walled off if nobody plays there in a
 * two-player game. Let a puck into your goal and you lose a life; three lives each. After 20 seconds a second puck
 * joins in, and the pucks speed up as the game goes on. Last one standing wins.
 */
final class ModHoki implements Arked4.Mod {
    private final Arked4 a;
    private final float[][] puck = new float[2][5];   // x, y, vx, vy, alive
    private final float[] pos = new float[4];          // along each side, 0..1
    private final boolean[] wall = new boolean[4];
    private float x0, y0, x1, y1, secondT;

    ModHoki(Arked4 a) { this.a = a; }

    @Override public void build() {
        float W = a.W, H = a.H, size = H * 0.84f;
        x0 = (W - size) / 2; x1 = x0 + size; y0 = H * 0.13f; y1 = y0 + size;
        for (Arked4.P pl : a.ps) { pl.lives = 3; pos[pl.seat] = 0.5f; wall[pl.seat] = !pl.on; }
        puck[1][4] = 0;
        serve(puck[0]);
        secondT = 20;
    }

    private void serve(float[] pk) {
        double an = a.rnd.nextDouble() * Math.PI * 2;
        float sp = a.H * 0.45f;
        pk[0] = (x0 + x1) / 2; pk[1] = (y0 + y1) / 2; pk[2] = (float) Math.cos(an) * sp; pk[3] = (float) Math.sin(an) * sp; pk[4] = 1;
    }

    @Override public void update(float dt) {
        float H = a.H, size = x1 - x0, pr = H * 0.018f, padHalf = size * 0.1f;
        secondT -= dt;
        if (secondT <= 0 && puck[1][4] == 0) { serve(puck[1]); a.sound.sfx(GameMusic.SFX_OK); }
        for (Arked4.P pl : a.ps) {
            int s = pl.seat;
            if (!pl.on || pl.lives <= 0) continue;
            float move;
            if (pl.bot) {   // the TV follows the nearest puck coming its way
                float best = Float.MAX_VALUE, target = 0.5f;
                for (float[] pk : puck) {
                    if (pk[4] == 0) continue;
                    float along = s < 2 ? (pk[1] - y0) / size : (pk[0] - x0) / size;
                    float dist = s == 0 ? pk[0] - x0 : s == 1 ? x1 - pk[0] : s == 2 ? pk[1] - y0 : y1 - pk[1];
                    if (dist < best) { best = dist; target = along; }
                }
                move = Math.max(-1, Math.min(1, (target - pos[s]) * 6)) * 0.8f;
            } else move = s < 2 ? a.iy(pl) : a.ix(pl);
            if (Math.abs(move) > 0.15f) pos[s] = Math.max(0.1f, Math.min(0.9f, pos[s] + move * 1.3f * dt));
        }
        for (float[] pk : puck) {
            if (pk[4] == 0) continue;
            float boost = 1 + a.time * 0.004f;
            pk[0] += pk[2] * dt * boost;
            pk[1] += pk[3] * dt * boost;
            for (int side = 0; side < 4; side++) {
                Arked4.P pl = a.ps[side];
                boolean alive = pl.on && pl.lives > 0;
                float edge = side == 0 ? pk[0] - pr - x0 : side == 1 ? x1 - pk[0] - pr : side == 2 ? pk[1] - pr - y0 : y1 - pk[1] - pr;
                if (edge > 0) continue;
                float along = side < 2 ? pk[1] : pk[0], padC = (side < 2 ? y0 : x0) + pos[side] * size;
                boolean saved = !alive || wall[side] || Math.abs(along - padC) < padHalf;
                if (saved) {   // bounces off the paddle or the wall, with a little spin from where it hit
                    if (side < 2) { pk[2] = -pk[2]; pk[0] = side == 0 ? x0 + pr : x1 - pr; if (alive && !wall[side]) pk[3] += (along - padC) / padHalf * H * 0.2f; }
                    else { pk[3] = -pk[3]; pk[1] = side == 2 ? y0 + pr : y1 - pr; if (alive && !wall[side]) pk[2] += (along - padC) / padHalf * H * 0.2f; }
                    if (alive && !wall[side]) a.sound.sfx(GameMusic.SFX_TICK);
                } else {   // a goal against that side
                    pl.lives--;
                    a.party.buzz(side, 250);
                    a.sound.sfx(GameMusic.SFX_WRONG);
                    for (Arked4.P o : a.ps) if (o.on && o.seat != side && o.lives > 0) o.score++;
                    serve(pk);
                    break;
                }
            }
        }
        int alive = 0, on = 0;
        Arked4.P last = null;
        for (Arked4.P pl : a.ps) if (pl.on) { on++; if (pl.lives > 0) { alive++; last = pl; } }
        if (alive <= (on > 1 ? 1 : 0)) a.finish(last == null ? "Tamat!" : a.who(last) + " penjaga gol terbaik! 🏒");
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H, size = x1 - x0, padHalf = size * 0.1f, th = H * 0.018f;
        Paint p = a.p;
        p.setColor(0xFF0F172A);
        c.drawRect(0, 0, W, H, p);
        p.setColor(0xFFE0F2FE);
        c.drawRoundRect(x0, y0, x1, y1, H * 0.03f, H * 0.03f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(H * 0.004f);
        p.setColor(0x6638BDF8);
        c.drawCircle((x0 + x1) / 2, (y0 + y1) / 2, size * 0.15f, p);
        c.drawLine(x0, (y0 + y1) / 2, x1, (y0 + y1) / 2, p);
        c.drawLine((x0 + x1) / 2, y0, (x0 + x1) / 2, y1, p);
        p.setStyle(Paint.Style.FILL);
        for (int side = 0; side < 4; side++) {
            Arked4.P pl = a.ps[side];
            boolean alive = pl.on && pl.lives > 0;
            p.setColor(wall[side] || !alive ? 0xFF475569 : Party.COLORS[side]);
            float c0 = (side < 2 ? y0 : x0) + pos[side] * size;
            if (wall[side] || !alive) {   // a closed side
                if (side == 0) c.drawRect(x0 - th, y0, x0, y1, p);
                else if (side == 1) c.drawRect(x1, y0, x1 + th, y1, p);
                else if (side == 2) c.drawRect(x0, y0 - th, x1, y0, p);
                else c.drawRect(x0, y1, x1, y1 + th, p);
                continue;
            }
            float px, py;
            if (side == 0) { c.drawRoundRect(x0 - th, c0 - padHalf, x0 + th * 0.5f, c0 + padHalf, th, th, p); px = x0 - th * 2; py = c0; }
            else if (side == 1) { c.drawRoundRect(x1 - th * 0.5f, c0 - padHalf, x1 + th, c0 + padHalf, th, th, p); px = x1 + th * 2; py = c0; }
            else if (side == 2) { c.drawRoundRect(c0 - padHalf, y0 - th, c0 + padHalf, y0 + th * 0.5f, th, th, p); px = c0; py = y0 - th; }
            else { c.drawRoundRect(c0 - padHalf, y1 - th * 0.5f, c0 + padHalf, y1 + th, th, th, p); px = c0; py = y1 + th * 3; }
            Badge.pin(c, side, pl.bot, side == 0 ? px - th : side == 1 ? px + th : px, py, H * 0.026f, t);
            a.hudBox(c, side, (pl.bot ? "TV " : "") + Arked4.hearts(pl.lives) + "  " + (side == 0 ? "kiri" : side == 1 ? "kanan" : side == 2 ? "atas" : "bawah"));
        }
        for (float[] pk : puck) {
            if (pk[4] == 0) continue;
            p.setColor(0x44000000);
            c.drawCircle(pk[0] + 3, pk[1] + 4, H * 0.018f, p);
            p.setColor(0xFF111827);
            c.drawCircle(pk[0], pk[1], H * 0.018f, p);
            p.setColor(0xFFF43F5E);
            c.drawCircle(pk[0], pk[1], H * 0.008f, p);
        }
    }
}
