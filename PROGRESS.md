# Progress

Current phase: **P2 done (device check of name/language switch pending by user) → awaiting review before P3**

## Phases
- [x] Setup (project, wrapper, git, CLAUDE.md)
- [x] P1 Core counter
- [x] P2 Settings & personalization
- [ ] P3 Widget
- [ ] P4 Reminder
- [ ] P5 Notes / tab
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
- `CansinApp` = manual DI (lazy db, repository, prefs)
- Tests: app/src/test (fake DAOs, SessionRepositoryTest, SessionRulesTest, SettingsTest, PaletteContrastTest)

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
- Launcher icons: adaptive vectors (amber mug / şalgam glass), placeholders until final art.

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
- Note amount as minor units (Long) OK?
- Release signing: no keystore yet; will use untracked signing.properties.
