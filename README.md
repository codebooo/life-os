# LifeOS

An all-in-one life organization, orchestration and managing powerhouse — a
private, offline-first Android app whose AI runs entirely on-device (Gemma)
or on your own NAS (Ollama). No third-party cloud, ever.

**Spec:** [`docs/PRODUCTION_PLAN.md`](docs/PRODUCTION_PLAN.md). Every module
and rule traces back to a section (and often a community demand source) there.

## Status — v0.1.0-alpha.18

| Area | State |
|---|---|
| Foundation: multi-module, M3 Expressive theme, Room (10 schema versions, auto-migrations), encrypted Vault (Tink+Keystore), foreground service, event bus + rules engine | Done |
| AI: Ollama streaming + on-device Gemma (MediaPipe), AiRouter w/ privacy tag + fallback, NotesRag | Done |
| Capture spine: quick capture, voice brain-dump w/ review sheet, structured logger | Done |
| Notes (Markdown files, vault option, backlinks, ask-my-notes) | Done |
| Time: exact reminders (lockscreen alarm, boot reschedule, NL times), to-do (lists+nesting), local calendar | Done |
| Message Center (notification listener), Email (IMAP to Proton Bridge) | Done |
| Rules live: R1 tracking→package+reminder · R2 invoice→task · R6 leave-by · R7 invite→event · R8 receipt→finance+warranty · R9 subscriptions · R10 brain-dump · R11 @scene tags · R12 quick-capture routing | Done |
| DHL tracking (hourly polling), Scan (CameraX+ML Kit receipts/boards), Finance (budget, subscriptions, warranties, CSV import) | Done |
| Books, Routes, Smart Home (HA REST), NAS browser + server-apps board, Planner "Jarvis" + Home top card | Done |
| Assistant role (long-press home → quick capture), Settings hub, theme palette picker, in-app Gemma model downloads | Done |
| Power-button capture auto-detects timers ("timer 6m"), reminders, calendar events, and time-stamped to-dos ("6pm feed cat") on-device | Done |
| Time-stamped to-dos surface in the Calendar; Clock has a Samsung-style wheel timer (mm:ss↔seconds toggle) + stopwatch laps; Routes embeds a native osmdroid OpenStreetMap | Done |
| Calendar v2: Month/Week/Day views, hour timeline, tap-to-edit, all-day + minute precision, Proton ICS one-way sync + .ics export | Done |
| Offline voice via open-source Vosk (one-time 40 MB model) — the Google recognizer is gone; nav hand-offs use plain geo: URIs | Done |
| Customizable bottom bar (toggle + reorder) + Home grid/list toggle with long-press drag-arrange; planner accept/skip persists (✓ completes the to-do); reminder alarms ring on the ALARM stream even in silent mode | Done |
| Clock (analog/digital/word faces, world clock, stopwatch, timer) | Done |
| ADHD tools: visual focus timer, streaks, overwhelm "What's next?" overlay (SYSTEM_ALERT_WINDOW) | Done |
| Memex archive: share-sheet clip + timeline, annotate-to-keep, 12-month auto-purge | Done |
| Agentic macros: natural-language → validated IR (MacroCompiler) → accessibility executor, dry-run gated | Done |
| Evolution layer: on-device interaction log + planner accept-rate; Planner accept/skip feeds it | Done |
| Calendar: one-way system Calendar Provider mirror + iCalendar (RFC 5545) codec (Proton ICS bridge, §8.6) | Done |
| Mail MCP client (JSON-RPC over HTTP/SSE) for the NAS Proton mail MCP; IMAP fallback stays primary | Done |
| Assistant overlay (Gemini-style): long-press home floats a glowing-border panel over any app — auto-listens (Vosk), text input, deep module commands (timers, packages, planner, to-dos) + AI fallback | Done |
| Foreground AlarmService rings reminders on the ALARM stream (silent-mode/app-killed proof) + "Test alarm" button; Clock gains a tap-to-add time-zone map and a zone converter | Done |
| Jarvis v3: chat is pure LLM (no pre-canned answers) — every module exposes live content via a prompt snapshot, and the model acts through `[[tool: args]]` lines the app executes + confirms (tasks, timers, reminders, events, notes, search); power phrases stay in the power menu/overlay | Done |
| New modules: Downloader (on-device stream extraction → Downloads, incl. HLS), Plants (offline care atlas + every-N-days watering reminders), News (RSS tile scroll: tagesschau, ZEIT, SZ, DLF, taz), hidden Vault (long-press "LifeOS" title, biometric/PIN, encrypted gallery w/ sort) | Done |
| Routes v2: tap-tap route planning with car/bike/foot timings + distance via OSRM, polyline on the osmdroid map | Done |
| Vault v2: dedicated screens per item — Proton-Pass-style logins (password generator, on-device TOTP 2FA, custom fields, attachments), Markdown secure texts, zoomable image gallery (downsampled, no OOM); hidden behind a 5-second hold on the Home title | Done |
| Notes editor: Word-style Markdown toolbar (bold/italic/heading/list/quote/code/link on the selection), rendered⇄raw toggle + pen; optional readable mirror to /Internal storage/LifeOS/Notes/*.md (all-files access) | Done |
| Focus timer: tap the ring for a custom HH:MM:SS time, plus an "Overlay" button that floats the countdown over any app (tap once for a close X that leaves it running); Overwhelm overlay now follows the theme | Done |
| Screen Time: mirrors Android digital-wellbeing into LifeOS and keeps it forever (survives Samsung's ~monthly purge) — weekly bars + average, week scrolling, tap a day for its own apps/unlocks, JSON export. Totals are derived from the raw RESUMED/PAUSED event stream, not `queryAndAggregateUsageStats` (which reports whole-bucket sums per day) | Done |
| Screen Time · Plants (custom photos + care atlas) · News · Downloader on Home; NAS server apps redesigned as an app-store list; Jarvis answers from a live data snapshot (note bodies included) and a Developer Options "Jarvis Debugging" toggle exposes snapshot/output/tool-calls with a copy button | Done |
| Brick (§Module Brick): tap-to-block modes — pick blocked apps + optional per-app daily allowances, turn a mode on/off by NFC tag, time window or by hand, strict mode refuses early exits; blocked apps hit a full-screen wall via an accessibility blocker (the only route Android gives a sideloaded app). Blocking rules covered by unit tests | Done |
| Screen Time day insight: tapping a bar selects that day inline (others dim) and the stat cards switch to that day; the download button opens an export dialog (JSON, CSV by day, CSV by app; week or all history) writing to Downloads | Done |
| Calendar: long-press-and-drag in day/week view sweeps a time range (5/10/15/30/60-minute steps) and opens the editor pre-filled; pinch to zoom the time ladder between 5-minute and hourly steps; the event sheet opens fully expanded | Done |
| Pastebin module: create pastes with expiry, visibility, syntax format, password and burn-after-read; sign in to list, open and delete your pastes; share-sheet defaults live in its Settings tab | Done |
| Share to LifeOS: sharing text or a link shows a small floating chooser over the source app (LifeOS never opens) - log it to the capture inbox, or mint a Pastebin link with your share defaults and copy it | Done |
| Downloader: browsable catalogue of 70+ supported sites (ThisVid, LinkedIn, Threads, Reddit, Instagram, file hosts, broadcasters, audio) plus site helpers - Reddit post JSON, LinkedIn progressive streams, Instagram/Threads embeds, Vimeo player config, kt_player/KVS link decoding and one level of iframe following | Done |
| Navigation bar: any module can be a bottom-bar tab, not just Calendar/Jarvis/Inbox/Tasks | Done |
| Clock timer: tap an h/m/s wheel to type the value on a number keyboard; a full number hops to the field on the right automatically | Done |
| Clear Sky Map module: full clearoutside.com forecast for any spot (search, coordinates or device location) - hourly good/OK/bad ratings, total/low/medium/high cloud, visibility, fog, precipitation, wind, temperature, dew point, humidity, pressure, ozone, sun/moon ephemeris, dark windows and estimated sky quality/Bortle class. No API key needed; place lookup via OpenStreetMap Nominatim | Done |
| Navigation bar settings list every module (was still showing only the original four), with a searchable picker; the default bar stays Home + Calendar/Tasks/Inbox/Jarvis | Done |
| Downloader resolves session-bound player links (ThisVid and the rest of the kt_player family) by letting the page's own player run in an offscreen WebView and recording the media request, then downloads it with that session's Referer, Cookie and User-Agent; teaser/sprite/ad URLs are filtered out | Done |
| Brick tags are programmed on pairing (LifeOS MIME record + Android Application Record), so a tap flips a mode with the app closed and without a chooser; broader NDEF/TECH/TAG filters, tag id read from the record, and an NFC-off banner that opens NFC settings | Done |
| Deferred post-alpha: Glance home-screen widgets, HA WebSocket live state/zones, Vault unlock UI, first-run onboarding checklist (grants live in Settings → System access), FinTS bank sync | Planned |

**Google-free by design:** no Google service is ever called at runtime (no Play Services, no Google recognizer, no Google Maps). Remaining Google-*authored* open-source, fully on-device libraries: AndroidX/Jetpack (unavoidable on Android), MediaPipe (Gemma inference), ML Kit on-device OCR/barcode (no network) — swap candidates documented in the plan.

## Install (alpha)

Grab `lifeos-v*.apk` from [Releases](../../releases), then:

```
adb install -r -g lifeos-v0.1.0-alpha.18.apk
```

or copy to the phone and allow *Install unknown apps*. Android 13+ (minSdk 33).

On-device AI: push a Gemma `.task`/`.litertlm` model to
`Android/data/com.lifeos/files/models/` (§8.2). NAS AI: set the Ollama URL in
Assistant → settings (§8.3). Both optional — everything degrades gracefully.

## Building

JDK 17+, Android SDK (compileSdk 37):

```
./gradlew assembleDebug testDebugUnitTest   # dev
./gradlew :app:assembleRelease              # signed sideload build
```

Release signing uses the committed `release.keystore` (personal sideload app;
stable key so updates never wipe the DB/Vault — §9.4).

## Layout

```
app/                   Shell: theme, bottom bar, NavHost, Home grid + planner card
core/{model,common,designsystem,database,datastore,network,ai,service,vault,ui}
feature/{chat,capture,notes,reminders,todo,calendar,messagecenter,dhl,
         imagereasoning,finance,email,nas,books,route,smarthome,planner,
         clock,adhd,memex,agentic,evolution,downloader,plants,news,vault,
         screentime,brick}
build-logic/           Convention plugins
docs/PRODUCTION_PLAN.md
```

Toolchain: AGP 8.13 · Kotlin 2.2 · Hilt 2.57 · Room 2.8 · Compose alpha BOM
(Material 3 Expressive is public only in material3 1.5 alphas).
