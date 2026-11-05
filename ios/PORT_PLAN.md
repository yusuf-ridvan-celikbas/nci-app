# iOS port plan: Android P13–P23 → iOS (do this on a Mac)

Status (2026-11-01): `CansinCore` has the pure rules for P14–P23 (`NightRules.swift`, model changes in
`Models.swift`/`VersusRules.swift`, tests in `NightRulesTests.swift`). They were written **without a Swift
compiler**, so run the core tests first:
`docker run --rm -v "$PWD/CansinCore:/src" -w /src swift:6.0 swift test`.
The App/Shared/Widget code has never been compiled (P12). New enum cases (`SessionMode.GROUP`, seat `Side`s,
`DrinkKind`) will break exhaustive `switch`es there. Fix those as part of the first Xcode build.

## Order on the Mac
1. `xcodegen`, build, fix P12 compile errors, device test (as before).
2. Update the records (`Shared/Records.swift`, SwiftData). Lightweight migration only: every new field must be optional or have a default.
   - `BeerRecord.kind: String = "BEER"` (P22)
   - `WaterRecord.side: String?` (P23)
   - new `GroupSeatRecord(sessionId, side, name?)` with cascade from session (P23)
   - `FriendResultRecord` (P13, still missing)
3. Re-run `python ios/tools/gen_strings.py`. Android strings for P13–P23 are already in `strings.xml`.
4. Add the features below, one commit per phase, using the core rules.

## Per phase
| Phase | Core (done, untested) | Still to build on iOS | iOS API |
|---|---|---|---|
| P13 result cards | `ResultCards.swift` | Friends tab, share/import UI, export `friendResults` | ShareLink; paste into TextField (no UIPasteboard reads) |
| P14 currency | `Currency` | Settings picker, amounts in tab/widget/export | — |
| P15 quick add | — | Home-screen quick actions (+1 / water / tab), Control Center control (iOS 18) instead of the Android tile | `UIApplicationShortcutItem`, `ControlWidget` + AppIntent |
| P16/P17 care + limits | `CareRules` | CareCard in counter/versus/group, Settings section; "Way home" opens Apple Maps transit (`maps://?daddr=…&dirflg=r`) | `openURL` (Maps does the networking, not us) |
| P18 PIN lock | — | PBKDF2-SHA256 100k + 16-byte salt in Keychain (this-device-only); backoff 30 s doubling, max 15 min; relock after 1 min in background; hide widget counts | CommonCrypto `CCKeyDerivationPBKDF`, `SecRandomCopyBytes`, `scenePhase`; privacy overlay in the app switcher |
| P19 settle up | `SettleRules`, `GroupRules.split` | Settle sheet + share text | ShareLink |
| P20 calendar | `CalendarRules` | Month grid in History → Stats, tab totals per session/month | `Calendar.current` (firstWeekday from locale) |
| P21 restore | — (Android has `RestoreParser`/`MiniJson`) | Port `RestoreParser` on top of `JSONSerialization` (same checks), "Replace all data?" confirm, one SwiftData transaction | `.fileImporter` (no file permission needed) |
| P22 other drinks | `Drinks`, wider limits (2–200 cl, 0–80 %) | Long-press picker, default-drink setting, kind in lists/export | — |
| P23 together | `GroupRules`, `isMineWater`, `mySpend` | Mode chooser, group setup sheet, seat grid (fixed seat colours = the 6 accent light tones), per-seat water, tab per seat, detail/export/restore seats; widget/controls only open the app in a group | — |
| P24 screen lock | — | iOS has no public API to start Guided Access from an app. In Versus/Together, show a lock button that opens an info card: "Settings → Accessibility → Guided Access, then triple-click the side button" | — (`UIAccessibility.isGuidedAccessEnabled` only reads the state) |
| P25 bottom navigation | — | `TabView`: Tonight (counter/versus/together) · Tab · Game · History · Settings | — |
| P26 Truth or Dare | — (Android `GameRules`, `ShuffleBag`) | Port the rules, a spinning glass (`rotationEffect` + `withAnimation(.easeOut)`), packs from `game_questions.xml` (extend gen_strings for string-arrays), SwiftData `CustomQuestionRecord`, linked players from Versus/Together | `UIImpactFeedbackGenerator` |
| Name (2026-11-01) | — | App = Salgam only (renamed from SalgamBalgam 2026-11-06) (project.yml already updated): remove the `AppName` enum/picker in `Shared/Settings.swift`, `App/SettingsView.swift` and `AppModel.swift`; the game title is "Soy tu Tentación" | — |

## Store/privacy checks (same as Android)
- No network or tracking SDKs. Maps/share sheets are system apps.
- Group mode has no winner and no ranking. Neither mode rewards quantity.
- `PrivacyInfo.xcprivacy`: Keychain and file import add no new "required reason" API. Re-check `UserDefaults` (CA92.1) if you add App Group defaults.

## P27 storage controls (Android 2026-11-06) — to port
- CansinCore: `StorageRules` (KeepHistory/CompressAfter, cutoff, isDue, summarize, sides codec, valid) + tests mirroring `StorageRulesTest.kt`.
- SwiftData: optional totals on the session record (same fields as Android `SessionTotals`); export/restore `totals` object.
- App: Settings → Privacy & data → Storage (usage, free up space, keep history, compress) and a daily upkeep on launch. SwiftData has no VACUUM API; "free up space" = delete temp export files only.

## P28 drinks on the Tab (Android 2026-11-06) — to port
- `priceMinor` on the drink record + price per kind in settings; `TabRules` (charges/withDrinks/drinkLines) in CansinCore with tests mirroring `TabRulesTest.kt`; Tab "Drinks (automatic)" block; Tonight count rows with −1 for drinks and water.
