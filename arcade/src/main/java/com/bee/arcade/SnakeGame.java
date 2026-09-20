package com.bee.arcade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

/**
 * Snake: grid + swipe, wrap-around edges, gliding head-led body with eyes,
 * pulsing food, low-poly board. Reused in the picker menu as a demo.
 */
final class SnakeGame extends BaseGameView {

    private static final int MENU = 0, PLAY = 1, OVER = 2;
    private int screen = MENU;

    private static final int COLS = 15;
    private static final float MARGIN = 12f;
    private static final float CELL = (LW - 2 * MARGIN) / COLS;
    private static final int MAX = COLS * 30;

    private final int[] sx = new int[MAX], sy = new int[MAX];
    private final int[] lx = new int[MAX], ly = new int[MAX];
    private int len, dir, nextDir, fx, fy, score, hi;
    private float tickAcc, intervalMs = 200f;

    private float downX, downY;

    SnakeGame(Context ctx, HomeListener home) {
        super(ctx, home);
        hi = prefs.getInt("s_hi", 0);
    }

    // ── Contract ──────────────────────────────────────────────────────────
    @Override
    void onUpdate(float dt) {
        tickAcc += dt * 1000f;
        while (tickAcc >= intervalMs) {
            tickAcc -= intervalMs;
            step();
            if (screen != PLAY) return;
        }
    }

    @Override
    void drawGame(Canvas c) {
        if (screen == MENU) drawMenu(c);
        else { drawBoard(c); drawHud(c); if (screen == OVER) overlay(c); }
    }

    @Override boolean isLive() { return screen == PLAY; }
    @Override void saveAll() { prefs.edit().putInt("s_hi", Math.max(hi, score)).apply(); }
    @Override boolean hidChrome() { return screen == MENU; }
    @Override int bgColor() { return 0xFF0B1420; }

    // ── Game logic ────────────────────────────────────────────────────────
    private void startGame() {
        len = 3;
        int cy = rows() - 1;
        for (int i = 0; i < len; i++) { sx[i] = 6 - i; sy[i] = cy; lx[i] = sx[i]; ly[i] = sy[i]; }
        dir = 1; nextDir = 1;
        score = 0; tickAcc = 0; intervalMs = 200f;
        spawnFood();
        screen = PLAY;
        sfx.pop();
    }

    private int rows() {
        int r = (int) ((LH - topPad() - 56f) / CELL);
        return Math.max(6, r);
    }

    private void spawnFood() {
        int Rs = rows();
        int cells = COLS * Rs;
        int start = (int) (Math.random() * cells);
        for (int k = 0; k < cells; k++) {
            int idx = (start + k) % cells;
            int gx = idx % COLS, gy = idx / COLS;
            boolean hit = false;
            for (int i = 0; i < len; i++) {
                if (sx[i] == gx && sy[i] == gy) { hit = true; break; }
            }
            if (!hit) { fx = gx; fy = gy; return; }
        }
        saveAll();
        screen = OVER; // board full — a win of sorts
    }

    private void step() {
        if ((nextDir ^ dir) == 2) nextDir = dir;
        dir = nextDir;
        int Rs = rows();
        int nx = (sx[0] + dx(dir) + COLS) % COLS;
        int ny = (sy[0] + dy(dir) + Rs) % Rs;
        boolean ate = nx == fx && ny == fy;
        int lim = ate ? len : len - 1;
        for (int i = 1; i < lim; i++) {
            if (sx[i] == nx && sy[i] == ny) { gameOver(); return; }
        }
        int tailX = sx[len - 1], tailY = sy[len - 1];
        System.arraycopy(sx, 0, lx, 0, len);
        System.arraycopy(sy, 0, ly, 0, len);
        for (int i = len - 1; i > 0; i--) { sx[i] = sx[i - 1]; sy[i] = sy[i - 1]; }
        sx[0] = nx; sy[0] = ny;
        if (ate) {
            sx[len] = tailX; sy[len] = tailY;
            lx[len] = tailX; ly[len] = tailY;
            len++;
            score++;
            sfx.eat(score);
            intervalMs = Math.max(80f, 200f - score * 2f);
            spawnFood();
        }
    }

    private static int dx(int d) { return d == 1 ? 1 : (d == 3 ? -1 : 0); }
    private static int dy(int d) { return d == 2 ? 1 : (d == 0 ? -1 : 0); }

