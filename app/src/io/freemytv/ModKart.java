package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Kart Belon: a kart battle in an arena. Every kart carries three balloons; drive over ? boxes for an item and fire it
 * with OK / A: a water bomb flies straight ahead, a durian husk stays on the ground behind you. A hit pops one
 * balloon. The last kart with balloons wins the round; three rounds, a new arena each.
 */
final class ModKart implements Arked4.Mod {
    private static final String[] ARENA = {"Dataran Merdeka", "Pasar Malam", "Pelabuhan"};
    private static final int[] ARENA_COL = {0xFF64748B, 0xFF7C2D12, 0xFF1E3A8A};
    private final Arked4 a;
    private final int[] balloons = new int[4], item = new int[4], wins = new int[4];   // item: 0 none, 1 water bomb, 2 husk
    private final float[] spd = new float[4], botT = new float[4];
    private final float[][] box = new float[6][3];     // x, y, respawn
    private final float[][] shot = new float[16][6];   // x, y, vx, vy, kind (1 bomb, 2 husk), owner; alive when kind > 0
    private final float[][] block = new float[4][4];
    private int arena, round;
    private float roundEnd = -1;

    ModKart(Arked4 a) { this.a = a; }

    @Override public void build() {
        round = 0;
        java.util.Arrays.fill(wins, 0);
        newRound();
    }

    private void newRound() {
        round++;
        roundEnd = -1;
        arena = (round - 1 + a.rnd.nextInt(3)) % ARENA.length;
        float W = a.W, H = a.H;
        for (int i = 0; i < 4; i++) {   // four blocks to drive round
            float bx = W * (i % 2 == 0 ? 0.3f : 0.62f), by = H * (i < 2 ? 0.32f : 0.66f);
            block[i][0] = bx; block[i][1] = by; block[i][2] = bx + W * 0.08f; block[i][3] = by + H * 0.1f;
        }
        for (int i = 0; i < box.length; i++) { box[i][0] = W * (0.15f + (i % 3) * 0.35f); box[i][1] = H * (i < 3 ? 0.22f : 0.88f); box[i][2] = 0; }
        float[][] start = {{0.1f, 0.2f}, {0.9f, 0.2f}, {0.1f, 0.9f}, {0.9f, 0.9f}};
        for (Arked4.P pl : a.ps) {
            int s = pl.seat;
            pl.x = W * start[s][0]; pl.y = H * start[s][1];
            pl.ang = (float) Math.atan2(H * 0.55f - pl.y, W / 2 - pl.x);
            balloons[s] = pl.on ? 3 : 0;
            item[s] = 0; spd[s] = 0; pl.inv = 0;
        }
        for (float[] sh : shot) sh[4] = 0;
    }

    private boolean inBlock(float x, float y) {
        for (float[] b : block) if (x > b[0] && x < b[2] && y > b[1] && y < b[3]) return true;
        return x < a.W * 0.02f || x > a.W * 0.98f || y < a.H * 0.1f || y > a.H * 0.98f;
    }

