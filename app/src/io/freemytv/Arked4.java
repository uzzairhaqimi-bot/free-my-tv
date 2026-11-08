package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.KeyEvent;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * Fork extra: "Arked 4 Pemain", four original arcade games for up to four players on one screen, each in a classic
 * genre: Lumba Pantas (top-down racing), Tikus Labirin (maze chase), Pertahanan Angkasa (co-op space shooter) and
 * Gelanggang Silat (platform brawler). Player 1 uses the remote (and, if chosen, a phone too); the others use phones
 * from the Party page. Empty seats are played by the TV in the racing, maze and silat games.
 */
final class Arked4 {
    static final int LUMBA = 0, LABIRIN = 1, ANGKASA = 2, SILAT = 3, WIRA = 4, PENDEKAR = 5, WAU = 6, LEBUHRAYA = 7,
            BUAH = 8, PULAU = 9, BELON = 10, LUNCUR = 11, TERBANG = 12, KUBU = 13;
    static final int COUNT = 14;

    /** One of the newer games, in its own file; it reads input and draws through this engine. */
    interface Mod {
        void build();
        void update(float dt);
        void draw(Canvas c, float t);
    }

    /** How many seats the TV fills, per game (0: only people play). */
    private static final int[] FILL = {4, 4, 0, -1, 0, 0, 0, 4, -1, 0, 4, 0, 0, 0};

    private Mod mod(int g) {
        switch (g) {
            case WIRA: return new ModWira(this);
            case PENDEKAR: return new ModPendekar(this);
            case BUAH: return new ModBuah(this);
            case BELON: return new ModBelon(this);
            default: return null;
        }
    }

    private Mod mod;
    static final String[] NAMES = {"Lumba Pantas", "Tikus Labirin", "Pertahanan Angkasa", "Gelanggang Silat",
            "Wira Hutan", "Pendekar Kampung", "Hujan Buah", "Perang Belon Air"};
    static final int[] IDS = {LUMBA, LABIRIN, ANGKASA, SILAT, WIRA, PENDEKAR, BUAH, BELON};   // the menu's order
    static final String[] HINTS = {"Lumba kereta 3 pusingan", "Kutip beras, lari dari kucing", "Bersama tembak ubur-ubur angkasa",
            "Tolak lawan keluar gelanggang", "Bersama selamatkan hutan, 5 kawasan", "Halau lanun dari kampung, 4 peringkat",
            "Padankan 4 buah, lawan 4 papan", "Baling belon air, 3 peta"};
    static final String[] HELP = {
            "◀▶ / kayu bedik: belok. Kereta laju sendiri. Lalu ⚡ untuk turbo!",
            "Arah / kayu bedik: gerak. Kutip beras. ★ = kucing takut, tangkap mereka!",
            "◀▶ / kayu bedik: gerak. Tembak automatik. Jangan kena buih!",
            "◀▶ gerak · ▲ / B lompat (2 kali) · OK / A serang. Jatuh = hilang nyawa.",
            "◀▶ gerak · ▲ / B lompat · tembak automatik. Kutip senjata baru!",
            "Arah gerak · OK / A tumbuk · ▲ / B lompat. Ketupat = tambah tenaga.",
            "",
            "",
            "◀▶ gerak · OK / A pusing · ▼ jatuh laju. 4 buah sama = hilang!",
            "",
            "Arah gerak · OK / A baling belon. Kena 3 kali = basah lencun!",
    };

    final Party party;
    final GameSound sound;
    final Random rnd = new Random();
    int game;
    boolean over;
    String result = "";
    float time, countdown;

    // ---------------------------------------------------------------- players and input

    static final class P {
        int seat, score, lives, lap, wp, place, kos;
        boolean on, bot, done, ground, face = true;
        float x, y, vx, vy, ang, speed, inv, turbo, cool, atk, dmg, jumps, power, fire, dbl;
        int dir = -1, want = -1, lastA, lastB;
        boolean stickUp;
    }

    final P[] ps = new P[4];
    private boolean kL, kR, kU, kD, kOk;
    private int okCount, upCount;

    Arked4(Party party, GameSound sound) {
        this.party = party;
        this.sound = sound;
        for (int i = 0; i < 4; i++) { ps[i] = new P(); ps[i].seat = i; }
        tx.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
    }

