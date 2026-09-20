package com.bee.arcade;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

/**
 * Brick Break — classic static-level brick breaker. Hand-built levels (brick HP
 * 1-4), angle-controlled paddle, 3 lives, level progression, win after the last
 * level. Style of the /dev/BrickBreaker build, slimmed to fit the arcade shell.
 */
final class SinkBreakGame extends BaseGameView {

    private static final int MENU = 0, PLAY = 1, OVER = 2, WON = 3;
    private int screen = MENU;

    private static final int COLS = 8, ROWS = 8;
    private static final float BW = 42f, BH = 18f, GAP = 3f, STRIDE = BH + GAP;
    private static final float BOX = (LW - (COLS * (BW + GAP) - GAP)) / 2f;

    private static final float BALL_R = 7f;
    private static final float PADDLE_W = 104f;
    private static final float PADDLE_H = 13f;
    private static final float PADDLE_Y = LH - 44f;

    // ── Physics (borrowed from the standalone BrickBreaker) ─────────────
    private static final float BASE_SPEED = 330f;
    private static final float MAX_BOUNCE_ANGLE = 62f;   // degrees from vertical at paddle edge
    private static final float MIN_VY_FRAC = 0.36f;      // floor so the ball never crawls sideways
    private static final float PADDLE_ENGLISH = 0.14f;   // fingertip motion adds spin

    private final int[][] hp = new int[ROWS][COLS];
    private int li, bricksLeft, score, best, lives;

    private float ballX = LW / 2f, ballY = PADDLE_Y - BALL_R - 3f;
    private float ballVx, ballVy;
    private boolean ballReady = true;
    private float paddleX = LW / 2f, prevPaddleX = LW / 2f, paddleVx;

    private float levelFlash;                              // "LEVEL n CLEAR!" beat
    private int lastCleared;                               // level number shown in the banner

    private final float[][] trail = new float[9][2];
    private int trailW;
    private final float[][] sparks = new float[24][5];     // x, y, vx, vy, life
    private int sparkW;

    // Static levels: brick HP per cell (0 = empty). HP 1-4, rows 4-8.
    private static final int[][][] LEVELS = {
        { // 1 — full little wall
            {1,1,1,1,1,1,1,1}, {1,1,1,1,1,1,1,1},
            {1,1,1,1,1,1,1,1}, {1,1,1,1,1,1,1,1}
        },
        { // 2 — checkerboard
            {1,0,1,0,1,0,1,0}, {0,1,0,1,0,1,0,1}, {1,0,1,0,1,0,1,0},
            {0,1,0,1,0,1,0,1}, {1,0,1,0,1,0,1,0}
        },
        { // 3 — diamond
            {0,0,1,1,1,1,0,0}, {0,1,1,1,1,1,1,0}, {1,1,1,1,1,1,1,1},
            {0,1,1,1,1,1,1,0}, {0,0,1,1,1,1,0,0}, {0,0,0,1,1,0,0,0}
        },
        { // 4 — pillars with base
            {1,0,0,0,0,0,0,1}, {1,0,0,0,0,0,0,1}, {1,1,1,1,1,1,1,1},
            {1,0,0,0,0,0,0,1}, {1,0,0,0,0,0,0,1}
        },
        { // 5 — rainbow stripes, tougher downward
            {1,1,1,1,1,1,1,1}, {2,2,2,2,2,2,2,2},
            {3,3,3,3,3,3,3,3}, {4,4,4,4,4,4,4,4}
        },
        { // 6 — hollow frame
            {2,2,2,2,2,2,2,2}, {2,0,0,0,0,0,0,2}, {2,0,1,1,1,1,0,2},
            {2,0,1,1,1,1,0,2}, {2,0,1,1,1,1,0,2}, {2,0,0,0,0,0,0,2},
            {2,2,2,2,2,2,2,2}
        },
        { // 7 — hexagon
            {1,0,0,0,0,0,0,1}, {1,1,0,0,0,0,1,1}, {0,1,1,0,0,1,1,0},
            {0,0,1,1,1,1,0,0}, {0,0,0,1,1,0,0,0}, {0,0,1,1,1,1,0,0},
            {1,0,1,0,0,1,0,1}
        },
        { // 8 — armoured core
            {2,2,2,2,2,2,2,2}, {2,3,3,3,3,3,3,2}, {2,3,2,2,2,2,3,2},
            {2,3,2,0,0,2,3,2}, {2,3,2,2,2,2,3,2}, {2,3,3,3,3,3,3,2},
            {2,2,2,2,2,2,2,2}
        },
        { // 9 — ring on frame
            {3,3,3,3,3,3,3,3}, {3,0,0,0,0,0,0,3}, {3,0,2,2,2,2,0,3},
            {3,0,2,0,0,2,0,3}, {3,0,2,2,2,2,0,3}, {3,0,0,0,0,0,0,3},
            {3,3,3,3,3,3,3,3}
        },
        { // 10 — towers
            {4,0,4,0,4,0,4,0}, {4,0,4,0,4,0,4,0}, {4,0,4,0,4,0,4,0},
            {4,0,4,0,4,0,4,0}, {2,2,2,2,2,2,2,2}, {3,3,3,3,3,3,3,3}
        },
        { // 11 — weave
            {1,3,1,3,1,3,1,3}, {3,1,3,1,3,1,3,1}, {1,3,1,3,1,3,1,3},
            {3,1,3,1,3,1,3,1}, {1,3,1,3,1,3,1,3}, {3,1,3,1,3,1,3,1}
        },
        { // 12 — fortress
            {4,4,4,4,4,4,4,4}, {4,3,3,3,3,3,3,4}, {4,3,2,2,2,2,3,4},
            {4,3,2,1,1,2,3,4}, {4,3,2,1,1,2,3,4}, {4,3,2,2,2,2,3,4},
            {4,3,3,3,3,3,3,4}, {4,4,4,4,4,4,4,4}
        }
    };

