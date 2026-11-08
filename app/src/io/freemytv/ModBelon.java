package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Perang Belon Air: a top-down water balloon fight for four. Run around the map (a park, a beach or a school yard,
 * chosen at random), throw balloons the way you face; three hits and you're soaked and sit out a moment. Pick-ups:
 * shoes (fast), a bucket (three balloons at once) and an umbrella (a shield). Most hits after 90 seconds wins.
 */
final class ModBelon implements Arked4.Mod {
    private static final String[] MAPS = {"Taman", "Pantai", "Sekolah"};
    private static final int[][] MAP_COL = {{0xFF4D7C0F, 0xFF65A30D, 0xFF166534}, {0xFFFDE68A, 0xFFFCD34D, 0xFF0EA5E9}, {0xFF9CA3AF, 0xFFD1D5DB, 0xFFB91C1C}};
    private static final float TIME = 90;

    private final Arked4 a;
    private final RectF[] blocks = new RectF[10];
    private int nBlocks, map;
    private final float[][] ball = new float[40][6];   // x, y, vx, vy, life, owner
    private final float[][] splash = new float[20][3]; // x, y, age
    private final float[] wet = new float[4], out = new float[4], fast = new float[4], triple = new float[4], shield = new float[4], aimX = new float[4], aimY = new float[4];
    private float itemX, itemY, itemT;
    private int item = -1;   // 0 shoes, 1 bucket, 2 umbrella

    ModBelon(Arked4 a) {
        this.a = a;
        for (int i = 0; i < blocks.length; i++) blocks[i] = new RectF();
    }

    @Override public void build() {
        float W = a.W, H = a.H;
        map = a.rnd.nextInt(MAPS.length);
        nBlocks = 0;
        // obstacles laid out symmetrically so nobody's corner is better
        float[][] layout = map == 0 ? new float[][]{{0.3f, 0.3f, 0.06f, 0.06f}, {0.64f, 0.3f, 0.06f, 0.06f}, {0.3f, 0.64f, 0.06f, 0.06f}, {0.64f, 0.64f, 0.06f, 0.06f}, {0.45f, 0.45f, 0.1f, 0.1f}}
                : map == 1 ? new float[][]{{0.2f, 0.45f, 0.12f, 0.05f}, {0.68f, 0.45f, 0.12f, 0.05f}, {0.46f, 0.25f, 0.08f, 0.05f}, {0.46f, 0.72f, 0.08f, 0.05f}}
                : new float[][]{{0.25f, 0.25f, 0.15f, 0.04f}, {0.6f, 0.25f, 0.15f, 0.04f}, {0.25f, 0.72f, 0.15f, 0.04f}, {0.6f, 0.72f, 0.15f, 0.04f}, {0.48f, 0.4f, 0.04f, 0.2f}};
        for (float[] b : layout) blocks[nBlocks++].set(W * b[0], H * (0.1f + b[1] * 0.9f), W * (b[0] + b[2]), H * (0.1f + (b[1] + b[3] * 1.6f) * 0.9f));
        float[][] corners = {{0.08f, 0.2f}, {0.92f, 0.2f}, {0.08f, 0.9f}, {0.92f, 0.9f}};
        for (Arked4.P pl : a.ps) {
            int i = pl.seat;
            pl.x = W * corners[i][0];
            pl.y = H * corners[i][1];
            wet[i] = out[i] = fast[i] = triple[i] = shield[i] = 0;
            aimX[i] = i % 2 == 0 ? 1 : -1;
            aimY[i] = 0;
            pl.cool = 0;
        }
        for (float[] b : ball) b[4] = 0;
        for (float[] s : splash) s[2] = 9;
        item = -1;
        itemT = 4;
    }

    private boolean blocked(float x, float y, float r) {
        if (x < r || x > a.W - r || y < a.H * 0.09f + r || y > a.H - r) return true;
        for (int i = 0; i < nBlocks; i++) {
            RectF b = blocks[i];
            if (x > b.left - r && x < b.right + r && y > b.top - r && y < b.bottom + r) return true;
        }
        return false;
    }

