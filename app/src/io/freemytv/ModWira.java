package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

/**
 * Wira Hutan: a co-op run-and-gun. Robots that cut down trees have come into the forest; the heroes run right
 * through five areas (forest, swamp, cave, waterfall, the robot factory), firing on their own the way they face.
 * Weapons drop from crates: spread (three shots), laser (goes through) and rapid. Each area ends with a big
 * bulldozer robot. One shared camera follows the group; nobody can fall behind the left edge.
 */
final class ModWira implements Arked4.Mod {
    private static final String[] AREA = {"Hutan", "Paya", "Gua", "Air Terjun", "Kilang Robot"};
    private static final int[][] SKY = {{0xFF16A34A, 0xFFBBF7D0}, {0xFF365314, 0xFFA3E635}, {0xFF1C1917, 0xFF57534E}, {0xFF0EA5E9, 0xFFE0F2FE}, {0xFF374151, 0xFFF97316}};
    private static final int[] GROUND = {0xFF713F12, 0xFF3F6212, 0xFF44403C, 0xFF475569, 0xFF1F2937};
    private static final float LEN = 6;   // an area is this many screens long

    private final Arked4 a;
    private final float[] fire = new float[4], gun = new float[4], gunT = new float[4];   // gun: 0 normal, 1 spread, 2 laser, 3 rapid
    private final float[][] bul = new float[60][6];   // x, y, vx, vy, life, owner (−1 enemy)
    private final float[][] en = new float[16][8];    // x, y, hp, kind (0 walker, 1 drone, 2 boss), t, alive, vx, max
    private final float[][] plats = new float[24][3]; // x, y, width
    private final float[] crate = new float[4];       // x, y, kind, alive
    private int area, nPlat;
    private float cam, spawnX, bossHp, banner;
    private boolean boss;
    private Shader sky;
    private int skyFor = -1;

    ModWira(Arked4 a) { this.a = a; }

    private float floor() { return a.H * 0.82f; }

    @Override public void build() {
        area = 0;
        startArea();
        for (Arked4.P pl : a.ps) pl.lives = 3;
    }

