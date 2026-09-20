package com.bee.arcade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * Flood Fill — a mini inspired by Flowit (renamed: it is not the same game).
 * Tap a TOOL cell and it fills EMPTY cells with its colour: the flood tool
 * spreads in all directions, an arrow tool fills a straight line. If nothing
 * can be filled, the tool clears its colour instead. Every cell must end up
 * matching its faint target colour; tool cells are skipped. 15 procedural
 * levels, then "ALL LEVELS CLEARED".
 */
final class FloodFillGame extends BaseGameView {

    private static final int MENU = 0, PLAY = 1, OVER = 2;
    private static final int MAX_LEVEL = 15;
    private static final int[] PAL = {0xFFE4572E, 0xFF4CAF50, 0xFF2F6FD0, 0xFFE0B91E};
    private static final int[] DR = {-1, 0, 1, 0};
    private static final int[] DC = {0, 1, 0, -1};

    private int screen = MENU;
    private int level = 1, hi;
    private int size, nColors;
    private int[][] target;   // -2 wall, -1 empty, 0..nColors-1 target colour
    private int[][] tool;     // -1 none, 0 FLOOD, 1..4 direction (U,R,D,L)
    private int[][] state;    // -1 empty, 0..nColors-1 filled colour
    private float wonT;       // >0 while showing "LEVEL CLEARED"
    private final Random rnd = new Random();

    FloodFillGame(Context ctx, HomeListener home) {
        super(ctx, home);
        hi = prefs.getInt("ff_hi", 0);
        newLevel();
    }

    // ── Contract ──────────────────────────────────────────────────────────
    @Override
    void onUpdate(float dt) {
        if (wonT > 0) {
            wonT -= dt;
            if (wonT <= 0) {
                if (level >= MAX_LEVEL) { screen = OVER; saveAll(); }
                else { level++; newLevel(); }
            }
        }
    }

    @Override
    void drawGame(Canvas c) {
        drawBoard(c);
        if (screen == MENU) {
            drawMenu(c, "FLOOD FILL", "tap tools to fill empty cells",
                    "best level  " + Math.max(hi, level), 0xFF4CAF50);
        } else {
            float tp = topPad();
            chip(c, 14, tp + 46, 96, "LEVEL", level);
            chip(c, LW - 110, tp + 46, 96, "BEST", Math.max(hi, level));
            if (wonT > 0) centerText(c, "LEVEL CLEARED!", LW / 2, LH * 0.30f, 34, 0xFF9C6BFF);
            if (screen == OVER) {
                c.drawColor(0x99000000);
                centerText(c, "ALL LEVELS CLEARED!", LW / 2, LH * 0.42f, 34, 0xFF9C6BFF);
                centerText(c, "you solved every puzzle", LW / 2, LH * 0.42f + 40, 20, 0xBBFFFFFF);
                endButtons(c, new String[]{"PLAY AGAIN", "HOME"}, 0xFF4CAF50);
            }
        }
    }

    @Override boolean isLive() { return screen == PLAY; }
    @Override void saveAll() { prefs.edit().putInt("ff_hi", Math.max(hi, level)).apply(); }
    @Override boolean hidChrome() { return screen == MENU; }
    @Override int bgColor() { return 0xFF10141F; }

    // ── Level generation ──────────────────────────────────────────────────
    private void newLevel() {
        size = Math.min(5 + (level - 1) / 5, 7);            // 5,5,5,6,6,6,7,7,7…
        nColors = Math.min(2 + (level - 1) / 3, 4);         // 2,2,2,3,3,3,4…
        target = new int[size][size];
        tool = new int[size][size];
        state = new int[size][size];
        for (int[] row : target) java.util.Arrays.fill(row, -1);
        for (int[] row : tool) java.util.Arrays.fill(row, -1);
        for (int[] row : state) java.util.Arrays.fill(row, -1);
        for (int attempt = 0; attempt < 50 && !buildLevel(); attempt++) {
            for (int[] row : target) java.util.Arrays.fill(row, -1);
            for (int[] row : tool) java.util.Arrays.fill(row, -1);
        }
    }

    /**
     * One FLOOD tool for colour 0 plus a straight-line tool per other colour.
     * Lines are placed first; the flood then fills whatever empty space the
     * filled lines leave — so the intended order is lines first, flood last,
     * and a premature flood overfills and must be cleared. Fails (and is
     * retried) if the flood could not reach every non-line cell.
     */
    private boolean buildLevel() {
        int fr = rnd.nextInt(size), fc = rnd.nextInt(size);
        target[fr][fc] = 0;
        tool[fr][fc] = 0;
        for (int k = 1; k < nColors; k++) {
            if (!placeLine(k)) return false;
        }
        // every remaining cell is the flood's region
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (target[r][c] == -1) target[r][c] = 0;
        return floodReachesAll(fr, fc);
    }

