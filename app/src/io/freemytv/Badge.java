package io.freemytv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;

/**
 * Fork extra: how a player is shown in the fork's games, so everyone finds themselves at a glance: a pin in the
 * player's colour with their number (a shape too, for those who tell colours apart badly), and a ring that pulses
 * under their character. Allocation-free.
 */
final class Badge {
    private Badge() {}

    /** One shape per seat: ● ▲ ■ ◆ */
    static final String[] SHAPE = {"●", "▲", "■", "◆"};

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG), T = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PIN = new Path();

    static {
        T.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        T.setTextAlign(Paint.Align.CENTER);
    }

    /** A pin whose point touches (x, y): the player's number in their colour; "TV" for the TV's players. */
    static void pin(Canvas c, int seat, boolean bot, float x, float y, float s, float t) {
        int col = Party.COLORS[seat];
        float bob = (float) Math.sin(t * 4 + seat) * s * 0.08f;
        float cy = y - s * 1.1f + bob;
        P.setColor(0x66000000);
        c.drawCircle(x + s * 0.06f, cy + s * 0.08f, s * 0.62f, P);
        PIN.reset();
        PIN.moveTo(x - s * 0.3f, cy + s * 0.45f);
        PIN.lineTo(x, y + bob);
        PIN.lineTo(x + s * 0.3f, cy + s * 0.45f);
        PIN.close();
        P.setColor(0xFFFFFFFF);
        c.drawPath(PIN, P);
        c.drawCircle(x, cy, s * 0.62f, P);
        P.setColor(col);
        c.drawCircle(x, cy, s * 0.52f, P);
        T.setColor(0xFF0B1020);
        T.setTextSize(s * (bot ? 0.48f : 0.72f));
        c.drawText(bot ? "TV" : String.valueOf(seat + 1), x, cy + s * (bot ? 0.17f : 0.26f), T);
        T.setTextSize(s * 0.42f);
        T.setColor(col);
        c.drawText(SHAPE[seat], x + s * 0.78f, cy - s * 0.3f, T);
    }

    /** A pulsing ring in the player's colour around (x, y). */
    static void ring(Canvas c, int seat, float x, float y, float r, float t) {
        int col = Party.COLORS[seat];
        float k = 0.5f + 0.5f * (float) Math.sin(t * 5 + seat);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(r * 0.14f);
        P.setColor(Color.argb((int) (120 + 110 * k), Color.red(col), Color.green(col), Color.blue(col)));
        c.drawCircle(x, y, r * (1 + 0.08f * k), P);
        P.setStyle(Paint.Style.FILL);
    }

    /** The small badge used in score boxes: a coloured dot with the number. */
    static void dot(Canvas c, int seat, boolean bot, float x, float y, float r) {
        P.setColor(Party.COLORS[seat]);
        c.drawCircle(x, y, r, P);
        T.setColor(0xFF0B1020);
        T.setTextSize(r * (bot ? 0.9f : 1.3f));
        c.drawText(bot ? "TV" : String.valueOf(seat + 1), x, y + r * (bot ? 0.32f : 0.45f), T);
    }
}
