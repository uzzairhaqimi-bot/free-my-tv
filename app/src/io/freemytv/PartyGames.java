package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fork extra: four original party games for 1 to 4 players (Retro Arked). Seats without a person are played by a
 * simple bot when fewer than two people joined. Every game reads the same input: a stick (x, y in -1..1) and the
 * A and B buttons (counted presses), and draws neon shapes on one Canvas.
 */
final class PartyGames {
    static final String[] NAMES = {"Arena Bom", "Tolak Jatuh", "Arena Laser", "Kart Neon"};
    static final String[] HINTS = {
            "Letak bom (A), pecahkan kotak, tinggal seorang menang",
            "Tolak lawan keluar pentas, A untuk meluru",
            "Tembak (A), peluru melantun, 3 nyawa",
            "Tahan A untuk pecut, B brek, 3 pusingan"};

    /** One seat in a game: inputs from a phone, the remote, or a bot. */
    static final class Seat {
        final int id, color;
        final boolean bot;
        float x, y;
        boolean aHeld, bHeld;
        int aTaps, bTaps;
        boolean alive = true;

        Seat(int id, int color, boolean bot) {
            this.id = id;
            this.color = color;
            this.bot = bot;
        }
    }

    abstract static class Mini {
        final Random rnd = new Random();
        final List<Seat> seats = new ArrayList<>();
        float W, H;
        boolean over;
        int winner = -1;           // seat id, -1 for none
        String result = "";
        Party party;

        void start(List<Seat> s, float w, float h, Party p) {
            seats.clear();
            seats.addAll(s);
            W = w;
            H = h;
            party = p;
            over = false;
            winner = -1;
            reset();
        }

        abstract void reset();
        abstract void ai(Seat s);
        abstract void update(float dt);
        abstract void draw(Canvas c, Paint p, Paint glow);

        void buzz(Seat s, int ms) {
            if (party != null && !s.bot) party.buzz(s.id, ms);
        }

        /** Ends the round when one (or none) is left alive. */
        void checkLastStanding() {
            int alive = 0;
            Seat last = null;
            for (Seat s : seats) if (s.alive) { alive++; last = s; }
            if (alive <= 1) {
                over = true;
                winner = last == null ? -1 : last.id;
                result = last == null ? "Seri!" : "Pemain " + (last.id + 1) + " menang!";
            }
        }

        static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
    }

    static Mini create(int i) {
        switch (i) {
            case 0: return new Bom();
            case 1: return new Sumo();
            case 2: return new Laser();
            default: return new Kart();
        }
    }

    // =================================================================== Arena Bom

    static final class Bom extends Mini {
        static final int GW = 15, GH = 11;
        static final int EMPTY = 0, WALL = 1, BOX = 2, P_RANGE = 3, P_BOMB = 4;
        final int[][] g = new int[GW][GH];
        final float[] px = new float[4], py = new float[4];
        final int[] range = new int[4], maxBombs = new int[4], live = new int[4];
        final List<float[]> bombs = new ArrayList<>();   // {x, y, timer, owner}
        final List<float[]> fire = new ArrayList<>();    // {x, y, timer}
        float cell, ox, oy;

        @Override void reset() {
            cell = Math.min(W / GW, H * 0.9f / GH);
            ox = (W - cell * GW) / 2;
            oy = (H - cell * GH) / 2 + H * 0.02f;
            for (int x = 0; x < GW; x++) for (int y = 0; y < GH; y++) {
                boolean edge = x == 0 || y == 0 || x == GW - 1 || y == GH - 1;
                g[x][y] = edge || (x % 2 == 0 && y % 2 == 0) ? WALL : (rnd.nextFloat() < 0.55f ? BOX : EMPTY);
            }
            int[][] corners = {{1, 1}, {GW - 2, GH - 2}, {GW - 2, 1}, {1, GH - 2}};
            for (int i = 0; i < seats.size(); i++) {
                int cx = corners[i][0], cy = corners[i][1];
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                    int x = cx + dx, y = cy + dy;
                    if (g[x][y] == BOX && (dx == 0 || dy == 0)) g[x][y] = EMPTY;
                }
                g[cx][cy] = EMPTY;
                px[i] = cx + 0.5f;
                py[i] = cy + 0.5f;
                range[i] = 2;
                maxBombs[i] = 1;
                live[i] = 0;
            }
            bombs.clear();
            fire.clear();
        }

