# GamePlayer ("Mini Games")

Single-APK launcher wrapping several open-source games:

- **Brick Breaker** — native module `:brickbreaker` (source: `~/dev/BrickBreaker`)
- **Flowit** — native module `:flowit`
- **Anuto TD** — native module `:anuto`
- **Arrow Escape** — the real Flutter game ([sidhant947/ArrowEscape](https://github.com/sidhant947/ArrowEscape)),
  embedded as a Flutter AAR and launched via `FlutterActivity`
- **2048**, **Minesweeper** — web builds in `app/src/main/assets/`, shown in `WebGameActivity`
- **Idle Brick Breaker** — not included: the TechTreeGames original is closed-source and
  no faithful open-source clone exists (greyed-out note card in the list)

Upstream clones live in `games-src/` (gitignored).

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

Build/install: `./gradlew :app:assembleRelease` (release is signed with the debug key;
R8 keeps for Flutter are in `app/proguard-rules.pro`), then
`adb install -r app/build/outputs/apk/release/app-release.apk`.
