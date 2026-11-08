package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

/**
 * Juruterbang Wau: a co-op side-scrolling shooter. The players fly kites (wau) across the sky and shoot at storm
 * clouds and angry birds coming from the right; every fourth wave the Storm King comes. The sky turns from morning to
 * night as the waves go by; a gust of wind (◆) makes a kite fire three ways for a while.
 */
final class ModWau implements Arked4.Mod {
    private static final int WAVES = 8;
    private static final int[][] SKY = {{0xFF38BDF8, 0xFFE0F2FE}, {0xFF0EA5E9, 0xFFFDE68A}, {0xFFF97316, 0xFFFDBA74}, {0xFF1E1B4B, 0xFF6D28D9}};

    private final Arked4 a;
    private final float[][] en = new float[24][8];   // x, y, hp, kind (0 cloud, 1 bird, 2 king), t, alive, y0, max
    private final float[][] shot = new float[60][5]; // x, y, vx, vy, owner(+1)  alive when vx != 0
    private final float[][] bolt = new float[40][4]; // x, y, vx, vy (alive when vx != 0)
    private final float[] spread = new float[4], fire = new float[4];
    private final float[] gx = new float[1], gy = new float[1];
    private boolean gust;
    private int wave;
    private float banner, cloudOff;
    private Shader sky;
    private int skyFor = -1;

    ModWau(Arked4 a) { this.a = a; }