        boolean solid(int x, int y) {
            if (x < 0 || y < 0 || x >= GW || y >= GH) return true;
            if (g[x][y] == WALL || g[x][y] == BOX) return true;
            return false;
        }

        boolean bombAt(int x, int y) {
            for (float[] b : bombs) if ((int) b[0] == x && (int) b[1] == y) return true;
            return false;
        }

        boolean danger(int x, int y) {
            for (float[] f : fire) if ((int) f[0] == x && (int) f[1] == y) return true;
            for (float[] b : bombs) {
                int bx = (int) b[0], by = (int) b[1];
                if ((bx == x && Math.abs(by - y) <= range[(int) b[3]]) || (by == y && Math.abs(bx - x) <= range[(int) b[3]])) return true;
            }
            return false;
        }

        @Override void ai(Seat s) {
            int i = seats.indexOf(s);
            int cx = (int) px[i], cy = (int) py[i];
            s.x = s.y = 0;
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            if (danger(cx, cy)) {   // run to the first safe free neighbour
                for (int[] d : dirs) {
                    int nx = cx + d[0], ny = cy + d[1];
                    if (!solid(nx, ny) && !bombAt(nx, ny) && !danger(nx, ny)) { s.x = d[0]; s.y = d[1]; return; }
                }
                for (int[] d : dirs) if (!solid(cx + d[0], cy + d[1]) && !bombAt(cx + d[0], cy + d[1])) { s.x = d[0]; s.y = d[1]; return; }
                return;
            }
            boolean nearBox = false;
            for (int[] d : dirs) if (g[cx + d[0]][cy + d[1]] == BOX) nearBox = true;
            for (Seat o : seats) {
                if (o == s || !o.alive) continue;
                int oi = seats.indexOf(o);
                if (Math.abs((int) px[oi] - cx) + Math.abs((int) py[oi] - cy) <= 2) nearBox = true;
            }
            if (nearBox && live[i] < maxBombs[i] && rnd.nextFloat() < 0.05f) { s.aTaps++; return; }
            if (rnd.nextFloat() < 0.6f) {
                int[] d = dirs[(int) ((System.nanoTime() / 900_000_000L + s.id) % 4)];
                if (!solid(cx + d[0], cy + d[1]) && !danger(cx + d[0], cy + d[1])) { s.x = d[0]; s.y = d[1]; }
            }
        }

