package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Hujan Buah: each player has a board where pairs of fruit fall. Four or more of the same fruit touching are picked;
 * chains score more, and big picks drop durian husks onto the others' boards. The speed goes up a level every 30 s.
 * The last board still standing wins (playing alone, the TV plays one board against you).
 */
final class ModBuah implements Arked4.Mod {
    private static final int COLS = 6, ROWS = 12, KINDS = 5, HUSK = 9;
    private static final int[] FRUIT = {0xFFEF4444, 0xFFFACC15, 0xFF8B5CF6, 0xFF22C55E, 0xFFF97316};
    private static final int[] OX = {0, 1, 0, -1}, OY = {-1, 0, 1, 0};   // where the second fruit sits, by rotation

    private final Arked4 a;
    private final int[][][] grid = new int[4][ROWS][COLS];   // 0 empty, 1..5 fruit, 9 husk
    private final int[] px = new int[4], py = new int[4], rot = new int[4], f1 = new int[4], f2 = new int[4], nx1 = new int[4], nx2 = new int[4];
    private final float[] fall = new float[4], moveT = new float[4], lastDir = new float[4], botT = new float[4], flash = new float[4];
    private final int[] pending = new int[4], botCol = new int[4], botRot = new int[4], chain = new int[4];
    private final boolean[] dead = new boolean[4], settling = new boolean[4];
    private final boolean[][] seen = new boolean[ROWS][COLS];
    private final int[] stackX = new int[ROWS * COLS], stackY = new int[ROWS * COLS];
    private int level = 1;
    private final RectF r = new RectF();

    ModBuah(Arked4 a) { this.a = a; }

    @Override public void build() {
        for (Arked4.P pl : a.ps) {
            int i = pl.seat;
            for (int[] row : grid[i]) java.util.Arrays.fill(row, 0);
            dead[i] = !pl.on;
            pending[i] = 0;
            nx1[i] = 1 + a.rnd.nextInt(KINDS);
            nx2[i] = 1 + a.rnd.nextInt(KINDS);
            spawn(i);
        }
        level = 1;
    }

    private void spawn(int i) {
        f1[i] = nx1[i]; f2[i] = nx2[i];
        nx1[i] = 1 + a.rnd.nextInt(KINDS); nx2[i] = 1 + a.rnd.nextInt(KINDS);
        px[i] = 2; py[i] = 1; rot[i] = 0; fall[i] = 0; botT[i] = -1;
        if (grid[i][1][2] != 0 || grid[i][0][2] != 0) {
            dead[i] = true;
            a.sound.sfx(GameMusic.SFX_WRONG);
            a.party.buzz(i, 300);
        }
    }

    private boolean fits(int i, int x, int y, int r) {
        int x2 = x + OX[r], y2 = y + OY[r];
        return free(i, x, y) && free(i, x2, y2);
    }

    private boolean free(int i, int x, int y) {
        if (x < 0 || x >= COLS || y >= ROWS) return false;
        return y < 0 || grid[i][y][x] == 0;
    }

    @Override public void update(float dt) {
        if (a.time > level * 30) { level++; a.sound.sfx(GameMusic.SFX_OK); }
        float speed = 1.2f + level * 0.5f;   // rows per second
        int alive = 0, last = -1;
        for (Arked4.P pl : a.ps) {
            int i = pl.seat;
            if (!pl.on || dead[i]) continue;
            alive++;
            last = i;
            if (flash[i] > 0) flash[i] -= dt;
            if (pl.bot) botPlay(i, dt);
            else {
                float dx = a.ix(pl);
                int dir = dx > 0.4f ? 1 : dx < -0.4f ? -1 : 0;
                moveT[i] -= dt;
                if (dir != 0 && (dir != lastDir[i] || moveT[i] <= 0)) {
                    if (fits(i, px[i] + dir, py[i], rot[i])) px[i] += dir;
                    moveT[i] = dir != lastDir[i] ? 0.22f : 0.08f;
                }
                lastDir[i] = dir;
                if (a.pressA(pl)) turn(i);
                if (a.iy(pl) > 0.5f) fall[i] += dt * 14;   // ▼ drops fast
            }
            fall[i] += dt * speed;
            while (fall[i] >= 1) {
                fall[i] -= 1;
                if (fits(i, px[i], py[i] + 1, rot[i])) py[i]++;
                else { land(i); break; }
            }
        }
        int on = 0;
        for (Arked4.P pl : a.ps) if (pl.on) on++;
        if (on > 1 && alive <= 1) a.finish(last < 0 ? "Seri!" : a.who(a.ps[last]) + " juara Hujan Buah! 🍎 (tahap " + level + ")");
        else if (on == 1 && alive == 0) a.finish("Papan penuh pada tahap " + level + ". Cuba lagi!");
    }

    private void turn(int i) {
        int r2 = (rot[i] + 1) % 4;
        if (fits(i, px[i], py[i], r2)) rot[i] = r2;
        else if (fits(i, px[i] - 1, py[i], r2)) { px[i]--; rot[i] = r2; }
        else if (fits(i, px[i] + 1, py[i], r2)) { px[i]++; rot[i] = r2; }
    }

