package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Fork extra: "Pengembaraan Sang Kancil", an original platformer in five worlds for 1-4 players on one TV. One player
 * plays full screen; with two the screen splits top and bottom, with three or four into quarters. Player 1 uses the
 * remote (◀▶ run, OK or ▲ jump); the others use their phones from the Party page (stick to run, A to jump).
 * Levels are built from small hand-made chunks. The world is in tiles; physics runs in tiles per second.
 */
final class Kancil {
    // ---------------------------------------------------------------- worlds and their chunks

    static final String[] WORLD = {"Hutan Rimba", "Sungai Buaya", "Kebun Pak Tani", "Gua Gelap", "Bukit Harimau"};
    static final String[] STORY = {
            "Sang Kancil lapar. Kutip rambutan sebanyak mungkin dan cari jalan ke sungai!",
            "Buaya-buaya lapar di sungai. Kancil yang bijak berkata: \"Berbaris, aku nak kira kamu!\" Lompat atas belakang buaya untuk menyeberang. Hati-hati bila mulut terbuka!",
            "Timun Pak Tani segar-segar. Setiap timun memulihkan satu nyawa. Jauhi lebah!",
            "Gua gelap penuh kelawar. Melompat rendah, siling dekat!",
            "Harimau menjaga bukit. Lompat atas kepalanya untuk buat dia pening, kemudian lari ke puncak!"
    };
    static final String[] TIPS = {"Lompat atas papan kayu yang bergerak", "Buaya ialah titian. Mulut terbuka? Tunggu!",
            "Timun = +1 nyawa", "Landak berduri, jangan pijak!", "Pijak harimau, dia pening 3 saat"};

    private static final int ROWS = 12, CHUNK_ROWS = 8;

    // Chunks: 8 rows each (level rows 4-11), drawn top to bottom. '#' ground, '=' log (stand on top), '^' thorns,
    // '~' water, 'r' rambutan, 't' cucumber, 'P' checkpoint, 'E' the end, 'S' start, 'C' crocodile, 'B' bee,
    // 'K' bat, 'H' porcupine, 'T' tiger, 'M' log moving sideways.
    private static final String[][] CH = {
            /* 0 start */ {"", "", "", "", "", "S...r.r.r...", "############", "############"},
            /* 1 pit */ {"", "", "", ".....r.r......", "", "", "#####....#####", "#####....#####"},
            /* 2 logs */ {"", ".......rrr....", "......====....", "", "..===.....r...", "", "##############", "##############"},
            /* 3 thorns */ {"", "", ".....rrr......", "", "", ".....^^^......", "##############", "##############"},
            /* 4 porcupine */ {"", "", "", "", "...r.r.r.r....", ".......H......", "##############", "##############"},
            /* 5 step */ {"", "", ".........rrr..", "........######", ".....r..######", "....##########", "##############", "##############"},
            /* 6 bee */ {"", "......B.......", "", "...r..r..r....", "", "", "##############", "##############"},
            /* 7 moving log */ {"", "", ".....r..r..r....", "", "....M...........", "", "###..........###", "###..........###"},
            /* 8 checkpoint */ {"", "", "", "", "", ".....P......", "############", "############"},
            /* 9 cucumber */ {"", ".....t........", "....===.......", "", ".=====...r....", "", "##############", "##############"},
            /* 10 end */ {"", "", "", "", "", "..r.r.r....E....", "################", "################"},
            /* 11 gap */ {"", "", "........r.........", ".......===........", "...r..........r...", "..===........===..", "###............###", "###............###"},
            /* 12 croc river */ {"", "", "", "....r....r....r...", "", "", "##~~~~C~~~~~~~~~##", "##~~~~~~~~~~~~~~##"},
            /* 13 two crocs */ {"", "", "..........t.............", ".........===............", "....r.........r.....r...", "", "##~~~C~~~~~~~~~~C~~~~~##", "##~~~~~~~~~~~~~~~~~~~~##"},
            /* 14 log river */ {"", "", "...r....r....r....", "", "..M.......M.......", "", "#~~~~~~~~~~~~~~~~#", "#~~~~~~~~~~~~~~~~#"},
            /* 15 tiger */ {"", "", "", "", "....r..r..r.....", "..........T.....", "################", "################"},
            /* 16 tiger + ledge */ {"", "", "......====........", "", "...r.........r....", ".......T..........", "##################", "##################"},
            /* 17 bees + cucumbers */ {"", "", "....B.....B.....", "", "..t..r..r..r..t.", "", "################", "################"},
            /* 18 bats */ {"", "", ".....K.....K....", "", "...r..r..r..r...", "", "################", "################"},
            /* 19 porcupine pit */ {"", "", "...r......r.....", "..===....===....", "", "......H.........", "#####..#########", "#####..#########"},
    };
    private static final int[][] LEVELS = {
            {0, 2, 1, 4, 3, 8, 6, 5, 9, 7, 11, 19, 10},
            {0, 12, 2, 14, 8, 13, 4, 12, 9, 13, 10},
            {0, 17, 3, 8, 4, 6, 11, 17, 9, 19, 7, 10},
            {0, 18, 3, 1, 8, 7, 18, 5, 19, 2, 10},
            {0, 15, 5, 3, 8, 16, 11, 7, 15, 9, 16, 10},
    };

    // per-world colours: sky top, sky bottom, far hills, near hills, grass, soil
    private static final int[][] PAL = {
            {0xFF38BDF8, 0xFFBAE6FD, 0xFF4ADE80, 0xFF15803D, 0xFF22C55E, 0xFF92400E},
            {0xFF0EA5E9, 0xFFFDE68A, 0xFF65A30D, 0xFF3F6212, 0xFF84CC16, 0xFFA16207},
            {0xFFF59E0B, 0xFFFDE68A, 0xFFA3E635, 0xFF4D7C0F, 0xFF65A30D, 0xFF78350F},
            {0xFF0F0A1F, 0xFF312E81, 0xFF1E1B4B, 0xFF0B0820, 0xFF6D28D9, 0xFF3F3F46},
            {0xFFF97316, 0xFFFDBA74, 0xFF9A3412, 0xFF431407, 0xFFD97706, 0xFF57534E},
    };

    // ---------------------------------------------------------------- state

    static final class Pl {
        int seat, hearts = 3, fruit, deaths;
        float x, y, vx, vy, cpX, cpY, inv, coyote, buffer, hurtT, run;
        boolean on, ground, face = true, done, jumpHeld, jumpWas;
        Ent onEnt;
        float camX, camY;
    }

    static final class Ent {
        char type;
        float x, y, x0, y0, w, h, vx, range, t, stun, dx, dy;
        boolean alive = true;
    }