        @Override void update(float dt) {
            float speed = 3.6f * dt;
            for (int i = 0; i < seats.size(); i++) {
                Seat s = seats.get(i);
                if (!s.alive) continue;
                float mx = Math.abs(s.x) > Math.abs(s.y) ? Math.signum(s.x) : 0;
                float my = mx == 0 ? Math.signum(s.y) : 0;
                if (Math.abs(s.x) < 0.3f && Math.abs(s.y) < 0.3f) mx = my = 0;
                // Slide along the lane: snap the other axis toward the centre of the cell.
                if (mx != 0) {
                    float target = (int) py[i] + 0.5f;
                    py[i] += clamp(target - py[i], -speed, speed);
                    float nx = px[i] + mx * speed;
                    int tx = (int) (nx + mx * 0.45f);
                    if (!solid(tx, (int) py[i]) && !(bombAt(tx, (int) py[i]) && tx != (int) px[i])) px[i] = nx;
                    else px[i] += clamp((int) px[i] + 0.5f - px[i], -speed, speed);
                } else if (my != 0) {
                    float target = (int) px[i] + 0.5f;
                    px[i] += clamp(target - px[i], -speed, speed);
                    float ny = py[i] + my * speed;
                    int ty = (int) (ny + my * 0.45f);
                    if (!solid((int) px[i], ty) && !(bombAt((int) px[i], ty) && ty != (int) py[i])) py[i] = ny;
                    else py[i] += clamp((int) py[i] + 0.5f - py[i], -speed, speed);
                }
                int cx = (int) px[i], cy = (int) py[i];
                if (g[cx][cy] == P_RANGE) { range[i] = Math.min(6, range[i] + 1); g[cx][cy] = EMPTY; buzz(s, 30); }
                if (g[cx][cy] == P_BOMB) { maxBombs[i] = Math.min(4, maxBombs[i] + 1); g[cx][cy] = EMPTY; buzz(s, 30); }
                while (s.aTaps > 0) {
                    s.aTaps--;
                    if (live[i] < maxBombs[i] && !bombAt(cx, cy)) {
                        bombs.add(new float[]{cx, cy, 2.2f, i});
                        live[i]++;
                    }
                }
            }
            for (int k = bombs.size() - 1; k >= 0; k--) {
                float[] b = bombs.get(k);
                b[2] -= dt;
                if (b[2] <= 0) explode(k);
            }
            for (int k = fire.size() - 1; k >= 0; k--) {
                float[] f = fire.get(k);
                f[2] -= dt;
                if (f[2] <= 0) { fire.remove(k); continue; }
                for (int i = 0; i < seats.size(); i++) {
                    Seat s = seats.get(i);
                    if (s.alive && (int) px[i] == (int) f[0] && (int) py[i] == (int) f[1]) { s.alive = false; buzz(s, 300); }
                }
            }
            checkLastStanding();
        }

