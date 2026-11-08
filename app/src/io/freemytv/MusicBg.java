package io.freemytv;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fork extra: background music while the home screen shows, from the person's own links: YouTube videos or playlists
 * (YouTube's embedded player, out of sight) or direct https audio files. Shuffled, one after another. Stopped as soon
 * as the home screen pauses. Never throws.
 */
final class MusicBg extends FrameLayout {
    private WebView web;
    private MediaPlayer player;
    private String playing;
    private List<String> files;
    private int index;
    private float volume = 0.45f;

    MusicBg(Context c) {
        super(c);
        setFocusable(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    }

    boolean play(String link, float vol) {
        if (link == null) link = "";
        boolean same = link.equals(playing) && (web != null || player != null);
        volume = vol;
        if (same) { applyVolume(); return true; }
        stop();
        Extras.Source s = Extras.parse(link);
        if (s.empty()) return false;
        try {
            if (!s.youtube.isEmpty()) startYouTube(s.youtube);
            else startFiles(s.direct);
            playing = link;
            return true;
        } catch (Throwable t) {
            AppLog.w("music: " + t);
            stop();
            return false;
        }
    }

    void stop() {
        playing = null;
        try { if (player != null) player.release(); } catch (Throwable ignored) { }
        player = null;
        try {
            if (web != null) {
                removeView(web);
                web.loadUrl("about:blank");
                web.destroy();
            }
        } catch (Throwable ignored) { }
        web = null;
    }

    /** Next track now. */
    void skip() {
        try {
            if (web != null) web.evaluateJavascript("try{next()}catch(e){}", null);
            else if (player != null) nextFile();
        } catch (Throwable ignored) { }
    }

    private void applyVolume() {
        try {
            if (player != null) player.setVolume(volume, volume);
            if (web != null) web.evaluateJavascript("try{p.setVolume(" + Math.round(volume * 100) + ")}catch(e){}", null);
        } catch (Throwable ignored) { }
    }

    private void startFiles(List<String> urls) {
        files = new ArrayList<>(urls);
        Collections.shuffle(files);
        index = 0;
        openFile();
    }

    private void openFile() {
        try { if (player != null) player.release(); } catch (Throwable ignored) { }
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        player.setOnPreparedListener(mp -> { mp.setVolume(volume, volume); mp.start(); });
        player.setOnCompletionListener(mp -> nextFile());
        player.setOnErrorListener((mp, w, x) -> { nextFile(); return true; });
        try {
            player.setDataSource(files.get(index));
            player.prepareAsync();
        } catch (Throwable t) {
            AppLog.w("music: " + t);
        }
    }

    private void nextFile() {
        if (files == null || files.isEmpty()) return;
        index = (index + 1) % files.size();
        openFile();
    }

    private void startYouTube(List<String[]> ids) {
        List<String[]> order = new ArrayList<>(ids);
        Collections.shuffle(order);
        web = new WebView(getContext());
        web.setFocusable(false);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        // Small and transparent: only the sound is wanted.
        addView(web, new LayoutParams(2, 2));
        web.setAlpha(0.01f);
        web.loadDataWithBaseURL("https://freemytv.local/", html(order, Math.round(volume * 100)), "text/html", "utf-8", null);
    }

    private static String html(List<String[]> ids, int vol) {
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) arr.append(',');
            arr.append("[\"").append(ids.get(i)[0]).append("\",\"").append(ids.get(i)[1]).append("\"]");
        }
        arr.append(']');
        return "<!doctype html><html><body style='margin:0;background:#000'><div id='p'></div><script>"
                + "var items=" + arr + ",i=0,p,errs=0;"
                + "function load(){var it=items[i];if(it[0]=='v')p.loadVideoById(it[1]);"
                + "else{p.loadPlaylist({list:it[1],listType:'playlist'});setTimeout(function(){try{p.setShuffle(true);}catch(e){}},1500);}}"
                + "function next(){i=(i+1)%items.length;load();}"
                + "function onYouTubeIframeAPIReady(){var it=items[0];"
                + "var pv={autoplay:1,controls:0,disablekb:1,fs:0,iv_load_policy:3,playsinline:1,rel:0,origin:'https://freemytv.local'};"
                + "var cfg={width:'200',height:'200',playerVars:pv,events:{"
                + "onReady:function(e){e.target.unMute();e.target.setVolume(" + vol + ");"
                + "if(items[0][0]=='l'){try{e.target.setShuffle(true);}catch(x){}}e.target.playVideo();},"
                + "onStateChange:function(e){if(e.data===1)errs=0;if(e.data===0){if(items[i][0]=='l'){var pl=p.getPlaylist()||[];"
                + "if(p.getPlaylistIndex()<pl.length-1){p.nextVideo();return;}}next();}},"
                + "onError:function(){errs++;if(errs<8)setTimeout(function(){if(items[i][0]=='l')p.nextVideo();else next();},1000);}}};"
                + "if(it[0]=='v')cfg.videoId=it[1];else{pv.listType='playlist';pv.list=it[1];}"
                + "p=new YT.Player('p',cfg);}"
                + "</script><script src='https://www.youtube.com/iframe_api'></script></body></html>";
    }
}
