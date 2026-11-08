package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

/**
 * Pendekar Kampung: a co-op beat 'em up. Pirates have come ashore; the players, silat fighters, clear them from four
 * places (the jetty, the market, the forest, the pirates' ship), three waves each and the captain at the end. Players
 * walk on a strip of ground (up and down for depth), punch with A and jump with B; a ketupat restores energy.
 * Everyone shares the screen. Knocked down players get up again; the game ends when everyone is down at once.
 */
final class ModPendekar implements Arked4.Mod {
    private static final String[] STAGE = {"Jeti", "Pasar", "Hutan", "Kapal Lanun"};
    private static final int[][] SKY = {{0xFF38BDF8, 0xFFBAE6FD}, {0xFFF59E0B, 0xFFFDE68A}, {0xFF15803D, 0xFF86EFAC}, {0xFF1E1B4B, 0xFF7C3AED}};
    private static final int[] GROUND = {0xFF92400E, 0xFF78716C, 0xFF3F6212, 0xFF7C2D12};

    private final Arked4 a;
    private final float[][] en = new float[12][10];   // x, y(depth), hp, max, kind (0 pirate, 1 big, 2 captain), cool, hit, vx, alive, face
    private final float[] hp = new float[4], down = new float[4], z = new float[4], vz = new float[4], punch = new float[4];
    private int stage, wave;
    private float banner, ketX, ketY;
    private boolean ket;
    private Shader sky;
    private int skyFor = -1;

    ModPendekar(Arked4 a) { this.a = a; }

    private float top() { return a.H * 0.62f; }
    private float bottom() { return a.H * 0.94f; }

