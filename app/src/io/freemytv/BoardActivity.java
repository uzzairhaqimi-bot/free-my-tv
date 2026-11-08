package io.freemytv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Fork extra: "Papan Permainan", five family games made for this fork, played by passing the TV remote around (and,
 * for Siapa Juara?, with phones as answer pads): Hartanah Malaysia (buy towns, collect rent), Ular & Tangga Bijak
 * (answer to climb), Congkak (the traditional Malay seed game), Siapa Juara? (a quiz show) and Teka Kata (guess the
 * five-letter word in six tries), plus Pengembaraan Sang Kancil (Kancil.java), a split-screen platformer. Players who
 * are not people are played by the TV.
 */
public class BoardActivity extends Activity {
    private Board board;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        board = new Board(this);
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(board);
        board.sound.attach(root);
        setContentView(root);
        board.setFocusable(true);
        board.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume();
        board.party.start(this);
        board.sound.resume(this);
    }

    @Override protected void onPause() {
        board.party.stop();
        board.sound.pause();
        super.onPause();
    }

    static final class Board extends View {
        static final int MENU = 0, SETUP = 1, PLAY = 2, END = 3;
        static final int HARTA = 0, ULAR = 1, CONGKAK = 2, JUARA = 3, TEKA = 4, KANCIL = 5, ARKED = 6;
        static final String[] NAMES = concat(new String[]{"Hartanah Malaysia", "Ular & Tangga Bijak", "Congkak", "Siapa Juara?", "Teka Kata",
                "Sang Kancil"}, Arked4.NAMES);
        static final String[] HINTS = {"Beli bandar, kutip sewa, jadi terkaya", "Jawab soalan untuk naik tangga",
                "Permainan tradisional, kumpul biji terbanyak", "Kuiz TV, telefon jadi butang jawapan", "Teka perkataan 5 huruf, 6 cubaan",
                "Pengembaraan 5 dunia, 1-4 pemain skrin pisah"};
        static final int[] COLS = {0xFF22C55E, 0xFFF97316, 0xFFB45309, 0xFF8B5CF6, 0xFF0EA5E9, 0xFF65A30D,
                0xFFEF4444, 0xFFA16207, 0xFF6366F1, 0xFFEA580C, 0xFF15803D, 0xFFB91C1C, 0xFFDB2777, 0xFF0284C7,
                0xFF0EA5E9, 0xFF475569, 0xFF0D9488, 0xFFF59E0B, 0xFF7C3AED, 0xFFCA8A04};

        static String[] concat(String[] x, String[] y) {
            String[] out = java.util.Arrays.copyOf(x, x.length + y.length);
            System.arraycopy(y, 0, out, x.length, y.length);
            return out;
        }

        static String hint(int i) { return i < HINTS.length ? HINTS[i] : Arked4.HINTS[i - HINTS.length]; }

        /** The arcade engine's id for menu entry `g` (g >= ARKED). */
        static int arkedId(int g) { return Arked4.IDS[g - ARKED]; }
        static final int[] PCOL = {0xFF22D3EE, 0xFFFF4FD8, 0xFFFACC15, 0xFF4ADE80};

        final Party party = new Party();
        final GameSound sound = new GameSound();
        final Kancil kancil = new Kancil(party, sound);
        final Arked4 arked = new Arked4(party, sound);
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), g = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final Random rnd = new Random();
        final LearnBank bank = new LearnBank(System.nanoTime());
        float W, H, t;
        long last;
        Shader bg;
        final FancyArt.Aurora aurora = new FancyArt.Aurora(0xFF140A2E, 0xFF3B0A2A,
                new int[]{0xFFF97316, 0xFF8B5CF6, 0xFF22D3EE}, new String[]{"⚀", "⚂", "⚄", "♜", "♞", "★", "⚁", "⚃", "⚅", "♛", "♟", "◆"});
        final FancyArt.Cards cards = new FancyArt.Cards();
        int screen = MENU, sel, game;
        int players = 2;
        final boolean[] bot = {false, true, true, true};
        int setupRow;
        int turn;
        String msg = "", winnerText = "";
        long msgAt;

        Board(Context c) {
            super(c);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            W = w;
            H = h;
            bg = new LinearGradient(0, 0, W, H, 0xFF0F172A, 0xFF312E81, Shader.TileMode.CLAMP);
        }

        void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align a) {
            p.setTextAlign(a);
            p.setTextSize(size);
            p.setColor(color);
            c.drawText(s, x, y, p);
        }

        void fit(Canvas c, String s, float cx, float cy, float maxW, float size, int color) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(size);
            while (p.measureText(s) > maxW && size > 8) { size *= 0.9f; p.setTextSize(size); }
            p.setColor(color);
            c.drawText(s, cx, cy + size * 0.35f, p);
        }

        void wrapped(Canvas c, String s, float x, float top, float width, float size, int color, Layout.Alignment a) {
            tp.setTextSize(size);
            tp.setColor(color);
            StaticLayout l = new StaticLayout(s, tp, (int) width, a, 1.1f, 0, false);
            c.save();
            c.translate(x, top);
            l.draw(c);
            c.restore();
        }

        void card(Canvas c, RectF rr, int color, boolean on) {
            cards.draw(c, rr, color, on, t, H * 0.03f);
        }

        void say(String s) { msg = s; msgAt = SystemClock.uptimeMillis(); }

        boolean isBot(int i) { return bot[i]; }

        // ================================================================ frame

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            t += dt;
            sound.mood(screen == PLAY && (game == JUARA || game >= KANCIL) ? GameMusic.SEMANGAT : GameMusic.SANTAI);
            if (screen != PLAY || game < KANCIL) aurora.draw(c, W, H, t);   // Kancil and the arcade games draw their own
            if (screen == MENU) drawMenu(c);
            else if (screen == SETUP) drawSetup(c);
            else if (screen == PLAY) {
                if (game == HARTA) harta.draw(c, dt);
                else if (game == ULAR) ular.draw(c, dt);
                else if (game == CONGKAK) congkak.draw(c, dt);
                else if (game == JUARA) juara.draw(c, dt);
                else if (game == KANCIL) {
                    kancil.update(dt);
                    kancil.draw(c, W, H, t);
                    if (kancil.over) endGame(kancil.result);
                } else if (game >= ARKED) {
                    arked.update(dt);
                    arked.draw(c, W, H, t);
                    if (arked.over) endGame(arked.result);
                }
                else teka.draw(c, dt);
            } else drawEnd(c);
            if (screen == PLAY && !msg.isEmpty() && now - msgAt < 3500) {
                p.setTextSize(H * 0.032f);
                float w = p.measureText(msg) + H * 0.06f;
                r.set(W / 2 - w / 2, H * 0.9f, W / 2 + w / 2, H * 0.96f);
                g.setColor(0xDD000000);
                c.drawRoundRect(r, H * 0.03f, H * 0.03f, g);
                text(c, msg, W / 2, H * 0.94f, H * 0.032f, 0xFFFFFFFF, Paint.Align.CENTER);
            }
            postInvalidateOnAnimation();
        }

        void drawMenu(Canvas c) {
            text(c, "Papan Permainan", W / 2, H * 0.11f, H * 0.065f, 0xFFFFFFFF, Paint.Align.CENTER);
            text(c, "Untuk satu keluarga: remote + telefon, sampai 4 pemain", W / 2, H * 0.17f, H * 0.03f, 0xBBFFFFFF, Paint.Align.CENTER);
            int n = NAMES.length, perRow = 5, rows = (n + perRow - 1) / perRow;
            float cw = W * 0.17f, gap = W * 0.015f, x0 = (W - perRow * cw - (perRow - 1) * gap) / 2, ch = H * 0.23f;
            // three rows show at a time; the list slides to keep the chosen one in view
            float want = Math.max(0, Math.min(rows - 3, sel / perRow - 1));
            menuScroll += (want - menuScroll) * 0.2f;
            c.save();
            c.clipRect(0, H * 0.2f, W, H * 0.945f);
            for (int i = 0; i < n; i++) {
                float rowPos = i / perRow - menuScroll;
                if (rowPos < -1 || rowPos > 3) continue;
                float top = H * (0.21f + rowPos * 0.245f), left = x0 + (i % perRow) * (cw + gap);
                r.set(left, top, left + cw, top + ch);
                boolean on = i == sel;
                if (on) { r.offset(0, -H * 0.02f); float gr = W * (0.005f + 0.003f * (float) Math.sin(t * 3)); r.inset(-gr, -gr); }
                card(c, r, COLS[i], on);
                drawIcon(c, i, r.centerX(), r.top + H * 0.075f + (on ? (float) Math.sin(t * 4) * H * 0.008f : 0), H * 0.045f);
                fit(c, NAMES[i], r.centerX(), r.top + H * 0.155f, cw * 0.9f, H * 0.03f, 0xFFFFFFFF);
                fit(c, hint(i), r.centerX(), r.top + H * 0.198f, cw * 0.92f, H * 0.019f, 0xDDFFFFFF);
            }
            c.restore();
            if (menuScroll > 0.05f) text(c, "▲", W / 2, H * 0.205f, H * 0.03f, 0x99FFFFFF, Paint.Align.CENTER);
            if (menuScroll < rows - 3 - 0.05f) text(c, "▼ lagi permainan", W / 2, H * 0.94f, H * 0.024f, 0xCCFDE68A, Paint.Align.CENTER);
            text(c, "◀▶▲▼ pilih   ·   OK main   ·   Back keluar   ·   " + NAMES.length + " permainan", W / 2, H * 0.975f, H * 0.024f, 0x99FFFFFF, Paint.Align.CENTER);
        }

        float menuScroll;
        final android.graphics.Bitmap[] icons = new android.graphics.Bitmap[NAMES.length];

        /** The game's picture, drawn once at this screen's size; s is half its height. */
        void drawIcon(Canvas c, int i, float cx, float cy, float s) {
            int size = Math.max(16, Math.round(s * 2.4f));
            if (icons[i] == null || icons[i].getWidth() != size) icons[i] = FancyArt.gameIcon(i, size);
            c.drawBitmap(icons[i], cx - size / 2f, cy - size / 2f, null);
        }

        // ================================================================ setup

        boolean needsPlayers() { return game != TEKA; }

        void drawSetup(Canvas c) {
            text(c, NAMES[game], W / 2, H * 0.14f, H * 0.065f, 0xFFFFFFFF, Paint.Align.CENTER);
            if (game == JUARA || game >= KANCIL) {
                drawJuaraLobby(c);
                return;
            }
            int min = game == CONGKAK ? 2 : 2, max = game == CONGKAK ? 2 : 4;
            players = Math.max(min, Math.min(max, players));
            text(c, "Bilangan pemain:  ◀  " + players + "  ▶", W / 2, H * 0.3f, H * 0.045f, setupRow == 0 ? 0xFF67E8F9 : 0xFFFFFFFF, Paint.Align.CENTER);
            for (int i = 0; i < players; i++) {
                float y = H * (0.42f + i * 0.1f);
                boolean on = setupRow == i + 1;
                r.set(W * 0.3f, y - H * 0.045f, W * 0.7f, y + H * 0.035f);
                card(c, r, PCOL[i], on);
                text(c, "Pemain " + (i + 1) + "  ·  " + (bot[i] ? "🤖 TV main" : "🙂 Orang"), W / 2, y + H * 0.008f, H * 0.035f, 0xFFFFFFFF, Paint.Align.CENTER);
            }
            text(c, "▲▼ pilih  ·  ◀▶ tukar  ·  OK pada pemain: orang / TV  ·  Play/Menu: MULA", W / 2, H * 0.9f, H * 0.026f, 0xAAFFFFFF, Paint.Align.CENTER);
            r.set(W * 0.4f, H * 0.8f - H * 0.04f, W * 0.6f, H * 0.8f + H * 0.03f);
            card(c, r, 0xFF22C55E, setupRow == players + 1);
            text(c, "MULA ▶", W / 2, H * 0.81f, H * 0.04f, 0xFFFFFFFF, Paint.Align.CENTER);
        }

        void startGame() {
            screen = PLAY;
            turn = 0;
            msg = "";
            if (game == HARTA) harta.start();
            else if (game == ULAR) ular.start();
            else if (game == CONGKAK) congkak.start();
            else if (game == JUARA) juara.start();
            else if (game == KANCIL) kancil.start();
            else if (game >= ARKED) arked.start(arkedId(game), W, H);
            else teka.start(true, true);
        }

        void endGame(String w) {
            winnerText = w;
            screen = END;
        }

        void drawEnd(Canvas c) {
            text(c, "🏆", W / 2, H * 0.32f, H * 0.14f, 0xFFFFFFFF, Paint.Align.CENTER);
            fit(c, winnerText, W / 2, H * 0.45f, W * 0.8f, H * 0.06f, 0xFFFDE68A);
            wrapped(c, "✦  " + Quotes.next(), W * 0.15f, H * 0.55f, W * 0.7f, H * 0.034f, 0xFF67E8F9, Layout.Alignment.ALIGN_CENTER);
            text(c, "OK main lagi   ·   Back ke menu", W / 2, H * 0.88f, H * 0.03f, 0x99FFFFFF, Paint.Align.CENTER);
        }

        // ================================================================ dice

        int die1 = 1, die2 = 1;
        float rolling;

        void drawDice(Canvas c, float cx, float cy, float s, int n, boolean two) {
            int a = rolling > 0 ? 1 + rnd.nextInt(6) : die1, b = rolling > 0 ? 1 + rnd.nextInt(6) : die2;
            drawDie(c, two ? cx - s * 0.65f : cx, cy, s, a);
            if (two) drawDie(c, cx + s * 0.65f, cy, s, b);
        }

        void drawDie(Canvas c, float cx, float cy, float s, int v) {
            r.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
            g.setColor(0xFFFFFFFF);
            g.setShadowLayer(10, 0, 4, 0x88000000);
            c.drawRoundRect(r, s * 0.18f, s * 0.18f, g);
            g.setShadowLayer(0, 0, 0, 0);
            g.setColor(0xFF111827);
            float d = s * 0.26f, rr = s * 0.09f;
            boolean[][] pips = {
                    {false, false, false, false, true, false, false, false, false},
                    {true, false, false, false, false, false, false, false, true},
                    {true, false, false, false, true, false, false, false, true},
                    {true, false, true, false, false, false, true, false, true},
                    {true, false, true, false, true, false, true, false, true},
                    {true, false, true, true, false, true, true, false, true}};
            for (int k = 0; k < 9; k++) if (pips[v - 1][k]) c.drawCircle(cx + (k % 3 - 1) * d, cy + (k / 3 - 1) * d, rr, g);
        }

        // ================================================================ Hartanah Malaysia

        final Harta harta = new Harta();

        final class Harta {
            // 24 squares around the board. type: 0 start, 1 town, 2 peluang, 3 nasib, 4 cukai, 5 rehat, 6 jem
            final String[] name = {"MULA", "Kangar", "Alor Setar", "Peluang", "George Town", "Ipoh", "Rehat",
                    "Kuala Kubu", "Shah Alam", "Nasib", "Seremban", "Melaka", "Jem Trafik",
                    "Johor Bahru", "Kuantan", "Cukai Jalan", "Kuala Terengganu", "Kota Bharu", "Rehat",
                    "Kuching", "Miri", "Peluang", "Kota Kinabalu", "Kuala Lumpur"};
            final int[] type = {0, 1, 1, 2, 1, 1, 5, 1, 1, 3, 1, 1, 6, 1, 1, 4, 1, 1, 5, 1, 1, 2, 1, 1};
            final int[] price = {0, 60, 80, 0, 100, 120, 0, 140, 160, 0, 180, 200, 0, 220, 240, 0, 260, 280, 0, 300, 320, 0, 350, 400};
            final int[] group = {-1, 0, 0, -1, 1, 1, -1, 2, 2, -1, 3, 3, -1, 4, 4, -1, 5, 5, -1, 6, 6, -1, 7, 7};
            final int[] gcol = {0xFF8B5CF6, 0xFF38BDF8, 0xFFF472B6, 0xFFFB923C, 0xFFEF4444, 0xFFFACC15, 0xFF22C55E, 0xFF2563EB};
            final int[] owner = new int[24], houses = new int[24];
            final int[] pos = new int[4], money = new int[4], skip = new int[4];
            final boolean[] out = new boolean[4];
            float[] drawPos = new float[4];
            int phase;   // 0 waiting roll, 1 moving, 2 decide buy/upgrade, 3 next turn
            int rounds, steps;
            float stepT;
            String[] PELUANG = {"Menang pertandingan melukis: +RM100", "Jual kuih di pasar malam: +RM50", "Dapat biasiswa: +RM150",
                    "Tolong jiran, dapat hadiah: +RM40", "Maju 3 petak", "Pergi terus ke MULA"};
            String[] NASIB = {"Bayar yuran tuisyen: −RM60", "Basikal pancit, baiki: −RM30", "Derma ke sekolah: −RM50",
                    "Beli buku rujukan: −RM40", "Undur 2 petak", "Hadiah hari jadi: +RM80"};

            void start() {
                for (int i = 0; i < 24; i++) { owner[i] = -1; houses[i] = 0; }
                for (int i = 0; i < 4; i++) { pos[i] = 0; money[i] = 1500; skip[i] = 0; out[i] = i >= players; drawPos[i] = 0; }
                phase = 0;
                rounds = 0;
                say("Pemain 1: tekan OK untuk baling dadu");
            }

            int rent(int sq) {
                int base = price[sq] / 8;
                boolean both = true;
                for (int k = 0; k < 24; k++) if (group[k] == group[sq] && owner[k] != owner[sq]) both = false;
                return (base * (both ? 2 : 1)) * (1 + houses[sq] * 2);
            }

            int worth(int pl) {
                int w = money[pl];
                for (int k = 0; k < 24; k++) if (owner[k] == pl) w += price[k] + houses[k] * price[k] / 2;
                return w;
            }

            void roll() {
                die1 = 1 + rnd.nextInt(6);
                die2 = 1 + rnd.nextInt(6);
                rolling = 0.6f;
                steps = die1 + die2;
                stepT = 0;
                phase = 1;
            }

            void land() {
                int pl = turn, sq = pos[pl];
                switch (type[sq]) {
                    case 1:
                        if (owner[sq] == -1) {
                            if (isBot(pl)) {
                                if (money[pl] >= price[sq] + 150) buy(); else nextTurn();
                            } else {
                                phase = 2;
                                say(name[sq] + " RM" + price[sq] + ": OK beli · Back tidak");
                            }
                        } else if (owner[sq] == pl) {
                            int cost = price[sq] / 2;
                            if (houses[sq] < 3 && money[pl] >= cost) {
                                if (isBot(pl)) {
                                    if (money[pl] >= cost + 200) upgrade(); else nextTurn();
                                } else { phase = 2; say("Bina rumah di " + name[sq] + " RM" + cost + "? OK ya · Back tidak"); }
                            } else nextTurn();
                        } else {
                            int rnt = rent(sq);
                            money[pl] -= rnt;
                            money[owner[sq]] += rnt;
                            say("P" + (pl + 1) + " bayar sewa RM" + rnt + " kepada P" + (owner[sq] + 1));
                            nextTurn();
                        }
                        break;
                    case 2: case 3: {
                        boolean good = type[sq] == 2;
                        int k = rnd.nextInt(6);
                        String card = good ? PELUANG[k] : NASIB[k];
                        say((good ? "Peluang: " : "Nasib: ") + card);
                        if (good) {
                            if (k == 0) money[pl] += 100; else if (k == 1) money[pl] += 50; else if (k == 2) money[pl] += 150;
                            else if (k == 3) money[pl] += 40; else if (k == 4) { steps = 3; phase = 1; return; }
                            else { pos[pl] = 0; drawPos[pl] = 0; money[pl] += 200; }
                        } else {
                            if (k == 0) money[pl] -= 60; else if (k == 1) money[pl] -= 30; else if (k == 2) money[pl] -= 50;
                            else if (k == 3) money[pl] -= 40; else if (k == 4) { pos[pl] = (pos[pl] + 22) % 24; drawPos[pl] = pos[pl]; }
                            else money[pl] += 80;
                        }
                        nextTurn();
                        break;
                    }
                    case 4: money[pl] -= 100; say("Cukai jalan: −RM100"); nextTurn(); break;
                    case 6: skip[pl] = 1; say("Jem trafik! Tunggu 1 giliran"); nextTurn(); break;
                    default: nextTurn();
                }
            }

            void buy() {
                int pl = turn, sq = pos[pl];
                money[pl] -= price[sq];
                owner[sq] = pl;
                say("P" + (pl + 1) + " beli " + name[sq] + "!");
                nextTurn();
            }

            void upgrade() {
                int pl = turn, sq = pos[pl];
                money[pl] -= price[sq] / 2;
                houses[sq]++;
                say("Rumah baru di " + name[sq] + " 🏠");
                nextTurn();
            }

            void nextTurn() {
                for (int i = 0; i < players; i++) {
                    if (!out[i] && money[i] < 0) {
                        out[i] = true;
                        for (int k = 0; k < 24; k++) if (owner[k] == i) { owner[k] = -1; houses[k] = 0; }
                        say("Pemain " + (i + 1) + " muflis!");
                    }
                }
                int alive = 0, lastAlive = 0;
                for (int i = 0; i < players; i++) if (!out[i]) { alive++; lastAlive = i; }
                if (alive <= 1) { endGame("Pemain " + (lastAlive + 1) + " menang!"); return; }
                do {
                    turn = (turn + 1) % players;
                    if (turn == 0) rounds++;
                } while (out[turn]);
                if (rounds >= 20) {
                    int best = 0;
                    for (int i = 0; i < players; i++) if (!out[i] && worth(i) > worth(best)) best = i;
                    endGame("Pemain " + (best + 1) + " paling kaya: RM" + worth(best));
                    return;
                }
                phase = 3;
                stepT = 0;
            }

            void key(int k, boolean ok, boolean back) {
                if (isBot(turn)) return;
                if (phase == 0 && ok) roll();
                else if (phase == 2) {
                    int sq = pos[turn];
                    if (ok) { if (owner[sq] == turn) upgrade(); else if (money[turn] >= price[sq]) buy(); else { say("Duit tak cukup"); nextTurn(); } }
                    else if (back) nextTurn();
                }
            }

            void update(float dt) {
                if (rolling > 0) { rolling -= dt; return; }
                if (phase == 3) {
                    stepT += dt;
                    if (stepT > 0.6f) {
                        if (skip[turn] > 0) { skip[turn]--; say("P" + (turn + 1) + " masih dalam jem"); nextTurn(); return; }
                        phase = 0;
                        if (!isBot(turn)) say("Pemain " + (turn + 1) + ": tekan OK untuk baling dadu");
                    }
                    return;
                }
                if (phase == 0 && isBot(turn)) { stepT += dt; if (stepT > 0.8f) roll(); return; }
                if (phase == 1) {
                    stepT += dt;
                    if (stepT > 0.18f && steps > 0) {
                        stepT = 0;
                        pos[turn] = (pos[turn] + 1) % 24;
                        if (pos[turn] == 0) { money[turn] += 200; say("Lalu MULA: +RM200"); }
                        steps--;
                        if (steps == 0) land();
                    }
                }
                for (int i = 0; i < 4; i++) drawPos[i] += (pos[i] - drawPos[i] + (pos[i] < drawPos[i] - 12 ? 24 : 0)) * Math.min(1, dt * 12);
            }

            /** Square k's rectangle on an 8 × 6 ring. */
            void rect(int k, RectF out) {
                float bw = H * 0.92f * 8 / 6, bh = H * 0.92f, x0 = W * 0.04f, y0 = H * 0.04f, cw = bw / 8, ch = bh / 6;
                int col, row;
                if (k < 8) { col = k; row = 5; }               // bottom, left → right
                else if (k < 13) { col = 7; row = 5 - (k - 7); }  // right, up
                else if (k < 20) { col = 7 - (k - 12); row = 0; } // top, right → left
                else { col = 0; row = k - 19; }                  // left, down
                out.set(x0 + col * cw + 3, y0 + row * ch + 3, x0 + (col + 1) * cw - 3, y0 + (row + 1) * ch - 3);
            }

            void draw(Canvas c, float dt) {
                update(dt);
                RectF q = new RectF();
                for (int k = 0; k < 24; k++) {
                    rect(k, q);
                    g.setColor(type[k] == 1 ? 0xFF1E293B : type[k] == 2 ? 0xFF065F46 : type[k] == 3 ? 0xFF7F1D1D : 0xFF334155);
                    c.drawRoundRect(q, 10, 10, g);
                    if (type[k] == 1) {
                        g.setColor(gcol[group[k]]);
                        c.drawRect(q.left, q.top, q.right, q.top + q.height() * 0.22f, g);
                        if (owner[k] >= 0) {
                            g.setStyle(Paint.Style.STROKE); g.setStrokeWidth(5); g.setColor(PCOL[owner[k]]);
                            c.drawRoundRect(q, 10, 10, g); g.setStyle(Paint.Style.FILL);
                        }
                        for (int hh = 0; hh < houses[k]; hh++) { g.setColor(0xFF22C55E); c.drawRect(q.left + 6 + hh * 14, q.top + 4, q.left + 16 + hh * 14, q.top + 14, g); }
                    }
                    fit(c, name[k], q.centerX(), q.centerY(), q.width() * 0.9f, H * 0.022f, 0xFFFFFFFF);
                    if (price[k] > 0) fit(c, "RM" + price[k], q.centerX(), q.bottom - q.height() * 0.2f, q.width() * 0.9f, H * 0.018f, 0xAAFFFFFF);
                }
                for (int i = 0; i < players; i++) {
                    if (out[i]) continue;
                    int k = Math.round(drawPos[i]) % 24;
                    rect(k, q);
                    g.setColor(PCOL[i]);
                    g.setShadowLayer(10, 0, 0, PCOL[i]);
                    c.drawCircle(q.left + q.width() * (0.25f + 0.5f * (i % 2)), q.top + q.height() * (0.55f + 0.25f * (i / 2)), H * 0.017f, g);
                    g.setShadowLayer(0, 0, 0, 0);
                }
                // centre panel
                float cx = W * 0.04f + H * 0.92f * 8 / 6 / 2, cy = H * 0.5f;
                text(c, "HARTANAH MALAYSIA", cx, cy - H * 0.17f, H * 0.04f, 0xFFFDE68A, Paint.Align.CENTER);
                drawDice(c, cx, cy - H * 0.04f, H * 0.09f, 2, true);
                text(c, "Giliran: Pemain " + (turn + 1) + (isBot(turn) ? " 🤖" : ""), cx, cy + H * 0.1f, H * 0.035f, PCOL[turn], Paint.Align.CENTER);
                text(c, "Pusingan " + (rounds + 1) + " / 20", cx, cy + H * 0.15f, H * 0.026f, 0xAAFFFFFF, Paint.Align.CENTER);
                // money
                float px = W * 0.04f + H * 0.92f * 8 / 6 + W * 0.03f;
                for (int i = 0; i < players; i++) {
                    float y = H * (0.12f + i * 0.13f);
                    r.set(px, y - H * 0.05f, W * 0.97f, y + H * 0.05f);
                    card(c, r, PCOL[i], i == turn);
                    text(c, "P" + (i + 1) + (isBot(i) ? " 🤖" : "") + (out[i] ? " (muflis)" : ""), px + H * 0.02f, y - H * 0.005f, H * 0.03f, 0xFFFFFFFF, Paint.Align.LEFT);
                    text(c, "RM" + money[i], W * 0.96f, y + H * 0.03f, H * 0.035f, 0xFFFDE68A, Paint.Align.RIGHT);
                }
            }
        }

        // ================================================================ Ular & Tangga Bijak

        final Ular ular = new Ular();

        final class Ular {
            final int[][] ladders = {{3, 21}, {8, 30}, {28, 55}, {36, 44}, {50, 67}, {71, 92}, {80, 99}};
            final int[][] snakes = {{17, 4}, {48, 26}, {62, 19}, {64, 42}, {87, 53}, {93, 73}, {95, 75}, {98, 79}};
            final int[] pos = new int[4];
            final float[] draw = new float[4];
            int phase, steps, pending, pick;
            float tmr;
            LearnBank.Q q;
            boolean climbing;   // question for a ladder (true) or a snake (false)

            void start() {
                for (int i = 0; i < 4; i++) { pos[i] = 1; draw[i] = 1; }
                phase = 0;
                say("Pemain 1: OK untuk baling dadu");
            }

            void cell(int n, RectF out) {
                float s = H * 0.088f, x0 = W * 0.04f, y0 = H * 0.06f;
                int i = n - 1, row = i / 10, col = row % 2 == 0 ? i % 10 : 9 - i % 10;
                out.set(x0 + col * s, y0 + (9 - row) * s, x0 + (col + 1) * s, y0 + (10 - row) * s);
            }

            void update(float dt) {
                if (rolling > 0) { rolling -= dt; return; }
                tmr += dt;
                for (int i = 0; i < players; i++) draw[i] += (pos[i] - draw[i]) * Math.min(1, dt * 10);
                if (phase == 0 && isBot(turn) && tmr > 0.8f) roll();
                if (phase == 1 && tmr > 0.2f && steps > 0) {
                    tmr = 0;
                    if (pos[turn] + 1 <= 100) pos[turn]++;
                    steps--;
                    if (steps == 0) arrive();
                }
                if (phase == 2 && isBot(turn) && tmr > 1.5f) answer(rnd.nextFloat() < 0.6f ? q.answer : (q.answer + 1) % q.options.length);
                if (phase == 3 && tmr > 1.6f) {
                    if (pending > 0) pos[turn] = pending;
                    pending = 0;
                    after();
                }
            }

            void roll() {
                die1 = 1 + rnd.nextInt(6);
                rolling = 0.5f;
                steps = Math.min(die1, 100 - pos[turn]);
                phase = 1;
                tmr = 0;
                if (steps == 0) after();
            }

            void arrive() {
                for (int[] l : ladders) if (l[0] == pos[turn]) { ask(true, l[1]); return; }
                for (int[] s : snakes) if (s[0] == pos[turn]) { ask(false, s[1]); return; }
                after();
            }

            void ask(boolean ladder, int to) {
                climbing = ladder;
                pending = to;
                int s = rnd.nextInt(4);
                q = bank.next(s, 3 + rnd.nextInt(3), new HashSet<String>());
                pick = 0;
                phase = 2;
                tmr = 0;
                say(ladder ? "Tangga! Jawab betul untuk naik 🪜" : "Ular! Jawab betul untuk selamat 🐍");
            }

            void answer(int i) {
                boolean right = i == q.answer;
                if (climbing && !right) pending = 0;          // no climb
                if (!climbing && right) pending = 0;          // escaped the snake
                say(right ? (climbing ? "Betul! Naik tangga!" : "Betul! Selamat dari ular!")
                        : (climbing ? "Salah. Jawapan: " + q.correct() : "Salah. Turun… Jawapan: " + q.correct()));
                phase = 3;
                tmr = 0;
            }

            void after() {
                if (pos[turn] >= 100) { endGame("Pemain " + (turn + 1) + " sampai ke 100!"); return; }
                turn = (turn + 1) % players;
                phase = 0;
                tmr = 0;
                if (!isBot(turn)) say("Pemain " + (turn + 1) + ": OK untuk baling dadu");
            }

            void key(int k, boolean ok) {
                if (isBot(turn)) return;
                if (phase == 0 && ok) roll();
                else if (phase == 2) {
                    if (k == KeyEvent.KEYCODE_DPAD_UP) pick = (pick + q.options.length - 1) % q.options.length;
                    if (k == KeyEvent.KEYCODE_DPAD_DOWN) pick = (pick + 1) % q.options.length;
                    if (ok) answer(pick);
                }
            }

            void draw(Canvas c, float dt) {
                update(dt);
                RectF q2 = new RectF(), b2 = new RectF();
                for (int n = 1; n <= 100; n++) {
                    cell(n, q2);
                    g.setColor(((n + (n - 1) / 10) % 2 == 0) ? 0xFF1E3A8A : 0xFF1E40AF);
                    c.drawRect(q2, g);
                    text(c, String.valueOf(n), q2.left + 6, q2.top + H * 0.022f, H * 0.018f, 0x88FFFFFF, Paint.Align.LEFT);
                }
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int[] l : ladders) {
                    cell(l[0], q2); cell(l[1], b2);
                    p.setStrokeWidth(H * 0.008f);
                    p.setColor(0xFFFBBF24);
                    float dx = H * 0.015f;
                    c.drawLine(q2.centerX() - dx, q2.centerY(), b2.centerX() - dx, b2.centerY(), p);
                    c.drawLine(q2.centerX() + dx, q2.centerY(), b2.centerX() + dx, b2.centerY(), p);
                    for (int k = 1; k < 6; k++) {
                        float f = k / 6f;
                        float x = q2.centerX() + (b2.centerX() - q2.centerX()) * f, y = q2.centerY() + (b2.centerY() - q2.centerY()) * f;
                        c.drawLine(x - dx, y, x + dx, y, p);
                    }
                }
                for (int[] s : snakes) {
                    cell(s[0], q2); cell(s[1], b2);
                    android.graphics.Path path = new android.graphics.Path();
                    path.moveTo(q2.centerX(), q2.centerY());
                    float mx = (q2.centerX() + b2.centerX()) / 2, my = (q2.centerY() + b2.centerY()) / 2;
                    path.cubicTo(mx + H * 0.08f, my - H * 0.06f, mx - H * 0.08f, my + H * 0.06f, b2.centerX(), b2.centerY());
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(H * 0.016f);
                    p.setColor(0xFF22C55E);
                    c.drawPath(path, p);
                    p.setStyle(Paint.Style.FILL);
                    g.setColor(0xFF15803D);
                    c.drawCircle(q2.centerX(), q2.centerY(), H * 0.017f, g);
                    g.setColor(0xFFFFFFFF);
                    c.drawCircle(q2.centerX() - 4, q2.centerY() - 3, 3, g);
                    c.drawCircle(q2.centerX() + 4, q2.centerY() - 3, 3, g);
                }
                p.setStrokeCap(Paint.Cap.BUTT);
                for (int i = 0; i < players; i++) {
                    int n = Math.max(1, Math.min(100, Math.round(draw[i])));
                    cell(n, q2);
                    g.setColor(PCOL[i]);
                    g.setShadowLayer(12, 0, 0, PCOL[i]);
                    c.drawCircle(q2.left + q2.width() * (0.3f + 0.4f * (i % 2)), q2.top + q2.height() * (0.4f + 0.35f * (i / 2)), H * 0.018f, g);
                    g.setShadowLayer(0, 0, 0, 0);
                }
                float px = W * 0.04f + H * 0.88f + W * 0.04f, pw = W * 0.96f - px;
                text(c, "ULAR & TANGGA BIJAK", px + pw / 2, H * 0.1f, H * 0.038f, 0xFFFDE68A, Paint.Align.CENTER);
                drawDice(c, px + pw / 2, H * 0.22f, H * 0.09f, 1, false);
                for (int i = 0; i < players; i++)
                    text(c, "P" + (i + 1) + (isBot(i) ? " 🤖" : "") + "  petak " + pos[i] + (i == turn ? "  ◀" : ""), px, H * (0.36f + i * 0.05f), H * 0.03f, PCOL[i], Paint.Align.LEFT);
                if (phase == 2 || (phase == 3 && q != null)) {
                    r.set(px, H * 0.56f, W * 0.97f, H * 0.97f);
                    g.setColor(0xCC000000);
                    c.drawRoundRect(r, H * 0.03f, H * 0.03f, g);
                    wrapped(c, q.prompt, px + H * 0.02f, H * 0.58f, pw - H * 0.04f, H * 0.028f, 0xFFFFFFFF, Layout.Alignment.ALIGN_NORMAL);
                    for (int i = 0; i < q.options.length; i++) {
                        boolean on = i == pick && phase == 2;
                        int col = phase == 3 ? (i == q.answer ? 0xFF4ADE80 : 0x88FFFFFF) : on ? 0xFF67E8F9 : 0xFFFFFFFF;
                        fit(c, (on ? "▶ " : "") + "ABCD".charAt(i) + ". " + q.options[i], px + pw / 2, H * (0.74f + i * 0.055f), pw * 0.9f, H * 0.03f, col);
                    }
                }
            }
        }

        // ================================================================ Congkak

        final Congkak congkak = new Congkak();

        static final class CongkakRules {
            /** 16 slots: 0-6 player 0's holes, 7 player 0's store, 8-14 player 1's holes, 15 player 1's store. */
            final int[] s = new int[16];

            void reset() { for (int i = 0; i < 16; i++) s[i] = (i == 7 || i == 15) ? 0 : 7; }

            CongkakRules copy() { CongkakRules c = new CongkakRules(); System.arraycopy(s, 0, c.s, 0, 16); return c; }

            static int store(int pl) { return pl == 0 ? 7 : 15; }

            boolean canMove(int pl) {
                for (int i = 0; i < 7; i++) if (s[pl * 8 + i] > 0) return true;
                return false;
            }

            /** Sows from hole h of player pl; returns true when pl moves again (last seed in own store). */
            boolean move(int pl, int h) {
                int at = pl * 8 + h, seeds = s[at];
                s[at] = 0;
                int guard = 0;
                while (guard++ < 500) {
                    while (seeds > 0) {
                        at = (at + 1) % 16;
                        if (at == store(1 - pl)) continue;   // skip the other player's store
                        s[at]++;
                        seeds--;
                    }
                    if (at == store(pl)) return true;
                    if (s[at] > 1) { seeds = s[at]; s[at] = 0; continue; }   // relay: pick up and go on
                    boolean mine = pl == 0 ? at < 7 : (at > 7 && at < 15);
                    if (mine) {
                        int opp = 14 - at;
                        if (s[opp] > 0) { s[store(pl)] += s[opp] + 1; s[opp] = 0; s[at] = 0; }
                    }
                    return false;
                }
                return false;
            }

            void finish() {
                for (int i = 0; i < 7; i++) { s[7] += s[i]; s[i] = 0; s[15] += s[8 + i]; s[8 + i] = 0; }
            }
        }

        final class Congkak {
            final CongkakRules g2 = new CongkakRules();
            int pick, phase;
            float tmr;

            void start() {
                g2.reset();
                pick = 0;
                phase = 0;
                tmr = 0;
                say("Pemain 1: pilih lubang (◀▶) dan tekan OK");
            }

            int best(int pl) {
                int bestH = -1, bestV = Integer.MIN_VALUE;
                for (int h = 0; h < 7; h++) {
                    if (g2.s[pl * 8 + h] == 0) continue;
                    CongkakRules c = g2.copy();
                    boolean again = c.move(pl, h);
                    int v = c.s[CongkakRules.store(pl)] - c.s[CongkakRules.store(1 - pl)] + (again ? 6 : 0);
                    if (v > bestV) { bestV = v; bestH = h; }
                }
                return bestH;
            }

            void play(int h) {
                if (g2.s[turn * 8 + h] == 0) { say("Lubang itu kosong"); return; }
                boolean again = g2.move(turn, h);
                if (!g2.canMove(0) || !g2.canMove(1)) {
                    g2.finish();
                    int a = g2.s[7], b = g2.s[15];
                    endGame(a == b ? "Seri! " + a + " : " + b : "Pemain " + (a > b ? 1 : 2) + " menang! " + Math.max(a, b) + " biji");
                    return;
                }
                if (again) say("Masuk rumah sendiri: main lagi!");
                else { turn = 1 - turn; say("Giliran Pemain " + (turn + 1)); }
                tmr = 0;
            }

            void key(int k, boolean ok) {
                if (isBot(turn)) return;
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) pick = Math.max(0, pick - 1);
                if (k == KeyEvent.KEYCODE_DPAD_RIGHT) pick = Math.min(6, pick + 1);
                if (ok) play(turn == 0 ? pick : 6 - pick);
            }

            void draw(Canvas c, float dt) {
                tmr += dt;
                if (isBot(turn) && tmr > 1.2f) { int h = best(turn); if (h >= 0) play(h); }
                float bw = W * 0.86f, bh = H * 0.42f, x0 = (W - bw) / 2, y0 = H * 0.3f;
                r.set(x0, y0, x0 + bw, y0 + bh);
                g.setColor(0xFF7C2D12);
                g.setShadowLayer(30, 0, 10, 0xAA000000);
                c.drawRoundRect(r, bh * 0.5f, bh * 0.5f, g);
                g.setShadowLayer(0, 0, 0, 0);
                float hr = bh * 0.16f, sx = x0 + bw * 0.18f, step = bw * 0.64f / 6;
                // stores: player 1 (index 15) left, player 0 (index 7) right
                drawPit(c, x0 + bw * 0.07f, y0 + bh / 2, bh * 0.3f, g2.s[15], PCOL[1], false);
                drawPit(c, x0 + bw * 0.93f, y0 + bh / 2, bh * 0.3f, g2.s[7], PCOL[0], false);
                for (int h = 0; h < 7; h++) {
                    // player 0 bottom row left→right (0..6); player 1 top row right→left (8..14)
                    boolean on0 = turn == 0 && pick == h && !isBot(0), on1 = turn == 1 && 6 - pick == h && !isBot(1);
                    drawPit(c, sx + h * step, y0 + bh * 0.73f, hr, g2.s[h], PCOL[0], on0);
                    drawPit(c, sx + (6 - h) * step, y0 + bh * 0.27f, hr, g2.s[8 + h], PCOL[1], on1);
                }
                text(c, "CONGKAK", W / 2, H * 0.12f, H * 0.06f, 0xFFFDE68A, Paint.Align.CENTER);
                text(c, "P2" + (isBot(1) ? " 🤖" : "") + ": " + g2.s[15] + " biji", x0, H * 0.24f, H * 0.035f, PCOL[1], Paint.Align.LEFT);
                text(c, "P1" + (isBot(0) ? " 🤖" : "") + ": " + g2.s[7] + " biji", x0 + bw, H * 0.82f, H * 0.035f, PCOL[0], Paint.Align.RIGHT);
                text(c, "Giliran Pemain " + (turn + 1) + "  ·  biji bergerak lawan jam, masuk rumah sendiri = main lagi", W / 2, H * 0.88f, H * 0.026f, 0xAAFFFFFF, Paint.Align.CENTER);
            }

            void drawPit(Canvas c, float cx, float cy, float rad, int n, int col, boolean on) {
                g.setColor(0xFF431407);
                c.drawCircle(cx, cy, rad, g);
                if (on) {
                    g.setStyle(Paint.Style.STROKE); g.setStrokeWidth(6); g.setColor(col);
                    g.setShadowLayer(14, 0, 0, col);
                    c.drawCircle(cx, cy, rad + 4, g);
                    g.setShadowLayer(0, 0, 0, 0); g.setStyle(Paint.Style.FILL);
                }
                Random seedR = new Random((long) cx * 31 + (long) cy);
                for (int k = 0; k < Math.min(n, 30); k++) {
                    float a = seedR.nextFloat() * 6.28f, d = seedR.nextFloat() * rad * 0.6f;
                    g.setColor(0xFFFDE68A);
                    c.drawCircle(cx + (float) Math.cos(a) * d, cy + (float) Math.sin(a) * d, rad * 0.11f, g);
                }
                fit(c, String.valueOf(n), cx, cy + rad + H * 0.03f, rad * 2, H * 0.028f, 0xFFFFFFFF);
            }
        }

        // ================================================================ Siapa Juara?

        final Juara juara = new Juara();
        Bitmap qr;
        String qrFor;

        void drawJuaraLobby(Canvas c) {
            text(c, "Setiap pemain imbas kod QR dengan telefon (Wi-Fi yang sama)", W / 2, H * 0.22f, H * 0.03f, 0xCCFFFFFF, Paint.Align.CENTER);
            if (party.url != null) {
                if (!party.url.equals(qrFor)) { qrFor = party.url; qr = qrBitmap(Qr.encode(party.url)); }
                float s = H * 0.42f;
                r.set(W * 0.27f - s / 2, H * 0.3f, W * 0.27f + s / 2, H * 0.3f + s);
                p.setColor(0xFFFFFFFF);
                c.drawRoundRect(r.left - 12, r.top - 12, r.right + 12, r.bottom + 12, 16, 16, p);
                p.setFilterBitmap(false);
                c.drawBitmap(qr, null, r, p);
            }
            for (int i = 0; i < Party.MAX; i++) {
                Party.Player pl = party.players[i];
                float y = H * (0.36f + i * 0.1f);
                g.setColor(pl.joined ? pl.color() : 0x33FFFFFF);
                c.drawCircle(W * 0.58f, y - H * 0.012f, H * 0.025f, g);
                String who = i == 0 ? (pl.phone ? "Remote + Telefon" : party.phoneFirst ? "Remote (imbas QR untuk telefon)" : "Remote TV")
                        : pl.joined ? "Telefon" : "Menunggu…";
                text(c, "Pemain " + (i + 1) + " · " + who, W * 0.62f, y, H * 0.032f,
                        pl.joined ? 0xFFFFFFFF : 0x66FFFFFF, Paint.Align.LEFT);
            }
            text(c, game >= ARKED ? Arked4.HELP[arkedId(game)] + "  ·  Tempat kosong dimain oleh TV"
                    : game == KANCIL ? "Remote: ◀▶ lari, OK lompat  ·  Telefon: kayu bedik lari, butang A lompat  ·  2 pemain = skrin atas/bawah"
                    : "Remote: ◀▶▲▼ pilih, OK jawab  ·  Telefon: kayu bedik pilih, A jawab", W / 2, H * 0.82f, H * 0.028f, 0xFF67E8F9, Paint.Align.CENTER);
            text(c, "◀▶  Pemain 1 guna telefon juga: " + (party.phoneFirst ? "YA" : "TIDAK"), W * 0.62f, H * 0.76f, H * 0.03f,
                    party.phoneFirst ? 0xFFFDE047 : 0xBBFFFFFF, Paint.Align.LEFT);
            text(c, "OK untuk mula", W / 2, H * 0.9f, H * 0.04f, 0xFF4ADE80, Paint.Align.CENTER);
        }

        static Bitmap qrBitmap(boolean[][] m) {
            int n = m.length, q = 4;
            Bitmap b = Bitmap.createBitmap(n + q * 2, n + q * 2, Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);
            for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) if (m[y][x]) b.setPixel(x + q, y + q, Color.BLACK);
            return b;
        }

        final class Juara {
            static final int ROUNDS = 12;
            final int[] score = new int[4], choice = new int[4], streak = new int[4], gained = new int[4];
            final long[] at = new long[4], aimAt = new long[4];
            final int[] lastA = new int[4], aim = new int[4];   // aim: each player's cursor on the 2×2 answers
            final boolean[] stickIdle = new boolean[4];
            final List<LearnBank.Q> qs = new ArrayList<>();
            int qNo;
            long start;
            boolean reveal;
            LearnBank.Q q;
            float pop;   // grows the new question in

            void start() {
                for (int i = 0; i < 4; i++) { score[i] = 0; streak[i] = 0; }
                // mostly general knowledge, never the same as the last games; every fourth from the school banks
                qs.clear();
                List<String[]> rows = JuaraBank.pick(getContext(), ROUNDS, rnd);
                for (int i = 0; i < ROUNDS; i++) {
                    if (i % 4 == 3 || i >= rows.size()) qs.add(bank.next(1 + rnd.nextInt(3), 3 + i / 4, new HashSet<String>()));
                    else qs.add(JuaraBank.asQ(rows.get(i), rnd));
                }
                qNo = 0;
                next();
            }

            void next() {
                q = qs.get(qNo);
                for (int i = 0; i < 4; i++) {
                    choice[i] = -1; at[i] = 0; aim[i] = 0; gained[i] = 0; stickIdle[i] = false;
                    lastA[i] = party.players[i].aPresses;
                }
                reveal = false;
                pop = 0;
                start = SystemClock.uptimeMillis();
            }

            int dirToOption(float x, float y) {
                if (Math.abs(x) < 0.5f && Math.abs(y) < 0.5f) return -1;
                if (Math.abs(y) >= Math.abs(x)) return y < 0 ? 0 : 2;
                return x > 0 ? 1 : 3;
            }

            void lock(int i, int o, long now) {
                if (choice[i] >= 0 || o < 0 || o >= q.options.length) return;
                choice[i] = o;
                at[i] = now;
                party.buzz(i, 40);
                sound.sfx(GameMusic.SFX_TICK);
            }

            void update() {
                long now = SystemClock.uptimeMillis();
                if (!reveal) {
                    for (int i = 0; i < Party.MAX; i++) {
                        Party.Player pl = party.players[i];
                        if (!pl.joined || !pl.phone || choice[i] >= 0) continue;
                        // the stick moves the cursor one answer per push (like the remote's arrows), A answers
                        boolean idle = Math.abs(pl.x) < 0.35f && Math.abs(pl.y) < 0.35f;
                        if (!idle && stickIdle[i]) {
                            if (Math.abs(pl.x) > Math.abs(pl.y)) aim[i] = move(aim[i], pl.x > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
                            else aim[i] = move(aim[i], pl.y > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP);
                            party.buzz(i, 12);
                            sound.sfx(GameMusic.SFX_TICK);
                        }
                        stickIdle[i] = idle;
                        if (pl.aPresses != lastA[i]) { lastA[i] = pl.aPresses; lock(i, aim[i], now); }
                    }
                    boolean all = true;
                    for (int i = 0; i < Party.MAX; i++) if (party.players[i].joined && choice[i] < 0) all = false;
                    if (all || now - start > 20000) {
                        reveal = true;
                        boolean anyRight = false;
                        for (int i = 0; i < Party.MAX; i++) {
                            if (choice[i] == q.answer) {
                                anyRight = true;
                                streak[i]++;
                                // 500 for a right answer, up to 500 more the faster it came, +100 for each right in a row
                                gained[i] = 500 + (int) Math.max(0, 500 - (at[i] - start) / 40) + Math.min(5, streak[i] - 1) * 100;
                                score[i] += gained[i];
                                party.buzz(i, 60);
                            } else streak[i] = 0;
                        }
                        sound.sfx(anyRight ? GameMusic.SFX_OK : GameMusic.SFX_WRONG);
                        start = now;
                    }
                } else if (now - start > 4000) {
                    qNo++;
                    if (qNo >= qs.size()) {
                        int best = 0;
                        for (int i = 0; i < Party.MAX; i++) if (party.players[i].joined && score[i] > score[best]) best = i;
                        sound.sfx(GameMusic.SFX_WIN);
                        endGame("Pemain " + (best + 1) + " Juara! " + score[best] + " mata");
                    } else next();
                }
            }

            /** Moves a cursor on the 2×2 grid: A B on top, C D below. */
            int move(int at, int k) {
                int n = q.options.length, col = at % 2, row = at / 2;
                if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) col = 1 - col;
                if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) row = 1 - row;
                int o = row * 2 + col;
                return o < n ? o : at;
            }

            /** The remote: arrows move player 1's cursor, OK answers. */
            void key(int k, boolean ok) {
                if (reveal || choice[0] >= 0) return;
                if (ok) { lock(0, aim[0], SystemClock.uptimeMillis()); return; }
                int o = move(aim[0], k);
                if (o != aim[0]) { aim[0] = o; sound.sfx(GameMusic.SFX_TICK); }
            }

            final RectF[] slot = {new RectF(), new RectF(), new RectF(), new RectF()};

            void draw(Canvas c, float dt) {
                update();
                pop = Math.min(1, pop + dt * 3);
                long now = SystemClock.uptimeMillis();
                // spotlights sweeping behind the stage
                for (int k = 0; k < 2; k++) {
                    float ang = (float) Math.sin(t * 0.7f + k * 2.5f) * 0.5f;
                    float bx = W * (k == 0 ? 0.15f : 0.85f);
                    android.graphics.Path beam = beamPath;
                    beam.reset();
                    beam.moveTo(bx, H * 1.05f);
                    beam.lineTo(bx + (float) Math.sin(ang - 0.12f) * H * 1.3f, H * 1.05f - (float) Math.cos(ang - 0.12f) * H * 1.3f);
                    beam.lineTo(bx + (float) Math.sin(ang + 0.12f) * H * 1.3f, H * 1.05f - (float) Math.cos(ang + 0.12f) * H * 1.3f);
                    beam.close();
                    g.setColor(k == 0 ? 0x1838BDF8 : 0x18F472B6);
                    c.drawPath(beam, g);
                }
                float pulse = 1 + 0.03f * (float) Math.sin(t * 4);
                text(c, "SIAPA JUARA?", W / 2, H * 0.075f, H * 0.045f * pulse, 0xFFFDE68A, Paint.Align.CENTER);
                text(c, "Soalan " + (qNo + 1) + " / " + qs.size() + "   ·   " + (q.explain != null && q.explain.length() < 14 ? q.explain : LearnBank.SUBJECTS[q.subject]),
                        W / 2, H * 0.115f, H * 0.026f, 0xBBFFFFFF, Paint.Align.CENTER);
                // the timer: a ring that empties
                float left = reveal ? 0 : 1 - Math.min(1, (now - start) / 20000f);
                float tcx = W * 0.92f, tcy = H * 0.1f, tr = H * 0.055f;
                g.setStyle(Paint.Style.STROKE);
                g.setStrokeWidth(H * 0.012f);
                g.setColor(0x33FFFFFF);
                c.drawCircle(tcx, tcy, tr, g);
                g.setColor(left > 0.3f ? 0xFF4ADE80 : 0xFFF87171);
                r.set(tcx - tr, tcy - tr, tcx + tr, tcy + tr);
                c.drawArc(r, -90, 360 * left, false, g);
                g.setStyle(Paint.Style.FILL);
                if (!reveal) text(c, String.valueOf((int) Math.ceil(left * 20)), tcx, tcy + H * 0.015f, H * 0.04f, 0xFFFFFFFF, Paint.Align.CENTER);
                // the question on top
                float qs0 = 0.9f + 0.1f * pop;
                r.set(W * 0.08f, H * 0.15f, W * 0.86f, H * 0.37f);
                c.save();
                c.scale(qs0, qs0, r.centerX(), r.centerY());
                card(c, r, 0xFF1E293B, false);
                wrapped(c, q.prompt, W * 0.11f, H * 0.18f, W * 0.72f, H * 0.044f, 0xFFFFFFFF, Layout.Alignment.ALIGN_CENTER);
                c.restore();
                // the answers in a 2×2 grid: A B / C D, each player's cursor a coloured ring of dots
                String[] letters = {"A", "B", "C", "D"};
                int[] oc = {0xFF2563EB, 0xFFDC2626, 0xFF16A34A, 0xFFD97706};
                for (int i = 0; i < q.options.length; i++) {
                    float x = W * (i % 2 == 0 ? 0.08f : 0.5f), y = H * (0.42f + (i / 2) * 0.19f);
                    RectF sl = slot[i];
                    sl.set(x, y, x + W * 0.4f, y + H * 0.16f);
                    boolean p1 = !reveal && (choice[0] >= 0 ? choice[0] == i : aim[0] == i);
                    int col = reveal ? (i == q.answer ? 0xFF16A34A : 0xFF334155) : oc[i];
                    card(c, sl, col, reveal ? i == q.answer : p1);
                    float ax = sl.left + H * 0.06f, ay = sl.centerY();
                    g.setColor(0x55000000);
                    c.drawCircle(ax, ay, H * 0.04f, g);
                    text(c, letters[i], ax, ay + H * 0.016f, H * 0.045f, 0xFFFFFFFF, Paint.Align.CENTER);
                    float tw = sl.width() - H * 0.14f;
                    fit(c, q.options[i], sl.left + H * 0.12f + tw / 2, ay, tw, H * 0.042f, 0xFFFFFFFF);
                    if (reveal) continue;
                    for (int pl = 0; pl < Party.MAX; pl++) {
                        if (!party.players[pl].joined) continue;
                        int on = choice[pl] >= 0 ? choice[pl] : aim[pl];
                        if (on != i) continue;
                        float dx = sl.right - H * 0.035f - pl * H * 0.045f, dy = sl.top + H * 0.035f;
                        g.setColor(PCOL[pl]);
                        c.drawCircle(dx, dy, H * 0.017f, g);
                        if (choice[pl] >= 0) text(c, "✔", dx, dy + H * 0.008f, H * 0.022f, 0xFF000000, Paint.Align.CENTER);
                    }
                }
                // the players along the bottom
                for (int i = 0; i < Party.MAX; i++) {
                    if (!party.players[i].joined) continue;
                    float x = W * (0.06f + i * 0.235f);
                    String st = reveal ? (choice[i] == q.answer ? "✓ +" + gained[i] : "✗") : choice[i] >= 0 ? "✔ sudah jawab" : "berfikir…";
                    g.setColor(PCOL[i]);
                    c.drawCircle(x, H * 0.905f, H * 0.018f, g);
                    text(c, "P" + (i + 1) + "  " + score[i] + "   " + st + (streak[i] >= 2 ? "  🔥" + streak[i] : ""),
                            x + H * 0.03f, H * 0.915f, H * 0.03f, 0xFFFFFFFF, Paint.Align.LEFT);
                }
                if (!reveal) text(c, "Remote: ◀▶▲▼ pilih, OK jawab   ·   Telefon: tolak kayu bedik untuk pilih, A jawab",
                        W / 2, H * 0.975f, H * 0.024f, 0x99FFFFFF, Paint.Align.CENTER);
            }
        }

        final android.graphics.Path beamPath = new android.graphics.Path();

        // ================================================================ Teka Kata

        final Teka teka = new Teka();

        final class Teka {
            final String KEYS = "QWERTYUIOPASDFGHJKLZXCVBNM";
            String word = "";
            final List<String> guesses = new ArrayList<>();
            String cur = "";
            boolean bm = true, daily = true, done;
            int kx, ky;   // keyboard cursor: row 0-2, plus row 3 = actions

            void start(boolean bmLang, boolean today) {
                bm = bmLang;
                daily = today;
                word = today ? WordList.daily(bm) : WordList.random(bm, rnd);
                guesses.clear();
                cur = "";
                done = false;
                kx = ky = 0;
                say(bm ? "Teka perkataan BM 5 huruf" : "Guess the 5-letter English word");
            }

            String row(int i) { return i == 0 ? "QWERTYUIOP" : i == 1 ? "ASDFGHJKL" : "ZXCVBNM"; }

            int state(String guess, int i) {   // 2 right place, 1 in word, 0 not
                if (word.charAt(i) == guess.charAt(i)) return 2;
                int inWord = 0, used = 0;
                char ch = guess.charAt(i);
                for (int k = 0; k < 5; k++) if (word.charAt(k) == ch && guess.charAt(k) != ch) inWord++;
                for (int k = 0; k < i; k++) if (guess.charAt(k) == ch && word.charAt(k) != ch) used++;
                return used < inWord ? 1 : 0;
            }

            int keyState(char ch) {
                int best = -1;
                for (String gs : guesses) for (int i = 0; i < 5; i++) if (gs.charAt(i) == ch) best = Math.max(best, state(gs, i));
                return best;
            }

            void press() {
                if (done) {
                    start(bm, false);
                    return;
                }
                if (ky == 3) {
                    if (kx == 0 && cur.length() > 0) cur = cur.substring(0, cur.length() - 1);
                    else if (kx == 1) submit();
                    else if (kx == 2) start(!bm, true);
                    return;
                }
                if (cur.length() < 5) cur += row(ky).charAt(kx);
                if (cur.length() == 5 && ky != 3) { ky = 3; kx = 1; }   // jump to SEMAK
            }

            void submit() {
                if (cur.length() < 5) { say("Perlukan 5 huruf"); return; }
                guesses.add(cur);
                if (cur.equals(word)) { done = true; say("Hebat! Perkataannya " + word + " 🎉"); }
                else if (guesses.size() >= 6) { done = true; say("Perkataannya ialah " + word); }
                cur = "";
                ky = 0;
                kx = 0;
            }

            void key(int k, boolean ok) {
                int rowLen = ky == 3 ? 3 : row(ky).length();
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) kx = (kx + rowLen - 1) % rowLen;
                if (k == KeyEvent.KEYCODE_DPAD_RIGHT) kx = (kx + 1) % rowLen;
                if (k == KeyEvent.KEYCODE_DPAD_UP) { ky = (ky + 3) % 4; kx = Math.min(kx, (ky == 3 ? 3 : row(ky).length()) - 1); }
                if (k == KeyEvent.KEYCODE_DPAD_DOWN) { ky = (ky + 1) % 4; kx = Math.min(kx, (ky == 3 ? 3 : row(ky).length()) - 1); }
                if (k >= KeyEvent.KEYCODE_A && k <= KeyEvent.KEYCODE_Z && cur.length() < 5 && !done) cur += (char) ('A' + k - KeyEvent.KEYCODE_A);
                if (k == KeyEvent.KEYCODE_DEL && cur.length() > 0) cur = cur.substring(0, cur.length() - 1);
                if (ok) press();
            }

            void draw(Canvas c, float dt) {
                text(c, (bm ? "TEKA KATA" : "WORD GUESS") + (daily ? "  ·  Harian" : "  ·  Latihan"), W / 2, H * 0.08f, H * 0.045f, 0xFFFDE68A, Paint.Align.CENTER);
                float s = H * 0.085f, gap = H * 0.012f, x0 = W / 2 - (5 * s + 4 * gap) / 2, y0 = H * 0.12f;
                for (int row = 0; row < 6; row++) {
                    String gs = row < guesses.size() ? guesses.get(row) : row == guesses.size() ? cur : "";
                    for (int i = 0; i < 5; i++) {
                        r.set(x0 + i * (s + gap), y0 + row * (s + gap), x0 + i * (s + gap) + s, y0 + row * (s + gap) + s);
                        int col = 0x22FFFFFF;
                        if (row < guesses.size()) { int st = state(gs, i); col = st == 2 ? 0xFF16A34A : st == 1 ? 0xFFCA8A04 : 0xFF475569; }
                        g.setColor(col);
                        c.drawRoundRect(r, 10, 10, g);
                        if (i < gs.length()) fit(c, String.valueOf(gs.charAt(i)), r.centerX(), r.centerY(), s, s * 0.6f, 0xFFFFFFFF);
                    }
                }
                float ky0 = y0 + 6 * (s + gap) + H * 0.02f, kw = H * 0.062f, kh = H * 0.062f;
                for (int rr = 0; rr < 3; rr++) {
                    String keys = row(rr);
                    float rx = W / 2 - keys.length() * (kw + 6) / 2;
                    for (int i = 0; i < keys.length(); i++) {
                        r.set(rx + i * (kw + 6), ky0 + rr * (kh + 6), rx + i * (kw + 6) + kw, ky0 + rr * (kh + 6) + kh);
                        int st = keyState(keys.charAt(i));
                        int col = st == 2 ? 0xFF16A34A : st == 1 ? 0xFFCA8A04 : st == 0 ? 0xFF1E293B : 0x44FFFFFF;
                        boolean on = ky == rr && kx == i;
                        card(c, r, on ? 0xFF67E8F9 : col, on);
                        if (!on) { g.setColor(col); c.drawRoundRect(r, H * 0.03f, H * 0.03f, g); }
                        fit(c, String.valueOf(keys.charAt(i)), r.centerX(), r.centerY(), kw, kh * 0.5f, 0xFFFFFFFF);
                    }
                }
                String[] acts = {"⌫ PADAM", "✔ SEMAK", bm ? "🌐 ENGLISH" : "🌐 BM"};
                for (int i = 0; i < 3; i++) {
                    r.set(W / 2 - W * 0.24f + i * W * 0.165f, ky0 + 3 * (kh + 6), W / 2 - W * 0.24f + i * W * 0.165f + W * 0.15f, ky0 + 3 * (kh + 6) + kh);
                    card(c, r, 0xFF8B5CF6, ky == 3 && kx == i);
                    fit(c, acts[i], r.centerX(), r.centerY(), r.width() * 0.9f, kh * 0.4f, 0xFFFFFFFF);
                }
                if (done) text(c, "OK untuk perkataan latihan baru", W / 2, H * 0.985f, H * 0.026f, 0xFF67E8F9, Paint.Align.CENTER);
                text(c, "🟩 betul tempat", W * 0.04f, H * 0.3f, H * 0.026f, 0xFF4ADE80, Paint.Align.LEFT);
                text(c, "🟨 ada, salah tempat", W * 0.04f, H * 0.35f, H * 0.026f, 0xFFFACC15, Paint.Align.LEFT);
                text(c, "⬛ tiada", W * 0.04f, H * 0.4f, H * 0.026f, 0xFFCBD5E1, Paint.Align.LEFT);
            }
        }

        // ================================================================ keys

        @Override public boolean onKeyDown(int k, KeyEvent e) {
            boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A;
            boolean back = k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_BUTTON_B;
            if (e.getRepeatCount() > 0 && (ok || back)) return true;
            switch (screen) {
                case MENU:
                    if (back) return false;
                    if (k == KeyEvent.KEYCODE_DPAD_LEFT) sel = (sel + NAMES.length - 1) % NAMES.length;
                    if (k == KeyEvent.KEYCODE_DPAD_RIGHT) sel = (sel + 1) % NAMES.length;
                    if (k == KeyEvent.KEYCODE_DPAD_DOWN) sel = sel + 5 < NAMES.length ? sel + 5 : sel % 5;
                    if (k == KeyEvent.KEYCODE_DPAD_UP) sel = sel - 5 >= 0 ? sel - 5 : Math.min(NAMES.length - 1, sel + ((NAMES.length - 1) / 5) * 5);
                    if (ok) {
                        game = sel;
                        setupRow = 0;
                        if (game == TEKA) startGame(); else screen = SETUP;
                    }
                    return true;
                case SETUP:
                    if (back) { screen = MENU; return true; }
                    if (game == JUARA || game == KANCIL || game >= ARKED) {
                        if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) party.phoneFirst = !party.phoneFirst;
                        if (ok) startGame();
                        return true;
                    }
                    int rows = players + 2;
                    if (k == KeyEvent.KEYCODE_DPAD_UP) setupRow = (setupRow + rows - 1) % rows;
                    if (k == KeyEvent.KEYCODE_DPAD_DOWN) setupRow = (setupRow + 1) % rows;
                    if (setupRow == 0 && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                        players += k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                        players = Math.max(2, Math.min(game == CONGKAK ? 2 : 4, players));
                    }
                    if (setupRow >= 1 && setupRow <= players && (ok || k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                        bot[setupRow - 1] = !bot[setupRow - 1];
                        if (setupRow == 1) bot[0] = false;   // player 1 is always a person
                    }
                    if ((setupRow == players + 1 && ok) || k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) startGame();
                    return true;
                case PLAY:
                    if (back) { screen = MENU; return true; }
                    if (game == HARTA) harta.key(k, ok, false);
                    else if (game == ULAR) ular.key(k, ok);
                    else if (game == CONGKAK) congkak.key(k, ok);
                    else if (game == JUARA) juara.key(k, ok);
                    else if (game == KANCIL) kancil.key(k, true);
                    else if (game >= ARKED) arked.key(k, true);
                    else teka.key(k, ok);
                    return true;
                default:
                    if (back) { screen = MENU; return true; }
                    if (ok) startGame();
                    return true;
            }
        }

        @Override public boolean onKeyUp(int k, KeyEvent e) {
            if (screen == PLAY && game == KANCIL) { kancil.key(k, false); return true; }
            if (screen == PLAY && game >= ARKED) { arked.key(k, false); return true; }
            return super.onKeyUp(k, e);
        }

        /** In Hartanah, Back is "no thanks" on a buy offer instead of leaving the game. */
        @Override public boolean dispatchKeyEvent(KeyEvent e) {
            if (screen == PLAY && game == HARTA && harta.phase == 2 && !isBot(turn)
                    && (e.getKeyCode() == KeyEvent.KEYCODE_BACK || e.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B)) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) harta.key(e.getKeyCode(), false, true);
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
    }
}
