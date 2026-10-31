# Progress

Current phase: **P14–P19 done (feature/p18-pin; next P20 stats extras). Earlier: P12 iOS port on branch feature/p12-ios (64203b3); core logic tested on Linux Swift, app/widget NOT compiled yet (needs a Mac + Xcode). Next: build on a Mac, fix compile issues, device test. P13 = offline result cards (Option A) on branch feature/p13-cards: Android done, iOS core codec done, iOS UI for it not yet.**

## Phases
- [x] Setup (project, wrapper, git, CLAUDE.md)
- [x] P1 Core counter
- [x] P2 Settings & personalization
- [x] P3 Widget
- [x] P4 Reminder
- [x] P5 Notes / tab
- [x] P6 Beer size
- [x] P7 Promil estimator
- [x] P8 Versus
- [x] P9 Water reminder
- [x] P10 History & stats
- [x] P11 Export & hardening
- [~] P12 iOS port (code written; awaiting first Xcode build)
- [x] P13 Friends, offline (Option A result cards; Android done, iOS core only) (design first)
- Plan P14–P23 (2026-11-01, user-approved; file ~/.claude/plans/bright-swimming-diffie.md), branch feature/p14-plus:
  - [x] P14 Currency  - [x] P15 Quick tile + shortcuts  - [x] P16 Care messages  - [x] P17 Self-set limits
  - [x] P18 PIN lock  - [x] P19 Settle up (branch feature/p18-pin, on top of p14-plus)  - [ ] P20 Stats extras  - [ ] checkpoint: phone test + merge
  - [ ] P21 Restore backup  - [ ] P22 Other drinks (DB v4)  - [ ] P23 Group mode 2–6 (DB v5, no winner/race)

