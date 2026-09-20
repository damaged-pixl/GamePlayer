package com.bee.arcade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.view.MotionEvent;

/**
 * Flappy: one-tap flap, constant pipe speed, high score. Physics and drawing
 * identical to the standalone TinyGames build.
 */
final class FlappyGame extends BaseGameView {

    private static final int MENU = 0, PLAY = 1, OVER = 2;
    private volatile int screen = MENU;

    private static final float GRAV = 1200f;
    private static final float FLAP = -430f;
    private static final float MAXFALL = 760f;
    private static final float SPEED = 150f;
    private static final float PIPE_W = 66f;
    private static final float GAP_H = 160f;
    private static final float SPACING = 230f;
    private static final float BIRD_X = 140f;
    private static final float BIRD_R = 13f;
    private static final float GROUND_Y = LH - 58f;

    private float ceilY, birdY, vy;

    private final float[] pipeX = new float[12];
    private final float[] pipeCy = new float[12];
    private final boolean[] pipePassed = new boolean[12];
    private int pipeCount;
    private float pipeTimer;

    private int score, hi;
    private long lastFlapMs;

    private final float[][] stars = new float[12][2];

    FlappyGame(Context ctx, HomeListener home) {
        super(ctx, home);
        hi = prefs.getInt("f_hi", 0);
        for (int i = 0; i < stars.length; i++) {
            stars[i][0] = (float) Math.random() * LW;
            stars[i][1] = (float) Math.random() * LH * 0.7f;
        }
    }

    // ── Contract ──────────────────────────────────────────────────────────
    @Override
    void onUpdate(float dt) {
        vy = Math.min(vy + GRAV * dt, MAXFALL);
        birdY += vy * dt;
        if (birdY - BIRD_R <= ceilY) { birdY = ceilY + BIRD_R; vy = 0f; }
        if (birdY >= GROUND_Y - BIRD_R) { birdY = GROUND_Y - BIRD_R; die(); return; }

        pipeTimer += dt;
        float interval = SPACING / SPEED;
        while (pipeTimer >= interval && pipeCount < pipeX.length) {
            pipeTimer -= interval;
            spawnPipe();
        }
        for (int i = 0; i < pipeCount; i++) {
            pipeX[i] -= SPEED * dt;
            if (!pipePassed[i] && pipeX[i] + PIPE_W < BIRD_X - BIRD_R) {
                pipePassed[i] = true;
                score++;
                sfx.eat(score);
                if (score > hi) hi = score;
            }
        }
        int w = 0;
        for (int i = 0; i < pipeCount; i++) {
            if (pipeX[i] + PIPE_W > 0) {
                pipeX[w] = pipeX[i];
                pipeCy[w] = pipeCy[i];
                pipePassed[w] = pipePassed[i];
                w++;
            }
        }
        pipeCount = w;

        for (int i = 0; i < pipeCount; i++) {
            if (hitPipe(i)) { die(); return; }
        }
    }

    @Override
    void drawGame(Canvas c) {
        ceilY = topPad() + 14f;
        if (screen == MENU) {
            birdY = LH * 0.38f + (float) Math.sin(frame / 15.0) * 7f;
            vy = (float) Math.cos(frame / 15.0) * 60f;
        }
        drawSky(c);
        if (screen != MENU) drawPipes(c);
        drawBird(c);
        drawGround(c);

        if (screen == MENU) {
            drawMenu(c, "FLAPPY", "one tap to flap", "best  " + hi, 0xFFFFE15A);
        } else {
            drawHud(c);
            if (screen == OVER) {
                c.drawColor(0x99000000);
                centerText(c, "GAME OVER", LW / 2, LH * 0.28f, 46, 0xFFFF8C5A);
                centerText(c, "score  " + score, LW / 2, LH * 0.28f + 42, 26, 0xFFD7E8FF);
                centerText(c, "best   " + hi, LW / 2, LH * 0.28f + 72, 20, 0x99D7E8FF);
                endButtons(c, new String[]{"PLAY AGAIN", "HOME"}, 0xFFFFE15A);
            }
        }
    }

    @Override boolean isLive() { return screen == PLAY; }
    @Override void saveAll() { prefs.edit().putInt("f_hi", Math.max(hi, score)).apply(); }
    @Override boolean hidChrome() { return screen == MENU; }
    @Override int bgColor() { return 0xFF0B1830; }

    // ── Game logic ────────────────────────────────────────────────────────
    private void startGame() {
        ceilY = topPad() + 14f;
        birdY = LH * 0.38f;
        vy = 0;
        score = 0;
        pipeCount = 0;
        pipeTimer = 0f;
        screen = PLAY;
        sfx.blip();
    }

    private void spawnPipe() {
        float topEdge = ceilY + 24f;
        float bottomEdge = GROUND_Y - 24f;
        float cy = topEdge + GAP_H / 2f + (float) Math.random() * (bottomEdge - topEdge - GAP_H);
        pipeX[pipeCount] = LW + 10f;
        pipeCy[pipeCount] = cy;
        pipePassed[pipeCount] = false;
        pipeCount++;
    }

