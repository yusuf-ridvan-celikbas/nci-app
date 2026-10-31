# CansinApp
Native, offline-first beer/bar-tab tracker (Android now, iOS in P12). Package `com.cansin.app`.

Spec: prompt/prompt.md — read its Rules section before any work.
Progress: PROGRESS.md — read this first instead of re-scanning the code; update it at the end of every phase.

## Commands (Git Bash, repo root)
- Build debug: `./gradlew assembleDebug`
- Install on device: `./gradlew installDebug` (device via adb)
- Unit tests: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug` (report: app/build/reports/lint-results-debug.html)
- Release (R8): `./gradlew assembleRelease bundleRelease`
- Merged manifest: app/build/intermediates/merged_manifest/release/

## Top rules (full list in spec)
1. Ask before adding any dependency (size + why the SDK can't do it).
2. No analytics/ads/crash SDKs/tracking, no WebView, no dynamic code, no clipboard reads; no INTERNET permission until P13.
3. Zero hardcoded UI strings (EN default + TR); all colors via theme tokens (Versus RED/BLUE fixed).
4. Per phase: implement → unit-test domain logic → lint → report release size, permissions, security trade-offs.
5. Ambiguity → ask, don't guess. Store policy: never encourage drinking; no rewards for quantity.

Versions: AGP 9.4.1 (built-in Kotlin), Kotlin 2.2.10, Gradle 9.6.0 wrapper, compile/target SDK 37, minSdk 26.