## Code map
- `data/` Entities.kt (all 5 Room entities, enums stored as TEXT), Daos.kt (SessionDao, BeerDao), AppDatabase.kt (v1, schema in app/schemas), SessionRepository.kt (all writes behind one Mutex)
- `domain/SessionRules.kt` pure staleness rule
- `prefs/Settings.kt` enums (AppName+alias, Language, ThemeMode, Accent) + Settings + enumOr; `prefs/SettingsStore.kt` single DataStore "settings" (age_confirmed, app_name, language, theme_mode, accent; corruption → reset)
- `system/AppLocale.kt` locale (33+: LocaleManager; 26–32: DataStore + attachBaseContext wrap + recreate); `system/LauncherAlias.kt` enables chosen activity-alias, disables other (DONT_KILL_APP)
- `ui/SettingsScreen.kt` grouped radio lists; `ui/theme/Palettes.kt` 6 accents light/dark + shared neutrals (Long ARGB, unit-tested for AA)
- `ui/` CounterScreen (top bar: app name + settings), CounterViewModel, AgeGateScreen, theme/Theme.kt (scheme from palette or dynamic)
- Navigation: rememberSaveable flag in MainActivity + BackHandler (no navigation lib)
- `widget/` CounterWidget (exported AppWidgetProvider, system actions only; refreshAll), WidgetActionReceiver (exported=false, ACTION_ADD → addBeer), WidgetRenderer (RemoteViews, colors from ui/theme colorScheme, localized via AppLocale.localized), WidgetLayout (pure helpers)
- `reminder/` ReminderPolicy (pure), Reminders (controller: schedule/snooze/stop/restore, AlarmManager), ReminderNotifier (channels + notification), ReminderReceiver (exported=false: REMIND/ANOTHER/SNOOZE/STOP, requires session_id extra), BootReceiver (exported=false: BOOT_COMPLETED, MY_PACKAGE_REPLACED)
- `domain/NoteRules.kt` (text/person caps, amount parse/format in minor units, pickTime across midnight); NoteDao; repository addNote/updateNote/deleteNote
- `ui/NotesScreen.kt` (Tab screen: list, total, quick-add text+amount), `ui/NoteEditDialog.kt` (text/person/amount/time picker, delete), NotesViewModel; MainActivity Screen enum COUNTER/TAB/SETTINGS
- `domain/BeerSize.kt` (BeerSize, BeerRules: presets 33/50 cl, cl/ABV parse+format, clamp); `ui/BeerSizeDialog.kt` (shared by Settings default + long-press +1); repository `addBeer(size)` falls back to `defaultSize` lambda (Settings)
- `domain/BodyProfile.kt` (Sex, License, ranges, parse), `domain/Promille.kt` (PromilleModel: grams, Watson TBW, drinkEnd, 1-min simulation → current/peak/zeroAt), `domain/Warnings.kt` (TrDrivingLaw config, WarningRules → Warning list)
- `ui/PromilleScreen.kt` (PromilleTicker, PromilleCard on counter, PromilleScreen with beer list, BeerEditDialog, WarningBanner + taxi/112 intents), `ui/PromilleViewModel.kt`, `ui/ProfileDialog.kt`; `ui/theme/Theme.kt` LocalAlertColors (fixed danger=RED, caution=AMBER palettes)
- P8: `domain/VersusRules.kt` (VersusSetup, TabTotals, cleanName, fits, meSide, isMine, noteSide, totals); `data/FriendRepository.kt` + FriendDao; repository startVersus/updateVersus/addBeer(side)/addMyBeer/removeLatest(side); `ui/VersusScreen.kt`, `ui/VersusSetupDialog.kt`, `ui/VersusViewModel.kt`, `ui/SideUi.kt` (sideName, sideColor, SideChips); `UndoEffect` shared in CounterScreen.kt; theme `VersusColors` (fixed RED/BLUE light tones)
- P9: `domain/WaterRules.kt` (beersSinceWater, shouldNudge, clamp 1–10); WaterDao; repository addWater/undoWater/water(); `ui/WaterViewModel.kt`, `ui/WaterBar.kt` (count + Water +1 + nudge card); UndoEffect now generic
- P10: `domain/StatsRules.kt` (durationMs, paceMin, peak, session, overview); `data/HistoryRepository.kt` (SessionDao.observeHistory → SessionSummary with beer/note/water counts, observe(id), deleteEnded; BeerDao.observeEnded); `ui/HistoryViewModel.kt`, `ui/HistoryScreen.kt` (Sessions | Stats tabs), `ui/SessionDetailScreen.kt` (delete with confirm), `ui/BarChart.kt` (Canvas, no lib)
- P11: `domain/ExportJson.kt` (hand-written JSON writer, unit-tested escaping), ExportDao (all rows), `system/DataManager.kt` (export → cache/export/cansin-export.json via FileProvider + ACTION_SEND chooser; deleteAll), `ui/DataSection.kt` (Settings → Data)
- Widget v2 (branch feature/widget-pages): `widget/` WidgetRenderer (3 pages BEER/TAB/WATER, ‹ › + dots, WidgetData), WidgetActionReceiver (ADD, WATER, TIMER, PREV, NEXT; validates action + widget id), CounterWidget (page per widget id, cleaned onDeleted); `domain/TimerRules.kt` (TimerState, WidgetPage, canStart, isCurrent, page/step); `timer/DrinkTimer.kt`; `ui/TimerBar.kt`
- `ui/ReminderSection.kt` settings section incl. POST_NOTIFICATIONS rationale + request
- `CansinApp` = manual DI (appScope, db, SettingsStore, Reminders, SessionRepository with SessionListener → widget refresh + reminders)
- Tests: app/src/test (fake DAOs, SessionRepositoryTest, SessionRulesTest, SettingsTest, PaletteContrastTest, WidgetLayoutTest, ReminderPolicyTest, NoteRepositoryTest, NoteRulesTest)