    SinkBreakGame(Context ctx, HomeListener home) {
        super(ctx, home);
        best = prefs.getInt("k_hi", 0);
        li = 0;
        loadLevel();
    }

    // ── Contract ──────────────────────────────────────────────────────────
    @Override
    void onUpdate(float dt) {
        levelFlash -= dt;
        if (levelFlash > 0) return;

        for (int i = 0; i < sparkW; ) {
            float[] s = sparks[i];
            s[0] += s[2] * dt; s[1] += s[3] * dt;
            s[3] += 260f * dt;
            s[4] -= 2.2f * dt;
            if (s[4] <= 0f) { sparks[i] = sparks[--sparkW]; }
            else i++;
        }

        if (ballReady) {
            ballX = paddleX;
            ballY = PADDLE_Y - BALL_R - 3f;
            return;
        }

        // fingertip velocity feeds paddle "english" (spin steering)
        float pvx = (paddleX - prevPaddleX) / Math.max(dt, 0.0001f);
        paddleVx += (pvx - paddleVx) * Math.min(1f, 12f * dt);
        prevPaddleX = paddleX;

        float bx0 = ballX, by0 = ballY;
        ballX += ballVx * dt;
        ballY += ballVy * dt;

        if (ballX < BALL_R) { ballX = BALL_R; ballVx = Math.abs(ballVx); sfx.bounce(); }
        if (ballX > LW - BALL_R) { ballX = LW - BALL_R; ballVx = -Math.abs(ballVx); sfx.bounce(); }
        float ceilY = brickY(0) - 6f;
        if (ballY < ceilY + BALL_R) { ballY = ceilY + BALL_R; ballVy = Math.abs(ballVy); }

        if (ballVy > 0 && by0 + BALL_R <= PADDLE_Y && ballY + BALL_R >= PADDLE_Y
                && ballX >= paddleX - PADDLE_W / 2f - BALL_R && ballX <= paddleX + PADDLE_W / 2f + BALL_R) {
            hitPaddle();
        }

        detectBricks();
        normaliseSpeed();

        if (ballY > LH + 30f) loseLife();
    }

