package io.freemytv;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Fork extra: music for the fork's games, composed by the app as it plays (no files, nothing downloaded). Two moods:
 * SANTAI (a soft lo-fi groove: electric piano chords, round bass, light drums) and SEMANGAT (an upbeat chiptune:
 * pulse lead, bouncing bass, four-on-the-floor drums). Each song is made from a seed: a chord progression, a melody
 * motif and its variation; after a few loops a new song starts. Plus short sound effects. Runs on its own thread
 * and never throws.
 */
final class GameMusic {
    static final int SANTAI = 0, SEMANGAT = 1;
    static final int SFX_OK = 1, SFX_WRONG = 2, SFX_COIN = 3, SFX_JUMP = 4, SFX_TICK = 5, SFX_WIN = 6;

    private static final int RATE = 22050;
    private volatile boolean running;
    private volatile int mood = SANTAI;
    private volatile float volume = 0.5f;
    private volatile int pendingSfx;
    private Thread thread;
    private AudioTrack track;

    private volatile boolean musicOn = true;

    /** Starts (or changes) the music; with musicOn false only the sound effects play. */
    void start(int mood, float volume, boolean musicOn) {
        this.mood = mood;
        this.volume = volume;
        this.musicOn = musicOn;
        if (running) return;
        lastMood = -1;
        fade = 0;
        running = true;
        thread = new Thread(this::run, "game-music");
        thread.setPriority(Thread.MAX_PRIORITY - 1);
        thread.start();
    }

    void mood(int m) { mood = m; }

    void stop() {
        running = false;
        Thread t = thread;
        thread = null;
        if (t != null) try { t.join(500); } catch (InterruptedException ignored) { }
    }

    void sfx(int kind) { pendingSfx = kind; }

    // ---------------------------------------------------------------- the synth

    private static final int VOICES = 24;
    // per voice: frequency, phase, amplitude, decay per sample, wave (0 sine, 1 square, 2 triangle, 3 noise, 4 epiano), samples left, slide
    private final float[] vf = new float[VOICES], vp = new float[VOICES], va = new float[VOICES], vd = new float[VOICES], vs = new float[VOICES];
    private final int[] vw = new int[VOICES], vl = new int[VOICES];
    private int next;
    private int noise = 0x1234567;

    private void note(float freq, float amp, float seconds, int wave, float slide) {
        int i = next;
        next = (next + 1) % VOICES;
        vf[i] = freq;
        vp[i] = 0;
        va[i] = amp;
        vl[i] = (int) (seconds * RATE);
        vd[i] = (float) Math.pow(0.001, 1.0 / Math.max(1, vl[i]));   // fades to -60 dB over its length
        vw[i] = wave;
        vs[i] = slide;
    }

    private static float midi(int n) { return 440f * (float) Math.pow(2, (n - 69) / 12.0); }

