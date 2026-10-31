# Progress

Current phase: **P5 done (not yet tested on device; phone disconnected) → awaiting review before P6**

## Phases
- [x] Setup (project, wrapper, git, CLAUDE.md)
- [x] P1 Core counter
- [x] P2 Settings & personalization
- [x] P3 Widget
- [x] P4 Reminder
- [x] P5 Notes / tab
- [ ] P6 Beer size
- [ ] P7 Promil estimator
- [ ] P8 Versus
- [ ] P9 Water reminder
- [ ] P10 History & stats
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
- `ui/ReminderSection.kt` settings section incl. POST_NOTIFICATIONS rationale + request
- `CansinApp` = manual DI (appScope, db, SettingsStore, Reminders, SessionRepository with SessionListener → widget refresh + reminders)
- Tests: app/src/test (fake DAOs, SessionRepositoryTest, SessionRulesTest, SettingsTest, PaletteContrastTest, WidgetLayoutTest, ReminderPolicyTest, NoteRepositoryTest, NoteRulesTest)

## Decisions
- Toolchain: JDK 17, AGP 9.4.1 + Gradle 9.6.0 wrapper, Kotlin 2.2.10 + KSP 2.3.11, compile/targetSdk 37, minSdk 26. Kotlin 2.4.x / AGP 9.5 deferred.
- Approved deps: Compose BOM 2026.09.00 (ui, material3), activity-compose 1.13.0, lifecycle-viewmodel 2.11.0, Room 2.8.5 (+KSP), DataStore prefs 1.3.0; junit (test).
- Times stored as epoch millis (Long). Note amount stored as `amountMinor: Long` (cents/kuruş) – confirm in P5.
- Default BeerEvent size 500 ml, ABV 5.0 (P6 makes configurable).
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
- P4: Repository emits events via SessionListener (onBeerAdded, onSessionClosed, onChanged), dispatched after the write lock is released, in order.
- P4: Scheduling = AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP), inexact; no WorkManager, no SCHEDULE_EXACT_ALARM. One alarm PendingIntent (same action/request code) → reschedule replaces, no duplicates.
- P4: Reminders off by default. Enabling on API 33+ shows rationale dialog then the system prompt; denied/blocked shows a link to system notification settings. Enabling mid-session schedules from now.
- P4: Stored state (DataStore): next_at, session, stopped_session. Fired reminder clears next_at. Boot/app update restores (missed → fires 1 min after restore). Session close cancels; stop-for-tonight only applies to that session.
- P4: Notification: title = chosen app name, text localized; VISIBILITY_PRIVATE with public version "Reminder"; two channels (default, silent = IMPORTANCE_LOW) since channel sound can't change later. Actions verify session_id == open session, else ignored.
- P4: "Yes, got another" sets finishedAt on the latest beer (only if unset) and adds a beer. Interval changes apply from the next scheduling.
- P3: updatePeriodMillis=0 (no periodic wakeups). Widget picker label is static ("Counter"/"Sayaç"); in-widget title follows app name.

## P5 report
- Release APK 1.47 MB (+250 KB, mostly M3 TimePicker + text fields/LazyColumn). Option: platform TimePickerDialog saves ~?KB but ignores app theme colors. Permissions unchanged. Lint clean except version notices. Tests: 55/55. Not tested on device.

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
- Note amount as minor units (Long), no currency symbol — OK? (implemented)
- P5 size: keep M3 TimePicker (+~200 KB est.) or use platform TimePickerDialog?
- Release signing: no keystore yet; will use untracked signing.properties.