        void explode(int k) {
            float[] b = bombs.remove(k);
            int owner = (int) b[3], bx = (int) b[0], by = (int) b[1];
            live[owner]--;
            fire.add(new float[]{bx, by, 0.5f});
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : dirs) {
                for (int r = 1; r <= range[owner]; r++) {
                    int x = bx + d[0] * r, y = by + d[1] * r;
                    if (g[x][y] == WALL) break;
                    fire.add(new float[]{x, y, 0.5f});
                    if (g[x][y] == BOX) {
                        float roll = rnd.nextFloat();
                        g[x][y] = roll < 0.18f ? P_RANGE : roll < 0.32f ? P_BOMB : EMPTY;
                        break;
                    }
                    for (int j = bombs.size() - 1; j >= 0; j--) {   // chain reaction
                        float[] o = bombs.get(j);
                        if ((int) o[0] == x && (int) o[1] == y) o[2] = Math.min(o[2], 0.05f);
                    }
                }
            }
        }

        @Override void draw(Canvas c, Paint p, Paint glow) {
            RectF r = new RectF();
            for (int x = 0; x < GW; x++) for (int y = 0; y < GH; y++) {
                r.set(ox + x * cell + 2, oy + y * cell + 2, ox + (x + 1) * cell - 2, oy + (y + 1) * cell - 2);
                int v = g[x][y];
                if (v == WALL) { p.setColor(0xFF2A2560); c.drawRoundRect(r, cell * 0.15f, cell * 0.15f, p); }
                else if (v == BOX) { p.setColor(0xFF6D28D9); c.drawRoundRect(r, cell * 0.2f, cell * 0.2f, p); p.setColor(0x40FFFFFF); c.drawLine(r.left, r.top, r.right, r.bottom, p); }
                else if (v == P_RANGE || v == P_BOMB) {
                    glow.setColor(v == P_RANGE ? 0xFFFFB020 : 0xFF22D3EE);
                    glow.setShadowLayer(12, 0, 0, glow.getColor());
                    c.drawCircle(r.centerX(), r.centerY(), cell * 0.22f, glow);
                }
            }
            for (float[] f : fire) {
                glow.setColor(0xFFFF7A18);
                glow.setShadowLayer(18, 0, 0, 0xFFFF3B30);
                r.set(ox + f[0] * cell + 4, oy + f[1] * cell + 4, ox + (f[0] + 1) * cell - 4, oy + (f[1] + 1) * cell - 4);
                c.drawRoundRect(r, cell * 0.3f, cell * 0.3f, glow);
            }
            for (float[] b : bombs) {
                float pulse = 0.32f + 0.05f * (float) Math.sin(b[2] * 14);
                glow.setColor(0xFF111111);
                glow.setShadowLayer(14, 0, 0, 0xFFFF4FD8);
                c.drawCircle(ox + (b[0] + 0.5f) * cell, oy + (b[1] + 0.5f) * cell, cell * pulse, glow);
            }
            for (int i = 0; i < seats.size(); i++) {
                Seat s = seats.get(i);
                if (!s.alive) continue;
                glow.setColor(s.color);
                glow.setShadowLayer(16, 0, 0, s.color);
                c.drawCircle(ox + px[i] * cell, oy + py[i] * cell, cell * 0.36f, glow);
            }
        }
    }

    // =================================================================== Tolak Jatuh

    static final class Sumo extends Mini {
        final float[] x = new float[4], y = new float[4], vx = new float[4], vy = new float[4], cool = new float[4];
        float radius, time;

        @Override void reset() {
            radius = H * 0.42f;
            time = 0;
            for (int i = 0; i < seats.size(); i++) {
                double a = Math.PI * 2 * i / seats.size();
                x[i] = W / 2 + (float) Math.cos(a) * radius * 0.6f;
                y[i] = H / 2 + (float) Math.sin(a) * radius * 0.6f;
                vx[i] = vy[i] = cool[i] = 0;
            }
        }

        @Override void ai(Seat s) {
            int i = seats.indexOf(s);
            int t = -1;
            float best = Float.MAX_VALUE;
            for (int j = 0; j < seats.size(); j++) {
                if (j == i || !seats.get(j).alive) continue;
                float d = (x[j] - x[i]) * (x[j] - x[i]) + (y[j] - y[i]) * (y[j] - y[i]);
                if (d < best) { best = d; t = j; }
            }
            float dx = (t < 0 ? W / 2 : x[t]) - x[i], dy = (t < 0 ? H / 2 : y[t]) - y[i];
            float cx = W / 2 - x[i], cy = H / 2 - y[i];
            float edge = (float) Math.hypot(cx, cy) / radius;
            if (edge > 0.7f) { dx = cx; dy = cy; }   // back toward the middle
            float l = (float) Math.hypot(dx, dy) + 0.001f;
            s.x = dx / l;
            s.y = dy / l;
            if (t >= 0 && Math.sqrt(best) < H * 0.16f && cool[i] <= 0 && rnd.nextFloat() < 0.08f) s.aTaps++;
        }

        @Override void update(float dt) {
            time += dt;
            radius = Math.max(H * 0.18f, H * 0.42f - Math.max(0, time - 10) * H * 0.006f);   // the stage shrinks
            float pr = H * 0.045f, acc = H * 1.6f;
            for (int i = 0; i < seats.size(); i++) {
                Seat s = seats.get(i);
                if (!s.alive) continue;
                cool[i] -= dt;
                vx[i] += s.x * acc * dt;
                vy[i] += s.y * acc * dt;
                while (s.aTaps > 0) {
                    s.aTaps--;
                    if (cool[i] <= 0) {
                        float l = (float) Math.hypot(s.x, s.y);
                        if (l > 0.2f) { vx[i] += s.x / l * H * 1.1f; vy[i] += s.y / l * H * 1.1f; cool[i] = 1f; }
                    }
                }
                vx[i] *= (float) Math.pow(0.35, dt);
                vy[i] *= (float) Math.pow(0.35, dt);
            }
            for (int i = 0; i < seats.size(); i++) for (int j = i + 1; j < seats.size(); j++) {
                if (!seats.get(i).alive || !seats.get(j).alive) continue;
                float dx = x[j] - x[i], dy = y[j] - y[i], d = (float) Math.hypot(dx, dy);
                if (d < pr * 2 && d > 0.01f) {
                    float nx = dx / d, ny = dy / d;
                    float rel = (vx[i] - vx[j]) * nx + (vy[i] - vy[j]) * ny;
                    if (rel > 0) {
                        float imp = rel * 1.25f;
                        vx[i] -= imp * nx; vy[i] -= imp * ny;
                        vx[j] += imp * nx; vy[j] += imp * ny;
                        buzz(seats.get(i), 25);
                        buzz(seats.get(j), 25);
                    }
                    float push = (pr * 2 - d) / 2;
                    x[i] -= nx * push; y[i] -= ny * push;
                    x[j] += nx * push; y[j] += ny * push;
                }
            }
            for (int i = 0; i < seats.size(); i++) {
                Seat s = seats.get(i);
                if (!s.alive) continue;
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt;
                if (Math.hypot(x[i] - W / 2, y[i] - H / 2) > radius + pr * 0.3f) { s.alive = false; buzz(s, 300); }
            }
            checkLastStanding();
        }

        @Override void draw(Canvas c, Paint p, Paint glow) {
            glow.setStyle(Paint.Style.FILL);
            glow.setColor(0xFF1E1B4B);
            glow.setShadowLayer(30, 0, 0, 0xFF8B5CF6);
            c.drawCircle(W / 2, H / 2, radius, glow);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            p.setColor(0xFFA78BFA);
            c.drawCircle(W / 2, H / 2, radius, p);
            p.setColor(0x40A78BFA);
            c.drawCircle(W / 2, H / 2, radius * 0.5f, p);
            p.setStyle(Paint.Style.FILL);
            float pr = H * 0.045f;
            for (int i = 0; i < seats.size(); i++) {
                Seat s = seats.get(i);
                if (!s.alive) continue;
                glow.setColor(s.color);
                glow.setShadowLayer(cool[i] > 0.7f ? 30 : 16, 0, 0, s.color);
                c.drawCircle(x[i], y[i], pr, glow);
                p.setColor(0xCC000000);
                c.drawCircle(x[i] + vx[i] * 0.02f, y[i] + vy[i] * 0.02f, pr * 0.3f, p);
            }
        }
    }

    // =================================================================== Arena Laser

    static final class Laser extends Mini {
        final float[] x = new float[4], y = new float[4], ang = new float[4], hurt = new float[4];
        final int[] lives = new int[4];
        final List<float[]> shots = new ArrayList<>();   // {x, y, vx, vy, bounces, owner, life}
        final List<RectF> walls = new ArrayList<>();

        @Override void reset() {
            walls.clear();
            float t = H * 0.03f;
            walls.add(new RectF(0, 0, W, t));
            walls.add(new RectF(0, H - t, W, H));
            walls.add(new RectF(0, 0, t, H));
            walls.add(new RectF(W - t, 0, W, H));
            walls.add(new RectF(W * 0.47f, H * 0.18f, W * 0.53f, H * 0.38f));
            walls.add(new RectF(W * 0.47f, H * 0.62f, W * 0.53f, H * 0.82f));
            walls.add(new RectF(W * 0.2f, H * 0.47f, W * 0.34f, H * 0.53f));
            walls.add(new RectF(W * 0.66f, H * 0.47f, W * 0.8f, H * 0.53f));
            float[][] start = {{0.1f, 0.15f}, {0.9f, 0.85f}, {0.9f, 0.15f}, {0.1f, 0.85f}};
            for (int i = 0; i < seats.size(); i++) {
                x[i] = W * start[i][0];
                y[i] = H * start[i][1];
                ang[i] = (float) Math.atan2(H / 2 - y[i], W / 2 - x[i]);
                lives[i] = 3;
                hurt[i] = 0;
            }
            shots.clear();
        }

        boolean blocked(float px, float py, float r) {
            for (RectF w : walls) if (px + r > w.left && px - r < w.right && py + r > w.top && py - r < w.bottom) return true;
            return false;
        }

        @Override void ai(Seat s) {
            int i = seats.indexOf(s);
            int t = -1;
            float best = Float.MAX_VALUE;
            for (int j = 0; j < seats.size(); j++) {
                if (j == i || !seats.get(j).alive) continue;
                float d = (float) Math.hypot(x[j] - x[i], y[j] - y[i]);
                if (d < best) { best = d; t = j; }
            }
            if (t < 0) { s.x = s.y = 0; return; }
            float a = (float) Math.atan2(y[t] - y[i], x[t] - x[i]);
            float wob = (float) Math.sin(System.nanoTime() / 6e8 + s.id) * 0.9f;
            float move = best > H * 0.35f ? 1f : 0.35f;
            s.x = (float) Math.cos(a + wob) * move;
            s.y = (float) Math.sin(a + wob) * move;
            float diff = Math.abs((float) Math.atan2(Math.sin(a - ang[i]), Math.cos(a - ang[i])));
            if (diff < 0.25f && rnd.nextFloat() < 0.06f) s.aTaps++;
        }

        @Override void update(float dt) {
            float r = H * 0.035f, sp = H * 0.45f;
            for (int i = 0; i < seats.size(); i++) {
                Seat s = seats.get(i);
                hurt[i] -= dt;
                if (!s.alive) continue;
                float l = (float) Math.hypot(s.x, s.y);
                if (l > 0.25f) {
                    float target = (float) Math.atan2(s.y, s.x);
                    float diff = (float) Math.atan2(Math.sin(target - ang[i]), Math.cos(target - ang[i]));
                    ang[i] += clamp(diff, -6f * dt, 6f * dt);
                    float nx = x[i] + (float) Math.cos(ang[i]) * sp * Math.min(1, l) * dt;
                    float ny = y[i] + (float) Math.sin(ang[i]) * sp * Math.min(1, l) * dt;
                    if (!blocked(nx, y[i], r)) x[i] = nx;
                    if (!blocked(x[i], ny, r)) y[i] = ny;
                }
                while (s.aTaps > 0) {
                    s.aTaps--;
                    int mine = 0;
                    for (float[] b : shots) if ((int) b[5] == i) mine++;
                    if (mine < 3) {
                        float c = (float) Math.cos(ang[i]), sn = (float) Math.sin(ang[i]), v = H * 0.9f;
                        shots.add(new float[]{x[i] + c * r * 1.6f, y[i] + sn * r * 1.6f, c * v, sn * v, 0, i, 4f});
                    }
                }
            }
            for (int k = shots.size() - 1; k >= 0; k--) {
                float[] b = shots.get(k);
                b[6] -= dt;
                float nx = b[0] + b[2] * dt, ny = b[1] + b[3] * dt;
                boolean hx = blocked(nx, b[1], 3), hy = blocked(b[0], ny, 3);
                if (hx) { b[2] = -b[2]; b[4]++; } else b[0] = nx;
                if (hy) { b[3] = -b[3]; b[4]++; } else b[1] = ny;
                boolean gone = b[4] > 2 || b[6] <= 0;
                for (int i = 0; i < seats.size() && !gone; i++) {
                    Seat s = seats.get(i);
                    if (!s.alive || hurt[i] > 0) continue;
                    if (Math.hypot(b[0] - x[i], b[1] - y[i]) < r) {
                        gone = true;
                        lives[i]--;
                        hurt[i] = 1.2f;
                        buzz(s, 150);
                        if (lives[i] <= 0) { s.alive = false; buzz(s, 400); }
                    }
                }
                if (gone) shots.remove(k);
            }
            checkLastStanding();
        }

        @Override void draw(Canvas c, Paint p, Paint glow) {
            glow.setStyle(Paint.Style.FILL);
            for (RectF w : walls) {
                glow.setColor(0xFF312E81);
                glow.setShadowLayer(14, 0, 0, 0xFF6366F1);
                c.drawRect(w, glow);
            }
            float r = H * 0.035f;
            Path tri = new Path();
            for (int i = 0; i < seats.size(); i++) {
                Seat s = seats.get(i);
                if (!s.alive) continue;
                if (hurt[i] > 0 && ((int) (hurt[i] * 10)) % 2 == 0) continue;   // blink while hurt
                float c0 = (float) Math.cos(ang[i]), s0 = (float) Math.sin(ang[i]);
                tri.reset();
                tri.moveTo(x[i] + c0 * r * 1.5f, y[i] + s0 * r * 1.5f);
                tri.lineTo(x[i] - c0 * r + s0 * r, y[i] - s0 * r - c0 * r);
                tri.lineTo(x[i] - c0 * r - s0 * r, y[i] - s0 * r + c0 * r);
                tri.close();
                glow.setColor(s.color);
                glow.setShadowLayer(16, 0, 0, s.color);
                c.drawPath(tri, glow);
                p.setColor(s.color);
                for (int l = 0; l < lives[i]; l++) c.drawCircle(x[i] - r + l * r, y[i] + r * 1.9f, r * 0.18f, p);
            }
            for (float[] b : shots) {
                int col = seats.get((int) b[5]).color;
                glow.setColor(0xFFFFFFFF);
                glow.setShadowLayer(12, 0, 0, col);
                c.drawCircle(b[0], b[1], H * 0.009f, glow);
            }
        }
    }

    // =================================================================== Kart Neon

    static final class Kart extends Mini {
        static final int LAPS = 3, WP = 24;
        final float[] x = new float[4], y = new float[4], ang = new float[4], v = new float[4], boost = new float[4];
        final int[] next = new int[4], lap = new int[4];
        final float[] wx = new float[WP], wy = new float[WP];
        final boolean[] box = new boolean[WP];
        int finished;
        float rx, ry, width;

        @Override void reset() {
            rx = W * 0.38f;
            ry = H * 0.34f;
            width = H * 0.15f;
            for (int k = 0; k < WP; k++) {
                double a = Math.PI * 2 * k / WP;
                // a rounded, slightly pinched loop
                wx[k] = W / 2 + (float) Math.cos(a) * rx;
                wy[k] = H / 2 + (float) Math.sin(a) * ry * (1 - 0.25f * (float) Math.cos(2 * a));
                box[k] = k % 6 == 3;
            }
            for (int i = 0; i < seats.size(); i++) {
                x[i] = wx[0] - (i % 2) * width * 0.4f + width * 0.2f;
                y[i] = wy[0] - width * 0.3f + (i / 2) * width * 0.35f + (i % 2) * width * 0.2f;
                ang[i] = (float) Math.PI / 2;
                v[i] = 0;
                boost[i] = 0;
                next[i] = 1;
                lap[i] = 0;
            }
            finished = 0;
        }

        float offTrack(float px, float py) {
            float best = Float.MAX_VALUE;
            for (int k = 0; k < WP; k++) {
                int n = (k + 1) % WP;
                float ax = wx[k], ay = wy[k], bx = wx[n], by = wy[n];
                float t = clamp(((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / ((bx - ax) * (bx - ax) + (by - ay) * (by - ay)), 0, 1);
                float dx = px - (ax + t * (bx - ax)), dy = py - (ay + t * (by - ay));
                best = Math.min(best, dx * dx + dy * dy);
            }
            return (float) Math.sqrt(best);
        }

        @Override void ai(Seat s) {
            int i = seats.indexOf(s);
            int n = (next[i] + 1) % WP;
            float a = (float) Math.atan2(wy[n] - y[i], wx[n] - x[i]);
            s.x = (float) Math.cos(a);
            s.y = (float) Math.sin(a);
            s.aHeld = true;
        }

        @Override void update(float dt) {
            float maxV = H * 0.75f;
            for (int i = 0; i < seats.size(); i++) {
                Seat s = seats.get(i);
                if (!s.alive) continue;   // "alive" here means still racing
                float l = (float) Math.hypot(s.x, s.y);
                if (l > 0.3f) {   // steer toward the stick's direction
                    float target = (float) Math.atan2(s.y, s.x);
                    float diff = (float) Math.atan2(Math.sin(target - ang[i]), Math.cos(target - ang[i]));
                    ang[i] += clamp(diff, -3.2f * dt, 3.2f * dt);
                }
                boolean gas = s.aHeld || (s.bot && true);
                boost[i] -= dt;
                float top = maxV * (boost[i] > 0 ? 1.5f : 1f);
                if (offTrack(x[i], y[i]) > width / 2) top *= 0.45f;
                if (gas) v[i] += H * 0.9f * dt; else v[i] -= H * 0.5f * dt;
                if (s.bHeld) v[i] -= H * 1.6f * dt;
                v[i] = clamp(v[i], -maxV * 0.3f, top);
                if (v[i] > top) v[i] = top;
                x[i] += (float) Math.cos(ang[i]) * v[i] * dt;
                y[i] += (float) Math.sin(ang[i]) * v[i] * dt;
                x[i] = clamp(x[i], 0, W);
                y[i] = clamp(y[i], 0, H);
                int n = next[i];
                if (Math.hypot(x[i] - wx[n], y[i] - wy[n]) < width * 0.9f) {
                    if (box[n]) { boost[i] = 1.4f; buzz(s, 40); }
                    if (n == 0) {
                        lap[i]++;
                        buzz(s, 80);
                        if (lap[i] >= LAPS) {
                            s.alive = false;
                            if (finished++ == 0) {
                                over = true;
                                winner = s.id;
                                result = "Pemain " + (s.id + 1) + " menang!";
                            }
                        }
                    }
                    next[i] = (n + 1) % WP;
                }
            }
        }

        @Override void draw(Canvas c, Paint p, Paint glow) {
            Path track = new Path();
            track.moveTo(wx[0], wy[0]);
            for (int k = 1; k <= WP; k++) track.lineTo(wx[k % WP], wy[k % WP]);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(width + 10);
            p.setColor(0xFF22D3EE);
            p.setShadowLayer(20, 0, 0, 0xFF22D3EE);
            c.drawPath(track, p);
            p.setShadowLayer(0, 0, 0, 0);
            p.setStrokeWidth(width);
            p.setColor(0xFF111827);
            c.drawPath(track, p);
            p.setStrokeWidth(3);
            p.setColor(0x55FFFFFF);
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{18, 18}, 0));
            c.drawPath(track, p);
            p.setPathEffect(null);
            p.setStyle(Paint.Style.FILL);
            // start line and boost pads
            p.setColor(0xFFFFFFFF);
            c.drawRect(wx[0] - width / 2, wy[0] - 3, wx[0] + width / 2, wy[0] + 3, p);
            for (int k = 0; k < WP; k++) {
                if (!box[k]) continue;
                glow.setColor(0xFFFFB020);
                glow.setShadowLayer(12, 0, 0, 0xFFFFB020);
                c.drawCircle(wx[k], wy[k], H * 0.018f, glow);
            }
            float kl = H * 0.032f, kw = H * 0.018f;
            for (int i = 0; i < seats.size(); i++) {
                Seat s = seats.get(i);
                c.save();
                c.translate(x[i], y[i]);
                c.rotate((float) Math.toDegrees(ang[i]));
                glow.setColor(s.color);
                glow.setShadowLayer(boost[i] > 0 ? 26 : 12, 0, 0, s.color);
                c.drawRoundRect(-kl, -kw, kl, kw, kw * 0.6f, kw * 0.6f, glow);
                p.setColor(0xCC000000);
                c.drawRect(kl * 0.1f, -kw * 0.6f, kl * 0.6f, kw * 0.6f, p);
                c.restore();
            }
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(H * 0.032f);
            for (int i = 0; i < seats.size(); i++) {
                p.setColor(seats.get(i).color);
                c.drawText("P" + (seats.get(i).id + 1) + "  Pusingan " + Math.min(LAPS, lap[i] + 1) + "/" + LAPS, W * 0.02f, H * (0.06f + i * 0.045f), p);
            }
        }
    }
}
