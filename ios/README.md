# CansinApp iOS (P12)

SwiftUI, iOS 17+, SwiftData in an App Group, WidgetKit + App Intents, local notifications.
No third-party code. The project file is generated from `project.yml`.

## Build on a Mac
1. Install Xcode 16+ and XcodeGen (a build tool only, nothing is shipped): `brew install xcodegen`
2. `cd ios && xcodegen` → opens as `CansinApp.xcodeproj`
3. In Xcode set your Team for both targets. Register the App Group `group.com.cansin.app` for
   `com.cansin.app` and `com.cansin.app.widget` (Xcode does this automatically with "Automatically manage signing").
4. Run on a device or simulator. For release: Product → Archive.

## Layout
- `CansinCore/` Swift package: models and all pure rules (promille, notes, beer size, versus, stats, export, timer,
  reminder policy). Tested on Linux too:
  `docker run --rm -v "$PWD/CansinCore:/src" -w /src swift:6.0 swift test`
- `Shared/` code used by the app and the widget: SwiftData records + store, repository, settings, reminders, timer, palette.
- `App/` SwiftUI screens. `Widget/` widget + intents.
- `App/Resources/*.lproj` are generated from the Android `strings.xml`: `python ios/tools/gen_strings.py`.
