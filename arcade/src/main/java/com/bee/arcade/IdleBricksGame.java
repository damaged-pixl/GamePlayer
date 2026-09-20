package com.bee.arcade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.Random;

/**
 * Idle Bricks — a mini inspired by Idle Brick Breaker (renamed: it is not the
 * same game). Bricks with HP at the top, balls that auto-bounce and deal
 * damage, gold per brick, four upgrades (damage / speed / balls / click).
 * Tap a brick for click damage. Endless levels, gold and upgrades persist.
 */
final class IdleBricksGame extends BaseGameView {

    private static final int MENU = 0, PLAY = 1;
    private static final int COLS = 8;
    private static final int MAX_BALLS = 3;
    private static final float BRICK_H = 26f;

    private volatile int screen = MENU;
    private int level = 1, hi;
    private long gold;
    private int dmg, spd, balls, click;   // upgrade levels

    private int[][] brickHp;
    private int brickRows;
    private float[] bx, by, bvx, bvy;     // balls
    private int nBalls;
    private final RectF[] upBtn = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final Random rnd = new Random();

    IdleBricksGame(Context ctx, HomeListener home) {
        super(ctx, home);
        gold = prefs.getLong("ib_gold", 0);
        dmg = prefs.getInt("ib_dmg", 0);
        spd = prefs.getInt("ib_spd", 0);
        balls = prefs.getInt("ib_balls", 0);
        click = prefs.getInt("ib_click", 0);
        hi = prefs.getInt("ib_hi", 0);
        newLevel();
    }

    // ── Contract ──────────────────────────────────────────────────────────
    @Override
    void onUpdate(float dt) {
        float speed = 150f + spd * 25f;
        for (int i = 0; i < nBalls; i++) {
            bx[i] += bvx[i] * speed * dt;
            by[i] += bvy[i] * speed * dt;
            float r = 6f;
            if (bx[i] < r) { bx[i] = r; bvx[i] = -bvx[i]; }
            if (bx[i] > LW - r) { bx[i] = LW - r; bvx[i] = -bvx[i]; }
            float topY = brickY();
            if (by[i] < topY) { by[i] = topY; bvy[i] = -bvy[i]; }
            // auto-paddle sits ABOVE the upgrade panel so the balls stay visible
            if (by[i] > LH - 200f) { by[i] = LH - 200f; bvy[i] = -Math.abs(bvy[i]); }
            hitBrick(i);
        }
    }

    @Override
    void drawGame(Canvas c) {
        drawBricks(c);
        drawBalls(c);
        drawPaddle(c);
        if (screen == MENU) {
            drawMenu(c, "IDLE BRICKS", "auto-battle, tap bricks, buy upgrades",
                    "best level  " + hi, 0xFFFFB020);
        } else {
            float tp = topPad();
            chip(c, 14, tp + 46, 96, "LEVEL", level);
            chip(c, LW / 2 - 48, tp + 46, 96, "GOLD", (int) gold);
            chip(c, LW - 110, tp + 46, 96, "BEST", hi);
            drawUpgrades(c);
        }
    }

    @Override boolean isLive() { return screen == PLAY; }
    @Override void saveAll() {
        prefs.edit()
                .putLong("ib_gold", gold)
                .putInt("ib_dmg", dmg)
                .putInt("ib_spd", spd)
                .putInt("ib_balls", balls)
                .putInt("ib_click", click)
                .putInt("ib_hi", hi)
                .apply();
    }
    @Override boolean hidChrome() { return screen == MENU; }
    @Override int bgColor() { return 0xFF1A1408; }

    // ── Level setup ───────────────────────────────────────────────────────
    private void newLevel() {
        brickRows = Math.min(3 + level / 3, 6);
        brickHp = new int[brickRows][COLS];
        int hp = 1 + level / 2;
        for (int[] row : brickHp) java.util.Arrays.fill(row, hp);
        nBalls = 1 + balls;
        bx = new float[nBalls];
        by = new float[nBalls];
        bvx = new float[nBalls];
        bvy = new float[nBalls];
        for (int i = 0; i < nBalls; i++) spawnBall(i);
    }

