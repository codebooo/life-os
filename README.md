# LifeOS

An all-in-one life organization, orchestration and managing powerhouse — a
private, offline-first Android app whose AI runs entirely on-device (Gemma)
or on your own NAS (Ollama). No third-party cloud, ever.

**Spec:** [`docs/PRODUCTION_PLAN.md`](docs/PRODUCTION_PLAN.md). Every module
and rule traces back to a section (and often a community demand source) there.

## Status — v0.1.0-alpha.7

| Area | State |
|---|---|
| Foundation: multi-module, M3 Expressive theme, Room (10 schema versions, auto-migrations), encrypted Vault (Tink+Keystore), foreground service, event bus + rules engine | ✅ |
| AI: Ollama streaming + on-device Gemma (MediaPipe), AiRouter w/ privacy tag + fallback, NotesRag | ✅ |
| Capture spine: quick capture, voice brain-dump w/ review sheet, structured logger | ✅ |
| Notes (Markdown files, vault option, backlinks, ask-my-notes) | ✅ |
| Time: exact reminders (lockscreen alarm, boot reschedule, NL times), to-do (lists+nesting), local calendar | ✅ |
| Message Center (notification listener), Email (IMAP to Proton Bridge) | ✅ |
| Rules live: R1 tracking→package+reminder · R2 invoice→task · R6 leave-by · R7 invite→event · R8 receipt→finance+warranty · R9 subscriptions · R10 brain-dump · R11 @scene tags · R12 quick-capture routing | ✅ |
| DHL tracking (hourly polling), Scan (CameraX+ML Kit receipts/boards), Finance (budget, subscriptions, warranties, CSV import) | ✅ |
| Books, Routes, Smart Home (HA REST), NAS browser + server-apps board, Planner "Jarvis" + Home top card | ✅ |
| Assistant role (long-press home → quick capture), Settings hub, theme palette picker, in-app Gemma model downloads | ✅ |
| Power-button capture auto-detects timers ("timer 6m"), reminders, calendar events, and time-stamped to-dos ("6pm feed cat") on-device | ✅ |
| Time-stamped to-dos surface in the Calendar; Clock has a Samsung-style wheel timer (mm:ss↔seconds toggle) + stopwatch laps; Routes embeds a native osmdroid OpenStreetMap | ✅ |
| Calendar v2: Month/Week/Day views, hour timeline, tap-to-edit, all-day + minute precision, Proton ICS one-way sync + .ics export | ✅ |
| Offline voice via open-source Vosk (one-time 40 MB model) — the Google recognizer is gone; nav hand-offs use plain geo: URIs | ✅ |
| Customizable bottom bar (toggle + reorder) + Home grid/list toggle with long-press drag-arrange; planner accept/skip persists (✓ completes the to-do); reminder alarms ring on the ALARM stream even in silent mode | ✅ |
| Clock (analog/digital/word faces, world clock, stopwatch, timer) | ✅ |
| ADHD tools: visual focus timer, streaks, overwhelm "What's next?" overlay (SYSTEM_ALERT_WINDOW) | ✅ |
| Memex archive: share-sheet clip + timeline, annotate-to-keep, 12-month auto-purge | ✅ |
| Agentic macros: natural-language → validated IR (MacroCompiler) → accessibility executor, dry-run gated | ✅ |
| Evolution layer: on-device interaction log + planner accept-rate; Planner accept/skip feeds it | ✅ |
| Calendar: one-way system Calendar Provider mirror + iCalendar (RFC 5545) codec (Proton ICS bridge, §8.6) | ✅ |
| Mail MCP client (JSON-RPC over HTTP/SSE) for the NAS Proton mail MCP; IMAP fallback stays primary | ✅ |
| Deferred post-alpha: Glance home-screen widgets, HA WebSocket live state/zones, Vault unlock UI, first-run onboarding checklist (grants live in Settings → System access), FinTS bank sync | 🔜 |

**Google-free by design:** no Google service is ever called at runtime (no Play Services, no Google recognizer, no Google Maps). Remaining Google-*authored* open-source, fully on-device libraries: AndroidX/Jetpack (unavoidable on Android), MediaPipe (Gemma inference), ML Kit on-device OCR/barcode (no network) — swap candidates documented in the plan.

## Install (alpha)

Grab `lifeos-v*.apk` from [Releases](../../releases), then:

```
adb install -r -g lifeos-v0.1.0-alpha.7.apk
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
         clock,adhd,memex,agentic,evolution}
build-logic/           Convention plugins
docs/PRODUCTION_PLAN.md
```

Toolchain: AGP 8.13 · Kotlin 2.2 · Hilt 2.57 · Room 2.8 · Compose alpha BOM
(Material 3 Expressive is public only in material3 1.5 alphas).
