# GamePlayer ("Mini Games")

Single-APK launcher. The **Tiny Arcade** set is primary — six tiny framework-only
games (Snake, 2048, Flappy, Brick Break, Whac-a-mole, Memory) in the `:arcade`
module — plus the heavier open-source games:

- **Tiny Arcade** — native module `:arcade` (package `com.bee.arcade`, pure Java,
  no deps). Six games hosted by `TinyGameActivity`; scores persist under the
  `"arcade"` prefs file (same keys as the old standalone TinyArcade app).
  Merged 2026-09-20 from `~/dev/TinyArcade` (snapshot in `~/backups/TinyArcade-pre-merge-2026-09-20`).
  The old `~/dev/TinyGames` three-APK set (Snake/2048/Flappy) is fully subsumed —
  all three games already existed in TinyArcade.
- **Flowit** — native module `:flowit`
- **Anuto TD** — native module `:anuto`
- **Arrow Escape** — the real Flutter game ([sidhant947/ArrowEscape](https://github.com/sidhant947/ArrowEscape)),
  embedded as a Flutter AAR and launched via `FlutterActivity`
- **Minesweeper** — web build in `app/src/main/assets/`, shown in `WebGameActivity`
- **Idle Brick Breaker** — not included: the TechTreeGames original is closed-source and
  no faithful open-source clone exists (greyed-out note card in the list)

Superseded on merge: the old Kotlin `:brickbreaker` module and the web 2048
build were removed from the launcher (Tiny Arcade's Brick Break / 2048 replace
them). Both are recoverable from git history.

Upstream clones live in `games-src/` (gitignored).

## Building

```sh
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

- `local.properties` has `sdk.dir=/mnt/steamgames/home-cold/android-sdk`.
- Release is signed with the debug key; R8 keeps for Flutter are in
  `app/proguard-rules.pro`.
- **No test suite** — verify by building and playing on a device/emulator.

## Rebuilding the Arrow Escape AAR

The AAR is consumed from `games-src/arrowescape_module/build/host/outputs/repo`, which is
gitignored, so after a fresh checkout it must be rebuilt:

```sh
export PATH="$HOME/dev/flutter/bin:$PATH" ANDROID_HOME="$HOME/Android/Sdk"
cd games-src/arrowescape_module   # Flutter module wrapping sidhant947/ArrowEscape's lib/ + assets/
flutter pub get
flutter build aar --build-number 1
```

Then **strip the engine jars** or the APK balloons to ~480 MB (the artifacts on
`download.flutter.io` ship with full debug info, and AGP does not strip them):

```sh
STRIP=~/Android/Sdk/ndk/<ndk-version>/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip
# for each io/flutter/{arm64_v8a,armeabi_v7a,x86_64}_release/<hash>/ jar in the local repo:
#   unzip, $STRIP --strip-unneeded lib/<abi>/libflutter.so, re-zip
# (see git history for the exact loop)
```

## Tiny Arcade module rules

See `arcade/AGENTS.md` — the BaseGameView contract and invariants are load-bearing.