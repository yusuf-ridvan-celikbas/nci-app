# Progress

Current phase: **P14–P26 done on Android (branch feature/p20-plus, local only, NOT pushed). Phone review paused by user (2026-11-01): user reviews P20–P23 later in order, then we merge. iOS: CansinCore has P14–P23 rules (uncompiled); app/widget never compiled; see ios/PORT_PLAN.md (needs a Mac).**

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
  - [x] P18 PIN lock  - [x] P19 Settle up (branch feature/p18-pin, on top of p14-plus)  - [x] P20 Stats extras  - [ ] checkpoint: phone test + merge (paused by user)
  - [x] P21 Restore backup  - [x] P22 Other drinks (DB v4)  - [x] P23 Group mode 2–6 (DB v5, no winner/race)  - [x] P24 Screen pinning in Versus/Together  - [x] P25 Bottom navigation  - [x] P26 Truth or Dare game (branch feature/p20-plus on top of p18-pin)

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

## Soy tu Tentación extras (2026-11-01, user requests)
- Who asks whom: two-ended glass. The rim points at who answers, the foot at who asks (`GameRules.askerCandidates`/`askerTilt`; for odd counts it tilts toward the asker and the rim stays on the answerer). The headline reads "Ece asks Can" (TR "Soran: Ece · Cevaplayan: Can") and the card shows "Ece → Can · Truth".
- Question sources (saved prefs `game_built_in`, `game_own`, never both off): switch "Built-in questions (written with AI)" plus "Include my own". If nothing can be drawn, a hint points to My questions.
- Import own questions from CSV (`domain/QuestionImport.kt`): Excel/Sheets/Numbers "Save as CSV". Column A = Truth/Dare (or Doğruluk/Cesaret), column B = text. Accepts comma/semicolon/tab, quotes, line breaks in cells and an optional header. UTF-8 with a Windows-1254 fallback; max 256 KB. Strict: bad rows, duplicates and anything over 300 are counted and reported, never guessed. "How to make the file" guide + "Get the template file" (a UTF-8 CSV with BOM, shared via the existing FileProvider). The file comes from the system picker, so no permission is needed. No .xlsx (would need zip/XML parsing; CSV covers Excel).

## Naming + game in Versus (2026-11-01, user request)
- App name is now only **SalgamBalgam**: the app-name setting, the `AppName` enum and `LauncherAlias` are gone. The manifest has one launcher alias `.Launcher` (a new component name, so no persisted DISABLED state from the old `.LauncherSoy`/`.LauncherSalgam`). A home-screen icon pinned to the old alias has to be added again once.
- The game is called **Soy tu Tentación** (brand, not translated), with "Truth or Dare" as the subtitle.
- Truth or Dare is an option **inside Versus and Together** (wine-glass switch in the top bar). Versus stays the main view: compact sides are pinned on top and the game scrolls below; players sync with the sides/seats. The Game tab stays for solo play.

## P25–P26 report (2026-11-01)
- P25: bottom `NavigationBar` (`ui/AppNavBar.kt`): Tonight (counter, or the open Versus/Together) · Tab · Game · History · Settings. History/Settings icons removed from the top bars, back arrows removed on root tabs; system back → Tonight. Promille/detail open on top without the bar. The outer Scaffold consumes insets so inner bottom bars don't double-pad.
- P23 redesign (63a6fbf, user-approved on phone): full-width person rows, last drink/water "… ago", at the table, per-person drink timer (my seat = normal DrinkTimer; others in DataStore `group_timers`, strict codec `domain/SeatTimes.kt`, cleared on session end), long-press drink picker.
- P26: Truth or Dare with a spinning wine glass (`ui/SpinGlass.kt`, Animatable 2.8 s ease-out, haptic on stop, no VIBRATE permission). `domain/GameRules.kt` (players 2–8, avoid-repeat pick, spin target/pointed-at math, ShuffleBag with no repeats until exhausted, linked players from Versus/Together). Packs Friends / Best friends / Couples (romantic, not explicit), 24 truths + 16 dares each, EN+TR in `res/values*/game_questions.xml`. `GameContentTest` enforces equal EN/TR counts and no alcohol words (Unicode-aware regex, with a sanity test). Skip is always free; there's no link to drink counts and answers are never stored. Own questions: DB v6 (AutoMigration 5→6) `custom_question` (≤200 chars, ≤300 items), in export/restore and delete-all.
- Release APK 1.80 MB. Permissions unchanged, no INTERNET, no new libraries. Lint 0 errors. Tests 188/188.
- Decided – confirm: TR labels "Kankalar" (best friends) and "Sevgililer" (couples); the glass button says "Kadehi çevir".

## P24 report (2026-11-01) — screen pinning for Versus/Together (user request)
- Lock icon in the Versus and Together top bars → Android screen pinning (`system/ScreenPin.kt`, `startLockTask`, user confirms the system prompt; no permission, no manifest change). The first time, an info dialog explains how to leave (pref `pin_info_seen`). Tap again to unpin; the state is re-read on resume/focus (a gesture unpin also updates the icon). It auto-unpins when no Versus/Together session is open (`domain/PinScreenRules.kt`).
- Release APK 1.74 MB. Permissions unchanged, no INTERNET. Lint 0 errors. Tests 172/172. Not tested on a device.
- iOS: no API; plan = info card pointing to Guided Access (ios/PORT_PLAN.md).