    @Override public void update(float dt) {
        float W = a.W, H = a.H, rad = H * 0.03f;
        for (Arked4.P pl : a.ps) {
            int i = pl.seat;
            if (!pl.on) continue;
            if (out[i] > 0) { out[i] -= dt; if (out[i] <= 0) { wet[i] = 0; pl.inv = 1.5f; } continue; }
            if (pl.inv > 0) pl.inv -= dt;
            fast[i] -= dt; triple[i] -= dt; shield[i] -= dt;
            pl.cool -= dt;
            float mx, my;
            boolean throwIt;
            if (pl.bot) {
                Arked4.P tgt = null;
                float bd = Float.MAX_VALUE;
                for (Arked4.P o : a.ps) {
                    if (o == pl || !o.on || out[o.seat] > 0) continue;
                    float d = (float) Math.hypot(o.x - pl.x, o.y - pl.y);
                    if (d < bd) { bd = d; tgt = o; }
                }
                if (item >= 0 && Math.hypot(itemX - pl.x, itemY - pl.y) < W * 0.25f) { mx = itemX - pl.x; my = itemY - pl.y; }
                else if (tgt != null) {
                    mx = tgt.x - pl.x; my = tgt.y - pl.y;
                    if (bd < W * 0.18f) { float ox = mx; mx = -my * 0.6f + ox * 0.2f; my = ox * 0.6f + my * 0.2f; }   // circle at a distance
                } else { mx = 0; my = 0; }
                float l = (float) Math.hypot(mx, my);
                if (l > 1) { mx /= l; my /= l; }
                throwIt = tgt != null && bd < W * 0.35f && a.rnd.nextFloat() < dt * 2.2f;
                if (throwIt && tgt != null) { aimX[i] = (tgt.x - pl.x) / bd; aimY[i] = (tgt.y - pl.y) / bd; }
            } else {
                mx = a.ix(pl);
                my = a.iy(pl);
                throwIt = a.pressA(pl);
            }
            float l = (float) Math.hypot(mx, my);
            if (l > 0.3f) {
                if (!pl.bot || !throwIt) { aimX[i] = mx / l; aimY[i] = my / l; }
                float sp = W * (fast[i] > 0 ? 0.32f : 0.22f) * Math.min(1, l);
                float nx = pl.x + mx / Math.max(1, l) * sp * dt, ny = pl.y + my / Math.max(1, l) * sp * dt;
                if (!blocked(nx, pl.y, rad)) pl.x = nx;
                if (!blocked(pl.x, ny, rad)) pl.y = ny;
            }
            if (throwIt && pl.cool <= 0) {
                pl.cool = 0.45f;
                throwBall(pl.x, pl.y, aimX[i], aimY[i], i);
                if (triple[i] > 0) {
                    float c = (float) Math.cos(0.3), s = (float) Math.sin(0.3);
                    throwBall(pl.x, pl.y, aimX[i] * c - aimY[i] * s, aimX[i] * s + aimY[i] * c, i);
                    throwBall(pl.x, pl.y, aimX[i] * c + aimY[i] * s, -aimX[i] * s + aimY[i] * c, i);
                }
            }
            if (item >= 0 && Math.hypot(itemX - pl.x, itemY - pl.y) < rad * 1.6f) {
                if (item == 0) fast[i] = 7; else if (item == 1) triple[i] = 7; else shield[i] = 6;
                item = -1;
                itemT = 6 + a.rnd.nextFloat() * 4;
                a.sound.sfx(GameMusic.SFX_OK);
            }
        }
        for (float[] b : ball) {
            if (b[4] <= 0) continue;
            b[4] -= dt;
            b[0] += b[2] * dt;
            b[1] += b[3] * dt;
            boolean pop = b[4] <= 0 || blocked(b[0], b[1], 2);
            for (Arked4.P pl : a.ps) {
                int i = pl.seat;
                if (pop || !pl.on || i == (int) b[5] || out[i] > 0 || pl.inv > 0) continue;
                if (Math.hypot(pl.x - b[0], pl.y - b[1]) < H * 0.04f) {
                    pop = true;
                    if (shield[i] > 0) continue;
                    wet[i]++;
                    a.ps[(int) b[5]].score++;
                    a.party.buzz(i, 120);
                    a.sound.sfx(GameMusic.SFX_COIN);
                    if (wet[i] >= 3) { out[i] = 3; a.sound.sfx(GameMusic.SFX_WRONG); }
                }
            }
            if (pop) {
                b[4] = 0;
                for (float[] s : splash) if (s[2] > 0.5f) { s[0] = b[0]; s[1] = b[1]; s[2] = 0; break; }
            }
        }
        for (float[] s : splash) s[2] += dt;
        if (item < 0) {
            itemT -= dt;
            if (itemT <= 0) {
                for (int tries = 0; tries < 20; tries++) {
                    itemX = W * (0.15f + a.rnd.nextFloat() * 0.7f);
                    itemY = H * (0.2f + a.rnd.nextFloat() * 0.7f);
                    if (!blocked(itemX, itemY, rad)) { item = a.rnd.nextInt(3); break; }
                }
                itemT = 5;
            }
        }
        if (a.time > TIME) {
            Arked4.P best = null;
            for (Arked4.P pl : a.ps) if (pl.on && (best == null || pl.score > best.score)) best = pl;
            a.finish(a.who(best) + " paling banyak membasahkan kawan: " + best.score + " kali! 💦");
        }
    }

