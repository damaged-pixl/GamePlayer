package com.bee.arcade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

/**
 * Whac-a-mole — 30 second arcade countdown, up to two moles at once, combo
 * scoring for quick streaks. Tap a hole to whack, tap empty to break your combo.
 */
final class WhacGame extends BaseGameView {

    private static final int MENU = 0, PLAY = 1, OVER = 2;
    private volatile int screen = MENU;

    private static final int GRID = 3, HOLES = GRID * GRID;
    private static final float CELL = 150f;
    private static final float OX = (LW - (GRID - 1) * CELL) / 2f;
    private static final float HOLE_R = 48f;
    private static final float GRID_TOP = 250f;   // first hole row, below the menu title block

    private static final float GAME_TIME = 30f;

    private float time = GAME_TIME;
    private int score, combo, hi;
    private final boolean[] up = new boolean[HOLES];
    private final float[] upT = new float[HOLES];
    private final float[] splat = new float[HOLES];
    private final float[][] pops = new float[8][3];   // x, y, life
    private final String[] popText = new String[8];
    private int popW;
    private float spawnAcc = 0f;

    private float flash;   // time the whole field flashed on last whack

    WhacGame(Context ctx, HomeListener home) {
        super(ctx, home);
        hi = prefs.getInt("w_hi", 0);
    }

    // ── Contract ──────────────────────────────────────────────────────────
    @Override
    void onUpdate(float dt) {
        flash -= dt;
        time -= dt;
        if (time <= 0f) { endGame(); return; }

        for (int i = 0; i < HOLES; i++) {
            if (up[i]) {
                upT[i] -= dt;
                if (upT[i] <= 0f) { up[i] = false; combo = 0; }
            } else if (splat[i] > 0f) {
                splat[i] -= dt;
            }
        }

        int active = 0;
        for (int i = 0; i < HOLES; i++) if (up[i]) active++;
        if (active < 2) {
            spawnAcc -= dt;
            if (spawnAcc <= 0f) {
                spawnAcc = time < GAME_TIME * 0.35f ? 0.5f : 0.85f;
                if (active < 2 && Math.random() < 0.7f) {
                    int h;
                    do { h = (int) (Math.random() * HOLES); } while (up[h]);
                    up[h] = true;
                    upT[h] = 0.9f + 0.3f * (float) Math.random();
                }
            }
        }

        for (int i = 0; i < popW; ) {
            pops[i][2] -= dt;
            if (pops[i][2] <= 0f) {
                pops[i] = pops[--popW];
                popText[i] = popText[popW];
            } else i++;
        }
    }

    @Override
    void drawGame(Canvas c) {
        drawField(c);
        for (int i = 0; i < HOLES; i++) if (up[i]) drawMole(c, i);
        if (screen == MENU) drawMenu(c);
        else {
            drawHud(c);
            if (screen == OVER) { c.drawColor(0x99000000); gameOverPanel(c); }
        }
        drawPops(c);
        for (int i = 0; i < HOLES; i++) if (!up[i] && splat[i] > 0f) drawSplat(c, i);
    }

    @Override boolean isLive() { return screen == PLAY; }
    @Override void saveAll() { prefs.edit().putInt("w_hi", Math.max(hi, score)).apply(); }
    @Override boolean hidChrome() { return screen == MENU; }
    @Override int bgColor() { return 0xFF35582F; }

    // ── Logic ─────────────────────────────────────────────────────────────
    private void startGame() {
        score = combo = 0;
        time = GAME_TIME;
        for (int i = 0; i < HOLES; i++) { up[i] = false; upT[i] = 0; splat[i] = 0; }
        popW = 0;
        spawnAcc = 0f;
        flash = 0;
        screen = PLAY;
        sfx.pop();
    }

    private void endGame() {
        saveAll();
        sfx.win();
        screen = OVER;
    }

    private void whack(int i) {
        if (!up[i]) {
            if (splat[i] > 0f) return;   // tapping a mole you just hit isn't a miss
            combo = 0;
            sfx.bounce();
            return;
        }
        up[i] = false;
        splat[i] = 0.5f;
        flash = 0.14f;
        combo = Math.min(combo + 1, 5);
        int gain = 10 * combo;
        score += gain;
        if (score > hi) hi = score;
        sfx.pluck(combo);
        addPop(i, "+" + gain);
        if (combo >= 3) buzz();
    }

    private void addPop(int i, String s) {
        if (popW == pops.length) return;
        pops[popW][0] = holeX(i);
        pops[popW][1] = holeUp(i) - 44f;
        pops[popW][2] = 0.7f;
        popText[popW++] = s;
    }

    private int holeAt(float lx, float ly) {
        for (int i = 0; i < HOLES; i++) {
            float dx = lx - holeX(i), dy = ly - holeUp(i);
            if (dx * dx + dy * dy <= HOLE_R * HOLE_R) return i;
        }
        return -1;
    }

