package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Fork extras' settings: "Video & LED". Opens from All Apps, and also takes values from the computer, e.g.
 * adb shell am start -n io.freemytv/.ExtrasActivity --es link "https://youtu.be/…" --ez video true
 */
public class ExtrasActivity extends Activity {
    private static final String[] STYLES = {"Pelangi", "Komet", "Bernafas"};
    private static final String[] COLORS = {"Ikut warna aplikasi", "Biru", "Ungu", "Merah jambu", "Merah", "Oren", "Hijau", "Putih"};
    private static final String[] SPEEDS = {"Perlahan", "Sederhana", "Laju"};
    private static final String[] WIDTHS = {"Nipis", "Sederhana", "Tebal"};
    private static final String[] VOLS = {"Perlahan", "Sederhana", "Kuat"};
    private static final String[] GAME_MUSIC = {"Campur: lagu saya + lagu semangat", "Lagu ciptaan Bijak sahaja", "Pautan saya sahaja", "Senyap"};

    private interface Val { String get(); }

    private Extras x;
    private LinearLayout list;
    private final List<Runnable> refreshers = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        x = Extras.load(this);
        if (fromComputer(getIntent())) {
            finish();
            return;
        }
        build();
    }

    /** Values sent with am start: saved, then the screen closes. */
    private boolean fromComputer(Intent i) {
        if (i == null || i.getExtras() == null) return false;
        boolean any = false;
        if (i.hasExtra("link")) { x.link = String.valueOf(i.getStringExtra("link")).trim(); any = true; }
        if (i.hasExtra("video")) { x.video = i.getBooleanExtra("video", x.video); any = true; }
        if (i.hasExtra("led")) { x.led = i.getBooleanExtra("led", x.led); any = true; }
        if (i.hasExtra("music")) { x.musicLink = String.valueOf(i.getStringExtra("music")).trim(); x.music = !Extras.parse(x.musicLink).empty(); any = true; }
        if (i.hasExtra("videoEvery")) { x.videoEvery = Math.max(0, Math.min(60, i.getIntExtra("videoEvery", 5))); any = true; }
        if (i.hasExtra("musicVol")) { x.musicVol = Math.max(0, Math.min(2, i.getIntExtra("musicVol", 1))); any = true; }
        if (i.hasExtra("gameMusicLink")) { x.gameMusicLink = String.valueOf(i.getStringExtra("gameMusicLink")).trim(); any = true; }
        if (i.hasExtra("gameMusic")) { x.gameMusic = Math.max(0, Math.min(3, i.getIntExtra("gameMusic", 0))); any = true; }
        if (i.hasExtra("quotes")) { x.quotes = i.getBooleanExtra("quotes", x.quotes); any = true; }
        if (i.hasExtra("ledStyle")) { x.ledStyle = Math.max(0, Math.min(2, i.getIntExtra("ledStyle", 0))); any = true; }
        if (!any) return false;
        x.save(this);
        Toast.makeText(this, "Video & LED disimpan", Toast.LENGTH_SHORT).show();
        return true;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF161A2B, 0xFF0B0B10, 0xFF000000}));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER);
        TextView title = new TextView(this);
        title.setText("Video & LED");
        title.setTextSize(42);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        TextView sub = new TextView(this);
        sub.setText("Tambahan untuk skrin utama Free My TV");
        sub.setTextSize(15);
        sub.setTextColor(0x99FFFFFF);
        left.addView(title);
        left.addView(sub);
        root.addView(left, new LinearLayout.LayoutParams(0, -1, 0.38f));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(dp(20), dp(36), dp(36), dp(36));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(0, -1, 0.62f));

        header("Paling Mudah");
        row("📱 Hantar lagu & video dari telefon", () -> "Imbas kod QR", this::phoneLinks);

        header("Video Latar Belakang");
        row("Video latar", () -> x.video ? "Hidup" : "Mati", () -> {
            x.video = !x.video;
            if (x.video && Extras.parse(x.link).empty()) editLink();
        });
        row("Pautan video", () -> {
            int n = Extras.parse(x.link).youtube.size() + Extras.parse(x.link).direct.size();
            return n == 0 ? "Tiada" : n + " pautan";
        }, this::editLink);

        row("Tukar video setiap", () -> x.videoEvery == 0 ? "Bila tamat" : x.videoEvery + " minit", () -> {
            int i = 0;
            for (int k = 0; k < Extras.EVERY.length; k++) if (Extras.EVERY[k] == x.videoEvery) i = k;
            x.videoEvery = Extras.EVERY[(i + 1) % Extras.EVERY.length];
        });

        header("Muzik Latar");
        row("Muzik latar", () -> x.music ? "Hidup" : "Mati", () -> {
            x.music = !x.music;
            if (x.music && Extras.parse(x.musicLink).empty()) editMusic();
        });
        row("Pautan muzik", () -> {
            Extras.Source s = Extras.parse(x.musicLink);
            int n = s.youtube.size() + s.direct.size();
            return n == 0 ? "Tiada" : n + " pautan (rawak)";
        }, this::editMusic);
        row("Kelantangan muzik", () -> VOLS[x.musicVol], () -> x.musicVol = (x.musicVol + 1) % 3);

        header("Jimat & Ringan");
        row("⚡ Ringankan stick sekarang", () -> "Tutup aplikasi di latar, kosongkan memori",
                () -> startActivity(new Intent(this, RinganActivity.class)));
        row("Mod Jimat (bila video latar hidup)", () -> x.jimat ? "Hidup" : "Mati", () -> x.jimat = !x.jimat);
        row("Auto ringankan bila kembali ke skrin utama", () -> x.autoRingan ? "Hidup" : "Mati", () -> x.autoRingan = !x.autoRingan);

        header("Muzik Dalam Permainan");
        row("Jenis muzik", () -> GAME_MUSIC[x.gameMusic], () -> x.gameMusic = (x.gameMusic + 1) % GAME_MUSIC.length);
        row("Pautan lagu permainan", () -> {
            Extras.Source s = Extras.parse(x.gameMusicLink);
            int n = s.youtube.size() + s.direct.size();
            return n == 0 ? "Tiada" : n + " pautan";
        }, this::editGameMusic);

        header("Kata-kata Semangat");
        row("Tukar setiap minit", () -> x.quotes ? "Hidup" : "Mati", () -> x.quotes = !x.quotes);
        row("Gaya", () -> x.quoteStyle == 0 ? "Besar & tebal di bawah jam" : "Kecil di atas", () -> x.quoteStyle = 1 - x.quoteStyle);

        header("Permainan");
        row("Retro Arked", () -> "Pong · Komet · Ular", () -> startActivity(new android.content.Intent(this, RetroActivity.class)));

        header("Lampu LED RGB Tepi Skrin");
        row("Lampu LED", () -> x.led ? "Hidup" : "Mati", () -> x.led = !x.led);
        row("Gaya", () -> STYLES[x.ledStyle], () -> x.ledStyle = (x.ledStyle + 1) % 3);
        row("Warna", () -> COLORS[x.ledColor], () -> new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Warna LED")
                .setSingleChoiceItems(COLORS, x.ledColor, (d, w) -> {
                    d.dismiss();
                    x.ledColor = w;
                    x.save(this);
                    refresh();
                }).show());
        row("Kelajuan", () -> SPEEDS[x.ledSpeed], () -> x.ledSpeed = (x.ledSpeed + 1) % 3);
        row("Ketebalan", () -> WIDTHS[x.ledWidth], () -> x.ledWidth = (x.ledWidth + 1) % 3);

        header("Sudah ada dalam Free My TV");
        row("Teruskan menonton", () -> "Home Settings › Top of the screen", null);
        row("Widget YouTube Music", () -> "Home Settings › Now Playing widget", null);

        setContentView(root);
    }

    private void header(String s) {
        TextView t = new TextView(this);
        t.setText(s.toUpperCase());
        t.setTextSize(12);
        t.setLetterSpacing(0.12f);
        t.setTextColor(0x80FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(list.getChildCount() == 0 ? 0 : 22);
        lp.bottomMargin = dp(6);
        lp.leftMargin = dp(18);
        list.addView(t, lp);
    }

    private void row(String label, Val val, Runnable click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setPadding(dp(18), dp(13), dp(18), dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0x14FFFFFF);
        r.setBackground(bg);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(17);
        l.setTextColor(Color.WHITE);
        r.addView(l, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = new TextView(this);
        v.setTextSize(15);
        v.setTextColor(0x99FFFFFF);
        v.setSingleLine(true);
        v.setMaxWidth(dp(260));
        v.setEllipsize(TextUtils.TruncateAt.END);
        r.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        list.addView(r, lp);
        Runnable upd = () -> v.setText(val.get());
        refreshers.add(upd);
        upd.run();
        r.setOnFocusChangeListener((x1, f) -> {
            bg.setColor(f ? 0xFFFFFFFF : 0x14FFFFFF);
            l.setTextColor(f ? 0xFF000000 : 0xFFFFFFFF);
            v.setTextColor(f ? 0x99000000 : 0x99FFFFFF);
            r.animate().scaleX(f ? 1.03f : 1f).scaleY(f ? 1.03f : 1f).setDuration(140).start();
        });
        r.setOnClickListener(x1 -> {
            if (click == null) return;
            click.run();
            x.save(this);
            refresh();
        });
        if (list.getChildCount() == 2) r.post(r::requestFocus);
    }

    private void refresh() {
        for (Runnable r : refreshers) r.run();
    }

    private void editMusic() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
        e.setMinLines(2);
        e.setHint("Pautan YouTube / playlist, atau fail .mp3 (https)");
        e.setText(x.musicLink);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(10), dp(20), 0);
        wrap.addView(e, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Pautan muzik")
                .setMessage("Satu pautan setiap baris. Dimainkan secara rawak bila skrin utama dibuka, dan berhenti bila aplikasi lain dibuka.")
                .setView(wrap)
                .setPositiveButton("Simpan", (d, w) -> {
                    x.musicLink = e.getText().toString().trim();
                    if (!Extras.parse(x.musicLink).empty()) x.music = true;
                    else Toast.makeText(this, "Tiada pautan yang sah", Toast.LENGTH_SHORT).show();
                    x.save(this);
                    refresh();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private LinkPage linkPage;

    /** Shows a QR code; the phone that scans it pastes the links (LinkPage), and they're saved as they come. */
    private void phoneLinks() {
        java.net.InetAddress lan = KeyPage.lanAddress(this);
        if (lan == null) { Toast.makeText(this, "TV tidak bersambung ke Wi-Fi", Toast.LENGTH_LONG).show(); return; }
        final AlertDialog[] dialog = new AlertDialog[1];
        try {
            if (linkPage != null) linkPage.close();
            linkPage = new LinkPage(lan, (music, video, game) -> runOnUiThread(() -> {
                int got = 0;
                if (!Extras.parse(music).empty()) { x.musicLink = music; x.music = true; got++; }
                if (!Extras.parse(video).empty()) { x.link = video; x.video = true; got++; }
                if (!Extras.parse(game).empty()) { x.gameMusicLink = game; if (x.gameMusic == 3) x.gameMusic = 0; got++; }
                x.save(this);
                refresh();
                Toast.makeText(this, got > 0 ? "Pautan diterima dari telefon ✓" : "Tiada pautan YouTube yang sah", Toast.LENGTH_LONG).show();
                if (got > 0 && dialog[0] != null) dialog[0].dismiss();
            }));
        } catch (java.io.IOException e) {
            Toast.makeText(this, "Tidak dapat buka halaman telefon", Toast.LENGTH_LONG).show();
            return;
        }
        boolean[][] m = Qr.encode(linkPage.url);
        int q = 4, n = m.length;
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(n + q * 2, n + q * 2, android.graphics.Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.WHITE);
        for (int y = 0; y < n; y++) for (int xx = 0; xx < n; xx++) if (m[y][xx]) b.setPixel(xx + q, y + q, Color.BLACK);
        android.widget.ImageView img = new android.widget.ImageView(this);
        android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(getResources(), b);
        d.setFilterBitmap(false);
        img.setImageDrawable(d);
        TextView how = new TextView(this);
        how.setText("1. Telefon guna Wi-Fi yang sama dengan TV.\n2. Buka kamera telefon, imbas kod ini.\n"
                + "3. Dalam YouTube: Kongsi → Salin pautan, kemudian tampal di halaman itu.\n4. Tekan \"Hantar ke TV\".");
        how.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        how.setTextColor(0xFFE2E8F0);
        how.setPadding(dp(16), 0, 0, 0);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setPadding(dp(20), dp(10), dp(20), dp(10));
        wrap.addView(img, new LinearLayout.LayoutParams(dp(220), dp(220)));
        wrap.addView(how, new LinearLayout.LayoutParams(0, -2, 1));
        dialog[0] = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Hantar pautan dari telefon")
                .setView(wrap)
                .setNegativeButton("Tutup", null)
                .setOnDismissListener(di -> { if (linkPage != null) { linkPage.close(); linkPage = null; } })
                .show();
    }

    private void editGameMusic() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
        e.setMinLines(2);
        e.setHint("Pautan YouTube / playlist, atau fail .mp3 (https)");
        e.setText(x.gameMusicLink);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(10), dp(20), 0);
        wrap.addView(e, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Lagu dalam permainan")
                .setMessage("Dimainkan dalam Papan Permainan dan Kelas Bijak. Satu pautan setiap baris.")
                .setView(wrap)
                .setPositiveButton("Simpan", (d, w) -> {
                    x.gameMusicLink = e.getText().toString().trim();
                    x.save(this);
                    refresh();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void editLink() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
        e.setMinLines(2);
        e.setHint("https://youtu.be/… atau pautan video .mp4 (https)");
        e.setText(x.link);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(10), dp(20), 0);
        wrap.addView(e, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Pautan video")
                .setMessage("Video YouTube, playlist, atau pautan terus video (https). Dimainkan tanpa suara.")
                .setView(wrap)
                .setPositiveButton("Simpan", (d, w) -> {
                    x.link = e.getText().toString().trim();
                    if (!Extras.parse(x.link).empty()) x.video = true;
                    else Toast.makeText(this, "Tiada pautan yang sah", Toast.LENGTH_SHORT).show();
                    x.save(this);
                    refresh();
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
