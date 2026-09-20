# AGENTS.md — GamePlayer `:arcade` module (Tiny Arcade)

Nine tiny games (Snake, 2048, Flappy, Brick Break, Whac-a-mole, Memory, Flood
Fill, Arrow Slide, Idle Bricks) as a library module inside GamePlayer. Pure
framework Android, `View`/`SurfaceView` only — no Compose, no WebView, no
fragments, no deps. Package `com.bee.arcade`, minSdk 26, compileSdk 36. Merged
from the standalone `~/dev/TinyArcade` app on 2026-09-20 (snapshot:
`~/backups/TinyArcade-pre-merge-2026-09-20`). The three native minis (Flood
Fill, Arrow Slide, Idle Bricks) replaced the heavy `:flowit` / `:anuto`
modules and the Flutter Arrow Escape AAR on 2026-09-20. The minis are
inspired-by originals, deliberately renamed because they are not the same
games.

## How it fits in GamePlayer

- `:arcade` is a **library** — no applicationId, no launcher activity, no
  picker. The GamePlayer launcher (`:app`) lists the nine games as its primary
  cards and hosts each one in `TinyGameActivity`.
- `ArcadeGames.create(id, ctx, goHome)` is the **only public seam** — the game
  classes stay package-private. Game ids 0-5 match the old TinyArcade picker so
  the `"arcade"` prefs keys keep working; ids 6-8 are the native minis.
- `BaseGameView` is public (the host activity calls `activityPause()` /
  `activityResume()`); `HomeListener` stays package-private — the factory wraps
  a `Runnable`.
- The in-game home button calls `goHome()` → `TinyGameActivity.finish()` (back
  to the launcher). System back does the same.

## Build / install

```bash
./gradlew :app:assembleRelease        # builds :arcade as a dependency
adb install -r app/build/outputs/apk/release/app-release.apk
```

- **No test suite** — verify by building and playing on a device/emulator.
- R8 runs at the app level; the nine games are kept via the factory references.

## Architecture (read before editing)

| File | Purpose |
|---|---|
| `ArcadeGames.java` | Public factory: `create(int id, Context, Runnable goHome) -> View` |
| `BaseGameView.java` | Shared `SurfaceView` shell: loop, uniform logical scale, insets, home/pause chrome, pause overlay, vibration, prefs, procedural audio |
| `Sfx.java` | Shared soft/plucked serialised audio |
| `SnakeGame.java` / `T2048Game.java` / `FlappyGame.java` / `SinkBreakGame.java` / `WhacGame.java` / `MemoryGame.java` / `FloodFillGame.java` / `ArrowSlideGame.java` / `IdleBricksGame.java` | Games as `BaseGameView` subclasses |

### BaseGameView contract (subclasses implement)

- `onUpdate(dt)` — called on the game thread only while `isLive() && !paused`.
- `drawGame(Canvas)` — draw on the already-scaled logical canvas (LW 390 × LH 700).
- `isLive()` — true only while a game is actually in progress (not menu/over).
- `saveAll()` — persist scores on background/destroy.
- `hidChrome()` — hide the home/pause buttons on menu screens (default false).
- `bgColor()`, `buzz()`, `centerText()`, `sfx`, `prefs`, `p`, `frame` available.
- **Menus use the shared template** `drawMenu(c, title, subtitle, best, accent)`
  (title block at LH*0.28, pulsing TAP TO START at LH*0.88). Call it AFTER
  drawing the game's demo/field so the text overlays it. All nine games use it —
  keep menu text positions consistent.

### Invariants (do not break)

- **Uniform scale, letterboxed:** `uScale = min(w/LW, hpx/LH)`, centred with
  `ox/oy`. Touch coords are `(e.getX()-ox)/uScale`. Never use separate scaleX/scaleY.
- **Chrome is drawn by `BaseGameView` after `drawGame`:** home button top-left
  `(12, topPad+8)`, pause top-right `(LW-58..LW-20)`, both ~38 logical units wide.
  Subclass HUDs must fit in the centre (e.g. snake/2048/sink centre their score).
- **Pause is centralised:** `BaseGameView` owns pause/pause-overlay/resume. The
  same UP that taps pause won't insta-resume (`resumeOnUp`). Subclasses must call
  `super.onTouchEvent(e)` first and act only when it returns `false`.
- **`onDraw` is taken by `View`** — the subclass draw hook is `drawGame`, do not
  reintroduce `onDraw` in a `BaseGameView` subclass.
- **`onApplyWindowInsets` top inset is max-only** — avoids jitter while bars hide.
- **Audio:** `Sfx` is the only sound source; it is soft (sine/pluck, envelope,
  volume ≈0.30), serialised on one thread, throttled to ≥24 ms. Don't add raw
  square/saw tones or overlapping tracks.
- **Score keys** in prefs `"arcade"`: `s_hi`, `t_hi`, `f_hi`, `k_hi`, `w_hi`,
  `m_hi` (ids 0-5 — do not rename, scores from the standalone TinyArcade app
  carry over), plus `ff_hi` (Flood Fill), `as_hi` (Arrow Slide) and the idle
  keys `ib_gold`, `ib_dmg`, `ib_spd`, `ib_balls`, `ib_click`, `ib_hi`.

## Games

- **Snake** — grid+swipe, wrap edges, glide between ticks, bevel chips, menu demo snake.
- **2048** — swipe to slide/merge; swipe DOWN drives tiles down; NEW restarts; endless after 2048.
- **Flappy** — one-tap flap, constant pipe speed (150 u/s), Best persisted.
- **Brick Break** — classic brick breaker: static hand-built levels (brick HP 1-4),
  angle-controlled paddle, 3 lives, level progression, win after the final level.
- **Whac-a-mole** — 30 s arcade timer, up to two moles at once, combo chains
  (×5 cap), score popups, splat flash, best persisted (`w_hi`).
- **Memory** — 4×4 pairs; flip two per turn, mismatch shows for 0.9 s then hides;
  best score is the lowest move count (`m_hi`, 0 = unset → shown as "—").
- **Flood Fill** — Flowit-style tool puzzle: tap a TOOL cell (flood compass or
  direction arrow) and it fills EMPTY cells with its colour; if nothing can be
  filled it clears its colour instead. Win = every cell matches its faint
  target colour (tool cells skipped). 15 procedural levels, then
  "ALL LEVELS CLEARED" (`ff_hi` = best level). Levels: one flood tool + one
  straight-line tool per other colour; lines first, flood last.
- **Arrow Slide** — slide every arrow off the board: tap an arrow to slide it;
  clear path to the edge = it escapes, hitting another arrow = crash (both
  destroyed, −1 life). 3 lives, endless procedural levels (`as_hi` = best level).
- **Idle Bricks** — bricks with HP, balls auto-bounce and deal damage,
  tap a brick for click damage, gold per brick. Four upgrades (damage / speed /
  balls / click) bought from the 2×2 panel at the bottom; gold and upgrades
  persist (`ib_*`). Endless levels.
- **Shared `chip()` HUD helper** lives in `BaseGameView` (dark rounded rect,
  label over bold value) — used by 2048, Whac, Memory, Flood Fill, Arrow Slide
  and Idle Bricks; don't re-implement it.