    @Override public void update(float dt) {
        float W = a.W, H = a.H;
        if (roundEnd >= 0) {
            roundEnd += dt;
            if (roundEnd > 2.5f) {
                if (round >= 3) {
                    Arked4.P best = null;
                    for (Arked4.P pl : a.ps) if (pl.on && (best == null || wins[pl.seat] > wins[best.seat])) best = pl;
                    a.finish(a.who(best) + " juara Kart Belon: " + wins[best.seat] + " pusingan! 🎈");
                } else newRound();
            }
            return;
        }
        for (float[] b : box) if (b[2] > 0) b[2] -= dt;
        for (Arked4.P pl : a.ps) {
            int s = pl.seat;
            if (!pl.on || balloons[s] <= 0) continue;
            if (pl.inv > 0) pl.inv -= dt;
            float steer;
            boolean fire;
            if (pl.bot) {
                Arked4.P tgt = null;
                float bd = Float.MAX_VALUE;
                for (Arked4.P o : a.ps) if (o != pl && o.on && balloons[o.seat] > 0) { float d = (float) Math.hypot(o.x - pl.x, o.y - pl.y); if (d < bd) { bd = d; tgt = o; } }
                float tx = tgt != null ? tgt.x : W / 2, ty = tgt != null ? tgt.y : H / 2;
                if (item[s] == 0) for (float[] b : box) if (b[2] <= 0) { tx = b[0]; ty = b[1]; break; }
                float want = (float) Math.atan2(ty - pl.y, tx - pl.x), d = want - pl.ang;
                while (d > Math.PI) d -= 2 * Math.PI;
                while (d < -Math.PI) d += 2 * Math.PI;
                steer = Math.max(-1, Math.min(1, d * 2));
                botT[s] -= dt;
                fire = item[s] > 0 && Math.abs(d) < 0.25f && botT[s] <= 0;
                if (fire) botT[s] = 1;
            } else { steer = a.ix(pl); fire = a.pressA(pl); }
            spd[s] += (W * 0.24f - spd[s]) * Math.min(1, dt * 1.5f);
            pl.ang += steer * 3f * dt;
            float nx = pl.x + (float) Math.cos(pl.ang) * spd[s] * dt, ny = pl.y + (float) Math.sin(pl.ang) * spd[s] * dt;
            if (inBlock(nx, ny)) { spd[s] *= -0.4f; pl.ang += 0.5f; } else { pl.x = nx; pl.y = ny; }
            for (float[] b : box) {
                if (b[2] > 0 || item[s] > 0 || Math.hypot(b[0] - pl.x, b[1] - pl.y) > H * 0.045f) continue;
                item[s] = 1 + a.rnd.nextInt(2);
                b[2] = 4;
                a.sound.sfx(GameMusic.SFX_COIN);
            }
            if (fire && item[s] > 0) {
                for (float[] sh : shot) {
                    if (sh[4] > 0) continue;
                    boolean bombIt = item[s] == 1;
                    float dir = bombIt ? 1 : -1;
                    sh[0] = pl.x + (float) Math.cos(pl.ang) * H * 0.05f * dir;
                    sh[1] = pl.y + (float) Math.sin(pl.ang) * H * 0.05f * dir;
                    sh[2] = bombIt ? (float) Math.cos(pl.ang) * W * 0.55f : 0;
                    sh[3] = bombIt ? (float) Math.sin(pl.ang) * W * 0.55f : 0;
                    sh[4] = item[s]; sh[5] = s;
                    break;
                }
                item[s] = 0;
                a.sound.sfx(GameMusic.SFX_JUMP);
            }
        }
        for (float[] sh : shot) {
            if (sh[4] <= 0) continue;
            sh[0] += sh[2] * dt;
            sh[1] += sh[3] * dt;
            if (sh[4] == 1 && inBlock(sh[0], sh[1])) { sh[4] = 0; continue; }
            for (Arked4.P pl : a.ps) {
                int s = pl.seat;
                if (!pl.on || balloons[s] <= 0 || pl.inv > 0 || (s == (int) sh[5] && sh[4] == 1)) continue;
                if (Math.hypot(pl.x - sh[0], pl.y - sh[1]) < a.H * 0.045f) {
                    sh[4] = 0;
                    balloons[s]--;
                    pl.inv = 1.5f;
                    spd[s] = 0;
                    a.ps[(int) sh[5]].score++;
                    a.party.buzz(s, 200);
                    a.sound.sfx(GameMusic.SFX_WRONG);
                    break;
                }
            }
        }
        int alive = 0, last = -1, on = 0;
        for (Arked4.P pl : a.ps) if (pl.on) { on++; if (balloons[pl.seat] > 0) { alive++; last = pl.seat; } }
        if (alive <= (on > 1 ? 1 : 0)) {
            if (last >= 0) wins[last]++;
            roundEnd = 0;
            a.sound.sfx(GameMusic.SFX_WIN);
        }
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        p.setColor(ARENA_COL[arena]);
        c.drawRect(0, 0, W, H, p);
        p.setColor(0x22FFFFFF);
        for (int i = 0; i < 20; i++) c.drawRect(W * i / 20f, 0, W * i / 20f + 2, H, p);
        p.setColor(0xFFDC2626);
        c.drawRect(0, H * 0.09f, W, H * 0.1f, p);
        for (float[] b : block) {
            p.setColor(0xFF1F2937);
            c.drawRoundRect(b[0], b[1], b[2], b[3], H * 0.015f, H * 0.015f, p);
            p.setColor(0xFFFACC15);
            c.drawRect(b[0], b[1], b[2], b[1] + H * 0.01f, p);
        }
        for (float[] b : box) {
            if (b[2] > 0) continue;
            p.setColor(Color.argb(255, 168, 85, 247));
            c.save();
            c.rotate(t * 60, b[0], b[1]);
            c.drawRoundRect(b[0] - H * 0.025f, b[1] - H * 0.025f, b[0] + H * 0.025f, b[1] + H * 0.025f, H * 0.006f, H * 0.006f, p);
            c.restore();
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.035f);
            a.tx.setColor(0xFFFFFFFF);
            c.drawText("?", b[0], b[1] + H * 0.012f, a.tx);
        }
        for (float[] sh : shot) {
            if (sh[4] <= 0) continue;
            if (sh[4] == 1) { p.setColor(0xFF38BDF8); c.drawCircle(sh[0], sh[1], H * 0.015f, p); }
            else { p.setColor(0xFF65A30D); c.drawCircle(sh[0], sh[1], H * 0.02f, p); p.setColor(0xFF3F6212); c.drawCircle(sh[0], sh[1], H * 0.01f, p); }
        }
        for (Arked4.P pl : a.ps) {
            int s = pl.seat;
            if (!pl.on || balloons[s] <= 0) continue;
            if (pl.inv > 0 && ((int) (pl.inv * 10)) % 2 == 0) continue;
            float L = H * 0.05f, w = H * 0.032f;
            Badge.ring(c, s, pl.x, pl.y, H * 0.035f, t);
            c.save();
            c.translate(pl.x, pl.y);
            c.rotate((float) Math.toDegrees(pl.ang));
            p.setColor(0xFF111111);
            c.drawRect(-L * 0.4f, -w * 0.62f, -L * 0.15f, w * 0.62f, p);
            c.drawRect(L * 0.15f, -w * 0.62f, L * 0.4f, w * 0.62f, p);
            p.setColor(Party.COLORS[s]);
            c.drawRoundRect(-L / 2, -w / 2, L / 2, w / 2, w * 0.3f, w * 0.3f, p);
            p.setColor(0xFF1F2937);
            c.drawCircle(-L * 0.05f, 0, w * 0.3f, p);
            c.restore();
            for (int k = 0; k < balloons[s]; k++) {   // the balloons on strings
                float bx = pl.x - H * 0.025f + k * H * 0.025f, by = pl.y - H * 0.065f + (float) Math.sin(t * 3 + k) * H * 0.004f;
                p.setColor(0x88FFFFFF);
                c.drawLine(pl.x, pl.y, bx, by, p);
                p.setColor(Party.COLORS[s]);
                c.drawOval(bx - H * 0.012f, by - H * 0.017f, bx + H * 0.012f, by + H * 0.012f, p);
            }
            a.label(c, pl, pl.x + H * 0.045f, pl.y - H * 0.03f);
        }
        for (Arked4.P pl : a.ps) if (pl.on) a.hudBox(c, pl.seat, (pl.bot ? "TV " : "") + wins[pl.seat] + " menang  " + (item[pl.seat] == 1 ? "💧" : item[pl.seat] == 2 ? "◆" : ""));
        a.tx.setTextAlign(Paint.Align.RIGHT);
        a.tx.setTextSize(H * 0.024f);
        a.tx.setColor(0xFFFFFFFF);
        c.drawText(ARENA[arena] + "  ·  pusingan " + round + "/3", W * 0.99f, H * 0.135f, a.tx);
        if (roundEnd >= 0) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.07f);
            a.tx.setColor(0xFFFDE047);
            c.drawText("Pusingan " + round + " tamat!", W / 2, H * 0.55f, a.tx);
        }
    }
}
