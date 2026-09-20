package com.bee.arcade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;

import java.util.Random;

/**
 * Arrow Slide — a mini inspired by Arrow Escape (renamed: it is not the same
 * game). Slide every arrow off the board: tap an arrow and it slides in its
 * direction — clear path to the edge = it escapes; hitting another arrow =
 * crash, both are destroyed and you lose a life. 3 lives, endless procedural
 * levels.
 */
final class ArrowSlideGame extends BaseGameView {

    private static final int MENU = 0, PLAY = 1, OVER = 2;
    private static final int SIZE = 5;
    private static final int MAX_LIVES = 3;
    private static final int[] DR = {-1, 0, 1, 0};
    private static final int[] DC = {0, 1, 0, -1};

    private int screen = MENU;
    private int level = 1, hi;
    private int lives = MAX_LIVES;
    private int[][] grid;            // -1 empty, else arrow index
    private int[] arR, arC, arD;     // arrow positions + direction
    private int nArrows;
    // slide animation
    private int slideIdx = -1;
    private float slideT, slideDur = 0.18f;
    private float fromR, fromC, toR, toC;
    private boolean slideCrash;
    private final Random rnd = new Random();

    ArrowSlideGame(Context ctx, HomeListener home) {
        super(ctx, home);
        hi = prefs.getInt("as_hi", 0);
        newLevel();
    }

    // ── Contract ──────────────────────────────────────────────────────────
    @Override
    void onUpdate(float dt) {
        if (slideIdx < 0) return;
        slideT += dt;
        if (slideT < slideDur) return;
        slideIdx = -1;
        if (slideCrash) {
            grid[(int) toR][(int) toC] = -1;   // the arrow that was hit
            nArrows -= 2;
            lives--;
            sfx.down();
            buzz();
            if (lives <= 0) { screen = OVER; saveAll(); }
        } else {
            nArrows--;
            sfx.pluck(level);
            if (nArrows == 0) {
                if (level > hi) { hi = level; saveAll(); }
                level++;
                newLevel();
                sfx.win();
            }
        }
    }

    @Override
    void drawGame(Canvas c) {
        drawBoard(c);
        if (screen == MENU) {
            drawMenu(c, "ARROW SLIDE", "slide every arrow off the board",
                    "best level  " + Math.max(hi, level), 0xFF3DA9E2);
        } else {
            float tp = topPad();
            chip(c, 14, tp + 46, 96, "LEVEL", level);
            chip(c, LW / 2 - 48, tp + 46, 96, "LIVES", Math.max(0, lives));
            chip(c, LW - 110, tp + 46, 96, "BEST", Math.max(hi, level));
            if (screen == OVER) {
                c.drawColor(0x99000000);
                centerText(c, "OUT OF LIVES", LW / 2, LH * 0.42f, 40, 0xFFFF5D5D);
                centerText(c, "reached level  " + level, LW / 2, LH * 0.42f + 40, 22, 0xBBFFFFFF);
                endButtons(c, new String[]{"PLAY AGAIN", "HOME"}, 0xFF3DA9E2);
            }
        }
    }

    @Override boolean isLive() { return screen == PLAY; }
    @Override void saveAll() { prefs.edit().putInt("as_hi", Math.max(hi, level)).apply(); }
    @Override boolean hidChrome() { return screen == MENU; }
    @Override int bgColor() { return 0xFF0E1A26; }

    // ── Level generation ──────────────────────────────────────────────────
    private void newLevel() {
        nArrows = Math.min(4 + (level - 1) / 2, 8);
        grid = new int[SIZE][SIZE];
        arR = new int[nArrows];
        arC = new int[nArrows];
        arD = new int[nArrows];
        for (int attempt = 0; attempt < 30; attempt++) {
            for (int[] row : grid) java.util.Arrays.fill(row, -1);
            int placed = 0;
            while (placed < nArrows) {
                int r = rnd.nextInt(SIZE), c = rnd.nextInt(SIZE);
                if (grid[r][c] >= 0) continue;
                grid[r][c] = placed;
                arR[placed] = r;
                arC[placed] = c;
                arD[placed] = rnd.nextInt(4);
                placed++;
            }
            if (anyCanExit()) return;
        }
    }