    /** Place a straight line of 2-4 cells for colour k; its first cell is the tool. */
    private boolean placeLine(int k) {
        for (int attempt = 0; attempt < 40; attempt++) {
            int dir = 1 + rnd.nextInt(4);
            int len = 2 + rnd.nextInt(3);
            int r, c;
            switch (dir) {
                case 1: r = rnd.nextInt(size - len + 1) + len - 1; c = rnd.nextInt(size); break; // U
                case 2: r = rnd.nextInt(size); c = rnd.nextInt(size - len + 1); break;          // R
                case 3: r = rnd.nextInt(size - len + 1); c = rnd.nextInt(size); break;          // D
                default: r = rnd.nextInt(size); c = rnd.nextInt(size - len + 1) + len - 1; break; // L
            }
            boolean free = true;
            for (int i = 0; i < len; i++) {
                if (target[r + DR[dir - 1] * i][c + DC[dir - 1] * i] >= 0) { free = false; break; }
            }
            if (!free) continue;
            for (int i = 0; i < len; i++) {
                target[r + DR[dir - 1] * i][c + DC[dir - 1] * i] = k;
                if (i == 0) tool[r + DR[dir - 1] * i][c + DC[dir - 1] * i] = dir;
            }
            return true;
        }
        return false;
    }