    @Override
    void drawGame(Canvas c) {
        drawBackdrop(c);
        drawBricks(c);
        drawTrail(c);
        drawPaddleAndBall(c);
        if (screen == MENU) drawMenu(c);
        else if (screen == WON) {
            drawHud(c);
            c.drawColor(0x88000000);
            centerText(c, "ALL LEVELS CLEAR!", LW / 2, LH * 0.28f, 34, 0xFF6BFF8A);
            centerText(c, "score  " + score, LW / 2, LH * 0.28f + 42, 26, 0xFFD7E8FF);
            centerText(c, "best   " + Math.max(best, score), LW / 2, LH * 0.28f + 72, 20, 0x99D7E8FF);
            endButtons(c, new String[]{"PLAY AGAIN", "HOME"}, 0xFF4FC3F7);
        } else {
            drawHud(c);
            if (levelFlash > 0) centerText(c, "LEVEL " + lastCleared + " CLEAR!", LW / 2, LH * 0.42f, 34, 0xCC6BFF8A);
            if (screen == OVER) { c.drawColor(0x99000000); gameOverPanel(c); }
        }
    }

    @Override boolean isLive() { return screen == PLAY; }
    @Override void saveAll() { prefs.edit().putInt("k_hi", Math.max(best, score)).apply(); }
    @Override boolean hidChrome() { return screen == MENU; }
    @Override int bgColor() { return 0xFF081123; }

    // ── Game logic ────────────────────────────────────────────────────────
    private void startGame() {
        score = 0;
        lives = 3;
        li = 0;
        levelFlash = 0;
        lastCleared = 0;
        ballReady = true;
        paddleX = LW / 2f;
        prevPaddleX = paddleX;
        paddleVx = 0;
        trailW = 0;
        sparkW = 0;
        loadLevel();
        screen = PLAY;
        sfx.pop();
    }

