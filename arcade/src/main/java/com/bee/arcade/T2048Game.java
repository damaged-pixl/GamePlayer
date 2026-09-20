package com.bee.arcade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

/**
 * 2048: swipe to slide, tiles merge, spawn/merge animations, endless play
 * after reaching 2048. Swipe DOWN drives tiles down (fix applied here).
 */
final class T2048Game extends BaseGameView {

    private static final int MENU = 0, PLAY = 1, OVER = 2, WIN = 3;
    private volatile int screen = MENU;

    private static final float GAP = 8f, MARGIN = 8f;
    private float cell, boardX, boardY;

    private final int[] grid = new int[16];
    private final int[] animFrom = new int[16];
    private final boolean[] animMerged = new boolean[16];
    private int spawnCell = -1;
    private long animStartMs;
    private int score, best;
    private boolean won;

    // dir 0=left, 1=up, 2=right, 3=down. SEQ[d][line][pos] -> cell index.
    private static final int[][][] SEQ;
    static {
        SEQ = new int[4][4][4];
        for (int d = 0; d < 4; d++) {
            for (int li = 0; li < 4; li++) {
                for (int pos = 0; pos < 4; pos++) {
                    int r, c;
                    if (d == 0 || d == 2) { r = li; c = d == 0 ? pos : 3 - pos; }
                    else                  { c = li; r = d == 1 ? pos : 3 - pos; }
                    SEQ[d][li][pos] = r * 4 + c;
                }
            }
        }
    }

    private final RectF newBtn = new RectF();
    private float downX, downY;
    private boolean downOnNew;

    T2048Game(Context ctx, HomeListener home) {
        super(ctx, home);
        best = prefs.getInt("t_hi", 0);
        for (int i = 0; i < 16; i++) animFrom[i] = i;
    }

    // ── Contract ──────────────────────────────────────────────────────────
    @Override void onUpdate(float dt) {}
    @Override boolean isLive() { return screen == PLAY; }
    @Override void saveAll() {
        if (score > best) best = score;
        prefs.edit().putInt("t_hi", best).apply();
    }
    @Override boolean hidChrome() { return screen == MENU; }
    @Override int bgColor() { return 0xFF171310; }

    @Override
    void drawGame(Canvas c) {
        layout();
        if (screen == MENU) {
            drawMiniBoard(c);
            drawMenu(c, "2048", "swipe to slide, merge tiles", "best  " + best, 0xFFEDC22E);
        } else {
            drawHeader(c);
            drawBoard(c);
            if (screen == PLAY) centerText(c, "swipe to move", LW / 2, LH - 24, 16, 0x66F9F6F2);
            if (screen == OVER) {
                c.drawColor(0xAA000000);
                centerText(c, "GAME OVER", LW / 2, LH * 0.28f, 44, 0xFFFF7A59);
                centerText(c, "score  " + score, LW / 2, LH * 0.28f + 42, 26, 0xFFF9F6F2);
                centerText(c, "best   " + Math.max(best, score), LW / 2, LH * 0.28f + 72, 20, 0x99F9F6F2);
                endButtons(c, new String[]{"PLAY AGAIN", "HOME"}, 0xFFEDC22E);
            } else if (screen == WIN) {
                c.drawColor(0x88000000);
                centerText(c, "YOU WIN!", LW / 2, LH * 0.28f, 48, 0xFFFFD27D);
                centerText(c, "2048 reached — keep going!", LW / 2, LH * 0.28f + 43, 22, 0xCCFFE9B8);
                endButtons(c, new String[]{"KEEP GOING", "NEW GAME"}, 0xFFEDC22E);
            }
        }
    }

    // ── Game logic ────────────────────────────────────────────────────────
    private void layout() {
        cell = (LW - 2 * MARGIN - 5 * GAP) / 4f;
        boardX = MARGIN;
        // centre the square board in the space below the header so the play
        // area fills the canvas instead of hugging the top
        float bh = 4 * cell + 5 * GAP;
        float top = topPad() + 96f;
        float bottom = LH - 40f;
        boardY = top + Math.max(0f, (bottom - top - bh) / 2f);
    }

    private void startGame() {
        layout();
        for (int i = 0; i < 16; i++) grid[i] = 0;
        score = 0;
        won = false;
        spawnTile();
        spawnTile();
        animStartMs = System.currentTimeMillis();
        screen = PLAY;
        sfx.pop();
    }

    private void spawnTile() {
        int[] free = new int[16];
        int n = 0;
        for (int i = 0; i < 16; i++) if (grid[i] == 0) free[n++] = i;
        if (n == 0) return;
        int c = free[(int) (Math.random() * n)];
        grid[c] = Math.random() < 0.9 ? 2 : 4;
        spawnCell = c;
        animFrom[c] = c;
        animMerged[c] = false;
    }

    private boolean canMove() {
        for (int i = 0; i < 16; i++) {
            int v = grid[i];
            if (v == 0) return true;
            int col = i % 4, row = i / 4;
            if (col < 3 && grid[i + 1] == v) return true;
            if (row < 3 && grid[i + 4] == v) return true;
        }
        return false;
    }