## Decisions
- Toolchain: JDK 17, AGP 9.4.1 + Gradle 9.6.0 wrapper, Kotlin 2.2.10 + KSP 2.3.11, compile/targetSdk 37, minSdk 26. Kotlin 2.4.x / AGP 9.5 deferred.
- Approved deps: Compose BOM 2026.09.00 (ui, material3), activity-compose 1.13.0, lifecycle-viewmodel 2.11.0, Room 2.8.5 (+KSP), DataStore prefs 1.3.0; junit (test).
- Times stored as epoch millis (Long). Note amount stored as `amountMinor: Long` (cents/kuruş) – confirm in P5.
- Default BeerEvent size 500 ml, ABV 5.0; configurable in Settings since P6.
- Auto-close (6 h) checked lazily on app start (onStart) and on each +1/−1; endedAt = last beer time. No background work.
- Undo (5 s snackbar) deletes that exact event; −1 deletes newest beer in open session. Session with 0 beers is deleted.
- "End session" always visible (bottom bar), disabled when no session.
- 18+ gate: DataStore flag; "Exit" finishes activity.
- Backup off: allowBackup=false, fullBackupContent=false, dataExtractionRules exclude all.
- Release: minify + shrinkResources; Log.* stripped.
- P2: Locale is SDK-only (no AppCompat, user-approved). On 26–32 the stored language is read with runBlocking in attachBaseContext (tiny cached file).
- P2: MainActivity exported=false; launcher = 2 activity-aliases (LauncherSoy default enabled, LauncherSalgam disabled). Application label/icon stay SoyTuTentación (system app-info).
- P2: localeFilters en+tr (drops library translations); AAB language split disabled so in-app switch always has TR strings.
- P2: Dynamic color option only shown on API 31+. Secondary/tertiary roles reuse the accent; neutrals are grey and shared by all accents.
- Launcher icon: "nCi" logo (app-logo/nci-app-logo.jpg.jpeg) traced to vector adaptive icon, maroon #7B1F2D / cream #FFF5E3; same icon for both aliases (user choice).
- P3: Widget uses RemoteViews, not Glance (user-approved: no dependency). +1 goes through a non-exported receiver via explicit immutable PendingIntent; the exported provider only refreshes. Rapid taps serialized by repository Mutex.
- P3: Repository has onChange callback → every write refreshes widgets; settings changes refresh too. snapshot() is read-only and shows a stale (>6 h) session as 0.
- P3: One provider, two layouts: small (count + +1) and wide ≥250dp (name, count, "since last" Chronometer, +1). API 31+: responsive size map + setColorInt light/dark pair (follows system night automatically); API 26–30: layout from min width option, colors fixed at render time.
- P5: Amount = Long minor units (cents/kuruş), no currency; input accepts "12.5"/"12,50", max 1,000,000.00, ≤2 decimals; shown with locale decimal separator. (Chosen while user away – confirm.)
- P5: A note with no open session starts one. Session is "empty" only with 0 beers AND 0 notes (undo/−1/delete/end use this). Notes count as activity for 6 h auto-close.
- P5: Edited time stored in editedTime (createdAt kept); picker time resolves to the date closest to createdAt (handles midnight). Notes list ordered by COALESCE(editedTime, createdAt). Note delete added (not in spec, needed for typos).
- Widget v2: user chose arrows + dots (no swipe: avoids a RemoteViewsService), manual timer, tab page = total + open Tab. Page stored per widget in DataStore (widget_page_<id>). Beer page: +1 (versus: score, opens app). Tab page: total (+ note count on wide), button/value open the Tab screen (MainActivity EXTRA_OPEN_TAB, CLEAR_TOP|SINGLE_TOP → onNewIntent). Water page: count, +1 adds water (needs open session with a me side, else opens app). Old "since last beer" chronometer removed.
- Drink timer (manual): ▶ starts timing my latest unfinished beer (DataStore timer_beer_id/session_id/started_at); ■ stops and sets that beer's finishedAt (used by promille). Shown on wide widget beer page + in app above Water. Cleared on session close. Settings → Drinking timer switch (default on) hides all timers and clears a running one. Small widget has no timer (no room).
- Snapshot now read-only staleness via lastActivity (beers/notes/water), plus notes, tabMinor, water, hasMe, myLatestBeer.
- P11: Export: JSON format "cansin-export" v1, sessions with nested beers/water/notes + friends; body profile only if the user ticks it (default off). No storage permission: file in cacheDir/export (replaced each export, wiped on delete-all), shared via non-exported FileProvider (grantUriPermissions, read-only grant on the share intent).
- P11: Delete all data: cancels reminders, Room clearAllTables, wipes export cache, clears DataStore except age_confirmed, app_name, language, theme_mode, accent (so the launcher alias + locale stay consistent), refreshes widgets. Confirm dialog.
- P10: History lists ended sessions only (open one is on the main screen). Delete only works on ended sessions (SQL guard), FK CASCADE removes beers/notes/water. History icon in counter + versus top bars; back from detail → history.
- P10: Stats count only *my* beers (solo, or my versus side); versus sessions without a me side are skipped. Avg pace = total span / total intervals (sessions with ≥2 beers). Water ratio = water / my beers. Peak ‰ recomputed with the *current* body profile (noted on screen + disclaimer). Charts: last 20 sessions, beers and peak ‰; a11y description lists values. No records/badges (store policy).
- P9: Water reminder is an in-app nudge card (no notification): shown when ≥ N of *my* beers since the last water; "Not now" hides it until the next beer (in-memory). On by default, N=2 (prefs water_enabled, water_every). Water +1 needs an open session (solo, or versus with a me side), has 5 s undo. Water counts for session activity (6 h) and keeps a session from being auto-deleted. No DB schema change (WaterEvent existed in v1).
- P8: DB v2 via Room AutoMigration(1→2): session + redName, blueName, redFriendId, blueFriendId, meSide; note + side (null = shared). Verified upgrade on device.
- P8: Versus = own session (user choice). Start from the people icon on the counter; ends open solo session (deleted if empty). Main screen routes to VersusScreen while open session is VERSUS; End session → back to solo. Empty versus session is NOT auto-deleted on undo/−1 (only on End session).
- P8: Side rules: SOLO beers only outside versus, RED/BLUE only in versus (addBeer returns null otherwise). "Me" side per session: promille uses only my beers; reminders schedule only on my beers; reminder "another" → addMyBeer (no-op if no me side). Widget in versus shows "R–B" score and +1 opens the app; WidgetActionReceiver add is a no-op in versus.
- P8: Names ≤30, blank → default "Red/Blue". Friends: pick chip fills name + links id; "Save as friend" when name is new; friend color = side color at save; delete in Settings → Friends (unlinks sessions, names kept). Versus +1 uses default size (no long-press picker).
- P8: Tab in versus: chips Red/Blue/Shared on quick add + edit; totals per side, shared, and overall (shared not split).
- P7: BodyProfile in DataStore (profile_* keys, invalid/incomplete → null); prompt card on counter until "Not now" (profile_prompt_dismissed); editable in Settings → Blood alcohol estimate, or from the estimate screen; "Remove profile" clears keys.
- P7: Model per spec. finishedAt missing → min(at+20 min, next beer's at) (conservative). finishedAt < at treated as at. Peak/zeroAt are over the projected curve (incl. still-absorbing drinks); simulation capped at 72 h. ‰ rounded up with 1e-9 guard.
- P7: Recompute on every beers/profile change and on a 1-min ticker that runs only while the activity is resumed (onResume/onPause flag) and the counter or estimate screen is showing. No background work.
- P7: Warnings decided by the app's effective language (Configuration locale == tr), user choice. EN shows only the generic line (incl. no health cautions – per spec; flag). Evening greeting changed to neutral "İyi akşamlar! Su içmeyi unutma." (user choice, store policy). Over-limit "Taksi/ulaşım" = ACTION_VIEW geo:0,0?q=taksi; poisoning = ACTION_DIAL tel:112 (no CALL_PHONE). Both catch ActivityNotFoundException.
- P7: TrDrivingLaw: lawVersion "TR-KTK-48", lastVerified "unverified"; limits CANDIDATE 0.20, COMMERCIAL 0.20, NORMAL 0.50 — must verify before release.
- P7: Beer edit (start, finish, size/ABV) validated in repository: at ≤ now, finishedAt clamped to [at, now], size clamped.
- P6: Size stored in ml (cl input, ≤1 decimal, 10–200 cl); ABV 0–20 %, ≤1 decimal. Prefs default_size_ml/default_abv, clamped on read and in repository. Widget and reminder "another" use the default size. Long-press +1 → dialog (33/50/custom + ABV prefilled with default) → adds that beer (normal undo snackbar). +1 is now a Box+combinedClickable (M3 Button has no long-press), with a11y long-click label.
- P4: Repository emits events via SessionListener (onBeerAdded, onSessionClosed, onChanged), dispatched after the write lock is released, in order.
- P4: Scheduling = AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP), inexact; no WorkManager, no SCHEDULE_EXACT_ALARM. One alarm PendingIntent (same action/request code) → reschedule replaces, no duplicates.
- P4: Reminders off by default. Enabling on API 33+ shows rationale dialog then the system prompt; denied/blocked shows a link to system notification settings. Enabling mid-session schedules from now.
- P4: Stored state (DataStore): next_at, session, stopped_session. Fired reminder clears next_at. Boot/app update restores (missed → fires 1 min after restore). Session close cancels; stop-for-tonight only applies to that session.
- P4: Notification: title = chosen app name, text localized; VISIBILITY_PRIVATE with public version "Reminder"; two channels (default, silent = IMPORTANCE_LOW) since channel sound can't change later. Actions verify session_id == open session, else ignored.
- P4: "Yes, got another" sets finishedAt on the latest beer (only if unset) and adds a beer. Interval changes apply from the next scheduling.
- P3: updatePeriodMillis=0 (no periodic wakeups). Widget picker label is static ("Counter"/"Sayaç"); in-widget title follows app name.

