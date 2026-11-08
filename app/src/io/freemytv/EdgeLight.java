package io.freemytv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.view.View;

/** Fork extra: an animated RGB "LED strip" glow running around the screen's edges (Extras). */
final class EdgeLight extends View {
    public static final int[] COLORS = {0, 0xFF3D8BFF, 0xFFA05BFF, 0xFFFF4FA3, 0xFFFF3B30, 0xFFFF9F0A, 0xFF30D158, 0xFFFFFFFF};

    private final Paint wide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Matrix m = new Matrix();
    private Shader shader;

    private int style = 0, colorIdx = 0, speed = 1, thickness = 1;
    private int appColor = 0xFF3D8BFF;
    private boolean running;
    private float angle;
    private float intensity = 1f;

    public void setIntensity(float k) {
        intensity = k;
        invalidate();
    }
    private long last;

    EdgeLight(Context c) {
        super(c);
        for (Paint p : new Paint[]{wide, mid, core}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(Color.WHITE);
        }
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void configure(int style, int colorIdx, int speed, int thickness) {
        this.style = style;
        this.colorIdx = (colorIdx >= 0 && colorIdx < COLORS.length) ? colorIdx : 0;
        this.speed = speed;
        this.thickness = thickness;
        rebuild();
    }

    public void setAppColor(int c) {
        if (c == appColor) return;
        appColor = c;
        if (colorIdx == 0) rebuild();
    }

    private int color() {
        return colorIdx == 0 ? appColor : COLORS[colorIdx];
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rebuild();
    }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float d = getResources().getDisplayMetrics().density;
        float k = thickness == 0 ? 0.6f : thickness == 1 ? 1f : 1.6f;
        wide.setStrokeWidth(28 * d * k);
        mid.setStrokeWidth(11 * d * k);
        core.setStrokeWidth(3 * d * k);
        float inset = core.getStrokeWidth() / 2f;
        path.reset();
        path.addRoundRect(new RectF(inset, inset, w - inset, h - inset), 22 * d, 22 * d, Path.Direction.CW);
        float cx = w / 2f, cy = h / 2f;
        int c = color();
        if (style == 0) {
            int[] cs = new int[13];
            for (int i = 0; i < 13; i++) cs[i] = Color.HSVToColor(new float[]{(i * 30) % 360, 0.85f, 1f});
            shader = new SweepGradient(cx, cy, cs, null);
        } else if (style == 1) {
            int t = c & 0x00FFFFFF;
            int[] cs = {t, t, c, 0xFFFFFFFF, t, t, c, 0xFFFFFFFF};
            float[] pos = {0f, 0.20f, 0.485f, 0.5f, 0.5f, 0.70f, 0.985f, 1f};
            shader = new SweepGradient(cx, cy, cs, pos);
        } else {
            shader = null;
        }
        for (Paint p : new Paint[]{wide, mid, core}) {
            p.setShader(shader);
            if (shader == null) p.setColor(c);
            else p.setColor(Color.WHITE);
        }
        invalidate();
    }

    public void start() {
        running = true;
        last = 0;
        invalidate();
    }

    public void stop() {
        running = false;
    }

    @Override
    protected void onDraw(Canvas cv) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.2f, (now - last) / 1000f);
        last = now;
        float dps = speed == 0 ? 30 : speed == 1 ? 70 : 150;
        angle = (angle + dps * dt) % 360f;
        float a = 1f;
        if (shader != null) {
            m.setRotate(angle, getWidth() / 2f, getHeight() / 2f);
            shader.setLocalMatrix(m);
        } else {
            a = 0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(now / 1000.0 * (dps / 35f)));
        }
        a *= intensity;
        wide.setAlpha((int) (55 * a));
        mid.setAlpha((int) (125 * a));
        core.setAlpha((int) (255 * a));
        cv.drawPath(path, wide);
        cv.drawPath(path, mid);
        cv.drawPath(path, core);
        if (running) postInvalidateDelayed(Extras.jimatNow ? 120 : 33);   // Mod Jimat: about 8 frames a second
    }
}
