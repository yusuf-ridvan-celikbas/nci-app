# P13 Online friends — design proposal (for approval, nothing implemented)

Status: 2026-10-31 user chose **Option A (no internet)**. Implemented on Android as: paste-only import (no QR, no camera,
no dependency), shared fields name/day/beers/duration/water (my side only; Versus without a me side can't share),
friend results in History → Friends, never mixed into stats. Option B is not planned.

## 1. Goal and limits
Let friends see each other's **finished session results** (e.g. "Ali · Fri 31 Oct · 4 beers · 3 h 10 min").
Hard limits from the spec and store policy:
- Share only **display name + counts** (data minimization). Never: body profile, promille, notes/amounts, times of
  single beers, location, contacts.
- **No rankings, leaderboards, streaks or "most beers" highlights** (Apple 1.4.3 / Play: must not encourage drinking).
  Friend results are shown as a plain list, newest first.
- 18+ gate stays; sharing is opt-in per session, off by default.
- Everything else in the app keeps working offline; local data remains the source of truth.

## 2. Options

### Option A — Offline "result card" exchange (recommended first)
After ending a session, the user taps **Share result**. The app builds a tiny card:
`{v:1, name, date (day only), beers, durationMin, water}` (~80 bytes), shown as a **QR code** and also
shareable as a short text code through the share sheet (WhatsApp etc.). A friend scans/pastes it →
"Import result" → stored locally under that friend.
- Network: **none**. No INTERNET permission, no server, no account, no cost.
- Android: QR shown with a small hand-written encoder (~300 lines, no dependency); scanning with the
  **Google code scanner** (play-services-code-scanner, ~0.2 MB, no CAMERA permission — needs approval as a
  dependency) *or* paste-only (no dependency at all).
- iOS: QR via Core Image (`CIQRCodeGenerator`), scanning via VisionKit (needs camera permission text) or paste.
- Integrity: a card is just a claim from a friend; no signature needed (nothing to protect — it's their own
  number). Import validates schema, ranges (beers 0–100, duration ≤ 72 h, name ≤ 30), and rejects anything else.
- Limits: not live; both people must exchange actively.

### Option B — Small backend (Supabase + Row Level Security)
Live friend feed. App talks to Supabase REST (PostgREST) over HTTPS with plain `HttpsURLConnection` /
`URLSession` — **no Supabase SDK** (avoids ~2 MB Ktor dependency).
- Auth: Supabase **anonymous sign-in** (no email/phone). Device keeps the refresh token in
  EncryptedSharedPreferences-equivalent (Android Keystore-wrapped) / iOS Keychain (`ThisDeviceOnly`).
- Friends: each user gets a random **friend code** (8 chars, ~40 bits); adding = entering/scanning a code,
  the other side must accept (mutual table row).
- Tables: `profiles(user_id, display_name)`, `friendships(a, b, status)`, `results(id, user_id, day, beers,
  duration_min, water, created_at)`. RLS: you read only your own rows and rows of users with an *accepted*
  friendship; you write only your own rows. Server-side CHECK constraints for every range and length.
- Rate limits: Postgres function + per-user counters (e.g. 30 results/day, 20 friend requests/day),
  Supabase built-in auth rate limits.
- Deletion: "Delete online account" in Settings calls an RPC that deletes the user and cascades all rows
  (required by Apple 5.1.1(v) when accounts exist). Local "Delete all data" also offers it.
- Hosting: EU region (Frankfurt) for KVKK/GDPR; free tier is enough to start.
- Adds: INTERNET permission (Android), network code, a privacy policy URL, a data-safety form, ongoing ops
  (keys, backups, abuse handling), and App Store "data linked to user: name, usage data".

### Comparison
| | A: result cards | B: Supabase |
|---|---|---|
| INTERNET permission | no | yes |
| Account / server / cost | none | anonymous account, server, free tier |
| Live updates | no | yes |
| Privacy risk | very low (peer-to-peer, user-initiated) | moderate (central store of names + drinking counts) |
| App size | +~10–20 KB (+0.2 MB if Google scanner) | +~30–50 KB (no SDK) |
| Store review | simple | privacy policy, account deletion, data-safety labels |
| Effort | ~2–3 days | ~1–2 weeks incl. backend, RLS tests, abuse limits |

**Recommendation:** ship **A** now; it covers "see friends' results" with no new attack surface.
Do **B** only if live feeds are really wanted, and then reuse A's card format as the payload.

## 3. Threat model (both options)
| Threat | A | B mitigation |
|---|---|---|
| Malicious card / payload (huge, malformed, script) | strict parser, size cap 512 B, ranges, plain text rendering | same + DB CHECK constraints |
| Fake numbers from a "friend" | accepted risk (shown as "from Ali", no ranking → no incentive) | same |
| Stranger reads my results | n/a (I choose who gets the card) | RLS: accepted friendships only; codes random, revocable |
| Friend-code brute force / spam requests | n/a | 40-bit codes, rate limits, requests need acceptance, block |
| Stolen device / token | local data already protected | token in Keystore/Keychain, revocable server-side |
| Server breach | n/a | minimal data (name + counts), no email/phone, EU host, no service key in app |
| Secrets in app | none | only the public anon key (safe by design with RLS); service key never shipped |
| MITM | n/a | TLS only, no cleartext (`usesCleartextTraffic=false`, no ATS exceptions); optional pinning |
| Tracking / profiling | none | no analytics, no IP logging beyond Supabase defaults (documented) |

## 4. Privacy policy (draft outline)
- What: display name you type; for each result you choose to share: date (day), beer count, duration, water count.
- Not collected: body profile, blood-alcohol estimate, notes, amounts, location, contacts, device IDs, ads/analytics.
- Where: A — only on your and your friend's phones. B — Supabase (EU), deleted when you delete the online account.
- Who sees it: only friends you exchanged a card with / accepted.
- Rights (KVKK/GDPR): delete in app at any time; contact address for requests.
- 18+ only.

## 5. Open questions (please answer before implementation)
1. A, B, or A now + B later?
2. For A: paste/share-sheet only (no dependency), or also QR scanning (Android: Google code scanner +0.2 MB, iOS: camera permission)?
3. Which fields to share: beers + duration + water? Include Versus results (score) or solo only?
4. Should imported friend results appear in History/Stats, or only in a separate "Friends" list (recommended: separate, never mixed into stats)?
5. For B only: who owns/pays for the Supabase project, and which contact address goes in the privacy policy?
