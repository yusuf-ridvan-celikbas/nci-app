# Progress

Current phase: **P10 done (installed on device, awaiting user check) → awaiting review before P11**

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
- [ ] P11 Export & hardening
- [ ] P12 iOS port
- [ ] P13 Online friends (design first)

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
- P10: Counter top bar now has 4 icons; long app name (SoyTuTentación) may truncate – switch to start-aligned title if so.
- Release signing: no keystore yet; will use untracked signing.properties.
