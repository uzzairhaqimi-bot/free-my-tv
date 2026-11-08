package io.freemytv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.TextView;

/**
 * The Free My TV home screen (Android's "Home" role in Free My TV mode). It runs in the :home process: if it crashes,
 * the safety net in the main process keeps working. On the 3rd crash within 5 min, the Watchdog gives the home screen
 * back to the TV's original one.
 */
public class HomeActivity extends Activity {
    /** Opens All Apps (a remote's All Apps button, RemoteKeys). */
    static final String EXTRA_ALL_APPS = "io.freemytv.ALL_APPS";
    private HomeView home;
    private int prefsVersion = Prefs.version;   // settings changed while we were away: start over

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Watchdog.install(this);
        SleepTimer.listen(this);
        SleepTimer.check(this);
        if (Watchdog.crashedTooOften(this)) {
            // Don't try to draw again: only show a notice while the main process restores the original home screen.
            HomeMode.requestRevertHome(this);
            TextView t = new TextView(this);
            t.setText(Ui.tr("Free My TV's home screen ran into a problem.\nGoing back to the TV's original home screen…"));
            t.setTextColor(Color.WHITE);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            t.setGravity(Gravity.CENTER);
            t.setBackgroundColor(Color.rgb(7, 11, 30));
            setContentView(t);
            return;
        }
        Ui.init(this);
        home = new HomeView(this);
        // Fork extras: a video behind the home screen and an RGB LED glow over its edges (Extras, "Video & LED").
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        videoBg = new VideoBg(this);
        videoBg.setVisibility(android.view.View.GONE);
        root.addView(videoBg, new android.widget.FrameLayout.LayoutParams(-1, -1));
        root.addView(home, new android.widget.FrameLayout.LayoutParams(-1, -1));
        edge = new EdgeLight(this);
        edge.setVisibility(android.view.View.GONE);
        root.addView(edge, new android.widget.FrameLayout.LayoutParams(-1, -1));
        // Fork extras: music (out of sight) and a motivational line that changes every minute (top centre).
        music = new MusicBg(this);
        root.addView(music, new android.widget.FrameLayout.LayoutParams(2, 2));
        quote = new android.widget.TextView(this);
        quote.setTextColor(0xF2FFFFFF);
        quote.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        quote.setGravity(Gravity.CENTER);
        quote.setMaxLines(2);
        quote.setShadowLayer(8, 0, 2, 0x99000000);
        float d = getResources().getDisplayMetrics().density;
        quote.setPadding(Math.round(18 * d), Math.round(8 * d), Math.round(18 * d), Math.round(8 * d));
        android.graphics.drawable.GradientDrawable qb = new android.graphics.drawable.GradientDrawable();
        qb.setCornerRadius(40 * d);
        qb.setColor(0x59000000);
        qb.setStroke(Math.round(d), 0x33FFFFFF);
        quote.setBackground(qb);
        quote.setFocusable(false);
        quote.setVisibility(android.view.View.GONE);
        android.widget.FrameLayout.LayoutParams ql = new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ql.topMargin = Math.round(30 * d);
        quote.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.42f));
        root.addView(quote, ql);
        setContentView(root);
        getWindow().setBackgroundDrawable(null);
        if (getIntent().getBooleanExtra(EXTRA_ALL_APPS, false)) home.showAllApps();
        // At power-on "Live TV" (#346): the first time the home screen comes up after a full start, in Free My TV mode.
        if (HomeMode.noteHomeShown(this) && SystemClock.elapsedRealtime() < 240_000 && "live".equals(Prefs.powerOn())) {
            org.json.JSONObject s = HomeMode.state(this);
            if (HomeMode.FREE.equals(s.optString("mode")) && !s.optBoolean("homeReverted")) home.postDelayed(powerOnLive, 10_000);
        }
    }

    private boolean resumed, keyPressed;
    private VideoBg videoBg;
    private EdgeLight edge;
    private MusicBg music;
    private android.widget.TextView quote;
    private final android.os.Handler extrasHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private int videoEvery;

    /** Every minute: a new line; every `videoEvery` minutes: the next video. */
    private final Runnable minute = new Runnable() {
        private int count;
        @Override public void run() {
            try {
                count++;
                if (quote.getVisibility() == android.view.View.VISIBLE) showQuote();
                if (Extras.motto != null) setMotto(Quotes.next());
                if (videoEvery > 0 && Extras.videoActive && count % videoEvery == 0) videoBg.nextItem();
            } catch (Throwable ignored) { }
            extrasHandler.postDelayed(this, 60_000);
        }
    };

    /** The big line under the clock (HomeView draws it in place of the prices). */
    private void setMotto(String s) {
        Extras.motto = s;
        Extras.mottoAt = SystemClock.uptimeMillis();
        home.mottoChanged();
    }

    private void showQuote() {
        final String s = Quotes.next();
        quote.animate().alpha(0f).setDuration(400).withEndAction(() -> {
            quote.setText("✦  " + s);
            quote.animate().alpha(1f).setDuration(600).start();
        }).start();
    }

    /** Fork extras: start or stop the video and the LED glow from Extras' file. Never throws. */
    private void applyExtras() {
        if (home == null) return;
        try {
            Extras x = Extras.load(this);
            boolean video = x.video && videoBg.play(x.link);
            if (!video) videoBg.stop();
            videoBg.setVisibility(video ? android.view.View.VISIBLE : android.view.View.GONE);
            if (video != Extras.videoActive) {
                Extras.videoActive = video;
                home.extrasChanged();
            }
            videoEvery = x.videoEvery;
            if (x.music) music.play(x.musicLink, x.musicVolume()); else music.stop();
            boolean big = x.quotes && x.quoteStyle == 0;
            if (big && Extras.motto == null) setMotto(Quotes.next());
            if (!big && Extras.motto != null) { Extras.motto = null; home.mottoChanged(); }
            if (x.quotes && !big) {
                if (quote.getVisibility() != android.view.View.VISIBLE) {
                    quote.setText("✦  " + Quotes.next());
                    quote.setAlpha(1f);
                    quote.setVisibility(android.view.View.VISIBLE);
                }
            } else {
                quote.setVisibility(android.view.View.GONE);
            }
            extrasHandler.removeCallbacks(minute);
            extrasHandler.postDelayed(minute, 60_000);
            if (x.led) {
                edge.configure(x.ledStyle, x.ledColor, x.ledSpeed, x.ledWidth);
                edge.setVisibility(android.view.View.VISIBLE);
                edge.start();
            } else {
                edge.stop();
                edge.setVisibility(android.view.View.GONE);
            }
        } catch (Throwable t) {
            AppLog.w("extras: " + t);
        }
    }

    /** Free memory while an app is in front: no video and no LED animation behind it. */
    private void pauseExtras() {
        try {
            if (videoBg != null) videoBg.stop();
            if (edge != null) edge.stop();
            if (music != null) music.stop();
            extrasHandler.removeCallbacks(minute);
        } catch (Throwable ignored) { }
    }

    /** Live TV, unless a key came first or something else is in front by then. */
    private final Runnable powerOnLive = () -> {
        if (!resumed || keyPressed) return;
        AppLog.w("at power-on: Live TV");
        HomeApps.open(this, HomeApps.action(HomeApps.LIVE_TV, HomeApps.TOP_LIVE, Ui.tr("Live TV"), "live_tv"), null);
    };

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        // TCL's Android 11 records the home activity as translucent (occludesParent=false) whatever its theme says:
        // Android then keeps the app just left visible, and home tasks are layered under all others, so that app stays
        // on screen over ours. Opaque again on every resume (setTranslucent is public from Android 11).
        if (RemoteKeys.TCL && Build.VERSION.SDK_INT >= 30) setTranslucent(false);
        if (prefsVersion != Prefs.version) {
            prefsVersion = Prefs.version;
            HomeData.get(this).settingsChanged();
            recreate();   // a new language or city: draw everything again
            return;
        }
        if (home != null) home.resume();
        applyExtras();
        Updates.tellOnce(this);   // an update the week's check got ready: said once
    }

    @Override protected void onPause() {
        resumed = false;
        pauseExtras();
        if (home != null) home.pause();
        super.onPause();
    }

    /** Home button pressed on the home screen: back to the entry state (focus on the first app, sheet closed). */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (home == null) return;
        if (intent.getBooleanExtra(EXTRA_ALL_APPS, false)) {
            home.showAllApps();
            return;
        }
        long now = SystemClock.uptimeMillis();
        boolean twice = Prefs.recents().equals("home") && lastHome != 0 && now - lastHome < TWICE_MS;
        lastHome = twice ? 0 : now;
        if (twice) home.showRecents();
        else home.reset(true);
    }

    /** When Home was last pressed, and how soon the second press counts (Settings > Home Settings). */
    private long lastHome;
    private static final long TWICE_MS = 700;

    @Override protected void onDestroy() {
        pauseExtras();
        if (home != null) {
            home.removeCallbacks(powerOnLive);
            home.destroy();
        }
        super.onDestroy();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_COMPLETE) Recs.get(this).trim();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        keyPressed = true;
        if (RemoteKeys.onKey(this, event)) return true;
        if (home != null && home.onKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

}