    private void land(int i) {
        put(i, px[i], py[i], f1[i]);
        put(i, px[i] + OX[rot[i]], py[i] + OY[rot[i]], f2[i]);
        gravity(i);
        chain[i] = 0;
        int cleared;
        while ((cleared = clear(i)) > 0) {
            chain[i]++;
            a.ps[i].score += cleared * 10 * chain[i];
            a.sound.sfx(chain[i] > 1 ? GameMusic.SFX_WIN : GameMusic.SFX_COIN);
            flash[i] = 0.3f;
            gravity(i);
            int send = (cleared - 3) + (chain[i] - 1) * 3;   // big picks and chains annoy the others
            if (send > 0) for (Arked4.P o : a.ps) if (o.on && o.seat != i && !dead[o.seat]) pending[o.seat] += send;
        }
        if (pending[i] > 0) {   // husks fall onto the board
            int n = Math.min(pending[i], COLS * 2);
            pending[i] -= n;
            for (int k = 0; k < n; k++) {
                int x = k % COLS;
                for (int y = ROWS - 1; y >= 0; y--) if (grid[i][y][x] == 0) {
                    boolean top = true;
                    for (int yy = y - 1; yy >= 0; yy--) if (grid[i][yy][x] != 0) top = false;
                    if (top) { grid[i][y][x] = HUSK; break; }
                }
            }
            a.party.buzz(i, 120);
        }
        spawn(i);
    }

    private void put(int i, int x, int y, int f) {
        if (y >= 0 && y < ROWS && x >= 0 && x < COLS) grid[i][y][x] = f;
    }

    private void gravity(int i) {
        for (int x = 0; x < COLS; x++) {
            int w = ROWS - 1;
            for (int y = ROWS - 1; y >= 0; y--) if (grid[i][y][x] != 0) { int v = grid[i][y][x]; grid[i][y][x] = 0; grid[i][w--][x] = v; }
        }
    }