    private boolean hitPipe(int i) {
        float left = pipeX[i];
        float top = pipeCy[i] - GAP_H / 2f;
        return circleRect(BIRD_X, birdY, BIRD_R, left, ceilY, left + PIPE_W, top)
            || circleRect(BIRD_X, birdY, BIRD_R, left, top + GAP_H, left + PIPE_W, GROUND_Y);
    }

    private static boolean circleRect(float cx, float cy, float r, float rx, float ry, float rx2, float ry2) {
        float nx = Math.max(rx, Math.min(cx, rx2));
        float ny = Math.max(ry, Math.min(cy, ry2));
        float dx = cx - nx, dy = cy - ny;
        return dx * dx + dy * dy < r * r;
    }

    private void die() {
        saveAll();
        buzz();
        sfx.down();
        screen = OVER;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (super.onTouchEvent(e)) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // flap fires on touch-down (responsive); pause chrome is handled
                // in super.onTouchEvent, so this is only true game area taps.
                if (screen == PLAY) {
                    if (System.currentTimeMillis() - lastFlapMs > 80) {
                        lastFlapMs = System.currentTimeMillis();
                        vy = FLAP;
                        sfx.blip();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (screen == OVER) {
                    float lx = (e.getX() - ox) / uScale, ly = (e.getY() - oy) / uScale;
                    int b = pressedEndButton(lx, ly);
                    if (b == 0) startGame();
                    else if (b == 1) goHome();
                    return true;
                }
                if (screen == MENU) { startGame(); return true; }
                return true;
        }
        return true;
    }

    // ── Renderer ──────────────────────────────────────────────────────────
    private void drawSky(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, 0, LH, 0xFF0B1830, 0xFF14345C, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, LW, LH, p);
        p.setShader(null);
        p.setColor(0x66FFFFFF);
        for (float[] s : stars) c.drawCircle(s[0], s[1], 1.6f, p);
    }

    private void drawGround(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF132A40);
        c.drawRect(0, GROUND_Y, LW, LH, p);
        p.setColor(0xFF3AB85F);
        c.drawRect(0, GROUND_Y, LW, GROUND_Y + 5f, p);
        p.setColor(0xFF0E2032);
        for (int x = 0; x < LW / 24f; x++) {
            for (int y = 0; y < 2; y++) {
                float r = GROUND_Y + 10f + y * 22f;
                if ((x + y) % 2 == 0) c.drawRect(x * 24f, r, x * 24f + 12f, r + 12f, p);
            }
        }
    }

    private void drawPipes(Canvas c) {
        for (int i = 0; i < pipeCount; i++) {
            float x = pipeX[i];
            float top = pipeCy[i] - GAP_H / 2f;
            float bot = pipeCy[i] + GAP_H / 2f;
            pipe(c, x, ceilY, x + PIPE_W, top, true);
            pipe(c, x, bot, x + PIPE_W, GROUND_Y, false);
        }
    }

    private void pipe(Canvas c, float x1, float y1, float x2, float y2, boolean top) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF1F8A41);
        c.drawRect(x1, y1, x2, y2, p);
        float capX1 = x1 - 5, capX2 = x2 + 5, capH = 22;
        p.setColor(0xFF50C674);
        if (top) c.drawRoundRect(capX1, y2 - capH, capX2, y2, 4, 4, p);
        else c.drawRoundRect(capX1, y1, capX2, y1 + capH, 4, 4, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0xFF145C2B);
        c.drawRect(x1, y1, x2, y2, p);
    }

    private void drawBird(Canvas c) {
        float ang = Math.max(-25f, Math.min(75f, vy * 0.07f));
        c.save();
        c.translate(BIRD_X, birdY);
        c.rotate(ang);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFE15A);
        c.drawOval(-BIRD_R, -BIRD_R * 0.92f, BIRD_R, BIRD_R * 0.92f, p);
        p.setColor(0xFFF0C94D);
        c.drawOval(-BIRD_R + 2f, 2f, BIRD_R - 1f, BIRD_R * 0.7f + 3f, p);

        p.setColor(0xFFFFFFFF);
        c.drawCircle(4f, -5f, 5f, p);
        p.setColor(0xFF0E2530);
        c.drawCircle(5.4f, -5f, 2.6f, p);

        p.setColor(0xFFFF8C42);
        c.drawCircle(BIRD_R + 3f, 1f, 4.2f, p);

        c.restore();
    }

    private void drawHud(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(44);
        p.setFakeBoldText(true);
        p.setColor(0xEEFFFFFF);
        c.drawText(String.valueOf(score), LW / 2, topPad() + 56, p);
        p.setFakeBoldText(false);
        p.setTextSize(16);
        p.setColor(0x99D7E8FF);
        c.drawText("best " + hi, LW / 2, topPad() + 78, p);
    }
}