    private void spawnBall(int i) {
        bx[i] = LW / 2 + (i - (nBalls - 1) / 2f) * 40f;
        by[i] = LH - 212f;   // just above the paddle, above the upgrade panel
        float ang = (float) (Math.PI * (0.25 + 0.5 * rnd.nextDouble()));
        bvx[i] = (float) Math.cos(ang) * (rnd.nextBoolean() ? 1 : -1);
        bvy[i] = -(float) Math.sin(ang);
    }

    private int brickValue() { return 1 + level / 3; }

    // ── Game logic ────────────────────────────────────────────────────────
    private float brickX() { return 12f; }
    private float brickY() { return topPad() + 90f; }
    private float brickW() { return (LW - 24f) / COLS; }

    private void hitBrick(int i) {
        int col = (int) ((bx[i] - brickX()) / brickW());
        int row = (int) ((by[i] - brickY()) / BRICK_H);
        if (row < 0 || row >= brickRows || col < 0 || col >= COLS) return;
        if (brickHp[row][col] <= 0) return;
        brickHp[row][col] -= 1 + dmg;
        if (brickHp[row][col] <= 0) {
            gold += brickValue();
            sfx.brick(row);
        } else {
            sfx.bounce();
        }
        // push the ball out of the brick and bounce vertically
        if (bvy[i] > 0) { by[i] = brickY() + row * BRICK_H - 7f; bvy[i] = -bvy[i]; }
        else { by[i] = brickY() + (row + 1) * BRICK_H + 7f; bvy[i] = -bvy[i]; }
        checkLevelCleared();
    }

    private void checkLevelCleared() {
        for (int[] row : brickHp)
            for (int hp : row)
                if (hp > 0) return;
        if (level > hi) { hi = level; }
        level++;
        gold += level * 5L;
        newLevel();
        sfx.win();
    }

    private void tapBrick(float lx, float ly) {
        int col = (int) ((lx - brickX()) / brickW());
        int row = (int) ((ly - brickY()) / BRICK_H);
        if (row < 0 || row >= brickRows || col < 0 || col >= COLS) return;
        if (brickHp[row][col] <= 0) return;
        brickHp[row][col] -= 1 + click;
        if (brickHp[row][col] <= 0) {
            gold += brickValue();
            sfx.brick(row);
            checkLevelCleared();
        } else {
            sfx.pop();
        }
    }

    // ── Upgrades ──────────────────────────────────────────────────────────
    private static final String[] UP_NAME = {"DAMAGE", "SPEED", "BALLS", "CLICK"};

    private long upgradeCost(int i) {
        switch (i) {
            case 0: return 10L * (dmg + 1) * (dmg + 1);
            case 1: return 8L * (spd + 1) * (spd + 1);
            case 2: return 50L * (balls + 1) * (balls + 1);
            default: return 6L * (click + 1) * (click + 1);
        }
    }

    private void buyUpgrade(int i) {
        if (i == 2 && balls >= MAX_BALLS - 1) return;
        long cost = upgradeCost(i);
        if (gold < cost) { sfx.blip(); return; }
        gold -= cost;
        switch (i) {
            case 0: dmg++; break;
            case 1: spd++; break;
            case 2:
                balls++;
                // grow the arrays before publishing nBalls so the game thread
                // never reads the new count with the old, shorter arrays
                bx = java.util.Arrays.copyOf(bx, nBalls + 1);
                by = java.util.Arrays.copyOf(by, nBalls + 1);
                bvx = java.util.Arrays.copyOf(bvx, nBalls + 1);
                bvy = java.util.Arrays.copyOf(bvy, nBalls + 1);
                spawnBall(nBalls);
                nBalls++;
                break;
            default: click++; break;
        }
        sfx.eat(i);
        saveAll();
    }

