# LifeOS

An all-in-one life organization, orchestration and managing powerhouse — a
private, offline-first Android app whose AI runs entirely on-device (Gemma)
or on your own NAS (Ollama). No third-party cloud, ever.

**Spec:** [`docs/PRODUCTION_PLAN.md`](docs/PRODUCTION_PLAN.md). Every module
and rule traces back to a section (and often a community demand source) there.

## Status — v0.1.0-alpha.3

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
| Deferred post-alpha: Clock faces + Glance widgets, system-calendar/ICS mirror, Proton MCP primary mail path, Agentic accessibility macros + NL compiler, Memex archive, Evolution layer, ADHD overlay, HA WebSocket/zones, Vault UI, first-run onboarding checklist (grants live in Settings → System access), FinTS bank sync | 🔜 |

## Install (alpha)

Grab `lifeos-v*.apk` from [Releases](../../releases), then:

```
adb install -r -g lifeos-v0.1.0-alpha.3.apk
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
         imagereasoning,finance,email,nas,books,route,smarthome,planner}
build-logic/           Convention plugins
docs/PRODUCTION_PLAN.md
```

Toolchain: AGP 8.13 · Kotlin 2.2 · Hilt 2.57 · Room 2.8 · Compose alpha BOM
(Material 3 Expressive is public only in material3 1.5 alphas).