    private void gameOver() {
        saveAll();
        buzz();
        sfx.down();
        screen = OVER;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (super.onTouchEvent(e)) return true;
        float lxX = (e.getX() - ox) / uScale, lxY = (e.getY() - oy) / uScale;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lxX; downY = lxY;
                return true;
            case MotionEvent.ACTION_UP:
                if (screen == OVER) {
                    int b = pressedEndButton(lxX, lxY);
                    if (b == 0) startGame();
                    else if (b == 1) goHome();
                    return true;
                }
                if (screen == MENU) { startGame(); return true; }
                if (screen == PLAY) {
                    float dX = lxX - downX, dY = lxY - downY;
                    float adx = Math.abs(dX), ady = Math.abs(dY);
                    if (Math.max(adx, ady) > 22f) {
                        if (adx > ady) nextDir = dX > 0 ? 1 : 3;
                        else nextDir = dY > 0 ? 2 : 0;
                        sfx.pop();
                    }
                }
                return true;
        }
        return true;
    }

    // ── Renderer ──────────────────────────────────────────────────────────
    private float boardTop() { return topPad() + 46f; }

    private void drawMenu(Canvas c) {
        drawMini(c, LH * 0.55f, 0.9f + 0.1f * (float) Math.sin(frame / 20.0));
        drawMenu(c, "SNAKE", "swipe to steer, eat the food", "best  " + hi, 0xFF3DE24F);
    }

    private void drawMini(Canvas c, float cy, float scale) {
        float t = frame / 30.0f;
        int n = 8;
        float r = 120 * scale;
        for (int i = 0; i < n; i++) {
            float ang = (float) (t + Math.PI * 2 * i / n);
            float x0 = LW / 2 + (float) Math.cos(ang) * r;
            float y0 = cy + (float) Math.sin(ang) * r * 0.5f;
            slot(c, x0, y0, 0xFF1E7F33, false);
        }
        float ang = t;
        float hx = LW / 2 + (float) Math.cos(ang) * r;
        float hy = cy + (float) Math.sin(ang) * r * 0.5f;
        slot(c, hx, hy, 0xFF7CFF6B, true);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0B1420);
        c.drawCircle(hx - 4, hy - 3, 3.2f, p);
        c.drawCircle(hx + 4, hy - 3, 3.2f, p);
    }

    private void drawBoard(Canvas c) {
        float btop = boardTop();
        int Rs = rows();
        float bh = Rs * CELL;

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0F1B2A);
        c.drawRoundRect(MARGIN - 8, btop - 10, LW - MARGIN + 8, btop + bh + 6, 18, 18, p);

        p.setColor(0xFF12202F);
        for (int r = 0; r < Rs; r++) {
            for (int col = (r & 1); col < COLS; col += 2) {
                float x = MARGIN + col * CELL, y = btop + r * CELL;
                c.drawRect(x, y, x + CELL, y + CELL, p);
            }
        }

        float g = Math.min(1f, tickAcc / intervalMs);

        float fc = MARGIN + fx * CELL + CELL / 2;
        float fr = btop + fy * CELL + CELL / 2;
        float pulse = 1f + 0.18f * (float) Math.sin(frame / 9.0);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x28FF5D5D);
        c.drawCircle(fc, fr, CELL * 0.58f * pulse, p);
        p.setColor(0xFFFF5D5D);
        c.drawCircle(fc, fr, CELL * 0.26f * pulse, p);
        p.setColor(0xFFFFB1B1);
        c.drawCircle(fc - CELL * 0.07f, fr - CELL * 0.07f, CELL * 0.08f * pulse, p);

        p.setStyle(Paint.Style.FILL);
        for (int i = len - 1; i >= 0; i--) {
            float x = MARGIN + lerp(lx[i], sx[i], g) * CELL;
            float y = btop + lerp(ly[i], sy[i], g) * CELL;
            int col = i == 0 ? 0xFF7CFF6B
                    : lerpColor(0xFF22863B, 0xFF115C2E, i / (float) len);
            slot(c, x, y, col, i == 0);
        }

        float hx = MARGIN + (lerp(lx[0], sx[0], g) + 0.5f) * CELL;
        float hy = btop + (lerp(ly[0], sy[0], g) + 0.5f) * CELL;
        float edx = dx(dir) * CELL * 0.18f, edy = dy(dir) * CELL * 0.18f;
        float pdx = (dir == 1 || dir == 3) ? CELL * 0.18f : 0;
        float pdy = (dir == 0 || dir == 2) ? CELL * 0.18f : 0;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0B1420);
        c.drawCircle(hx + edx + pdx, hy + edy + pdy, CELL * 0.10f, p);
        c.drawCircle(hx + edx - pdx, hy + edy - pdy, CELL * 0.10f, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(hx + edx + pdx - CELL * 0.03f, hy + edy + pdy - CELL * 0.03f, CELL * 0.04f, p);
        c.drawCircle(hx + edx - pdx - CELL * 0.03f, hy + edy - pdy - CELL * 0.03f, CELL * 0.04f, p);
    }

    private void slot(Canvas c, float cx, float cy, int fill, boolean lead) {
        float pad = CELL * 0.14f;
        float rr = CELL * 0.4f;
        float leadB = lead ? CELL * 0.10f : 0f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0A3A1C);
        c.drawRoundRect(cx + pad - leadB, cy + pad - leadB,
                cx + CELL - pad + leadB, cy + CELL - pad + leadB, rr, rr, p);
        p.setColor(fill);
        c.drawRoundRect(cx + pad, cy + pad,
                cx + CELL - pad, cy + CELL - pad, rr, rr, p);
        p.setColor(0x40FFFFFF);
        c.drawRoundRect(cx + pad + CELL * 0.16f, cy + pad + CELL * 0.16f,
                cx + CELL - pad - CELL * 0.2f, cy + CELL - pad - CELL * 0.52f, rr * 0.6f, rr * 0.6f, p);
    }

    private void drawHud(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(19);
        p.setFakeBoldText(true);
        p.setColor(0xFF3DE24F);
        c.drawText("SCORE  " + score, LW / 2 - 62, topPad() + 30, p);
        p.setColor(0x99CDE8FF);
        p.setTextSize(17);
        p.setFakeBoldText(false);
        c.drawText("BEST  " + Math.max(hi, score), LW / 2 + 62, topPad() + 30, p);
    }

    private void overlay(Canvas c) {
        c.drawColor(0x99000000);
        centerText(c, "GAME OVER", LW / 2, LH * 0.28f, 44, 0xFFFF5D5D);
        centerText(c, "score  " + score, LW / 2, LH * 0.28f + 42, 24, 0xFFCDE8FF);
        centerText(c, "best  " + Math.max(hi, score), LW / 2, LH * 0.28f + 72, 18, 0x99CDE8FF);
        endButtons(c, new String[]{"PLAY AGAIN", "HOME"}, 0xFF3DE24F);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16)
            | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}