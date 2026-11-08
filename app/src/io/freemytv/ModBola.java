package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Bola Sepak Kampung: two against two, seen from above. Players 1 and 3 play for Biru, 2 and 4 for Merah; the TV
 * fills empty places. Run into the ball to dribble it, OK / A to kick it hard the way you face. First to five goals,
 * or the most after three minutes. The pitch changes every match: a padang, the beach or a school field.
 */
final class ModBola implements Arked4.Mod {
    private static final String[] FIELD = {"Padang Kampung", "Pantai", "Padang Sekolah"};
    private static final int[][] FIELD_COL = {{0xFF16A34A, 0xFF15803D}, {0xFFFDE68A, 0xFFFCD34D}, {0xFF65A30D, 0xFF4D7C0F}};
    private static final float TIME = 180;
    private final Arked4 a;
    private float bx, by, bvx, bvy, cool, goalT = -1, fx0, fy0, fx1, fy1;
    private final int[] goals = new int[2];
    private final float[] faceX = new float[4], faceY = new float[4], kickCool = new float[4];
    private int field, lastTouch = -1;

    ModBola(Arked4 a) { this.a = a; }

    private int team(int seat) { return seat % 2; }   // 0 Biru (P1, P3), 1 Merah (P2, P4)

    @Override public void build() {
        field = a.rnd.nextInt(FIELD.length);
        goals[0] = goals[1] = 0;
        fx0 = a.W * 0.05f; fx1 = a.W * 0.95f; fy0 = a.H * 0.12f; fy1 = a.H * 0.96f;
        kickoff();
    }

    private void kickoff() {
        bx = a.W / 2; by = (fy0 + fy1) / 2; bvx = bvy = 0;
        for (Arked4.P pl : a.ps) {
            int s = pl.seat;
            boolean left = team(s) == 0;
            pl.x = a.W * (left ? (s < 2 ? 0.3f : 0.15f) : (s < 2 ? 0.7f : 0.85f));
            pl.y = by + (s < 2 ? -1 : 1) * a.H * 0.15f;
            faceX[s] = left ? 1 : -1; faceY[s] = 0;
        }
        goalT = -1;
    }

