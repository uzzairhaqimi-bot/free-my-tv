package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Lumba Lebuhraya: a motorbike race on a road seen from behind (the classic arcade trick of drawing the road as
 * stacked strips). Each person gets their own view: full screen alone, top and bottom for two, quarters for three or
 * four; the TV's riders fill the field to four. One of four routes (coast, city, mountain, night) and the weather
 * (clear or rain) are picked each race. Two laps. The bike speeds up on its own; ◀▶ steer; curves pull outwards.
 */
final class ModLebuhraya implements Arked4.Mod {
    private static final String[] ROUTE = {"Pantai", "Bandar", "Gunung", "Malam"};
    private static final int SEGS = 500, DRAW = 60, LAPS = 2;
    private static final float SEG = 1, MAXV = 60;   // segments per second at top speed
    // sky, grass light, grass dark, road, scenery
    private static final int[][] PAL = {{0xFF7DD3FC, 0xFFFDE68A, 0xFFFCD34D, 0xFF6B7280, 0xFF16A34A},
            {0xFF93C5FD, 0xFF84CC16, 0xFF65A30D, 0xFF4B5563, 0xFF64748B},
            {0xFFBAE6FD, 0xFF65A30D, 0xFF4D7C0F, 0xFF57534E, 0xFF78716C},
            {0xFF0F172A, 0xFF1E3A8A, 0xFF172554, 0xFF1F2937, 0xFFFDE047}};

    private final Arked4 a;
    private final float[] curve = new float[SEGS], hill = new float[SEGS];
    private final float[] pos = new float[4], off = new float[4], spd = new float[4], camH = new float[4];
    private final int[] lap = new int[4], place = new int[4];
    private final boolean[] fin = new boolean[4];
    private int route, finished;
    private boolean rain;
    private float firstFin = -1;
    private final Path road = new Path();
    private final RectF vp = new RectF();
    private final float[] rainX = new float[60], rainY = new float[60];

    ModLebuhraya(Arked4 a) { this.a = a; }

    @Override public void build() {
        route = a.rnd.nextInt(ROUTE.length);
        rain = route != 3 && a.rnd.nextFloat() < 0.35f;
        // the road: stretches of curves and hills, smoothed in and out
        int i = 0;
        while (i < SEGS) {
            int len = 30 + a.rnd.nextInt(50);
            float cv = (a.rnd.nextFloat() - 0.5f) * (route == 2 ? 6 : route == 1 ? 3 : 4.5f);
            float hl = route == 2 ? (a.rnd.nextFloat() - 0.5f) * 40 : route == 0 ? (a.rnd.nextFloat() - 0.5f) * 10 : 0;
            if (i < 20 || i > SEGS - 30) { cv = 0; hl = 0; }   // straight at the start line
            for (int k = 0; k < len && i < SEGS; k++, i++) {
                float e = (float) Math.sin(Math.PI * k / len);
                curve[i] = cv * e;
                hill[i] = hl * e;
            }
        }
        int k = 0;
        for (Arked4.P pl : a.ps) {
            int s = pl.seat;
            pos[s] = SEGS - 3 - (k / 2) * 3;
            off[s] = k % 2 == 0 ? -0.4f : 0.4f;
            spd[s] = 0;
            lap[s] = -1;
            fin[s] = false;
            if (pl.on) k++;
        }
        finished = 0;
        firstFin = -1;
        for (int r = 0; r < rainX.length; r++) { rainX[r] = a.rnd.nextFloat(); rainY[r] = a.rnd.nextFloat(); }
    }

