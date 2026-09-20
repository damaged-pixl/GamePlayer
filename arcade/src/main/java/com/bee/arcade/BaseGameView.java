package com.bee.arcade;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowInsets;

/**
 * Shared game shell: game loop, uniform logical scale (LW x LH letterboxed to
 * the real screen), status-bar cutout insets, central home + pause buttons,
 * pause overlay, procedural audio, vibration, prefs.
 *
 * Subclasses fill in a game: update/draw on a logical canvas, plus their own
 * touch handling (delegated to them wherever the base chrome didn't consume it).
 */
public abstract class BaseGameView extends SurfaceView implements SurfaceHolder.Callback {

    static final float LW = 390f, LH = 700f;

    interface HomeListener { void goHome(); }

    final Sfx sfx = new Sfx();
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final SharedPreferences prefs;
    final Vibrator vibrator;

    float uScale = 1f, ox, oy;
    int topInsetPx;
    volatile boolean paused;
    long frame;
    private boolean resumeOnUp;

    private final HomeListener home;
    private final RectF homeBtn = new RectF();
    private final RectF pauseBtn = new RectF();

    private Thread thread;
    private volatile boolean running;

    BaseGameView(Context ctx, HomeListener home) {
        super(ctx);
        this.home = home;
        prefs = ctx.getSharedPreferences("arcade", Context.MODE_PRIVATE);
        vibrator = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        getHolder().addCallback(this);
        setFocusable(true);
    }

    // ── Subclass contract ─────────────────────────────────────────────────
    abstract void onUpdate(float dt);
    abstract void drawGame(Canvas c);
    /** True while a game is actually in progress (not menu / game-over). */
    abstract boolean isLive();
    /** Persist scores. */
    abstract void saveAll();

    /** Hide home/pause chrome on menu screens. */
    boolean hidChrome() { return false; }
    int bgColor() { return 0xFF0B1420; }

    void buzz() {
        try { vibrator.vibrate(VibrationEffect.createOneShot(70, 140)); } catch (Exception ignored) {}
    }