    final Party party;
    final GameSound sound;
    final Pl[] pls = new Pl[4];
    final List<Ent> ents = new ArrayList<>();
    char[][] map;
    int cols, world, active;
    float time, introT, doneT = -1, endT = -1, firstDone = -1;
    boolean over;
    String result = "";
    private boolean kLeft, kRight, kJump;

    // drawing
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), q = new Paint(Paint.ANTI_ALIAS_FLAG), tx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF(), vp = new RectF();
    private float T, lastVpW, lastVpH;
    private int bmWorld = -1;
    private Bitmap grassB, soilB, logB, thornB, fruitB, cucB;
    private Shader sky;
    private final Path far = new Path(), near = new Path();
    private final float[] cloudX = {0.1f, 0.45f, 0.8f, 1.2f}, cloudY = {0.12f, 0.2f, 0.08f, 0.16f};

    Kancil(Party party, GameSound sound) {
        this.party = party;
        this.sound = sound;
        for (int i = 0; i < 4; i++) { pls[i] = new Pl(); pls[i].seat = i; }
        tx.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
    }

    // ---------------------------------------------------------------- flow

    void start() {
        active = 0;
        for (int i = 0; i < 4; i++) {
            Pl pl = pls[i];
            pl.on = i == 0 || party.players[i].joined;
            if (pl.on) active++;
            pl.fruit = 0;
            pl.deaths = 0;
        }
        over = false;
        load(0);
    }

    void load(int w) {
        world = w;
        int[] seq = LEVELS[w];
        cols = 0;
        for (int id : seq) cols += width(CH[id]);
        map = new char[ROWS][cols];
        for (char[] row : map) java.util.Arrays.fill(row, '.');
        ents.clear();
        float sx = 2, sy = 9;
        int at = 0;
        for (int id : seq) {
            String[] ch = CH[id];
            int cw = width(ch);
            for (int rr = 0; rr < CHUNK_ROWS; rr++) {
                String row = ch[rr];
                for (int cc = 0; cc < cw; cc++) {
                    char k = cc < row.length() ? row.charAt(cc) : '.';
                    int y = ROWS - CHUNK_ROWS + rr, x = at + cc;
                    switch (k) {
                        case 'S': sx = x; sy = y; k = '.'; break;
                        case 'C': case 'B': case 'K': case 'H': case 'T': case 'M': spawn(k, x, y); k = k == 'C' ? '~' : '.'; break;
                        default:
                    }
                    map[y][x] = k;
                }
            }
            at += cw;
        }
        if (w == 3) {   // the cave: rock above, a low ceiling
            for (int x = 0; x < cols; x++) for (int y = 0; y < 4; y++) map[y][x] = '#';
        }
        for (Ent e : ents) if (e.type == 'C') crocSpan(e);
        for (int i = 0; i < 4; i++) {
            Pl pl = pls[i];
            pl.x = pl.cpX = sx + i * 0.6f;
            pl.y = pl.cpY = sy;
            pl.vx = pl.vy = 0;
            pl.hearts = 3;
            pl.done = false;
            pl.inv = 0;
            pl.onEnt = null;
            pl.camX = pl.camY = -1;
        }
        time = 0;
        introT = 0;
        firstDone = -1;
        doneT = -1;
    }

    private static int width(String[] ch) {
        int w = 0;
        for (String s : ch) w = Math.max(w, s.length());
        return w;
    }

    private void spawn(char k, int x, int y) {
        Ent e = new Ent();
        e.type = k;
        e.x = e.x0 = x;
        e.y = e.y0 = y;
        e.t = (x * 0.37f) % 6;
        switch (k) {
            case 'C': e.w = 2.6f; e.h = 0.55f; e.y = e.y0 = y - 0.45f; e.vx = 1.6f; break;
            case 'M': e.w = 2.2f; e.h = 0.4f; e.range = 6; e.vx = 1.8f; break;
            case 'H': e.w = 0.9f; e.h = 0.7f; e.vx = -1.4f; e.y = e.y0 = y + 0.3f; break;
            case 'T': e.w = 1.5f; e.h = 0.95f; e.vx = -2.4f; e.y = e.y0 = y + 0.05f; break;
            default: e.w = 0.7f; e.h = 0.6f;   // bee, bat
        }
        ents.add(e);
    }

    /** A crocodile swims between the two banks of its water. */
    private void crocSpan(Ent e) {
        int row = (int) (e.y0 + 0.5f), x = (int) e.x0, a = x, b = x;
        while (a > 0 && map[row][a - 1] == '~') a--;
        while (b < cols - 1 && map[row][b + 1] == '~') b++;
        e.x0 = a + 0.1f;
        e.range = Math.max(0, (b + 1 - 0.1f) - e.w - e.x0);
    }

    boolean intro() { return introT < 4.5f; }

    void key(int k, boolean down) {
        if (k == KeyEvent.KEYCODE_DPAD_LEFT) kLeft = down;
        else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) kRight = down;
        else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A
                || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_SPACE) {
            kJump = down;
            if (down && intro()) introT = 4.5f;   // skip the story
        }
    }

    // ---------------------------------------------------------------- simulation

    void update(float dt) {
        if (over) return;
        if (intro()) { introT += dt; return; }
        if (doneT >= 0) {   // the level's done screen
            doneT += dt;
            if (doneT > 4.5f) {
                if (world + 1 < WORLD.length) load(world + 1);
                else finish();
            }
            return;
        }
        time += dt;
        for (Ent e : ents) moveEnt(e, dt);
        int done = 0, on = 0;
        for (Pl pl : pls) {
            if (!pl.on) continue;
            on++;
            if (pl.done) { done++; continue; }
            // controls
            float dir;
            boolean jump;
            if (pl.seat == 0) {
                dir = (kRight ? 1 : 0) - (kLeft ? 1 : 0);
                jump = kJump;
                Party.Player pp = party.players[0];
                if (pp.phone) {   // player 1 on a phone too
                    if (dir == 0) dir = pp.x > 0.35f ? 1 : pp.x < -0.35f ? -1 : 0;
                    jump |= pp.a;
                }
            }
            else {
                Party.Player pp = party.players[pl.seat];
                if (!pp.joined) { pl.on = false; continue; }
                dir = pp.x > 0.35f ? 1 : pp.x < -0.35f ? -1 : 0;
                jump = pp.a;
            }
            step(pl, dir, jump, dt);
        }
        if (firstDone >= 0 && (done >= on || time - firstDone > 20)) {
            for (Pl pl : pls) if (pl.on) pl.done = true;
            doneT = 0;
        }
    }

    private void moveEnt(Ent e, float dt) {
        e.t += dt;
        float ox = e.x, oy = e.y;
        switch (e.type) {
            case 'C': case 'M':
                e.x += e.vx * dt;
                if (e.x > e.x0 + e.range) { e.x = e.x0 + e.range; e.vx = -Math.abs(e.vx); }
                if (e.x < e.x0) { e.x = e.x0; e.vx = Math.abs(e.vx); }
                break;
            case 'B':
                e.y = e.y0 + (float) Math.sin(e.t * 2.2f) * 1.6f;
                e.x = e.x0 + (float) Math.sin(e.t * 0.9f) * 0.8f;
                break;
            case 'K':
                e.x = e.x0 + (float) Math.sin(e.t * 1.1f) * 3f;
                e.y = e.y0 + (float) Math.sin(e.t * 3.3f) * 0.6f;
                break;
            default:   // walkers turn at walls and edges
                if (e.stun > 0) { e.stun -= dt; break; }
                float nx = e.x + e.vx * dt;
                float ahead = e.vx > 0 ? nx + e.w : nx;
                int below = (int) (e.y + e.h + 0.1f), mid = (int) (e.y + e.h * 0.5f);
                if (solid(ahead, mid) || !solid(ahead, below) || water(ahead, below)) e.vx = -e.vx;
                else e.x = nx;
        }
        e.dx = e.x - ox;
        e.dy = e.y - oy;
    }

    private boolean solid(float x, float y) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        if (cx < 0 || cx >= cols) return true;
        if (cy < 0 || cy >= ROWS) return false;
        return map[cy][cx] == '#';
    }

    private boolean water(float x, float y) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        return cx >= 0 && cx < cols && cy >= 0 && cy < ROWS && map[cy][cx] == '~';
    }

    private static final float PW = 0.8f, PH = 0.85f, G = 40, JUMP = 15.5f, RUN = 7.5f;

    private void step(Pl pl, float dir, boolean jump, float dt) {
        if (pl.inv > 0) pl.inv -= dt;
        // carried by what it stands on
        if (pl.onEnt != null) { pl.x += pl.onEnt.dx; pl.y += pl.onEnt.dy; }
        float target = dir * RUN;
        float acc = pl.ground ? 60 : 30;
        if (pl.vx < target) pl.vx = Math.min(target, pl.vx + acc * dt);
        else if (pl.vx > target) pl.vx = Math.max(target, pl.vx - acc * dt);
        if (dir != 0) pl.face = dir > 0;
        pl.run += Math.abs(pl.vx) * dt * 2.2f;
        // jumping: a little grace after leaving a ledge, and a press just before landing still counts
        pl.coyote = pl.ground ? 0.1f : pl.coyote - dt;
        if (jump && !pl.jumpWas) pl.buffer = 0.12f; else pl.buffer -= dt;
        if (pl.buffer > 0 && pl.coyote > 0) {
            pl.vy = -JUMP;
            sound.sfx(GameMusic.SFX_JUMP);
            pl.buffer = 0;
            pl.coyote = 0;
            pl.ground = false;
            pl.onEnt = null;
        }
        if (!jump && pl.vy < -4) pl.vy = -4;   // let go early, jump lower
        pl.jumpWas = jump;
        pl.vy = Math.min(22, pl.vy + G * dt);
        // move x
        pl.x += pl.vx * dt;
        if (pl.vx > 0 && (solid(pl.x + PW, pl.y + 0.05f) || solid(pl.x + PW, pl.y + PH - 0.05f))) { pl.x = (float) Math.floor(pl.x + PW) - PW - 0.001f; pl.vx = 0; }
        if (pl.vx < 0 && (solid(pl.x, pl.y + 0.05f) || solid(pl.x, pl.y + PH - 0.05f))) { pl.x = (float) Math.floor(pl.x) + 1.001f; pl.vx = 0; }
        if (pl.x < 0) pl.x = 0;
        // move y
        float oldBottom = pl.y + PH;
        pl.y += pl.vy * dt;
        pl.ground = false;
        pl.onEnt = null;
        if (pl.vy >= 0) {
            float bottom = pl.y + PH;
            int row = (int) Math.floor(bottom);
            boolean landed = false;
            for (float fx = pl.x + 0.05f; fx <= pl.x + PW - 0.05f + 1e-3f; fx += PW - 0.1f) {
                int cx = (int) Math.floor(fx);
                if (cx < 0 || cx >= cols || row < 0 || row >= ROWS) continue;
                char k = map[row][cx];
                if (k == '#' || (k == '=' && oldBottom <= row + 0.05f)) landed = true;
            }
            if (landed) { pl.y = row - PH; pl.vy = 0; pl.ground = true; }
            // platforms that move: crocodiles and logs
            for (Ent e : ents) {
                if (e.type != 'C' && e.type != 'M') continue;
                if (pl.x + PW > e.x + 0.1f && pl.x < e.x + e.w - 0.1f && oldBottom <= e.y + 0.15f && pl.y + PH >= e.y) {
                    pl.y = e.y - PH; pl.vy = 0; pl.ground = true; pl.onEnt = e;
                }
            }
        } else if (solid(pl.x + 0.1f, pl.y) || solid(pl.x + PW - 0.1f, pl.y)) {
            pl.y = (float) Math.floor(pl.y) + 1;
            pl.vy = 0;
        }
        // tiles touched
        for (int yy = (int) pl.y; yy <= (int) (pl.y + PH); yy++) for (int xx = (int) pl.x; xx <= (int) (pl.x + PW); xx++) {
            if (yy < 0 || yy >= ROWS || xx < 0 || xx >= cols) continue;
            char k = map[yy][xx];
            if (k == 'r') { map[yy][xx] = '.'; pl.fruit++; sound.sfx(GameMusic.SFX_COIN); }
            else if (k == 't') { map[yy][xx] = '.'; pl.hearts = Math.min(5, pl.hearts + 1); party.buzz(pl.seat, 40); sound.sfx(GameMusic.SFX_OK); }
            else if (k == 'P') { pl.cpX = xx; pl.cpY = yy; map[yy][xx] = 'p'; }
            else if (k == 'p') { pl.cpX = xx; pl.cpY = yy; }
            else if (k == 'E') {
                pl.done = true;
                sound.sfx(GameMusic.SFX_WIN);
                if (firstDone < 0) firstDone = time;
                party.buzz(pl.seat, 120);
            } else if (k == '^' && pl.y + PH > yy + 0.45f) hurt(pl, false);
        }
        // fell in water or off the world
        if (pl.y > ROWS + 1 || water(pl.x + PW / 2, pl.y + PH - 0.2f)) { hurt(pl, true); return; }
        // creatures
        for (Ent e : ents) {
            if (e.type == 'M') continue;
            if (pl.x + PW < e.x || pl.x > e.x + e.w || pl.y + PH < e.y || pl.y > e.y + e.h) continue;
            if (e.type == 'C') {   // the open mouth bites
                boolean open = (e.t % 3.2f) > 2.3f;
                float head = e.vx > 0 ? e.x + e.w - 0.8f : e.x;
                if (open && pl.x + PW > head && pl.x < head + 0.8f) hurt(pl, false);
                continue;
            }
            if (e.type == 'T') {
                if (e.stun > 0) continue;
                if (pl.vy > 0 && pl.y + PH - e.y < 0.45f) { e.stun = 3; pl.vy = -11; party.buzz(pl.seat, 50); continue; }
            }
            hurt(pl, false);
        }
    }

    private void hurt(Pl pl, boolean fell) {
        if (!fell && pl.inv > 0) return;
        pl.hearts--;
        pl.inv = 1.6f;
        sound.sfx(GameMusic.SFX_WRONG);
        party.buzz(pl.seat, 200);
        if (pl.hearts <= 0) {   // no game over for children: back to the flag, with half the fruit
            pl.hearts = 3;
            pl.fruit /= 2;
            pl.deaths++;
            fell = true;
        }
        if (fell) {
            pl.x = pl.cpX;
            pl.y = pl.cpY;
            pl.vx = pl.vy = 0;
        } else {
            pl.vx = pl.face ? -7 : 7;
            pl.vy = -9;
        }
    }

    private void finish() {
        over = true;
        int best = -1;
        for (Pl pl : pls) if (pl.on && (best < 0 || pl.fruit > pls[best].fruit)) best = pl.seat;
        int total = 0;
        for (Pl pl : pls) if (pl.on) total += pl.fruit;
        result = active > 1 ? "Pemain " + (best + 1) + " paling banyak rambutan (" + pls[best].fruit + ")! Jumlah pasukan: " + total
                : "Tahniah! Sang Kancil sampai ke puncak dengan " + total + " rambutan!";
    }

    // ---------------------------------------------------------------- drawing

    void draw(Canvas c, float W, float H, float t) {
        int n = 0;
        for (Pl pl : pls) if (pl.on) n++;
        n = Math.max(1, n);
        float vw = n >= 3 ? W / 2 : W, vh = n >= 2 ? H / 2 : H;
        prepare(vw, vh);
        int k = 0;
        for (Pl pl : pls) {
            if (!pl.on) continue;
            float x0 = n >= 3 ? (k % 2) * vw : 0, y0 = n >= 3 ? (k / 2) * vh : k * vh;
            vp.set(x0, y0, x0 + vw, y0 + vh);
            view(c, pl, t);
            k++;
        }
        // split lines
        p.setColor(0xFF0B0B12);
        if (n >= 2) c.drawRect(0, H / 2 - 3, W, H / 2 + 3, p);
        if (n >= 3) c.drawRect(W / 2 - 3, 0, W / 2 + 3, H, p);
        if (intro()) drawIntro(c, W, H, t);
        else if (doneT >= 0) drawDone(c, W, H);
    }

    private void prepare(float vw, float vh) {
        T = vh / 9.5f;
        if (vw == lastVpW && vh == lastVpH && bmWorld == world) return;
        lastVpW = vw;
        lastVpH = vh;
        bmWorld = world;
        int[] pal = PAL[world];
        int s = Math.max(8, Math.round(T) + 1);
        grassB = tileBitmap(s, 0, pal);
        soilB = tileBitmap(s, 1, pal);
        logB = tileBitmap(s, 2, pal);
        thornB = tileBitmap(s, 3, pal);
        fruitB = tileBitmap(s, 4, pal);
        cucB = tileBitmap(s, 5, pal);
        sky = new LinearGradient(0, 0, 0, vh, pal[0], pal[1], Shader.TileMode.CLAMP);
        hills(far, vw, vh, 0.55f, 0.12f, 3);
        hills(near, vw, vh, 0.7f, 0.1f, 5);
    }

    private static void hills(Path h, float vw, float vh, float base, float amp, int bumps) {
        h.reset();
        float w = vw * 2;
        h.moveTo(0, vh);
        for (int i = 0; i <= 64; i++) {
            float x = w * i / 64f;
            double a = i / 64.0 * Math.PI * 2 * bumps;
            h.lineTo(x, vh * (base - amp * (float) (0.6 * Math.sin(a) + 0.4 * Math.sin(a * 2.3 + 1))));
        }
        h.lineTo(w, vh);
        h.close();
    }

    private Bitmap tileBitmap(int s, int kind, int[] pal) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint a = new Paint(Paint.ANTI_ALIAS_FLAG);
        switch (kind) {
            case 0:   // grass on soil
            case 1: {
                a.setShader(new LinearGradient(0, 0, 0, s, pal[5], darker(pal[5], 0.7f), Shader.TileMode.CLAMP));
                c.drawRect(0, 0, s, s, a);
                a.setShader(null);
                a.setColor(darker(pal[5], 0.8f));
                for (int i = 0; i < 4; i++) c.drawCircle((i * 37 % 10) / 10f * s + s * 0.1f, (i * 53 % 10) / 10f * s, s * 0.06f, a);
                if (kind == 0) {
                    a.setShader(new LinearGradient(0, 0, 0, s * 0.35f, lighter(pal[4]), pal[4], Shader.TileMode.CLAMP));
                    c.drawRect(0, 0, s, s * 0.3f, a);
                    a.setShader(null);
                    a.setColor(pal[4]);
                    for (int i = 0; i < 6; i++) c.drawCircle(s * (i + 0.5f) / 6f, s * 0.3f, s * 0.08f, a);
                }
                break;
            }
            case 2: {   // a log plank
                a.setShader(new LinearGradient(0, 0, 0, s * 0.4f, 0xFFD97706, 0xFF78350F, Shader.TileMode.CLAMP));
                c.drawRoundRect(0, 0, s, s * 0.4f, s * 0.1f, s * 0.1f, a);
                a.setShader(null);
                a.setColor(0x55000000);
                a.setStrokeWidth(Math.max(1, s * 0.03f));
                c.drawLine(s * 0.1f, s * 0.2f, s * 0.9f, s * 0.2f, a);
                break;
            }
            case 3: {   // thorns
                a.setColor(0xFF64748B);
                Path th = new Path();
                for (int i = 0; i < 3; i++) {
                    float x = s * i / 3f;
                    th.moveTo(x, s); th.lineTo(x + s / 6f, s * 0.45f); th.lineTo(x + s / 3f, s); th.close();
                }
                c.drawPath(th, a);
                a.setColor(0xFFE2E8F0);
                for (int i = 0; i < 3; i++) c.drawCircle(s * i / 3f + s / 6f, s * 0.5f, s * 0.04f, a);
                break;
            }
            case 4: {   // a rambutan
                float cx = s / 2f, cy = s / 2f, rr = s * 0.24f;
                a.setColor(0xFF15803D);
                a.setStrokeWidth(Math.max(1, s * 0.035f));
                for (int i = 0; i < 18; i++) {
                    double an = i * Math.PI * 2 / 18;
                    c.drawLine(cx + (float) Math.cos(an) * rr * 0.8f, cy + (float) Math.sin(an) * rr * 0.8f,
                            cx + (float) Math.cos(an) * rr * 1.45f, cy + (float) Math.sin(an) * rr * 1.45f, a);
                }
                a.setShader(new RadialGradient(cx - rr * 0.3f, cy - rr * 0.3f, rr * 1.3f, 0xFFFCA5A5, 0xFFB91C1C, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, rr, a);
                a.setShader(null);
                a.setColor(0xFFDC2626);
                for (int i = 0; i < 10; i++) {
                    double an = i * Math.PI * 2 / 10 + 0.3;
                    c.drawLine(cx + (float) Math.cos(an) * rr, cy + (float) Math.sin(an) * rr,
                            cx + (float) Math.cos(an) * rr * 1.35f, cy + (float) Math.sin(an) * rr * 1.35f, a);
                }
                break;
            }
            default: {   // a cucumber
                c.save();
                c.rotate(-30, s / 2f, s / 2f);
                a.setShader(new LinearGradient(0, s * 0.35f, 0, s * 0.65f, 0xFF86EFAC, 0xFF166534, Shader.TileMode.CLAMP));
                c.drawRoundRect(s * 0.15f, s * 0.36f, s * 0.85f, s * 0.64f, s * 0.14f, s * 0.14f, a);
                a.setShader(null);
                a.setColor(0xFFBBF7D0);
                for (int i = 0; i < 5; i++) c.drawCircle(s * (0.25f + i * 0.12f), s * (i % 2 == 0 ? 0.45f : 0.55f), s * 0.025f, a);
                c.restore();
            }
        }
        return b;
    }

    private void view(Canvas c, Pl me, float t) {
        float vw = vp.width(), vh = vp.height();
        // camera follows, smoothly
        float tx0 = (me.x + PW / 2) * T - vw * 0.4f, ty0 = (me.y + PH / 2) * T - vh * 0.55f;
        tx0 = Math.max(0, Math.min(cols * T - vw, tx0));
        ty0 = Math.max(0, Math.min(ROWS * T - vh, ty0));
        if (me.camX < 0) { me.camX = tx0; me.camY = ty0; }
        me.camX += (tx0 - me.camX) * 0.15f;
        me.camY += (ty0 - me.camY) * 0.1f;
        float cx = me.camX, cy = me.camY;
        int[] pal = PAL[world];

        c.save();
        c.clipRect(vp);
        c.translate(vp.left, vp.top);
        // sky, sun or moon, clouds, two layers of hills
        p.setShader(sky);
        c.drawRect(0, 0, vw, vh, p);
        p.setShader(null);
        p.setColor(world == 3 ? 0x33FFFFFF : 0xCCFFF7D6);
        c.drawCircle(vw * 0.8f, vh * 0.18f, vh * 0.08f, p);
        if (world != 3) {
            p.setColor(0xCCFFFFFF);
            for (int i = 0; i < cloudX.length; i++) {
                float x = ((cloudX[i] * vw * 1.4f - cx * 0.1f - t * 8) % (vw * 1.4f) + vw * 1.4f) % (vw * 1.4f) - vw * 0.2f;
                float y = cloudY[i] * vh;
                c.drawCircle(x, y, vh * 0.04f, p);
                c.drawCircle(x + vh * 0.05f, y - vh * 0.02f, vh * 0.05f, p);
                c.drawCircle(x + vh * 0.1f, y, vh * 0.04f, p);
            }
        }
        layer(c, far, pal[2], cx * 0.2f, vw);
        layer(c, near, pal[3], cx * 0.45f, vw);

        c.translate(-cx, -cy);
        int c0 = Math.max(0, (int) (cx / T)), c1 = Math.min(cols - 1, (int) ((cx + vw) / T) + 1);
        int r0 = Math.max(0, (int) (cy / T)), r1 = Math.min(ROWS - 1, (int) ((cy + vh) / T) + 1);
        float bob = (float) Math.sin(t * 3) * T * 0.06f;
        for (int y = r0; y <= r1; y++) for (int x = c0; x <= c1; x++) {
            char k = map[y][x];
            float px = x * T, py = y * T;
            switch (k) {
                case '#': c.drawBitmap(y > 0 && map[y - 1][x] != '#' ? grassB : soilB, px, py, null); break;
                case '=': c.drawBitmap(logB, px, py, null); break;
                case '^': c.drawBitmap(thornB, px, py, null); break;
                case 'r': c.drawBitmap(fruitB, px, py + bob, null); break;
                case 't': c.drawBitmap(cucB, px, py + bob, null); break;
                case '~':
                    p.setColor(0xCC0EA5E9);
                    c.drawRect(px, py + (y > 0 && map[y - 1][x] == '~' ? 0 : T * 0.25f), px + T + 1, py + T + 1, p);
                    if (y > 0 && map[y - 1][x] != '~') {
                        p.setColor(0xAAE0F2FE);
                        c.drawCircle(px + T * 0.5f, py + T * 0.25f + (float) Math.sin(t * 3 + x) * T * 0.04f, T * 0.08f, p);
                    }
                    break;
                case 'P': case 'p': flag(c, px, py, k == 'p', t); break;
                case 'E': house(c, px, py, t); break;
                default:
            }
        }
        for (Ent e : ents) {
            if (e.x * T > cx + vw + T * 3 || (e.x + e.w) * T < cx - T * 3) continue;
            drawEnt(c, e, t);
        }
        for (Pl pl : pls) {
            if (!pl.on || (pl.done && doneT < 0 && pl != me)) continue;
            if (pl.inv > 0 && ((int) (pl.inv * 12)) % 2 == 0) continue;   // blink while safe
            drawKancil(c, p, path, (pl.x + PW / 2) * T, (pl.y + PH) * T, T * 1.05f, pl.face,
                    pl.ground ? pl.run : 0.8f, Party.COLORS[pl.seat], t, pl.ground && Math.abs(pl.vx) > 0.5f);
            Badge.pin(c, pl.seat, false, (pl.x + PW / 2) * T, (pl.y - 0.15f) * T, T * 0.32f, t);
        }
        c.restore();
        hud(c, me);
    }

    private void layer(Canvas c, Path h, int col, float off, float vw) {
        float o = -(off % (vw * 2));
        p.setColor(col);
        c.save();
        c.translate(o, 0);
        c.drawPath(h, p);
        c.translate(vw * 2, 0);
        c.drawPath(h, p);
        c.restore();
    }

    private void flag(Canvas c, float x, float y, boolean on, float t) {
        p.setColor(0xFFE5E7EB);
        c.drawRect(x + T * 0.45f, y - T * 0.6f, x + T * 0.55f, y + T, p);
        path.reset();
        float wave = (float) Math.sin(t * 6) * T * 0.06f;
        float top = on ? y - T * 0.6f : y + T * 0.2f;
        path.moveTo(x + T * 0.55f, top);
        path.lineTo(x + T * 1.25f, top + T * 0.2f + wave);
        path.lineTo(x + T * 0.55f, top + T * 0.4f);
        path.close();
        p.setColor(on ? 0xFF22C55E : 0xFFEF4444);
        c.drawPath(path, p);
    }

    private void house(Canvas c, float x, float y, float t) {
        float s = T * 1.6f, bx = x - s * 0.3f, by = y + T;
        p.setColor(0xFF92400E);
        c.drawRect(bx, by - s * 0.7f, bx + s, by, p);
        path.reset();
        path.moveTo(bx - s * 0.15f, by - s * 0.65f);
        path.lineTo(bx + s * 0.5f, by - s * 1.2f);
        path.lineTo(bx + s * 1.15f, by - s * 0.65f);
        path.close();
        p.setColor(0xFFCA8A04);
        c.drawPath(path, p);
        p.setColor(0xFF451A03);
        c.drawRect(bx + s * 0.38f, by - s * 0.4f, bx + s * 0.62f, by, p);
        tx.setTextAlign(Paint.Align.CENTER);
        tx.setTextSize(T * 0.4f);
        tx.setColor(Color.argb((int) (180 + 70 * Math.sin(t * 4)), 255, 255, 255));
        c.drawText("TAMAT", bx + s * 0.5f, by - s * 1.35f, tx);
    }

    private void drawEnt(Canvas c, Ent e, float t) {
        float x = e.x * T, y = e.y * T, w = e.w * T, h = e.h * T;
        boolean right = e.vx > 0;
        switch (e.type) {
            case 'M':
                c.drawBitmap(logB, x, y, null);
                c.drawBitmap(logB, x + T, y, null);
                if (e.w > 2) c.drawBitmap(logB, x + w - T, y, null);
                break;
            case 'C': {   // crocodile: long body, bumps, a jaw that opens
                boolean open = (e.t % 3.2f) > 2.3f;
                c.save();
                if (!right) c.scale(-1, 1, x + w / 2, 0);
                p.setColor(0xFF15803D);
                c.drawRoundRect(x, y, x + w * 0.8f, y + h, h * 0.5f, h * 0.5f, p);
                p.setColor(0xFF166534);
                for (int i = 0; i < 5; i++) c.drawCircle(x + w * (0.12f + i * 0.13f), y + h * 0.1f, h * 0.18f, p);
                p.setColor(0xFF15803D);
                c.drawRoundRect(x + w * 0.65f, y + h * 0.2f, x + w, y + h * 0.75f, h * 0.2f, h * 0.2f, p);   // lower jaw
                c.save();
                c.rotate(open ? -28 : 0, x + w * 0.68f, y + h * 0.3f);
                c.drawRoundRect(x + w * 0.65f, y - h * 0.05f, x + w, y + h * 0.4f, h * 0.2f, h * 0.2f, p);   // upper jaw
                p.setColor(0xFFFFFFFF);
                for (int i = 0; i < 4; i++) {
                    float tx1 = x + w * (0.7f + i * 0.07f);
                    path.reset(); path.moveTo(tx1, y + h * 0.4f); path.lineTo(tx1 + w * 0.025f, y + h * 0.55f); path.lineTo(tx1 + w * 0.05f, y + h * 0.4f); path.close();
                    c.drawPath(path, p);
                }
                c.restore();
                p.setColor(0xFFFDE047);
                c.drawCircle(x + w * 0.66f, y - h * 0.05f, h * 0.17f, p);
                p.setColor(0xFF000000);
                c.drawCircle(x + w * 0.67f, y - h * 0.05f, h * 0.07f, p);
                c.restore();
                if (open) { tx.setTextAlign(Paint.Align.CENTER); tx.setTextSize(T * 0.4f); tx.setColor(0xFFFFFFFF); c.drawText("!", right ? x + w * 0.85f : x + w * 0.15f, y - h * 0.8f, tx); }
                break;
            }
            case 'B': {   // bee
                float cx = x + w / 2, cy = y + h / 2;
                p.setColor(0xAAFFFFFF);
                float flap = (float) Math.abs(Math.sin(t * 30)) * h * 0.4f;
                c.drawOval(cx - w * 0.35f, cy - h * 0.55f - flap, cx + w * 0.05f, cy - h * 0.1f, p);
                c.drawOval(cx - w * 0.05f, cy - h * 0.55f - flap, cx + w * 0.35f, cy - h * 0.1f, p);
                p.setColor(0xFFFACC15);
                c.drawOval(cx - w * 0.45f, cy - h * 0.3f, cx + w * 0.45f, cy + h * 0.4f, p);
                p.setColor(0xFF111827);
                c.drawRect(cx - w * 0.12f, cy - h * 0.3f, cx - w * 0.02f, cy + h * 0.4f, p);
                c.drawRect(cx + w * 0.12f, cy - h * 0.28f, cx + w * 0.22f, cy + h * 0.38f, p);
                c.drawCircle(cx - w * 0.3f, cy, h * 0.06f, p);
                break;
            }
            case 'K': {   // bat
                float cx = x + w / 2, cy = y + h / 2, fl = (float) Math.sin(t * 18) * h * 0.4f;
                p.setColor(0xFF4C1D95);
                path.reset();
                path.moveTo(cx, cy); path.lineTo(cx - w * 0.9f, cy - h * 0.2f + fl); path.lineTo(cx - w * 0.5f, cy + h * 0.3f); path.close();
                path.moveTo(cx, cy); path.lineTo(cx + w * 0.9f, cy - h * 0.2f + fl); path.lineTo(cx + w * 0.5f, cy + h * 0.3f); path.close();
                c.drawPath(path, p);
                c.drawCircle(cx, cy, h * 0.35f, p);
                p.setColor(0xFFFDE047);
                c.drawCircle(cx - h * 0.12f, cy - h * 0.05f, h * 0.07f, p);
                c.drawCircle(cx + h * 0.12f, cy - h * 0.05f, h * 0.07f, p);
                break;
            }
            case 'H': {   // porcupine: a dark dome of quills
                float cx = x + w / 2, by = y + h;
                p.setColor(0xFF1C1917);
                p.setStrokeWidth(Math.max(1, T * 0.04f));
                for (int i = 0; i <= 8; i++) {
                    double an = Math.PI + i * Math.PI / 8;
                    c.drawLine(cx, by - h * 0.2f, cx + (float) Math.cos(an) * w * 0.75f, by - h * 0.2f + (float) Math.sin(an) * h * 1.15f, p);
                }
                p.setColor(0xFF44403C);
                c.drawArc(cx - w * 0.5f, by - h * 0.9f, cx + w * 0.5f, by + h * 0.3f, 180, 180, true, p);
                p.setColor(0xFFA8A29E);
                float nx = right ? cx + w * 0.45f : cx - w * 0.45f;
                c.drawCircle(nx, by - h * 0.25f, h * 0.18f, p);
                p.setColor(0xFF000000);
                c.drawCircle(nx + (right ? h * 0.12f : -h * 0.12f), by - h * 0.28f, h * 0.06f, p);
                break;
            }
            default: {   // tiger
                c.save();
                if (!right) c.scale(-1, 1, x + w / 2, 0);
                float step = e.stun > 0 ? 0 : (float) Math.sin(e.t * 12) * h * 0.12f;
                p.setColor(0xFFEA580C);
                p.setStrokeWidth(h * 0.14f);
                p.setStrokeCap(Paint.Cap.ROUND);
                c.drawLine(x + w * 0.2f, y + h * 0.6f, x + w * 0.2f - step, y + h, p);
                c.drawLine(x + w * 0.7f, y + h * 0.6f, x + w * 0.7f + step, y + h, p);
                c.drawLine(x + w * 0.05f, y + h * 0.35f, x - w * 0.15f, y + h * 0.05f + step, p);   // tail
                c.drawRoundRect(x, y + h * 0.2f, x + w * 0.85f, y + h * 0.72f, h * 0.25f, h * 0.25f, p);
                c.drawCircle(x + w * 0.85f, y + h * 0.3f, h * 0.3f, p);
                p.setColor(0xFF111827);
                p.setStrokeWidth(h * 0.07f);
                for (int i = 0; i < 4; i++) c.drawLine(x + w * (0.15f + i * 0.17f), y + h * 0.2f, x + w * (0.1f + i * 0.17f), y + h * 0.5f, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(x + w * 0.95f, y + h * 0.38f, h * 0.12f, p);
                p.setColor(0xFF111827);
                c.drawCircle(x + w * 0.9f, y + h * 0.22f, h * 0.06f, p);
                c.restore();
                p.setStrokeCap(Paint.Cap.BUTT);
                if (e.stun > 0) {
                    tx.setTextAlign(Paint.Align.CENTER);
                    tx.setTextSize(T * 0.35f);
                    tx.setColor(0xFFFDE047);
                    for (int i = 0; i < 3; i++) {
                        double an = t * 5 + i * 2.1;
                        c.drawText("★", x + w / 2 + (float) Math.cos(an) * w * 0.4f, y - h * 0.1f + (float) Math.sin(an) * h * 0.15f, tx);
                    }
                }
            }
        }
    }

    /**
     * Sang Kancil, the mouse-deer: slender legs, a round body, a small head with a big eye, and a scarf in the player's
     * colour. (x, bottom) is where its feet touch; s is about its height.
     */
    static void drawKancil(Canvas c, Paint p, Path path, float x, float bottom, float s, boolean right, float run, int scarf, float t, boolean running) {
        c.save();
        if (!right) c.scale(-1, 1, x, 0);
        float by = bottom - s * 0.42f;
        float bob = running ? (float) Math.abs(Math.sin(run * Math.PI)) * s * 0.05f : (float) Math.sin(t * 2) * s * 0.01f;
        by -= bob;
        // legs
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.07f);
        p.setColor(0xFF78350F);
        float sw = running ? (float) Math.sin(run * Math.PI) * s * 0.14f : 0;
        c.drawLine(x - s * 0.2f, by + s * 0.05f, x - s * 0.22f + sw, bottom, p);
        c.drawLine(x + s * 0.18f, by + s * 0.05f, x + s * 0.2f - sw, bottom, p);
        p.setColor(0xFF92400E);
        c.drawLine(x - s * 0.12f, by + s * 0.05f, x - s * 0.12f - sw, bottom, p);
        c.drawLine(x + s * 0.26f, by + s * 0.05f, x + s * 0.26f + sw, bottom, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        // body and belly
        p.setColor(0xFFB45309);
        c.drawOval(x - s * 0.36f, by - s * 0.22f, x + s * 0.36f, by + s * 0.14f, p);
        p.setColor(0xFFFDE68A);
        c.drawOval(x - s * 0.22f, by - s * 0.02f, x + s * 0.22f, by + s * 0.13f, p);
        p.setColor(0xFF92400E);
        c.drawCircle(x - s * 0.36f, by - s * 0.12f, s * 0.06f, p);   // tail
        // neck and head
        p.setColor(0xFFB45309);
        path.reset();
        path.moveTo(x + s * 0.15f, by - s * 0.15f);
        path.lineTo(x + s * 0.33f, by - s * 0.48f);
        path.lineTo(x + s * 0.45f, by - s * 0.42f);
        path.lineTo(x + s * 0.33f, by - s * 0.05f);
        path.close();
        c.drawPath(path, p);
        float hx = x + s * 0.4f, hy = by - s * 0.5f;
        c.drawCircle(hx, hy, s * 0.15f, p);
        c.drawOval(hx, hy - s * 0.06f, hx + s * 0.28f, hy + s * 0.08f, p);   // snout
        p.setColor(0xFF1C1917);
        c.drawCircle(hx + s * 0.27f, hy - s * 0.01f, s * 0.035f, p);   // nose
        // ear
        p.setColor(0xFF92400E);
        path.reset();
        path.moveTo(hx - s * 0.1f, hy - s * 0.08f);
        path.lineTo(hx - s * 0.16f, hy - s * 0.3f);
        path.lineTo(hx + s * 0.02f, hy - s * 0.12f);
        path.close();
        c.drawPath(path, p);
        // eye
        p.setColor(0xFFFFFFFF);
        c.drawCircle(hx + s * 0.04f, hy - s * 0.04f, s * 0.07f, p);
        p.setColor(0xFF1C1917);
        c.drawCircle(hx + s * 0.06f, hy - s * 0.04f, s * 0.04f, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(hx + s * 0.07f, hy - s * 0.06f, s * 0.013f, p);
        // white chin mark, as real mouse-deer have
        p.setColor(0xFFFEF3C7);
        c.drawCircle(hx + s * 0.12f, hy + s * 0.08f, s * 0.04f, p);
        // scarf in the player's colour
        p.setColor(scarf);
        c.drawRoundRect(x + s * 0.14f, by - s * 0.3f, x + s * 0.38f, by - s * 0.2f, s * 0.04f, s * 0.04f, p);
        path.reset();
        float fl = (float) Math.sin(t * 8) * s * 0.04f;
        path.moveTo(x + s * 0.16f, by - s * 0.27f);
        path.lineTo(x - s * 0.08f, by - s * 0.32f + fl);
        path.lineTo(x - s * 0.05f, by - s * 0.2f + fl);
        path.close();
        c.drawPath(path, p);
        c.restore();
    }

    private void hud(Canvas c, Pl me) {
        float x = vp.left + T * 0.3f, y = vp.top + T * 0.3f, s = T * 0.5f;
        r.set(x - T * 0.15f, y - T * 0.12f, x + T * 5.6f, y + s + T * 0.15f);
        p.setColor(0x88000000);
        c.drawRoundRect(r, T * 0.2f, T * 0.2f, p);
        Badge.dot(c, me.seat, false, x + s * 0.5f, y + s * 0.5f, s * 0.5f);
        for (int i = 0; i < Math.max(3, me.hearts); i++) heart(c, x + s * 1.3f + i * s * 0.75f, y + s * 0.5f, s * 0.3f, i < me.hearts);
        float fx = x + s * 1.3f + Math.max(3, me.hearts) * s * 0.75f + s * 0.2f;
        c.drawBitmap(fruitB, null, rect(fx, y - s * 0.1f, s * 1.2f), null);
        tx.setTextAlign(Paint.Align.LEFT);
        tx.setTextSize(s * 0.8f);
        tx.setColor(0xFFFFFFFF);
        c.drawText("×" + me.fruit, fx + s * 1.15f, y + s * 0.82f, tx);
        if (vp.top == 0 && vp.left == 0) {   // world name, top right of the first view
            tx.setTextAlign(Paint.Align.RIGHT);
            tx.setTextSize(s * 0.7f);
            tx.setColor(0xDDFFFFFF);
            c.drawText("Dunia " + (world + 1) + ": " + WORLD[world], vp.right - T * 0.3f, y + s * 0.75f, tx);
        }
        if (me.done && doneT < 0) {
            tx.setTextAlign(Paint.Align.CENTER);
            tx.setTextSize(T * 0.7f);
            tx.setColor(0xFFFDE047);
            c.drawText("Sampai! Tunggu kawan… " + Math.max(0, (int) (20 - (time - firstDone))) + "s", vp.centerX(), vp.centerY(), tx);
        }
    }

    private final RectF tmp = new RectF();

    private RectF rect(float x, float y, float s) { tmp.set(x, y, x + s, y + s); return tmp; }

    private void heart(Canvas c, float cx, float cy, float s, boolean full) {
        p.setColor(full ? 0xFFF43F5E : 0x55FFFFFF);
        c.drawCircle(cx - s * 0.45f, cy - s * 0.2f, s * 0.5f, p);
        c.drawCircle(cx + s * 0.45f, cy - s * 0.2f, s * 0.5f, p);
        path.reset();
        path.moveTo(cx - s * 0.93f, cy - s * 0.05f);
        path.lineTo(cx, cy + s * 0.95f);
        path.lineTo(cx + s * 0.93f, cy - s * 0.05f);
        path.close();
        c.drawPath(path, p);
    }

    private void drawIntro(Canvas c, float W, float H, float t) {
        float a = Math.min(1, Math.min(introT * 3, (4.5f - introT) * 3));
        p.setColor(Color.argb((int) (220 * a), 5, 8, 20));
        c.drawRect(0, 0, W, H, p);
        int al = (int) (255 * a);
        tx.setTextAlign(Paint.Align.CENTER);
        tx.setTextSize(H * 0.035f);
        tx.setColor(Color.argb(al, 253, 224, 71));
        c.drawText("DUNIA " + (world + 1) + " / " + WORLD.length, W / 2, H * 0.24f, tx);
        tx.setTextSize(H * 0.08f);
        tx.setColor(Color.argb(al, 255, 255, 255));
        c.drawText(WORLD[world], W / 2, H * 0.34f, tx);
        drawKancil(c, q, path, W * 0.5f, H * 0.56f, H * 0.16f, true, t * 2, Party.COLORS[0], t, true);
        android.text.TextPaint tp = storyPaint;
        tp.setTextSize(H * 0.034f);
        tp.setColor(Color.argb(al, 226, 232, 240));
        if (storyLayout == null || storyFor != world || storyW != (int) (W * 0.7f)) {
            storyFor = world;
            storyW = (int) (W * 0.7f);
            storyLayout = new android.text.StaticLayout(STORY[world], tp, storyW, android.text.Layout.Alignment.ALIGN_CENTER, 1.15f, 0, false);
        }
        c.save();
        c.translate(W * 0.15f, H * 0.62f);
        storyLayout.draw(c);
        c.restore();
        tx.setTextSize(H * 0.028f);
        tx.setColor(Color.argb(al, 103, 232, 249));
        c.drawText("Tip: " + TIPS[world] + "   ·   OK untuk mula", W / 2, H * 0.9f, tx);
    }

    private final android.text.TextPaint storyPaint = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
    private android.text.StaticLayout storyLayout;
    private int storyFor = -1, storyW;

    private void drawDone(Canvas c, float W, float H) {
        p.setColor(0xCC05081A);
        c.drawRect(0, 0, W, H, p);
        tx.setTextAlign(Paint.Align.CENTER);
        tx.setTextSize(H * 0.07f);
        tx.setColor(0xFFFDE047);
        c.drawText(WORLD[world] + " selesai!", W / 2, H * 0.3f, tx);
        int k = 0;
        for (Pl pl : pls) {
            if (!pl.on) continue;
            float y = H * (0.42f + k * 0.08f);
            p.setColor(Party.COLORS[pl.seat]);
            c.drawCircle(W * 0.36f, y - H * 0.012f, H * 0.022f, p);
            tx.setTextAlign(Paint.Align.LEFT);
            tx.setTextSize(H * 0.04f);
            tx.setColor(0xFFFFFFFF);
            c.drawText("Pemain " + (pl.seat + 1) + "   ·   " + pl.fruit + " rambutan", W * 0.39f, y, tx);
            k++;
        }
        tx.setTextAlign(Paint.Align.CENTER);
        tx.setTextSize(H * 0.03f);
        tx.setColor(0xFF67E8F9);
        c.drawText(world + 1 < WORLD.length ? "Seterusnya: " + WORLD[world + 1] + "…" : "Ke puncak bukit!", W / 2, H * 0.85f, tx);
    }

    private static int darker(int c, float f) {
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    private static int lighter(int c) {
        return Color.rgb(Math.min(255, Color.red(c) + 50), Math.min(255, Color.green(c) + 50), Math.min(255, Color.blue(c) + 50));
    }
}