    @Override public void build() {
        int k = 0;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            pl.x = a.W * 0.12f;
            pl.y = a.H * (0.25f + k * 0.17f);
            pl.lives = 3;
            k++;
        }
        wave = 0;
        nextWave();
    }

    private void nextWave() {
        wave++;
        banner = 2;
        for (Arked4.P pl : a.ps) if (pl.on && pl.lives <= 0) { pl.lives = 1; pl.inv = 2; }
        if (wave % 4 == 0) { spawn(2, a.W * 1.1f, a.H * 0.5f, 60 + wave * 10); return; }
        int n = 6 + wave * 2;
        for (int i = 0; i < n && i < en.length; i++) {
            boolean bird = a.rnd.nextFloat() < 0.4f;
            spawn(bird ? 1 : 0, a.W * (1.05f + i * 0.12f), a.H * (0.15f + a.rnd.nextFloat() * 0.75f), bird ? 1 : 2 + wave / 3);
        }
    }

    private void spawn(int kind, float x, float y, float hp) {
        for (float[] e : en) {
            if (e[5] > 0) continue;
            e[0] = x; e[1] = e[6] = y; e[2] = e[7] = hp; e[3] = kind; e[4] = a.rnd.nextFloat() * 6; e[5] = 1;
            return;
        }
    }

    @Override public void update(float dt) {
        float W = a.W, H = a.H;
        if (banner > 0) banner -= dt;
        cloudOff += dt * W * 0.05f;
        for (Arked4.P pl : a.ps) {
            if (!pl.on || pl.lives <= 0) continue;
            int i = pl.seat;
            if (pl.inv > 0) pl.inv -= dt;
            spread[i] -= dt;
            pl.x = Math.max(W * 0.04f, Math.min(W * 0.6f, pl.x + a.ix(pl) * W * 0.35f * dt));
            pl.y = Math.max(H * 0.1f, Math.min(H * 0.95f, pl.y + a.iy(pl) * H * 0.6f * dt));
            fire[i] -= dt;
            if (fire[i] <= 0) {
                fire[i] = 0.22f;
                shoot(pl.x + H * 0.05f, pl.y, W, 0, i);
                if (spread[i] > 0) { shoot(pl.x, pl.y, W * 0.95f, -H * 0.4f, i); shoot(pl.x, pl.y, W * 0.95f, H * 0.4f, i); }
            }
            if (gust && Math.hypot(gx[0] - pl.x, gy[0] - pl.y) < H * 0.06f) { gust = false; spread[i] = 9; a.sound.sfx(GameMusic.SFX_OK); }
        }
        if (gust) { gx[0] -= W * 0.15f * dt; if (gx[0] < -20) gust = false; }
        for (float[] s : shot) if (s[2] != 0) { s[0] += s[2] * dt; s[1] += s[3] * dt; if (s[0] > W * 1.05f || s[1] < 0 || s[1] > H) s[2] = 0; }
        for (float[] b : bolt) if (b[2] != 0) {
            b[0] += b[2] * dt; b[1] += b[3] * dt;
            if (b[0] < -20 || b[1] < 0 || b[1] > H) { b[2] = 0; continue; }
            for (Arked4.P pl : a.ps) {
                if (!pl.on || pl.lives <= 0 || pl.inv > 0) continue;
                if (Math.abs(b[0] - pl.x) < H * 0.035f && Math.abs(b[1] - pl.y) < H * 0.035f) { b[2] = 0; hurt(pl); }
            }
        }
        int alive = 0;
        for (float[] e : en) {
            if (e[5] <= 0) continue;
            alive++;
            e[4] += dt;
            float r;
            if (e[3] == 0) {   // cloud: drifts, throws lightning
                e[0] -= W * (0.07f + wave * 0.006f) * dt;
                r = H * 0.06f;
                if (e[0] < W && a.rnd.nextFloat() < dt * (0.25f + wave * 0.04f)) bolt(e[0], e[1], -W * 0.35f, 0);
            } else if (e[3] == 1) {   // bird: swoops in waves
                e[0] -= W * 0.16f * dt;
                e[1] = e[6] + (float) Math.sin(e[4] * 3) * H * 0.08f;
                r = H * 0.03f;
            } else {   // the Storm King
                e[0] = Math.max(W * 0.78f, e[0] - W * 0.1f * dt);
                e[1] = H * (0.5f + 0.3f * (float) Math.sin(e[4] * 0.8f));
                r = H * 0.14f;
                if (a.rnd.nextFloat() < dt * 1.8f) for (int k = -2; k <= 2; k++) bolt(e[0] - r, e[1], -W * 0.3f, k * H * 0.12f);
            }
            if (e[0] < -W * 0.1f) { e[0] = W * 1.1f; continue; }   // goes round again
            for (Arked4.P pl : a.ps) {
                if (!pl.on || pl.lives <= 0 || pl.inv > 0) continue;
                if (Math.abs(e[0] - pl.x) < r && Math.abs(e[1] - pl.y) < r) hurt(pl);
            }
            for (float[] s : shot) {
                if (s[2] == 0 || Math.abs(s[0] - e[0]) > r || Math.abs(s[1] - e[1]) > r) continue;
                s[2] = 0;
                if (--e[2] <= 0) {
                    e[5] = 0;
                    a.ps[(int) s[4]].score += e[3] == 2 ? 300 : e[3] == 1 ? 15 : 10;
                    a.sound.sfx(e[3] == 2 ? GameMusic.SFX_WIN : GameMusic.SFX_COIN);
                    if (!gust && a.rnd.nextFloat() < 0.1f) { gust = true; gx[0] = e[0]; gy[0] = e[1]; }
                }
                break;
            }
        }
        if (alive == 0 && banner <= 0) {
            if (wave >= WAVES) { done(true); return; }
            nextWave();
        }
        boolean any = false;
        for (Arked4.P pl : a.ps) if (pl.on && pl.lives > 0) any = true;
        if (!any) done(false);
    }

    private void done(boolean won) {
        int total = 0;
        for (Arked4.P pl : a.ps) if (pl.on) total += pl.score;
        a.finish((won ? "Langit cerah semula! 🌈 " : "Ribut terlalu kuat pada gelombang " + wave + ". ") + "Markah pasukan " + total);
    }

    private void hurt(Arked4.P pl) {
        pl.lives--;
        pl.inv = 2;
        a.party.buzz(pl.seat, 200);
        a.sound.sfx(GameMusic.SFX_WRONG);
    }

    private void shoot(float x, float y, float vx, float vy, int owner) {
        for (float[] s : shot) if (s[2] == 0) { s[0] = x; s[1] = y; s[2] = vx; s[3] = vy; s[4] = owner; return; }
    }

    private void bolt(float x, float y, float vx, float vy) {
        for (float[] b : bolt) if (b[2] == 0) { b[0] = x; b[1] = y; b[2] = vx; b[3] = vy; return; }
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        int sk = Math.min(SKY.length - 1, (wave - 1) * SKY.length / WAVES);
        if (sk != skyFor) { skyFor = sk; sky = new LinearGradient(0, 0, 0, H, SKY[sk][0], SKY[sk][1], Shader.TileMode.CLAMP); }
        p.setShader(sky);
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        p.setColor(sk == 3 ? 0x22FFFFFF : 0x55FFFFFF);   // far clouds drifting
        for (int i = 0; i < 6; i++) {
            float x = ((i * W * 0.3f - cloudOff) % (W * 1.8f) + W * 1.8f) % (W * 1.8f) - W * 0.3f, y = H * (0.15f + (i % 3) * 0.28f);
            c.drawCircle(x, y, H * 0.06f, p);
            c.drawCircle(x + H * 0.07f, y - H * 0.02f, H * 0.08f, p);
            c.drawCircle(x + H * 0.15f, y, H * 0.06f, p);
        }
        p.setColor(0xFF166534);   // the kampung's palm line far below
        c.drawRect(0, H * 0.97f, W, H, p);
        for (float[] e : en) {
            if (e[5] <= 0) continue;
            if (e[3] == 1) bird(c, p, e[0], e[1], H * 0.03f, e[4]);
            else storm(c, p, e[0], e[1], e[3] == 2 ? H * 0.14f : H * 0.06f, e[3] == 2, t);
            if (e[3] == 2) {
                p.setColor(0xFFF43F5E);
                c.drawRect(W * 0.3f, H * 0.08f, W * 0.3f + W * 0.4f * e[2] / e[7], H * 0.095f, p);
            }
        }
        for (float[] s : shot) if (s[2] != 0) { p.setColor(Party.COLORS[(int) s[4]]); c.drawCircle(s[0], s[1], H * 0.008f, p); }
        p.setColor(0xFFFDE047);
        for (float[] b : bolt) {
            if (b[2] == 0) continue;
            a.path.reset();
            a.path.moveTo(b[0], b[1] - H * 0.02f); a.path.lineTo(b[0] - H * 0.01f, b[1]); a.path.lineTo(b[0] + H * 0.005f, b[1]);
            a.path.lineTo(b[0] - H * 0.006f, b[1] + H * 0.022f); a.path.lineTo(b[0] + H * 0.012f, b[1] - H * 0.004f); a.path.lineTo(b[0], b[1] - H * 0.004f);
            a.path.close();
            c.drawPath(a.path, p);
        }
        if (gust) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.05f);
            a.tx.setColor(Color.argb((int) (180 + 70 * Math.sin(t * 8)), 255, 255, 255));
            c.drawText("◆", gx[0], gy[0] + H * 0.018f, a.tx);
        }
        for (Arked4.P pl : a.ps) {
            if (!pl.on || pl.lives <= 0) continue;
            if (pl.inv > 0 && ((int) (pl.inv * 10)) % 2 == 0) continue;
            wau(c, p, pl.x, pl.y, H * 0.05f, Party.COLORS[pl.seat], t + pl.seat);
            a.label(c, pl, pl.x, pl.y - H * 0.06f);
        }
        for (Arked4.P pl : a.ps) if (pl.on) a.hudBox(c, pl.seat, Arked4.hearts(pl.lives) + "  " + pl.score + (spread[pl.seat] > 0 ? " ◆" : ""));
        a.tx.setTextAlign(Paint.Align.RIGHT);
        a.tx.setTextSize(H * 0.026f);
        a.tx.setColor(0xFFFFFFFF);
        c.drawText("Gelombang " + wave + "/" + WAVES, W * 0.99f, H * 0.045f, a.tx);
        if (banner > 0) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.07f);
            a.tx.setColor(Color.argb((int) (255 * Math.min(1, banner)), 253, 224, 71));
            c.drawText(wave % 4 == 0 ? "RAJA RIBUT!" : "GELOMBANG " + wave, W / 2, H * 0.4f, a.tx);
        }
    }

    /** A wau bulan: crescent wings, a round body and a ribbon tail, in the player's colour. */
    private void wau(Canvas c, Paint p, float x, float y, float s, int col, float t) {
        p.setStrokeWidth(s * 0.06f);
        p.setColor(0xCCFFFFFF);
        c.drawLine(x - s * 0.6f, y, x - s * 1.6f, y + (float) Math.sin(t * 6) * s * 0.4f, p);
        p.setColor(col);
        c.drawArc(x - s, y - s * 0.9f, x + s, y + s * 0.3f, 200, 140, true, p);   // the wings
        c.drawCircle(x, y + s * 0.2f, s * 0.35f, p);
        c.drawArc(x - s * 0.7f, y + s * 0.3f, x + s * 0.7f, y + s * 1.0f, 200, 140, true, p);   // the moon tail
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x, y + s * 0.2f, s * 0.15f, p);
    }

    private void storm(Canvas c, Paint p, float x, float y, float s, boolean king, float t) {
        p.setColor(king ? 0xFF312E81 : 0xFF475569);
        c.drawCircle(x - s * 0.5f, y, s * 0.6f, p);
        c.drawCircle(x + s * 0.1f, y - s * 0.25f, s * 0.75f, p);
        c.drawCircle(x + s * 0.6f, y + s * 0.05f, s * 0.55f, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x - s * 0.15f, y - s * 0.1f, s * 0.15f, p);
        c.drawCircle(x + s * 0.3f, y - s * 0.1f, s * 0.15f, p);
        p.setColor(0xFF111827);
        c.drawCircle(x - s * 0.18f, y - s * 0.08f, s * 0.07f, p);
        c.drawCircle(x + s * 0.27f, y - s * 0.08f, s * 0.07f, p);
        if (king) {
            p.setColor(0xFFFACC15);
            for (int k = 0; k < 3; k++) {
                a.path.reset();
                float cx = x - s * 0.3f + k * s * 0.3f;
                a.path.moveTo(cx - s * 0.12f, y - s * 0.9f); a.path.lineTo(cx, y - s * 1.2f); a.path.lineTo(cx + s * 0.12f, y - s * 0.9f); a.path.close();
                c.drawPath(a.path, p);
            }
        }
    }

    private void bird(Canvas c, Paint p, float x, float y, float s, float ph) {
        float f = (float) Math.sin(ph * 14) * s * 0.6f;
        p.setStrokeWidth(s * 0.25f);
        p.setColor(0xFF7C2D12);
        c.drawLine(x, y, x + s, y - f, p);
        c.drawLine(x, y, x - s * 0.2f, y - f, p);
        c.drawCircle(x, y, s * 0.35f, p);
        p.setColor(0xFFF59E0B);
        c.drawCircle(x - s * 0.35f, y, s * 0.12f, p);
    }
}