    private void loadLevel() {
        int[][] def = LEVELS[li];
        bricksLeft = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < COLS; col++) {
                int v = r < def.length ? def[r][col] : 0;
                hp[r][col] = v;
                if (v != 0) bricksLeft++;
            }
        }
        ballReady = true;
        ballX = paddleX;
        ballY = PADDLE_Y - BALL_R - 3f;
    }

    private void advanceLevel() {
        levelFlash = 1.35f;
        if (li + 1 >= LEVELS.length) {
            saveAll();
            sfx.win();
            screen = WON;
            return;
        }
        lastCleared = li + 1;
        li++;
        loadLevel();
        sfx.win();
    }

    /** Arcade continue: keep the score, retry the current level at full lives. */
    private void continueRun() {
        lives = 3;
        levelFlash = 0;
        paddleVx = 0;
        trailW = 0;
        sparkW = 0;
        loadLevel();
        screen = PLAY;
        sfx.pop();
    }

    private void hitPaddle() {
        float rel = (ballX - paddleX) / (PADDLE_W / 2f);
        rel = Math.max(-1f, Math.min(1f, rel));
        rel = Math.max(-1.1f, Math.min(1.1f, rel + (paddleVx / BASE_SPEED) * PADDLE_ENGLISH));
        double ang = rel * MAX_BOUNCE_ANGLE * Math.PI / 180.0;
        float nvy = -Math.abs((float) Math.cos(ang) * BASE_SPEED);
        float nvx = (float) Math.sin(ang) * BASE_SPEED;
        float minVy = BASE_SPEED * MIN_VY_FRAC;
        if (-nvy < minVy) {
            nvy = -minVy;
            float sgn = nvx >= 0 ? 1f : -1f;
            nvx = sgn * (float) Math.sqrt(Math.max(0f, BASE_SPEED * BASE_SPEED - minVy * minVy));
        }
        ballVx = nvx;
        ballVy = nvy;
        sfx.bounce();
    }

    private void normaliseSpeed() {
        float spd = (float) Math.hypot(ballVx, ballVy);
        if (spd > 0f && Math.abs(spd - BASE_SPEED) > 16f) {
            float f = BASE_SPEED / spd;
            ballVx *= f;
            ballVy *= f;
        }
    }

    private void detectBricks() {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int h = hp[row][col];
                if (h == 0) continue;
                float bLeft = brickX(col), bTop = brickY(row);
                float cx = Math.max(bLeft, Math.min(ballX, bLeft + BW));
                float cy = Math.max(bTop, Math.min(ballY, bTop + BH));
                float dx = ballX - cx, dy = ballY - cy;
                if (dx * dx + dy * dy > BALL_R * BALL_R) continue;

                hp[row][col]--;
                if (hp[row][col] == 0) {
                    bricksLeft--;
                    int gain = 10 + row * 2;
                    score += gain;
                    if (score > best) best = score;
                    burst(bLeft + BW / 2f, bTop + BH / 2f);
                    sfx.brick(col);
                    if (bricksLeft == 0) { advanceLevel(); return; }
                } else {
                    sfx.bounce();
                }

                float bcx = bLeft + BW / 2f, bcy = bTop + BH / 2f;
                float fx = (BW / 2f + BALL_R) - Math.abs(ballX - bcx);
                float fy = (BH / 2f + BALL_R) - Math.abs(ballY - bcy);
                if (fx < fy) {
                    ballVx = ballX < bcx ? -Math.abs(ballVx) : Math.abs(ballVx);
                    ballX += ballX < bcx ? -fx : fx;
                } else {
                    ballVy = ballY < bcy ? -Math.abs(ballVy) : Math.abs(ballVy);
                    ballY += ballY < bcy ? -fy : fy;
                }
                return;
            }
        }
        // add a trail sample only after a clean move
        trailW = Math.min(trail.length, trailW + 1);
        for (int i = trailW - 1; i > 0; i--) { trail[i][0] = trail[i - 1][0]; trail[i][1] = trail[i - 1][1]; }
        trail[0][0] = ballX; trail[0][1] = ballY;
    }

    private void burst(float x, float y) {
        for (int i = 0; i < 8 && sparkW < sparks.length; i++, sparkW++) {
            double a = Math.random() * Math.PI * 2;
            float sp = 60f + (float) Math.random() * 130f;
            sparks[sparkW][0] = x; sparks[sparkW][1] = y;
            sparks[sparkW][2] = (float) Math.cos(a) * sp;
            sparks[sparkW][3] = (float) Math.sin(a) * sp - 30f;
            sparks[sparkW][4] = 1f;
        }
    }

    private void loseLife() {
        lives--;
        buzz();
        sfx.down();
        if (lives <= 0) { gameOver(); return; }
        ballReady = true;
        ballX = paddleX;
        ballY = PADDLE_Y - BALL_R - 3f;
    }

    private void gameOver() {
        saveAll();
        buzz();
        sfx.down();
        screen = OVER;
    }

    private void launch() {
        ballReady = false;
        ballVx = ((paddleX - LW / 2f) / (LW / 2f)) * 150f;   // first serve follows where you aimed
        ballVy = -BASE_SPEED;
        sfx.blip();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (super.onTouchEvent(e)) return true;
        float lx = (e.getX() - ox) / uScale, ly = (e.getY() - oy) / uScale;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (screen == PLAY) {
                    paddleX = Math.max(PADDLE_W / 2f, Math.min(LW - PADDLE_W / 2f, lx));
                    if (ballReady && levelFlash <= 0 && ly >= brickY(0)) launch();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (screen == PLAY) {
                    paddleX = Math.max(PADDLE_W / 2f, Math.min(LW - PADDLE_W / 2f, lx));
                    if (ballReady) { ballX = paddleX; }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (screen == OVER || screen == WON) {
                    int b = pressedEndButton(lx, ly);
                    if (screen == OVER) {
                        if (b == 0) continueRun();
                        else if (b == 1) startGame();
                        else if (b == 2) goHome();
                    } else {
                        if (b == 0) startGame();
                        else if (b == 1) goHome();
                    }
                    return true;
                }
                if (screen == MENU) startGame();
                return true;
        }
        return true;
    }

    // ── Renderer ──────────────────────────────────────────────────────────
    private float brickX(int col) { return BOX + col * (BW + GAP); }
    private float brickY(int row) { return topPad() + 48f + row * STRIDE; }

    private void drawBackdrop(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0C1830);
        c.drawRoundRect(BOX - 8, brickY(0) - 10, BOX + COLS * BW + (COLS - 1) * GAP + 8,
                PADDLE_Y + PADDLE_H + 9, 16, 16, p);
    }

    private void drawBricks(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int h = hp[row][col];
                if (h == 0) continue;
                int[] pal = PAL[h - 1];
                float x = brickX(col), y = brickY(row);
                p.setColor(0xFF000000 | pal[0]);
                c.drawRoundRect(x, y, x + BW, y + BH, 6, 6, p);
                p.setColor(0x40FFFFFF);
                c.drawRoundRect(x + 3, y + 3, x + BW - 8, y + BH * 0.45f, 4, 4, p);

                if (h > 1) {
                    float pip = Math.max(2.5f, BW * 0.14f);
                    float px = x + BW / 2f - (h * pip + (h - 1) * 2f) / 2f;
                    for (int i = 0; i < h; i++) {
                        p.setColor(0xFFFFFFFF);
                        c.drawCircle(px + pip / 2f, y + BH - 5f, pip / 2f, p);
                        p.setColor(0xAA555555);
                        p.setStyle(Paint.Style.STROKE);
                        c.drawCircle(px + pip / 2f, y + BH - 5f, pip / 2f, p);
                        p.setStyle(Paint.Style.FILL);
                        px += pip + 2f;
                    }
                }
            }
        }
        // sparks
        for (int i = 0; i < sparkW; i++) {
            float[] s = sparks[i];
            p.setColor((((int) (s[4] * 255)) << 24) | 0xFFFFFF);
            c.drawCircle(s[0], s[1], 2.2f * s[4], p);
        }
    }

    private void drawTrail(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        for (int i = trailW - 1; i >= 1; i--) {
            float a = 1f - i / (float) trail.length;
            p.setColor(((int) (a * 120)) << 24 | 0xCFFFFF6B);
            c.drawCircle(trail[i][0], trail[i][1], BALL_R * (1f - i / (float) trail.length), p);
        }
    }

    private void drawPaddleAndBall(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x334FC3F7);
        c.drawRoundRect(paddleX - PADDLE_W / 2f - 5, PADDLE_Y - 3, paddleX + PADDLE_W / 2f + 5,
                PADDLE_Y + PADDLE_H + 3, 10, 10, p);
        p.setColor(0xFF4FC3F7);
        c.drawRoundRect(paddleX - PADDLE_W / 2f, PADDLE_Y, paddleX + PADDLE_W / 2f,
                PADDLE_Y + PADDLE_H, 8, 8, p);
        p.setColor(0x44FFFFFF);
        c.drawRoundRect(paddleX - PADDLE_W / 2f + 4, PADDLE_Y + 2.5f, paddleX + PADDLE_W / 2f - 4,
                PADDLE_Y + PADDLE_H * 0.5f, 6, 6, p);

        if (ballReady) {
            float pulse = 1f + 0.12f * (float) Math.sin(frame / 6.0);
            p.setColor(0x44FFFFFF);
            c.drawCircle(ballX, ballY, BALL_R * 1.8f * pulse, p);
        }
        p.setColor(0xFFFFD84F);
        c.drawCircle(ballX, ballY, BALL_R, p);
        p.setColor(0xFFFFF6C8);
        c.drawCircle(ballX - 2.2f, ballY - 2.2f, BALL_R * 0.45f, p);
    }

    private void drawMenu(Canvas c) {
        drawMenu(c, "BRICK BREAK", "static levels — drag to aim, tap to launch",
                "best  " + best, 0xFF4FC3F7);
    }

    private void drawHud(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(19);
        p.setFakeBoldText(true);
        p.setColor(0xFFFFD84F);
        c.drawText("SCORE " + score, LW / 2 - 62, topPad() + 30, p);
        p.setFakeBoldText(false);
        p.setTextSize(15);
        p.setColor(0xFF4FC3F7);
        c.drawText("LV " + (li + 1), LW / 2 + 24, topPad() + 30, p);
        p.setTextSize(17);
        p.setColor(0xFFFF6B6B);
        String hearts = "";
        for (int i = 0; i < lives; i++) hearts += "♥";
        c.drawText(hearts, LW / 2 + 92, topPad() + 30, p);
    }

    private void gameOverPanel(Canvas c) {
        centerText(c, "GAME OVER", LW / 2, LH * 0.28f, 36, 0xFFFF5D5D);
        centerText(c, "score  " + score, LW / 2, LH * 0.28f + 42, 26, 0xFFD7E8FF);
        centerText(c, "best   " + Math.max(best, score), LW / 2, LH * 0.28f + 72, 20, 0x99D7E8FF);
        endButtons(c, new String[]{"CONTINUE", "PLAY AGAIN", "HOME"}, 0xFF4FC3F7);
    }

    // hp 1..4 — green, amber, red, purple
    private static final int[][] PAL = {
        {0x20944B, 0x141414},
        {0xD9A024, 0x1A1606},
        {0xD95745, 0x200A07},
        {0x8A63D9, 0x160A20}
    };
}