    /** With the lines treated as filled (blocking), the flood must reach every
     *  non-line cell — otherwise some cells could never be coloured. */
    private boolean floodReachesAll(int fr, int fc) {
        boolean[][] seen = new boolean[size][size];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{fr, fc});
        seen[fr][fc] = true;
        int reached = 0, total = 0;
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (target[r][c] == 0) total++;
        while (!q.isEmpty()) {
            int[] cell = q.poll();
            reached++;
            for (int d = 0; d < 4; d++) {
                int nr = cell[0] + DR[d], nc = cell[1] + DC[d];
                if (nr < 0 || nr >= size || nc < 0 || nc >= size || seen[nr][nc]) continue;
                seen[nr][nc] = true;
                if (target[nr][nc] == 0) q.add(new int[]{nr, nc});
            }
        }
        return reached == total;
    }

    // ── Game logic ────────────────────────────────────────────────────────
    private void tapTool(int r, int c) {
        int k = target[r][c];
        int t = tool[r][c];
        boolean filled = t == 0 ? floodFill(r, c, k) : lineFill(r, c, k, t);
        if (filled) {
            sfx.eat(k);
        } else {
            clearColour(k, t, r, c);
            sfx.blip();
        }
        if (checkWon()) {
            wonT = 1.2f;
            sfx.win();
            if (level > hi) { hi = level; saveAll(); }
        }
    }

    /** Flood from the tool through EMPTY cells, painting them colour k. */
    private boolean floodFill(int r, int c, int k) {
        boolean filled = false;
        boolean[][] seen = new boolean[size][size];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{r, c});
        seen[r][c] = true;
        while (!q.isEmpty()) {
            int[] cell = q.poll();
            for (int d = 0; d < 4; d++) {
                int nr = cell[0] + DR[d], nc = cell[1] + DC[d];
                if (nr < 0 || nr >= size || nc < 0 || nc >= size || seen[nr][nc]) continue;
                seen[nr][nc] = true;
                if (state[nr][nc] == -1 && tool[nr][nc] == -1 && target[nr][nc] != -2) {
                    state[nr][nc] = k;
                    filled = true;
                    q.add(new int[]{nr, nc});
                }
            }
        }
        return filled;
    }

    /** Walk the line from the tool through its own EMPTY cells, painting them
     *  colour k. Stops at the line's end (target != k) so it never bleeds into
     *  the flood region. */
    private boolean lineFill(int r, int c, int k, int t) {
        boolean filled = false;
        int nr = r + DR[t - 1], nc = c + DC[t - 1];
        while (nr >= 0 && nr < size && nc >= 0 && nc < size
                && state[nr][nc] == -1 && tool[nr][nc] == -1 && target[nr][nc] == k) {
            state[nr][nc] = k;
            filled = true;
            nr += DR[t - 1];
            nc += DC[t - 1];
        }
        return filled;
    }

    /** Clear colour k: a flood clears its whole region, a line clears its path. */
    private void clearColour(int k, int t, int r, int c) {
        if (t == 0) {
            boolean[][] seen = new boolean[size][size];
            ArrayDeque<int[]> q = new ArrayDeque<>();
            q.add(new int[]{r, c});
            seen[r][c] = true;
            while (!q.isEmpty()) {
                int[] cell = q.poll();
                if (state[cell[0]][cell[1]] == k) state[cell[0]][cell[1]] = -1;
                for (int d = 0; d < 4; d++) {
                    int nr = cell[0] + DR[d], nc = cell[1] + DC[d];
                    if (nr < 0 || nr >= size || nc < 0 || nc >= size || seen[nr][nc]) continue;
                    seen[nr][nc] = true;
                    if (state[nr][nc] == k) q.add(new int[]{nr, nc});
                }
            }
        } else {
            int nr = r + DR[t - 1], nc = c + DC[t - 1];
            while (nr >= 0 && nr < size && nc >= 0 && nc < size && state[nr][nc] == k) {
                state[nr][nc] = -1;
                nr += DR[t - 1];
                nc += DC[t - 1];
            }
        }
    }

    /** Win = every non-tool cell's state matches its target colour. */
    private boolean checkWon() {
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++) {
                if (tool[r][c] >= 0) continue;
                int t = target[r][c];
                if (t == -2) { if (state[r][c] != -1) return false; }
                else if (state[r][c] != t) return false;
            }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (super.onTouchEvent(e)) return true;
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
        float lx = (e.getX() - ox) / uScale, ly = (e.getY() - oy) / uScale;
        if (screen == MENU) { screen = PLAY; sfx.pop(); return true; }
        if (screen == OVER) {
            int b = pressedEndButton(lx, ly);
            if (b == 0) { level = 1; newLevel(); screen = PLAY; sfx.pop(); }
            else if (b == 1) goHome();
            return true;
        }
        if (screen == PLAY && wonT <= 0) {
            float cs = cell(), bx = boardX(), by = boardY();
            int c = (int) ((lx - bx) / cs);
            int r = (int) ((ly - by) / cs);
            if (r >= 0 && r < size && c >= 0 && c < size && tool[r][c] >= 0) tapTool(r, c);
        }
        return true;
    }

    // ── Renderer ──────────────────────────────────────────────────────────
    private float cell() { return Math.min((LW - 24) / size, (LH - 150) / size); }
    private float boardX() { return (LW - size * cell()) / 2f; }
    private float boardY() { return topPad() + 90; }

    private void drawBoard(Canvas c) {
        float cs = cell(), bx = boardX(), by = boardY();
        for (int r = 0; r < size; r++) {
            for (int col = 0; col < size; col++) {
                float x = bx + col * cs, y = by + r * cs;
                int t = target[r][col];
                int s = state[r][col];
                int tl = tool[r][col];
                p.setStyle(Paint.Style.FILL);
                if (t == -2) {
                    p.setColor(0xFF141824);                        // wall
                    c.drawRoundRect(x + 2, y + 2, x + cs - 2, y + cs - 2, 8, 8, p);
                } else if (tl >= 0) {
                    p.setColor(PAL[t]);                            // tool cell
                    c.drawRoundRect(x + 2, y + 2, x + cs - 2, y + cs - 2, 8, 8, p);
                    drawToolIcon(c, x, y, cs, tl);
                } else if (s >= 0) {
                    p.setColor(PAL[s]);                            // filled
                    c.drawRoundRect(x + 2, y + 2, x + cs - 2, y + cs - 2, 8, 8, p);
                } else if (t >= 0) {
                    p.setColor((PAL[t] & 0x00FFFFFF) | 0x38000000); // ghost target
                    c.drawRoundRect(x + 2, y + 2, x + cs - 2, y + cs - 2, 8, 8, p);
                } else {
                    p.setColor(0xFF1A2030);                        // empty
                    c.drawRoundRect(x + 2, y + 2, x + cs - 2, y + cs - 2, 8, 8, p);
                }
            }
        }
    }

    /** White icon on a tool cell: compass for FLOOD, arrow for directions. */
    private void drawToolIcon(Canvas c, float x, float y, float cs, int t) {
        float cx = x + cs / 2, cy = y + cs / 2;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        if (t == 0) {
            c.drawCircle(cx, cy, cs * 0.09f, p);
            float d = cs * 0.17f, s = cs * 0.07f;
            Path n = new Path(); n.moveTo(cx, cy - d - s); n.lineTo(cx - s, cy - d); n.lineTo(cx + s, cy - d); n.close();
            Path s2 = new Path(); s2.moveTo(cx, cy + d + s); s2.lineTo(cx - s, cy + d); s2.lineTo(cx + s, cy + d); s2.close();
            Path e = new Path(); e.moveTo(cx + d + s, cy); e.lineTo(cx + d, cy - s); e.lineTo(cx + d, cy + s); e.close();
            Path w = new Path(); w.moveTo(cx - d - s, cy); w.lineTo(cx - d, cy - s); w.lineTo(cx - d, cy + s); w.close();
            c.drawPath(n, p); c.drawPath(s2, p); c.drawPath(e, p); c.drawPath(w, p);
        } else {
            float s = cs * 0.16f;
            Path a = new Path();
            switch (t) {
                case 1: a.moveTo(cx, cy - s); a.lineTo(cx - s, cy + s); a.lineTo(cx + s, cy + s); break;
                case 2: a.moveTo(cx + s, cy); a.lineTo(cx - s, cy - s); a.lineTo(cx - s, cy + s); break;
                case 3: a.moveTo(cx, cy + s); a.lineTo(cx - s, cy - s); a.lineTo(cx + s, cy - s); break;
                default: a.moveTo(cx - s, cy); a.lineTo(cx + s, cy - s); a.lineTo(cx + s, cy + s); break;
            }
            a.close();
            c.drawPath(a, p);
        }
    }
}