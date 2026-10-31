# SPEC
Native beer-counter app. Name undecided (vote): "SoyTuTentación" | "SalgamBalgam". Default: SoyTuTentación. Priorities: 1) security/privacy 2) minimal size/RAM/battery.

## Rules
- Build phases in order. Per phase: implement → unit-test domain logic → report (release size, permissions, security trade-offs).
- Ask before adding any dependency (state size + why SDK can't do it).
- No analytics/ads/crash SDKs/tracking. No WebView, dynamic code, clipboard reads.
- Ambiguity → ask, don't guess.
- Keep files small; no boilerplate comments.
- From P1: zero hardcoded UI strings (all in resources, EN default + TR); all colors via theme tokens. Versus RED/BLUE stay fixed in every theme.

## Stack
Android (P1–P11): Kotlin, Compose, Material3, Glance widget, Room, Preferences DataStore, AlarmManager/WorkManager (justify; avoid SCHEDULE_EXACT_ALARM). minSdk 26, latest targetSdk. Release: R8 full, minify+shrinkResources, AAB, vectors only, no bundled fonts. Target <5 MB.
iOS (P12): SwiftUI, iOS 17+, WidgetKit+AppIntents, SwiftData via App Group, UNUserNotificationCenter.

## Data (Room)
- Session(id, startedAt, endedAt?, mode: SOLO|VERSUS)
- BeerEvent(id, sessionId, side: SOLO|RED|BLUE, sizeMl, abv(default 5.0), at, finishedAt?)
- WaterEvent(id, sessionId, at)
- Note(id, sessionId, text≤1000, person?≤30, amount?≥0, createdAt, editedTime?)
- FriendProfile(id, name≤30, color)
- BodyProfile (DataStore, optional, local only): sex M|F|unspecified, age 18–100, heightCm 120–230, weightKg 35–250, license NONE|CANDIDATE|NORMAL|COMMERCIAL
Counts derive from events, never stored totals.

## Security (all phases)
- No INTERNET permission until P13; verify merged manifest.
- Only needed perms (expected POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED); justify each.
- exported=false except launcher + widget receiver; receivers validate actions.
- PendingIntent FLAG_IMMUTABLE, explicit intents.
- allowBackup=false or dataExtractionRules excluding DB.
- Strip Log in release (R8). Parameterized queries. Validate/limit all input.
- Notifications VISIBILITY_PRIVATE + neutral public version.
- Run lint each phase; fix security warnings.

## Store policy
Apple 1.4.3 / Play: no encouraging excess drinking. Frame as tracker/bar tab. 18+ confirm on first launch. No badges/rewards for quantity. Promil screen must never imply fitness to drive. Neutral scores ("Red: 3 · Blue: 2"). Always-visible "End session".

## Phases (priority order)
P1 Core counter
- Data model, repository (serialized writes).
- Big "+1" button; copy: "Ordered from the bartender? Tap now."
- Undo snackbar 5 s + manual −1; never <0.
- Session auto-starts on first tap; manual end; auto-close after inactivity (default 6 h).
- 18+ gate.

P2 Settings & personalization
- App name switch: SoyTuTentación | SalgamBalgam (temporary, for vote). Updates in-app title, widget label, notification title. Android launcher label+icon via 2 activity-alias (enable one, disable other; no restart prompt loop). iOS: launcher name can't change at runtime → in-app name + alternate icon only.
- Language: System | Türkçe | English. Android per-app locale (AppCompatDelegate.setApplicationLocales + locales_config.xml). Apply without data loss.
- Theme mode: System | Light | Dark.
- Color theme (accent, combinable with mode): Amber (default, beer), Red, Blue, Yellow, Green, Purple. Each = M3 ColorScheme light+dark; check WCAG AA contrast. Optional "Dynamic (Android 12+)".
- Widget follows chosen name/theme/language.
- Store all in DataStore; one Settings screen, grouped: General (name, language), Appearance (mode, color), later sections added by other phases.

P3 Widget
- Glance: count + "+1" via actionRunCallback; no app open; instant refresh; rapid-tap safe.
- Optional 2nd size: count + time since last beer.

P4 Reminder
- After each +1 schedule local notif (default 10 min, 5–60 configurable): "Is your beer finished?"
- Actions: "Yes, got another (+1)" → set finishedAt on current beer, increment+reschedule; "Still drinking" → snooze 1 interval; "Stop for tonight" → cancel for session.
- New +1 cancels/reschedules (no duplicates). Settings: off, interval, silent.
- Ask POST_NOTIFICATIONS only when user enables reminders, with rationale.
- Restore after reboot.

P5 Notes / tab
- Per-session list; quick-add field (Enter = save with current time).
- createdAt auto (default); time editable via picker → editedTime, keep original.
- Optional person + amount; running tab total.

P6 Beer size
- Default size setting (33/50/custom cl, default 50) + default ABV % (default 5.0). Long-press +1 → pick size for that beer.

P7 Promil estimator (basic)
- Screen + compact card on main screen. Needs BodyProfile; if missing → prompt to fill (skippable).
- Inputs: profile + session BeerEvents (sizeMl, abv, at, finishedAt). finishedAt missing → at+20 min (or next beer's at). Editable per beer.
- Model (pure Kotlin, unit-tested, no lib):
  grams = sizeMl·abv/100·0.789
  TBW (Watson): M = 2.447−0.09516·age+0.1074·h+0.3362·w ; F = −2.097+0.1069·h+0.2466·w ; unspecified → F (conservative).
  Absorption linear from at→finishedAt+30 min. Simulate 1-min steps from first beer: bac += absorbedG·0.8/TBW ; bac = max(0, bac−0.15/60). Output ‰ (g/L ÷ 1.055), round UP to 2 decimals.
  Show: current estimate, peak, estimated time to 0.00. Never show "time until legal to drive".
- Always-visible disclaimer: "Estimate only. Not a breathalyzer. Never drink and drive."
- Recompute on each +1/edit and every minute while screen visible (no background work for this).

Warnings (TR locale only; EN → generic "Limits vary by country. Don't drink and drive.", no numbers):
- Limits in one config object (lawVersion, lastVerified, limits per license). Current (verify before release; 2026 bill may lower NORMAL to 0.20): CANDIDATE 0.20, COMMERCIAL 0.20, NORMAL 0.50.
- license ≠ NONE:
  ≥ limit → full-width red "UYARI: Tahmini promiliniz yasal sınırın (X‰) üzerinde. Araç kullanmayın." + quick action "Taksi/ulaşım" (opens dialer/maps intent, no network perm).
  0 < bac < limit → amber "Sınırın altında görünse de tahmindir. Alkollü araç kullanmayın."
- license = NONE: time-based friendly msg: 05:00–11:59 "Günaydın! Bol su, iyi dinlenme."; 12:00–17:59 "Keyifli günler!"; 18:00–04:59 "GECENİZİN KEYFİNİ ÇIKARIN! 🍻".
- Any license, bac ≥ 1.50 → health caution "Yavaşla, su iç."; ≥ 3.00 → "Alkol zehirlenmesi riski. Kendini kötü hissedersen 112'yi ara." (dial intent).
- BodyProfile = sensitive: never exported unless user ticks it, wiped by "Delete all data".

P8 Versus (offline)
- Screen split red/blue, each own name, +1, count, undo.
- Sides linkable to FriendProfile (local).
- Tab split: assign notes with amount to RED/BLUE/shared → per-side totals.

P9 Water reminder
- Toggle: "Every N beers, drink water" (default 2). Water "+1" button; logs WaterEvent.

P10 History & stats
- Past sessions: date, beers, duration, notes count; detail view; delete.
- Stats: peak ‰/session, beers/session, avg pace (min/beer), water ratio. Plain Compose drawing, no chart lib.

P11 Export & hardening
- Export JSON via share sheet (no storage perm). "Delete all data".
- Audit: manifest, exported components, R8 mapping, size report.

P12 iOS port
- Same model/features. Widget Button(intent:). App Group shared store. Data Protection complete. No ATS exceptions.

P13 Online friends (design first, implement after approval)
- Propose: backend (e.g. Supabase + RLS) vs QR exchange of session results; auth; threat model; privacy policy; data minimization (share only counts + display name).
- Adds INTERNET perm only in this phase. TLS only, no secrets in app, server-side validation, rate limiting.