    /** True if at least one arrow has a clear path to the edge. */
    private boolean anyCanExit() {
        for (int i = 0; i < nArrows; i++) {
            int r = arR[i], c = arC[i], d = arD[i];
            boolean clear = true;
            while (true) {
                r += DR[d];
                c += DC[d];
                if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) break;
                if (grid[r][c] >= 0) { clear = false; break; }
            }
            if (clear) return true;
        }
        return false;
    }

    // ── Game logic ────────────────────────────────────────────────────────
    private void tapArrow(float lx, float ly) {
        float cs = cell(), bx = boardX(), by = boardY();
        int c = (int) ((lx - bx) / cs);
        int r = (int) ((ly - by) / cs);
        if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) return;
        int idx = grid[r][c];
        if (idx < 0 || slideIdx >= 0) return;
        int d = arD[idx];
        int nr = r, nc = c;
        boolean crash = false;
        while (true) {
            nr += DR[d];
            nc += DC[d];
            if (nr < 0 || nr >= SIZE || nc < 0 || nc >= SIZE) break; // exit
            if (grid[nr][nc] >= 0) { crash = true; break; }
        }
        grid[r][c] = -1;             // arrow is now in flight
        slideIdx = idx;
        fromR = r; fromC = c;
        toR = nr; toC = nc;
        slideCrash = crash;
        slideT = 0;
        sfx.bounce();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (super.onTouchEvent(e)) return true;
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
        float lx = (e.getX() - ox) / uScale, ly = (e.getY() - oy) / uScale;
        if (screen == MENU) { screen = PLAY; sfx.pop(); return true; }
        if (screen == OVER) {
            int b = pressedEndButton(lx, ly);
            if (b == 0) { level = 1; lives = MAX_LIVES; newLevel(); screen = PLAY; sfx.pop(); }
            else if (b == 1) goHome();
            return true;
        }
        if (screen == PLAY) tapArrow(lx, ly);
        return true;
    }

    // ── Renderer ──────────────────────────────────────────────────────────
    private float cell() { return Math.min((LW - 24) / SIZE, (LH - 150) / SIZE); }
    private float boardX() { return (LW - SIZE * cell()) / 2f; }
    private float boardY() { return topPad() + 90; }

    private void drawBoard(Canvas c) {
        float cs = cell(), bx = boardX(), by = boardY();
        // grid slots
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF16283A);
        for (int r = 0; r < SIZE; r++) {
            for (int col = 0; col < SIZE; col++) {
                float x = bx + col * cs, y = by + r * cs;
                c.drawRoundRect(x + 2, y + 2, x + cs - 2, y + cs - 2, 8, 8, p);
            }
        }
        // arrows still on the board (grid is the source of truth — an arrow
        // that escaped or crashed is gone even if its index < nArrows)
        for (int r = 0; r < SIZE; r++) {
            for (int col = 0; col < SIZE; col++) {
                int idx = grid[r][col];
                if (idx < 0 || idx == slideIdx) continue;
                drawArrow(c, bx + col * cs, by + r * cs, cs, arD[idx], 0xFFE8F4FF);
            }
        }
        // in-flight arrow at interpolated position
        if (slideIdx >= 0) {
            float t = Math.min(1f, slideT / slideDur);
            float x = bx + (fromC + (toC - fromC) * t) * cs;
            float y = by + (fromR + (toR - fromR) * t) * cs;
            drawArrow(c, x, y, cs, arD[slideIdx], 0xFFFFD166);
        }
    }

    private void drawArrow(Canvas c, float x, float y, float cs, int d, int color) {
        float cx = x + cs / 2, cy = y + cs / 2;
        float s = cs * 0.24f;
        Path path = new Path();
        switch (d) {
            case 0: path.moveTo(cx, cy - s); path.lineTo(cx - s, cy + s); path.lineTo(cx + s, cy + s); break;
            case 1: path.moveTo(cx + s, cy); path.lineTo(cx - s, cy - s); path.lineTo(cx - s, cy + s); break;
            case 2: path.moveTo(cx, cy + s); path.lineTo(cx - s, cy - s); path.lineTo(cx + s, cy - s); break;
            default: path.moveTo(cx - s, cy); path.lineTo(cx + s, cy - s); path.lineTo(cx + s, cy + s); break;
        }
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(path, p);
    }
}