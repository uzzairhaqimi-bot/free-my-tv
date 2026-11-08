package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fork extra: three small neon retro games made for this fork, played with the TV remote (or one or two gamepads):
 * Pong Neon (against the TV, or two players with two gamepads), Lompat Komet (a comet through gates) and Ular Neon.
 * A motivational line shows on every "game over". Drawn on one Canvas, nothing allocated per frame.
 */
public class RetroActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new Game(this);
        setContentView(game);
        game.setFocusable(true);
        game.requestFocus();
    }

    private Game game;

    @Override protected void onResume() {
        super.onResume();
        game.party.start(this);
        game.invalidate();
    }

    @Override protected void onPause() {
        game.party.stop();
        super.onPause();
    }

    static final class Game extends View {
        private static final int MENU = 0, PONG = 1, COMET = 2, SNAKE = 3, LOBBY = 4, PARTY = 5;
        private static final String[] NAMES = {"Pong Neon", "Lompat Komet", "Ular Neon",
                PartyGames.NAMES[0], PartyGames.NAMES[1], PartyGames.NAMES[2], PartyGames.NAMES[3]};
        private static final String[] HINTS = {
                "▲▼ gerak  ·  2 gamepad = 2 pemain",
                "OK / ▲ untuk melonjak",
                "Anak panah untuk pusing",
                "1–4 pemain · telefon", "1–4 pemain · telefon", "1–4 pemain · telefon", "1–4 pemain · telefon"};

        final Party party = new Party();
        private PartyGames.Mini mini;
        private int miniIndex;
        private final List<PartyGames.Seat> seats = new ArrayList<>();
        private android.graphics.Bitmap qr;
        private String qrFor;
        // the TV remote as player 1
        private boolean kUp, kDown, kLeft, kRight, kOk;

        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final Random rnd = new Random();
        private int mode = MENU, sel, best;
        private boolean over;
        private String quote = "";
        private long last;
        private float W, H;

        // Pong
        private float p1, p2, bx, by, vx, vy;
        private int s1, s2, dir1, dir2;
        private int dev1 = -1, dev2 = -1;
        // Comet
        private float cy, cv;
        private final float[] gx = new float[4], gy = new float[4];
        private final boolean[] passed = new boolean[4];
        private int score;
        // Snake
        private static final int GW = 32, GH = 18;
        private final List<int[]> snake = new ArrayList<>();
        private int dx = 1, dy = 0, ndx = 1, ndy = 0, fx, fy;
        private float acc;

        Game(Context c) {
            super(c);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            W = w;
            H = h;
            sky = new LinearGradient(0, 0, 0, H, 0xFF0B0B2B, 0xFF1A0B3B, Shader.TileMode.CLAMP);
        }

        private Shader sky;

        private void start(int m) {
            mode = m;
            over = false;
            last = 0;
            if (m == PONG) {
                p1 = p2 = H / 2;
                s1 = s2 = 0;
                serve(1);
            } else if (m == COMET) {
                cy = H / 2;
                cv = 0;
                score = 0;
                for (int i = 0; i < 4; i++) {
                    gx[i] = W + i * W * 0.32f;
                    gy[i] = H * (0.3f + rnd.nextFloat() * 0.4f);
                    passed[i] = false;
                }
            } else if (m == SNAKE) {
                snake.clear();
                for (int i = 0; i < 4; i++) snake.add(new int[]{8 - i, GH / 2});
                dx = ndx = 1;
                dy = ndy = 0;
                score = 0;
                acc = 0;
                food();
            }
            invalidate();
        }

        private void serve(int toward) {
            bx = W / 2;
            by = H / 2;
            vx = toward * W * 0.45f;
            vy = (rnd.nextFloat() - 0.5f) * H * 0.6f;
        }

        private void food() {
            while (true) {
                fx = rnd.nextInt(GW);
                fy = rnd.nextInt(GH);
                boolean hit = false;
                for (int[] s : snake) if (s[0] == fx && s[1] == fy) hit = true;
                if (!hit) return;
            }
        }

        private void gameOver(int sc) {
            over = true;
            if (sc > best) best = sc;
            quote = Quotes.next();
        }

        // ------------------------------------------------------------------ update

        private void update(float dt) {
            if (mode == PARTY && mini != null) {
                if (mini.over) { if (!over) { over = true; quote = Quotes.next(); } return; }
                feed();
                mini.update(dt);
                return;
            }
            if (over) return;
            if (mode == PONG) {
                float ph = H * 0.18f, sp = H * 1.1f;
                p1 += dir1 * sp * dt;
                if (dev2 == -1) {   // the TV plays the right paddle, a little slower than the ball
                    float target = vx > 0 ? by : H / 2;
                    p2 += Math.signum(target - p2) * Math.min(Math.abs(target - p2), H * 0.75f * dt);
                } else {
                    p2 += dir2 * sp * dt;
                }
                p1 = clamp(p1, ph / 2, H - ph / 2);
                p2 = clamp(p2, ph / 2, H - ph / 2);
                bx += vx * dt;
                by += vy * dt;
                float rad = H * 0.015f;
                if (by < rad) { by = rad; vy = -vy; }
                if (by > H - rad) { by = H - rad; vy = -vy; }
                float lx = W * 0.05f, rx = W * 0.95f;
                if (vx < 0 && bx - rad < lx && bx > lx - rad * 2 && Math.abs(by - p1) < ph / 2) {
                    vx = -vx * 1.06f;
                    vy += (by - p1) / ph * H * 0.9f;
                }
                if (vx > 0 && bx + rad > rx && bx < rx + rad * 2 && Math.abs(by - p2) < ph / 2) {
                    vx = -vx * 1.06f;
                    vy += (by - p2) / ph * H * 0.9f;
                }
                if (bx < 0) { s2++; if (s2 >= 7) gameOver(s1); else serve(1); }
                if (bx > W) { s1++; if (s1 >= 7) gameOver(s1); else serve(-1); }
            } else if (mode == COMET) {
                cv += H * 2.2f * dt;
                cy += cv * dt;
                float speed = W * (0.28f + Math.min(score, 30) * 0.006f), gap = H * 0.30f, gw = W * 0.06f;
                float cx = W * 0.25f, rad = H * 0.025f;
                for (int i = 0; i < 4; i++) {
                    gx[i] -= speed * dt;
                    if (gx[i] < -gw) {
                        float far = 0;
                        for (float g : gx) far = Math.max(far, g);
                        gx[i] = far + W * 0.32f;
                        gy[i] = H * (0.25f + rnd.nextFloat() * 0.5f);
                        passed[i] = false;
                    }
                    if (!passed[i] && gx[i] + gw < cx) { passed[i] = true; score++; }
                    if (cx + rad > gx[i] && cx - rad < gx[i] + gw && Math.abs(cy - gy[i]) > gap / 2 - rad) gameOver(score);
                }
                if (cy < 0 || cy > H) gameOver(score);
            } else if (mode == SNAKE) {
                acc += dt;
                float step = Math.max(0.06f, 0.14f - score * 0.003f);
                while (acc >= step && !over) {
                    acc -= step;
                    dx = ndx;
                    dy = ndy;
                    int[] head = snake.get(0);
                    int nx = (head[0] + dx + GW) % GW, ny = (head[1] + dy + GH) % GH;
                    for (int[] s : snake) if (s[0] == nx && s[1] == ny) { gameOver(score); return; }
                    snake.add(0, new int[]{nx, ny});
                    if (nx == fx && ny == fy) { score++; food(); } else snake.remove(snake.size() - 1);
                }
            }
        }

        private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

        // ------------------------------------------------------------------ draw

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            update(dt);

            p.setShader(sky);
            c.drawRect(0, 0, W, H, p);
            p.setShader(null);
            // retro grid floor
            p.setColor(0x22FF4FD8);
            p.setStrokeWidth(2);
            for (int i = 0; i < 12; i++) c.drawLine(0, H * 0.6f + i * i * H * 0.004f, W, H * 0.6f + i * i * H * 0.004f, p);

            if (mode == MENU) drawMenu(c);
            else if (mode == PONG) drawPong(c);
            else if (mode == COMET) drawComet(c);
            else if (mode == SNAKE) drawSnake(c);
            else if (mode == LOBBY) drawLobby(c);
            else if (mode == PARTY && mini != null) mini.draw(c, p, glow);

            if (over) drawOver(c);
            if ((mode != MENU && !over) || mode == LOBBY) postInvalidateOnAnimation();
        }

        private void neon(Canvas c, float x, float y, String s, float size, int color, Paint.Align a) {
            p.setTextAlign(a);
            p.setTextSize(size);
            p.setShadowLayer(size * 0.4f, 0, 0, color);
            p.setColor(color);
            c.drawText(s, x, y, p);
            p.setShadowLayer(0, 0, 0, 0);
        }

        private void drawMenu(Canvas c) {
            neon(c, W / 2, H * 0.14f, "RETRO ARKED", H * 0.08f, 0xFFFF4FD8, Paint.Align.CENTER);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(H * 0.028f);
            p.setColor(0x99FFFFFF);
            c.drawText("SEORANG", W * 0.27f, H * 0.24f, p);
            c.drawText("RAMAI-RAMAI  ·  TELEFON JADI JOYSTICK", W * 0.69f, H * 0.24f, p);
            for (int i = 0; i < NAMES.length; i++) {
                boolean on = i == sel, left = i < 3;
                int row = left ? i : i - 3;
                float cx = left ? W * 0.27f : W * 0.69f, top = H * (0.28f + row * 0.15f);
                r.set(cx - W * 0.19f, top, cx + W * 0.19f, top + H * 0.12f);
                p.setColor(on ? 0x33FFFFFF : 0x14FFFFFF);
                c.drawRoundRect(r, H * 0.03f, H * 0.03f, p);
                if (on) {
                    glow.setStyle(Paint.Style.STROKE);
                    glow.setStrokeWidth(4);
                    glow.setColor(0xFF22D3EE);
                    glow.setShadowLayer(16, 0, 0, 0xFF22D3EE);
                    c.drawRoundRect(r, H * 0.03f, H * 0.03f, glow);
                    glow.setStyle(Paint.Style.FILL);
                }
                neon(c, cx, r.centerY() - H * 0.005f, NAMES[i], H * 0.045f, on ? 0xFF22D3EE : 0xCCFFFFFF, Paint.Align.CENTER);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(H * 0.022f);
                p.setColor(0x99FFFFFF);
                c.drawText(HINTS[i], cx, r.centerY() + H * 0.04f, p);
            }
            p.setTextSize(H * 0.026f);
            p.setColor(0x80FFFFFF);
            c.drawText("▲▼◀▶ pilih   ·   OK main   ·   Back keluar", W / 2, H * 0.95f, p);
        }

        private void drawLobby(Canvas c) {
            neon(c, W / 2, H * 0.12f, PartyGames.NAMES[miniIndex], H * 0.07f, 0xFFFF4FD8, Paint.Align.CENTER);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(H * 0.028f);
            p.setColor(0xCCFFFFFF);
            c.drawText(PartyGames.HINTS[miniIndex], W / 2, H * 0.19f, p);
            if (party.url != null) {
                if (!party.url.equals(qrFor)) {
                    qrFor = party.url;
                    qr = qrBitmap(Qr.encode(party.url));
                }
                float s = H * 0.42f;
                r.set(W * 0.27f - s / 2, H * 0.26f, W * 0.27f + s / 2, H * 0.26f + s);
                p.setColor(0xFFFFFFFF);
                c.drawRoundRect(r.left - 12, r.top - 12, r.right + 12, r.bottom + 12, 16, 16, p);
                p.setFilterBitmap(false);
                c.drawBitmap(qr, null, r, p);
                p.setTextSize(H * 0.026f);
                p.setColor(0xCCFFFFFF);
                c.drawText("Imbas dengan kamera telefon", W * 0.27f, H * 0.76f, p);
                p.setTextSize(H * 0.02f);
                p.setColor(0x80FFFFFF);
                c.drawText("Telefon mesti guna Wi-Fi yang sama", W * 0.27f, H * 0.8f, p);
            } else {
                p.setTextSize(H * 0.03f);
                p.setColor(0xFFFFB020);
                c.drawText("Sambung TV ke Wi-Fi untuk joystick telefon", W * 0.27f, H * 0.5f, p);
            }
            for (int i = 0; i < Party.MAX; i++) {
                Party.Player pl = party.players[i];
                float y = H * (0.32f + i * 0.12f);
                glow.setColor(pl.joined ? pl.color() : 0x33FFFFFF);
                glow.setShadowLayer(pl.joined ? 16 : 0, 0, 0, pl.color());
                c.drawCircle(W * 0.58f, y - H * 0.012f, H * 0.03f, glow);
                p.setTextAlign(Paint.Align.LEFT);
                p.setTextSize(H * 0.035f);
                p.setColor(pl.joined ? 0xFFFFFFFF : 0x66FFFFFF);
                String who = i == 0 ? "Remote TV" : pl.joined ? "Telefon" : "Menunggu…";
                c.drawText("Pemain " + (i + 1) + "  ·  " + who, W * 0.62f, y, p);
            }
            int humans = party.count();
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(H * 0.03f);
            p.setColor(0xFF22D3EE);
            c.drawText(humans < 2 ? "OK untuk mula (lawan komputer)" : "OK untuk mula  ·  " + humans + " pemain", W * 0.72f, H * 0.86f, p);
        }

        private static android.graphics.Bitmap qrBitmap(boolean[][] m) {
            int n = m.length, q = 4;
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(n + q * 2, n + q * 2, android.graphics.Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) if (m[y][x]) b.setPixel(x + q, y + q, Color.BLACK);
            return b;
        }

        private void startParty() {
            seats.clear();
            for (int i = 0; i < Party.MAX; i++) if (party.players[i].joined) seats.add(new PartyGames.Seat(i, Party.COLORS[i], false));
            int want = miniIndex == 3 ? 4 : 2;   // races and duels are better with company
            for (int i = 0; i < Party.MAX && seats.size() < want && party.count() < 2; i++) {
                if (!party.players[i].joined) seats.add(new PartyGames.Seat(i, Party.COLORS[i], true));
            }
            mini = PartyGames.create(miniIndex);
            mini.start(seats, W, H, party);
            mode = PARTY;
            over = false;
            last = 0;
            invalidate();
        }

        /** Copies this frame's inputs: phones, the remote (seat 1), or a bot. */
        private void feed() {
            for (PartyGames.Seat s : seats) {
                if (s.bot) { mini.ai(s); continue; }
                Party.Player pl = party.players[s.id];
                if (s.id == 0 && !pl.phone) {
                    s.x = (kRight ? 1 : 0) - (kLeft ? 1 : 0);
                    s.y = (kDown ? 1 : 0) - (kUp ? 1 : 0);
                    s.aHeld = mini instanceof PartyGames.Kart ? !kDown : kOk;
                    s.bHeld = mini instanceof PartyGames.Kart && kDown;
                    continue;
                }
                s.x = pl.x;
                s.y = pl.y;
                s.aHeld = pl.a;
                s.bHeld = pl.b;
                s.aTaps += pl.aPresses;
                pl.aPresses = 0;
                s.bTaps += pl.bPresses;
                pl.bPresses = 0;
            }
        }

        private void drawPong(Canvas c) {
            float ph = H * 0.18f, pw = W * 0.012f;
            p.setColor(0x40FFFFFF);
            for (float y = 0; y < H; y += H * 0.05f) c.drawRect(W / 2 - 2, y, W / 2 + 2, y + H * 0.025f, p);
            glow.setStyle(Paint.Style.FILL);
            glow.setColor(0xFF22D3EE);
            glow.setShadowLayer(18, 0, 0, 0xFF22D3EE);
            c.drawRect(W * 0.05f - pw, p1 - ph / 2, W * 0.05f, p1 + ph / 2, glow);
            glow.setColor(0xFFFF4FD8);
            glow.setShadowLayer(18, 0, 0, 0xFFFF4FD8);
            c.drawRect(W * 0.95f, p2 - ph / 2, W * 0.95f + pw, p2 + ph / 2, glow);
            glow.setColor(0xFFFFFFFF);
            glow.setShadowLayer(14, 0, 0, 0xFFFFFFFF);
            c.drawCircle(bx, by, H * 0.015f, glow);
            neon(c, W * 0.4f, H * 0.12f, String.valueOf(s1), H * 0.08f, 0xFF22D3EE, Paint.Align.CENTER);
            neon(c, W * 0.6f, H * 0.12f, String.valueOf(s2), H * 0.08f, 0xFFFF4FD8, Paint.Align.CENTER);
            p.setTextSize(H * 0.025f);
            p.setColor(0x80FFFFFF);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(dev2 == -1 ? "Awak lawan TV  ·  7 mata menang" : "2 pemain  ·  7 mata menang", W / 2, H * 0.96f, p);
        }

        private void drawComet(Canvas c) {
            float gw = W * 0.06f, gap = H * 0.30f, cx = W * 0.25f;
            for (int i = 0; i < 4; i++) {
                glow.setStyle(Paint.Style.FILL);
                glow.setColor(0xFF8B5CF6);
                glow.setShadowLayer(16, 0, 0, 0xFFA78BFA);
                c.drawRect(gx[i], 0, gx[i] + gw, gy[i] - gap / 2, glow);
                c.drawRect(gx[i], gy[i] + gap / 2, gx[i] + gw, H, glow);
            }
            for (int t = 1; t <= 6; t++) {   // the comet's tail
                p.setColor(Color.argb(150 - t * 22, 255, 200, 80));
                c.drawCircle(cx - t * H * 0.022f, cy + cv * 0.004f * t, H * (0.024f - t * 0.003f), p);
            }
            glow.setColor(0xFFFFE08A);
            glow.setShadowLayer(22, 0, 0, 0xFFFFB020);
            c.drawCircle(cx, cy, H * 0.026f, glow);
            neon(c, W / 2, H * 0.12f, String.valueOf(score), H * 0.08f, 0xFFFFE08A, Paint.Align.CENTER);
        }

        private void drawSnake(Canvas c) {
            float cs = Math.min(W / GW, H / GH), ox = (W - cs * GW) / 2, oy = (H - cs * GH) / 2;
            p.setColor(0x10FFFFFF);
            c.drawRect(ox, oy, ox + cs * GW, oy + cs * GH, p);
            glow.setStyle(Paint.Style.FILL);
            glow.setColor(0xFFFF4F6D);
            glow.setShadowLayer(16, 0, 0, 0xFFFF4F6D);
            c.drawCircle(ox + (fx + 0.5f) * cs, oy + (fy + 0.5f) * cs, cs * 0.35f, glow);
            for (int i = 0; i < snake.size(); i++) {
                int[] s = snake.get(i);
                int col = i == 0 ? 0xFF4ADE80 : Color.argb(255, 34, Math.max(120, 211 - i * 3), 238);
                glow.setColor(col);
                glow.setShadowLayer(i == 0 ? 16 : 6, 0, 0, col);
                r.set(ox + s[0] * cs + 2, oy + s[1] * cs + 2, ox + (s[0] + 1) * cs - 2, oy + (s[1] + 1) * cs - 2);
                c.drawRoundRect(r, cs * 0.25f, cs * 0.25f, glow);
            }
            neon(c, W - W * 0.04f, H * 0.09f, "Skor " + score, H * 0.05f, 0xFF4ADE80, Paint.Align.RIGHT);
        }

        private void drawOver(Canvas c) {
            p.setColor(0xB3000000);
            c.drawRect(0, 0, W, H, p);
            String title = mode == PARTY ? (mini.result.isEmpty() ? "TAMAT" : mini.result.toUpperCase())
                    : mode == PONG ? (s1 > s2 ? "MENANG!" : "KALAH") : "TAMAT";
            neon(c, W / 2, H * 0.32f, title, H * 0.1f, 0xFFFF4FD8, Paint.Align.CENTER);
            int sc = mode == PONG ? s1 : score;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(H * 0.04f);
            p.setColor(0xFFFFFFFF);
            if (mode != PARTY) c.drawText(mode == PONG ? s1 + "  :  " + s2 : "Skor " + sc + "   ·   Terbaik " + best, W / 2, H * 0.42f, p);
            neon(c, W / 2, H * 0.58f, "✦  " + quote, H * 0.038f, 0xFF22D3EE, Paint.Align.CENTER);
            p.setTextSize(H * 0.03f);
            p.setColor(0x99FFFFFF);
            c.drawText("OK: main semula   ·   Back: menu", W / 2, H * 0.78f, p);
        }

        // ------------------------------------------------------------------ keys

        @Override public boolean onKeyDown(int k, KeyEvent e) {
            boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                    || k == KeyEvent.KEYCODE_BUTTON_A || k == KeyEvent.KEYCODE_SPACE;
            if (mode == PARTY || mode == LOBBY) {
                if (k == KeyEvent.KEYCODE_DPAD_UP) kUp = true;
                if (k == KeyEvent.KEYCODE_DPAD_DOWN) kDown = true;
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) kLeft = true;
                if (k == KeyEvent.KEYCODE_DPAD_RIGHT) kRight = true;
                if (ok) {
                    if (e.getRepeatCount() == 0 && mode == LOBBY) { startParty(); return true; }
                    if (e.getRepeatCount() == 0 && over) { mode = LOBBY; over = false; invalidate(); return true; }
                    kOk = true;
                    if (e.getRepeatCount() == 0) for (PartyGames.Seat s : seats) if (s.id == 0 && !s.bot) s.aTaps++;
                    return true;
                }
            }
            if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_BUTTON_B) {
                if (mode == PARTY) { mode = LOBBY; over = false; invalidate(); return true; }
                if (mode == MENU) return false;   // leave the games
                mode = MENU;
                over = false;
                dev1 = dev2 = -1;
                invalidate();
                return true;
            }
            if (mode == MENU) {
                boolean left = sel < 3;
                int rows = left ? 3 : 4, row = left ? sel : sel - 3;
                if (k == KeyEvent.KEYCODE_DPAD_UP) row = (row + rows - 1) % rows;
                else if (k == KeyEvent.KEYCODE_DPAD_DOWN) row = (row + 1) % rows;
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && left) { left = false; row = Math.min(row, 3); }
                else if (k == KeyEvent.KEYCODE_DPAD_LEFT && !left) { left = true; row = Math.min(row, 2); }
                sel = left ? row : row + 3;
                if (ok) {
                    if (sel < 3) start(sel + 1);
                    else { miniIndex = sel - 3; mode = LOBBY; over = false; last = 0; }
                }
                invalidate();
                return true;
            }
            if (mode == LOBBY || mode == PARTY) return true;
            if (over) {
                if (ok) start(mode);
                return true;
            }
            if (mode == PONG) {
                int d = e.getDeviceId();
                // The first controller plays left; a second, different gamepad takes the right paddle.
                if (dev1 == -1) dev1 = d;
                else if (d != dev1 && dev2 == -1 && (e.getSource() & android.view.InputDevice.SOURCE_GAMEPAD) != 0) dev2 = d;
                int dir = k == KeyEvent.KEYCODE_DPAD_UP ? -1 : k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
                if (dir != 0) { if (d == dev2) dir2 = dir; else dir1 = dir; }
                return true;
            }
            if (mode == COMET) {
                if (ok || k == KeyEvent.KEYCODE_DPAD_UP) cv = -H * 0.75f;
                return true;
            }
            if (mode == SNAKE) {
                if (k == KeyEvent.KEYCODE_DPAD_UP && dy == 0) { ndx = 0; ndy = -1; }
                else if (k == KeyEvent.KEYCODE_DPAD_DOWN && dy == 0) { ndx = 0; ndy = 1; }
                else if (k == KeyEvent.KEYCODE_DPAD_LEFT && dx == 0) { ndx = -1; ndy = 0; }
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT && dx == 0) { ndx = 1; ndy = 0; }
                return true;
            }
            return super.onKeyDown(k, e);
        }

        @Override public boolean onKeyUp(int k, KeyEvent e) {
            if (k == KeyEvent.KEYCODE_DPAD_UP) kUp = false;
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) kDown = false;
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) kLeft = false;
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) kRight = false;
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A) kOk = false;
            if (mode == PONG && (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN)) {
                if (e.getDeviceId() == dev2) dir2 = 0; else dir1 = 0;
                return true;
            }
            return super.onKeyUp(k, e);
        }
    }
}