    void key(int k, boolean down) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT: kL = down; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: kR = down; break;
            case KeyEvent.KEYCODE_DPAD_UP: if (down && !kU) upCount++; kU = down; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: kD = down; break;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_BUTTON_A:
                if (down && !kOk) okCount++;
                kOk = down;
                break;
            default:
        }
    }

    float ix(P p) {
        if (p.seat == 0) {
            float v = (kR ? 1 : 0) - (kL ? 1 : 0);
            if (v != 0 || !party.players[0].phone) return v;
        }
        return party.players[p.seat].x;
    }

    float iy(P p) {
        if (p.seat == 0) {
            float v = (kD ? 1 : 0) - (kU ? 1 : 0);
            if (v != 0 || !party.players[0].phone) return v;
        }
        return party.players[p.seat].y;
    }

    /** "A" presses since last asked (remote OK, phone A). */
    boolean pressA(P p) {
        Party.Player pp = party.players[p.seat];
        int n = (p.seat == 0 ? okCount : 0) + (pp.phone ? pp.aPresses : 0);
        boolean hit = n != p.lastA;
        p.lastA = n;
        return hit;
    }

    /** "Jump" presses: remote ▲, phone B, or a phone stick pushed up. */
    boolean pressJump(P p) {
        Party.Player pp = party.players[p.seat];
        int n = (p.seat == 0 ? upCount : 0) + (pp.phone ? pp.bPresses : 0);
        boolean hit = n != p.lastB;
        p.lastB = n;
        if (pp.phone) {
            boolean up = pp.y < -0.6f;
            if (up && !p.stickUp) hit = true;
            p.stickUp = up;
        }
        return hit;
    }

    // ---------------------------------------------------------------- flow

    void start(int g, float W, float H) {
        game = g;
        over = false;
        time = 0;
        countdown = 3;
        int humans = 0;
        for (P p : ps) {
            p.on = p.seat == 0 || party.players[p.seat].joined;
            p.bot = false;
            if (p.on) humans++;
            p.score = 0; p.lap = 0; p.wp = 0; p.place = 0; p.kos = 0; p.done = false;
            p.inv = 0; p.turbo = 0; p.cool = 0; p.atk = 0; p.dmg = 0; p.power = 0; p.dbl = 0;
            p.vx = p.vy = 0; p.dir = p.want = -1;
            p.lastA = (p.seat == 0 ? okCount : 0) + party.players[p.seat].aPresses;
            p.lastB = (p.seat == 0 ? upCount : 0) + party.players[p.seat].bPresses;
        }
        // the TV fills empty seats where a crowd is more fun
        int want = FILL[g] >= 0 ? FILL[g] : Math.max(2, Math.min(4, humans + 1));
        for (P p : ps) if (!p.on && humans < want) { p.on = true; p.bot = true; humans++; }
        sized = false;
        size(W, H);
    }

    boolean sized;
    float W, H;

    private void size(float w, float h) {
        if (sized && w == W && h == H) return;
        sized = true;
        W = w;
        H = h;
        mod = game >= WIRA ? mod(game) : null;
        if (mod != null) mod.build();
        else if (game == LUMBA) buildTrack();
        else if (game == LABIRIN) buildMaze();
        else if (game == ANGKASA) startSpace();
        else startSilat();
    }

    void update(float dt) {
        if (over) return;
        if (countdown > 0) {
            float before = countdown;
            countdown -= dt;
            if ((int) Math.ceil(before) != (int) Math.ceil(countdown)) sound.sfx(countdown <= 0 ? GameMusic.SFX_OK : GameMusic.SFX_TICK);
            return;
        }
        time += dt;
        for (P p : ps) {
            if (p.on && !p.bot && p.seat > 0 && !party.players[p.seat].joined) p.on = false;   // a phone that left
        }
        if (mod != null) mod.update(dt);
        else if (game == LUMBA) lumba(dt);
        else if (game == LABIRIN) labirin(dt);
        else if (game == ANGKASA) angkasa(dt);
        else silat(dt);
    }

    void draw(Canvas c, float w, float h, float t) {
        size(w, h);
        tNow = t;
        if (mod != null) mod.draw(c, t);
        else if (game == LUMBA) drawLumba(c, t);
        else if (game == LABIRIN) drawLabirin(c, t);
        else if (game == ANGKASA) drawAngkasa(c, t);
        else drawSilat(c, t);
        if (countdown > 0) {
            tx.setTextAlign(Paint.Align.CENTER);
            float f = countdown - (float) Math.floor(countdown);
            tx.setTextSize(H * (0.18f + 0.08f * f));
            tx.setColor(Color.argb((int) (255 * Math.min(1, f * 2 + 0.3f)), 255, 255, 255));
            c.drawText(String.valueOf((int) Math.ceil(countdown)), W / 2, H * 0.55f, tx);
            tx.setTextSize(H * 0.032f);
            tx.setColor(0xFFFDE68A);
            c.drawText(HELP[game], W / 2, H * 0.68f, tx);
        }
    }

    void finish(String text) {
        over = true;
        result = text;
        sound.sfx(GameMusic.SFX_WIN);
    }

    String who(P p) { return (p.bot ? "TV " : "Pemain ") + (p.seat + 1); }

    // ---------------------------------------------------------------- drawing helpers

    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), tx = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();
    final RectF r = new RectF();
    Bitmap bg;

    float tNow;

    /** The player's pin above (x, y). */
    void label(Canvas c, P pl, float x, float y) {
        Badge.pin(c, pl.seat, pl.bot, x, y, H * 0.03f, tNow);
    }

    void hudBox(Canvas c, int i, String s) {
        float bw = W * 0.2f, x = W * 0.02f + i * (bw + W * 0.045f), y = H * 0.012f;
        r.set(x, y, x + bw, y + H * 0.05f);
        p.setColor(0x99000000);
        c.drawRoundRect(r, H * 0.02f, H * 0.02f, p);
        Badge.dot(c, i, ps[i].bot, x + H * 0.025f, y + H * 0.025f, H * 0.017f);
        tx.setTextAlign(Paint.Align.LEFT);
        tx.setTextSize(H * 0.026f);
        tx.setColor(0xFFFFFFFF);
        c.drawText(s, x + H * 0.05f, y + H * 0.035f, tx);
    }

    // ================================================================ LUMBA PANTAS: top-down racing

    private static final int N = 72, LAPS = 3;
    private final float[] tx0 = new float[N], ty0 = new float[N];
    private float hw;
    private int finished;
    private float firstFinish = -1;
    private final int[] pads = {14, 34, 55};

    private void buildTrack() {
        float cx = W / 2, cy = H * 0.54f, rx = W * 0.4f, ry = H * 0.36f;
        for (int i = 0; i < N; i++) {
            double a = i * Math.PI * 2 / N;
            tx0[i] = cx + rx * (float) (Math.cos(a) * (1 + 0.12 * Math.sin(3 * a)));
            ty0[i] = cy + ry * (float) (Math.sin(a) * (1 + 0.18 * Math.cos(2 * a)) - 0.08 * Math.sin(4 * a));
        }
        hw = H * 0.072f;
        // the scenery is drawn once
        bg = Bitmap.createBitmap((int) W, (int) H, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bg);
        Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
        a.setShader(new LinearGradient(0, 0, 0, H, 0xFF4D7C0F, 0xFF365314, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, a);
        a.setShader(null);
        Random rr = new Random(9);
        for (int i = 0; i < 400; i++) {   // grass tufts
            a.setColor(rr.nextBoolean() ? 0xFF65A30D : 0xFF3F6212);
            c.drawCircle(rr.nextFloat() * W, rr.nextFloat() * H, H * 0.004f, a);
        }
        Path track = new Path();
        track.moveTo(tx0[0], ty0[0]);
        for (int i = 1; i <= N; i++) track.lineTo(tx0[i % N], ty0[i % N]);
        a.setStyle(Paint.Style.STROKE);
        a.setStrokeJoin(Paint.Join.ROUND);
        a.setStrokeWidth(hw * 2 + H * 0.02f);
        a.setColor(0xFFFFFFFF);
        c.drawPath(track, a);
        a.setColor(0xFFDC2626);
        a.setPathEffect(new DashPathEffect(new float[]{H * 0.025f, H * 0.025f}, 0));
        c.drawPath(track, a);
        a.setPathEffect(null);
        a.setStrokeWidth(hw * 2);
        a.setColor(0xFF374151);
        c.drawPath(track, a);
        a.setStrokeWidth(H * 0.004f);
        a.setColor(0x88FFFFFF);
        a.setPathEffect(new DashPathEffect(new float[]{H * 0.03f, H * 0.03f}, 0));
        c.drawPath(track, a);
        a.setPathEffect(null);
        a.setStyle(Paint.Style.FILL);
        // trees inside and around, never on the road
        for (int i = 0; i < 70; i++) {
            float x = rr.nextFloat() * W, y = H * 0.08f + rr.nextFloat() * H * 0.92f;
            if (distTrack(x, y, -1) < hw * 1.6f) continue;
            float s = H * (0.02f + rr.nextFloat() * 0.02f);
            a.setColor(0x55000000);
            c.drawCircle(x + s * 0.3f, y + s * 0.3f, s, a);
            a.setColor(rr.nextBoolean() ? 0xFF166534 : 0xFF15803D);
            c.drawCircle(x, y, s, a);
            a.setColor(0x3322C55E);
            c.drawCircle(x - s * 0.3f, y - s * 0.3f, s * 0.5f, a);
        }
        // the start line, chequered
        float sx = tx0[0], sy = ty0[0], dx = tx0[1] - tx0[N - 1], dy = ty0[1] - ty0[N - 1], len = (float) Math.hypot(dx, dy);
        float nx = -dy / len, ny = dx / len, cell = hw / 4;
        for (int i = -4; i < 4; i++) for (int j = 0; j < 2; j++) {
            a.setColor((i + j) % 2 == 0 ? 0xFFFFFFFF : 0xFF111111);
            float px = sx + nx * cell * (i + 0.5f) + dx / len * cell * (j - 1), py = sy + ny * cell * (i + 0.5f) + dy / len * cell * (j - 1);
            c.drawRect(px - cell / 2, py - cell / 2, px + cell / 2, py + cell / 2, a);
        }
        // the cars on the grid, behind the line
        int k = 0;
        for (P pl : ps) {
            if (!pl.on) continue;
            int back = N - 2 - (k / 2) * 2;
            float bx = tx0[back], by = ty0[back];
            float side = (k % 2 == 0 ? -1 : 1) * hw * 0.45f;
            pl.x = bx + nx * side;
            pl.y = by + ny * side;
            pl.ang = (float) Math.atan2(dy, dx);
            pl.speed = 0;
            pl.wp = back;
            pl.lap = -1;   // the line is crossed once at the start
            k++;
        }
        finished = 0;
        firstFinish = -1;
    }

    /** Distance from a point to the road's centre line; near = the waypoint to search around, or -1 for all. */
    private float distTrack(float x, float y, int near) {
        float best = Float.MAX_VALUE;
        int from = near < 0 ? 0 : near - 5, to = near < 0 ? N : near + 5;
        for (int j = from; j < to; j++) {
            int i = ((j % N) + N) % N, i2 = (i + 1) % N;
            float ax = tx0[i], ay = ty0[i], bx = tx0[i2] - ax, by = ty0[i2] - ay;
            float tt = Math.max(0, Math.min(1, ((x - ax) * bx + (y - ay) * by) / (bx * bx + by * by)));
            float d = (float) Math.hypot(x - ax - bx * tt, y - ay - by * tt);
            if (d < best) { best = d; if (near >= 0) bestWp = i; }
        }
        return best;
    }

    private int bestWp;

    private void lumba(float dt) {
        float vmax = H * 0.62f;
        for (P pl : ps) {
            if (!pl.on || pl.done) {
                if (pl.on) pl.speed *= 0.97f;
                continue;
            }
            float steer;
            if (pl.bot) {
                int ahead = (pl.wp + 4) % N;
                float want = (float) Math.atan2(ty0[ahead] - pl.y, tx0[ahead] - pl.x);
                float d = angleDiff(want, pl.ang);
                steer = Math.max(-1, Math.min(1, d * 2.5f));
            } else steer = ix(pl);
            float d = distTrack(pl.x, pl.y, pl.wp);
            int newWp = bestWp;
            boolean grass = d > hw;
            float top = vmax * (pl.bot ? 0.9f + 0.02f * pl.seat : 1f) * (grass ? 0.45f : 1f) * (pl.turbo > 0 ? 1.5f : 1f);
            pl.speed += (top - pl.speed) * Math.min(1, dt * (pl.speed < top ? 1.2f : 3f));
            pl.ang += steer * dt * 2.8f * Math.min(1, pl.speed / (vmax * 0.3f));
            pl.x += (float) Math.cos(pl.ang) * pl.speed * dt;
            pl.y += (float) Math.sin(pl.ang) * pl.speed * dt;
            pl.x = Math.max(0, Math.min(W, pl.x));
            pl.y = Math.max(0, Math.min(H, pl.y));
            if (pl.turbo > 0) pl.turbo -= dt;
            // laps: crossing waypoint 0 forwards
            if (newWp != pl.wp) {
                int diff = ((newWp - pl.wp) % N + N) % N;
                if (diff > 0 && diff < N / 2) {
                    if (pl.wp > N - 10 && newWp < 10) {
                        pl.lap++;
                        if (pl.lap >= 1 && !pl.bot) party.buzz(pl.seat, 60);
                        if (pl.lap >= LAPS) {
                            pl.done = true;
                            pl.place = ++finished;
                            if (firstFinish < 0) firstFinish = time;
                            sound.sfx(pl.bot ? GameMusic.SFX_TICK : GameMusic.SFX_WIN);
                        }
                    }
                } else if (pl.wp < 10 && newWp > N - 10) pl.lap--;   // went back over the line
                pl.wp = newWp;
            }
            for (int pd : pads) {
                if (Math.hypot(pl.x - tx0[pd], pl.y - ty0[pd]) < hw * 0.6f && pl.turbo <= 0) {
                    pl.turbo = 1.3f;
                    if (!pl.bot) { sound.sfx(GameMusic.SFX_JUMP); party.buzz(pl.seat, 40); }
                }
            }
        }
        // bumping
        float cr = H * 0.03f;
        for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) {
            P a = ps[i], b = ps[j];
            if (!a.on || !b.on) continue;
            float dx = b.x - a.x, dy = b.y - a.y, d = (float) Math.hypot(dx, dy);
            if (d > 0 && d < cr * 2) {
                float push = (cr * 2 - d) / 2;
                a.x -= dx / d * push; a.y -= dy / d * push;
                b.x += dx / d * push; b.y += dy / d * push;
                a.speed *= 0.97f; b.speed *= 0.97f;
            }
        }
        // places while racing
        int k = 0;
        int humans = 0, humansDone = 0;
        for (P pl : ps) {
            if (!pl.on) continue;
            if (!pl.bot) { humans++; if (pl.done) humansDone++; }
            if (pl.done) continue;
            int rank = finished + 1;
            for (P o : ps) if (o != pl && o.on && !o.done && prog(o) > prog(pl)) rank++;
            pl.place = rank;
            k++;
        }
        if ((humans > 0 && humansDone == humans) || (firstFinish >= 0 && time - firstFinish > 25)) {
            P best = null;
            for (P pl : ps) if (pl.on && (best == null || pl.place < best.place)) best = pl;
            finish(best == null ? "Tamat!" : who(best) + " menang perlumbaan! 🏁");
        }
    }

    private float prog(P pl) { return pl.lap * N + pl.wp; }

    private static float angleDiff(float a, float b) {
        float d = a - b;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return d;
    }

    private void drawLumba(Canvas c, float t) {
        if (bg != null) c.drawBitmap(bg, 0, 0, null);
        for (int pd : pads) {   // turbo pads glow
            p.setColor(Color.argb((int) (150 + 100 * Math.sin(t * 8)), 250, 204, 21));
            c.drawCircle(tx0[pd], ty0[pd], hw * 0.4f, p);
            tx.setTextAlign(Paint.Align.CENTER);
            tx.setTextSize(hw * 0.5f);
            tx.setColor(0xFF111827);
            c.drawText("⚡", tx0[pd], ty0[pd] + hw * 0.18f, tx);
        }
        for (P pl : ps) {
            if (!pl.on) continue;
            Badge.ring(c, pl.seat, pl.x, pl.y, H * 0.038f, t);
            car(c, pl.x, pl.y, pl.ang, Party.COLORS[pl.seat], pl.turbo > 0, t);
            label(c, pl, pl.x, pl.y - H * 0.03f);
        }
        for (P pl : ps) {
            if (!pl.on) continue;
            String s = pl.done ? "Tamat #" + pl.place : "Pusingan " + Math.max(1, Math.min(LAPS, pl.lap + 1)) + "/" + LAPS + "   #" + pl.place;
            hudBox(c, pl.seat, (pl.bot ? "TV  " : "") + s);
        }
    }

    private void car(Canvas c, float x, float y, float ang, int col, boolean turbo, float t) {
        float L = H * 0.055f, w = H * 0.03f;
        c.save();
        c.translate(x, y);
        c.rotate((float) Math.toDegrees(ang));
        if (turbo) {
            p.setColor(Color.argb(200, 251, 146, 60));
            c.drawCircle(-L * 0.6f, 0, w * (0.4f + 0.2f * (float) Math.sin(t * 40)), p);
        }
        p.setColor(0x55000000);
        c.drawRoundRect(-L / 2 + 3, -w / 2 + 4, L / 2 + 3, w / 2 + 4, w * 0.3f, w * 0.3f, p);
        p.setColor(0xFF111111);
        c.drawRect(-L * 0.35f, -w * 0.6f, -L * 0.15f, w * 0.6f, p);
        c.drawRect(L * 0.18f, -w * 0.6f, L * 0.38f, w * 0.6f, p);
        p.setColor(col);
        c.drawRoundRect(-L / 2, -w / 2, L / 2, w / 2, w * 0.35f, w * 0.35f, p);
        p.setColor(0xCC1E293B);
        c.drawRoundRect(-L * 0.05f, -w * 0.35f, L * 0.22f, w * 0.35f, w * 0.12f, w * 0.12f, p);
        p.setColor(0xFFFFFFFF);
        c.drawRect(-L * 0.45f, -w * 0.08f, -L * 0.1f, w * 0.08f, p);
        c.restore();
    }

    // ================================================================ TIKUS LABIRIN: maze chase

    private static final int MC = 27, MR = 15;
    private final boolean[][] wall = new boolean[MR][MC];
    private final byte[][] dot = new byte[MR][MC];   // 0 none, 1 rice, 2 star
    private float cell, ox, oy;
    private int dotsLeft;
    private final float[][] cats = new float[3][6];   // x, y, dir, home timer, scared, speed
    private final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};

    private void buildMaze() {
        for (boolean[] row : wall) java.util.Arrays.fill(row, true);
        ArrayDeque<int[]> st = new ArrayDeque<>();
        wall[1][1] = false;
        st.push(new int[]{1, 1});
        while (!st.isEmpty()) {
            int[] at = st.peek();
            int[] order = {0, 1, 2, 3};
            for (int i = 3; i > 0; i--) { int j = rnd.nextInt(i + 1); int tt = order[i]; order[i] = order[j]; order[j] = tt; }
            boolean moved = false;
            for (int d : order) {
                int nx = at[0] + DX[d] * 2, ny = at[1] + DY[d] * 2;
                if (nx > 0 && nx < MC - 1 && ny > 0 && ny < MR - 1 && wall[ny][nx]) {
                    wall[at[1] + DY[d]][at[0] + DX[d]] = false;
                    wall[ny][nx] = false;
                    st.push(new int[]{nx, ny});
                    moved = true;
                    break;
                }
            }
            if (!moved) st.pop();
        }
        // knock down some walls: loops make chases fun
        for (int y = 1; y < MR - 1; y++) for (int x = 1; x < MC - 1; x++) {
            if (!wall[y][x]) continue;
            boolean h = !wall[y][x - 1] && !wall[y][x + 1], v = !wall[y - 1][x] && !wall[y + 1][x];
            if ((h ^ v) && rnd.nextFloat() < 0.18f) wall[y][x] = false;
        }
        // the cats' pen in the middle
        for (int y = MR / 2 - 1; y <= MR / 2 + 1; y++) for (int x = MC / 2 - 2; x <= MC / 2 + 2; x++) wall[y][x] = false;
        cell = Math.min(W / MC, H * 0.9f / MR);
        ox = (W - cell * MC) / 2;
        oy = H * 0.08f + (H * 0.92f - cell * MR) / 2;
        dotsLeft = 0;
        for (int y = 0; y < MR; y++) for (int x = 0; x < MC; x++) {
            dot[y][x] = (byte) (wall[y][x] ? 0 : 1);
            if (dot[y][x] == 1) dotsLeft++;
        }
        int[][] stars = {{1, MR / 2}, {MC - 2, MR / 2}, {MC / 2, 1}, {MC / 2, MR - 2}};
        for (int[] s : stars) if (!wall[s[1]][s[0]]) dot[s[1]][s[0]] = 2;
        int[][] corners = {{1, 1}, {MC - 2, 1}, {1, MR - 2}, {MC - 2, MR - 2}};
        for (P pl : ps) {
            pl.x = corners[pl.seat][0];
            pl.y = corners[pl.seat][1];
            pl.dir = pl.want = -1;
            if (dot[(int) pl.y][(int) pl.x] == 1) { dot[(int) pl.y][(int) pl.x] = 0; dotsLeft--; }
        }
        for (int i = 0; i < cats.length; i++) {
            cats[i][0] = MC / 2 - 1 + i; cats[i][1] = MR / 2; cats[i][2] = 3; cats[i][3] = 2 + i * 2; cats[i][4] = 0; cats[i][5] = 3.4f;
        }
        bg = Bitmap.createBitmap((int) W, (int) H, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bg);
        Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
        a.setShader(new LinearGradient(0, 0, 0, H, 0xFF1C1917, 0xFF292524, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, a);
        a.setShader(null);
        for (int y = 0; y < MR; y++) for (int x = 0; x < MC; x++) {
            float px = ox + x * cell, py = oy + y * cell;
            if (wall[y][x]) {
                a.setColor(0xFF166534);
                c.drawRoundRect(px + 1, py + 1, px + cell - 1, py + cell - 1, cell * 0.3f, cell * 0.3f, a);
                a.setColor(0xFF22C55E);
                c.drawCircle(px + cell * 0.35f, py + cell * 0.35f, cell * 0.15f, a);
            } else {
                a.setColor((x + y) % 2 == 0 ? 0xFFD6C7A1 : 0xFFCDBD94);
                c.drawRect(px, py, px + cell, py + cell, a);
            }
        }
    }

    private final float[] xy = new float[2];

    private static boolean near(float v) { return Math.abs(v - Math.round(v)) < 1e-3f; }

    /** Moves xy along dir by step, stopping at the next cell centre; returns the step left there, or -1 if used up. */
    private float glide(float[] xy, int dir, float step) {
        boolean hz = dir == 0 || dir == 2;
        float pos = hz ? xy[0] : xy[1];
        int sg = hz ? DX[dir] : DY[dir];
        float target = near(pos) ? Math.round(pos) + sg : (sg > 0 ? (float) Math.ceil(pos) : (float) Math.floor(pos));
        float dist = Math.abs(target - pos);
        float left;
        if (step >= dist) { pos = target; left = step - dist; } else { pos += sg * step; left = -1; }
        if (hz) xy[0] = pos; else xy[1] = pos;
        return left;
    }

    private boolean open(int x, int y) { return x >= 0 && x < MC && y >= 0 && y < MR && !wall[y][x]; }

    /** First step of the shortest path from (x,y) to a cell where goal is true; -1 if none. */
    private int bfs(int x, int y, int goal, int avoidDir) {
        int[] from = bfsFrom;
        java.util.Arrays.fill(from, -2);
        int head = 0, tail = 0;
        from[y * MC + x] = -1;
        queue[tail++] = y * MC + x;
        while (head < tail) {
            int at = queue[head++], ax = at % MC, ay = at / MC;
            if (at != y * MC + x && isGoal(ax, ay, goal)) {
                while (from[at] != y * MC + x && from[at] >= 0) at = from[at];
                int dx = at % MC - x, dy = at / MC - y;
                for (int d = 0; d < 4; d++) if (DX[d] == dx && DY[d] == dy) return d;
                return -1;
            }
            for (int d = 0; d < 4; d++) {
                if (at == y * MC + x && d == avoidDir) continue;
                int nx = ax + DX[d], ny = ay + DY[d];
                if (!open(nx, ny) || from[ny * MC + nx] != -2) continue;
                from[ny * MC + nx] = at;
                queue[tail++] = ny * MC + nx;
            }
        }
        return -1;
    }

    private final int[] bfsFrom = new int[MR * MC], queue = new int[MR * MC];

    private boolean isGoal(int x, int y, int goal) {
        if (goal == 0) return dot[y][x] > 0;
        for (P pl : ps) if (pl.on && (int) (pl.x + 0.5f) == x && (int) (pl.y + 0.5f) == y) return true;
        return false;
    }

    private void labirin(float dt) {
        for (P pl : ps) {
            if (!pl.on) continue;
            if (pl.inv > 0) pl.inv -= dt;
            if (pl.power > 0) pl.power -= dt;
            if (!pl.bot) {
                float sx = ix(pl), sy = iy(pl);
                if (Math.abs(sx) > 0.4f || Math.abs(sy) > 0.4f) pl.want = Math.abs(sx) > Math.abs(sy) ? (sx > 0 ? 0 : 2) : (sy > 0 ? 1 : 3);
            }
            if (pl.want >= 0 && pl.dir >= 0 && pl.want == (pl.dir + 2) % 4) pl.dir = pl.want;   // reverse at any time
            float step = (pl.bot ? 4.1f : 4.6f) * dt;
            for (int guard = 0; guard < 4; guard++) {
                if (near(pl.x) && near(pl.y)) {   // at a cell's centre: eat, then turn or stop
                    int cx = Math.round(pl.x), cy = Math.round(pl.y);
                    pl.x = cx; pl.y = cy;
                    if (dot[cy][cx] > 0) {
                        if (dot[cy][cx] == 2) { pl.power = 7; sound.sfx(GameMusic.SFX_OK); }
                        else { pl.score++; dotsLeft--; if (!pl.bot && pl.score % 5 == 0) sound.sfx(GameMusic.SFX_COIN); }
                        dot[cy][cx] = 0;
                    }
                    if (pl.bot) pl.want = bfs(cx, cy, 0, -1);
                    if (pl.want >= 0 && open(cx + DX[pl.want], cy + DY[pl.want])) pl.dir = pl.want;
                    else if (pl.dir >= 0 && !open(cx + DX[pl.dir], cy + DY[pl.dir])) pl.dir = -1;
                }
                if (pl.dir < 0) break;
                xy[0] = pl.x; xy[1] = pl.y;
                step = glide(xy, pl.dir, step);
                pl.x = xy[0]; pl.y = xy[1];
                if (step < 0) break;
            }
        }
        boolean anyPower = false;
        for (P pl : ps) if (pl.on && pl.power > 0) anyPower = true;
        for (float[] k : cats) {
            if (k[3] > 0) { k[3] -= dt; continue; }
            k[4] = anyPower ? 1 : 0;
            float step = (k[4] > 0 ? 2.6f : k[5]) * dt;
            for (int guard = 0; guard < 4; guard++) {
                if (near(k[0]) && near(k[1])) {
                    int cx = Math.round(k[0]), cy = Math.round(k[1]);
                    k[0] = cx; k[1] = cy;
                    int back = ((int) k[2] + 2) % 4, d;
                    if (k[4] > 0 || rnd.nextFloat() < 0.3f) {   // scared, or wandering: a random way, not back
                        int tries = 0;
                        do { d = rnd.nextInt(4); } while ((d == back || !open(cx + DX[d], cy + DY[d])) && ++tries < 12);
                        if (tries >= 12) d = back;
                    } else {
                        d = bfs(cx, cy, 1, back);
                        if (d < 0) d = back;
                    }
                    if (!open(cx + DX[d], cy + DY[d])) break;
                    k[2] = d;
                }
                xy[0] = k[0]; xy[1] = k[1];
                step = glide(xy, (int) k[2], step);
                k[0] = xy[0]; k[1] = xy[1];
                if (step < 0) break;
            }
            for (P pl : ps) {
                if (!pl.on || pl.inv > 0) continue;
                if (Math.hypot(pl.x - k[0], pl.y - k[1]) < 0.6f) {
                    if (pl.power > 0) {   // caught a cat: back to the pen
                        pl.score += 10;
                        k[0] = MC / 2; k[1] = MR / 2; k[3] = 4;
                        sound.sfx(GameMusic.SFX_WIN);
                        party.buzz(pl.seat, 80);
                    } else {
                        pl.score = Math.max(0, pl.score - 5);
                        int[][] corners = {{1, 1}, {MC - 2, 1}, {1, MR - 2}, {MC - 2, MR - 2}};
                        pl.x = corners[pl.seat][0]; pl.y = corners[pl.seat][1]; pl.dir = -1; pl.inv = 2;
                        sound.sfx(GameMusic.SFX_WRONG);
                        party.buzz(pl.seat, 200);
                    }
                }
            }
        }
        if (dotsLeft <= 0 || time > 150) {
            P best = null;
            for (P pl : ps) if (pl.on && (best == null || pl.score > best.score)) best = pl;
            finish(best == null ? "Tamat!" : who(best) + " tikus paling hebat: " + best.score + " mata!");
        }
    }

    private void drawLabirin(Canvas c, float t) {
        if (bg != null) c.drawBitmap(bg, 0, 0, null);
        for (int y = 0; y < MR; y++) for (int x = 0; x < MC; x++) {
            if (dot[y][x] == 0) continue;
            float px = ox + (x + 0.5f) * cell, py = oy + (y + 0.5f) * cell;
            if (dot[y][x] == 1) {
                p.setColor(0xFFFFFBEB);
                c.drawOval(px - cell * 0.09f, py - cell * 0.05f, px + cell * 0.09f, py + cell * 0.05f, p);
            } else {
                p.setColor(Color.argb((int) (200 + 55 * Math.sin(t * 6)), 250, 204, 21));
                tx.setTextAlign(Paint.Align.CENTER);
                tx.setTextSize(cell * 0.8f);
                tx.setColor(p.getColor());
                c.drawText("★", px, py + cell * 0.28f, tx);
            }
        }
        for (float[] k : cats) {
            float px = ox + (k[0] + 0.5f) * cell, py = oy + (k[1] + 0.5f) * cell;
            cat(c, px, py, cell * 0.45f, k[4] > 0, k[3] > 0);
        }
        for (P pl : ps) {
            if (!pl.on || (pl.inv > 0 && ((int) (pl.inv * 10)) % 2 == 0)) continue;
            float px = ox + (pl.x + 0.5f) * cell, py = oy + (pl.y + 0.5f) * cell;
            Badge.ring(c, pl.seat, px, py, cell * 0.55f, t);
            mouse(c, px, py, cell * 0.42f, Party.COLORS[pl.seat], pl.dir, pl.power > 0, t);
            label(c, pl, px, py - cell * 0.45f);
        }
        long left = Math.max(0, 150 - (long) time);
        for (P pl : ps) if (pl.on) hudBox(c, pl.seat, (pl.bot ? "TV  " : "") + pl.score + " mata");
        tx.setTextAlign(Paint.Align.RIGHT);
        tx.setTextSize(H * 0.026f);
        tx.setColor(0xCCFFFFFF);
        c.drawText(left / 60 + ":" + String.format(java.util.Locale.US, "%02d", left % 60), W * 0.99f, H * 0.045f, tx);
    }

    private void mouse(Canvas c, float x, float y, float s, int col, int dir, boolean power, float t) {
        c.save();
        c.translate(x, y);
        c.rotate(dir == 1 ? 90 : dir == 2 ? 180 : dir == 3 ? 270 : 0);
        if (power) { p.setColor(Color.argb(90, 250, 204, 21)); c.drawCircle(0, 0, s * 1.4f, p); }
        p.setStrokeWidth(s * 0.12f);
        p.setColor(0xFFF9A8D4);
        c.drawLine(-s * 0.8f, 0, -s * 1.3f, (float) Math.sin(t * 12) * s * 0.3f, p);   // tail
        p.setColor(col);   // the mouse wears its player's colour
        c.drawOval(-s * 0.85f, -s * 0.55f, s * 0.6f, s * 0.55f, p);
        p.setColor(0xFFE5E7EB);
        c.drawCircle(s * 0.55f, 0, s * 0.4f, p);
        p.setColor(0xFFF9A8D4);
        c.drawCircle(s * 0.35f, -s * 0.45f, s * 0.22f, p);
        c.drawCircle(s * 0.35f, s * 0.45f, s * 0.22f, p);
        p.setColor(0xFF111827);
        c.drawCircle(s * 0.65f, -s * 0.15f, s * 0.08f, p);
        c.drawCircle(s * 0.65f, s * 0.15f, s * 0.08f, p);
        p.setColor(0xFFEC4899);
        c.drawCircle(s * 0.95f, 0, s * 0.1f, p);
        c.restore();
    }

    private void cat(Canvas c, float x, float y, float s, boolean scared, boolean home) {
        int body = scared ? 0xFF60A5FA : home ? 0xFF78716C : 0xFFF97316;
        p.setColor(body);
        path.reset();
        path.moveTo(x - s * 0.8f, y - s * 0.2f); path.lineTo(x - s * 0.6f, y - s); path.lineTo(x - s * 0.15f, y - s * 0.55f); path.close();
        path.moveTo(x + s * 0.8f, y - s * 0.2f); path.lineTo(x + s * 0.6f, y - s); path.lineTo(x + s * 0.15f, y - s * 0.55f); path.close();
        c.drawPath(path, p);
        c.drawCircle(x, y, s * 0.82f, p);
        p.setColor(scared ? 0xFFFFFFFF : 0xFFFDE047);
        c.drawCircle(x - s * 0.3f, y - s * 0.1f, s * 0.18f, p);
        c.drawCircle(x + s * 0.3f, y - s * 0.1f, s * 0.18f, p);
        p.setColor(0xFF111827);
        c.drawRect(x - s * 0.33f, y - s * 0.22f, x - s * 0.27f, y + s * 0.02f, p);
        c.drawRect(x + s * 0.27f, y - s * 0.22f, x + s * 0.33f, y + s * 0.02f, p);
        p.setStrokeWidth(s * 0.05f);
        c.drawLine(x - s * 0.9f, y + s * 0.2f, x - s * 0.4f, y + s * 0.25f, p);
        c.drawLine(x + s * 0.9f, y + s * 0.2f, x + s * 0.4f, y + s * 0.25f, p);
        p.setColor(0xFFEC4899);
        c.drawCircle(x, y + s * 0.2f, s * 0.08f, p);
    }

    // ================================================================ PERTAHANAN ANGKASA: co-op shooter

    private static final int SHOTS = 80, ENEMIES = 40, BUBBLES = 40;
    private final float[][] shot = new float[SHOTS][4];     // x, y, alive, owner
    private final float[][] en = new float[ENEMIES][7];     // x, y, alive, hp, kind (0 jelly, 1 boss), phase, max hp
    private final float[][] bub = new float[BUBBLES][4];    // x, y, vy, alive
    private final float[][] stars = new float[80][3];
    private float formX, formDir = 1, formY;
    private int wave;
    private float waveT, starX, starY, starT = -1;

    private void startSpace() {
        Random sr = new Random(3);
        for (float[] s : stars) { s[0] = sr.nextFloat(); s[1] = sr.nextFloat(); s[2] = 0.3f + sr.nextFloat(); }
        int k = 0, n = 0;
        for (P pl : ps) if (pl.on) n++;
        for (P pl : ps) {
            if (!pl.on) continue;
            pl.x = W * (k + 1) / (n + 1);
            pl.y = H * 0.86f;
            pl.lives = 3;
            k++;
        }
        for (float[] s : shot) s[2] = 0;
        for (float[] b : bub) b[3] = 0;
        wave = 0;
        nextWave();
    }

    private void nextWave() {
        wave++;
        for (float[] e : en) e[2] = 0;
        for (P pl : ps) if (pl.on && pl.lives <= 0) { pl.lives = 1; pl.inv = 2; }   // friends come back each wave
        if (wave % 5 == 0) {
            float[] e = en[0];
            e[0] = W / 2; e[1] = H * 0.22f; e[2] = 1; e[4] = 1; e[3] = e[6] = 30 + wave * 6; e[5] = 0;
        } else {
            int rows = Math.min(5, 2 + wave / 2), cols = 8, k = 0;
            for (int ry = 0; ry < rows; ry++) for (int cx = 0; cx < cols && k < ENEMIES; cx++, k++) {
                float[] e = en[k];
                e[0] = cx * W * 0.08f; e[1] = ry * H * 0.08f; e[2] = 1; e[3] = e[6] = 1 + wave / 4; e[4] = 0; e[5] = rnd.nextFloat() * 6;
            }
            formX = W * 0.18f;
            formY = H * 0.12f;
        }
        waveT = 0;
        sound.sfx(GameMusic.SFX_OK);
    }

    private void angkasa(float dt) {
        waveT += dt;
        for (P pl : ps) {
            if (!pl.on || pl.lives <= 0) continue;
            if (pl.inv > 0) pl.inv -= dt;
            if (pl.dbl > 0) pl.dbl -= dt;
            pl.x = Math.max(W * 0.03f, Math.min(W * 0.97f, pl.x + ix(pl) * W * 0.65f * dt));
            pl.y = Math.max(H * 0.6f, Math.min(H * 0.92f, pl.y + iy(pl) * H * 0.5f * dt));
            pl.fire -= dt;
            if (pl.fire <= 0) {
                pl.fire = 0.3f;
                shoot(pl.x, pl.y - H * 0.04f, pl.seat);
                if (pl.dbl > 0) { shoot(pl.x - H * 0.03f, pl.y - H * 0.02f, pl.seat); shoot(pl.x + H * 0.03f, pl.y - H * 0.02f, pl.seat); }
            }
        }
        for (float[] s : shot) if (s[2] > 0) { s[1] -= H * 1.4f * dt; if (s[1] < 0) s[2] = 0; }
        // the formation sways and comes down
        float speed = W * (0.06f + wave * 0.012f);
        formX += formDir * speed * dt;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        int alive = 0;
        for (float[] e : en) {
            if (e[2] <= 0) continue;
            alive++;
            if (e[4] == 0) { minX = Math.min(minX, formX + e[0]); maxX = Math.max(maxX, formX + e[0]); }
        }
        if (minX < W * 0.04f && formDir < 0 || maxX > W * 0.96f && formDir > 0) { formDir = -formDir; formY += H * 0.03f; }
        for (float[] e : en) {
            if (e[2] <= 0) continue;
            e[5] += dt;
            float ex, ey;
            if (e[4] == 1) {   // the boss floats around
                e[0] = W / 2 + (float) Math.sin(e[5] * 0.6f) * W * 0.35f;
                e[1] = H * 0.22f + (float) Math.sin(e[5] * 1.3f) * H * 0.06f;
                ex = e[0]; ey = e[1];
                if (rnd.nextFloat() < dt * (1.2f + wave * 0.1f)) for (int k = -1; k <= 1; k++) bubble(ex + k * H * 0.05f, ey + H * 0.06f);
            } else {
                ex = formX + e[0];
                ey = formY + e[1] + (float) Math.sin(e[5] * 2) * H * 0.008f;
                if (rnd.nextFloat() < dt * (0.05f + wave * 0.012f)) bubble(ex, ey);
                if (ey > H * 0.8f) { for (P pl : ps) hitShip(pl); e[2] = 0; continue; }
            }
            float rad = e[4] == 1 ? H * 0.09f : H * 0.03f;
            for (float[] s : shot) {
                if (s[2] <= 0 || Math.abs(s[0] - ex) > rad || Math.abs(s[1] - ey) > rad) continue;
                s[2] = 0;
                if (--e[3] <= 0) {
                    e[2] = 0;
                    P owner = ps[(int) s[3]];
                    owner.score += e[4] == 1 ? 200 : 10;
                    sound.sfx(e[4] == 1 ? GameMusic.SFX_WIN : GameMusic.SFX_COIN);
                    if (rnd.nextFloat() < 0.08f && starT < 0) { starX = ex; starY = ey; starT = 0; }
                }
                break;
            }
        }
        for (float[] b : bub) {
            if (b[3] <= 0) continue;
            b[1] += b[2] * dt;
            if (b[1] > H) { b[3] = 0; continue; }
            for (P pl : ps) {
                if (!pl.on || pl.lives <= 0 || pl.inv > 0) continue;
                if (Math.abs(b[0] - pl.x) < H * 0.03f && Math.abs(b[1] - pl.y) < H * 0.03f) { b[3] = 0; hitShip(pl); }
            }
        }
        if (starT >= 0) {   // a falling star: triple shot for whoever catches it
            starT += dt;
            starY += H * 0.25f * dt;
            for (P pl : ps) if (pl.on && pl.lives > 0 && Math.hypot(pl.x - starX, pl.y - starY) < H * 0.05f) { pl.dbl = 8; starT = -1; sound.sfx(GameMusic.SFX_OK); break; }
            if (starY > H) starT = -1;
        }
        if (alive == 0) {
            if (wave >= 10) { winSpace(true); return; }
            nextWave();
        }
        boolean anyAlive = false;
        for (P pl : ps) if (pl.on && pl.lives > 0) anyAlive = true;
        if (!anyAlive) winSpace(false);
    }

    private void winSpace(boolean won) {
        int total = 0;
        P best = null;
        for (P pl : ps) if (pl.on) { total += pl.score; if (best == null || pl.score > best.score) best = pl; }
        finish((won ? "Bumi selamat! 🌍 " : "Sampai gelombang " + wave + ". ") + "Markah pasukan " + total
                + (best != null && total > 0 ? " · terbaik: " + who(best) : ""));
    }

    private void hitShip(P pl) {
        if (!pl.on || pl.lives <= 0 || pl.inv > 0) return;
        pl.lives--;
        pl.inv = 2;
        party.buzz(pl.seat, 200);
        sound.sfx(GameMusic.SFX_WRONG);
    }

    private void shoot(float x, float y, int owner) {
        for (float[] s : shot) if (s[2] <= 0) { s[0] = x; s[1] = y; s[2] = 1; s[3] = owner; return; }
    }

    private void bubble(float x, float y) {
        for (float[] b : bub) if (b[3] <= 0) { b[0] = x; b[1] = y; b[2] = H * (0.3f + 0.02f * wave); b[3] = 1; return; }
    }

    private void drawAngkasa(Canvas c, float t) {
        p.setColor(0xFF020617);
        c.drawRect(0, 0, W, H, p);
        for (float[] s : stars) {
            float y = (s[1] + t * 0.03f * s[2]) % 1f;
            p.setColor(Color.argb((int) (100 + 100 * s[2] / 1.3f), 255, 255, 255));
            c.drawCircle(s[0] * W, y * H, s[2] * H * 0.0025f, p);
        }
        // a planet low in the sky
        p.setShader(planet(H));
        c.drawCircle(W * 0.85f, H * 1.05f, H * 0.35f, p);
        p.setShader(null);
        for (float[] e : en) {
            if (e[2] <= 0) continue;
            float ex = e[4] == 1 ? e[0] : formX + e[0], ey = e[4] == 1 ? e[1] : formY + e[1] + (float) Math.sin(e[5] * 2) * H * 0.008f;
            jelly(c, ex, ey, e[4] == 1 ? H * 0.09f : H * 0.03f, e[5], e[4] == 1 ? 0xFFE879F9 : (((int) e[1] / 10) % 2 == 0 ? 0xFF22D3EE : 0xFFA78BFA));
            if (e[4] == 1) {
                r.set(W * 0.3f, H * 0.08f, W * 0.3f + W * 0.4f * e[3] / e[6], H * 0.095f);
                p.setColor(0xFFF43F5E);
                c.drawRect(r, p);
            }
        }
        p.setColor(0xFFFDE047);
        for (float[] s : shot) if (s[2] > 0) c.drawRoundRect(s[0] - H * 0.004f, s[1] - H * 0.015f, s[0] + H * 0.004f, s[1] + H * 0.015f, 4, 4, p);
        for (float[] b : bub) {
            if (b[3] <= 0) continue;
            p.setColor(0xAAF0ABFC);
            c.drawCircle(b[0], b[1], H * 0.012f, p);
            p.setColor(0xCCFFFFFF);
            c.drawCircle(b[0] - H * 0.004f, b[1] - H * 0.004f, H * 0.003f, p);
        }
        if (starT >= 0) {
            tx.setTextAlign(Paint.Align.CENTER);
            tx.setTextSize(H * 0.05f);
            tx.setColor(0xFFFDE047);
            c.drawText("★", starX, starY, tx);
        }
        for (P pl : ps) {
            if (!pl.on || pl.lives <= 0) continue;
            if (pl.inv > 0 && ((int) (pl.inv * 10)) % 2 == 0) continue;
            ship(c, pl.x, pl.y, Party.COLORS[pl.seat], t);
            label(c, pl, pl.x, pl.y - H * 0.045f);
        }
        for (P pl : ps) if (pl.on) hudBox(c, pl.seat, pl.score + "   " + hearts(pl.lives));
        tx.setTextAlign(Paint.Align.RIGHT);
        tx.setTextSize(H * 0.026f);
        tx.setColor(0xCCFFFFFF);
        c.drawText("Gelombang " + wave + "/10", W * 0.99f, H * 0.045f, tx);
        if (waveT < 1.5f) {
            tx.setTextAlign(Paint.Align.CENTER);
            tx.setTextSize(H * 0.07f);
            tx.setColor(Color.argb((int) (255 * (1 - waveT / 1.5f)), 253, 224, 71));
            c.drawText(wave % 5 == 0 ? "RAJA UBUR-UBUR!" : "GELOMBANG " + wave, W / 2, H * 0.5f, tx);
        }
    }

    static String hearts(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append('♥');
        return b.length() == 0 ? "✖" : b.toString();
    }

    private Shader planetShader;
    private float planetH;

    private Shader planet(float h) {
        if (planetShader == null || planetH != h) {
            planetH = h;
            planetShader = new RadialGradient(W * 0.8f, H * 0.85f, h * 0.45f, 0xFF7C3AED, 0xFF1E1B4B, Shader.TileMode.CLAMP);
        }
        return planetShader;
    }

    private void ship(Canvas c, float x, float y, int col, float t) {
        float s = H * 0.035f;
        p.setColor(Color.argb(200, 251, 146, 60));
        c.drawCircle(x, y + s * 1.1f, s * (0.3f + 0.1f * (float) Math.sin(t * 40)), p);
        path.reset();
        path.moveTo(x, y - s * 1.2f);
        path.lineTo(x + s, y + s);
        path.lineTo(x, y + s * 0.6f);
        path.lineTo(x - s, y + s);
        path.close();
        p.setColor(col);
        c.drawPath(path, p);
        p.setColor(0xFFE0F2FE);
        c.drawCircle(x, y - s * 0.2f, s * 0.3f, p);
    }

    private void jelly(Canvas c, float x, float y, float s, float ph, int col) {
        p.setColor(col);
        c.drawArc(x - s, y - s, x + s, y + s * 0.6f, 180, 180, true, p);
        c.drawRect(x - s, y - s * 0.21f, x + s, y, p);
        p.setStrokeWidth(s * 0.12f);
        for (int i = 0; i < 4; i++) {
            float lx = x - s * 0.7f + i * s * 0.47f;
            c.drawLine(lx, y, lx + (float) Math.sin(ph * 4 + i) * s * 0.2f, y + s * 0.8f, p);
        }
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x - s * 0.35f, y - s * 0.4f, s * 0.18f, p);
        c.drawCircle(x + s * 0.35f, y - s * 0.4f, s * 0.18f, p);
        p.setColor(0xFF111827);
        c.drawCircle(x - s * 0.33f, y - s * 0.36f, s * 0.08f, p);
        c.drawCircle(x + s * 0.37f, y - s * 0.36f, s * 0.08f, p);
    }

    // ================================================================ GELANGGANG SILAT: platform brawler

    private final RectF[] plat = {new RectF(), new RectF(), new RectF(), new RectF()};   // [0] the main floor
    private static final float SILAT_TIME = 120;

    private void startSilat() {
        plat[0].set(W * 0.16f, H * 0.72f, W * 0.84f, H * 0.78f);
        plat[1].set(W * 0.22f, H * 0.5f, W * 0.38f, H * 0.52f);
        plat[2].set(W * 0.62f, H * 0.5f, W * 0.78f, H * 0.52f);
        plat[3].set(W * 0.42f, H * 0.3f, W * 0.58f, H * 0.32f);
        int k = 0;
        for (P pl : ps) {
            if (!pl.on) continue;
            pl.x = W * (0.25f + k * 0.17f);
            pl.y = plat[0].top;
            pl.vx = pl.vy = 0;
            pl.lives = 3;
            pl.dmg = 0;
            pl.face = pl.x < W / 2;
            k++;
        }
        bg = Bitmap.createBitmap((int) W, (int) H, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bg);
        Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
        a.setShader(new LinearGradient(0, 0, 0, H, 0xFFF97316, 0xFF7C2D12, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, a);
        a.setShader(new RadialGradient(W / 2, H * 0.62f, H * 0.25f, 0xFFFDE68A, 0x00FDE68A, Shader.TileMode.CLAMP));
        c.drawCircle(W / 2, H * 0.62f, H * 0.25f, a);
        a.setShader(null);
        a.setColor(0xFF431407);   // distant hills and a kampung roofline
        Path hills = new Path();
        hills.moveTo(0, H);
        for (int i = 0; i <= 20; i++) hills.lineTo(W * i / 20f, H * (0.68f - 0.05f * (float) Math.sin(i * 0.9f)));
        hills.lineTo(W, H);
        hills.close();
        c.drawPath(hills, a);
        a.setColor(0xFF1C0A05);
        c.drawRect(0, H * 0.86f, W, H, a);   // the river below
        a.setColor(0x6638BDF8);
        for (int i = 0; i < 30; i++) c.drawRect(W * i / 30f, H * 0.88f + (i % 3) * H * 0.02f, W * i / 30f + W * 0.02f, H * 0.885f + (i % 3) * H * 0.02f, a);
        for (int i = 0; i < 4; i++) {
            RectF pf = plat[i];
            a.setShader(new LinearGradient(0, pf.top, 0, pf.bottom + (i == 0 ? H * 0.04f : 0), 0xFFD97706, 0xFF78350F, Shader.TileMode.CLAMP));
            c.drawRoundRect(pf.left, pf.top, pf.right, pf.bottom + (i == 0 ? H * 0.04f : 0), H * 0.01f, H * 0.01f, a);
            a.setShader(null);
            if (i == 0) {   // the mat's border pattern
                a.setColor(0xFFFDE68A);
                for (float x = pf.left + H * 0.02f; x < pf.right - H * 0.02f; x += H * 0.05f) c.drawCircle(x, pf.top + H * 0.03f, H * 0.008f, a);
            }
        }
    }

    private void silat(float dt) {
        float g = H * 2.6f, run = W * 0.32f, jumpV = H * 1.05f;
        for (P pl : ps) {
            if (!pl.on || pl.lives <= 0) continue;
            if (pl.inv > 0) pl.inv -= dt;
            if (pl.cool > 0) pl.cool -= dt;
            if (pl.atk > 0) pl.atk -= dt;
            float move;
            boolean jump, attack;
            if (pl.bot) {
                P tgt = null;
                float bd = Float.MAX_VALUE;
                for (P o : ps) {
                    if (o == pl || !o.on || o.lives <= 0) continue;
                    float d = Math.abs(o.x - pl.x) + Math.abs(o.y - pl.y);
                    if (d < bd) { bd = d; tgt = o; }
                }
                boolean offStage = pl.x < plat[0].left || pl.x > plat[0].right;
                if (offStage) move = pl.x < W / 2 ? 1 : -1;
                else if (tgt != null) move = Math.abs(tgt.x - pl.x) > H * 0.06f ? Math.signum(tgt.x - pl.x) : 0;
                else move = 0;
                jump = (offStage && pl.vy > 0 && pl.y > plat[0].top - H * 0.05f && pl.jumps < 2)
                        || (tgt != null && tgt.y < pl.y - H * 0.12f && pl.ground && rnd.nextFloat() < dt * 2);
                attack = tgt != null && Math.abs(tgt.x - pl.x) < H * 0.09f && Math.abs(tgt.y - pl.y) < H * 0.08f && rnd.nextFloat() < dt * 4;
            } else {
                move = ix(pl);
                if (Math.abs(move) < 0.3f) move = 0;
                jump = pressJump(pl);
                attack = pressA(pl);
            }
            pl.vx += (move * run - pl.vx) * Math.min(1, dt * (pl.ground ? 12 : 4));
            if (move != 0) pl.face = move > 0;
            if (jump && pl.jumps < 2) { pl.vy = -jumpV; pl.jumps++; pl.ground = false; if (!pl.bot) sound.sfx(GameMusic.SFX_JUMP); }
            if (attack && pl.cool <= 0) {
                pl.cool = 0.4f;
                pl.atk = 0.18f;
                float hx = pl.x + (pl.face ? 1 : -1) * H * 0.07f, hy = pl.y - H * 0.06f;
                for (P o : ps) {
                    if (o == pl || !o.on || o.lives <= 0 || o.inv > 0) continue;
                    if (Math.abs(o.x - hx) < H * 0.065f && Math.abs((o.y - H * 0.06f) - hy) < H * 0.08f) {
                        o.dmg += 9 + rnd.nextInt(5);
                        float kb = H * (0.5f + o.dmg * 0.012f);
                        o.vx = (pl.face ? 1 : -1) * kb;
                        o.vy = -kb * 0.6f;
                        o.ground = false;
                        if (!o.bot) party.buzz(o.seat, 80);
                        if (!pl.bot) party.buzz(pl.seat, 30);
                        sound.sfx(GameMusic.SFX_COIN);
                    }
                }
            }
            pl.vy += g * dt;
            float oldY = pl.y;
            pl.x += pl.vx * dt;
            pl.y += pl.vy * dt;
            pl.ground = false;
            if (pl.vy >= 0) {
                for (int i = 0; i < plat.length; i++) {
                    RectF pf = plat[i];
                    if (pl.x > pf.left && pl.x < pf.right && oldY <= pf.top + 1 && pl.y >= pf.top) {
                        pl.y = pf.top; pl.vy = 0; pl.ground = true; pl.jumps = 0;
                        pl.vx *= 0.85f;
                    }
                }
            }
            // out of the ring: into the river or off the sides
            if (pl.y > H * 1.05f || pl.x < -W * 0.08f || pl.x > W * 1.08f) {
                pl.lives--;
                sound.sfx(GameMusic.SFX_WRONG);
                if (!pl.bot) party.buzz(pl.seat, 250);
                if (pl.lives > 0) { pl.x = W / 2; pl.y = H * 0.1f; pl.vx = pl.vy = 0; pl.dmg = 0; pl.inv = 2; pl.jumps = 1; }
            }
        }
        int alive = 0;
        P last = null;
        for (P pl : ps) if (pl.on && pl.lives > 0) { alive++; last = pl; }
        int total = 0;
        for (P pl : ps) if (pl.on) total++;
        if (total > 1 && alive <= 1) { finish(last == null ? "Seri!" : who(last) + " Pendekar Juara! 🥋"); return; }
        if (time > SILAT_TIME) {
            P best = null;
            for (P pl : ps) if (pl.on && (best == null || pl.lives > best.lives || (pl.lives == best.lives && pl.dmg < best.dmg))) best = pl;
            finish(who(best) + " Pendekar Juara! 🥋");
        }
    }

    private void drawSilat(Canvas c, float t) {
        if (bg != null) c.drawBitmap(bg, 0, 0, null);
        for (P pl : ps) {
            if (!pl.on || pl.lives <= 0) continue;
            if (pl.inv > 0 && ((int) (pl.inv * 10)) % 2 == 0) continue;
            Badge.ring(c, pl.seat, pl.x, pl.y - H * 0.005f, H * 0.035f, t);
            fighter(c, pl, t);
            tx.setTextAlign(Paint.Align.CENTER);
            tx.setTextSize(H * 0.026f);
            tx.setColor(pl.dmg > 60 ? 0xFFF87171 : pl.dmg > 30 ? 0xFFFDE047 : 0xFFFFFFFF);
            c.drawText((int) pl.dmg + "%", pl.x + H * 0.06f, pl.y - H * 0.13f, tx);
            label(c, pl, pl.x, pl.y - H * 0.18f);
        }
        for (P pl : ps) if (pl.on) hudBox(c, pl.seat, (pl.bot ? "TV  " : "") + hearts(pl.lives) + "  " + (int) pl.dmg + "%");
        long left = Math.max(0, (long) (SILAT_TIME - time));
        tx.setTextAlign(Paint.Align.RIGHT);
        tx.setTextSize(H * 0.026f);
        tx.setColor(0xCCFFFFFF);
        c.drawText(left / 60 + ":" + String.format(java.util.Locale.US, "%02d", left % 60), W * 0.99f, H * 0.045f, tx);
    }

    /** A silat fighter: a tanjak (folded headcloth) and a sampin in the player's colour. (x, y) are the feet. */
    private void fighter(Canvas c, P pl, float t) {
        float s = H * 0.13f, x = pl.x, y = pl.y;
        int col = Party.COLORS[pl.seat];
        float dir = pl.face ? 1 : -1;
        float walk = pl.ground && Math.abs(pl.vx) > 10 ? (float) Math.sin(t * 14) * s * 0.12f : 0;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.09f);
        p.setColor(0xFF1F2937);
        float hipY = y - s * 0.45f;
        c.drawLine(x, hipY, x - s * 0.15f + walk, y, p);
        c.drawLine(x, hipY, x + s * 0.15f - walk, y, p);
        // sampin (waist cloth)
        p.setColor(col);
        c.drawRoundRect(x - s * 0.17f, hipY - s * 0.1f, x + s * 0.17f, hipY + s * 0.12f, s * 0.04f, s * 0.04f, p);
        // body
        p.setColor(0xFF111827);
        c.drawRoundRect(x - s * 0.14f, y - s * 0.85f, x + s * 0.14f, hipY - s * 0.05f, s * 0.06f, s * 0.06f, p);
        // arms: one strikes when attacking
        p.setColor(0xFFD6A77A);
        float shY = y - s * 0.78f;
        if (pl.atk > 0) {
            c.drawLine(x, shY, x + dir * s * 0.5f, shY + s * 0.02f, p);
            p.setColor(Color.argb(160, 255, 255, 255));
            c.drawCircle(x + dir * s * 0.55f, shY, s * 0.1f, p);
            p.setColor(0xFFD6A77A);
        } else c.drawLine(x, shY, x + dir * s * 0.28f, shY + s * 0.15f, p);
        c.drawLine(x, shY, x - dir * s * 0.2f, shY + s * 0.25f, p);
        // head and tanjak
        c.drawCircle(x, y - s * 0.98f, s * 0.13f, p);
        p.setColor(col);
        path.reset();
        path.moveTo(x - s * 0.16f, y - s * 1.04f);
        path.lineTo(x + dir * s * 0.05f, y - s * 1.3f);
        path.lineTo(x + s * 0.16f, y - s * 1.04f);
        path.close();
        c.drawPath(path, p);
        p.setColor(0xFF111827);
        c.drawCircle(x + dir * s * 0.06f, y - s * 0.99f, s * 0.025f, p);
        p.setStrokeCap(Paint.Cap.BUTT);
    }
}