    /** Clears every group of 4+ same fruit (and husks touching them); returns how many fruit went. */
    private int clear(int i) {
        int total = 0;
        for (boolean[] row : seen) java.util.Arrays.fill(row, false);
        for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLS; x++) {
            int f = grid[i][y][x];
            if (f == 0 || f == HUSK || seen[y][x]) continue;
            int n = group(i, x, y, f);
            if (n >= 4) {
                total += n;
                for (int k = 0; k < n; k++) {
                    int gx = stackX[k], gy = stackY[k];
                    grid[i][gy][gx] = 0;
                    for (int d = 0; d < 4; d++) {
                        int hx = gx + OX[d], hy = gy + OY[d];
                        if (hx >= 0 && hx < COLS && hy >= 0 && hy < ROWS && grid[i][hy][hx] == HUSK) grid[i][hy][hx] = 0;
                    }
                }
            }
        }
        return total;
    }

    private int group(int i, int x0, int y0, int f) {
        int n = 0, head = 0;
        stackX[n] = x0; stackY[n] = y0; n++;
        seen[y0][x0] = true;
        while (head < n) {
            int x = stackX[head], y = stackY[head];
            head++;
            for (int d = 0; d < 4; d++) {
                int nx = x + OX[d], ny = y + OY[d];
                if (nx < 0 || nx >= COLS || ny < 0 || ny >= ROWS || seen[ny][nx] || grid[i][ny][nx] != f) continue;
                seen[ny][nx] = true;
                stackX[n] = nx; stackY[n] = ny; n++;
            }
        }
        return n;
    }

    /** The TV: picks the column and turn that put each fruit next to the most of its kind, then goes there. */
    private void botPlay(int i, float dt) {
        if (botT[i] < 0) {
            int best = -1, bc = px[i], br = 0;
            for (int r = 0; r < 4; r++) for (int x = 0; x < COLS; x++) {
                int y = py[i];
                if (!fits(i, x, y, r)) continue;
                while (fits(i, x, y + 1, r)) y++;
                int v = touching(i, x, y, f1[i]) + touching(i, x + OX[r], y + OY[r], f2[i]) + y * 2 + a.rnd.nextInt(3);
                if (v > best) { best = v; bc = x; br = r; }
            }
            botCol[i] = bc;
            botRot[i] = br;
            botT[i] = 0;
        }
        botT[i] += dt;
        if (botT[i] < 0.25f) return;
        botT[i] = 0.01f;
        if (rot[i] != botRot[i]) turn(i);
        else if (px[i] < botCol[i] && fits(i, px[i] + 1, py[i], rot[i])) px[i]++;
        else if (px[i] > botCol[i] && fits(i, px[i] - 1, py[i], rot[i])) px[i]--;
        else fall[i] += 0.5f;
    }

    private int touching(int i, int x, int y, int f) {
        int n = 0;
        for (int d = 0; d < 4; d++) {
            int nx = x + OX[d], ny = y + OY[d];
            if (nx >= 0 && nx < COLS && ny >= 0 && ny < ROWS && grid[i][ny][nx] == f) n += 3;
        }
        return n;
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        p.setColor(0xFF0F172A);
        c.drawRect(0, 0, W, H, p);
        int n = 0;
        for (Arked4.P pl : a.ps) if (pl.on) n++;
        float slot = W / Math.max(1, n);
        float cs = Math.min(H * 0.74f / ROWS, slot * 0.6f / COLS);
        int k = 0;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int i = pl.seat;
            float bx = slot * k + (slot - cs * COLS) / 2, by = H * 0.16f;
            r.set(bx - cs * 0.2f, by - cs * 0.2f, bx + cs * (COLS + 0.2f), by + cs * (ROWS + 0.2f));
            p.setColor(Party.COLORS[i]);
            c.drawRoundRect(r, cs * 0.4f, cs * 0.4f, p);
            r.inset(cs * 0.12f, cs * 0.12f);
            p.setColor(flash[i] > 0 ? 0xFF334155 : 0xFF1E293B);
            c.drawRoundRect(r, cs * 0.3f, cs * 0.3f, p);
            for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLS; x++) {
                int f = grid[i][y][x];
                if (f != 0) fruit(c, p, bx + (x + 0.5f) * cs, by + (y + 0.5f) * cs, cs * 0.45f, f);
            }
            if (!dead[i]) {
                float off = Math.min(1, fall[i]) * cs;
                fruit(c, p, bx + (px[i] + 0.5f) * cs, by + (py[i] + 0.5f) * cs + off, cs * 0.45f, f1[i]);
                fruit(c, p, bx + (px[i] + OX[rot[i]] + 0.5f) * cs, by + (py[i] + OY[rot[i]] + 0.5f) * cs + off, cs * 0.45f, f2[i]);
                // next pair
                fruit(c, p, bx + cs * (COLS + 0.9f), by + cs * 0.6f, cs * 0.3f, nx2[i]);
                fruit(c, p, bx + cs * (COLS + 0.9f), by + cs * 1.25f, cs * 0.3f, nx1[i]);
            } else {
                a.tx.setTextAlign(Paint.Align.CENTER);
                a.tx.setTextSize(cs * 0.9f);
                a.tx.setColor(0xFFF87171);
                c.drawText("PENUH!", bx + cs * COLS / 2, by + cs * ROWS / 2, a.tx);
            }
            if (pending[i] > 0) {
                a.tx.setTextAlign(Paint.Align.LEFT);
                a.tx.setTextSize(cs * 0.45f);
                a.tx.setColor(0xFFFCA5A5);
                c.drawText("+" + pending[i] + " kulit", bx, by - cs * 0.35f, a.tx);
            }
            Badge.pin(c, i, pl.bot, bx + cs * COLS / 2, by - cs * 0.6f, H * 0.028f, t);
            a.hudBox(c, i, (pl.bot ? "TV  " : "") + pl.score + (chain[i] > 1 ? "  x" + chain[i] + "!" : ""));
            k++;
        }
        a.tx.setTextAlign(Paint.Align.CENTER);
        a.tx.setTextSize(H * 0.03f);
        a.tx.setColor(0xFFFDE68A);
        c.drawText("Tahap " + level, W / 2, H * 0.97f, a.tx);
    }

    private static void fruit(Canvas c, Paint p, float x, float y, float r, int f) {
        if (f == HUSK) {   // a spiky durian husk
            p.setColor(0xFF65A30D);
            c.drawCircle(x, y, r, p);
            p.setColor(0xFF3F6212);
            for (int k = 0; k < 6; k++) {
                double an = k * Math.PI / 3;
                c.drawCircle(x + (float) Math.cos(an) * r * 0.55f, y + (float) Math.sin(an) * r * 0.55f, r * 0.2f, p);
            }
            return;
        }
        int col = FRUIT[f - 1];
        p.setColor(col);
        c.drawCircle(x, y, r, p);
        p.setColor(Color.argb(110, 255, 255, 255));
        c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.3f, p);
        p.setColor(0xFF15803D);   // a leaf, and a mark per fruit so colour-blind players can tell them apart
        c.drawOval(x, y - r * 1.1f, x + r * 0.5f, y - r * 0.75f, p);
        p.setColor(0x66000000);
        switch (f) {
            case 1: c.drawCircle(x, y + r * 0.2f, r * 0.15f, p); break;
            case 2: c.drawRect(x - r * 0.4f, y, x + r * 0.4f, y + r * 0.15f, p); break;
            case 3: for (int k = 0; k < 3; k++) c.drawCircle(x - r * 0.3f + k * r * 0.3f, y + r * 0.25f, r * 0.1f, p); break;
            case 4: c.drawRect(x - r * 0.08f, y - r * 0.3f, x + r * 0.08f, y + r * 0.5f, p); break;
            default: c.drawCircle(x, y + r * 0.1f, r * 0.35f, p); p.setColor(col); c.drawCircle(x, y + r * 0.1f, r * 0.22f, p);
        }
    }
}