    @Override public void build() {
        int k = 0;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int i = pl.seat;
            pl.x = a.W * (0.12f + k * 0.08f);
            pl.y = top() + (bottom() - top()) * (0.2f + 0.2f * k);
            hp[i] = 100; down[i] = 0; z[i] = vz[i] = 0; punch[i] = 0; pl.lives = 3; pl.face = true;
            k++;
        }
        stage = 0;
        wave = 0;
        nextWave();
    }

    private void nextWave() {
        wave++;
        if (wave > 4) { wave = 1; stage++; }
        if (stage >= STAGE.length) {
            int total = 0;
            for (Arked4.P pl : a.ps) if (pl.on) total += pl.score;
            a.finish("Kampung selamat! Lanun lari! 🏝  Markah pasukan " + total);
            return;
        }
        for (float[] e : en) e[8] = 0;
        int players = 0;
        for (Arked4.P pl : a.ps) if (pl.on) players++;
        if (wave == 4) spawn(2, a.W * 0.9f, (top() + bottom()) / 2, 40 + stage * 15 + players * 10);
        else {
            int n = 2 + wave + stage + players;
            for (int i = 0; i < Math.min(n, en.length); i++) {
                boolean big = a.rnd.nextFloat() < 0.15f + stage * 0.08f;
                float x = i % 2 == 0 ? a.W * (1.05f + i * 0.05f) : -a.W * (0.05f + i * 0.05f);
                spawn(big ? 1 : 0, x, top() + a.rnd.nextFloat() * (bottom() - top()), big ? 8 + stage * 2 : 3 + stage);
            }
        }
        for (Arked4.P pl : a.ps) if (pl.on && pl.lives <= 0) { pl.lives = 1; hp[pl.seat] = 60; down[pl.seat] = 0; }   // friends get up
        banner = 2;
        if (wave == 2) { ket = true; ketX = a.W * (0.3f + a.rnd.nextFloat() * 0.4f); ketY = top() + a.rnd.nextFloat() * (bottom() - top()); }
    }

    private void spawn(int kind, float x, float y, float hpv) {
        for (float[] e : en) {
            if (e[8] > 0) continue;
            e[0] = x; e[1] = y; e[2] = e[3] = hpv; e[4] = kind; e[5] = 1 + a.rnd.nextFloat(); e[6] = 0; e[7] = 0; e[8] = 1; e[9] = 1;
            return;
        }
    }

    @Override public void update(float dt) {
        float W = a.W, H = a.H;
        if (banner > 0) banner -= dt;
        int aliveP = 0;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int i = pl.seat;
            if (pl.lives <= 0) continue;
            aliveP++;
            if (down[i] > 0) { down[i] -= dt; if (down[i] <= 0) { hp[i] = 100; pl.inv = 2; } continue; }
            if (pl.inv > 0) pl.inv -= dt;
            if (punch[i] > 0) punch[i] -= dt;
            pl.cool -= dt;
            float mx = a.ix(pl), my = a.iy(pl);
            if (Math.abs(mx) < 0.3f) mx = 0;
            if (Math.abs(my) < 0.3f) my = 0;
            pl.x = Math.max(W * 0.03f, Math.min(W * 0.97f, pl.x + mx * W * 0.25f * dt));
            pl.y = Math.max(top(), Math.min(bottom(), pl.y + my * H * 0.2f * dt));
            if (mx != 0) pl.face = mx > 0;
            if (a.pressJump(pl) && z[i] == 0) vz[i] = H * 0.9f;
            if (z[i] > 0 || vz[i] > 0) { z[i] += vz[i] * dt; vz[i] -= H * 2.6f * dt; if (z[i] <= 0) { z[i] = 0; vz[i] = 0; } }
            if (a.pressA(pl) && pl.cool <= 0) {
                pl.cool = 0.28f;
                punch[i] = 0.15f;
                float reach = H * (z[i] > 0 ? 0.12f : 0.1f);
                for (float[] e : en) {
                    if (e[8] <= 0) continue;
                    float dx = e[0] - pl.x;
                    if ((dx > 0) == pl.face && Math.abs(dx) < reach && Math.abs(e[1] - pl.y) < H * 0.06f) {
                        e[2] -= z[i] > 0 ? 2 : 1;   // a flying kick hits harder
                        e[6] = 0.2f;
                        e[0] += (pl.face ? 1 : -1) * H * (e[4] == 2 ? 0.01f : 0.04f);
                        a.sound.sfx(GameMusic.SFX_COIN);
                        a.party.buzz(i, 25);
                        if (e[2] <= 0) { e[8] = 0; pl.score += e[4] == 2 ? 100 : e[4] == 1 ? 25 : 10; a.sound.sfx(GameMusic.SFX_OK); }
                    }
                }
            }
            if (ket && Math.hypot(pl.x - ketX, pl.y - ketY) < H * 0.05f) { ket = false; hp[i] = 100; a.sound.sfx(GameMusic.SFX_OK); }
        }
        int aliveE = 0;
        for (float[] e : en) {
            if (e[8] <= 0) continue;
            aliveE++;
            if (e[6] > 0) { e[6] -= dt; continue; }
            Arked4.P tgt = null;
            float bd = Float.MAX_VALUE;
            for (Arked4.P pl : a.ps) {
                if (!pl.on || pl.lives <= 0 || down[pl.seat] > 0) continue;
                float d = Math.abs(pl.x - e[0]) + Math.abs(pl.y - e[1]) * 2;
                if (d < bd) { bd = d; tgt = pl; }
            }
            if (tgt == null) continue;
            float sp = W * (e[4] == 0 ? 0.11f : 0.08f) * (1 + stage * 0.08f);
            float want = tgt.x + (tgt.x < e[0] ? H * 0.08f : -H * 0.08f);
            if (Math.abs(want - e[0]) > H * 0.01f) e[0] += Math.signum(want - e[0]) * sp * dt;
            if (Math.abs(tgt.y - e[1]) > H * 0.01f) e[1] += Math.signum(tgt.y - e[1]) * sp * 0.6f * dt;
            e[9] = tgt.x > e[0] ? 1 : -1;
            e[5] -= dt;
            if (e[5] <= 0 && Math.abs(tgt.x - e[0]) < H * 0.11f && Math.abs(tgt.y - e[1]) < H * 0.06f) {
                e[5] = e[4] == 2 ? 0.9f : 1.4f + a.rnd.nextFloat();
                int i = tgt.seat;
                if (tgt.inv <= 0 && z[i] < H * 0.05f) {
                    hp[i] -= e[4] == 2 ? 18 : e[4] == 1 ? 14 : 8;
                    tgt.inv = 0.6f;
                    a.party.buzz(i, 150);
                    a.sound.sfx(GameMusic.SFX_WRONG);
                    if (hp[i] <= 0) { tgt.lives--; down[i] = tgt.lives > 0 ? 2.5f : 0; hp[i] = 0; }
                }
            }
        }
        if (aliveE == 0 && banner <= 0) nextWave();
        boolean any = false;
        for (Arked4.P pl : a.ps) if (pl.on && pl.lives > 0) any = true;
        if (!any) {
            int total = 0;
            for (Arked4.P pl : a.ps) if (pl.on) total += pl.score;
            a.finish("Lanun terlalu ramai di " + STAGE[Math.min(stage, 3)] + ". Markah pasukan " + total + ". Cuba lagi!");
        }
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        int st = Math.min(stage, 3);
        if (skyFor != st) { skyFor = st; sky = new LinearGradient(0, 0, 0, top(), SKY[st][0], SKY[st][1], Shader.TileMode.CLAMP); }
        p.setShader(sky);
        c.drawRect(0, 0, W, top(), p);
        p.setShader(null);
        scenery(c, p, st, W, H, t);
        p.setColor(GROUND[st]);
        c.drawRect(0, top() - H * 0.03f, W, H, p);
        p.setColor(0x22000000);
        for (int i = 0; i < 8; i++) c.drawRect(0, top() + i * H * 0.045f, W, top() + i * H * 0.045f + 2, p);
        if (ket) {
            p.setColor(0xFF65A30D);
            c.save();
            c.rotate(45, ketX, ketY - H * 0.03f);
            c.drawRect(ketX - H * 0.025f, ketY - H * 0.055f, ketX + H * 0.025f, ketY - H * 0.005f, p);
            c.restore();
            p.setColor(0xFF3F6212);
            c.drawLine(ketX, ketY - H * 0.07f, ketX, ketY, p);
        }
        // draw back to front by depth
        for (float row = top(); row <= bottom() + 1; row += H * 0.02f) {
            for (float[] e : en) if (e[8] > 0 && e[1] >= row && e[1] < row + H * 0.02f) pirate(c, p, e, t);
            for (Arked4.P pl : a.ps) if (pl.on && pl.lives > 0 && pl.y >= row && pl.y < row + H * 0.02f) fighter(c, p, pl, t);
        }
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int i = pl.seat;
            a.hudBox(c, i, Arked4.hearts(pl.lives) + "  " + (int) Math.max(0, hp[i]) + "%  " + pl.score);
        }
        a.tx.setTextAlign(Paint.Align.RIGHT);
        a.tx.setTextSize(H * 0.026f);
        a.tx.setColor(0xFFFFFFFF);
        c.drawText(STAGE[st] + " · " + (wave == 4 ? "Kapten!" : "gelombang " + wave + "/3"), W * 0.99f, H * 0.045f, a.tx);
        if (banner > 0) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.07f);
            a.tx.setColor(Color.argb((int) (255 * Math.min(1, banner)), 253, 224, 71));
            c.drawText(wave == 4 ? "KAPTEN LANUN!" : wave == 1 ? STAGE[st].toUpperCase() : "GELOMBANG " + wave, W / 2, H * 0.35f, a.tx);
        }
    }

    private void scenery(Canvas c, Paint p, int st, float W, float H, float t) {
        float base = top() - H * 0.03f;
        switch (st) {
            case 0:   // the sea and a jetty's posts
                p.setColor(0xFF0284C7);
                c.drawRect(0, base - H * 0.12f, W, base, p);
                p.setColor(0xFF78350F);
                for (int i = 0; i < 10; i++) c.drawRect(W * i / 10f, base - H * 0.15f, W * i / 10f + H * 0.02f, base, p);
                break;
            case 1:   // market stalls with striped roofs
                for (int i = 0; i < 5; i++) {
                    float x = W * (0.05f + i * 0.2f);
                    p.setColor(0xFF78350F);
                    c.drawRect(x, base - H * 0.2f, x + W * 0.15f, base, p);
                    for (int k = 0; k < 5; k++) {
                        p.setColor(k % 2 == 0 ? 0xFFDC2626 : 0xFFFFFFFF);
                        c.drawRect(x + k * W * 0.03f, base - H * 0.26f, x + (k + 1) * W * 0.03f, base - H * 0.2f, p);
                    }
                }
                break;
            case 2:   // trees
                for (int i = 0; i < 9; i++) {
                    float x = W * (0.04f + i * 0.12f);
                    p.setColor(0xFF422006);
                    c.drawRect(x - H * 0.015f, base - H * 0.25f, x + H * 0.015f, base, p);
                    p.setColor(i % 2 == 0 ? 0xFF14532D : 0xFF166534);
                    c.drawCircle(x, base - H * 0.3f, H * 0.11f, p);
                }
                break;
            default:   // the pirates' ship and its flag
                p.setColor(0xFF451A03);
                c.drawRect(W * 0.1f, base - H * 0.1f, W * 0.9f, base, p);
                p.setColor(0xFF292524);
                c.drawRect(W * 0.48f, base - H * 0.5f, W * 0.5f, base - H * 0.1f, p);
                p.setColor(0xFFE7E5E4);
                c.drawRect(W * 0.5f, base - H * 0.48f, W * 0.62f, base - H * 0.25f, p);
                p.setColor(0xFF111827);
                c.drawRect(W * 0.43f, base - H * 0.5f, W * 0.48f, base - H * 0.46f + (float) Math.sin(t * 5) * H * 0.005f, p);
        }
    }

    private void fighter(Canvas c, Paint p, Arked4.P pl, float t) {
        int i = pl.seat;
        float H = a.H, s = H * 0.12f, x = pl.x, y = pl.y - z[i];
        if (pl.inv > 0 && ((int) (pl.inv * 10)) % 2 == 0) return;
        p.setColor(0x44000000);
        c.drawOval(pl.x - s * 0.3f, pl.y - s * 0.05f, pl.x + s * 0.3f, pl.y + s * 0.05f, p);
        Badge.ring(c, i, pl.x, pl.y, s * 0.3f, t);
        if (down[i] > 0) {   // lying down for a moment
            p.setColor(Party.COLORS[i]);
            c.drawRoundRect(x - s * 0.5f, y - s * 0.15f, x + s * 0.5f, y, s * 0.06f, s * 0.06f, p);
            p.setColor(0xFFD6A77A);
            c.drawCircle(x + s * 0.55f, y - s * 0.08f, s * 0.1f, p);
            return;
        }
        float dir = pl.face ? 1 : -1;
        float walk = (float) Math.sin(t * 12 + i) * s * 0.08f;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.09f);
        p.setColor(0xFF1F2937);
        c.drawLine(x, y - s * 0.4f, x - s * 0.12f + walk, y, p);
        c.drawLine(x, y - s * 0.4f, x + s * 0.12f - walk, y, p);
        p.setColor(Party.COLORS[i]);
        c.drawRoundRect(x - s * 0.15f, y - s * 0.5f, x + s * 0.15f, y - s * 0.32f, s * 0.04f, s * 0.04f, p);
        p.setColor(0xFF111827);
        c.drawRoundRect(x - s * 0.13f, y - s * 0.82f, x + s * 0.13f, y - s * 0.45f, s * 0.05f, s * 0.05f, p);
        p.setColor(0xFFD6A77A);
        if (punch[i] > 0) c.drawLine(x, y - s * 0.72f, x + dir * s * 0.45f, y - s * 0.72f, p);
        else c.drawLine(x, y - s * 0.72f, x + dir * s * 0.22f, y - s * 0.55f, p);
        c.drawCircle(x, y - s * 0.93f, s * 0.12f, p);
        p.setColor(Party.COLORS[i]);
        a.path.reset();
        a.path.moveTo(x - s * 0.15f, y - s * 1.0f);
        a.path.lineTo(x + dir * s * 0.04f, y - s * 1.22f);
        a.path.lineTo(x + s * 0.15f, y - s * 1.0f);
        a.path.close();
        c.drawPath(a.path, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        a.label(c, pl, x, y - s * 1.25f);
        // energy bar
        p.setColor(0x88000000);
        c.drawRect(x - s * 0.3f, y - s * 1.6f, x + s * 0.3f, y - s * 1.55f, p);
        p.setColor(hp[i] > 40 ? 0xFF4ADE80 : 0xFFF87171);
        c.drawRect(x - s * 0.3f, y - s * 1.6f, x - s * 0.3f + s * 0.6f * Math.max(0, hp[i]) / 100f, y - s * 1.55f, p);
    }

    private void pirate(Canvas c, Paint p, float[] e, float t) {
        float H = a.H, s = H * (e[4] == 2 ? 0.17f : e[4] == 1 ? 0.14f : 0.115f), x = e[0], y = e[1];
        float dir = e[9];
        p.setColor(0x44000000);
        c.drawOval(x - s * 0.3f, y - s * 0.05f, x + s * 0.3f, y + s * 0.05f, p);
        float walk = (float) Math.sin(t * 10 + x) * s * 0.07f;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.1f);
        p.setColor(0xFF1C1917);
        c.drawLine(x, y - s * 0.4f, x - s * 0.12f + walk, y, p);
        c.drawLine(x, y - s * 0.4f, x + s * 0.12f - walk, y, p);
        p.setColor(e[6] > 0 ? 0xFFFFFFFF : e[4] == 2 ? 0xFF7F1D1D : e[4] == 1 ? 0xFF1D4ED8 : 0xFFE5E7EB);
        float bw = e[4] == 1 ? 0.24f : 0.15f;
        c.drawRoundRect(x - s * bw, y - s * 0.85f, x + s * bw, y - s * 0.38f, s * 0.06f, s * 0.06f, p);
        if (e[4] != 1) {   // striped shirt
            p.setColor(0xFFDC2626);
            for (int k = 0; k < 3; k++) c.drawRect(x - s * bw, y - s * (0.78f - k * 0.13f), x + s * bw, y - s * (0.72f - k * 0.13f), p);
        }
        p.setColor(0xFFC08457);
        c.drawLine(x, y - s * 0.72f, x + dir * s * 0.25f, y - s * 0.55f, p);
        c.drawCircle(x, y - s * 0.95f, s * 0.13f, p);
        p.setColor(e[4] == 2 ? 0xFF111827 : 0xFFDC2626);   // bandana, or the captain's hat
        if (e[4] == 2) c.drawRect(x - s * 0.2f, y - s * 1.12f, x + s * 0.2f, y - s * 1.02f, p);
        else c.drawRect(x - s * 0.13f, y - s * 1.07f, x + s * 0.13f, y - s * 1.0f, p);
        p.setColor(0xFF111827);
        c.drawCircle(x + dir * s * 0.05f, y - s * 0.96f, s * 0.03f, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        if (e[3] > 1) {
            p.setColor(0x88000000);
            c.drawRect(x - s * 0.3f, y - s * 1.3f, x + s * 0.3f, y - s * 1.25f, p);
            p.setColor(0xFFF87171);
            c.drawRect(x - s * 0.3f, y - s * 1.3f, x - s * 0.3f + s * 0.6f * e[2] / e[3], y - s * 1.25f, p);
        }
    }
}