## P14–P17 report (2026-11-01, Android only; iOS later on a Mac)
- P14: `domain/Currency.kt` (NONE/TRY/EUR/USD/GBP, display only; ₺ after, $/£ before, € by language). Pref `currency`; `LocalCurrency` provided in MainActivity; `rememberAmountFormat` adds the symbol, `rememberPlainAmountFormat` for edit fields; widget tab page; export adds top-level `currency` only when set.
- P15: `system/BeerTileService` (exported, guarded by BIND_QUICK_SETTINGS_TILE; unlockAndRun when locked; adds directly only if age confirmed and not versus, else opens app; subtitle = count on API 29+). Static shortcuts `res/xml/shortcuts.xml` on both launcher aliases → MainActivity extra `com.cansin.app.SHORTCUT` (beer/water/tab, parsed by `domain/QuickActions.kt`, run only after age gate).
- P16/P17: `domain/CareRules.kt` + `ui/CareViewModel.kt`, `ui/CareCard.kt` (one card in the promille slot of counter + versus), `ui/CareSection.kt` (Settings → Looking after you). Priority: own beer limit > own budget > late (default 01:00, choices 22–03, until 06:00; "Not now" = 1 h) > slow down (≥5 of my beers; no numbers, also covers EN health caution). Limits work even with kind reminders off. Budget compares my spend (solo: whole tab; versus: my side + shared). "Way home": saved home text → https maps transit directions; none → geo search "bus stop". Taxi = existing geo search. Prefs: care_enabled, care_late_hour, home_place (≤100, no control chars), beer_limit (1–20), budget_minor.
- Release APK 1.67 MB (+23 KB). Permissions unchanged, no INTERNET. Lint 0 errors. Tests 131/131. Not tested on device.
- Remote added: origin https://github.com/yusuf-ridvan-celikbas/nci-app.git (nothing pushed yet).

