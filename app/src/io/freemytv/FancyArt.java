package io.freemytv;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Random;

/**
 * Fork extra: the look of the fork's own screens. Premium cards for All Apps (drawn once into a bitmap) and an
 * animated background (aurora blobs, rising bokeh and floating glyphs) for the menus of Kelas Bijak and Papan
 * Permainan. The background allocates nothing per frame: shaders are made once and moved with a Matrix.
 */
final class FancyArt {
    private FancyArt() {}

    // ================================================================ tiles for All Apps

    /** A drawn card for one of the fork's entries, or null for any other key. */
    static Bitmap tile(String key, int w, int h) {
        int kind;
        switch (key) {
            case "freemytv-learn": kind = 0; break;
            case "freemytv-board": kind = 1; break;
            case "freemytv-retro": kind = 2; break;
            case "freemytv-extras": kind = 3; break;
            default: return null;
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[][] grads = {
                {0xFF0F766E, 0xFF1E1B4B},   // learn: teal to indigo
                {0xFFEA580C, 0xFF831843},   // board: orange to wine
                {0xFF2E1065, 0xFF020617},   // retro: night purple
                {0xFF1D4ED8, 0xFF701A75},   // extras: blue to magenta
        };
        p.setShader(new LinearGradient(0, 0, w * 0.4f, h, grads[kind][0], grads[kind][1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        int[] glow = {0x7034D399, 0x80FDE047, 0x90F472B6, 0x8022D3EE};
        p.setShader(new RadialGradient(w * 0.5f, h * 0.5f, w * 0.45f, glow[kind], 0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Random r = new Random(kind * 31 + 7);
        for (int i = 0; i < 26; i++) {   // little stars
            p.setColor(Color.argb(40 + r.nextInt(140), 255, 255, 255));
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h, 0.6f + r.nextFloat() * 1.8f, p);
        }
        float s = h * 0.36f, cx = w / 2f, cy = h * 0.52f;
        switch (kind) {
            case 0: owlTile(c, p, cx, cy, s, w, h); break;
            case 1: boardTile(c, p, cx, cy, s, w, h); break;
            case 2: retroTile(c, p, cx, cy, s, w, h); break;
            default: extrasTile(c, p, cx, cy, s, w, h);
        }
        // glass gloss on the top half
        p.setShader(new LinearGradient(0, 0, 0, h * 0.55f, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.55f, p);
        p.setShader(null);
        b.setHasMipMap(true);
        return b;
    }

    private static void softShadow(Canvas c, Paint p, float cx, float cy, float rx, float ry) {
        p.setShader(new RadialGradient(cx, cy, rx, 0x66000000, 0, Shader.TileMode.CLAMP));
        c.save();
        c.scale(1, ry / rx, cx, cy);
        c.drawCircle(cx, cy, rx, p);
        c.restore();
        p.setShader(null);
    }

    /** Kelas Bijak: the owl Bijak in a graduation cap, with ABC and 123 around. */
    private static void owlTile(Canvas c, Paint p, float cx, float cy, float s, int w, int h) {
        softShadow(c, p, cx, cy + s * 1.0f, s * 0.9f, s * 0.22f);
        p.setShader(new LinearGradient(0, cy - s, 0, cy + s, 0xFF8B5CF6, 0xFF4C1D95, Shader.TileMode.CLAMP));
        c.drawOval(cx - s * 0.75f, cy - s * 0.75f, cx + s * 0.75f, cy + s * 0.95f, p);
        p.setShader(null);
        p.setColor(0xFFEDE9FE);
        c.drawOval(cx - s * 0.42f, cy + s * 0.05f, cx + s * 0.42f, cy + s * 0.85f, p);
        for (int k = -1; k <= 1; k += 2) {
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx + k * s * 0.3f, cy - s * 0.22f, s * 0.27f, p);
            p.setColor(0xFF1E1B4B);
            c.drawCircle(cx + k * s * 0.3f + s * 0.04f, cy - s * 0.2f, s * 0.13f, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx + k * s * 0.3f + s * 0.08f, cy - s * 0.25f, s * 0.04f, p);
        }
        Path beak = new Path();
        beak.moveTo(cx - s * 0.09f, cy - s * 0.02f); beak.lineTo(cx + s * 0.09f, cy - s * 0.02f); beak.lineTo(cx, cy + s * 0.15f); beak.close();
        p.setColor(0xFFF59E0B);
        c.drawPath(beak, p);
        // cap
        Path cap = new Path();
        cap.moveTo(cx - s * 0.8f, cy - s * 0.78f); cap.lineTo(cx, cy - s * 1.05f); cap.lineTo(cx + s * 0.8f, cy - s * 0.78f); cap.lineTo(cx, cy - s * 0.52f); cap.close();
        p.setColor(0xFF111827);
        c.drawPath(cap, p);
        p.setColor(0xFFFACC15);
        p.setStrokeWidth(s * 0.04f);
        c.drawLine(cx + s * 0.5f, cy - s * 0.68f, cx + s * 0.62f, cy - s * 0.35f, p);
        c.drawCircle(cx + s * 0.62f, cy - s * 0.33f, s * 0.06f, p);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(s * 0.42f);
        p.setColor(0xFF6EE7B7);
        c.drawText("ABC", w * 0.17f, h * 0.36f, p);
        p.setColor(0xFFFDE68A);
        c.drawText("123", w * 0.83f, h * 0.36f, p);
        p.setColor(0xFFF9A8D4);
        c.drawText("+", w * 0.2f, h * 0.82f, p);
        p.setColor(0xFF93C5FD);
        c.drawText("×", w * 0.8f, h * 0.82f, p);
    }

    /** Papan Permainan: a tilted board with two dice in front. */
    private static void boardTile(Canvas c, Paint p, float cx, float cy, float s, int w, int h) {
        c.save();
        c.rotate(-12, cx - s * 0.6f, cy);
        float bs = s * 1.5f, bx = cx - s * 1.45f, by = cy - bs / 2;
        softShadow(c, p, bx + bs / 2, by + bs + s * 0.1f, bs * 0.6f, s * 0.15f);
        int[] cols = {0xFFFDE68A, 0xFF22C55E, 0xFF3B82F6, 0xFFEF4444};
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
            p.setColor((i + j) % 2 == 0 ? 0xFFFFF7ED : cols[(i * 4 + j) % 4]);
            c.drawRect(bx + i * bs / 4, by + j * bs / 4, bx + (i + 1) * bs / 4 - 1, by + (j + 1) * bs / 4 - 1, p);
        }
        p.setColor(0xFF7C2D12);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.05f);
        c.drawRect(bx, by, bx + bs, by + bs, p);
        p.setStyle(Paint.Style.FILL);
        c.restore();
        die(c, p, cx + s * 0.55f, cy - s * 0.15f, s * 0.75f, 15, 5);
        die(c, p, cx + s * 1.25f, cy + s * 0.45f, s * 0.6f, -18, 3);
        // pawns
        pawn(c, p, cx - s * 0.2f, cy + s * 0.75f, s * 0.32f, 0xFF22D3EE);
        pawn(c, p, cx - s * 1.6f, cy + s * 0.85f, s * 0.28f, 0xFFF472B6);
    }

    private static void die(Canvas c, Paint p, float cx, float cy, float s, float rot, int pips) {
        c.save();
        c.rotate(rot, cx, cy);
        softShadow(c, p, cx, cy + s * 0.62f, s * 0.6f, s * 0.12f);
        RectF r = new RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
        p.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0xFFFFFFFF, 0xFFD6D3D1, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, s * 0.2f, s * 0.2f, p);
        p.setShader(null);
        p.setColor(0xFFDC2626);
        float d = s * 0.25f, pr = s * 0.09f;
        if (pips % 2 == 1) c.drawCircle(cx, cy, pr, p);
        if (pips >= 2) { c.drawCircle(cx - d, cy - d, pr, p); c.drawCircle(cx + d, cy + d, pr, p); }
        if (pips >= 4) { c.drawCircle(cx + d, cy - d, pr, p); c.drawCircle(cx - d, cy + d, pr, p); }
        c.restore();
    }

    private static void pawn(Canvas c, Paint p, float cx, float bottom, float s, int col) {
        p.setShader(new LinearGradient(cx - s, 0, cx + s, 0, col, darker(col), Shader.TileMode.CLAMP));
        c.drawOval(cx - s * 0.6f, bottom - s * 0.35f, cx + s * 0.6f, bottom, p);
        Path body = new Path();
        body.moveTo(cx - s * 0.45f, bottom - s * 0.2f); body.lineTo(cx - s * 0.15f, bottom - s * 1.0f);
        body.lineTo(cx + s * 0.15f, bottom - s * 1.0f); body.lineTo(cx + s * 0.45f, bottom - s * 0.2f); body.close();
        c.drawPath(body, p);
        c.drawCircle(cx, bottom - s * 1.15f, s * 0.32f, p);
        p.setShader(null);
    }

    /** Retro Arked: a synthwave sun over a neon grid, with a joystick. */
    private static void retroTile(Canvas c, Paint p, float cx, float cy, float s, int w, int h) {
        float hz = h * 0.62f;
        p.setShader(new LinearGradient(0, hz - s * 1.2f, 0, hz, 0xFFFDE047, 0xFFEC4899, Shader.TileMode.CLAMP));
        c.save();
        c.clipRect(0, 0, w, hz);
        c.drawCircle(cx, hz, s * 1.1f, p);
        c.restore();
        p.setShader(null);
        p.setColor(0xFF2E1065);
        for (int i = 0; i < 4; i++) {
            float y = hz - s * (0.15f + i * 0.2f);
            c.drawRect(cx - s * 1.2f, y, cx + s * 1.2f, y + s * (0.03f + i * 0.015f), p);
        }
        p.setColor(0xFF0B0420);
        c.drawRect(0, hz, w, h, p);
        p.setColor(0xFFE879F9);
        p.setStrokeWidth(2);
        for (int i = 0; i < 6; i++) {
            float y = hz + (h - hz) * (i * i) / 25f;
            c.drawLine(0, y, w, y, p);
        }
        for (int i = -8; i <= 8; i++) c.drawLine(cx + i * w * 0.02f, hz, cx + i * w * 0.14f, h, p);
        // joystick
        float jx = w * 0.78f, jy = h * 0.82f;
        p.setColor(0xFF111827);
        c.drawRoundRect(jx - s * 0.5f, jy - s * 0.12f, jx + s * 0.5f, jy + s * 0.18f, s * 0.08f, s * 0.08f, p);
        p.setColor(0xFFCBD5E1);
        p.setStrokeWidth(s * 0.1f);
        c.drawLine(jx, jy - s * 0.05f, jx - s * 0.12f, jy - s * 0.6f, p);
        p.setColor(0xFFEF4444);
        c.drawCircle(jx - s * 0.12f, jy - s * 0.62f, s * 0.2f, p);
        p.setColor(0xFF22D3EE);
        c.drawCircle(jx + s * 0.32f, jy - s * 0.12f, s * 0.07f, p);
        // a little invader on the left
        p.setColor(0xFF4ADE80);
        float u = s * 0.09f, ix = w * 0.12f, iy = h * 0.2f;
        String[] inv = {"00100000100", "00010001000", "00111111100", "01101110110", "11111111111", "10111111101", "10100000101", "00011011000"};
        for (int y = 0; y < inv.length; y++) for (int x = 0; x < 11; x++)
            if (inv[y].charAt(x) == '1') c.drawRect(ix + x * u, iy + y * u, ix + (x + 1) * u, iy + (y + 1) * u, p);
    }

    /** Video & LED: a glass play button inside an RGB ring of lights, with music notes. */
    private static void extrasTile(Canvas c, Paint p, float cx, float cy, float s, int w, int h) {
        int[] rgb = {0xFFFF3B3B, 0xFFFFB13B, 0xFFFFF03B, 0xFF3BFF6E, 0xFF3BD8FF, 0xFF6E5BFF, 0xFFFF3BE8, 0xFFFF3B3B};
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.12f);
        p.setShader(new android.graphics.SweepGradient(cx, cy, rgb, null));
        p.setMaskFilter(new BlurMaskFilter(s * 0.12f, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(cx, cy, s * 0.95f, p);
        p.setMaskFilter(null);
        p.setStrokeWidth(s * 0.06f);
        c.drawCircle(cx, cy, s * 0.95f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x40FFFFFF);
        c.drawCircle(cx, cy, s * 0.78f, p);
        Path tri = new Path();
        tri.moveTo(cx - s * 0.25f, cy - s * 0.4f); tri.lineTo(cx + s * 0.45f, cy); tri.lineTo(cx - s * 0.25f, cy + s * 0.4f); tri.close();
        p.setColor(0xFFFFFFFF);
        c.drawPath(tri, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(s * 0.7f);
        p.setColor(0xFFFDE68A);
        c.drawText("♪", w * 0.18f, h * 0.42f, p);
        p.setColor(0xFFA5F3FC);
        c.drawText("♫", w * 0.83f, h * 0.78f, p);
        // LED strip dots along the bottom
        for (int i = 0; i < 18; i++) {
            p.setColor(rgb[i % 7]);
            c.drawCircle(w * (0.05f + i * 0.053f), h * 0.94f, h * 0.012f, p);
        }
    }

    private static int darker(int c) {
        return Color.rgb(Color.red(c) * 6 / 10, Color.green(c) * 6 / 10, Color.blue(c) * 6 / 10);
    }

    // ================================================================ game icons for the board menu

    /** A square icon (size × size) for game 0-4 of Papan Permainan: house, snake & ladder, congkak, trophy, letters. */
    static Bitmap gameIcon(int game, int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float s = size * 0.4f, cx = size / 2f, cy = size / 2f;
        switch (game) {
            case 0: {   // a house with a gold coin
                softShadow(c, p, cx, cy + s * 0.85f, s * 0.8f, s * 0.12f);
                p.setShader(new LinearGradient(0, cy - s * 0.2f, 0, cy + s * 0.8f, 0xFFFFFFFF, 0xFFE2E8F0, Shader.TileMode.CLAMP));
                c.drawRect(cx - s * 0.6f, cy - s * 0.15f, cx + s * 0.6f, cy + s * 0.8f, p);
                Path roof = new Path();
                roof.moveTo(cx - s * 0.82f, cy - s * 0.1f); roof.lineTo(cx, cy - s * 0.85f); roof.lineTo(cx + s * 0.82f, cy - s * 0.1f); roof.close();
                p.setShader(new LinearGradient(0, cy - s * 0.85f, 0, cy, 0xFFF87171, 0xFFB91C1C, Shader.TileMode.CLAMP));
                c.drawPath(roof, p);
                p.setShader(null);
                p.setColor(0xFF92400E);
                c.drawRoundRect(cx - s * 0.16f, cy + s * 0.3f, cx + s * 0.16f, cy + s * 0.8f, s * 0.05f, s * 0.05f, p);
                p.setColor(0xFF38BDF8);
                c.drawRect(cx - s * 0.48f, cy + s * 0.05f, cx - s * 0.26f, cy + s * 0.27f, p);
                c.drawRect(cx + s * 0.26f, cy + s * 0.05f, cx + s * 0.48f, cy + s * 0.27f, p);
                coin(c, p, cx + s * 0.75f, cy + s * 0.55f, s * 0.3f);
                break;
            }
            case 1: {   // ladder with a snake around it
                p.setColor(0xFFFBBF24);
                p.setStrokeWidth(s * 0.13f);
                p.setStrokeCap(Paint.Cap.ROUND);
                c.drawLine(cx - s * 0.45f, cy - s * 0.85f, cx - s * 0.45f, cy + s * 0.85f, p);
                c.drawLine(cx + s * 0.45f, cy - s * 0.85f, cx + s * 0.45f, cy + s * 0.85f, p);
                for (int k = 0; k < 5; k++) c.drawLine(cx - s * 0.45f, cy - s * 0.65f + k * s * 0.33f, cx + s * 0.45f, cy - s * 0.65f + k * s * 0.33f, p);
                Path snake = new Path();
                snake.moveTo(cx - s * 0.9f, cy + s * 0.8f);
                snake.cubicTo(cx + s * 1.2f, cy + s * 0.6f, cx - s * 1.2f, cy - s * 0.2f, cx + s * 0.7f, cy - s * 0.6f);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.22f);
                p.setShader(new LinearGradient(cx - s, 0, cx + s, 0, 0xFF22C55E, 0xFF15803D, Shader.TileMode.CLAMP));
                c.drawPath(snake, p);
                p.setShader(null);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF16A34A);
                c.drawOval(cx + s * 0.55f, cy - s * 0.8f, cx + s * 0.95f, cy - s * 0.48f, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(cx + s * 0.8f, cy - s * 0.7f, s * 0.06f, p);
                p.setColor(0xFFEF4444);
                p.setStrokeWidth(s * 0.04f);
                c.drawLine(cx + s * 0.95f, cy - s * 0.62f, cx + s * 1.1f, cy - s * 0.58f, p);
                break;
            }
            case 2: {   // the wooden congkak boat with holes and seeds
                softShadow(c, p, cx, cy + s * 0.55f, s * 1.0f, s * 0.12f);
                p.setShader(new LinearGradient(0, cy - s * 0.5f, 0, cy + s * 0.5f, 0xFFD97706, 0xFF78350F, Shader.TileMode.CLAMP));
                c.drawRoundRect(cx - s * 1.05f, cy - s * 0.45f, cx + s * 1.05f, cy + s * 0.45f, s * 0.45f, s * 0.45f, p);
                p.setShader(null);
                Random r = new Random(3);
                for (int k = 0; k < 4; k++) {
                    float hx = cx - s * 0.5f + k * s * 0.33f;
                    for (int row = -1; row <= 1; row += 2) {
                        p.setColor(0xFF451A03);
                        c.drawCircle(hx, cy + row * s * 0.2f, s * 0.12f, p);
                        p.setColor(0xFFFEF3C7);
                        for (int q = 0; q < 2; q++) c.drawCircle(hx + (r.nextFloat() - 0.5f) * s * 0.1f, cy + row * s * 0.2f + (r.nextFloat() - 0.5f) * s * 0.1f, s * 0.035f, p);
                    }
                }
                for (int k = -1; k <= 1; k += 2) {
                    p.setColor(0xFF451A03);
                    c.drawOval(cx + k * s * 0.85f - s * 0.13f, cy - s * 0.3f, cx + k * s * 0.85f + s * 0.13f, cy + s * 0.3f, p);
                }
                break;
            }
            case 3: {   // a gold trophy with a star
                softShadow(c, p, cx, cy + s * 0.92f, s * 0.6f, s * 0.1f);
                p.setShader(new LinearGradient(cx - s, 0, cx + s, 0, new int[]{0xFFB45309, 0xFFFDE047, 0xFFCA8A04}, null, Shader.TileMode.CLAMP));
                Path cup = new Path();
                cup.moveTo(cx - s * 0.6f, cy - s * 0.8f); cup.lineTo(cx + s * 0.6f, cy - s * 0.8f);
                cup.quadTo(cx + s * 0.6f, cy + s * 0.2f, cx, cy + s * 0.3f);
                cup.quadTo(cx - s * 0.6f, cy + s * 0.2f, cx - s * 0.6f, cy - s * 0.8f);
                c.drawPath(cup, p);
                c.drawRect(cx - s * 0.1f, cy + s * 0.25f, cx + s * 0.1f, cy + s * 0.6f, p);
                c.drawRoundRect(cx - s * 0.45f, cy + s * 0.6f, cx + s * 0.45f, cy + s * 0.85f, s * 0.06f, s * 0.06f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.1f);
                c.drawArc(cx - s * 0.95f, cy - s * 0.7f, cx - s * 0.35f, cy - s * 0.1f, 90, 180, false, p);
                c.drawArc(cx + s * 0.35f, cy - s * 0.7f, cx + s * 0.95f, cy - s * 0.1f, 270, 180, false, p);
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                star(c, p, cx, cy - s * 0.35f, s * 0.22f, 0xFFFFFFFF);
                break;
            }
            case 5: {   // Sang Kancil on a grassy hill with a rambutan
                p.setColor(0xFF16A34A);
                c.drawOval(cx - s * 1.2f, cy + s * 0.55f, cx + s * 1.2f, cy + s * 1.3f, p);
                Kancil.drawKancil(c, p, new Path(), cx - s * 0.1f, cy + s * 0.75f, s * 1.5f, true, 0.4f, 0xFF22D3EE, 0, true);
                p.setColor(0xFFDC2626);
                c.drawCircle(cx + s * 0.85f, cy - s * 0.55f, s * 0.2f, p);
                p.setColor(0xFF15803D);
                p.setStrokeWidth(s * 0.04f);
                for (int k = 0; k < 12; k++) {
                    double an = k * Math.PI / 6;
                    c.drawLine(cx + s * 0.85f + (float) Math.cos(an) * s * 0.18f, cy - s * 0.55f + (float) Math.sin(an) * s * 0.18f,
                            cx + s * 0.85f + (float) Math.cos(an) * s * 0.3f, cy - s * 0.55f + (float) Math.sin(an) * s * 0.3f, p);
                }
                break;
            }
            default: {   // letter tiles in quiz colours
                String w = "KATA";
                int[] col = {0xFF22C55E, 0xFFEAB308, 0xFF64748B, 0xFF22C55E};
                float ts = s * 0.46f;
                p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(ts * 0.7f);
                for (int k = 0; k < 4; k++) {
                    float x = cx - ts * 2.1f + k * ts * 1.07f, y = cy - ts / 2 + (k % 2 == 0 ? -ts * 0.15f : ts * 0.15f);
                    p.setColor(0x55000000);
                    c.drawRoundRect(x + 3, y + 5, x + ts + 3, y + ts + 5, ts * 0.18f, ts * 0.18f, p);
                    p.setShader(new LinearGradient(0, y, 0, y + ts, col[k], darker(col[k]), Shader.TileMode.CLAMP));
                    c.drawRoundRect(x, y, x + ts, y + ts, ts * 0.18f, ts * 0.18f, p);
                    p.setShader(null);
                    p.setColor(0xFFFFFFFF);
                    c.drawText(String.valueOf(w.charAt(k)), x + ts / 2, y + ts * 0.74f, p);
                }
            }
        }
        return b;
    }

    private static void coin(Canvas c, Paint p, float cx, float cy, float r) {
        p.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.3f, 0xFFFEF08A, 0xFFCA8A04, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
        p.setColor(0xFF92400E);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(r * 0.9f);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        c.drawText("RM", cx, cy + r * 0.32f, p);
    }

    private static void star(Canvas c, Paint p, float cx, float cy, float r, int col) {
        Path st = new Path();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? r : r * 0.45f;
            float x = cx + (float) (Math.cos(a) * rr), y = cy - (float) (Math.sin(a) * rr);
            if (i == 0) st.moveTo(x, y); else st.lineTo(x, y);
        }
        st.close();
        p.setColor(col);
        c.drawPath(st, p);
    }

    // ================================================================ the animated background

    /** An animated, allocation-free background: deep gradient, three drifting colour blobs, bokeh and glyphs. */
    static final class Aurora {
        private final int top, bottom;
        private final int[] blobCols;
        private final String[] glyphs;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), gp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();
        private Shader base;
        private final Shader[] blobs = new Shader[3];
        private float w, h;
        private final float[] bx = new float[40], by = new float[40], bs = new float[40], bv = new float[40];
        private final int[] bc = new int[40];
        private final float[] gx = new float[12], gy = new float[12], gv = new float[12], gr = new float[12];
        private final int[] gi = new int[12];

        Aurora(int top, int bottom, int[] blobCols, String[] glyphs) {
            this.top = top;
            this.bottom = bottom;
            this.blobCols = blobCols;
            this.glyphs = glyphs;
            gp.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            gp.setTextAlign(Paint.Align.CENTER);
            Random r = new Random(5);
            for (int i = 0; i < bx.length; i++) {
                bx[i] = r.nextFloat(); by[i] = r.nextFloat(); bs[i] = 0.002f + r.nextFloat() * 0.008f; bv[i] = 0.01f + r.nextFloat() * 0.03f;
                bc[i] = blobCols[i % blobCols.length];
            }
            for (int i = 0; i < gx.length; i++) {
                gx[i] = r.nextFloat(); gy[i] = r.nextFloat(); gv[i] = 0.008f + r.nextFloat() * 0.015f; gr[i] = r.nextFloat() * 360;
                gi[i] = glyphs.length == 0 ? 0 : i % glyphs.length;
            }
        }

        void size(float W, float H) {
            if (W == w && H == h) return;
            w = W;
            h = H;
            base = new LinearGradient(0, 0, W * 0.3f, H, top, bottom, Shader.TileMode.CLAMP);
            // unit-radius blobs, placed and sized each frame with the matrix
            for (int i = 0; i < 3; i++) blobs[i] = new RadialGradient(0, 0, 1, Ui.alpha(blobCols[i % blobCols.length], 0.45f), 0, Shader.TileMode.CLAMP);
        }

        void draw(Canvas c, float W, float H, float t) {
            size(W, H);
            p.setShader(base);
            c.drawRect(0, 0, W, H, p);
            for (int i = 0; i < 3; i++) {
                float cx = W * (0.5f + 0.38f * (float) Math.sin(t * (0.07f + i * 0.03f) + i * 2.1f));
                float cy = H * (0.5f + 0.35f * (float) Math.cos(t * (0.05f + i * 0.04f) + i * 1.3f));
                float rad = W * (0.32f + 0.06f * (float) Math.sin(t * 0.2f + i));
                m.setScale(rad, rad);
                m.postTranslate(cx, cy);
                blobs[i].setLocalMatrix(m);
                p.setShader(blobs[i]);
                c.drawCircle(cx, cy, rad, p);
            }
            p.setShader(null);
            // bokeh rising slowly
            for (int i = 0; i < bx.length; i++) {
                float y = (by[i] - t * bv[i]) % 1f;
                if (y < 0) y += 1;
                float x = bx[i] + 0.01f * (float) Math.sin(t * 0.6f + i);
                int a = (int) (50 + 50 * Math.sin(t * 1.5f + i * 0.7f));
                p.setColor((bc[i] & 0x00FFFFFF) | (a << 24));
                c.drawCircle(x * W, y * H, bs[i] * H * 2, p);
            }
            // floating glyphs, faint
            if (glyphs.length > 0) {
                gp.setTextSize(H * 0.07f);
                for (int i = 0; i < gx.length; i++) {
                    float y = (gy[i] - t * gv[i]) % 1.15f;
                    if (y < -0.1f) y += 1.15f;
                    c.save();
                    c.rotate(gr[i] + t * (i % 2 == 0 ? 8 : -8), gx[i] * W, y * H);
                    gp.setColor(0x1CFFFFFF);
                    c.drawText(glyphs[gi[i]], gx[i] * W, y * H, gp);
                    c.restore();
                }
            }
        }
    }

    // ================================================================ premium cards

    /** Draws cards with a soft gradient, a gloss, a lift when focused and a light sweeping across it. */
    static final class Cards {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();
        private final Shader gloss = new LinearGradient(0, 0, 0, 1, 0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        private final Shader shade = new LinearGradient(0, 0, 0, 1, 0x00000000, 0x55000000, Shader.TileMode.CLAMP);
        private final Shader shine = new LinearGradient(0, 0, 1, 0, new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        private final Path clip = new Path();

        void draw(Canvas c, RectF r, int color, boolean on, float t, float radius) {
            // shadow and glow
            p.setColor(on ? color : 0x40000000);
            p.setShadowLayer(on ? radius * 1.1f : radius * 0.3f, 0, radius * 0.2f, on ? Ui.alpha(color, 0.9f) : 0x66000000);
            c.drawRoundRect(r, radius, radius, p);
            p.clearShadowLayer();
            if (!on) {   // frosted glass when not focused
                p.setColor(0x26FFFFFF);
                c.drawRoundRect(r, radius, radius, p);
                p.setColor(Ui.alpha(color, 0.35f));
                c.drawRoundRect(r.left, r.bottom - radius * 0.35f, r.right, r.bottom, radius * 0.3f, radius * 0.3f, p);
            }
            m.setScale(1, r.height());
            m.postTranslate(0, r.top);
            shade.setLocalMatrix(m);
            p.setShader(shade);
            c.drawRoundRect(r, radius, radius, p);
            m.setScale(1, r.height() * 0.5f);
            m.postTranslate(0, r.top);
            gloss.setLocalMatrix(m);
            p.setShader(gloss);
            c.drawRoundRect(r.left, r.top, r.right, r.top + r.height() * 0.5f, radius, radius, p);
            p.setShader(null);
            if (on) {
                // a light that sweeps across every 2.5 s
                float ph = (t % 2.5f) / 2.5f, sw = r.width() * 0.5f;
                float x = r.left - sw + ph * (r.width() + sw * 2);
                clip.reset();
                clip.addRoundRect(r, radius, radius, Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                m.setScale(sw, 1);
                m.postTranslate(x - sw / 2, 0);
                shine.setLocalMatrix(m);
                p.setShader(shine);
                c.drawRect(r, p);
                p.setShader(null);
                c.restore();
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2, radius * 0.12f));
                p.setColor(Color.argb((int) (180 + 75 * Math.sin(t * 4)), 255, 255, 255));
                c.drawRoundRect(r, radius, radius, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }
}
