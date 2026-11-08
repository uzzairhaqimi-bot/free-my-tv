package io.freemytv;

import android.content.Context;
import android.widget.FrameLayout;

/**
 * Fork extra: the sound of the fork's games. Picks, from Video & LED's "Muzik Dalam Permainan", between the person's
 * own link (YouTube's player, out of sight, through MusicBg) and the songs GameMusic composes, by the moment's mood:
 * calm menus and board games, or action (quiz show, platformer, boss fights). Sound effects always come from GameMusic.
 */
final class GameSound {
    private final GameMusic synth = new GameMusic();
    private MusicBg link;
    private Extras x;
    private boolean resumed, hasLink;
    private int now = -1;   // 0 the link, 1 composed calm, 2 composed upbeat, 3 silent

    void attach(FrameLayout root) {
        link = new MusicBg(root.getContext());
        root.addView(link, new FrameLayout.LayoutParams(2, 2));
    }

    void resume(Context c) {
        x = Extras.load(c);
        hasLink = !Extras.parse(x.gameMusicLink).empty();
        resumed = true;
        now = -1;
    }

    void pause() {
        resumed = false;
        now = -1;
        synth.stop();
        if (link != null) link.stop();
    }

    /** Cheap: call it every frame with the mood of what's on screen. UI thread only. */
    void mood(int mood) {
        if (!resumed || x == null) return;
        boolean calm = mood == GameMusic.SANTAI;
        int want;
        switch (x.gameMusic) {
            case 0: want = calm && hasLink ? 0 : calm ? 1 : 2; break;
            case 1: want = calm ? 1 : 2; break;
            case 2: want = hasLink ? 0 : 1; break;
            default: want = 3;
        }
        if (want == now) return;
        now = want;
        float vol = x.musicVolume();
        try {
            if (want == 0) {
                synth.start(GameMusic.SANTAI, vol, false);
                if (link != null) link.play(x.gameMusicLink, vol);
            } else if (want == 3) {
                if (link != null) link.stop();
                synth.stop();
            } else {
                if (link != null) link.stop();
                synth.start(want == 1 ? GameMusic.SANTAI : GameMusic.SEMANGAT, vol, true);
            }
        } catch (Throwable t) {
            AppLog.w("game sound: " + t);
        }
    }

    void sfx(int kind) { synth.sfx(kind); }
}
