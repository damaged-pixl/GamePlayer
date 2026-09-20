# GamePlayer ("Mini Games")

Single-APK launcher. The **Tiny Arcade** set is primary — nine tiny framework-only
games (Snake, 2048, Flappy, Brick Break, Whac-a-mole, Memory, Flood Fill, Arrow
Slide, Idle Bricks) in the `:arcade` module — plus Minesweeper:

- **Tiny Arcade** — native module `:arcade` (package `com.bee.arcade`, pure Java,
  no deps). Nine games hosted by `TinyGameActivity`; scores persist under the
  `"arcade"` prefs file (same keys as the old standalone TinyArcade app).
  Merged 2026-09-20 from `~/dev/TinyArcade` (snapshot in `~/backups/TinyArcade-pre-merge-2026-09-20`).
  The old `~/dev/TinyGames` three-APK set (Snake/2048/Flappy) is fully subsumed —
  all three games already existed in TinyArcade.
- **Flood Fill** — native mini (id 6, `ff_hi`): Flowit-style tool puzzle, 15 procedural levels
- **Arrow Slide** — native mini (id 7, `as_hi`): slide arrows off the board, 3 lives
- **Idle Bricks** — native mini (id 8, `ib_*`): auto-battle bricks, gold +
  upgrades persist
- **Minesweeper** — web build in `app/src/main/assets/`, shown in `WebGameActivity`

Superseded on merge: the old Kotlin `:brickbreaker` module and the web 2048
build were removed from the launcher (Tiny Arcade's Brick Break / 2048 replace
them). The heavy `:flowit` and `:anuto` modules and the Flutter Arrow Escape AAR
were removed 2026-09-20 — the native minis replace them. All are recoverable
from git history. The minis are inspired-by originals, deliberately renamed
(Flood Fill / Arrow Slide / Idle Bricks) because they are not the same games.

Upstream clones live in `games-src/` (gitignored).

## Building

```sh
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

- `local.properties` has `sdk.dir=/mnt/steamgames/home-cold/android-sdk`.
- Release is signed with the debug key.
- **No test suite** — verify by building and playing on a device/emulator.

## Tiny Arcade module rules

See `arcade/AGENTS.md` — the BaseGameView contract and invariants are load-bearing.