    private void drawUpgrades(Canvas c) {
        float bw = 180f, bh = 52f, gap = 10f;
        float[] xs = {14f, 196f};
        float[] ys = {LH - 190f, LH - 128f};   // above the paddle, not over it
        for (int i = 0; i < 4; i++) {
            float x = xs[i % 2], y = ys[i / 2];
            upBtn[i].set(x, y, x + bw, y + bh);
            long cost = upgradeCost(i);
            boolean maxed = i == 2 && balls >= MAX_BALLS - 1;
            boolean afford = gold >= cost && !maxed;
            p.setStyle(Paint.Style.FILL);
            p.setColor(afford ? 0xFF2A2410 : 0xFF1C1810);
            c.drawRoundRect(upBtn[i], 14, 14, p);
            p.setColor(afford ? 0xFFFFB020 : 0x66443322);
            p.setStrokeWidth(2);
            p.setStyle(Paint.Style.STROKE);
            c.drawRoundRect(upBtn[i], 14, 14, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeWidth(0);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(17);
            p.setFakeBoldText(true);
            p.setColor(afford ? 0xFFF9F6F2 : 0x66F9F6F2);
            c.drawText(UP_NAME[i], x + bw / 2, y + 22, p);
            p.setFakeBoldText(false);
            p.setTextSize(13);
            p.setColor(afford ? 0xFFFFB020 : 0x66FFB020);
            c.drawText(maxed ? "MAX" : "cost " + cost, x + bw / 2, y + 42, p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (super.onTouchEvent(e)) return true;
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
        float lx = (e.getX() - ox) / uScale, ly = (e.getY() - oy) / uScale;
        if (screen == MENU) { screen = PLAY; sfx.pop(); return true; }
        for (int i = 0; i < 4; i++) {
            if (upBtn[i].contains(lx, ly)) { buyUpgrade(i); return true; }
        }
        tapBrick(lx, ly);
        return true;
    }

    // ── Renderer ──────────────────────────────────────────────────────────
    private void drawBricks(Canvas c) {
        float bx0 = brickX(), by0 = brickY(), bw = brickW();
        for (int r = 0; r < brickRows; r++) {
            for (int col = 0; col < COLS; col++) {
                int hp = brickHp[r][col];
                if (hp <= 0) continue;
                float x = bx0 + col * bw, y = by0 + r * BRICK_H;
                // colour fades as hp drops
                int base = 0xFFFFB020;
                int shade = hp >= 3 ? 0xFFE8A020 : (hp == 2 ? 0xFFC08020 : 0xFF906020);
                p.setStyle(Paint.Style.FILL);
                p.setColor(shade);
                c.drawRoundRect(x + 1, y + 1, x + bw - 1, y + BRICK_H - 1, 5, 5, p);
                p.setColor(0x40FFFFFF);
                c.drawRoundRect(x + 3, y + 3, x + bw - 3, y + BRICK_H * 0.45f, 4, 4, p);
                if (hp > 1) {
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(13);
                    p.setFakeBoldText(true);
                    p.setColor(0xCCFFFFFF);
                    c.drawText(String.valueOf(hp), x + bw / 2, y + BRICK_H / 2 + 5, p);
                    p.setFakeBoldText(false);
                }
            }
        }
    }

    private void drawBalls(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < nBalls; i++) {
            p.setColor(0xFFFFD166);
            c.drawCircle(bx[i], by[i], 6f, p);
            p.setColor(0x66FFFFFF);
            c.drawCircle(bx[i] - 2, by[i] - 2, 2f, p);
        }
    }

    private void drawPaddle(Canvas c) {
        if (nBalls == 0) return;
        float px = Math.max(30f, Math.min(LW - 30f, bx[0]));
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFB020);
        c.drawRoundRect(px - 30f, LH - 212f, px + 30f, LH - 200f, 6, 6, p);
        p.setColor(0x40FFFFFF);
        c.drawRoundRect(px - 30f, LH - 212f, px + 30f, LH - 207f, 3, 3, p);
    }
}