    @Override public void update(float dt) {
        float W = a.W, H = a.H, pr = H * 0.035f, br = H * 0.018f;
        if (goalT >= 0) {
            goalT += dt;
            if (goalT > 2) {
                if (goals[0] >= 5 || goals[1] >= 5) { end(); return; }
                kickoff();
            }
            return;
        }
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            kickCool[s] -= dt;
            float mx, my;
            boolean kick;
            if (pl.bot) {
                // the TV: one goes for the ball, the other keeps back; shoot when facing the right way
                float goalX = team(s) == 0 ? fx1 : fx0;
                boolean chaser = true;
                for (Arked4.P o : a.ps) if (o != pl && o.on && team(o.seat) == team(s) && Math.hypot(o.x - bx, o.y - by) < Math.hypot(pl.x - bx, pl.y - by)) chaser = false;
                float tx, ty;
                if (chaser) { tx = bx - Math.signum(goalX - bx) * pr; ty = by; }
                else { tx = team(s) == 0 ? W * 0.25f : W * 0.75f; ty = by * 0.5f + (fy0 + fy1) * 0.25f; }
                mx = tx - pl.x; my = ty - pl.y;
                float l = (float) Math.hypot(mx, my);
                if (l > 1) { mx /= l; my /= l; } else { mx = 0; my = 0; }
                kick = chaser && Math.hypot(pl.x - bx, pl.y - by) < pr * 1.6f && Math.signum(goalX - pl.x) == Math.signum(faceX[s]) && a.rnd.nextFloat() < 0.1f;
                mx *= 0.85f; my *= 0.85f;
            } else { mx = a.ix(pl); my = a.iy(pl); kick = a.pressA(pl); }
            float l = (float) Math.hypot(mx, my);
            if (l > 0.25f) {
                faceX[s] = mx / l; faceY[s] = my / l;
                float sp = W * 0.2f * Math.min(1, l);
                pl.x = Math.max(fx0, Math.min(fx1, pl.x + faceX[s] * sp * dt));
                pl.y = Math.max(fy0, Math.min(fy1, pl.y + faceY[s] * sp * dt));
            }
            float d = (float) Math.hypot(pl.x - bx, pl.y - by);
            if (d < pr + br) {   // touch: the ball goes on in front
                float nx = (bx - pl.x) / Math.max(1, d), ny = (by - pl.y) / Math.max(1, d);
                bx = pl.x + nx * (pr + br);
                by = pl.y + ny * (pr + br);
                bvx = bvx * 0.3f + nx * W * 0.22f;
                bvy = bvy * 0.3f + ny * W * 0.22f;
                lastTouch = s;
            }
            if (kick && kickCool[s] <= 0 && d < pr * 1.8f) {
                kickCool[s] = 0.4f;
                bvx = faceX[s] * W * 0.75f;
                bvy = faceY[s] * W * 0.75f;
                lastTouch = s;
                a.sound.sfx(GameMusic.SFX_JUMP);
                if (!pl.bot) a.party.buzz(s, 40);
            }
        }
        // players don't overlap
        for (int i = 0; i < 4; i++) for (int j = i + 1; j < 4; j++) {
            Arked4.P p1 = a.ps[i], p2 = a.ps[j];
            if (!p1.on || !p2.on) continue;
            float dx = p2.x - p1.x, dy = p2.y - p1.y, d = (float) Math.hypot(dx, dy);
            if (d > 0 && d < pr * 2) { float push = (pr * 2 - d) / 2; p1.x -= dx / d * push; p1.y -= dy / d * push; p2.x += dx / d * push; p2.y += dy / d * push; }
        }
        bx += bvx * dt;
        by += bvy * dt;
        float fr = field == 1 ? 2.2f : 1.4f;   // sand slows the ball more
        bvx -= bvx * Math.min(1, dt * fr);
        bvy -= bvy * Math.min(1, dt * fr);
        float gTop = (fy0 + fy1) / 2 - H * 0.12f, gBot = (fy0 + fy1) / 2 + H * 0.12f;
        if (by < fy0 + br) { by = fy0 + br; bvy = Math.abs(bvy); }
        if (by > fy1 - br) { by = fy1 - br; bvy = -Math.abs(bvy); }
        if (bx < fx0 + br || bx > fx1 - br) {
            if (by > gTop && by < gBot) {   // goal!
                int scorer = bx > W / 2 ? 0 : 1;
                goals[scorer]++;
                if (lastTouch >= 0 && team(lastTouch) == scorer) a.ps[lastTouch].score++;
                goalT = 0;
                a.sound.sfx(GameMusic.SFX_WIN);
                for (Arked4.P pl : a.ps) if (pl.on && team(pl.seat) == scorer) a.party.buzz(pl.seat, 150);
                bx = Math.max(fx0, Math.min(fx1, bx));
                return;
            }
            if (bx < fx0 + br) { bx = fx0 + br; bvx = Math.abs(bvx); } else { bx = fx1 - br; bvx = -Math.abs(bvx); }
        }
        if (a.time > TIME) end();
    }

    private void end() {
        String w = goals[0] == goals[1] ? "Seri " + goals[0] + "–" + goals[1] + "!" : (goals[0] > goals[1] ? "Pasukan Biru" : "Pasukan Merah") + " menang " + Math.max(goals[0], goals[1]) + "–" + Math.min(goals[0], goals[1]) + "! ⚽";
        a.finish(w);
    }

    @Override public void draw(Canvas c, float t) {
        float W = a.W, H = a.H;
        Paint p = a.p;
        int[] col = FIELD_COL[field];
        for (int i = 0; i < 10; i++) {   // mown stripes
            p.setColor(i % 2 == 0 ? col[0] : col[1]);
            c.drawRect(W * i / 10f, 0, W * (i + 1) / 10f, H, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(H * 0.006f);
        p.setColor(0xCCFFFFFF);
        c.drawRect(fx0, fy0, fx1, fy1, p);
        c.drawLine(W / 2, fy0, W / 2, fy1, p);
        float my = (fy0 + fy1) / 2;
        c.drawCircle(W / 2, my, H * 0.12f, p);
        c.drawRect(fx0, my - H * 0.22f, fx0 + W * 0.1f, my + H * 0.22f, p);
        c.drawRect(fx1 - W * 0.1f, my - H * 0.22f, fx1, my + H * 0.22f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF3B82F6);   // goals, in the team colours
        c.drawRect(fx0 - W * 0.025f, my - H * 0.12f, fx0, my + H * 0.12f, p);
        p.setColor(0xFFEF4444);
        c.drawRect(fx1, my - H * 0.12f, fx1 + W * 0.025f, my + H * 0.12f, p);
        for (Arked4.P pl : a.ps) {
            if (!pl.on) continue;
            int s = pl.seat;
            float r = H * 0.035f;
            Badge.ring(c, s, pl.x, pl.y, r * 1.3f, t);
            p.setColor(team(s) == 0 ? 0xFF2563EB : 0xFFDC2626);   // the team's jersey
            c.drawCircle(pl.x, pl.y, r, p);
            p.setColor(Party.COLORS[s]);   // the player's own colour as a band
            c.drawCircle(pl.x, pl.y, r * 0.55f, p);
            p.setColor(0xFFFDE7C7);
            c.drawCircle(pl.x + faceX[s] * r * 0.5f, pl.y + faceY[s] * r * 0.5f, r * 0.4f, p);
            a.label(c, pl, pl.x, pl.y - r);
        }
        p.setColor(0x55000000);
        c.drawCircle(bx + 4, by + 5, H * 0.018f, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(bx, by, H * 0.018f, p);
        p.setColor(0xFF111827);
        c.drawCircle(bx + (float) Math.sin(bx * 0.05f) * H * 0.006f, by, H * 0.007f, p);
        long left = Math.max(0, (long) (TIME - a.time));
        a.tx.setTextAlign(Paint.Align.CENTER);
        a.tx.setTextSize(H * 0.05f);
        a.tx.setColor(0xFFFFFFFF);
        c.drawText("BIRU " + goals[0] + "  –  " + goals[1] + " MERAH", W / 2, H * 0.075f, a.tx);
        a.tx.setTextSize(H * 0.026f);
        a.tx.setTextAlign(Paint.Align.RIGHT);
        c.drawText(FIELD[field] + "  " + left / 60 + ":" + String.format(java.util.Locale.US, "%02d", left % 60), W * 0.99f, H * 0.045f, a.tx);
        a.tx.setTextAlign(Paint.Align.LEFT);
        c.drawText("P1+P3 = Biru · P2+P4 = Merah", W * 0.01f, H * 0.045f, a.tx);
        if (goalT >= 0) {
            a.tx.setTextAlign(Paint.Align.CENTER);
            a.tx.setTextSize(H * 0.12f);
            a.tx.setColor(0xFFFDE047);
            c.drawText("GOL!", W / 2, H * 0.55f, a.tx);
        }
    }
}
