package io.freemytv;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Fork extra: the "Ringankan Stick" button (All Apps, and Video & LED). A small panel says what it's doing, runs
 * Lighten, shows the result for a few seconds and closes itself.
 */
public class RinganActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        int pad = Math.round(28 * d);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xF01E1B4B, 0xF00F766E});
        bg.setCornerRadius(24 * d);
        box.setBackground(bg);
        TextView title = new TextView(this);
        title.setText("⚡ Meringankan stick…");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        TextView body = new TextView(this);
        body.setText("Menutup aplikasi di latar dan mengosongkan memori.\nJangan tekan apa-apa.");
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        body.setTextColor(0xDDE2E8F0);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, Math.round(12 * d), 0, 0);
        box.addView(title);
        box.addView(body);
        setContentView(box);
        Lighten.run(this, (msg, closed, freed) -> {
            if (isFinishing()) return;
            title.setText(closed > 0 || freed > 0 ? "✅ Stick lebih ringan" : "✅ Sudah ringan");
            body.setText(msg);
            title.postDelayed(this::finish, 4500);
        });
    }
}