    private float holeX(int i) { return OX + i % GRID * CELL; }
    private float holeUp(int i) { return topPad() + GRID_TOP + i / GRID * CELL; }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (super.onTouchEvent(e)) return true;
        float lx = (e.getX() - ox) / uScale;
        float ly = (e.getY() - oy) / uScale;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (screen == PLAY) { int h = holeAt(lx, ly); if (h >= 0) whack(h); else { combo = 0; sfx.bounce(); } }
                return true;
            case MotionEvent.ACTION_UP:
                if (screen == OVER) {
                    int b = pressedEndButton(lx, ly);
                    if (b == 0) startGame();
                    else if (b == 1) goHome();
                    return true;
                }
                if (screen == MENU) startGame();
                return true;
        }
        return true;
    }

    // ── Renderer ──────────────────────────────────────────────────────────
    private void drawField(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        float tp = topPad();
        int rows = (int) Math.ceil((LH - tp) / 60f);
        for (int r = 0; r < rows; r++) {
            p.setColor(r % 2 == 0 ? 0xFF3D6633 : 0xFF426D37);
            c.drawRect(0, tp + r * 60f, LW, Math.min(LH, tp + (r + 1) * 60f), p);
        }
        float s = HOLE_R / 40f;
        for (int i = 0; i < HOLES; i++) {
            float x = holeX(i), y = holeUp(i);
            p.setColor(0xFF2A1B0F);
            c.drawOval(x - 32 * s, y - 8 * s, x + 32 * s, y + 26 * s, p);
            p.setColor(0xFF5A3E26);
            c.drawOval(x - 26 * s, y - 3 * s, x + 26 * s, y + 20 * s, p);
        }
    }

    private void drawMole(Canvas c, int i) {
        float x = holeX(i), y = holeUp(i);
        float s = HOLE_R / 40f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF7A5230);
        c.drawCircle(x, y - 18 * s, 21 * s, p);
        p.setColor(0xFFC79A63);
        c.drawRoundRect(x - 19 * s, y - 6 * s, x + 19 * s, y + 18 * s, 10 * s, 10 * s, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x - 8 * s, y - 22 * s, 5.5f * s, p);
        c.drawCircle(x + 8 * s, y - 22 * s, 5.5f * s, p);
        p.setColor(0xFF0A0A0A);
        c.drawCircle(x - 8 * s, y - 22 * s, 2.6f * s, p);
        c.drawCircle(x + 8 * s, y - 22 * s, 2.6f * s, p);
        p.setColor(0xFFF087A6);
        c.drawOval(x - 6 * s, y - 15 * s, x + 6 * s, y - 7 * s, p);
    }

    private void drawSplat(Canvas c, int i) {
        float x = holeX(i), y = holeUp(i);
        p.setStrokeWidth(3);
        p.setColor(0xFFFFD84F);
        for (int k = 0; k < 5; k++) {
            double a = k / 5.0 * Math.PI * 2 + 0.785;
            c.drawLine(x, y - 16, x + (float) Math.cos(a) * 14, y - 16 + (float) Math.sin(a) * 10, p);
        }
        p.setStrokeWidth(0);
    }

    private void drawPops(Canvas c) {
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(22);
        p.setFakeBoldText(true);
        for (int i = 0; i < popW; i++) {
            float[] f = pops[i];
            int a = (int) Math.max(0, Math.min(255, f[2] / 0.7f * 255));
            p.setColor(a << 24 | 0xFFFFD84F);
            c.drawText(popText[i], f[0], f[1], p);
        }
        p.setFakeBoldText(false);
    }

    private void drawHud(Canvas c) {
        float tp = topPad();
        chip(c, 18, tp + 46, 92, "SCORE", score);
        chip(c, LW - 110, tp + 46, 92, "BEST", hi);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55FFFFFF);
        c.drawRoundRect(LW / 2f - 100, tp + 12, LW / 2f + 100, tp + 22, 5, 5, p);
        p.setColor(flash > 0 ? 0xFFFFD84F : 0xFF7CDE5B);
        float frac = Math.max(0f, time / GAME_TIME);
        c.drawRoundRect(LW / 2f - 96, tp + 14, LW / 2f - 96 + 192 * frac, tp + 20, 3, 3, p);

        if (combo > 1) {
            float a = 0.7f + 0.3f * (float) Math.sin(frame / 5.0);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(18);
            p.setFakeBoldText(true);
            p.setColor(((int) (a * 255)) << 24 | 0xFFFFD84F);
            c.drawText("COMBO x" + combo, LW / 2f, tp + 46, p);
            p.setFakeBoldText(false);
        }
    }

    private void drawMenu(Canvas c) {
        drawMenu(c, "WHAC-A-MOLE", "tap moles, chain combos, beat the clock",
                "best  " + hi, 0xFFFFD84F);
    }

    private void gameOverPanel(Canvas c) {
        centerText(c, "TIME UP!", LW / 2, LH * 0.28f, 38, 0xFFFFD84F);
        centerText(c, "score  " + score, LW / 2, LH * 0.28f + 42, 24, 0xFFD7E8FF);
        centerText(c, "best   " + Math.max(hi, score), LW / 2, LH * 0.28f + 72, 18, 0x99D7E8FF);
        endButtons(c, new String[]{"PLAY AGAIN", "HOME"}, 0xFFFF9E2E);
    }
}