    private void startArea() {
        float W = a.W, H = a.H;
        cam = 0;
        spawnX = W * 0.9f;
        boss = false;
        banner = 2.5f;
        for (float[] e : en) e[5] = 0;
        for (float[] b : bul) b[4] = 0;
        crate[3] = 0;
        nPlat = 0;
        for (int i = 0; i < plats.length; i++) {   // floating ledges along the way, a new layout each area
            float x = W * (0.6f + i * LEN / plats.length) + a.rnd.nextFloat() * W * 0.1f;
            if (x > W * (LEN - 0.6f)) break;
            plats[nPlat][0] = x;
            plats[nPlat][1] = floor() - H * (0.16f + a.rnd.nextInt(2) * 0.15f);
            plats[nPlat][2] = W * (0.08f + a.rnd.nextFloat() * 0.06f);
            nPlat++;
        }
        int k = 0;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            pl.x = W * (0.1f + k * 0.06f);
            pl.y = floor();
            pl.vx = pl.vy = 0;
            pl.face = true;
            if (pl.lives <= 0) pl.lives = 1;   // friends come back for the new area
            pl.inv = 2;
            k++;
        }
    }

    private float groundAt(float x, float y, float oldY) {
        float g = floor();
        for (int i = 0; i < nPlat; i++) {
            float[] pf = plats[i];
            if (x > pf[0] && x < pf[0] + pf[2] && oldY <= pf[1] + 1 && y >= pf[1]) return pf[1];
        }
        return y >= g ? g : -1;
    }

    @Override public void update(float dt) {
        float W = a.W, H = a.H;
        if (banner > 0) banner -= dt;
        // the camera follows the group's middle, and never goes back
        float sum = 0, n = 0, minX = Float.MAX_VALUE;
        for (Arked4.P pl : a.ps) if (pl.on && pl.lives > 0) { sum += pl.x; n++; minX = Math.min(minX, pl.x); }
        if (n > 0) {
            float target = Math.min(sum / n - W * 0.4f, minX - W * 0.05f + W * 0.6f);
            target = Math.max(cam, Math.min(W * (LEN - 1), target));
            cam += (target - cam) * Math.min(1, dt * 3);
        }
        for (Arked4.P pl : a.ps) {
            if (!pl.on || pl.lives <= 0) continue;
            int i = pl.seat;
            if (pl.inv > 0) pl.inv -= dt;
            if (gunT[i] > 0) { gunT[i] -= dt; if (gunT[i] <= 0) gun[i] = 0; }
            float mx = a.ix(pl);
            if (Math.abs(mx) < 0.3f) mx = 0;
            pl.vx = mx * W * 0.28f;
            if (mx != 0) pl.face = mx > 0;
            if (a.pressJump(pl) && pl.ground) { pl.vy = -H * 1.25f; pl.ground = false; a.sound.sfx(GameMusic.SFX_JUMP); }
            if (a.iy(pl) > 0.6f && pl.ground && pl.y < floor() - 1) { pl.y += 4; pl.ground = false; }   // ▼ drops off a ledge
            pl.vy += H * 3.2f * dt;
            float oldY = pl.y;
            pl.x = Math.max(cam + W * 0.02f, Math.min(cam + W * 0.98f, pl.x + pl.vx * dt));
            pl.y += pl.vy * dt;
            float gy = pl.vy >= 0 ? groundAt(pl.x, pl.y, oldY) : -1;
            pl.ground = gy >= 0;
            if (pl.ground) { pl.y = gy; pl.vy = 0; }
            fire[i] -= dt;
            if (fire[i] <= 0) {   // fires on its own
                fire[i] = gun[i] == 3 ? 0.12f : gun[i] == 2 ? 0.35f : 0.25f;
                float d = pl.face ? 1 : -1, sp = W * 0.9f;
                shoot(pl.x + d * H * 0.04f, pl.y - H * 0.07f, d * sp, 0, i, gun[i] == 2 ? 2 : 1);
                if (gun[i] == 1) {
                    shoot(pl.x, pl.y - H * 0.07f, d * sp * 0.95f, -sp * 0.25f, i, 1);
                    shoot(pl.x, pl.y - H * 0.07f, d * sp * 0.95f, sp * 0.25f, i, 1);
                }
            }
            if (crate[3] > 0 && Math.abs(crate[0] - pl.x) < H * 0.05f && Math.abs(crate[1] - pl.y) < H * 0.08f) {
                gun[i] = crate[2]; gunT[i] = 12; crate[3] = 0;
                a.sound.sfx(GameMusic.SFX_OK);
                a.party.buzz(i, 60);
            }
        }
        // enemies come in as the camera moves on
        if (!boss && cam + W > spawnX && spawnX < W * (LEN - 1)) {
            spawnX += W * (0.35f - area * 0.03f);
            boolean drone = a.rnd.nextFloat() < 0.35f + area * 0.05f;
            spawn(drone ? 1 : 0, cam + W * 1.05f, drone ? H * (0.3f + a.rnd.nextFloat() * 0.25f) : floor(), 2 + area);
            if (a.rnd.nextFloat() < 0.2f && crate[3] == 0) { crate[0] = cam + W * 1.0f; crate[1] = floor(); crate[2] = 1 + a.rnd.nextInt(3); crate[3] = 1; }
        }
        if (!boss && cam >= W * (LEN - 1) - 2) {
            boss = true;
            int players = 0;
            for (Arked4.P pl : a.ps) if (pl.on) players++;
            spawn(2, cam + W * 0.85f, floor(), 40 + area * 20 + players * 10);
            banner = 2;
        }
        boolean bossAlive = false;
        for (float[] e : en) {
            if (e[5] <= 0) continue;
            e[4] += dt;
            Arked4.P near = null;
            float bd = Float.MAX_VALUE;
            for (Arked4.P pl : a.ps) if (pl.on && pl.lives > 0 && Math.abs(pl.x - e[0]) < bd) { bd = Math.abs(pl.x - e[0]); near = pl; }
            if (e[3] == 0) {   // walker: comes left, shoots now and then
                e[0] -= W * (0.06f + area * 0.01f) * dt;
                if (near != null && a.rnd.nextFloat() < dt * 0.6f) shoot(e[0], e[1] - H * 0.05f, near.x < e[0] ? -W * 0.4f : W * 0.4f, 0, -1, 1);
            } else if (e[3] == 1) {   // drone: hovers and drops sparks
                e[0] -= W * 0.08f * dt;
                e[1] += (float) Math.sin(e[4] * 3) * H * 0.1f * dt;
                if (a.rnd.nextFloat() < dt * 0.5f) shoot(e[0], e[1], 0, H * 0.5f, -1, 1);
            } else {   // the bulldozer boss rolls back and forth and fires fans
                bossAlive = true;
                e[0] = cam + W * (0.75f + 0.12f * (float) Math.sin(e[4] * 0.7f));
                if (a.rnd.nextFloat() < dt * (0.8f + area * 0.15f)) for (int k = -1; k <= 1; k++) shoot(e[0] - H * 0.1f, e[1] - H * 0.1f, -W * 0.45f, k * H * 0.2f, -1, 1);
            }
            if (e[0] < cam - W * 0.2f) { e[5] = 0; continue; }
            float hw = e[3] == 2 ? H * 0.14f : H * 0.04f, hh = e[3] == 2 ? H * 0.2f : H * 0.07f;
            for (Arked4.P pl : a.ps) {   // touching hurts
                if (!pl.on || pl.lives <= 0 || pl.inv > 0) continue;
                if (Math.abs(pl.x - e[0]) < hw && pl.y > e[1] - hh - H * 0.08f && pl.y - H * 0.08f < e[1]) hurt(pl);
            }
            for (int bi = 0; bi < bul.length; bi++) {
                float[] b = bul[bi];
                if (b[4] <= 0 || b[5] < 0) continue;
                if (Math.abs(b[0] - e[0]) < hw && b[1] > e[1] - hh && b[1] < e[1] + H * 0.01f) {
                    e[2] -= 1;
                    if (!laser[bi]) b[4] = 0;   // the laser goes through
                    if (e[2] <= 0) {
                        e[5] = 0;
                        Arked4.P owner = a.ps[(int) b[5]];
                        owner.score += e[3] == 2 ? 300 : 15;
                        a.sound.sfx(e[3] == 2 ? GameMusic.SFX_WIN : GameMusic.SFX_COIN);
                        break;
                    }
                }
            }
        }
        for (float[] b : bul) {
            if (b[4] <= 0) continue;
            b[4] -= dt;
            b[0] += b[2] * dt;
            b[1] += b[3] * dt;
            if (b[5] >= 0) continue;
            for (Arked4.P pl : a.ps) {
                if (!pl.on || pl.lives <= 0 || pl.inv > 0) continue;
                if (Math.abs(b[0] - pl.x) < H * 0.03f && b[1] > pl.y - H * 0.1f && b[1] < pl.y) { hurt(pl); b[4] = 0; break; }
            }
        }
        if (boss && !bossAlive && banner <= 0) {
            area++;
            if (area >= AREA.length) {
                int total = 0;
                for (Arked4.P pl : a.ps) if (pl.on) total += pl.score;
                a.finish("Hutan selamat! 🌳 Semua robot dikalahkan. Markah pasukan " + total);
                return;
            }
            a.sound.sfx(GameMusic.SFX_WIN);
            startArea();
        }
        boolean any = false;
        for (Arked4.P pl : a.ps) if (pl.on && pl.lives > 0) any = true;
        if (!any) {
            int total = 0;
            for (Arked4.P pl : a.ps) if (pl.on) total += pl.score;
            a.finish("Robot menang kali ini di " + AREA[area] + ". Markah pasukan " + total + ". Cuba lagi!");
        }
    }

    private final boolean[] laser = new boolean[60];

    private void hurt(Arked4.P pl) {
        pl.lives--;
        pl.inv = 2;
        a.party.buzz(pl.seat, 200);
        a.sound.sfx(GameMusic.SFX_WRONG);
    }

    private void spawn(int kind, float x, float y, float hp) {
        for (float[] e : en) {
            if (e[5] > 0) continue;
            e[0] = x; e[1] = y; e[2] = e[7] = hp; e[3] = kind; e[4] = 0; e[5] = 1;
            return;
        }
    }

    private void shoot(float x, float y, float vx, float vy, int owner, int kind) {
        for (int i = 0; i < bul.length; i++) {
            float[] b = bul[i];
            if (b[4] > 0) continue;
            b[0] = x; b[1] = y; b[2] = vx; b[3] = vy; b[4] = 1.4f; b[5] = owner;
            laser[i] = kind == 2;
            return;
        }
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        if (skyFor != area) { skyFor = area; sky = new LinearGradient(0, 0, 0, floor(), SKY[area][0], SKY[area][1], Shader.TileMode.CLAMP); }
        p.setShader(sky);
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        // parallax trees (or stalactites, pipes)
        for (int layer = 0; layer < 2; layer++) {
            float f = layer == 0 ? 0.3f : 0.6f, off = -(cam * f) % (W * 0.25f);
            p.setColor(layer == 0 ? Color.argb(90, 0, 0, 0) : Color.argb(150, 0, 0, 0));
            for (float x = off - W * 0.25f; x < W + W * 0.25f; x += W * 0.25f) {
                if (area == 2) {
                    a.path.reset();
                    a.path.moveTo(x, 0); a.path.lineTo(x + W * 0.05f, H * (0.2f + layer * 0.1f)); a.path.lineTo(x + W * 0.1f, 0); a.path.close();
                    c.drawPath(a.path, p);
                } else if (area == 4) c.drawRect(x, H * (0.3f - layer * 0.1f), x + W * 0.04f, floor(), p);
                else {
                    c.drawRect(x + W * 0.05f, H * 0.35f, x + W * 0.07f, floor(), p);
                    c.drawCircle(x + W * 0.06f, H * (0.32f + layer * 0.05f), H * (0.1f + layer * 0.03f), p);
                }
            }
        }
        if (area == 3) {   // the waterfall
            p.setColor(0x99E0F2FE);
            float wx = W * 0.6f - (cam * 0.6f) % (W * 1.5f);
            c.drawRect(wx, 0, wx + W * 0.12f, floor(), p);
        }
        c.save();
        c.translate(-cam, 0);
        p.setColor(GROUND[area]);
        c.drawRect(cam, floor(), cam + W, H, p);
        p.setColor(0x33FFFFFF);
        c.drawRect(cam, floor(), cam + W, floor() + H * 0.01f, p);
        for (int i = 0; i < nPlat; i++) {
            float[] pf = plats[i];
            if (pf[0] > cam + W || pf[0] + pf[2] < cam) continue;
            p.setColor(0xFF78350F);
            c.drawRoundRect(pf[0], pf[1], pf[0] + pf[2], pf[1] + H * 0.025f, H * 0.01f, H * 0.01f, p);
            p.setColor(0xFF65A30D);
            c.drawRect(pf[0], pf[1], pf[0] + pf[2], pf[1] + H * 0.008f, p);
        }
        if (crate[3] > 0) {
            p.setColor(0xFFA16207);
            c.drawRect(crate[0] - H * 0.03f, crate[1] - H * 0.06f, crate[0] + H * 0.03f, crate[1], p);
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.035f);
            a.tx.setColor(0xFFFFFFFF);
            c.drawText(crate[2] == 1 ? "S" : crate[2] == 2 ? "L" : "R", crate[0], crate[1] - H * 0.018f, a.tx);
        }
        for (float[] e : en) if (e[5] > 0) robot(c, p, e, t);
        for (int i = 0; i < bul.length; i++) {
            float[] b = bul[i];
            if (b[4] <= 0) continue;
            if (b[5] < 0) { p.setColor(0xFFF87171); c.drawCircle(b[0], b[1], H * 0.009f, p); }
            else {
                p.setColor(laser[i] ? 0xFF22D3EE : Party.COLORS[(int) b[5]]);
                c.drawRect(b[0] - H * (laser[i] ? 0.03f : 0.012f), b[1] - H * 0.004f, b[0] + H * (laser[i] ? 0.03f : 0.012f), b[1] + H * 0.004f, p);
            }
        }
        for (Arked4.P pl : a.ps) if (pl.on && pl.lives > 0) hero(c, p, pl, t);
        c.restore();
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            String g = gun[pl.seat] == 1 ? " S" : gun[pl.seat] == 2 ? " L" : gun[pl.seat] == 3 ? " R" : "";
            a.hudBox(c, pl.seat, Arked4.hearts(pl.lives) + "  " + pl.score + g);
        }
        // progress through the area
        p.setColor(0x55000000);
        c.drawRect(W * 0.3f, H * 0.075f, W * 0.7f, H * 0.085f, p);
        p.setColor(0xFFFDE047);
        c.drawRect(W * 0.3f, H * 0.075f, W * 0.3f + W * 0.4f * Math.min(1, cam / (W * (LEN - 1))), H * 0.085f, p);
        a.tx.setTextAlign(Paint.Align.RIGHT);
        a.tx.setTextSize(H * 0.026f);
        a.tx.setColor(0xFFFFFFFF);
        c.drawText("Kawasan " + (area + 1) + "/5: " + AREA[area], W * 0.99f, H * 0.045f, a.tx);
        if (banner > 0) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.07f);
            a.tx.setColor(Color.argb((int) (255 * Math.min(1, banner)), 253, 224, 71));
            c.drawText(boss ? "ROBOT JENTOLAK!" : AREA[area].toUpperCase(), W / 2, H * 0.35f, a.tx);
        }
    }

    private void hero(Canvas c, Paint p, Arked4.P pl, float t) {
        int i = pl.seat;
        if (pl.inv > 0 && ((int) (pl.inv * 10)) % 2 == 0) return;
        float H = a.H, s = H * 0.1f, x = pl.x, y = pl.y, dir = pl.face ? 1 : -1;
        Badge.ring(c, i, x, y, s * 0.3f, t);
        float walk = pl.ground && pl.vx != 0 ? (float) Math.sin(t * 14) * s * 0.12f : 0;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.1f);
        p.setColor(0xFF1F2937);
        c.drawLine(x, y - s * 0.4f, x - s * 0.12f + walk, y, p);
        c.drawLine(x, y - s * 0.4f, x + s * 0.12f - walk, y, p);
        p.setColor(Party.COLORS[i]);
        c.drawRoundRect(x - s * 0.15f, y - s * 0.85f, x + s * 0.15f, y - s * 0.35f, s * 0.06f, s * 0.06f, p);
        p.setColor(0xFF374151);   // the water blaster
        c.drawRect(x, y - s * 0.7f, x + dir * s * 0.45f, y - s * 0.6f, p);
        p.setColor(0xFFD6A77A);
        c.drawCircle(x, y - s * 0.98f, s * 0.14f, p);
        p.setColor(0xFF166534);   // a ranger's hat
        c.drawRect(x - s * 0.2f, y - s * 1.08f, x + s * 0.2f, y - s * 1.04f, p);
        c.drawRect(x - s * 0.12f, y - s * 1.18f, x + s * 0.12f, y - s * 1.06f, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        a.label(c, pl, x, y - s * 1.25f);
    }

    private void robot(Canvas c, Paint p, float[] e, float t) {
        float H = a.H, x = e[0], y = e[1];
        if (e[3] == 2) {   // the bulldozer
            float s = H * 0.2f;
            p.setColor(0xFFCA8A04);
            c.drawRoundRect(x - s * 0.7f, y - s, x + s * 0.7f, y - s * 0.25f, s * 0.08f, s * 0.08f, p);
            p.setColor(0xFF1F2937);
            c.drawRoundRect(x - s * 0.75f, y - s * 0.3f, x + s * 0.75f, y, s * 0.15f, s * 0.15f, p);
            p.setColor(0xFF9CA3AF);
            c.drawRect(x - s * 1.0f, y - s * 0.7f, x - s * 0.8f, y - s * 0.05f, p);   // the blade
            p.setColor((int) (t * 4) % 2 == 0 ? 0xFFEF4444 : 0xFF7F1D1D);
            c.drawCircle(x - s * 0.3f, y - s * 0.75f, s * 0.08f, p);
            c.drawCircle(x + s * 0.0f, y - s * 0.75f, s * 0.08f, p);
            p.setColor(0x88000000);
            c.drawRect(x - s * 0.6f, y - s * 1.15f, x + s * 0.6f, y - s * 1.08f, p);
            p.setColor(0xFFF43F5E);
            c.drawRect(x - s * 0.6f, y - s * 1.15f, x - s * 0.6f + s * 1.2f * e[2] / e[7], y - s * 1.08f, p);
            return;
        }
        float s = H * 0.07f;
        if (e[3] == 1) {   // a drone with rotors
            p.setColor(0xFF64748B);
            c.drawRoundRect(x - s * 0.6f, y - s * 0.3f, x + s * 0.6f, y + s * 0.2f, s * 0.15f, s * 0.15f, p);
            p.setColor(0xAAFFFFFF);
            float w = (float) Math.abs(Math.sin(t * 40)) * s * 0.5f;
            c.drawRect(x - s * 0.7f - w, y - s * 0.45f, x - s * 0.7f + w, y - s * 0.38f, p);
            c.drawRect(x + s * 0.7f - w, y - s * 0.45f, x + s * 0.7f + w, y - s * 0.38f, p);
            p.setColor(0xFFEF4444);
            c.drawCircle(x - s * 0.2f, y, s * 0.1f, p);
            return;
        }
        float step = (float) Math.sin(e[4] * 8) * s * 0.15f;
        p.setColor(0xFF475569);
        c.drawRect(x - s * 0.35f + step, y - s * 0.4f, x - s * 0.15f + step, y, p);
        c.drawRect(x + s * 0.15f - step, y - s * 0.4f, x + s * 0.35f - step, y, p);
        p.setColor(0xFF94A3B8);
        c.drawRoundRect(x - s * 0.45f, y - s * 1.2f, x + s * 0.45f, y - s * 0.4f, s * 0.12f, s * 0.12f, p);
        p.setColor(0xFFEF4444);
        c.drawRect(x - s * 0.3f, y - s, x + s * 0.1f, y - s * 0.85f, p);
        p.setColor(0xFF9CA3AF);   // a saw arm
        c.drawCircle(x - s * 0.6f, y - s * 0.7f, s * 0.2f + (float) Math.sin(t * 30) * s * 0.02f, p);
    }
}
