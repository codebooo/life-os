# LifeOS

An all-in-one life organization, orchestration and managing powerhouse — a
private, offline-first Android app that feels like a first-party Google app
and runs its AI entirely on-device (Gemma 4) or on your own NAS (Ollama).

**The authoritative spec is [`docs/PRODUCTION_PLAN.md`](docs/PRODUCTION_PLAN.md)** —
24 feature modules, a shared capture spine, an encrypted vault, cross-module
intelligence rules, and a phased roadmap. Read it before contributing; every
module and rule in the codebase traces back to a section (and often a
community demand source) in that document.

## Status

| Phase | Scope | State |
|---|---|---|
| **0 — Foundation** | Multi-module skeleton, build-logic, M3 Expressive theme, Room DB, DataStore, encrypted Vault scaffolding, app shell + nav, foreground service + boot receiver, CI | ✅ done |
| 1 — AI layer + Chat | `:core:network`, `:core:ai` (Ollama + AiRouter + MCP), `:feature:chat` | ⏳ next |
| 2+ | See roadmap §6 of the plan | — |

## Project layout

```
app/                  Single-activity shell: theme, bottom bar, NavHost
core/model            Pure Kotlin domain models (no Android deps)
core/common           DispatcherProvider, LifeResult/LifeError, base ViewModel, logging
core/designsystem     Material 3 Expressive theme, dynamic color, shared composables
core/database         Room LifeDatabase (all entities/DAOs/migrations)
core/datastore        Typed DataStore settings repositories
core/vault            Encrypted at-rest blob store (Tink + Android Keystore)
core/service          LifeOsForegroundService, LifeEventBus, BootReceiver
core/ui               Navigation contracts (LifeDestination, top-level destinations)
build-logic/          Convention plugins (lifeos.android.*, lifeos.hilt, …)
docs/                 PRODUCTION_PLAN.md — the spec
```

## Building

Requirements: JDK 17+, Android SDK (compileSdk 36). Then:

```
./gradlew assembleDebug        # build the APK
./gradlew testDebugUnitTest    # unit tests
```

Target device: Samsung Galaxy S22 Ultra, Android 13+ (minSdk 33), sideloaded:

```
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```