## P20–P23 report (2026-11-01, built while the user studies; not on the phone, not pushed)
- Commits on feature/p20-plus: e249fbd P20, 9864fd9 P21, 9562429 P22, 4afffe8 P23, f44e4ff P23 budget fix, 906657d iOS prep.
- P20: `domain/CalendarRules.kt` + `ui/MonthCalendar.kt` (History → Stats): month grid that marks days with a session (presence only, no counts), ‹ › from the first session's month to now, tap a day → its sessions → detail. Tab total this month, average tab per session (only sessions with an amount), bar chart of tab total for the last 6 months. Session list rows show the tab total. `SessionSummary.spentMinor` = SUM(note.amountMinor) in the history query (no schema change).
- P21: `domain/MiniJson.kt` (own strict reader: depth ≤8, duplicate keys rejected, no trailing data; org.json is stubbed in JVM tests) + `domain/RestoreParser.kt` (format "cansin-export" v1, newer → "update app"; ≤8 MB, ≤10k sessions, ≤200k events; every field range-checked like the UI; at most one open session; one bad item rejects the whole file). `DataManager.readBackup` (SAF OpenDocument, no storage permission, strict UTF-8, BOM ok) → "Replace all data?" (shows session count) → `restore` in ONE Room transaction (delete sessions/friends/friend results, insert with new ids, friend links remapped). Keeps settings + PIN; applies currency/profile if in the file; cancels reminders, clears timer, refreshes widgets.
- P22: `DrinkKind` column on beer_event (AutoMigration 3→4, default 'BEER'). `domain/Drinks.kt`: presets wine 15 cl 12 %, rakı 5 cl 45 %, shot 4 cl 40 %, cocktail 20 cl 10 %, beer = own default size, other = custom. Size/alcohol limits widened for all drinks to 2–200 cl and 0–80 %. Long-press +1 → `ui/DrinkDialog.kt` (kind fills size, editable). Settings → Drinks → "Default drink for +1" (not "other"). Counter shows "Beer: 2 · Wine: 1" only when mixed. Lists show the kind. Export writes "kind" only when not beer. Wording: "beer" → "drink" where it counts any drink (EN+TR).
- P23: "Together" mode = new `SessionMode.GROUP` (Versus unchanged). DB v5 (AutoMigration 4→5): table `group_seat(sessionId, side, name)` + `water_event.side` (NULL = me). Seats = RED, BLUE, GREEN, PURPLE, AMBER, YELLOW (fixed light accent tones, AA-tested). Counter's people button → chooser Versus / Together. `ui/GroupSetupDialog.kt`: 2–6 seats (default 4), names, "which seat are you". `ui/GroupScreen.kt`: one card per seat in seat order with drinks −1/+1 and water +1. No winner, no ranking, no highlight; note "counts are only a record for the tab". Seats can be renamed/added mid-session, never removed. Tab: notes per seat + shared; settle splits shared evenly (leftover cents to the first seats). Promille, reminders, care, limits and result cards use only my seat. Widget and tile only open the app in a group. History row "· Together", detail shows drinks + water per seat. Export/restore include seats + water seat.
- Release APK 1.74 MB, AAB 4.4 MB. Permissions unchanged (POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED); no INTERNET (merged release manifest checked). No new dependencies. Lint 0 errors (2 version notices). Tests 171/171. Not on a device: the 3→4 and 4→5 migrations, the restore picker and all new UI still need a phone test.
- Security: restore reads only a user-picked file through SAF (no permission) with strict size, depth and range limits. The file can't add a PIN, can't change other settings, and can't break the DB (one transaction). MiniJson isn't a general parser and only reads our own format.
- Decided while user away – confirm:
  1. Rakı preset = 5 cl at 45 % (a "tek"; some bars pour 3.5 cl). Wine 15 cl 12 %, shot 4 cl 40 %, cocktail 20 cl 10 %.
  2. Calendar only marks session days (no count per day), so there is no "busiest day" look.
  3. Group allows 2–6 seats (plan said 2, 3, 4 or 6; 5 works too). Default 4.
  4. Group seats can't be linked to saved friends yet (names only).
  5. Restore keeps your settings and PIN and replaces only the data (sessions, notes, water, friends, friend results).
  6. Counts in widget/stats/result card now mean all drinks, not only beer.
- Known limits: the Stats "water per drink" in a group counts everyone's water; in a group, the counter's water bar duplicates my seat's water button.

## Phone review status (end of 2026-11-01)
- Approved on the phone: P13, P14, P15, P16, P17, P18, P19, P20, P21, P22, P23 (incl. the redesign 63a6fbf).
- Installed, waiting for review: P24 screen lock, P25 bottom bar, P26 Soy tu Tentación (+ inside Versus/Together, who asks whom, AI questions switch, CSV import), app name SalgamBalgam.
- After the OKs: merge feature/p20-plus into master in order, then ask the user before pushing.
- Phone: wireless adb (the IP/port changes each time, so ask the user). Debug builds installed over existing data.

## Phone review (2026-11-01, step by step, debug builds over existing data — paused by user)
- Installed in order on the phone: P13 (DB 2→3 ran, app started, no crash) → P14 → P15 → P16 → P17 → P18 (currently on phone).
- User approved: P14, P15, P16, P17. P13 + P18 installed but not yet confirmed. Next: user checks P18, then install P19 (bc7ae55), then merge approved features into master and push.
- adb taps are blocked by the auto-mode classifier; screenshots work. Options offered: user taps + I screenshot, a permission rule, or scrcpy (not installed).

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