    // chord progressions as scale degrees (0 = I) in a major key
    private static final int[][] PROGS = {{0, 4, 5, 3}, {5, 3, 0, 4}, {0, 5, 3, 4}, {3, 4, 2, 5}, {0, 3, 4, 3}, {1, 4, 0, 0}};
    private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] PENTA = {0, 2, 4, 7, 9};

    private int key, bpm, songBars, barInSong;
    private int[] prog;
    private final int[] motifA = new int[16], motifB = new int[16];
    private Random rnd = new Random();

    private void newSong() {
        rnd = new Random(System.nanoTime());
        key = 48 + new int[]{0, 2, 3, 5, 7, 8}[rnd.nextInt(6)];   // C, D, D#, F, G, G#
        prog = PROGS[rnd.nextInt(PROGS.length)];
        bpm = mood == SANTAI ? 76 + rnd.nextInt(14) : 124 + rnd.nextInt(16);
        // a motif: pentatonic steps, with rests, so the tune has a shape that repeats
        int pos = rnd.nextInt(5);
        for (int i = 0; i < 16; i++) {
            boolean rest = mood == SANTAI ? rnd.nextFloat() < 0.5f : rnd.nextFloat() < 0.3f;
            if (i % 4 == 0) rest = rnd.nextFloat() < 0.15f;
            pos = Math.max(0, Math.min(9, pos + rnd.nextInt(5) - 2));
            motifA[i] = rest ? -1 : pos;
        }
        for (int i = 0; i < 16; i++) motifB[i] = motifA[i] < 0 ? (rnd.nextFloat() < 0.3f ? rnd.nextInt(8) : -1) : Math.max(0, Math.min(9, motifA[i] + rnd.nextInt(3) - 1));
        songBars = 32;
        barInSong = 0;
    }

    private int scaleNote(int degree, int octave) {
        int o = degree / 7, d = degree % 7;
        return key + MAJOR[d] + 12 * (o + octave);
    }

    private int pentaNote(int idx) {
        return key + 12 + PENTA[idx % 5] + 12 * (idx / 5);
    }

    private void step(int bar, int s) {   // s: 0..15, sixteenth notes
        if (!musicOn) return;
        int chord = prog[bar % 4];
        boolean santai = mood == SANTAI;
        float beat = 60f / bpm;
        // drums
        if (santai) {
            if (s == 0 || s == 10) note(55, 0.55f, 0.25f, 0, -0.9f);              // soft kick
            if (s == 4 || s == 12) note(180, 0.18f, 0.12f, 3, 0);                 // brushed snare
            if (s % 2 == 0) note(8000, s % 4 == 2 ? 0.06f : 0.035f, 0.04f, 3, 0);  // hats
        } else {
            if (s % 4 == 0) note(60, 0.7f, 0.18f, 0, -0.95f);                      // kick, every beat
            if (s == 4 || s == 12) note(200, 0.3f, 0.14f, 3, 0);                   // snare
            if (s % 2 == 1) note(9000, 0.06f, 0.03f, 3, 0);                        // off-beat hats
        }
        // bass
        int root = scaleNote(chord, -1);
        if (santai) {
            if (s == 0) note(midi(root), 0.38f, beat * 1.8f, 0, 0);
            if (s == 8 && rnd.nextFloat() < 0.6f) note(midi(root + 7), 0.28f, beat * 0.9f, 0, 0);
        } else {
            if (s % 2 == 0) note(midi(root + (s % 4 == 2 ? 12 : 0)), 0.22f, beat * 0.4f, 2, 0);
        }
        // chords
        if (santai) {
            if (s == 0 || (s == 6 && bar % 2 == 1)) {
                for (int k = 0; k < 4; k++) note(midi(scaleNote(chord + k * 2, 1)), 0.075f, beat * 3f, 4, 0);   // a 7th chord
            }
        } else if (s % 2 == 0) {   // arpeggio
            int k = (s / 2) % 4;
            note(midi(scaleNote(chord + (k == 3 ? 4 : k * 2), 2)), 0.05f, beat * 0.25f, 1, 0);
        }
        // melody: A A B A over four bars
        int[] m = (bar % 4 == 2) ? motifB : motifA;
        int idx = (bar % 2) * 8 + s / 2;
        if (s % 2 == 0 && m[idx] >= 0) {
            float len = santai ? beat * 0.9f : beat * 0.45f;
            note(midi(pentaNote(m[idx])), santai ? 0.12f : 0.09f, len, santai ? 4 : 1, 0);
        }
    }

    private void playSfx(int kind) {
        switch (kind) {
            case SFX_OK: note(midi(76), 0.3f, 0.15f, 1, 0); note(midi(83), 0.25f, 0.35f, 0, 0); break;
            case SFX_WRONG: note(midi(45), 0.3f, 0.4f, 1, -0.3f); break;
            case SFX_COIN: note(midi(88), 0.18f, 0.08f, 1, 0); note(midi(95), 0.15f, 0.2f, 1, 0); break;
            case SFX_JUMP: note(midi(72), 0.12f, 0.12f, 1, 1.2f); break;
            case SFX_TICK: note(midi(96), 0.08f, 0.03f, 0, 0); break;
            default: for (int i = 0; i < 4; i++) note(midi(72 + new int[]{0, 4, 7, 12}[i]), 0.15f, 0.6f, 1, 0);
        }
    }


    private int lastMood = -1, bar, sixteenth;
    private long sampleInStep;
    private float fade;

    /** Renders the next samples (also used by tests). */
    void fill(short[] buf) {
        if (mood != lastMood) { lastMood = mood; newSong(); bar = 0; sixteenth = 0; sampleInStep = 0; }
        int perStep = (int) (RATE * 60f / bpm / 4);
        for (int n = 0; n < buf.length; n++) {
            if (sampleInStep == 0) {
                step(bar, sixteenth);
                int fx = pendingSfx;
                if (fx != 0) { pendingSfx = 0; playSfx(fx); }
            }
            if (++sampleInStep >= perStep) {
                sampleInStep = 0;
                if (++sixteenth == 16) {
                    sixteenth = 0;
                    bar++;
                    if (++barInSong >= songBars) { newSong(); bar = 0; }
                }
            }
            float mix = 0;
            for (int v = 0; v < VOICES; v++) {
                if (vl[v] <= 0) continue;
                vl[v]--;
                float ph = vp[v];
                float x;
                switch (vw[v]) {
                    case 0: x = (float) Math.sin(ph * 2 * Math.PI); break;
                    case 1: x = ph < 0.5f ? 0.6f : -0.6f; break;
                    case 2: x = ph < 0.5f ? 4 * ph - 1 : 3 - 4 * ph; break;
                    case 3:
                        noise ^= noise << 13; noise ^= noise >>> 17; noise ^= noise << 5;
                        x = (noise & 0xFFFF) / 32768f - 1;
                        break;
                    default:   // electric piano: sine plus a soft bell partial
                        x = (float) (Math.sin(ph * 2 * Math.PI) + 0.3 * Math.sin(ph * 4 * Math.PI) * va[v] * 8);
                }
                mix += x * va[v];
                va[v] *= vd[v];
                float f = vf[v];
                if (vs[v] != 0) { f *= 1 + vs[v] / RATE * 6; vf[v] = Math.max(20, Math.min(12000, f)); }
                ph += vf[v] / RATE;
                vp[v] = ph - (int) ph;
            }
            fade = Math.min(1, fade + 1f / RATE);   // fade in over a second
            float out = mix * volume * fade * 3;
            out = out / (1 + Math.abs(out));   // soft clip
            buf[n] = (short) (out * 30000);
        }
    }

    private void run() {
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(min, 4096) * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            track.play();
            short[] buf = new short[1024];
            while (running) {
                fill(buf);
                track.write(buf, 0, buf.length);
            }
        } catch (Throwable t) {
            AppLog.w("game music: " + t);
        } finally {
            try { if (track != null) { track.stop(); track.release(); } } catch (Throwable ignored) { }
            track = null;
        }
    }
}