    private boolean move(int dir) {
        int[] snap = grid.clone();
        int[] ng = new int[16];
        int[] orig = new int[16];
        boolean[] mrg = new boolean[16];
        boolean anyMerge = false;

        for (int li = 0; li < 4; li++) {
            int[] v = new int[4], oc = new int[4];
            int n = 0;
            for (int pos = 0; pos < 4; pos++) {
                int cellIdx = SEQ[dir][li][pos];
                if (grid[cellIdx] != 0) { v[n] = grid[cellIdx]; oc[n] = cellIdx; n++; }
            }
            int[] mv = new int[4], mo = new int[4];
            boolean[] mm = new boolean[4];
            int m = 0;
            for (int i = 0; i < n; i++) {
                if (i + 1 < n && v[i] == v[i + 1]) {
                    mv[m] = v[i] * 2;
                    mo[m] = oc[i + 1];
                    mm[m] = true;
                    anyMerge = true;
                    score += v[i] * 2;
                    i++;
                } else {
                    mv[m] = v[i]; mo[m] = oc[i]; mm[m] = false;
                }
                m++;
            }
            for (int pos = 0; pos < 4; pos++) {
                int cellIdx = SEQ[dir][li][pos];
                if (pos < m) {
                    ng[cellIdx] = mv[pos];
                    orig[cellIdx] = mo[pos];
                    mrg[cellIdx] = mm[pos];
                } else {
                    ng[cellIdx] = 0;
                    orig[cellIdx] = cellIdx;
                    mrg[cellIdx] = false;
                }
            }
        }

        boolean moved = false;
        for (int i = 0; i < 16; i++) if (ng[i] != snap[i]) { moved = true; break; }
        if (!moved) return false;

        System.arraycopy(ng, 0, grid, 0, 16);
        System.arraycopy(orig, 0, animFrom, 0, 16);
        System.arraycopy(mrg, 0, animMerged, 0, 16);
        spawnTile();
        animStartMs = System.currentTimeMillis();

        if (anyMerge) sfx.pluck(log2(mvMax())); else sfx.pop();

        int top = topTile();
        if (top >= 2048 && !won) { won = true; sfx.win(); screen = WIN; return true; }
        if (!canMove()) gameOver();
        return true;
    }

    private int mvMax() {
        int m = 0;
        for (int i = 0; i < 16; i++) if (animMerged[i] && grid[i] > m) m = grid[i];
        return m;
    }

    private int topTile() {
        int m = 0;
        for (int i = 0; i < 16; i++) if (grid[i] > m) m = grid[i];
        return m;
    }

    private void gameOver() {
        saveAll();
        buzz();
        sfx.down();
        screen = OVER;
    }

