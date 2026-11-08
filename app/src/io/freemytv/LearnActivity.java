package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * Fork extra: Kelas Bijak, learning games for ages 7 to 14 with Bijak the owl (this fork's own mascot).
 * Maths, Bahasa Melayu, English and Science in six ways to play; the level follows each child.
 * Drawn on one Canvas and played with the TV remote: ▲▼◀▶ to choose, OK to answer, Back to go back.
 */
public class LearnActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        UiFxLite.init(this);
        Board v = new Board(this);
        setContentView(v);
        v.setFocusable(true);
        v.requestFocus();
    }

    /** Small sound helper: the system's own click sounds, so no files are needed. */
    static final class UiFxLite {
        static android.media.AudioManager am;
        static void init(Context c) { am = (android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE); }
        static void move() { try { am.playSoundEffect(android.media.AudioManager.FX_FOCUS_NAVIGATION_RIGHT, 0.4f); } catch (Throwable ignored) { } }
        static void ok() { try { am.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK, 0.7f); } catch (Throwable ignored) { } }
    }

    final class Board extends View {
        static final int KIDS = 0, HUB = 1, SUBJECT = 2, PLAY = 3, END = 4;
        static final int QUIZ = 0, ROCKET = 1, BALLOON = 2, BOSS = 3, REVIEW = 4, DAILY = 5;
        final String[] MODES = {"Kuiz Pantas", "Roket ke Bulan", "Pecah Belon", "Lawan Si Lembap", "Ulang Kaji", "Cabaran Harian"};
        final String[] MODE_HINT = {"10 soalan, lawan masa", "Jawab betul, roket naik", "Pilih belon yang betul", "Kalahkan raksasa lendir",
                "Soalan yang pernah salah", "10 soalan campur, setiap hari"};
        final int[] MODE_COL = {0xFF38BDF8, 0xFFA78BFA, 0xFFF472B6, 0xFF4ADE80, 0xFFFBBF24, 0xFFFB7185};
        final int[] SUBJ_COL = {0xFF38BDF8, 0xFFF59E0B, 0xFF34D399, 0xFFA78BFA};
        final String[] SUBJ_ICON = {"123", "BM", "ABC", "⚗"};

        final LearnStore store;
        final LearnBank bank;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), g = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        final Random rnd = new Random();
        final float[] starX = new float[60], starY = new float[60];
        float W, H;
        Shader sky;
        int screen = KIDS, sel, kidSel, mode, subject;
        LearnStore.Kid kid;

        // a round
        LearnBank.Q q;
        int qNo, rights, total, pick;
        long qStart, feedbackAt;
        boolean feedback, lastRight;
        float timeLimit = 20f;
        float rocket, rocketShake;
        int bossHp, hearts;
        float balloonY;
        int earned;
        String endLine = "";
        final List<float[]> confetti = new ArrayList<>();
        long last;
        float t;
        LearnBank daily;

        Board(Context c) {
            super(c);
            store = new LearnStore(c);
            bank = new LearnBank(System.nanoTime());
            store.applyPack(bank);
            store.refreshPack(c);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            tp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            for (int i = 0; i < starX.length; i++) { starX[i] = rnd.nextFloat(); starY[i] = rnd.nextFloat(); }
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            W = w;
            H = h;
            sky = new LinearGradient(0, 0, 0, H, 0xFF1E1B4B, 0xFF0F766E, Shader.TileMode.CLAMP);
        }

        // ================================================================ drawing helpers

        void text(Canvas c, String s, float x, float y, float size, int color, Paint.Align a) {
            p.setTextAlign(a);
            p.setTextSize(size);
            p.setColor(color);
            c.drawText(s, x, y, p);
        }

        /** Text fitted into a width: shrinks until it fits on one line. */
        void fit(Canvas c, String s, float cx, float cy, float maxW, float size, int color) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(size);
            while (p.measureText(s) > maxW && size > H * 0.018f) { size *= 0.92f; p.setTextSize(size); }
            p.setColor(color);
            c.drawText(s, cx, cy + size * 0.35f, p);
        }

        void wrapped(Canvas c, String s, float cx, float top, float width, float size, int color) {
            tp.setTextSize(size);
            tp.setColor(color);
            StaticLayout l = new StaticLayout(s, tp, (int) width, Layout.Alignment.ALIGN_CENTER, 1.1f, 0, false);
            c.save();
            c.translate(cx - width / 2, top);
            l.draw(c);
            c.restore();
        }

        void card(Canvas c, RectF rr, int color, boolean on) {
            g.setStyle(Paint.Style.FILL);
            g.setColor(on ? color : 0x26FFFFFF);
            g.setShadowLayer(on ? 26 : 8, 0, on ? 6 : 3, on ? color : 0x66000000);
            c.drawRoundRect(rr, H * 0.035f, H * 0.035f, g);
            g.setShadowLayer(0, 0, 0, 0);
            if (on) {
                g.setStyle(Paint.Style.STROKE);
                g.setStrokeWidth(H * 0.006f);
                g.setColor(0xFFFFFFFF);
                c.drawRoundRect(rr, H * 0.035f, H * 0.035f, g);
                g.setStyle(Paint.Style.FILL);
            }
        }

        /** Bijak the owl: round body, big eyes, little ear tufts. mood: 0 calm, 1 happy, 2 oops. */
        void owl(Canvas c, float cx, float cy, float s, int body, int mood) {
            float bob = (float) Math.sin(t * 3) * s * 0.04f;
            cy += bob;
            Path ear = new Path();
            g.setColor(Color.rgb(Color.red(body) * 3 / 4, Color.green(body) * 3 / 4, Color.blue(body) * 3 / 4));
            ear.moveTo(cx - s * 0.45f, cy - s * 0.55f); ear.lineTo(cx - s * 0.3f, cy - s * 0.95f); ear.lineTo(cx - s * 0.1f, cy - s * 0.6f); ear.close();
            ear.moveTo(cx + s * 0.45f, cy - s * 0.55f); ear.lineTo(cx + s * 0.3f, cy - s * 0.95f); ear.lineTo(cx + s * 0.1f, cy - s * 0.6f); ear.close();
            c.drawPath(ear, g);
            g.setColor(body);
            g.setShadowLayer(s * 0.15f, 0, s * 0.05f, 0x66000000);
            c.drawOval(cx - s * 0.6f, cy - s * 0.75f, cx + s * 0.6f, cy + s * 0.8f, g);
            g.setShadowLayer(0, 0, 0, 0);
            g.setColor(0x33FFFFFF);
            c.drawOval(cx - s * 0.35f, cy - s * 0.05f, cx + s * 0.35f, cy + s * 0.7f, g);   // belly
            float wing = mood == 1 ? (float) Math.sin(t * 14) * 20 : 0;
            g.setColor(Color.rgb(Color.red(body) * 4 / 5, Color.green(body) * 4 / 5, Color.blue(body) * 4 / 5));
            c.save(); c.rotate(-15 - wing, cx - s * 0.55f, cy); c.drawOval(cx - s * 0.75f, cy - s * 0.1f, cx - s * 0.45f, cy + s * 0.5f, g); c.restore();
            c.save(); c.rotate(15 + wing, cx + s * 0.55f, cy); c.drawOval(cx + s * 0.45f, cy - s * 0.1f, cx + s * 0.75f, cy + s * 0.5f, g); c.restore();
            for (int side = -1; side <= 1; side += 2) {
                float ex = cx + side * s * 0.25f, ey = cy - s * 0.3f;
                g.setColor(0xFFFFFFFF);
                c.drawCircle(ex, ey, s * 0.22f, g);
                g.setColor(0xFF1F2937);
                if (mood == 1) {   // happy eyes: arches
                    g.setStyle(Paint.Style.STROKE);
                    g.setStrokeWidth(s * 0.06f);
                    c.drawArc(ex - s * 0.12f, ey - s * 0.08f, ex + s * 0.12f, ey + s * 0.14f, 200, 140, false, g);
                    g.setStyle(Paint.Style.FILL);
                } else {
                    float blink = (t % 4) < 0.12f ? 0.2f : 1f;
                    c.drawOval(ex - s * 0.1f, ey - s * 0.1f * blink, ex + s * 0.1f, ey + s * 0.1f * blink, g);
                    g.setColor(0xFFFFFFFF);
                    c.drawCircle(ex + s * 0.04f, ey - s * 0.04f, s * 0.03f, g);
                }
            }
            Path beak = new Path();
            beak.moveTo(cx - s * 0.08f, cy - s * 0.12f); beak.lineTo(cx + s * 0.08f, cy - s * 0.12f); beak.lineTo(cx, cy + s * 0.02f); beak.close();
            g.setColor(0xFFF59E0B);
            c.drawPath(beak, g);
            if (mood == 2) {   // a little sweat drop
                g.setColor(0xFF93C5FD);
                c.drawCircle(cx + s * 0.55f, cy - s * 0.55f, s * 0.07f, g);
            }
        }

        // ================================================================ frame

        @Override protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            t += dt;
            p.setShader(sky);
            c.drawRect(0, 0, W, H, p);
            p.setShader(null);
            for (int i = 0; i < starX.length; i++) {
                p.setColor(Color.argb(60 + (int) (60 * Math.sin(t * 2 + i)), 255, 255, 255));
                c.drawCircle(starX[i] * W, starY[i] * H * 0.7f, H * 0.003f, p);
            }
            switch (screen) {
                case KIDS: drawKids(c); break;
                case HUB: drawHub(c); break;
                case SUBJECT: drawSubjects(c); break;
                case PLAY: update(dt); drawPlay(c); break;
                default: drawEnd(c);
            }
            for (int i = confetti.size() - 1; i >= 0; i--) {
                float[] f = confetti.get(i);
                f[1] += f[3] * dt; f[0] += f[2] * dt; f[3] += H * 0.8f * dt; f[4] -= dt;
                if (f[4] <= 0) { confetti.remove(i); continue; }
                p.setColor((int) f[5]);
                c.drawRect(f[0], f[1], f[0] + H * 0.012f, f[1] + H * 0.02f, p);
            }
            postInvalidateOnAnimation();
        }

        void burst(float x, float y) {
            int[] cols = {0xFFFACC15, 0xFFF472B6, 0xFF38BDF8, 0xFF4ADE80, 0xFFFB923C};
            for (int i = 0; i < 40; i++) {
                confetti.add(new float[]{x, y, (rnd.nextFloat() - 0.5f) * W * 0.6f, -H * (0.3f + rnd.nextFloat() * 0.6f), 1.6f, cols[i % cols.length]});
            }
        }

        // ================================================================ screens

        void drawKids(Canvas c) {
            text(c, "Kelas Bijak", W / 2, H * 0.14f, H * 0.08f, 0xFFFFFFFF, Paint.Align.CENTER);
            text(c, "Siapa yang nak belajar hari ini?", W / 2, H * 0.21f, H * 0.035f, 0xCCFFFFFF, Paint.Align.CENTER);
            float cw = W * 0.2f, gap = W * 0.025f, x0 = (W - 4 * cw - 3 * gap) / 2;
            for (int i = 0; i < LearnStore.KIDS; i++) {
                LearnStore.Kid k = store.kids[i];
                boolean on = i == kidSel;
                r.set(x0 + i * (cw + gap), H * 0.3f, x0 + i * (cw + gap) + cw, H * 0.78f);
                if (on) { r.top -= H * 0.02f; }
                card(c, r, 0x55FFFFFF, on);
                owl(c, r.centerX(), r.top + H * 0.17f, H * 0.11f, LearnStore.COLORS[i], on ? 1 : 0);
                fit(c, k.name, r.centerX(), r.top + H * 0.33f, cw * 0.9f, H * 0.04f, 0xFFFFFFFF);
                text(c, "Umur " + k.age + "  ·  ⭐ " + k.stars, r.centerX(), r.top + H * 0.39f, H * 0.028f, 0xDDFFFFFF, Paint.Align.CENTER);
                if (k.days > 1) text(c, "🔥 " + k.days + " hari", r.centerX(), r.top + H * 0.44f, H * 0.026f, 0xFFFDE68A, Paint.Align.CENTER);
            }
            text(c, "◀▶ pilih   ·   OK masuk   ·   Menu: tukar nama & umur", W / 2, H * 0.9f, H * 0.028f, 0x99FFFFFF, Paint.Align.CENTER);
        }

        void drawHub(Canvas c) {
            owl(c, W * 0.1f, H * 0.14f, H * 0.08f, LearnStore.COLORS[kidSel], 0);
            text(c, "Hai, " + kid.name + "!", W * 0.17f, H * 0.12f, H * 0.05f, 0xFFFFFFFF, Paint.Align.LEFT);
            text(c, "⭐ " + kid.stars + "    🔥 " + kid.days + " hari    🏅 " + kid.badges.size() + " lencana", W * 0.17f, H * 0.18f, H * 0.03f, 0xDDFFFFFF, Paint.Align.LEFT);
            for (int s = 0; s < 4; s++) {
                float x = W * (0.6f + s * 0.095f);
                text(c, SUBJ_ICON[s], x, H * 0.11f, H * 0.03f, SUBJ_COL[s], Paint.Align.CENTER);
                text(c, "Tahap " + store.level(kid, s), x, H * 0.16f, H * 0.024f, 0xCCFFFFFF, Paint.Align.CENTER);
            }
            float cw = W * 0.27f, ch = H * 0.27f, gx = W * 0.03f, x0 = (W - 3 * cw - 2 * gx) / 2;
            for (int i = 0; i < 6; i++) {
                int col = i % 3, row = i / 3;
                r.set(x0 + col * (cw + gx), H * 0.27f + row * (ch + H * 0.04f), x0 + col * (cw + gx) + cw, H * 0.27f + row * (ch + H * 0.04f) + ch);
                boolean on = i == sel;
                card(c, r, MODE_COL[i], on);
                drawModeIcon(c, i, r.centerX(), r.top + ch * 0.38f, ch * 0.28f);
                text(c, MODES[i], r.centerX(), r.top + ch * 0.75f, H * 0.04f, 0xFFFFFFFF, Paint.Align.CENTER);
                text(c, MODE_HINT[i], r.centerX(), r.top + ch * 0.9f, H * 0.024f, 0xDDFFFFFF, Paint.Align.CENTER);
                if (i == REVIEW) text(c, kid.mistakes.size() + " soalan", r.right - W * 0.015f, r.top + H * 0.05f, H * 0.024f, 0xFFFFFFFF, Paint.Align.RIGHT);
            }
            text(c, "OK main   ·   Back tukar pemain", W / 2, H * 0.96f, H * 0.026f, 0x99FFFFFF, Paint.Align.CENTER);
        }

        void drawModeIcon(Canvas c, int i, float cx, float cy, float s) {
            g.setColor(0xFFFFFFFF);
            switch (i) {
                case QUIZ:
                    g.setStyle(Paint.Style.STROKE); g.setStrokeWidth(s * 0.12f);
                    c.drawCircle(cx, cy, s * 0.7f, g);
                    c.drawLine(cx, cy, cx, cy - s * 0.45f, g); c.drawLine(cx, cy, cx + s * 0.35f, cy, g);
                    g.setStyle(Paint.Style.FILL); break;
                case ROCKET: {
                    Path pth = new Path();
                    pth.moveTo(cx, cy - s); pth.lineTo(cx + s * 0.35f, cy + s * 0.3f); pth.lineTo(cx - s * 0.35f, cy + s * 0.3f); pth.close();
                    c.drawPath(pth, g);
                    g.setColor(0xFFFB923C);
                    c.drawCircle(cx, cy + s * 0.55f, s * 0.22f + (float) Math.sin(t * 20) * s * 0.05f, g);
                    break;
                }
                case BALLOON:
                    c.drawOval(cx - s * 0.5f, cy - s * 0.8f, cx + s * 0.5f, cy + s * 0.4f, g);
                    g.setStrokeWidth(3); c.drawLine(cx, cy + s * 0.4f, cx, cy + s, g); break;
                case BOSS:
                    c.drawArc(cx - s * 0.8f, cy - s * 0.6f, cx + s * 0.8f, cy + s * 0.9f, 180, 180, true, g);
                    g.setColor(0xFF1F2937); c.drawCircle(cx - s * 0.3f, cy - s * 0.05f, s * 0.12f, g); c.drawCircle(cx + s * 0.3f, cy - s * 0.05f, s * 0.12f, g);
                    break;
                case REVIEW:
                    g.setStyle(Paint.Style.STROKE); g.setStrokeWidth(s * 0.12f);
                    c.drawArc(cx - s * 0.6f, cy - s * 0.6f, cx + s * 0.6f, cy + s * 0.6f, 30, 300, false, g);
                    g.setStyle(Paint.Style.FILL); break;
                default:
                    r.set(cx - s * 0.7f, cy - s * 0.6f, cx + s * 0.7f, cy + s * 0.7f);
                    c.drawRoundRect(r, s * 0.15f, s * 0.15f, g);
                    text(c, String.valueOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)), cx, cy + s * 0.45f, s * 0.9f, 0xFFFB7185, Paint.Align.CENTER);
            }
        }

        void drawSubjects(Canvas c) {
            text(c, MODES[mode], W / 2, H * 0.14f, H * 0.06f, 0xFFFFFFFF, Paint.Align.CENTER);
            text(c, "Pilih subjek", W / 2, H * 0.21f, H * 0.035f, 0xCCFFFFFF, Paint.Align.CENTER);
            float cw = W * 0.2f, gap = W * 0.025f, x0 = (W - 4 * cw - 3 * gap) / 2;
            for (int s = 0; s < 4; s++) {
                boolean on = s == sel;
                r.set(x0 + s * (cw + gap), H * 0.32f, x0 + s * (cw + gap) + cw, H * 0.72f);
                card(c, r, SUBJ_COL[s], on);
                text(c, SUBJ_ICON[s], r.centerX(), r.top + H * 0.17f, H * 0.09f, 0xFFFFFFFF, Paint.Align.CENTER);
                fit(c, LearnBank.SUBJECTS[s], r.centerX(), r.top + H * 0.27f, cw * 0.9f, H * 0.036f, 0xFFFFFFFF);
                text(c, "Tahap " + store.level(kid, s) + " / 8", r.centerX(), r.top + H * 0.34f, H * 0.028f, 0xDDFFFFFF, Paint.Align.CENTER);
            }
        }

        // ================================================================ playing

        void startRound() {
            screen = PLAY;
            qNo = rights = earned = 0;
            total = 10;
            rocket = 0;
            bossHp = 8;
            hearts = 3;
            if (mode == DAILY) {
                Calendar cal = Calendar.getInstance();
                daily = new LearnBank(cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR) + kidSel * 7919L);
                store.applyPack(daily);
            }
            if (mode == REVIEW) total = Math.min(10, Math.max(1, kid.mistakes.size()));
            nextQ();
        }

        void nextQ() {
            feedback = false;
            pick = 0;
            q = null;
            if (mode == REVIEW && !kid.mistakes.isEmpty()) {
                String key = kid.mistakes.get(rnd.nextInt(kid.mistakes.size()));
                q = bank.fromKey(key, 4);
                if (q == null) kid.mistakes.remove(key);
            }
            if (q == null) {
                int s = mode == DAILY || mode == REVIEW ? qNo % 4 : subject;
                LearnBank b = mode == DAILY ? daily : bank;
                q = b.next(s, store.level(kid, s), kid.recent);
            }
            timeLimit = mode == QUIZ ? 20f : 30f;
            qStart = SystemClock.uptimeMillis();
            balloonY = 1.05f;
        }

        void update(float dt) {
            if (rocketShake > 0) rocketShake -= dt;
            if (feedback) {
                if (SystemClock.uptimeMillis() - feedbackAt > (lastRight ? 1100 : 2600)) {
                    qNo++;
                    boolean done = qNo >= total || (mode == BOSS && (bossHp <= 0 || hearts <= 0));
                    if (done) finish(); else nextQ();
                }
                return;
            }
            if (mode == BALLOON) {
                balloonY -= dt * (0.05f + store.level(kid, q.subject) * 0.004f);
                if (balloonY < 0.15f) answer(-1);
            } else if (mode == QUIZ && (SystemClock.uptimeMillis() - qStart) / 1000f > timeLimit) {
                answer(-1);
            }
        }

        void answer(int i) {
            if (feedback) return;
            feedback = true;
            feedbackAt = SystemClock.uptimeMillis();
            lastRight = i == q.answer;
            pick = i < 0 ? pick : i;
            store.answer(kid, q, lastRight);
            if (lastRight) {
                rights++;
                earned += 1 + (q.level >= 5 ? 1 : 0);
                rocket = Math.min(1f, rocket + 0.1f);
                bossHp--;
                burst(W / 2, H * 0.45f);
                UiFxLite.ok();
            } else {
                rocketShake = 0.5f;
                rocket = Math.max(0, rocket - 0.03f);
                hearts--;
            }
        }

        void finish() {
            screen = END;
            sel = 0;
            boolean bonus = mode == DAILY && rights >= 7;
            if (bonus) { kid.stars += 5; earned += 5; store.save(); }
            endLine = Quotes.next();
            if (rights >= total * 0.7f || (mode == BOSS && bossHp <= 0)) burst(W / 2, H * 0.3f);
        }

        void drawPlay(Canvas c) {
            int col = mode == DAILY || mode == REVIEW ? MODE_COL[mode] : SUBJ_COL[subject];
            // top bar
            text(c, MODES[mode] + "  ·  " + LearnBank.SUBJECTS[q.subject] + "  ·  Tahap " + q.level, W * 0.04f, H * 0.07f, H * 0.03f, 0xDDFFFFFF, Paint.Align.LEFT);
            text(c, "Soalan " + Math.min(total, qNo + 1) + "/" + total + "    ⭐ +" + earned, W * 0.96f, H * 0.07f, H * 0.03f, 0xFFFDE68A, Paint.Align.RIGHT);
            if (mode == QUIZ && !feedback) {
                float f = 1 - Math.min(1, (SystemClock.uptimeMillis() - qStart) / 1000f / timeLimit);
                p.setColor(0x33FFFFFF);
                c.drawRoundRect(W * 0.04f, H * 0.095f, W * 0.96f, H * 0.11f, 8, 8, p);
                p.setColor(f > 0.3f ? 0xFF4ADE80 : 0xFFF87171);
                c.drawRoundRect(W * 0.04f, H * 0.095f, W * 0.04f + W * 0.92f * f, H * 0.11f, 8, 8, p);
            }
            if (mode == ROCKET) drawRocket(c);
            if (mode == BOSS) drawBoss(c);
            // question card
            float qTop = mode == BALLOON ? H * 0.13f : H * 0.15f, qw = mode == ROCKET || mode == BOSS ? W * 0.6f : W * 0.8f;
            float qcx = mode == ROCKET || mode == BOSS ? W * 0.62f : W / 2;
            r.set(qcx - qw / 2, qTop, qcx + qw / 2, qTop + H * 0.2f);
            g.setColor(0x33000000);
            c.drawRoundRect(r, H * 0.03f, H * 0.03f, g);
            wrapped(c, q.prompt, qcx, qTop + H * 0.035f, qw * 0.92f, H * (q.prompt.length() > 70 ? 0.036f : 0.045f), 0xFFFFFFFF);
            if (mode == BALLOON) drawBalloons(c); else drawOptions(c, qcx, qw, col);
            // feedback
            if (feedback) {
                owl(c, W * 0.09f, H * 0.82f, H * 0.08f, LearnStore.COLORS[kidSel], lastRight ? 1 : 2);
                String msg = lastRight ? PRAISE[(qNo + rights) % PRAISE.length] : "Jawapan: " + q.correct() + ".  " + q.explain;
                r.set(W * 0.16f, H * 0.76f, W * 0.96f, H * 0.93f);
                g.setColor(lastRight ? 0xCC166534 : 0xCC7F1D1D);
                c.drawRoundRect(r, H * 0.03f, H * 0.03f, g);
                wrapped(c, msg, r.centerX(), r.top + H * 0.025f, r.width() * 0.94f, H * 0.032f, 0xFFFFFFFF);
            } else {
                owl(c, W * 0.09f, H * 0.82f, H * 0.08f, LearnStore.COLORS[kidSel], 0);
            }
        }

        final String[] PRAISE = {"Hebat! Betul!", "Bijak! 🌟", "Syabas!", "Tepat sekali!", "Teruskan, awak memang pandai!", "Mantap!"};

        void drawOptions(Canvas c, float cx, float width, int col) {
            float bw = width * 0.48f, bh = H * 0.13f, top = H * 0.4f;
            for (int i = 0; i < q.options.length; i++) {
                int cl = i % 2, row = i / 2;
                r.set(cx - width / 2 + cl * (width - bw), top + row * (bh + H * 0.03f), cx - width / 2 + cl * (width - bw) + bw, top + row * (bh + H * 0.03f) + bh);
                boolean on = i == pick && !feedback;
                int fill = col;
                if (feedback) fill = i == q.answer ? 0xFF16A34A : i == pick ? 0xFFDC2626 : 0x26FFFFFF;
                card(c, r, fill, on || (feedback && (i == q.answer || i == pick)));
                text(c, "ABCD".substring(i, i + 1), r.left + H * 0.035f, r.centerY() + H * 0.012f, H * 0.03f, 0x99FFFFFF, Paint.Align.LEFT);
                fit(c, q.options[i], r.centerX() + H * 0.015f, r.centerY(), bw - H * 0.1f, H * 0.042f, 0xFFFFFFFF);
            }
        }

        void drawBalloons(Canvas c) {
            int[] cols = {0xFFF472B6, 0xFF38BDF8, 0xFFFACC15, 0xFF4ADE80};
            float gap = W / (q.options.length + 1);
            for (int i = 0; i < q.options.length; i++) {
                float x = gap * (i + 1), y = H * (balloonY + 0.25f) + (float) Math.sin(t * 2 + i) * H * 0.01f;
                if (feedback && i == pick && lastRight) continue;   // popped
                float rw = W * 0.085f, rh = H * 0.13f;
                boolean on = i == pick;
                g.setColor(feedback && i == q.answer ? 0xFF16A34A : cols[i]);
                g.setShadowLayer(on ? 30 : 10, 0, 0, on ? 0xFFFFFFFF : 0x66000000);
                c.drawOval(x - rw, y - rh, x + rw, y + rh, g);
                g.setShadowLayer(0, 0, 0, 0);
                g.setColor(0x55FFFFFF);
                c.drawOval(x - rw * 0.55f, y - rh * 0.7f, x - rw * 0.15f, y - rh * 0.25f, g);
                p.setColor(0xAAFFFFFF);
                p.setStrokeWidth(2);
                c.drawLine(x, y + rh, x + (float) Math.sin(t * 3 + i) * 10, y + rh + H * 0.12f, p);
                if (on && !feedback) {
                    g.setStyle(Paint.Style.STROKE); g.setStrokeWidth(5); g.setColor(0xFFFFFFFF);
                    c.drawOval(x - rw - 6, y - rh - 6, x + rw + 6, y + rh + 6, g);
                    g.setStyle(Paint.Style.FILL);
                }
                fit(c, q.options[i], x, y, rw * 1.8f, H * 0.036f, 0xFFFFFFFF);
            }
        }

        void drawRocket(Canvas c) {
            float shake = rocketShake > 0 ? (float) Math.sin(t * 60) * H * 0.01f : 0;
            float baseY = H * 0.85f, topY = H * 0.2f, y = baseY - (baseY - topY) * rocket, x = W * 0.16f + shake;
            g.setColor(0xFFF1F5F9);   // the moon
            c.drawCircle(W * 0.16f, H * 0.14f, H * 0.06f, g);
            g.setColor(0xFFCBD5E1);
            c.drawCircle(W * 0.145f, H * 0.13f, H * 0.012f, g);
            c.drawCircle(W * 0.18f, H * 0.16f, H * 0.009f, g);
            p.setColor(0x44FFFFFF);
            c.drawRect(W * 0.16f - 2, topY, W * 0.16f + 2, baseY, p);
            Path body = new Path();
            float s = H * 0.06f;
            body.moveTo(x, y - s * 1.2f); body.lineTo(x + s * 0.45f, y + s * 0.5f); body.lineTo(x - s * 0.45f, y + s * 0.5f); body.close();
            g.setColor(0xFFF8FAFC);
            c.drawPath(body, g);
            g.setColor(0xFF38BDF8);
            c.drawCircle(x, y - s * 0.1f, s * 0.18f, g);
            g.setColor(0xFFFB923C);
            c.drawCircle(x, y + s * 0.75f, s * (0.25f + 0.08f * (float) Math.sin(t * 25)), g);
            text(c, Math.round(rocket * 100) + "%", x + s, y, H * 0.03f, 0xFFFFFFFF, Paint.Align.LEFT);
        }

        void drawBoss(Canvas c) {
            float cx = W * 0.16f, cy = H * 0.5f, s = H * 0.13f;
            float squish = 1 + 0.06f * (float) Math.sin(t * 4);
            if (feedback && lastRight) cx += (float) Math.sin(t * 50) * H * 0.012f;
            g.setColor(0xFF84CC16);
            g.setShadowLayer(20, 0, 0, 0xFF65A30D);
            c.drawArc(cx - s * squish, cy - s / squish, cx + s * squish, cy + s * 1.2f, 180, 180, true, g);
            c.drawRect(cx - s * squish, cy + s * 0.08f, cx + s * squish, cy + s * 0.4f, g);
            g.setShadowLayer(0, 0, 0, 0);
            g.setColor(0xFFFFFFFF);
            c.drawCircle(cx - s * 0.35f, cy - s * 0.15f, s * 0.2f, g);
            c.drawCircle(cx + s * 0.35f, cy - s * 0.15f, s * 0.2f, g);
            g.setColor(0xFF1F2937);
            c.drawCircle(cx - s * 0.3f, cy - s * 0.1f, s * 0.09f, g);
            c.drawCircle(cx + s * 0.4f, cy - s * 0.1f, s * 0.09f, g);
            text(c, "Si Lembap", cx, cy + s * 0.75f, H * 0.03f, 0xFFD9F99D, Paint.Align.CENTER);
            p.setColor(0x44FFFFFF);
            c.drawRoundRect(cx - s, cy - s * 1.45f, cx + s, cy - s * 1.3f, 6, 6, p);
            p.setColor(0xFFEF4444);
            c.drawRoundRect(cx - s, cy - s * 1.45f, cx - s + 2 * s * Math.max(0, bossHp) / 8f, cy - s * 1.3f, 6, 6, p);
            StringBuilder hs = new StringBuilder();
            for (int i = 0; i < 3; i++) hs.append(i < hearts ? "❤ " : "♡ ");
            text(c, hs.toString(), cx, cy - s * 1.6f, H * 0.04f, 0xFFF87171, Paint.Align.CENTER);
        }

        void drawEnd(Canvas c) {
            boolean won = mode == BOSS ? bossHp <= 0 : rights >= total * 0.7f;
            owl(c, W / 2, H * 0.22f, H * 0.12f, LearnStore.COLORS[kidSel], won ? 1 : 0);
            String title = mode == BOSS ? (bossHp <= 0 ? "Si Lembap kalah! 🎉" : "Cuba lagi!")
                    : mode == ROCKET ? (rocket >= 0.99f ? "Sampai ke Bulan! 🚀" : "Hampir sampai!")
                    : won ? "Hebat!" : "Bagus, teruskan berlatih!";
            text(c, title, W / 2, H * 0.43f, H * 0.065f, 0xFFFFFFFF, Paint.Align.CENTER);
            text(c, rights + " / " + Math.max(total, qNo) + " betul    ⭐ +" + earned, W / 2, H * 0.52f, H * 0.045f, 0xFFFDE68A, Paint.Align.CENTER);
            if (mode == DAILY) text(c, rights >= 7 ? "Bonus cabaran harian: +5 ⭐" : "Dapat 7 betul untuk bonus +5 ⭐", W / 2, H * 0.58f, H * 0.03f, 0xFFFDE68A, Paint.Align.CENTER);
            wrapped(c, "✦  " + endLine, W / 2, H * 0.63f, W * 0.7f, H * 0.035f, 0xFF67E8F9);
            text(c, "OK main lagi   ·   Back ke menu", W / 2, H * 0.9f, H * 0.03f, 0x99FFFFFF, Paint.Align.CENTER);
        }

        // ================================================================ keys

        @Override public boolean onKeyDown(int k, KeyEvent e) {
            boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A;
            boolean back = k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_BUTTON_B;
            int dx = k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
            int dy = k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : k == KeyEvent.KEYCODE_DPAD_UP ? -1 : 0;
            if (dx != 0 || dy != 0) UiFxLite.move();
            switch (screen) {
                case KIDS:
                    if (back) return false;
                    if (dx != 0) kidSel = (kidSel + dx + LearnStore.KIDS) % LearnStore.KIDS;
                    if (k == KeyEvent.KEYCODE_MENU) editKid();
                    if (ok) { kid = store.kids[kidSel]; screen = HUB; sel = 0; }
                    return true;
                case HUB:
                    if (back) { screen = KIDS; return true; }
                    if (dx != 0) sel = (sel / 3) * 3 + ((sel % 3) + dx + 3) % 3;
                    if (dy != 0) sel = (sel + 3) % 6;
                    if (ok) {
                        mode = sel;
                        if (mode == DAILY || mode == REVIEW) {
                            if (mode == REVIEW && kid.mistakes.isEmpty()) { endLine = "Tiada soalan untuk diulang kaji. Hebat!"; mode = REVIEW; screen = END; rights = 0; total = 0; earned = 0; return true; }
                            startRound();
                        } else { screen = SUBJECT; sel = 0; }
                    }
                    return true;
                case SUBJECT:
                    if (back) { screen = HUB; sel = mode; return true; }
                    if (dx != 0) sel = (sel + dx + 4) % 4;
                    if (ok) { subject = sel; startRound(); }
                    return true;
                case PLAY:
                    if (back) { screen = HUB; sel = mode; return true; }
                    if (feedback) return true;
                    int n = q.options.length;
                    if (mode == BALLOON) {
                        if (dx != 0) pick = (pick + dx + n) % n;
                    } else {
                        if (dx != 0) pick = (pick % 2 == 0 ? pick + 1 : pick - 1);
                        if (dy != 0) pick = (pick + 2) % 4;
                        if (pick >= n) pick = n - 1;
                    }
                    if (ok) answer(pick);
                    return true;
                default:
                    if (back) { screen = HUB; sel = mode; return true; }
                    if (ok) { if (mode == REVIEW && kid.mistakes.isEmpty()) { screen = HUB; sel = mode; } else if (mode == DAILY || mode == REVIEW) startRound(); else startRound(); }
                    return true;
            }
        }

        void editKid() {
            final LearnStore.Kid k = store.kids[kidSel];
            final EditText name = new EditText(getContext());
            name.setSingleLine(true);
            name.setText(k.name);
            name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            LinearLayout wrap = new LinearLayout(getContext());
            int pad = Math.round(20 * getResources().getDisplayMetrics().density);
            wrap.setPadding(pad, pad / 2, pad, 0);
            wrap.addView(name, new LinearLayout.LayoutParams(-1, -2));
            new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Nama pemain")
                    .setView(wrap)
                    .setPositiveButton("Seterusnya", (d, w) -> {
                        String nm = name.getText().toString().trim();
                        if (!nm.isEmpty()) k.name = nm.length() > 14 ? nm.substring(0, 14) : nm;
                        String[] ages = new String[8];
                        for (int i = 0; i < 8; i++) ages[i] = (7 + i) + " tahun";
                        new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle("Umur " + k.name)
                                .setSingleChoiceItems(ages, Math.max(0, Math.min(7, k.age - 7)), (d2, w2) -> {
                                    k.age = 7 + w2;
                                    for (int s = 0; s < 4; s++) k.level[s] = 0;   // start again from the new age's level
                                    store.save();
                                    d2.dismiss();
                                }).show();
                    })
                    .setNegativeButton("Batal", null)
                    .show();
        }
    }
}
