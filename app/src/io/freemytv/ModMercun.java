package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Mercun Raya: a maze battle for four. Each player drops harmless festive crackers (OK / A) that pop in a cross after
 * two seconds, breaking the kampung's wooden crates and sending anyone caught in the sparks out of the round. Crates
 * hide power-ups: a longer burst (✦), more crackers at once (+), faster feet (»). Best of three rounds, each on a new
 * layout; the crates get denser each round.
 */
final class ModMercun implements Arked4.Mod {
    private static final int GC = 15, GR = 11;
    private final Arked4 a;
    private final byte[][] g = new byte[GR][GC];      // 0 floor, 1 pillar, 2 crate, 3..5 power-up
    private final float[][] bomb = new float[16][5];  // x, y, timer, owner, alive
    private final float[][] fire = new float[GR][GC]; // seconds of sparks left
    private final int[] range = new int[4], max = new int[4], wins = new int[4];
    private final float[] speed = new float[4], botT = new float[4];
    private final boolean[] out = new boolean[4];
    private int round;
    private float roundEnd = -1, cs, ox, oy;

    ModMercun(Arked4 a) { this.a = a; }

    @Override public void build() {
        round = 0;
        java.util.Arrays.fill(wins, 0);
        newRound();
    }

    private void newRound() {
        round++;
        roundEnd = -1;
        for (int y = 0; y < GR; y++) for (int x = 0; x < GC; x++) {
            boolean edge = x == 0 || y == 0 || x == GC - 1 || y == GR - 1;
            g[y][x] = (byte) (edge || (x % 2 == 0 && y % 2 == 0) ? 1 : a.rnd.nextFloat() < 0.45f + round * 0.08f ? 2 : 0);
            fire[y][x] = 0;
        }
        int[][] corners = {{1, 1}, {GC - 2, 1}, {1, GR - 2}, {GC - 2, GR - 2}};
        for (Arked4.P pl : a.ps) {
            int s = pl.seat, cx = corners[s][0], cy = corners[s][1];
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) if (g[cy + dy][cx + dx] == 2) g[cy + dy][cx + dx] = 0;   // room to start
            pl.x = cx; pl.y = cy;
            range[s] = 2; max[s] = 1; speed[s] = 3.5f;
            out[s] = !pl.on;
        }
        for (float[] b : bomb) b[4] = 0;
    }

    private boolean solid(int x, int y) {
        if (x < 0 || y < 0 || x >= GC || y >= GR) return true;
        if (g[y][x] == 1 || g[y][x] == 2) return true;
        for (float[] b : bomb) if (b[4] > 0 && (int) b[0] == x && (int) b[1] == y) return true;
        return false;
    }

    @Override public void update(float dt) {
        if (roundEnd >= 0) {
            roundEnd += dt;
            if (roundEnd > 2.5f) {
                if (round >= 3) {
                    Arked4.P best = null;
                    for (Arked4.P pl : a.ps) if (pl.on && (best == null || wins[pl.seat] > wins[best.seat])) best = pl;
                    a.finish(a.who(best) + " juara Mercun Raya: " + wins[best.seat] + " pusingan! 🎆");
                } else newRound();
            }
            return;
        }
        for (int y = 0; y < GR; y++) for (int x = 0; x < GC; x++) if (fire[y][x] > 0) fire[y][x] -= dt;
        for (Arked4.P pl : a.ps) {
            int s = pl.seat;
            if (!pl.on || out[s]) continue;
            float mx, my;
            boolean drop;
            if (pl.bot) {
                float[] m = botMove(pl, dt);
                mx = m[0]; my = m[1]; drop = m[2] > 0;
            } else { mx = a.ix(pl); my = a.iy(pl); drop = a.pressA(pl); }
            // grid movement that slides round corners
            float sp = speed[s] * dt;
            if (Math.abs(mx) > 0.4f || Math.abs(my) > 0.4f) {
                boolean horiz = Math.abs(mx) > Math.abs(my);
                int dx = horiz ? (mx > 0 ? 1 : -1) : 0, dy = horiz ? 0 : (my > 0 ? 1 : -1);
                int cx = Math.round(pl.x), cy = Math.round(pl.y);
                if (horiz) {
                    if (Math.abs(pl.y - cy) > 0.05f) pl.y += Math.signum(cy - pl.y) * Math.min(sp, Math.abs(cy - pl.y));
                    else if (!solid(cx + dx, cy) || (dx > 0 ? pl.x < cx : pl.x > cx)) pl.x += dx * sp;
                    if (solid(cx + dx, cy) && (dx > 0 ? pl.x > cx : pl.x < cx) && !onBombAt(cx + dx, cy)) pl.x = cx;
                } else {
                    if (Math.abs(pl.x - cx) > 0.05f) pl.x += Math.signum(cx - pl.x) * Math.min(sp, Math.abs(cx - pl.x));
                    else if (!solid(cx, cy + dy) || (dy > 0 ? pl.y < cy : pl.y > cy)) pl.y += dy * sp;
                    if (solid(cx, cy + dy) && (dy > 0 ? pl.y > cy : pl.y < cy)) pl.y = cy;
                }
            }
            int cx = Math.round(pl.x), cy = Math.round(pl.y);
            if (drop) {
                int mine = 0;
                for (float[] b : bomb) if (b[4] > 0 && (int) b[3] == s) mine++;
                if (mine < max[s] && !solid(cx, cy)) {
                    for (float[] b : bomb) if (b[4] <= 0) { b[0] = cx; b[1] = cy; b[2] = 2; b[3] = s; b[4] = 1; a.sound.sfx(GameMusic.SFX_TICK); break; }
                }
            }
            byte k = g[cy][cx];
            if (k >= 3) {
                if (k == 3) range[s]++; else if (k == 4) max[s]++; else speed[s] = Math.min(5.5f, speed[s] + 0.6f);
                g[cy][cx] = 0;
                a.sound.sfx(GameMusic.SFX_OK);
            }
            if (fire[cy][cx] > 0) {
                out[s] = true;
                a.party.buzz(s, 300);
                a.sound.sfx(GameMusic.SFX_WRONG);
            }
        }
        for (float[] b : bomb) {
            if (b[4] <= 0) continue;
            b[2] -= dt;
            if (b[2] <= 0 || fire[(int) b[1]][(int) b[0]] > 0) pop(b);
        }
        int alive = 0, last = -1, on = 0;
        for (Arked4.P pl : a.ps) if (pl.on) { on++; if (!out[pl.seat]) { alive++; last = pl.seat; } }
        if (alive <= (on > 1 ? 1 : 0)) {
            if (last >= 0) { wins[last]++; a.ps[last].score = wins[last]; }
            roundEnd = 0;
            a.sound.sfx(GameMusic.SFX_WIN);
        }
    }

    private boolean onBombAt(int x, int y) { return false; }

    private void pop(float[] b) {
        b[4] = 0;
        int x0 = (int) b[0], y0 = (int) b[1], r = range[(int) b[3]];
        fire[y0][x0] = 0.5f;
        int[] DX = {1, -1, 0, 0}, DY = {0, 0, 1, -1};
        for (int d = 0; d < 4; d++) {
            for (int i = 1; i <= r; i++) {
                int x = x0 + DX[d] * i, y = y0 + DY[d] * i;
                if (g[y][x] == 1) break;
                fire[y][x] = 0.5f;
                if (g[y][x] == 2) {   // a crate breaks; sometimes there's something inside
                    float roll = a.rnd.nextFloat();
                    g[y][x] = (byte) (roll < 0.12f ? 3 : roll < 0.22f ? 4 : roll < 0.3f ? 5 : 0);
                    break;
                }
            }
        }
        a.sound.sfx(GameMusic.SFX_COIN);
    }

    private final float[] bm = new float[3];

    /** The TV: wanders, drops a cracker next to crates or players, and steps away from sparks to come. */
    private float[] botMove(Arked4.P pl, float dt) {
        int s = pl.seat, cx = Math.round(pl.x), cy = Math.round(pl.y);
        bm[2] = 0;
        boolean danger = false;
        for (float[] b : bomb) if (b[4] > 0 && ((int) b[0] == cx && Math.abs((int) b[1] - cy) <= range[(int) b[3]] || (int) b[1] == cy && Math.abs((int) b[0] - cx) <= range[(int) b[3]])) danger = true;
        botT[s] -= dt;
        if (danger || botT[s] <= 0) {
            int[] DX = {1, -1, 0, 0}, DY = {0, 0, 1, -1};
            int best = -1;
            for (int tries = 0; tries < 8; tries++) {
                int d = a.rnd.nextInt(4);
                if (!solid(cx + DX[d], cy + DY[d]) && fire[cy + DY[d]][cx + DX[d]] <= 0) { best = d; break; }
            }
            bm[0] = best < 0 ? 0 : DX[best];
            bm[1] = best < 0 ? 0 : DY[best];
            botT[s] = 0.4f + a.rnd.nextFloat() * 0.5f;
            if (!danger) {
                boolean target = false;
                for (int d = 0; d < 4; d++) if (g[cy + DY[d]][cx + DX[d]] == 2) target = true;
                for (Arked4.P o : a.ps) if (o != pl && o.on && !out[o.seat] && Math.abs(o.x - pl.x) + Math.abs(o.y - pl.y) < 2) target = true;
                if (target && a.rnd.nextFloat() < 0.6f) bm[2] = 1;
            }
        }
        return bm;
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        cs = Math.min(W * 0.96f / GC, H * 0.86f / GR);
        ox = (W - cs * GC) / 2;
        oy = H * 0.1f + (H * 0.9f - cs * GR) / 2;
        p.setColor(0xFF14532D);
        c.drawRect(0, 0, W, H, p);
        for (int y = 0; y < GR; y++) for (int x = 0; x < GC; x++) {
            float X = ox + x * cs, Y = oy + y * cs;
            byte k = g[y][x];
            p.setColor((x + y) % 2 == 0 ? 0xFF65A30D : 0xFF4D7C0F);
            c.drawRect(X, Y, X + cs + 1, Y + cs + 1, p);
            if (k == 1) {
                p.setColor(0xFF78716C);
                c.drawRoundRect(X + 2, Y + 2, X + cs - 2, Y + cs - 2, cs * 0.15f, cs * 0.15f, p);
                p.setColor(0x33FFFFFF);
                c.drawRect(X + cs * 0.15f, Y + cs * 0.15f, X + cs * 0.85f, Y + cs * 0.3f, p);
            } else if (k == 2) {
                p.setColor(0xFFA16207);
                c.drawRect(X + cs * 0.08f, Y + cs * 0.08f, X + cs * 0.92f, Y + cs * 0.92f, p);
                p.setColor(0xFF78350F);
                c.drawLine(X + cs * 0.1f, Y + cs * 0.1f, X + cs * 0.9f, Y + cs * 0.9f, p);
                c.drawLine(X + cs * 0.9f, Y + cs * 0.1f, X + cs * 0.1f, Y + cs * 0.9f, p);
            } else if (k >= 3) {
                p.setColor(Color.argb((int) (180 + 70 * Math.sin(t * 6)), 255, 255, 255));
                c.drawCircle(X + cs / 2, Y + cs / 2, cs * 0.35f, p);
                a.tx.setTextAlign(Paint.Align.CENTER);
                a.tx.setTextSize(cs * 0.5f);
                a.tx.setColor(0xFF7C2D12);
                c.drawText(k == 3 ? "✦" : k == 4 ? "+" : "»", X + cs / 2, Y + cs * 0.68f, a.tx);
            }
            if (fire[y][x] > 0) {
                p.setColor(Color.argb((int) (255 * Math.min(1, fire[y][x] * 3)), 250, 204, 21));
                c.drawCircle(X + cs / 2, Y + cs / 2, cs * 0.45f, p);
                p.setColor(0xFFF472B6);
                c.drawCircle(X + cs / 2, Y + cs / 2, cs * 0.22f, p);
            }
        }
        for (float[] b : bomb) {
            if (b[4] <= 0) continue;
            float X = ox + (b[0] + 0.5f) * cs, Y = oy + (b[1] + 0.5f) * cs, pulse = 1 + 0.1f * (float) Math.sin(t * (14 - b[2] * 4));
            p.setColor(0xFFDC2626);   // a red cracker with a lit fuse
            c.drawRoundRect(X - cs * 0.15f * pulse, Y - cs * 0.3f * pulse, X + cs * 0.15f * pulse, Y + cs * 0.3f * pulse, cs * 0.06f, cs * 0.06f, p);
            p.setColor(0xFFFDE047);
            c.drawRect(X - cs * 0.15f, Y - cs * 0.05f, X + cs * 0.15f, Y + cs * 0.05f, p);
            p.setColor(Party.COLORS[(int) b[3]]);
            c.drawCircle(X, Y - cs * 0.38f, cs * 0.08f + (float) Math.sin(t * 30) * cs * 0.02f, p);
        }
        for (Arked4.P pl : a.ps) {
            if (!pl.on || out[pl.seat]) continue;
            float X = ox + (pl.x + 0.5f) * cs, Y = oy + (pl.y + 0.5f) * cs;
            Badge.ring(c, pl.seat, X, Y, cs * 0.42f, t);
            p.setColor(Party.COLORS[pl.seat]);
            c.drawCircle(X, Y + cs * 0.05f, cs * 0.3f, p);
            p.setColor(0xFFFDE7C7);
            c.drawCircle(X, Y - cs * 0.15f, cs * 0.2f, p);
            p.setColor(0xFF111827);   // a songkok
            c.drawRect(X - cs * 0.18f, Y - cs * 0.4f, X + cs * 0.18f, Y - cs * 0.26f, p);
            a.label(c, pl, X, Y - cs * 0.42f);
        }
        for (Arked4.P pl : a.ps) if (pl.on) a.hudBox(c, pl.seat, (pl.bot ? "TV  " : "") + wins[pl.seat] + " menang" + (out[pl.seat] ? "  ✖" : ""));
        a.tx.setTextAlign(Paint.Align.RIGHT);
        a.tx.setTextSize(H * 0.026f);
        a.tx.setColor(0xFFFFFFFF);
        c.drawText("Pusingan " + round + "/3", W * 0.99f, H * 0.1f, a.tx);
        if (roundEnd >= 0) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.07f);
            a.tx.setColor(0xFFFDE047);
            int last = -1;
            for (Arked4.P pl : a.ps) if (pl.on && !out[pl.seat]) last = pl.seat;
            c.drawText(last >= 0 ? "Pemain " + (last + 1) + " menang pusingan!" : "Seri!", W / 2, H * 0.5f, a.tx);
        }
    }
}