    private static int log2(int v) {
        int e = 0;
        while (v > 1) { v >>= 1; e++; }
        return e;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (super.onTouchEvent(e)) return true;
        float lx = (e.getX() - ox) / uScale, ly = (e.getY() - oy) / uScale;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lx; downY = ly;
                downOnNew = false;
                if (screen == PLAY && newBtn.contains(lx, ly)) { downOnNew = true; startGame(); }
                return true;
            case MotionEvent.ACTION_UP:
                // swallowing the tail of a gesture that began on NEW so a flick
                // doesn't also fire a move on the freshly restarted board
                if (downOnNew) { downOnNew = false; return true; }
                if (screen == MENU) { startGame(); return true; }
                if (screen == OVER) {
                    int b = pressedEndButton(lx, ly);
                    if (b == 0) startGame();
                    else if (b == 1) goHome();
                    return true;
                }
                if (screen == WIN) {
                    int b = pressedEndButton(lx, ly);
                    if (b == 0) { screen = PLAY; sfx.pop(); return true; }
                    if (b == 1) { startGame(); return true; }
                    return true;
                }
                if (screen != PLAY) return true;

                float dX = lx - downX, dY = ly - downY;
                float adx = Math.abs(dX), ady = Math.abs(dY);
                if (Math.max(adx, ady) < 22f) return true;
                int dir;
                if (adx > ady) dir = dX > 0 ? 2 : 0;
                else dir = dY > 0 ? 3 : 1; // swipe DOWN -> tiles down
                move(dir);
                return true;
        }
        return true;
    }

    // ── Renderer ──────────────────────────────────────────────────────────
    private void drawHeader(Canvas c) {
        float tp = topPad();

        // title centred, NEW button tucked left of the pause chrome on the right
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(30);
        p.setFakeBoldText(true);
        p.setColor(0xFFEDC22E);
        c.drawText("2048", LW / 2, tp + 32, p);
        p.setFakeBoldText(false);

        newBtn.set(LW - 160, tp + 8, LW - 72, tp + 40);
        p.setColor(0xFF2A231D);
        c.drawRoundRect(newBtn, 9, 9, p);
        p.setColor(0xFFF9F6F2);
        p.setTextSize(15);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("NEW", LW - 116, tp + 27, p);

        chip(c, 46, tp + 42, 98, "SCORE", score);
        chip(c, 246, tp + 42, 98, "BEST", Math.max(best, score));
    }

    private void drawBoard(Canvas c) {
        float bh = 4 * cell + 5 * GAP;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF32291F);
        c.drawRoundRect(boardX, boardY, boardX + 4 * cell + 5 * GAP, boardY + bh, 16, 16, p);
        p.setColor(0xFF241E18);
        for (int r = 0; r < 4; r++) {
            for (int col = 0; col < 4; col++) {
                float x = cellX(col), y = cellY(r);
                c.drawRoundRect(x, y, x + cell, y + cell, 12, 12, p);
            }
        }

        long now = System.currentTimeMillis();
        float p01 = Math.min(1f, (now - animStartMs) / 150f);
        float q = 1f - (1f - p01) * (1f - p01) * (1f - p01);

        for (int j = 0; j < 16; j++) {
            int v = grid[j];
            if (v == 0) continue;
            int fromIdx = animFrom[j];
            float fx = cellX(fromIdx % 4), fy = cellY(fromIdx / 4);
            float tx = cellX(j % 4), ty = cellY(j / 4);
            float x = fx + (tx - fx) * q;
            float y = fy + (ty - fy) * q;

            float scale = 1f;
            if (j == spawnCell) scale = 0.5f + 0.5f * q;
            else if (animMerged[j]) scale = 1f + 0.12f * (float) Math.sin(p01 * Math.PI);

            drawTile(c, j, x, y, scale);

            if (animMerged[j]) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f);
                p.setColor(0xFFFFFFFF);
                p.setAlpha((int) (255 * (1f - q)));
                c.drawRoundRect(x + 4, y + 4, x + cell - 4, y + cell - 4, 12, 12, p);
                p.setStyle(Paint.Style.FILL);
                p.setAlpha(255);
            }
        }
    }

    /** Small static 4x4 board with a few tiles, filling the menu preview band. */
    private void drawMiniBoard(Canvas c) {
        float mc = 68f;
        float bw = 4 * mc + 5 * GAP;
        float x0 = LW / 2f - bw / 2f;
        float y0 = LH * 0.60f - bw / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF32291F);
        c.drawRoundRect(x0, y0, x0 + bw, y0 + bw, 12, 12, p);
        p.setColor(0xFF241E18);
        for (int r = 0; r < 4; r++) {
            for (int col = 0; col < 4; col++) {
                float x = x0 + GAP + col * (mc + GAP);
                float y = y0 + GAP + r * (mc + GAP);
                c.drawRoundRect(x, y, x + mc, y + mc, 8, 8, p);
            }
        }
        int[][] mini = {{2, 0, 0, 0}, {0, 4, 0, 0}, {0, 0, 8, 0}, {0, 0, 0, 16}};
        for (int r = 0; r < 4; r++) {
            for (int col = 0; col < 4; col++) {
                if (mini[r][col] == 0) continue;
                float x = x0 + GAP + col * (mc + GAP);
                float y = y0 + GAP + r * (mc + GAP);
                int v = mini[r][col];
                int e = Math.min(log2(v) - 1, 11);
                int[] pal = PAL[e];
                float cx = x + mc / 2f, cy = y + mc / 2f;
                float half = (mc - 2 * GAP) / 2f;
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF000000 | pal[0]);
                c.drawRoundRect(cx - half, cy - half, cx + half, cy + half, 8, 8, p);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(30);
                p.setColor(0xFF000000 | pal[1]);
                c.drawText(String.valueOf(v), cx, cy + 10.5f, p);
            }
        }
    }

    private void drawTile(Canvas c, int j, float x, float y, float scale) {
        int e = Math.min(log2(grid[j]) - 1, 11);
        int[] pal = PAL[e];
        float cx = x + cell / 2f, cy = y + cell / 2f;
        float half = (cell - 2 * GAP) * scale / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF000000 | pal[0]);
        c.drawRoundRect(cx - half, cy - half, cx + half, cy + half, 12 * scale, 12 * scale, p);

        int v = grid[j];
        int fs = v < 100 ? 40 : v < 1000 ? 34 : v < 10000 ? 30 : v < 100000 ? 24 : 20;
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(fs);
        p.setColor(0xFF000000 | pal[1]);
        c.drawText(String.valueOf(v), cx, cy + fs * 0.36f, p);
    }

    private float cellX(int col) { return boardX + GAP + col * (cell + GAP); }
    private float cellY(int row) { return boardY + GAP + row * (cell + GAP); }

    private static final int[][] PAL = {
        {0xEEE4DA, 0x776E65}, {0xEDE0C8, 0x776E65}, {0xF2B179, 0xF9F6F2},
        {0xF59563, 0xF9F6F2}, {0xF67C5F, 0xF9F6F2}, {0xF65E3B, 0xF9F6F2},
        {0xEDCF72, 0xF9F6F2}, {0xEDCC61, 0xF9F6F2}, {0xEDC850, 0xF9F6F2},
        {0xEDC53F, 0xF9F6F2}, {0xEDC22E, 0xF9F6F2}, {0x3C3A32, 0xF9F6F2}
    };
}