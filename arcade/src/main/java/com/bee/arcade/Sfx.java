package com.bee.arcade;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Procedural audio shared by all four games. Soft sine/pluck only, attack+release
 * envelopes, serialised playback on one background thread at a time, throttled
 * so rapid events never stack and blow the speakers.
 */
final class Sfx {

    static final Object LOCK = new Object();
    private static final double[] NOTE = {523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.7};
    private long lastAt;

    /** Gentle UI tick / pick sound. */
    void pop()        { tone(880.0,  880.0, 40,  0.08f); }
    /** Snake eat / bonus pickup. Pitch climbs slightly with combo. */
    void eat(int n)   { tone(NOTE[n % NOTE.length], NOTE[n % NOTE.length], 90, 0.12f); }
    /** Soft bounce / flap-style blip. */
    void blip()       { tone(480.0,  260.0, 55,  0.09f); }
    /** Ball/paddle bounce in Sink Break. */
    void bounce()     { tone(300.0,  180.0, 50,  0.08f); }
    /** Brick hit in Sink Break; higher pitch the closer the brick sits to the deck. */
    void brick(int row) { double f = NOTE[Math.max(0, Math.min(NOTE.length - 1, row))];
                          tone(f, f * 1.02, 70, 0.12f); }
    /** Merge / upgrade chime. */
    void pluck(int n) { tone(NOTE[n % NOTE.length], NOTE[n % NOTE.length] * 1.5, 120, 0.13f); }
    /** Life lost / failure sweep. */
    void down()       { tone(420.0,  65.0,  420, 0.18f); }
    /** Victory arpeggio — three rising notes in one serialised burst. */
    void win() {
        if (System.currentTimeMillis() - lastAt < 24) return;
        lastAt = System.currentTimeMillis();
        new Thread(() -> {
            synchronized (LOCK) {
                try {
                    int sr = 22050;
                    double[] fs = {523.25, 659.25, 783.99};
                    int[] ms = {90, 90, 170};
                    float[] vs = {0.13f, 0.13f, 0.15f};
                    int gap = sr * 60 / 1000;
                    int total = 0;
                    for (int k = 0; k < fs.length; k++) total += ms[k] * sr / 1000 + gap;
                    short[] pcm = new short[total];
                    int at = 0;
                    for (int k = 0; k < fs.length; k++) {
                        int n = ms[k] * sr / 1000;
                        int atk = Math.max(3, n / 25), rel = Math.max(4, n / 6);
                        for (int i = 0; i < n; i++) {
                            double env = 1.0;
                            if (i < atk) env = i / (double) atk;
                            if (i > n - rel) env = (n - i) / (double) rel;
                            pcm[at++] = (short) (Math.sin(2 * Math.PI * fs[k] * i / sr) * env * vs[k] * 0.30 * Short.MAX_VALUE);
                        }
                        for (int i = 0; i < gap; i++) pcm[at++] = 0;
                    }
                    play(pcm, sr);
                } catch (Exception ignored) {}
            }
        }, "sfx").start();
    }

    private void tone(final double f0, final double f1, final int durMs, final float vol) {
        if (System.currentTimeMillis() - lastAt < 24) return;
        lastAt = System.currentTimeMillis();
        new Thread(() -> {
            synchronized (LOCK) {
                try {
                    int sr = 22050;
                    int n = (int) (durMs * sr / 1000.0);
                    short[] pcm = new short[n];
                    int atk = Math.max(3, n / 25);
                    int rel = Math.max(4, n / 6);
                    for (int i = 0; i < n; i++) {
                        double t = i / (double) sr;
                        double f = f0 + (f1 - f0) * (i / (double) n);
                        double env = 1.0;
                        if (i < atk) env = i / (double) atk;
                        if (i > n - rel) env = (n - i) / (double) rel;
                        double s = Math.sin(2 * Math.PI * f * t) * env * vol * 0.30;
                        s = Math.max(-1.0, Math.min(1.0, s));
                        pcm[i] = (short) (s * Short.MAX_VALUE);
                    }
                    play(pcm, sr);
                } catch (Exception ignored) {}
            }
        }, "sfx").start();
    }

    private void play(short[] pcm, int sr) {
        try {
            AudioTrack tr = new AudioTrack(
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build(),
                new AudioFormat.Builder().setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
                pcm.length * 2, AudioTrack.MODE_STATIC, 0);
            tr.write(pcm, 0, pcm.length);
            tr.play();
            Thread.sleep((long) (pcm.length * 1000L / sr) + 20);
            tr.stop();
            tr.release();
        } catch (Exception ignored) {}
    }
}