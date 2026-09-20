package com.bee.arcade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;

/**
 * Memory — classic pairs game on a 4×4 board. Flip two cards per turn; find
 * all 8 pairs in the fewest moves. Best score is the lowest move count.
 */
final class MemoryGame extends BaseGameView {

    private static final int MENU = 0, PLAY = 1, OVER = 2;
    private int screen = MENU;

    private static final int N = 16, SIDE = 4;
    private static final float W = 84f, H = 104f, GAP = 10f, STEP = W + GAP;
    private static final float GX = (LW - (SIDE * (W + GAP) - GAP)) / 2f;
    // centre the board in the space below the HUD so the play area fills the canvas
    private static final float GY = (LH - (SIDE * H + (SIDE - 1) * GAP) - 40f) / 2f;

    private static final int[] PAL = {
        0xFFE4572E, 0xFFE78A2C, 0xFFE0B91E, 0xFF4CAF50,
        0xFF17A2B8, 0xFF2F6FD0, 0xFF6C5CE7, 0xFFD85FC7
    };

    private final int[] val = new int[N];
    private final boolean[] matched = new boolean[N];
    private int f1 = -1, f2 = -1;
    private float flipWait;
    private int moves, hi;

    private final Path tri = new Path();
    private final Path dia = new Path();

    MemoryGame(Context ctx, HomeListener home) {
        super(ctx, home);
        hi = prefs.getInt("m_hi", 0);
        newDeck();
    }

    // ── Contract ──────────────────────────────────────────────────────────
    @Override
    void onUpdate(float dt) {
        if (flipWait > 0f) {
            flipWait -= dt;
            if (flipWait <= 0f) { f1 = -1; f2 = -1; }
        }
    }

    @Override
    void drawGame(Canvas c) {
        boardPanel(c);
        for (int i = 0; i < N; i++) drawCard(c, i);
        if (screen == MENU) drawMenu(c);
        else {
            drawHud(c);
            if (screen == OVER) { c.drawColor(0x99000000); gameOverPanel(c); }
        }
    }

    @Override boolean isLive() { return screen == PLAY; }
    @Override void saveAll() { prefs.edit().putInt("m_hi", hi).apply(); }
    @Override boolean hidChrome() { return screen == MENU; }
    @Override int bgColor() { return 0xFF171E33; }

    // ── Logic ─────────────────────────────────────────────────────────────
    private void newDeck() {
        int[] base = {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7};
        for (int i = N - 1; i > 0; i--) {
            int j = (int) (Math.random() * (i + 1));
            int t = base[i]; base[i] = base[j]; base[j] = t;
        }
        System.arraycopy(base, 0, val, 0, N);
    }

    private void startGame() {
        newDeck();
        for (int i = 0; i < N; i++) matched[i] = false;
        f1 = f2 = -1;
        flipWait = 0;
        moves = 0;
        screen = PLAY;
        sfx.pop();
    }

    private void won() {
        hi = hi == 0 ? moves : Math.min(hi, moves);
        saveAll();
        sfx.win();
        buzz();
        screen = OVER;
    }

    private int cardAt(float lx, float ly) {
        int col = (int) ((lx - GX) / STEP);
        int row = (int) ((ly - (topPad() + GY)) / STEP);
        if (col < 0 || col >= SIDE || row < 0 || row >= SIDE) return -1;
        int i = row * SIDE + col;
        return i;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (super.onTouchEvent(e)) return true;
        float lx = (e.getX() - ox) / uScale;
        float ly = (e.getY() - oy) / uScale;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (screen == PLAY && flipWait <= 0f) {
                    int idx = cardAt(lx, ly);
                    if (idx < 0 || matched[idx]) return true;
                    if (f1 < 0) { f1 = idx; sfx.bounce(); return true; }
                    if (f2 >= 0) return true;
                    f2 = idx;
                    moves++;
                    if (val[f1] == val[f2]) {
                        matched[f1] = matched[f2] = true;
                        sfx.pluck(val[f2] + 2);
                        f1 = f2 = -1;
                        boolean all = true;
                        for (int i = 0; i < N; i++) if (!matched[i]) { all = false; break; }
                        if (all) won();
                    } else {
                        flipWait = 0.9f;
                        sfx.bounce();
                    }
                }
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
    private void boardPanel(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF10182E);
        c.drawRoundRect(GX - 12, topPad() + GY - 38, GX + SIDE * STEP - GAP + 12,
                topPad() + GY + SIDE * STEP - GAP + 16, 18, 18, p);
    }