    @Override public void update(float dt) {
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            if (fin[s]) { spd[s] *= 0.98f; pos[s] += spd[s] * dt; continue; }
            float steer;
            int seg = ((int) pos[s]) % SEGS;
            if (pl.bot) {
                float ahead = curve[(seg + 8) % SEGS];
                steer = Math.max(-1, Math.min(1, -off[s] * 1.5f + ahead * 0.35f + (s % 2 == 0 ? 0.15f : -0.15f)));
            } else steer = a.ix(pl);
            boolean offRoad = Math.abs(off[s]) > 1;
            float top = MAXV * (pl.bot ? 0.88f + 0.02f * s : 1) * (offRoad ? 0.4f : 1) * (rain ? 0.9f : 1);
            spd[s] += (top - spd[s]) * Math.min(1, dt * (spd[s] < top ? 0.6f : 2.5f));
            off[s] += steer * dt * 1.9f * (spd[s] / MAXV + 0.2f) * (rain ? 0.85f : 1);
            off[s] -= curve[seg] * dt * (spd[s] / MAXV) * 0.35f;   // curves pull outwards
            off[s] = Math.max(-2.2f, Math.min(2.2f, off[s]));
            float before = pos[s];
            pos[s] += spd[s] * dt;
            if ((int) (before / SEGS) != (int) (pos[s] / SEGS)) {
                lap[s]++;
                if (lap[s] >= LAPS) {
                    fin[s] = true;
                    place[s] = ++finished;
                    if (firstFin < 0) firstFin = a.time;
                    if (!pl.bot) a.sound.sfx(GameMusic.SFX_WIN);
                } else if (lap[s] >= 1 && !pl.bot) a.sound.sfx(GameMusic.SFX_OK);
            }
            // bumping other riders
            for (Arked4.P o : a.ps) {
                if (o == pl || !o.on) continue;
                float dz = pos[o.seat] - pos[s];
                if (dz > 0 && dz < 1.2f && Math.abs(off[o.seat] - off[s]) < 0.35f) { spd[s] *= 0.7f; off[s] += off[s] < off[o.seat] ? -0.15f : 0.15f; }
            }
        }
        int humans = 0, humansDone = 0;
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            if (!pl.bot) { humans++; if (fin[pl.seat]) humansDone++; }
            if (fin[pl.seat]) continue;
            int r = finished + 1;
            for (Arked4.P o : a.ps) if (o != pl && o.on && !fin[o.seat] && pos[o.seat] > pos[pl.seat]) r++;
            place[pl.seat] = r;
        }
        if ((humans > 0 && humansDone == humans) || (firstFin >= 0 && a.time - firstFin > 20)) {
            Arked4.P best = null;
            for (Arked4.P pl : a.ps) if (pl.on && (best == null || place[pl.seat] < place[best.seat])) best = pl;
            a.finish(a.who(best) + " juara Lebuhraya " + ROUTE[route] + "! 🏍");
        }
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        int n = 0;
        for (Arked4.P pl : a.ps) if (pl.on && !pl.bot) n++;
        n = Math.max(1, n);
        float vw = n >= 3 ? W / 2 : W, vh = n >= 2 ? H / 2 : H;
        int k = 0;
        for (Arked4.P pl : a.ps) {
            if (!pl.on || pl.bot) continue;
            float x0 = n >= 3 ? (k % 2) * vw : 0, y0 = n >= 3 ? (k / 2) * vh : k * vh;
            vp.set(x0, y0, x0 + vw, y0 + vh);
            c.save();
            c.clipRect(vp);
            c.translate(x0, y0);
            view(c, pl.seat, vw, vh, t);
            c.restore();
            k++;
        }
        a.p.setColor(0xFF0B0B12);
        if (n >= 2) c.drawRect(0, H / 2 - 3, W, H / 2 + 3, a.p);
        if (n >= 3) c.drawRect(W / 2 - 3, 0, W / 2 + 3, H, a.p);
    }

    /** One rider's view: sky, hills, the road strips from far to near, the other riders, then their own bike. */
    private void view(Canvas c, int me, float vw, float vh, float t) {
        Paint p = a.p;
        int[] pal = PAL[route];
        p.setColor(pal[0]);
        c.drawRect(0, 0, vw, vh, p);
        if (route == 3) { p.setColor(0xCCFFFFFF); for (int i = 0; i < 20; i++) c.drawCircle((i * 97 % 100) / 100f * vw, (i * 37 % 50) / 100f * vh, 2, p); }
        // distant hills that slide with the curves
        float bend = 0;
        int base = (int) pos[me];
        for (int i = 0; i < 20; i++) bend += curve[(base + i) % SEGS];
        p.setColor(pal[4]);
        float hx = -(bend * vw * 0.01f) % vw;
        for (int i = -1; i < 4; i++) c.drawCircle(hx + i * vw * 0.4f, vh * 0.5f, vw * 0.18f, p);
        float horizon = vh * 0.5f, camHeight = 1500, depth = 0.84f * vh / 1.6f;
        float fz = pos[me] - (int) pos[me];
        float x = 0, dx = -curve[base % SEGS] * fz;
        float maxY = vh;
        float playerHill = hill[base % SEGS];
        float[] sx = segX, sy = segY, sw = segW;
        for (int i = 0; i < DRAW; i++) {   // project each strip
            int seg = (base + i) % SEGS;
            float z = (i + 1 - fz) * 200;
            float scale = depth / z;
            sy[i] = horizon + scale * (camHeight - (hill[seg] - playerHill) * 40) ;
            sx[i] = vw / 2 + scale * (x - off[me] * 1000) ;
            sw[i] = scale * 1000;
            x += dx;
            dx += curve[seg] * 3;
        }
        for (int i = DRAW - 1; i > 0; i--) {
            int seg = (base + i) % SEGS;
            float y1 = sy[i], y2 = sy[i - 1];
            if (y2 <= y1) continue;
            boolean light = (seg / 3) % 2 == 0;
            p.setColor(light ? pal[1] : pal[2]);
            c.drawRect(0, y1, vw, y2, p);
            quad(c, p, sx[i], y1, sw[i] * 1.15f, sx[i - 1], y2, sw[i - 1] * 1.15f, light ? 0xFFFFFFFF : 0xFFDC2626);
            quad(c, p, sx[i], y1, sw[i], sx[i - 1], y2, sw[i - 1], pal[3]);
            if (light) quad(c, p, sx[i], y1, sw[i] * 0.03f, sx[i - 1], y2, sw[i - 1] * 0.03f, route == 3 ? 0xFFFDE047 : 0xFFFFFFFF);
            if (seg % 12 == 0) scenery(c, p, sx[i] + sw[i] * 1.6f * (seg % 24 == 0 ? 1 : -1), y1, sw[i], pal[4]);
        }
        // the other riders, far to near
        for (int i = DRAW - 1; i > 0; i--) {
            for (Arked4.P o : a.ps) {
                if (!o.on || o.seat == me) continue;
                float dz = pos[o.seat] - pos[me];
                if (dz < i - 1 || dz >= i) continue;
                float bx = sx[i] + (off[o.seat] - 0) * sw[i] + 0, by = sy[i];
                float s = sw[i] * 0.35f;
                bike(c, p, bx, by, s, Party.COLORS[o.seat], 0, t);
                if (s > 6) Badge.pin(c, o.seat, o.bot, bx, by - s * 1.3f, Math.max(8, s * 0.35f), t);
            }
        }
        float lean = Math.max(-1, Math.min(1, a.ix(a.ps[me]) - curve[base % SEGS] * 0.2f));
        bike(c, p, vw / 2, vh * 0.94f, vh * 0.2f, Party.COLORS[me], lean, t);
        if (rain) {
            p.setColor(0x66E0F2FE);
            p.setStrokeWidth(2);
            for (int i = 0; i < rainX.length; i++) {
                float rx = rainX[i] * vw, ry = ((rainY[i] + t * 1.5f) % 1f) * vh;
                c.drawLine(rx, ry, rx - vh * 0.01f, ry + vh * 0.04f, p);
            }
        }
        // the rider's panel
        Badge.dot(c, me, false, vh * 0.06f, vh * 0.07f, vh * 0.04f);
        a.tx.setTextAlign(Paint.Align.LEFT);
        a.tx.setTextSize(vh * 0.06f);
        a.tx.setColor(0xFFFFFFFF);
        c.drawText(fin[me] ? "Tamat #" + place[me] : "#" + place[me] + "   Pusingan " + Math.max(1, Math.min(LAPS, lap[me] + 1)) + "/" + LAPS,
                vh * 0.12f, vh * 0.09f, a.tx);
        a.tx.setTextAlign(Paint.Align.RIGHT);
        a.tx.setTextSize(vh * 0.05f);
        c.drawText((int) (spd[me] * 4) + " km/j  ·  " + ROUTE[route] + (rain ? " (hujan)" : ""), vw - vh * 0.03f, vh * 0.09f, a.tx);
    }

    private final float[] segX = new float[DRAW], segY = new float[DRAW], segW = new float[DRAW];

    private void quad(Canvas c, Paint p, float x1, float y1, float w1, float x2, float y2, float w2, int col) {
        road.reset();
        road.moveTo(x1 - w1, y1);
        road.lineTo(x1 + w1, y1);
        road.lineTo(x2 + w2, y2 + 1);
        road.lineTo(x2 - w2, y2 + 1);
        road.close();
        p.setColor(col);
        c.drawPath(road, p);
    }

    private void scenery(Canvas c, Paint p, float x, float y, float s, int col) {
        if (s < 2) return;
        if (route == 0) {   // coconut palms
            p.setColor(0xFF92400E);
            c.drawRect(x - s * 0.04f, y - s * 0.9f, x + s * 0.04f, y, p);
            p.setColor(0xFF15803D);
            c.drawCircle(x, y - s * 0.9f, s * 0.22f, p);
        } else if (route == 1) {   // buildings
            p.setColor(col);
            c.drawRect(x - s * 0.25f, y - s * 1.2f, x + s * 0.25f, y, p);
            p.setColor(0xFFFDE68A);
            c.drawRect(x - s * 0.15f, y - s, x - s * 0.05f, y - s * 0.9f, p);
        } else if (route == 2) {   // pines
            p.setColor(0xFF14532D);
            road.reset();
            road.moveTo(x, y - s);
            road.lineTo(x + s * 0.25f, y);
            road.lineTo(x - s * 0.25f, y);
            road.close();
            c.drawPath(road, p);
        } else {   // street lamps
            p.setColor(0xFF9CA3AF);
            c.drawRect(x - s * 0.02f, y - s, x + s * 0.02f, y, p);
            p.setColor(0xFFFDE047);
            c.drawCircle(x, y - s, s * 0.08f, p);
        }
    }

    /** A motorbike from behind: wheel, body in the rider's colour, a helmet. */
    private void bike(Canvas c, Paint p, float x, float y, float s, int col, float lean, float t) {
        c.save();
        c.rotate(lean * 12, x, y);
        p.setColor(0x55000000);
        c.drawOval(x - s * 0.35f, y - s * 0.06f, x + s * 0.35f, y + s * 0.04f, p);
        p.setColor(0xFF111827);
        c.drawRoundRect(x - s * 0.09f, y - s * 0.4f, x + s * 0.09f, y, s * 0.06f, s * 0.06f, p);
        p.setColor(col);
        c.drawRoundRect(x - s * 0.25f, y - s * 0.62f, x + s * 0.25f, y - s * 0.3f, s * 0.08f, s * 0.08f, p);
        p.setColor(0xFFEF4444);
        c.drawRect(x - s * 0.2f, y - s * 0.36f, x - s * 0.1f, y - s * 0.31f, p);
        c.drawRect(x + s * 0.1f, y - s * 0.36f, x + s * 0.2f, y - s * 0.31f, p);
        p.setColor(0xFF1F2937);
        c.drawRoundRect(x - s * 0.17f, y - s * 0.95f, x + s * 0.17f, y - s * 0.6f, s * 0.06f, s * 0.06f, p);
        p.setColor(col);
        c.drawCircle(x, y - s * 1.02f, s * 0.15f, p);
        p.setColor(0x88FFFFFF);
        c.drawCircle(x - s * 0.05f, y - s * 1.07f, s * 0.05f, p);
        c.restore();
    }
}