    // ── Cutout insets (max-only to avoid jitter while bars hide) ──────────
    @Override
    public android.view.WindowInsets onApplyWindowInsets(android.view.WindowInsets insets) {
        int top = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            top = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()).top;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            top = insets.getDisplayCutout() != null ? insets.getDisplayCutout().getSafeInsetTop() : 0;
        }
        if (top > topInsetPx) topInsetPx = top;
        return super.onApplyWindowInsets(insets);
    }

    // ── Surface lifecycle ─────────────────────────────────────────────────
    @Override
    public void surfaceCreated(SurfaceHolder h) {
        running = true;
        thread = new Thread(this::loop, "GameThread");
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int fmt, int w, int hpx) {
        uScale = Math.min(w / LW, hpx / LH);
        ox = (w - LW * uScale) / 2f;
        oy = (hpx - LH * uScale) / 2f;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        running = false;
        try { if (thread != null) thread.join(); } catch (InterruptedException ignored) {}
        saveAll();
    }

    /** Foreground activity lost focus — freeze the sim, bank scores. */
    public void activityPause() {
        if (isLive()) paused = true;
        resumeOnUp = false;
        saveAll();
    }

    public void activityResume() {}

    private void loop() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min((now - last) / 1e9f, 0.05f);
            last = now;
            frame++;
            if (!paused && isLive()) onUpdate(dt);
            Canvas c = getHolder().lockCanvas();
            if (c != null) {
                try { render(c); } finally { getHolder().unlockCanvasAndPost(c); }
            }
            long elapsed = (System.nanoTime() - now) / 1_000_000L;
            long sleep = (1000L / 60) - elapsed;
            if (sleep > 1) {
                try { Thread.sleep(sleep); } catch (InterruptedException ignored) { break; }
            }
        }
    }

    float topPad() { return topInsetPx / Math.max(1f, uScale); }

    private void render(Canvas c) {
        c.drawColor(bgColor());
        c.save();
        c.translate(ox, oy);
        c.scale(uScale, uScale);
        drawGame(c);
        if (!hidChrome()) drawChrome(c);
        if (paused) {
            c.drawColor(0x99000000);
            centerText(c, "PAUSED", LW / 2, LH * 0.42f, 46, 0xFFFFFFFF);
            centerText(c, "tap to resume", LW / 2, LH * 0.42f + 38, 22, 0xBBFFFFFF);
        }
        c.restore();
    }

    void centerText(Canvas c, String s, float x, float y, float size, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size);
        p.setColor(color);
        p.setFakeBoldText(true);
        c.drawText(s, x, y, p);
        p.setFakeBoldText(false);
    }

    /** Shared menu template — every game uses the same layout: title block at
     *  LH*0.28, a big demo band in the middle, pulsing TAP TO START at the
     *  bottom (LH*0.88). Call AFTER drawing the game's demo/field so the text
     *  overlays it. */
    void drawMenu(Canvas c, String title, String subtitle, String best, int accent) {
        centerText(c, title, LW / 2, LH * 0.28f, 60, accent);
        if (subtitle != null) centerText(c, subtitle, LW / 2, LH * 0.28f + 40, 18, 0xBBFFFFFF);
        if (best != null) centerText(c, best, LW / 2, LH * 0.28f + 66, 18, accent);
        float a = 0.55f + 0.35f * (float) Math.sin(frame / 20.0);
        centerText(c, "TAP TO START", LW / 2, LH * 0.88f, 26, ((int) (a * 255)) << 24 | 0xFFFFFFFF);
    }

    /** Shared HUD chip: dark rounded rect, small label over bold value. */
    void chip(Canvas c, float x, float y, float w, String label, int value) {
        chip(c, x, y, w, label, String.valueOf(value));
    }

    void chip(Canvas c, float x, float y, float w, String label, String value) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF251F19);
        c.drawRoundRect(x, y, x + w, y + 34, 14, 14, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(12);
        p.setColor(0x88F9F6F2);
        c.drawText(label, x + w / 2, y + 15, p);
        p.setTextSize(19);
        p.setColor(0xFFF9F6F2);
        c.drawText(value, x + w / 2, y + 30, p);
    }

    // ── End-of-game overlay buttons: vertical stack, tap to pick ───────────
    private final RectF[] endBtn = {new RectF(), new RectF(), new RectF()};
    private int endBtnN;

    /** Draw 1-3 stacked end-of-game buttons centred at LH*0.62. Call each frame
     *  the overlay is visible so the hit rects track the layout. */
    int endButtons(Canvas c, String[] labels, int accent) {
        endBtnN = Math.min(labels.length, endBtn.length);
        float bw = 216f, bh = 48f, gap = 14f;
        float x0 = (LW - bw) / 2f;
        float h = endBtnN * bh + (endBtnN - 1) * gap;
        float top = LH * 0.62f - h / 2f;
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < endBtnN; i++) {
            endBtn[i].set(x0, top + i * (bh + gap), x0 + bw, top + i * (bh + gap) + bh);
            p.setColor(accent);
            c.drawRoundRect(endBtn[i], 16, 16, p);
            p.setColor(0x28FFFFFF);
            c.drawRoundRect(endBtn[i].left + 5, endBtn[i].top + 3,
                    endBtn[i].right - 5, endBtn[i].top + bh * 0.45f, 12, 12, p);
            p.setStrokeWidth(2);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0x66FFFFFF);
            c.drawRoundRect(endBtn[i], 16, 16, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeWidth(0);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(19);
            p.setFakeBoldText(true);
            p.setColor(0xFFFFFFFF);
            c.drawText(labels[i], x0 + bw / 2, endBtn[i].centerY() + 7, p);
            p.setFakeBoldText(false);
        }
        return endBtnN;
    }

    /** Which end-of-game button was tapped, in [0, endBtnN), or -1. */
    int pressedEndButton(float lx, float ly) {
        for (int i = 0; i < endBtnN; i++) {
            if (endBtn[i].contains(lx, ly)) return i;
        }
        return -1;
    }

    // ── Chrome: home (top-left), pause (top-right) ────────────────────────
    private void drawChrome(Canvas c) {
        float tp = topPad();
        homeBtn.set(14, tp + 8, 52, tp + 40);
        pauseBtn.set(LW - 52, tp + 8, LW - 14, tp + 40);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33FFFFFF);
        c.drawRoundRect(homeBtn, 7, 7, p);
        if (isLive()) c.drawRoundRect(pauseBtn, 7, 7, p);

        // home glyph: little house
        p.setColor(0xEEFFFFFF);
        float x0 = homeBtn.left + 7, y0 = homeBtn.bottom - 7, tw = 10, th = 8;
        p.setStyle(Paint.Style.FILL);
        Path roof = new Path();
        roof.moveTo(x0 - 4, y0 - th);
        roof.lineTo(x0 + tw / 2f, y0 - th - 8);
        roof.lineTo(x0 + tw + 4, y0 - th);
        roof.close();
        c.drawPath(roof, p);
        c.drawRect(x0, y0 - th, x0 + tw, y0, p);

        // pause glyph — only when there's something to pause
        if (isLive()) {
            float by = (pauseBtn.top + pauseBtn.bottom) / 2f;
            c.drawRoundRect(pauseBtn.left + 7, by - 8, pauseBtn.left + 13, by + 8, 2.5f, 2.5f, p);
            c.drawRoundRect(pauseBtn.right - 13, by - 8, pauseBtn.right - 7, by + 8, 2.5f, 2.5f, p);
        }
    }

    // ── Input: chrome first, delegate the rest to subclasses ──────────────
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float lx = (e.getX() - ox) / uScale;
        float ly = (e.getY() - oy) / uScale;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // While paused, only UP decides — don't re-arm the flag here or
                // the resume tap gets swallowed and pause becomes un-resumable.
                if (paused) return true;
                if (!hidChrome() && homeBtn.contains(lx, ly)) { goHome(); return true; }
                if (isLive() && !hidChrome() && pauseBtn.contains(lx, ly)) {
                    paused = true; resumeOnUp = true; sfx.pop(); return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
                if (paused) {
                    if (resumeOnUp) { resumeOnUp = false; return true; }
                    paused = false;
                    sfx.pop();
                    return true;
                }
                return false;
        }
        return false;
    }

    void goHome() { home.goHome(); }
}