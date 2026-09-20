package com.bee.arcade;

import android.content.Context;
import android.view.View;

/**
 * Public entry point for the Tiny Arcade games from the host app. The game
 * classes themselves stay package-private; this factory is the only seam the
 * launcher needs. Game ids match the old TinyArcade picker (0-5) so the
 * "arcade" prefs keys (s_hi, t_hi, f_hi, k_hi, w_hi, m_hi) keep working.
 */
public final class ArcadeGames {

    public static final int SNAKE = 0;
    public static final int T2048 = 1;
    public static final int FLAPPY = 2;
    public static final int BRICK = 3;
    public static final int WHAC = 4;
    public static final int MEMORY = 5;

    private ArcadeGames() {}

    /** Build a game view. {@code goHome} fires when the in-game home button is tapped. */
    public static View create(int id, Context ctx, Runnable goHome) {
        BaseGameView.HomeListener home = goHome::run;
        switch (id) {
            case SNAKE:  return new SnakeGame(ctx, home);
            case T2048:  return new T2048Game(ctx, home);
            case FLAPPY: return new FlappyGame(ctx, home);
            case BRICK:  return new SinkBreakGame(ctx, home);
            case WHAC:   return new WhacGame(ctx, home);
            default:     return new MemoryGame(ctx, home);
        }
    }
}