    private void drawCard(Canvas c, int i) {
        float col = i % SIDE, row = i / SIDE;
        float x = GX + col * STEP, y = topPad() + GY + row * STEP;
        p.setStyle(Paint.Style.FILL);
        boolean shown = matched[i] || i == f1 || i == f2;

        p.setColor(0xFF0A101E);
        c.drawRoundRect(x + 2, y + 3, x + W + 2, y + H + 3, 9, 9, p);
        p.setColor(shown ? PAL[val[i]] : 0xFF2B4B63);
        c.drawRoundRect(x, y, x + W, y + H, 9, 9, p);

        if (shown) {
            int v = val[i];
            p.setColor(0xFFFFFDF6);
            float s = W / 74f;   // scale symbols up with the bigger cards
            if (s != 1f) {
                c.save();
                c.translate(x + W / 2f, y + H / 2f - 2);
                c.scale(s, s);
                c.translate(-(x + W / 2f), -(y + H / 2f - 2));
            }
            drawSymbol(c, x + W / 2f, y + H / 2f - 2, v, PAL[v]);
            if (s != 1f) c.restore();
        } else {
            p.setColor(0x44FFFFFF);
            c.drawRoundRect(x + 3, y + 3, x + W - 8, y + H * 0.38f, 6, 6, p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(38);
            p.setFakeBoldText(true);
            p.setColor(0xFFCFE4F7);
            c.drawText("?", x + W / 2f, y + H / 2f + 12, p);
            p.setFakeBoldText(false);
        }

        if (matched[i]) {
            p.setStrokeWidth(4);
            p.setColor(0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            float cx = x + W / 2f, cy = y + H / 2f;
            c.drawLine(cx - 12, cy, cx - 4, cy + 9, p);
            c.drawLine(cx - 4, cy + 9, cx + 13, cy - 9, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawSymbol(Canvas c, float cx, float cy, int v, int base) {
        p.setStyle(Paint.Style.FILL);
        switch (v) {
            case 0: p.setColor(0xFFFFFDF6); c.drawCircle(cx, cy, 15, p); break;
            case 1: p.setColor(0xFF303A50); c.drawRoundRect(cx - 13, cy - 13, cx + 13, cy + 13, 5, 5, p); break;
            case 2:
                tri.reset(); tri.moveTo(cx, cy - 16); tri.lineTo(cx - 15, cy + 12); tri.lineTo(cx + 15, cy + 12); tri.close();
                p.setColor(0xFFFFFDF6); c.drawPath(tri, p); break;
            case 3:
                dia.reset(); dia.moveTo(cx, cy - 15); dia.lineTo(cx + 13, cy); dia.lineTo(cx, cy + 15); dia.lineTo(cx - 13, cy); dia.close();
                p.setColor(0xFFFFFDF6); c.drawPath(dia, p); break;
            case 4:
                p.setColor(0xFF303A50);
                c.drawRoundRect(cx - 3, cy - 15, cx + 3, cy + 15, 2, 2, p);
                c.drawRoundRect(cx - 15, cy - 3, cx + 15, cy + 3, 2, 2, p);
                break;
            case 5:
                p.setColor(0xFFFFFDF6);
                c.drawCircle(cx - 7, cy - 4, 7.5f, p);
                c.drawCircle(cx + 7, cy - 4, 7.5f, p);
                tri.reset(); tri.moveTo(cx - 14, cy - 1); tri.lineTo(cx + 14, cy - 1); tri.lineTo(cx, cy + 15); tri.close();
                c.drawPath(tri, p);
                break;
            case 6:
                p.setColor(0xFF303A50);
                c.drawCircle(cx, cy, 15, p);
                p.setColor(base);
                c.drawCircle(cx, cy, 7.5f, p);
                break;
            default:
                p.setColor(0xFFFFFDF6);
                c.drawRoundRect(cx - 11, cy - 15, cx - 5, cy + 15, 2, 2, p);
                c.drawRoundRect(cx - 3, cy - 15, cx + 3, cy + 15, 2, 2, p);
                c.drawRoundRect(cx + 5, cy - 15, cx + 11, cy + 15, 2, 2, p);
                break;
        }
    }

    private void drawHud(Canvas c) {
        float tp = topPad();
        chip(c, 18, tp + 46, 92, "MOVES", moves);
        chip(c, LW - 110, tp + 46, 92, "BEST", hi);
        if (flipWait <= 0f && f1 >= 0 && f2 < 0) {
            centerText(c, "find its pair", LW / 2, tp + 58, 16, 0x88FFFFFF);
        }
    }

    private void drawMenu(Canvas c) {
        drawMenu(c, "MEMORY", "match all the pairs",
                "best  " + (hi == 0 ? "—" : hi + " moves"), 0xFF9C6BFF);
    }

    private void gameOverPanel(Canvas c) {
        centerText(c, "ALL MATCHED!", LW / 2, LH * 0.28f, 36, 0xFF9C6BFF);
        centerText(c, "moves  " + moves, LW / 2, LH * 0.28f + 42, 24, 0xFFD7E8FF);
        centerText(c, "best   " + (hi == 0 ? "—" : hi), LW / 2, LH * 0.28f + 72, 18, 0x99D7E8FF);
        endButtons(c, new String[]{"PLAY AGAIN", "HOME"}, 0xFF9C6BFF);
    }
}