    private void throwBall(float x, float y, float dx, float dy, int owner) {
        for (float[] b : ball) {
            if (b[4] > 0) continue;
            float sp = a.H * 0.9f;
            b[0] = x + dx * a.H * 0.04f; b[1] = y + dy * a.H * 0.04f; b[2] = dx * sp; b[3] = dy * sp; b[4] = 0.8f; b[5] = owner;
            a.sound.sfx(GameMusic.SFX_JUMP);
            return;
        }
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        int[] mc = MAP_COL[map];
        p.setColor(mc[0]);
        c.drawRect(0, 0, W, H, p);
        p.setColor(mc[1]);
        for (int i = 0; i < 12; i++) for (int j = 0; j < 7; j++) if ((i + j) % 2 == 0) c.drawRect(W * i / 12f, H * j / 7f, W * (i + 1) / 12f, H * (j + 1) / 7f, p);
        if (map == 1) {   // the sea along the top
            p.setColor(0xFF0EA5E9);
            c.drawRect(0, H * 0.08f, W, H * 0.13f, p);
        }
        for (int i = 0; i < nBlocks; i++) {
            RectF b = blocks[i];
            p.setColor(0x44000000);
            c.drawRoundRect(b.left + 6, b.top + 8, b.right + 6, b.bottom + 8, 10, 10, p);
            p.setColor(map == 0 ? 0xFF166534 : map == 1 ? 0xFFA16207 : mc[2]);
            c.drawRoundRect(b, 10, 10, p);
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(b.left + 6, b.top + 6, b.right - 6, b.top + (b.bottom - b.top) * 0.35f, 8, 8, p);
        }
        for (float[] s : splash) {
            if (s[2] > 0.5f) continue;
            p.setColor(Color.argb((int) (180 * (1 - s[2] * 2)), 125, 211, 252));
            c.drawCircle(s[0], s[1], H * (0.02f + s[2] * 0.08f), p);
        }
        if (item >= 0) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.05f);
            a.tx.setColor(0xFFFFFFFF);
            p.setColor(Color.argb((int) (150 + 100 * Math.sin(t * 6)), 255, 255, 255));
            c.drawCircle(itemX, itemY, H * 0.035f, p);
            c.drawText(item == 0 ? "👟" : item == 1 ? "×3" : "☂", itemX, itemY + H * 0.018f, a.tx);
        }
        for (float[] b : ball) {
            if (b[4] <= 0) continue;
            p.setColor(Party.COLORS[(int) b[5]]);
            c.drawCircle(b[0], b[1], H * 0.013f, p);
            p.setColor(0xAAFFFFFF);
            c.drawCircle(b[0] - H * 0.004f, b[1] - H * 0.004f, H * 0.004f, p);
        }
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int i = pl.seat;
            if (pl.inv > 0 && ((int) (pl.inv * 10)) % 2 == 0) continue;
            float s = H * 0.03f;
            Badge.ring(c, i, pl.x, pl.y, s * 1.3f, t);
            if (shield[i] > 0) { p.setColor(0x5538BDF8); c.drawCircle(pl.x, pl.y, s * 1.8f, p); }
            p.setColor(out[i] > 0 ? 0xFF60A5FA : Party.COLORS[i]);
            c.drawCircle(pl.x, pl.y, s, p);
            p.setColor(0xFFFDE7C7);
            c.drawCircle(pl.x + aimX[i] * s * 0.5f, pl.y + aimY[i] * s * 0.5f, s * 0.55f, p);   // the head, turned the way they aim
            p.setColor(0xFF111827);
            c.drawCircle(pl.x + aimX[i] * s * 0.8f, pl.y + aimY[i] * s * 0.8f, s * 0.12f, p);
            for (int k = 0; k < (int) wet[i]; k++) {   // drops show how wet
                p.setColor(0xFF38BDF8);
                c.drawCircle(pl.x - s + k * s, pl.y + s * 1.4f, s * 0.18f, p);
            }
            if (out[i] > 0) {
                a.tx.setTextAlign(Paint.Align.CENTER);
                a.tx.setTextSize(H * 0.025f);
                a.tx.setColor(0xFFFFFFFF);
                c.drawText("BASAH LENCUN!", pl.x, pl.y - s * 1.6f, a.tx);
            } else a.label(c, pl, pl.x, pl.y - s * 1.2f);
            a.hudBox(c, i, (pl.bot ? "TV  " : "") + pl.score + " kena");
        }
        long left = Math.max(0, (long) (TIME - a.time));
        a.tx.setTextAlign(Paint.Align.RIGHT);
        a.tx.setTextSize(H * 0.026f);
        a.tx.setColor(0xFFFFFFFF);
        c.drawText(MAPS[map] + "  " + left + "s", W * 0.99f, H * 0.045f, a.tx);
    }
}