## P18–P19 report (2026-11-01)
- P18: `domain/PinRules.kt` (4–6 digits, PBKDF2-SHA256 100k iterations, 16-byte salt, constant-time compare; backoff after 5 wrong: 30 s doubling, max 15 min; relock after 1 min in background / cold start / clock back), `system/AppLock.kt` (persisted wrong-try counter, process-level unlocked flag), `ui/LockScreen.kt` (forgot → delete all data, user-approved), `ui/PinSection.kt` (set / change / remove need current PIN). PIN on: recents preview off (API 33+), widget + tile show ••• and only open the app (option, default on), widget actions ignored. It's a privacy screen, not encryption (data already app-private, no backup). Delete-all removes the PIN.
- P19: `domain/SettleRules.kt` (per-person totals case-insensitive A–Z, unnamed last; versus shared 50/50, odd cent to red; even split rounded up), `ui/SettleDialog.kt` (Tab → Settle up; share as text).
- Release APK 1.69 MB. Permissions unchanged. Lint 0 errors. Tests 139/139. Not tested on device.

## P13 report (2026-10-31) — offline result cards, no network
- User: "no internet, no privacy worries" → Option A. INTERNET permission still absent (verified merged release manifest).
- `domain/ResultCards.kt`: code `cansin:1:` + base64url(UTF-8 "name
day
beers
durationMin
water"); decode finds the code anywhere in pasted text, caps text 5000 / payload 400, strict UTF-8, exact 5 fields, ranges (beers/water ≤100, duration ≤72 h, day 2020-01-01..today+1), name ≤30 with no control/bidi chars, must already be trimmed. Kotlin + Swift (`CansinCore/ResultCards.swift`) share an Android-generated fixture test.
- Data: Room v3 (AutoMigration 2→3) `friend_result` (name, day ISO, beers, durationMin, water, importedAt), unique index on all card fields → re-import = "already imported". Included in export JSON as `friendResults` (additive, still format v1) and wiped by delete-all (clearAllTables).
- UI: Session detail → share icon (only when the session has my side) → name (saved as DataStore `share_name`) + preview → ACTION_SEND text/plain chooser. History → Friends tab: Import (user pastes into a text field — app never reads the clipboard), preview, list with delete; note "results stay on this phone". No rankings/totals over friends (store policy).
- Release APK 1.64 MB (+20 KB). Permissions unchanged. Lint 0 errors (2 version notices). Tests: Android 122/122, Swift core 36/36. Not tested on device; DB 2→3 migration not yet verified on device.
- iOS: core codec only; Friends tab/share UI + `friendResults` export + SwiftData record still to do.

## P12 iOS (2026-10-31)
- Layout: `ios/project.yml` (XcodeGen), `ios/CansinCore` (SPM: models + all pure rules, 31 XCTest ported from Kotlin, run in Docker swift:6.0), `ios/Shared` (app + widget: SwiftData records/AppStore, Repository, SettingsStore, Reminders/DrinkTimer/Effects, Palette), `ios/App` (SwiftUI screens), `ios/Widget` (widget + AppIntents). Strings generated from Android strings.xml by `ios/tools/gen_strings.py` (+5 iOS-only keys).
- No third-party code. System frameworks only (SwiftUI, SwiftData, WidgetKit, AppIntents, UserNotifications, Charts). XcodeGen is a Mac build tool only — confirm OK, else hand-made .xcodeproj.
- Security: App Group store + defaults; `default-data-protection = NSFileProtectionComplete` (store dir also created with .complete); no CloudKit (`cloudKitDatabase: .none`); no ATS keys; no network code; PrivacyInfo.xcprivacy (no tracking/collection; UserDefaults reason 1C8F.1). Widget intents + notification actions require unlock (store unreadable while locked); widget shows "Unlock to see your session" then. Intents not discoverable in Shortcuts.
- iOS differences (decided while user away – confirm): app name setting changes in-app/widget/notification title only (iOS can't rename the home-screen label); language uses the iOS per-app language in Settings (button opens it); no "Dynamic" accent; age gate "Exit" can't quit the app (App Store rule) → shows a decline message; one widget page shared by all widget instances (intents don't know the instance); widget timeline adds an entry at last activity + 6 h so a stale session shows empty; reminders: pending notification survives reboot (no boot receiver), category has hiddenPreviewsBodyPlaceholder "Reminder"; URL scheme `cansin://tab|app` (only navigates, ignored before age gate).
- IDs: random Int64 < 2^53 (SwiftData has no autoincrement); times epoch millis like Android; export JSON format identical.
- Not done: compile/run (no Mac), size report, iOS unit tests for Repository (needs SwiftData → Mac).

## Widget v2 report (branch)
- Release APK 1.62 MB (+5 KB). Permissions unchanged; no new exported components (receiver stays exported=false). Lint clean except version notices. Tests: 116/116. Not tested on device.

## P11 report / audit (2026-10-31)
- Release APK 1.62 MB (+18 KB); AAB 4.13 MB (all ABIs; Play splits per device). Largest: classes.dex 2.68 MB (uncompressed). Target < 5 MB OK.
- Permissions (merged release manifest): POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, AndroidX DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION (signature). No INTERNET.
- Exported: LauncherSoy/LauncherSalgam aliases (launcher), CounterWidget (system widget broadcasts only), androidx ProfileInstallReceiver (guarded by android.permission.DUMP = adb/shell only). Everything else exported=false (MainActivity, WidgetActionReceiver, ReminderReceiver, BootReceiver, FileProvider, InitializationProvider, Room MultiInstanceInvalidationService).
- allowBackup=false + dataExtractionRules; no Log calls in app source (+ R8 strips Log). R8 mapping at app/build/outputs/mapping/release/mapping.txt (35 MB) – archive it per release (not in git).
- Lint clean except version notices. Tests: 111/111. Export/delete-all NOT yet tested on device.

## P10 report
- Release APK 1.60 MB (+20 KB). Permissions unchanged. Lint clean except version notices. Tests: 107/107. No schema change. Delete is local and irreversible (confirm dialog).

## P9 report
- Release APK 1.58 MB (+17 KB). Permissions unchanged. Lint clean except version notices. Tests: 101/101. No new security surface.

## P8 report
- Release APK 1.56 MB (+52 KB). Permissions unchanged. Lint clean except version notices. Tests: 93/93. DB migration v1→v2 tested by installing over existing data on device.
- Security: friend names local only (Room, backup excluded); names length-capped; side values validated against session mode in repository.

## P7 report
- Release APK 1.51 MB (+38 KB). Permissions unchanged (dial/maps via intents, no CALL_PHONE, no INTERNET). Lint clean except version notices. Tests: 78/78.
- Security/privacy: BodyProfile is sensitive, stays in app-private DataStore (backup already excluded). Export/"Delete all data" not built yet (later phase) – must include profile wipe.

## P6 report
- Release APK 1.47 MB (+1.4 KB). Permissions unchanged. Lint clean except version notices. Tests: 62/62. No new security surface (input validated + clamped). Not tested on device.

## P5 report
- Release APK 1.47 MB (+250 KB, mostly M3 TimePicker + text fields/LazyColumn). User chose to keep M3 TimePicker. Permissions unchanged. Lint clean except version notices. Tests: 55/55. Not tested on device.

## P4 report
- Release APK 1.22 MB (+69 KB: Slider/Switch/AlertDialog), AAB 2.96 MB. Permissions: POST_NOTIFICATIONS (runtime, only when user enables reminders), RECEIVE_BOOT_COMPLETED (restore pending reminder). Exported unchanged (aliases, widget provider, profileinstaller). Lint clean except version notices. Tests: 38/38.
- Trade-off: inexact alarm may fire minutes late in Doze; acceptable for a nudge, avoids exact-alarm permission.

## P3 report
- Release APK 1.15 MB (+4 KB), AAB 2.8 MB. Permissions: none. Exported: 2 launcher aliases, CounterWidget, profileinstaller receiver. Lint: clean except version notices. Tests: 27/27.
- Known limits: on API 26–30 widget colors don't flip with system dark mode until the next refresh; dynamic-color widget doesn't refresh on wallpaper change; a session that goes stale while idle shows its old count until the next refresh (no alarm, battery).

## P2 report
- Release APK (unsigned) 1.15 MB; AAB 2.8 MB. Permissions unchanged (none). Exported: 2 launcher aliases + profileinstaller receiver. Lint: clean except version notices. Tests: 21/21.
- Not yet verified on device: name switch, language switch, theme switch (auto-mode blocked my adb taps).

## P1 report
- Release APK (unsigned) 1.09 MB; AAB 2.4 MB.
- Permissions: none of our own (only AndroidX signature-level DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION).
- Exported: MainActivity (launcher) + profileinstaller receiver (DUMP-guarded).
- Lint: clean except "newer version available" (AGP 9.5.0, Kotlin 2.4.21).
- Tests: 15/15 pass.

## Open questions
- Widget label/theme (P3) and notification title (P4) must read SettingsStore; on API 26–32 notifications need AppLocale.wrap(appContext) for language.
- androidx.profileinstaller ProfileInstallReceiver is exported (DUMP-permission, adb-only). Keep or remove via manifest merge?
- P7: EN locale shows no health cautions (spec says EN → generic only). Add EN "slow down / poisoning" without numbers?
- P7: Verify TR limits (2026 bill may lower NORMAL to 0.20) and set lastVerified before release.
- P8: Shared tab amounts are shown separately, not split 50/50 — split wanted?
- P9: Water nudge default ON — OK? Should it also fire as a notification when reminders are on?
- P16: slow-down threshold fixed at 5 of my beers, late default 01:00 — OK?
- P10: Counter top bar now has 4 icons; long app name (SoyTuTentación) may truncate – switch to start-aligned title if so.
- P11: Delete-all keeps age confirmation + appearance – OK, or wipe everything (age gate again)?
- Release signing: no keystore yet; will use untracked signing.properties.
