package io.freemytv;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.VideoView;

import java.util.List;

/**
 * Fork extra: a silent, looping video behind the home screen, from a YouTube link (video or playlist) or a direct
 * https video link. Built only while the home screen shows and torn down on pause, to keep memory free on 1 GB TVs.
 * Never throws: :home's watchdog counts any crash as the home screen's.
 */
final class VideoBg extends FrameLayout {
    private WebView web;
    private VideoView video;
    private String playing;
    private List<String> direct;
    private int index;

    VideoBg(Context c) {
        super(c);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        setFocusable(false);
        setBackgroundColor(Color.BLACK);
    }

    /** Starts the link, unless it is already playing. Returns false when the link has nothing playable. */
    boolean play(String link) {
        if (link == null) link = "";
        if (link.equals(playing) && (web != null || video != null)) return true;
        stop();
        Extras.Source s = Extras.parse(link);
        if (s.empty()) return false;
        try {
            if (!s.youtube.isEmpty()) startYouTube(s.youtube);
            else startDirect(s.direct);
            playing = link;
            return true;
        } catch (Throwable t) {
            AppLog.w("video background: " + t);
            stop();
            return false;
        }
    }

    void stop() {
        playing = null;
        try {
            if (video != null) video.stopPlayback();
        } catch (Throwable ignored) { }
        video = null;
        try {
            if (web != null) {
                removeView(web);
                web.loadUrl("about:blank");
                web.destroy();
            }
        } catch (Throwable ignored) { }
        web = null;
        removeAllViews();
    }

    private void startDirect(List<String> urls) {
        direct = urls;
        index = 0;
        video = new VideoView(getContext());
        video.setFocusable(false);
        video.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE);
        addView(video, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER));
        video.setOnPreparedListener(mp -> {
            try {
                mp.setVolume(0f, 0f);
                mp.setLooping(direct.size() == 1);
                if (video != null) video.start();
            } catch (Throwable ignored) { }
        });
        video.setOnCompletionListener(mp -> next());
        video.setOnErrorListener((mp, what, extra) -> {
            AppLog.w("video background: can't play " + what + "/" + extra);
            if (direct.size() > 1) next();
            return true;   // no "Can't play this video" dialog over the home screen
        });
        video.setVideoURI(Uri.parse(direct.get(0)));
    }

    /** Fork extra: the next video now (the rotation timer). */
    void nextItem() {
        try {
            if (web != null) web.evaluateJavascript("try{next()}catch(e){}", null);
            else next();
        } catch (Throwable ignored) { }
    }

    private void next() {
        try {
            if (video == null || direct == null || direct.isEmpty()) return;
            index = (index + 1) % direct.size();
            video.setVideoURI(Uri.parse(direct.get(index)));
        } catch (Throwable ignored) { }
    }

    private void startYouTube(List<String[]> ids) {
        web = new WebView(getContext());
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        addView(web, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        web.loadDataWithBaseURL("https://freemytv.local/", html(ids), "text/html", "utf-8", null);
    }

    private static String html(List<String[]> ids) {
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) arr.append(',');
            arr.append("[\"").append(ids.get(i)[0]).append("\",\"").append(ids.get(i)[1]).append("\"]");
        }
        arr.append(']');
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}"
                + "#w{position:fixed;top:0;left:0;right:0;bottom:0;overflow:hidden}"
                + "#p{position:absolute;top:50%;left:50%;width:177.78vh;height:56.25vw;min-width:100vw;min-height:100vh;"
                + "transform:translate(-50%,-50%);-webkit-transform:translate(-50%,-50%);pointer-events:none;border:0}</style></head>"
                + "<body><div id='w'><div id='p'></div></div><script>"
                + "var items=" + arr + ",i=0,p;"
                + "function load(){var it=items[i];if(it[0]=='v')p.loadVideoById(it[1]);else p.loadPlaylist({list:it[1],listType:'playlist'});}"
                + "function next(){i=(i+1)%items.length;load();}"
                + "function onYouTubeIframeAPIReady(){var it=items[0];"
                + "var pv={autoplay:1,mute:1,controls:0,disablekb:1,fs:0,iv_load_policy:3,modestbranding:1,playsinline:1,rel:0,origin:'https://freemytv.local'};"
                + "var cfg={width:'100%',height:'100%',playerVars:pv,events:{"
                + "onReady:function(e){e.target.mute();e.target.playVideo();},"
                + "onStateChange:function(e){if(e.data===0){if(items[i][0]=='l'){var pl=p.getPlaylist()||[];if(p.getPlaylistIndex()<pl.length-1){p.nextVideo();return;}}next();}},"
                + "onError:function(){setTimeout(next,1500);}}};"
                + "if(it[0]=='v')cfg.videoId=it[1];else{pv.listType='playlist';pv.list=it[1];}"
                + "p=new YT.Player('p',cfg);}"
                + "</script><script src='https://www.youtube.com/iframe_api'></script></body></html>";
    }
}
