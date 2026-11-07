<p align="center">
  <img src="app-logo/nci-app-logo.jpg.jpeg" alt="Salgam logo" width="140">
</p>

<h1 align="center">Salgam</h1>

<p align="center">
  An offline, privacy-first night-out tracker for Android.<br>
  Drinks, water and the bar tab: everything stays on your phone.
</p>

---

## What it does

- **Tonight**: one big +1 for your drink, a water counter next to it, an optional drink timer.
- **Tab**: who had what and what it costs; split and settle at the end of the night.
- **Versus & Together**: track a night with one friend (red vs blue) or a whole table.
- **Game**: a Truth or Dare style table game, with your own questions.
- **History**: past nights, filters and simple stats.
- **Health**: water reminders, a care check late at night and a rough blood-alcohol *estimate* (never use it to decide whether you can drive).
- **Widgets, quick tile and shortcuts**: +1 from the home screen.
- **Privacy**: PIN lock, local JSON backup & restore, "delete all data". English and Turkish.

## Privacy

- No account, no ads, no analytics, no crash reporting, no tracking.
- No internet permission: the app cannot send anything anywhere.
- Backups are files you export and keep yourself.

## Build

Requirements: JDK 17+, Android SDK 37. From the repo root:

```sh
./gradlew assembleDebug        # debug APK
./gradlew installDebug         # install on a connected device
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # lint
./gradlew assembleRelease      # release APK -> release/Salgam-v<version>.apk
```

Release signing reads a local, gitignored `signing.properties`; without it the release build is unsigned.

Tech: Kotlin, Jetpack Compose (Material 3), Room, DataStore. Min Android 8.0 (API 26).
An iOS (SwiftUI) port is in progress in [`ios/`](ios/).

## Contact

Ideas, requests or a problem? Email **yusufridvancelikbas@gmail.com** (also in the app under Settings → About & contact).

## License

© 2026 Yusuf Ridvan Celikbas. All rights reserved.
No part of this code may be copied, modified or distributed without written permission.
