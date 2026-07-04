# LifeOS — Production Plan (v3)

**Target device:** Samsung Galaxy S22 Ultra · Android 13+ (One UI 5+) · sideloaded, no Play Store constraints
**Stack:** Kotlin · Jetpack Compose · **Material 3 Expressive** · single-activity · multi-module Gradle (Kotlin DSL)
**On-device AI:** Gemma 4 (E2B/E4B) · **NAS AI:** Gemma 4 (12B / 26B A4B) via Ollama
**Design intent:** feels like a first-party Google app on a Pixel — authentic Material 3 Expressive, dynamic color, spring motion.
**Audience:** an AI coding agent implementing the app module-by-module, top-to-bottom.

> **v3 change log.** This revision folds in 17 community-sourced feature ideas the product owner selected from the research report. Each is implemented as either a new module or a documented extension of an existing one, and each carries an inline **`[src N]`** tag mapping to **Appendix C — Community sources**, so the coding agent can read the original demand and design intent. New capture/notes/archive features form a shared "capture spine"; the Jarvis planner and NL-macro compiler lean on the existing on-device AI + agentic layers. Nothing in the AI/mail/calendar integration decisions changed.
>
> **Features added in v3:** structured logger `[src 11]`, personal Memex archive `[src 12]`, physical-board→digital `[src 15]`, voice-first brain-dump `[src 16]`★, encrypted vault `[src 20]`, one-tap self-hosting `[src 23]`, swipe-to-categorize `[src 24]`, receipt→finance+warranty `[src 25]`, subscription detection `[src 26]`, privacy-first budget/Mint import `[src 27]`, local-first plaintext notes `[src 28]`, on-device AI over notes `[src 29]`, agentic day planner "Jarvis" `[src 40]`★, natural-language macro creation `[src 41]`★, calendar/presence→home automations `[src 43]`, what-to-read-next `[src 45]`, privacy book tracker `[src 46]`. (★ = owner-highlighted.)

---

## Three integration decisions to read first
Checked against current sources; unchanged from v2.

**1. Gemma 4 — real, ideal for this app.** Released April 2026 (Apache-2.0), built from Gemini-3 research. Sizes **E2B / E4B** (mobile, **multimodal: text+image+audio**), **12B Unified**, **26B A4B** (MoE), **31B**. Context 128K (edge)/256K (large), native **function calling**, native `system` role, configurable **thinking mode**. QAT mobile format → **E2B ~1 GB** (text-only <1 GB), Q4_0 ~3.2/5 GB (E2B/E4B). On-device via **LiteRT-LM / Google AI Edge (AICore)**; NAS via **Ollama**. → On-device: E2B/E4B; NAS: 12B/26B A4B.

**2. Proton Mail — via `protonmail-pro-mcp`, but know what it is.** A Node/TS **MCP server** that is **SMTP+IMAP through Proton Bridge** under the hood (not a hidden Proton API). Run it on the NAS behind an **MCP-over-HTTP/SSE** transport; `:core:ai`'s MCP client connects over LAN/VPN. **Bridge/hydroxide must run on the NAS.** Give it only a **Bridge app password** (never the account password); it's an unvetted single-maintainer repo — review/pin/fork it. App keeps a **direct Jakarta-Mail IMAP/SMTP fallback**.

**3. Proton Calendar — no CalDAV/public API (confirmed June 2026).** Integration is **iCalendar (RFC 5545)** (ical4j) + Android system Calendar Provider + **one-way ICS bridges** to Proton (import Full-view share link; publish an ICS Proton subscribes to). No two-way Proton sync exists; stated in-product.

---

## Table of Contents
1. Architecture Blueprint
2. Module-by-Module Specification (**24 feature modules + core:vault**)
3. Cross-Module Intelligence Rules
4. Permissions & Manifest Plan
5. AI Routing Strategy (Gemma 4) — incl. Jarvis planner, NL-macro compiler, notes RAG
6. Implementation Roadmap (phased, tiered)
7. UI/UX Design System — Material 3 Expressive
8. Setup & Integration Guide
9. Dependency & Build Configuration Plan
- Appendix A — Definition of done · Appendix B — Global non-functionals · **Appendix C — Community sources**

---

# 1. Architecture Blueprint

## 1.1 Architectural style
Clean, layered, offline-first, unidirectional data flow: **UI (Compose) → ViewModel (StateFlow) → Repository → { Room | Network | AI | Service | Vault } DataSources**. DI: Hilt. Concurrency: Coroutines/Flow (`Dispatchers.IO`, injected `DispatcherProvider`). One immutable `UiState` `StateFlow` + `onEvent(UiEvent)` per screen. Persistence: single Room DB (feature-partitioned) + DataStore; **sensitive rows encrypted via `:core:vault`**. Background: one foreground service for coordination; WorkManager for deferrable/periodic jobs; AlarmManager for exact reminders.

## 1.2 Module graph
```
                         ┌──────────────┐
                         │    :app      │  depends on every :feature:* and :core:*
                         └──────┬───────┘
   ┌───────────────┬────────────┼───────────────┬────────────────┐
   ▼               ▼            ▼                ▼                ▼
:feature:dashboard :feature:capture ... (22 more feature modules) ...
   └───────────────┴─────┬──────┴───────┬────────┴────────┬───────┘
                         ▼              ▼                 ▼
                   :core:ui        :core:ai         :core:service
   ┌──────────┬──────────┼──────────┬──────────┬──────────┬─────────┐
   ▼          ▼          ▼          ▼          ▼          ▼         ▼
:core:designsystem :core:database :core:datastore :core:network :core:vault :core:common
                         │
                         ▼
                   :core:model   (pure Kotlin, no Android deps)
```

| Core module | Responsibility |
|---|---|
| `:core:model` | Pure Kotlin domain models/enums. |
| `:core:common` | `DispatcherProvider`, `Result`/`LifeError`, date/time, base ViewModel, logging. |
| `:core:designsystem` | M3 Expressive theme, dynamic color, tokens, motion, shape morphing, shared composables (§7). |
| `:core:database` | Room `LifeDatabase`, all entities/DAOs/converters/migrations. |
| `:core:datastore` | Typed DataStore repos (settings, flags, per-module config, credential refs). |
| `:core:network` | OkHttp/Retrofit factories, NAS TLS, connectivity observer, **HA WebSocket/REST client**. |
| `:core:ai` | `AiRouter`, `GemmaEngine` (LiteRT-LM/AICore), `OllamaClient`, MCP client, context envelope, tool-calls, **PlannerEngine**, **MacroCompiler**, **NotesRag**. |
| `:core:service` | `LifeOsForegroundService`, `LifeEventBus`, `RulesEngine`, `LifeAction` executors, scheduler façade. |
| `:core:vault` **(new, `[src 20]`)** | Encrypted at-rest store for sensitive captures (notes/photos/recordings/finance docs). Android Keystore-backed asymmetric envelope encryption (Tink); write-without-unlock, read-on-unlock; filesystem-backed blobs for safe NAS sync. Exposes `VaultRepository` used by capture/notes/memex/finance. |
| `:core:ui` | Navigation contracts (`LifeDestination`, deep links), `AiInputBar` host, adaptive scaffolds, **QuickCaptureSheet** host. |

**Feature modules (24):** `email`, `reminders`, `todo`, `clock`, `adhd`, `finance`, `messagecenter`, `nas`, `voice`, `imagereasoning`, `agentic`, `evolution`, `fileorganizer`, `dhl`, `books`, `route`, `chat`, `dashboard`, `calendar`, **`capture`** `[11,16]`, **`notes`** `[28,29]`, **`memex`** `[12]`, **`smarthome`** `[43]`, **`planner`** `[40]`.

**Dependency rules:** a `:feature:*` may depend on any `:core:*` but never another `:feature:*`; cross-feature effects flow through `:core:service` (event bus + `LifeAction`) or repository interfaces in `:core:database`. The capture spine is a `:core` concern (`QuickCaptureSheet` in `:core:ui` + `CaptureRouter` in `:core:capture`-adjacent code inside `:core:common`) so any module can raise capture without a feature-to-feature edge.

## 1.3 Single-activity navigation (M3 Expressive)
- `MainActivity` → `LifeOsTheme { LifeOsApp() }`; adaptive `Scaffold` + type-safe `NavHost`; short M3E `NavigationBar` / rail / drawer by `WindowSizeClass`.
- **Top-level (bottom bar):** `Home`, `Calendar`, `Tasks` (To-Do+Reminders), `Inbox` (Messages+Email), `Assistant` (AI Chat). All other modules — including the new **Capture, Notes, Memex, Planner, Smart Home** — live in the Home **app-grid** and are deep-linkable. **Planner** also surfaces as the top card on Home (§Module 24).
- **Global quick-capture:** a persistent capture affordance (FAB long-press, `AiInputBar` mic, quick-settings tile, and the `QuickAddWidget`) opens the `QuickCaptureSheet` from anywhere → routes to Capture/Notes/To-Do (§Module 20, `[src 16]`).
- Deep links: `lifeos://note/{id}`, `lifeos://log/{formId}`, `lifeos://memex/{id}`, `lifeos://plan/today`, `lifeos://home/scene/{id}`, plus the v2 set (reminder/event/package/tx/chat).
- Transitions: `SharedTransitionLayout`+`AnimatedContent`; M3E spring `MotionScheme`.

## 1.4 Persistent foreground service
`LifeOsForegroundService` (started + foreground, type `dataSync`+`specialUse`) owns the `LifeEventBus`, `RulesEngine`, `LifeAction` executors, and the Scheduler façade; optional AI warm-keep. System-bound services (NLS, Accessibility, VoiceInteraction) publish to the same bus. **New in v3:** the service also (a) hosts the **Home Assistant event subscription** (WebSocket) so HA state changes become `LifeEvent`s, and (b) runs the **Planner tick** (a periodic WorkManager job that invokes the `PlannerEngine` to recompute "what's next"). Survives reboot/battery-opt via WorkManager + AlarmManager + boot receiver; single process → shared in-memory bus, Room as source of truth.

## 1.5 Unified data layer — event bus + rules engine
Unchanged mechanism (write → publish `LifeEvent` → `RulesEngine` matches `CrossModuleRule`s → `LifeAction`s → writes → loop-guarded cascades; provenance via `SourceRef`). **v3 additions to the contracts:**
```kotlin
sealed interface LifeEvent { /* v2 events … */
  data class CaptureCreated(val captureId:Long, val kind:CaptureKind, val text:String?):LifeEvent   // [11,16]
  data class ReceiptScanned(val docId:Long, val merchant:String?, val total:Money?, val warrantyMonths:Int?):LifeEvent // [25]
  data class SubscriptionDetected(val merchant:String, val amount:Money, val cadence:Cadence, val sourceTxId:Long):LifeEvent // [26]
  data class HomeStateChanged(val entityId:String, val state:String):LifeEvent                       // [43]
  data class NoteSaved(val noteId:Long, val title:String):LifeEvent                                  // [28]
  data class PlanRecomputed(val topItem:PlanItem?):LifeEvent }                                        // [40]
sealed interface LifeAction { /* v2 actions … */
  data class OrganizeBrainDump(val captureId:Long):LifeAction        // [16] → Notes/To-Do via AI
  data class CreateWarranty(val spec:WarrantySpec, val source:SourceRef):LifeAction  // [25]
  data class SuggestCancelSubscription(val merchant:String, val source:SourceRef):LifeAction // [26]
  data class RunHomeScene(val sceneId:String):LifeAction             // [43]
  data class FileToVault(val blobRef:String, val meta:VaultMeta):LifeAction }        // [20]
```

## 1.6 Repository & ViewModel conventions
Unchanged: repository interfaces + `@Singleton` Hilt impls (`Flow` reads, `suspend` writes → `Result`, publish `LifeEvent` after mutating writes); `@HiltViewModel` with one `UiState` `StateFlow` + `onEvent` + `Channel<Effect>`; `XScreen`(stateless)/`XRoute`(stateful). **Sensitive repositories** (notes, memex, finance docs, vault captures) obtain a `VaultRepository` and store blob bodies encrypted, keeping only non-sensitive metadata in plaintext Room columns for indexing/search.

## 1.7 Room database overview
Single `LifeDatabase` (Room 2.7.x), partitioned by package, centralized migrations. v2 cross-cutting FKs retained. **New v3 entities/relationships:**
- **Capture/Logger `[11]`:** `LogFormEntity`(name, fieldsJson, chartConfig), `LogEntryEntity`(formId FK, valuesJson, at, source), `CaptureEntity`(kind, text?, blobRef?, routedTo, at).
- **Notes `[28,29]`:** `NoteEntity`(path, title, frontmatterJson, bodyVaultRef, updatedAt, embeddingId?), `NoteLinkEntity`(fromNoteId, toNoteId|targetModule/entityId), `NoteEmbeddingEntity`(noteId FK, vector BLOB) for on-device semantic search.
- **Memex `[12]`:** `ArchiveItemEntity`(uri/appPkg, kind, capturedAt, annotated:Boolean, annotationVaultRef?, expiresAt), `TrailEntity`(name), `TrailStepEntity`(trailId FK, archiveItemId FK, order).
- **Finance additions:** `WarrantyEntity`(productName, purchaseTxId? FK, purchasedAt, warrantyMonths, docFileId?, reminderId?) `[25]`; `SubscriptionEntity`(merchant, amount, cadence, firstSeenTxId FK, lastChargedAt, status, cancelUrl?) `[26]`.
- **Smart Home `[43]`:** `HaConnectionEntity`(baseUrl, tokenRef, wsState), `HaEntityEntity`(entityId, friendlyName, domain, lastState), `HaSceneEntity`(sceneId, name), `HaAutomationLinkEntity`(trigger:CalendarEvent|Zone|Reminder, targetSceneId, offsetMin).
- **Planner `[40]`:** `PlanItemEntity`(kind, refModule, refEntityId, score, reason, plannedFor, at), `PlanSnapshotEntity`(generatedAt, contextHash).
- **Vault `[20]`:** blobs live on the filesystem (app-private + optional NAS mirror); Room holds `VaultBlobEntity`(ref, algo, keyAlias, sizeBytes, createdAt, nasSynced) — no plaintext bodies.

---

# 2. Module-by-Module Specification

Format: **APIs/Libraries · Room · Composables/Screens · Service & AI · Permissions · External deps**. Extended modules mark v3 additions with **`[src N]`**. UI conventions defined once in §7.

---

### Module 1 — Email Sorter (`:feature:email`)
- **APIs/Libraries:** primary = **`protonmail-pro-mcp`** on the NAS via `:core:ai` MCP client; fallback = **Jakarta Mail** IMAP IDLE/SMTP to NAS Proton Bridge. AI classification via `:core:ai`.
- **Room:** `EmailAccountEntity`, `EmailMessageEntity`(…, hasInvoiceSignal, hasCalendarInvite, **hasSubscriptionSignal `[src 26]`**), `EmailCategoryEntity`, `EmailSortRuleEntity`.
- **Composables/Screens:** `InboxScreen` (M3E search app bar, filter chips, swipe actions), `MessageDetailScreen`, `CategoryManagerScreen`, `SortRuleEditorScreen`. FAB = compose.
- **Service & AI:** on new mail → classify (category/importance/invoice/`.ics` invite/**subscription-receipt signal**) → publish `EmailReceived`. **v3:** when a message looks like a subscription receipt or renewal notice, it carries `hasSubscriptionSignal` and can supply a **cancel URL** to Finance's subscription detector (R9, `[src 26]`).
- **Permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`. **External deps:** Bridge app-password `TODO("insert key")` (NAS-side).

---

### Module 2 — Reminders (`:feature:reminders`)
- **APIs/Libraries:** AlarmManager exact (`setAlarmClock`), full-screen intent, boot reschedule, RRULE recurrence.
- **Room:** `ReminderEntity`(…, sourceModule, sourceEntityId).
- **Composables/Screens:** `RemindersScreen`, `ReminderEditorScreen` (NL time via AI), full-screen `AlarmActivity`.
- **Service & AI:** `create()` via Scheduler façade; on fire publishes `ReminderFired`. NL time parse only.
- **Permissions:** `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK`. **External deps:** none.

---

### Module 3 — To-Do Lists (`:feature:todo`)
- **APIs/Libraries:** Room-only; drag-reorder via `animateItem()`.
- **Room:** `TaskListEntity`, `TaskEntity`(parentId self-ref nesting, dueEventId?, dueReminderId?), `TaskLinkEntity`.
- **Composables/Screens:** `TaskListsScreen`, `TaskListScreen` (nested, inline add, spring checkbox), `TaskDetailSheet`. FAB = add task.
- **Service & AI:** consumes `SuggestTodo` and **`OrganizeBrainDump` output `[src 16]`** (brain-dump → tasks); "break into subtasks" via AI.
- **Permissions:** none. **External deps:** none.

---

### Module 4 — Clock (`:feature:clock`)
- Compose `Canvas` faces (analog/binary/word), flip via `AnimatedContent`; Glance widgets; alarms reuse Module 2. `WorldClockEntity`, `ClockAlarmEntity`. Screens: `ClockScreen` (face switcher), `WorldClockScreen`, `StopwatchScreen`, `TimerScreen`. No AI/permissions/deps.

---

### Module 5 — ADHD Tools (`:feature:adhd`)
- Compose animation visual timers; `SYSTEM_ALERT_WINDOW` overwhelm overlay; haptic dopamine feedback; DataStore streaks. `FocusSessionEntity`, `StreakEntity`, `DopamineEventEntity`. Screens: `VisualTimerScreen`, `OverwhelmModeOverlay` (single "What's next?" — now fed by the **Planner**, §Module 24), `StreakDashboard`, `BodyDoubleTimer`. Permissions: `SYSTEM_ALERT_WINDOW`, `VIBRATE`, `POST_NOTIFICATIONS`.

---

### Module 6 — Finance Tracker (`:feature:finance`)  ⟵ EXTENDED
- **Positioning `[src 27]`:** a **privacy-first, on-device budget** for the post-Mint diaspora — no data sold, statements local. Includes a **Mint/Monarch/YNAB/CSV/Finanzguru importer** with column-map + dedupe.
- **APIs/Libraries:** CSV/Finanzguru + bank-CSV import; Gemma 4 categorization; Vico charts.
- **Room:** `AccountEntity`, `TransactionEntity`(…, categoryId, sourceEmailId?, source), `CategoryEntity`, `MerchantRuleEntity`, `BudgetEntity`, **`WarrantyEntity` `[src 25]`**, **`SubscriptionEntity` `[src 26]`**. DAOs incl. `WarrantyDao`, `SubscriptionDao`.
- **Composables/Screens:**
  - `FinanceOverviewScreen` (month summary, donut, trend, budget).
  - **`SwipeCategorizeScreen` `[src 24]`** — a Tinder-style deck of uncategorized transactions; Gemma 4 pre-suggests a category on each card, user swipes to confirm/redirect (left/right/up = business/personal/skip; long-press = pick). Turns categorization into seconds of play, not a chore.
  - **`SubscriptionsScreen` `[src 26]`** — detected recurring charges with cadence, annualized cost, "forgotten since" flag, and a **one-tap cancel** that opens the merchant cancel URL (from the email signal) or drafts a cancellation email.
  - **`WarrantyScreen` `[src 25]`** — items with purchase date, warranty window, linked receipt doc, and return/expiry countdown.
  - `ImportScreen` (Mint/CSV/Finanzguru), `BudgetsScreen`, `CategoryRulesScreen`.
- **Service & AI:** consumes **`ReceiptScanned` `[src 25]`** → creates a transaction + (if warranty months detected) a `WarrantyEntity` + files the receipt to the Vault/NAS; runs **subscription detection** over transaction history (recurring merchant+amount+cadence) → publishes `SubscriptionDetected` `[src 26]`; consumes invoice `FlagFinance`; publishes `FinanceEntryCreated`. Categorization batched on-device.
- **Permissions:** SAF picker / `READ_MEDIA_*`. **External deps:** none (all file/on-device).

---

### Module 7 — Message Center (`:feature:messagecenter`)
- `NotificationListenerService` unified inbox; `RemoteInput` reply. `UnifiedMessageEntity`, `MessageAppEntity`. Screens: `UnifiedInboxScreen` (search app bar), `ConversationThreadScreen`, `AppFilterScreen`. NLS → `NotificationPosted` (primary rule feeder). Permissions: `BIND_NOTIFICATION_LISTENER_SERVICE`, Notification Access, `QUERY_ALL_PACKAGES`, `POST_NOTIFICATIONS`.

---

### Module 8 — NAS Access (`:feature:nas`)  ⟵ EXTENDED
- **APIs/Libraries:** Synology DSM Web API (`SYNO.API.Info`→`Auth`→`FileStation.*`); Retrofit/OkHttp; QuickConnect/local IP.
- **Room:** `NasConnectionEntity`, `NasFileCacheEntity`, **`NasPackageEntity`(name, category, installed, version) `[src 23]`**.
- **Composables/Screens:** `NasBrowserScreen`, `NasConnectionSetupScreen`, `TransfersScreen`, **`ServerAppsScreen` `[src 23]`** — a friendly "app-store" GUI over Synology **Package Center** / Container Manager: browse curated packages (Proton Bridge, Ollama, Immich, the mail MCP), one-tap install/enable/configure with sane defaults, and health status. Goal: let a non-expert stand up the app's own NAS-side dependencies "as easily as on an iPad" without SSH.
- **Service & AI:** second file source for Files/Vault/Memex; hosts Bridge/MCP/Ollama/HA. Transfers = WorkManager. **v3:** `ServerAppsScreen` is also the guided installer used by the Setup flow (§8) to provision Bridge+MCP+Ollama.
- **Permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`. **External deps:** DSM creds/QuickConnect `TODO("insert key")`.

---

### Module 9 — Local AI (`:core:ai`; settings UI)
- LiteRT-LM/AICore on-device **Gemma 4 E2B/E4B** (multimodal) + NAS **Ollama 12B/26B A4B**; native function calling + thinking mode. `PromptTemplateEntity`, `AiModelConfigEntity`. `AiSettingsScreen` (model status/download/verify, endpoint test, routing/thinking toggles, template overrides). Exposes `complete`/`stream` + the v3 engines **PlannerEngine**, **MacroCompiler**, **NotesRag** (§5). Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`.

---

### Module 10 — Voice Input (`:feature:voice`)  ⟵ EXTENDED (★ brain-dump)
- **APIs/Libraries:** on-device `SpeechRecognizer` (`createOnDeviceSpeechRecognizer`, `EXTRA_PREFER_OFFLINE`); `VoiceInteractionService`+session for the Assistant role; Gemma 4 E4B on-device ASR as a fallback/long-form path (it accepts audio natively). Intent parse via `:core:ai` function-calling.
- **Room:** `VoiceCommandLogEntity`.
- **Composables/Screens:** mic in `AiInputBar`; assistant session overlay; `VoiceHistoryScreen`.
- **Service & AI — voice-first brain-dump `[src 16]`★:** the flagship low-friction capture path. The user taps once (widget/tile/FAB long-press/assistant) and talks a messy stream of thoughts with **no need to type and structure simultaneously**. Pipeline: `SpeechRecognizer` (short) or **Gemma 4 audio** (long-form) → raw transcript stored as a `CaptureEntity(kind=BrainDump)` → publish `CaptureCreated` → RulesEngine emits `OrganizeBrainDump` → Gemma 4 (function-calling) splits the stream into typed items: **tasks → To-Do, events → Calendar, facts/ideas → Notes, log values → Logger, reminders → Reminders** — each surfaced as **confirmable** cards in a single review sheet before anything is written. Everything runs on-device (privacy). Distinct commands still route immediately via Rule R3; brain-dump is the "just get it out of my head" mode.
- **Permissions:** `RECORD_AUDIO`, `BIND_VOICE_INTERACTION` + Digital-assistant role, `QUERY_ALL_PACKAGES`. **External deps:** none (on-device).

---

### Module 11 — Image Reasoning (`:feature:imagereasoning`)  ⟵ EXTENDED
- **APIs/Libraries:** CameraX; ML Kit Text Recognition v2 + Barcode for fast structured pulls; **Gemma 4 vision** (E4B on-device, or NAS 12B/26B) for reasoning; Coil.
- **Room:** `ScannedDocumentEntity`(kind:Receipt|Invoice|Label|Book|**Whiteboard**|Other, imageRef, ocrText, extracted JSON, linkedModule, linkedEntityId).
- **Composables/Screens:** `ScanScreen`, `ScanReviewScreen` (route to Finance/DHL/Books/Calendar), `ScanHistoryScreen`, **`BoardCaptureScreen` `[src 15]`**.
  - **Physical board → digital `[src 15]`:** point the camera at a whiteboard / wall of sticky notes; **on-device VLM (Gemma 4)** interprets handwriting, detects note boundaries/colors, and produces an **editable digital board** (columns/cards) synced to Notes/To-Do. Re-capture updates the same board (diff-merge), "tracking the tokens and syncing the state digitally" without any cloud round-trip.
  - **Receipt capture `[src 25]`:** receipt kind → OCR + Gemma 4 extracts merchant/date/total/line-items/warranty terms → publishes `ReceiptScanned` (feeds Finance + Warranty + Files/Vault).
- **Service & AI:** capture → OCR → Gemma 4 extract → publish `ImageAnalyzed`/`ReceiptScanned`/`TrackingNumberDetected`.
- **Permissions:** `CAMERA`; app-private/Vault storage. **External deps:** none.

---

### Module 12 — Agentic Smartphone Control (`:feature:agentic`)  ⟵ EXTENDED (★ NL macros)
- **APIs/Libraries:** `AccessibilityService` macro engine (node read, `ACTION_CLICK`, `dispatchGesture`, set text, global actions, launch intents); optional Device Admin (lock); Gemma 4 for authoring + on-screen reasoning.
- **Room:** `MacroEntity`(name, trigger JSON, enabled), `MacroStepEntity`(order, actionType, params JSON), `MacroRunLogEntity`, **`MacroDraftEntity`(nlPrompt, compiledIr JSON, status) `[src 41]`**.
- **Composables/Screens:** `MacroListScreen`, `MacroEditorScreen` (node/gesture capture, launcher, delay, conditional), `MacroRunLogScreen`, **`NlMacroScreen` `[src 41]`**, destructive-step confirmation gate.
- **Natural-language macro creation `[src 41]`★:** the user describes an automation in plain language ("every weekday at 8am, if it's raining, open Maps and start navigation to work"). Pipeline (**`MacroCompiler`** in `:core:ai`): NL + current-screen node context → Gemma 4 (function-calling, JSON-grammar) → a **validated intermediate representation** of typed steps (trigger, conditions, actions) → the app **maps IR to real accessibility/intent actions**, rejects unsupported steps, and presents a **dry-run preview** (step list + which app each targets). Nothing runs until the user confirms; destructive steps stay gated per-run. This removes the classic Tasker friction of hand-wiring every step. *Source flag: `[src 41]` is theme-level (widely requested; re-verify against a specific post) — treat the demand as strong but keep the feature opt-in and preview-gated.*
- **Service & AI:** executes steps; hands node summaries to `:core:ai`; triggered by `RunMacro`, and now by **Planner** suggestions and **Smart-Home** links.
- **Permissions:** `BIND_ACCESSIBILITY_SERVICE` + Accessibility, `BIND_DEVICE_ADMIN` (optional), `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`. **External deps:** none.

---

### Module 13 — Self-Evolving Intelligence Layer (`:feature:evolution`)
- Local interaction logging + prompt-template adaptation (contextual bandit); not weight training. `InteractionLogEntity`, `PromptVariantEntity`, `AdaptationEntity`. `EvolutionInsightsScreen`. Every AI call logs; nightly job scores + flips active template; `AiRouter` reads active variant. **v3:** also scores **Planner** suggestion acceptance and **brain-dump** organizing accuracy so those improve over time. No special permissions.

---

### Module 14 — File Organizer (`:feature:fileorganizer`)
- `MANAGE_EXTERNAL_STORAGE` local FS + NAS FileStation second source + SAF; rule/AI sorting. `FileIndexEntity`, `OrganizeRuleEntity`, `OrganizeRunLogEntity`. Screens: `FileBrowserScreen`, `OrganizeScreen` (preview + batch + undo), `RulesScreen`, `DuplicatesScreen`. **v3:** receives receipt/warranty docs `[src 25]` and can file sensitive docs to the **Vault** `[src 20]`. Permissions: `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_*`, `INTERNET`.

---

### Module 15 — DHL Package Notifier (`:feature:dhl`)
- DHL Unified Tracking API + regex + Gemma 4 extraction; WorkManager polling; **Live Updates** notification. `PackageEntity`, `TrackingEventEntity`. Screens: `PackagesScreen`, `PackageDetailScreen`, `AddPackageScreen`. Consumes `TrackingNumberDetected`→ package + (R1) reminder; polls; posts status + Live Updates. Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`. Key: DHL `TODO("insert key")`.

---

### Module 16 — Books (`:feature:books`)  ⟵ EXTENDED
- **APIs/Libraries:** ML Kit Barcode (ISBN); **Open Library API** (no key); **Goodreads/StoryGraph CSV import `[src 46]`**; Gemma 4 recommendations.
- **Room:** `BookEntity`, `ReadingStatusEntity`(status:Want|Reading|Read, rating incl. **half-stars `[src 46]`**, dates, notes, **privacyPerBook `[src 46]`**), `ReadingSessionEntity`(bookId FK, startedAt, minutes, pages) `[src 46]`, `RecommendationEntity`(bookId?, title, reason, source, at) `[src 45]`.
- **Composables/Screens:**
  - `LibraryScreen` (shelves by status, cover grid) — **Amazon-free, ad-free, self-owned `[src 46]`**; per-book privacy; half-star ratings; session tracking; import from Goodreads/StoryGraph.
  - `ScanBookScreen`, `BookDetailScreen`.
  - **`RecommendationsScreen` `[src 45]`** — a "what to read next" engine that reasons over **your own library, highlights, ratings, and reading history** (not opaque store recommendations). Gemma 4 explains each suggestion ("because you rated X 5★ and liked its themes") and can pull candidates from Open Library subjects. Fixes the community complaint that "Amazon recommendations [are] not browsable enough, Goodreads not assistive enough."
- **Service & AI:** barcode/OCR from Image can add books; recommendations on-device from local data.
- **Permissions:** `CAMERA`, `INTERNET`. **External deps:** Open Library (no key).

---

### Module 17 — Route Planner (`:feature:route`)
- Nav via intents (Google Maps `google.navigation:q=` / OsmAnd); optional Maps SDK preview; FusedLocation. `SavedPlaceEntity`, `RouteEntity`(eventId?, reminderId?). Screens: `RoutesScreen`, `RoutePreviewScreen`, `PlacePickerScreen`. Consumes `RouteRequested`/`ReminderFired`/`CalendarEventChanged` (location events → "leave by", R6); hands off to nav app. **v3:** a departure event can also fire a **Home Assistant** "leaving home" scene (R11, `[src 43]`). Permissions: `ACCESS_FINE/COARSE_LOCATION`, `INTERNET`. Key: Maps SDK `TODO("insert key")` (only if embedded preview).

---

### Module 18 — AI Chat Interface (`:feature:chat`)
- `:core:ai` completion/streaming + tool-calls; context-aware, action-capable across all modules (incl. calendar, notes, planner). `AiConversationEntity`, `AiMessageEntity`. Screens: `ChatScreen` (streaming, `ActionConfirmCard` tool-calls), `ConversationListScreen`. Assembles a context envelope from touched modules → `AiRouter` → confirmable `LifeAction`. **v3:** can query **Notes RAG** (`[src 29]`) and ask the **Planner** ("what should I do next?"). Permissions: `INTERNET`, `RECORD_AUDIO`.

---

### Module 19 — Calendar (`:feature:calendar`)
- Local-first **iCalendar (RFC 5545)** via ical4j; Android system Calendar Provider read/write; one-way ICS bridges to Proton (Full-view import, ICS publish); RRULE; alarms via Module 2. `CalendarEntity`, `CalendarEventEntity`(…, hasLocation, reminderId?), `EventAttendeeEntity`, `CalendarSubscriptionEntity`. Screens: month/week/day/agenda, `EventEditorScreen`, `EventDetailSheet`, `CalendarConnectionsScreen` (states Proton = view-only one-way). Publishes `CalendarEventChanged`; feeds R4 (briefing), R6 (leave-by), **R11 (home scene from event) `[src 43]`**; receives R7 (invite→event). AI: NL scheduling, conflict detection, free-slot finding. Permissions: `READ_CALENDAR`, `WRITE_CALENDAR`, `INTERNET`, `POST_NOTIFICATIONS`.

---
### Module 20 — Capture & Structured Logger (`:feature:capture`)  ⟵ NEW `[src 11, 16]`
- **What & why `[src 11]`:** the community's "personal data logger" — *"log anything with a little bit of structure… I would pay for this app."* User-defined forms (one field per line), **auto-detected field types** (number, date, location, rating, text, boolean, duration), instant logging, and **table/chart** views. This is the quantified-self spine: mood, caffeine, sleep, weight, symptoms, spend, anything.
- **APIs/Libraries:** Room; Vico charts; on-device Gemma 4 for "voice/NL → structured entry" and for inferring a form schema from an example sentence; fused location (optional) for auto location fields.
- **Room:** `LogFormEntity`(name, fieldsJson[{name,type,unit,default}], chartConfig, color), `LogEntryEntity`(formId FK, valuesJson, at, source:Manual|Voice|Auto|Rule), `CaptureEntity`(kind:BrainDump|Quick|Photo|Voice, text?, blobVaultRef?, routedTo, at).
- **Composables/Screens:**
  - **`QuickCaptureSheet`** (hosted in `:core:ui`, openable from anywhere — FAB long-press, `AiInputBar`, quick-settings tile, `QuickAddWidget`): one field, "just get it out"; on submit, Gemma 4 classifies destination (log/note/task/event/reminder) and shows a one-tap confirm. Honors the **60-day friction test** — capture costs ≤1 tap or one utterance.
  - `LogFormListScreen`, `LogFormEditorScreen` (type-per-line, live preview), `LogFormScreen` (fast entry: steppers/chips/sliders sized to the field type), `LogInsightsScreen` (tables + charts; correlations surfaced with the future correlation engine).
  - **Brain-dump review sheet `[src 16]`** (shared with Voice, Module 10): the confirmable split of a spoken stream into tasks/events/notes/logs/reminders.
- **Service & AI:** `QuickCaptureSheet`/brain-dump publish `CaptureCreated`; the RulesEngine routes (R8/R12). Voice long-form uses Gemma 4 audio on-device. All bodies with any sensitivity go through the **Vault** `[src 20]`.
- **Permissions:** `RECORD_AUDIO` (voice capture, shared), optional `ACCESS_COARSE_LOCATION` (location fields). **External deps:** none.

---

### Module 21 — Notes (Local-First PKM) (`:feature:notes`)  ⟵ NEW `[src 28, 29]`
- **What & why `[src 28]`:** plain-text **Markdown** notes stored as files, *"readable in any text editor, on any OS, forever — no vendor lock-in."* The freeform half of the capture spine and the destination for brain-dump ideas/facts.
- **On-device AI over notes `[src 29]`:** semantic search, summarize, "ask my notes," and auto-linking — **all local**, because privacy purists reject note apps whose AI *"must still sync to their cloud."* Implemented as **`NotesRag`** in `:core:ai`: on save, chunk → embed with an on-device embedding model → store `NoteEmbeddingEntity` vectors; queries retrieve top-k chunks and feed Gemma 4 for grounded answers/citations. No note text leaves the device.
- **APIs/Libraries:** Markdown files on disk (app dir + optional NAS mirror); compose-markdown rendering; on-device embeddings (LiteRT); ical/link parsing for backlinks.
- **Room:** `NoteEntity`(path, title, frontmatterJson, bodyVaultRef, updatedAt, embeddingId?), `NoteLinkEntity`(fromNoteId → toNoteId | targetModule/entityId), `NoteEmbeddingEntity`(noteId FK, vector BLOB). Bodies are stored via the **Vault** when marked sensitive; otherwise plain files for max portability.
- **Composables/Screens:** `NotesListScreen` (search app bar, backlinks), `NoteEditorScreen` (Markdown, wiki-links, slash-commands), `NoteGraphScreen` (backlink graph), `AskMyNotesScreen` (RAG Q&A with citations). FAB = new note.
- **Service & AI:** on save → publish `NoteSaved`, (re)embed in a WorkManager job; brain-dump facts land here; Chat/Assistant can query `NotesRag`; resurfacing job periodically re-presents old notes on Home (the "active resource" idea from the research).
- **Permissions:** storage via app dir / `MANAGE_EXTERNAL_STORAGE` if user picks an external vault; `INTERNET` only for NAS mirror. **External deps:** none.

---

### Module 22 — Personal Memex / Life Archive (`:feature:memex`)  ⟵ NEW `[src 12]`
- **What & why `[src 12]`:** *"a proxy that records everything you view… annotate without altering the source… auto-purge un-annotated content after a year… export shareable trails."* A durable, private, searchable archive of what you read/see, biased toward zero effort (passes the 60-day test).
- **Scope & honesty:** full-device "record everything" is battery/privacy-heavy, so v1 archives **opt-in streams** the app already sees — pages opened via the in-app reader, shared-in content, scanned docs, notification bodies (from Message Center), and manually "clip to archive." A system-wide capture (Accessibility screen text) is an **advanced opt-in** with clear on-screen indicators. This bounds risk while delivering the core value.
- **APIs/Libraries:** Room + Vault blobs (NAS-mirrored); on-device embeddings for search; Gemma 4 for summarizing/trailing; WorkManager purge job.
- **Room:** `ArchiveItemEntity`(source, kind, capturedAt, textVaultRef?, thumbRef?, annotated:Boolean, annotationVaultRef?, expiresAt), `TrailEntity`(name, createdAt), `TrailStepEntity`(trailId FK, archiveItemId FK, order, note).
- **Composables/Screens:** `ArchiveTimelineScreen` (searchable, filter by source/kind), `ArchiveItemScreen` (original + your annotation layer, never mutating the source), `TrailsScreen`/`TrailEditorScreen` (curate + **export a shareable trail** as Markdown/HTML). Retention UI: **un-annotated items auto-expire after a user-set window (default 12 months); annotated items persist.**
- **Service & AI:** ingests from reader/share/scan/notifications; purge job enforces retention; semantic search + "summarize what I read about X" via on-device RAG. All bodies in the **Vault**.
- **Permissions:** storage/`INTERNET` (NAS mirror); advanced mode reuses `BIND_ACCESSIBILITY_SERVICE` (Module 12) with explicit consent. **External deps:** none.

---

### Module 23 — Smart Home (Home Assistant) (`:feature:smarthome`)  ⟵ NEW `[src 43]`
- **What & why `[src 43]`:** trigger home automations from **calendar events and presence/zones** — natively supported by Home Assistant, whose docs confirm a *"Calendar trigger [that] fires when a Calendar event starts or ends… with an optional time offset"* and *zone triggers that fire when a person "enters or leaves the relevant zone."* LifeOS becomes the phone-side brain that drives them.
- **APIs/Libraries:** **Home Assistant REST + WebSocket API** (long-lived access token); the WebSocket lives in the foreground service so HA state changes arrive as `HomeStateChanged` events.
- **Room:** `HaConnectionEntity`(baseUrl, tokenRef, wsState), `HaEntityEntity`(entityId, friendlyName, domain, lastState), `HaSceneEntity`(sceneId, name), `HaAutomationLinkEntity`(trigger:CalendarEvent|Zone|Reminder|Manual, targetSceneId/serviceCall, offsetMin, enabled).
- **Composables/Screens:** `HaConnectionScreen` (base URL + token + test), `HaDashboardScreen` (entities/scenes with live state, tap to toggle/run), `HaLinksScreen` (build links: "when calendar event tagged 'gym' starts −30 min → run 'leave' scene"; "when I leave home zone → 'away' scene"). Dynamic-color, M3E cards.
- **Service & AI:** subscribes to HA state; `RulesEngine` fires `RunHomeScene` on calendar/zone/reminder triggers (R11); the Planner and Chat can call HA services ("set the office to focus mode"). Departure routine composes with object-permanence checks (from ADHD/agentic) and Route hand-off.
- **Permissions:** `INTERNET`, `ACCESS_FINE/COARSE_LOCATION` (zone presence). **External deps:** HA long-lived token `TODO("insert key")`; base URL configured.

---

### Module 24 — Agentic Day Planner "Jarvis" (`:feature:planner`)  ⟵ NEW `[src 40]` ★
- **What & why `[src 40]`:** the community's most-wanted AI use — *"I could really use a Jarvis… the whole damn promise of AI is to deliver that."* An assistant that decides **what deserves your attention now** by reasoning over the whole LifeOS graph, on-device.
- **The `PlannerEngine` (`:core:ai`) — reasoning loop:**
  1. **Gather** a compact snapshot: due/overdue reminders, today's calendar events (+travel/"leave by"), open high-priority tasks (incl. nested), flagged finance (due invoices, subscription renewals), unread priority mail, active packages arriving today, energy/time-of-day, and (when present) the latest mood/sleep log from the Logger.
  2. **Score & rank** each candidate `PlanItem` with a transparent heuristic (urgency × importance × time-fit × context), then let **Gemma 4 (thinking mode)** re-rank the top N and write a one-line rationale per item ("do this next because it's due in 40 min and you're near the location").
  3. **Emit** a ranked plan → `PlanSnapshotEntity` + `PlanItemEntity`s → publish `PlanRecomputed(topItem)`.
  4. **Act (with consent):** each item offers confirmable actions — start a focus timer, navigate, run a macro/home scene, snooze, delegate — dispatched as `LifeAction`s. **The planner proposes; the user disposes.** Nothing auto-executes without an explicitly enabled rule.
- **When it runs:** on a Planner tick (periodic WorkManager, and on wake/first-unlock), on significant `LifeEvent`s (new due reminder, calendar change, arrival), and on demand ("what's next?" from Chat/voice/overwhelm mode).
- **Routing:** on-device Gemma 4 E4B by default (private, fast); escalates to NAS 12B/26B for heavier multi-step reasoning when reachable and on power (per §5.2). Logs acceptance to the evolution layer so ranking personalizes.
- **Room:** `PlanItemEntity`(kind, refModule, refEntityId, score, reason, plannedFor, at), `PlanSnapshotEntity`(generatedAt, contextHash).
- **Composables/Screens:** **`PlannerScreen`** (the ranked "today" list with rationale + inline actions), a **Home top-card** ("Next: …") that also powers **ADHD overwhelm mode's** single "What's next?" card (§Module 5), and a voice/Chat entry point. M3E high-emphasis `WhatsNextCard`.
- **Service & AI:** the planner is the meta-consumer of the whole bus; it *reads* every module (via repository interfaces) and *writes* only plan items + user-confirmed actions.
- **Permissions:** none of its own (reuses others'). **External deps:** via `:core:ai`.

---

### Core — Encrypted Vault (`:core:vault`)  ⟵ NEW `[src 20]`
- **What & why `[src 20]`:** *"a place to keep sensitive information (notes, photos, recordings)"* that never leaks to a cloud, using asymmetric encryption so capture is frictionless (encrypt with a public key without unlocking) yet reads require unlock — and blobs are filesystem-backed for safe sync.
- **Design:** envelope encryption via **Google Tink** + **Android Keystore** (hardware-backed key on the S22 Ultra). Each blob: random data key → encrypts the body; data key wrapped by the Keystore key. **Write path needs no biometric** (public-key/keystore-wrap), so background captures (brain-dump, receipt, archive) are seamless; **read/decrypt requires user auth** (BiometricPrompt) and caches a session key briefly. Plaintext **metadata** (title, dates, tags) stays in Room for search/indexing; **bodies** are encrypted blobs (app-private, optionally mirrored to the NAS still encrypted).
- **Exposes:** `VaultRepository { suspend fun putBlob(bytes, meta): VaultRef; suspend fun openBlob(ref): ByteArray /*auth-gated*/; fun stream(...); syncToNas(ref) }`; `VaultBlobEntity`(ref, algo, keyAlias, sizeBytes, createdAt, nasSynced).
- **Consumers:** Notes bodies (when marked sensitive), Memex archive bodies/annotations, receipt/warranty/finance docs, voice recordings, any "private" capture. A small **`VaultScreen`** lists protected items and offers export/wipe.
- **Permissions:** `USE_BIOMETRIC`; storage for blobs. **External deps:** none.

---
# 3. Cross-Module Intelligence Rules

Each rule is a `CrossModuleRule` (`matches`/`produce`); actions carry `SourceRef`, are loop-guarded, and default to **confirmable suggestions** (per-rule auto-apply toggle). v2 rules R1–R7 retained; v3 adds R8–R12.

- **R1** DHL number (any source) → tracker + delivery reminder.
- **R2** Invoice email, unknown sender → flag Finance + suggest to-do.
- **R3** Voice command → intent parse (Gemma 4 function-calling) → confirmable action/route.
- **R4** Morning briefing → aggregate reminders + today's events + priority mail + finance alerts + day route; **now delegated to the Planner** (§Module 24) for ranking + rationale.
- **R5** ADHD overwhelm → single "What's next?" card = **Planner top item**.
- **R6** Location calendar event → "leave by" route reminder.
- **R7** Email/image invite (`.ics`/flyer) → suggested calendar event.
- **R8 — Receipt scanned → finance + warranty + file `[src 25]`.** Trigger `ReceiptScanned`. Produce a **Transaction** (via Finance) + if `warrantyMonths != null` a **`CreateWarranty`** (+ return/expiry reminder timed to the window) + **`FileToVault`/File Organizer** for the receipt image. Dedupe on merchant+total+date.
- **R9 — Subscription detected → cancel reminder + find confirmation `[src 26]`.** Trigger `SubscriptionDetected` (Finance recurring-charge detector). Produce `SuggestCancelSubscription` (surfaced in `SubscriptionsScreen`) + a renewal reminder; if a matching `hasSubscriptionSignal` email exists (Module 1), attach its **cancel URL**. Dedupe per merchant.
- **R10 — Brain-dump captured → organized items `[src 16]`★.** Trigger `CaptureCreated(kind=BrainDump)`. Produce `OrganizeBrainDump` → Gemma 4 splits the stream into tasks/events/notes/logs/reminders, each a **confirmable** card in one review sheet (writes to To-Do/Calendar/Notes/Logger/Reminders only on confirm). Fully on-device.
- **R11 — Calendar/presence → home scene `[src 43]`.** Trigger `CalendarEventChanged`(with a linked `HaAutomationLink`) or `HomeStateChanged`(zone enter/leave) or `ReminderFired`(tagged). Produce `RunHomeScene` at `startsAt ± offset` (HA calendar/zone semantics). Composes with the departure routine (object-permanence check + Route hand-off).
- **R12 — Quick capture → route `[src 11]`.** Trigger `CaptureCreated(kind=Quick|Voice|Photo)`. Gemma 4 classifies destination; produce the matching confirmable action (log entry / note / task / event / reminder). Distinct explicit commands bypass this via R3.

**Planner as meta-rule `[src 40]`★:** on relevant events and on a periodic tick, the `PlannerEngine` recomputes the ranked "what's next" and publishes `PlanRecomputed`; R4/R5 and the Home top-card consume it. It reads broadly, writes only plan items + user-confirmed actions.

Registration is deterministic; independent rules run concurrently; each rule is idempotent and dedupes on its target.

---

# 4. Permissions & Manifest Plan

## 4.1 Permission → module → strategy (v3 delta highlighted)
Just-in-time with rationale sheets; special access via onboarding deep-links. NAS-side components (Bridge/MCP/Ollama/HA server) add **no** app permission.

| Permission | Type | Module(s) | When / rationale |
|---|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | normal | Email, NAS, AI, DHL, Books, Route, Calendar, **Smart Home**, Notes/Memex (NAS mirror) | install-time |
| `FOREGROUND_SERVICE`(+`_DATA_SYNC`,`_SPECIAL_USE`) | normal/special | Core service (+ **HA WebSocket**, **Planner tick**) | install-time |
| `POST_NOTIFICATIONS` | runtime | Reminders, Messages, DHL, ADHD, Finance, Calendar, **Planner** | first launch |
| `USE_EXACT_ALARM` / `USE_FULL_SCREEN_INTENT` | special | Reminders, Clock, Calendar | manifest |
| `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK` | normal | Service, Reminders, ADHD, Clock | install-time |
| `RECORD_AUDIO` | runtime | Voice, Chat, **Capture (brain-dump)** | first mic use |
| `CAMERA` | runtime | Image (**receipts, board**), Books | first scan |
| `ACCESS_FINE/COARSE_LOCATION` | runtime | Route, location reminders/events, **Smart Home zones**, Logger location fields | first use |
| `READ_CALENDAR`, `WRITE_CALENDAR` | runtime | Calendar | connecting system calendars |
| `READ_MEDIA_*` | runtime | Files, Image, Finance import | first media access |
| `MANAGE_EXTERNAL_STORAGE` | special | File Organizer, **Notes/Memex external vault (optional)** | onboarding deep-link |
| `USE_BIOMETRIC` **(new)** | normal | **`:core:vault`** | first vault read |
| `QUERY_ALL_PACKAGES` | special | Messages, Voice, Agentic | manifest |
| `SYSTEM_ALERT_WINDOW` | special | ADHD overlay, Agentic | onboarding deep-link |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | special | Service | onboarding |
| Notification Access (`BIND_NOTIFICATION_LISTENER_SERVICE`) | special | Messages (**+ Memex ingest, opt-in**) | onboarding deep-link |
| Accessibility (`BIND_ACCESSIBILITY_SERVICE`) | special | Agentic (**+ Memex advanced capture, opt-in**) | onboarding deep-link (extra explanation) |
| Digital Assistant (`BIND_VOICE_INTERACTION`) | special | Voice | onboarding → Default digital assistant app |
| Device Admin (`BIND_DEVICE_ADMIN`) | special (optional) | Agentic (lock) | only if lock macros enabled |

## 4.2 Manifest service/receiver declarations
Unchanged from v2 (foreground service, NLS, Accessibility, VoiceInteraction + session, Device Admin receiver, Boot + Alarm receivers) with companion XML (`accessibility_config`, `interaction_service`, `device_admin`). **v3:** no new manifest services — the HA WebSocket and Planner tick run inside the existing foreground service; the Vault uses framework Keystore/Biometric APIs.

---

# 5. AI Routing Strategy (Gemma 4)

`AiRouter` is the single entry point; modules never pick an engine. Engines, policy, fallback, envelope, and token budgeting are unchanged from v2. **v3 adds three specialized capabilities layered on the router:**

## 5.1 Engines & policy (recap)
On-device **Gemma 4 E2B/E4B** (LiteRT-LM/AICore; multimodal; 128K) vs NAS **12B/26B A4B** (Ollama; 256K). Ordered policy: privacy tag → capability (vision/audio/long-context) → NAS reachability → task size → device state → default on-device. Fallback both ways then rule-based/cached; never crash a feature.

## 5.2 PlannerEngine `[src 40]`★
Reasoning loop in §Module 24. Routing: default on-device E4B (private, low-latency for the frequent ticks); escalate the *re-rank + rationale* step to NAS 12B/26B when reachable and on power for deeper multi-step reasoning (thinking mode on). Inputs are compact `{type,fields}` records from each module's repository (never raw rows); output is a ranked `PlanItem` list with per-item rationale. Acceptance/decline logged to the evolution layer to personalize scoring.

## 5.3 MacroCompiler `[src 41]`★
NL automation → validated steps. Pipeline: (NL prompt + optional current-screen node summary) → Gemma 4 **function-calling with a strict JSON grammar** → an **intermediate representation** (`{trigger, conditions[], actions[]}` over a closed schema of supported accessibility/intent/home actions) → **validator** rejects/repairs unsupported steps → **dry-run preview** rendered for the user → on confirm, IR compiles to `MacroEntity`+`MacroStepEntity`. Runs on-device by default; may use NAS for complex multi-branch macros. Destructive actions flagged and per-run gated.

## 5.4 NotesRag `[src 29]`
On-device retrieval-augmented answers over the user's notes/archive. On save/ingest: chunk → embed (on-device embedding model via LiteRT) → store vectors (`NoteEmbeddingEntity`, and archive equivalents). On query: embed query → top-k cosine retrieval → feed chunks + question to Gemma 4 → grounded answer **with citations to the source notes**. Nothing leaves the device; this is the privacy-preserving alternative to cloud-AI note apps. Also powers Memex "summarize what I read about X" and the Notes resurfacing job.

## 5.5 Context envelope (recap)
Uniform JSON envelope (`system`/`context{user_profile,module,module_context,recent_interactions}`/`query`/`response_format`/`thinking`/`tools`); serialized per engine (Ollama `messages`+`format:json`; Gemma 4 chat template + JSON grammar + native function-calling). Templates come from the evolution layer; each call is logged.

---
# 6. Implementation Roadmap (tiered)

Ordered by dependency, front-loading testability, and mapped to the research "ladder" (Foundational → Force-multiplier → Advanced → Aspirational). Each phase: **build / depends / exit**.

## Tier 0 — Foundational
**Phase 0 — Foundation.** Gradle multi-module skeleton + `build-logic` + version catalog; `:core:model/common/designsystem` (M3E theme + dynamic color), `:core:database` (empty DB), `:core:datastore`, **`:core:vault` scaffolding (Keystore/Tink/Biometric) `[src 20]`**; `:app` (`MainActivity`, Hilt, NavHost, short bottom bar, edge-to-edge); `LifeOsForegroundService` + `BootReceiver`. *Depends:* none. *Exit:* installs, nav works, service survives reboot, DB opens, vault can encrypt/decrypt a test blob, CI green.

**Phase 1 — AI layer + Chat.** `:core:network`; `:core:ai` `OllamaClient` (Gemma 4 NAS) + `AiRouter` + envelope + streaming + **MCP client**; `:feature:chat` + `AiInputBar`; then `GemmaEngine` (LiteRT-LM, E2B/E4B) + full routing/fallback. *Depends:* Phase 0, NAS Ollama (§8.3). *Exit:* streaming chat; graceful fallback; on-device answer offline.

**Phase 2 — Capture spine + Notes + on-device RAG `[src 11,16,28,29]`.** `QuickCaptureSheet` (`:core:ui`) + `:feature:capture` (logger forms/types/charts, quick/photo/text capture); `:feature:notes` (Markdown files, editor, backlinks) + **`NotesRag`** (on-device embeddings + ask-my-notes); brain-dump *text* pipeline (R10/R12) — *voice* brain-dump wired when Voice lands (Phase 9). Sensitive bodies → Vault. *Depends:* Phases 0–1. *Exit:* one-tap/one-utterance capture routes to log/note/task; notes render + persist as files; "ask my notes" answers locally with citations; the 60-day friction bar is met.

**Phase 3 — Time core.** `:core:notifications`; `:feature:reminders` (exact alarms, full-screen, boot reschedule, NL parse); `:feature:calendar` (ical4j, system provider, ICS refresh, month/week/day/agenda); `:feature:clock` (+ first Glance widget); `:feature:todo` (nested, cross-links; receives brain-dump tasks). *Depends:* Phases 0–2. *Exit:* reminders fire over lockscreen + survive reboot; events persist + sync to system provider; clock widget live.

## Tier 1 — Force multipliers
**Phase 4 — Event bus + Rules engine + Message Center.** Finalize bus/rules/executors; `:feature:messagecenter` (NLS → `NotificationPosted`); prove one rule end-to-end. *Depends:* Phase 3. *Exit:* event→rule→action→Room→UI with provenance + loop-guard.

**Phase 5 — DHL + R1.** `:feature:dhl` (regex + Gemma 4 extraction, WorkManager polling, Live Updates); Rule R1. *Exit:* shipping signal → package + delivery reminder, dedup/provenance; polling updates timeline.

**Phase 6 — Image Reasoning + receipts/board `[src 15,25]`.** `:feature:imagereasoning` (CameraX, ML Kit OCR/barcode, Gemma 4 vision); `ReceiptScanned` extraction; `BoardCaptureScreen` (board→digital → Notes/To-Do). *Depends:* Phases 1–2. *Exit:* receipt → structured fields; whiteboard → editable digital board synced to notes.

**Phase 7 — Finance `[src 24,25,26,27]`.** `:feature:finance` (accounts/tx/categories/budgets; **Mint/CSV/Finanzguru import**; Gemma 4 categorization; **SwipeCategorize**; **Warranty** + **Subscription** detection); Rules **R8** (receipt→finance+warranty+file) and **R9** (subscription→cancel+reminder). *Depends:* Phases 4, 6; Email signal (Phase 8) enriches R9's cancel URL. *Exit:* import + categorize + budgets; swipe-categorize works; receipts create tx + warranty; recurring charges detected with cancel flow.

**Phase 8 — Email (MCP) + R2/R7 + subscription signal `[src 26]`.** NAS Bridge + `protonmail-pro-mcp` behind HTTP/SSE (§8.5); `:feature:email` (MCP + IMAP fallback, AI sorting, invoice/invite/subscription signals); Rules R2, R7; feed R9 cancel URLs. *Exit:* inbox syncs + sorts; invoice→finance+to-do; invite→event; subscription emails enrich Finance.

## Tier 2 — Advanced
**Phase 9 — Voice + intent routing (R3) + Assistant role + voice brain-dump `[src 16]`★.** `:feature:voice` (on-device STT + Gemma 4 audio long-form; VoiceInteractionService/session; function-calling intent parse); wire `AiInputBar` mic and the **voice** brain-dump into the Phase-2 pipeline; Rule R3. *Exit:* long-press home opens assistant; spoken commands route/act with confirmation; spoken brain-dump organizes into tasks/events/notes/logs.

**Phase 10 — NAS + Files + one-tap self-hosting `[src 23]`.** `:feature:nas` (DSM auth, FileStation, cache, **ServerAppsScreen** installer); `:feature:fileorganizer` (all-files + NAS, AI tagging, organize+undo, receipt filing, Vault filing). *Exit:* unified browse; one-tap install/health for Bridge/MCP/Ollama; organize with undo.

**Phase 11 — Books + Route `[src 45,46]`.** `:feature:books` (Open Library, ISBN scan, **Goodreads/StoryGraph import, half-stars, per-book privacy, sessions, what-to-read-next**); `:feature:route` (places/routes, preview, nav hand-off; consumes location events; R6). *Exit:* scan-to-shelve + on-device recommendations with rationale; location event → leave-by + nav.

**Phase 12 — Agentic + NL macros `[src 41]`★ + Smart Home `[src 43]` + Memex `[src 12]`.** `:feature:agentic` (Accessibility macro engine, capture editor, run log, gating) + **`MacroCompiler`/NlMacroScreen**; `:feature:smarthome` (HA REST/WebSocket, dashboard, links) + Rule **R11**; `:feature:memex` (opt-in ingest, retention/purge, trails, RAG). *Depends:* Phases 3–4, 9. *Exit:* author macros by hand *and* by NL (preview-gated); calendar/zone → home scenes; archive with annotate/auto-purge/export trails.

## Tier 3 — Aspirational
**Phase 13 — Planner "Jarvis" `[src 40]`★ + Evolution + ADHD + briefing + widgets.** `:feature:planner` (`PlannerEngine`, ranked "today", Home top-card, overwhelm feed); `:feature:evolution` (scoring incl. planner acceptance + brain-dump accuracy); `:feature:adhd` (visual timers, overwhelm overlay driven by Planner, streaks) + R5; Rule R4 (briefing via Planner); remaining Glance widgets. *Depends:* rich data graph from all prior tiers. *Exit:* planner composes real cross-module data + on-device rationale; overwhelm shows one next step; templates + ranking measurably personalize.

**Phase 14 — Polish & hardening.** SharedTransition list→detail everywhere, M3E `MotionScheme`/shape-morph tuning, adaptive unfolded/landscape, R8/ProGuard (§9.4), battery/thermal (esp. on-device vision/RAG/embeddings), memory management for concurrent Gemma 4, empty/error states, accessibility pass, full onboarding checklist. *Exit:* cohesive motion, no jank, graceful degradation, clean minified sideload build.

**Invariant:** never build a rule before its action target (stub if needed); never an AI feature before `:core:ai`; never a repository before its entities; never surface a sensitive body outside the Vault.

---

# 7. UI/UX Design System — Material 3 Expressive ("feels like a Google app on a Pixel")

## 7.0 Intent & rules
Indistinguishable in feel from a first-party Google app (Gmail/Calendar/Keep/Files) on a Pixel: **authentic M3 Expressive**, deferring to wallpaper dynamic color and stock M3E components, spending expressiveness on emphasis/shape/spring motion. UX rules: start from the user's task; expressive emphasis builds hierarchy; motion guides attention (respect reduced-motion); accessibility floor (WCAG contrast, ≥48dp targets, TalkBack, dynamic type); Google-app copy voice (sentence case, plain verbs, consistent action words).

## 7.1 Tokens
- **Color — dynamic first:** `dynamicDarkColorScheme(context)` from wallpaper, dark-mode-first, M3E richer tonal palette (clear primary/secondary/tertiary); restrained neutral fallback; semantic-only extras (`success`/`warning`/`info`, package/finance status).
- **Typography — Roboto Flex (variable):** M3E scale, variable weight/optical-size for emphasis; **tabular figures** for amounts, clock digits, timers, log values, calendar times.
- **Shape — expanded M3E scale + morphing** via `androidx.graphics:graphics-shapes` (shape-morph FAB/selection, expressive loaders).
- Exposed via `LifeOsTheme { }` (expressive `androidx.compose.material3`) + M3E `MotionScheme` + `CompositionLocal` semantic tokens.

## 7.2 Shared components (thin wrappers over stock M3E)
`LifeSearchAppBar` (pill + external avatar/menu — Inbox/NAS/Books/Notes/Calendar), short `LifeBottomBar`/`LifeNavRail`, shape-morphing `LifeFab`, `LifeFloatingToolbar` (contextual bulk actions), `LifeCard` (tonal `surfaceContainer` + provenance chip), `WhatsNextCard` (high-emphasis — Planner/overwhelm), `ActionConfirmCard`, `ProvenanceChip`, `SectionHeader`, `EmptyState`, `ErrorState`, `LoadingIndicator`, filter `Chip` rows; domain visuals `StreakRing`, `VisualTimerRing`, `DonutChart`, `TrendSparkline`, `CalendarMonthGrid`, `AgendaList`, `SwipeableRow`, `BreadcrumbBar`.

## 7.3 Motion (M3E spring system)
`MotionScheme.expressive()`; list→detail via `SharedTransitionLayout`+`AnimatedContent`; swipe/dismiss spring detach + haptic with neighbor reaction; press shape-morph; `animateItem()` for list mutations; `AnimatedContent`/`Crossfade` for tab/view/face swaps; reduced-motion disables decoration.

## 7.4 Widgets (Glance) & Live Updates
`ClockWidget`, `NextReminderWidget`, `QuickAddWidget` (**text/voice quick-capture → `QuickCaptureSheet`**), `TodayWidget` (next events). **Live Updates** for DHL delivery + route navigation progress.

## 7.5 Persistent AI input bar
Slim fully-rounded surface (text + mic + send) above the bottom bar; opens/append the active chat; mic runs on-device STT and routes via R3; states idle/listening/thinking/action-proposed; IME-aware collapse. **Doubles as a global brain-dump entry `[src 16]`.**

## 7.6 Home card system
At-a-glance ranked feed from `DashboardCardProvider` + app-grid (Capture/Notes/Memex/Planner/Smart Home live here); **top card = Planner "Next: …" `[src 40]`**; overwhelm mode collapses to one `WhatsNextCard`.

## 7.7 Copy voice
Name things by what the user controls; active-voice controls; consistent action words; errors state what/how-to-fix in-voice; empty states invite action; sentence case.

## 7.8 New v3 surfaces (M3E patterns)
- **`QuickCaptureSheet` `[src 11,16]`** — a bottom sheet with one focused field + mic + type-ahead destination chips (log/note/task/event); expressive spring in; ≤1 tap. The **brain-dump review sheet** shows AI-split items as a checklist of `ActionConfirmCard`s.
- **`SwipeCategorizeScreen` `[src 24]`** — a card deck (like Google Photos' review flow), Gemma 4's suggested category shown as a chip, spring/shape-morph on swipe, haptic confirm; undo snackbar.
- **`NoteEditorScreen` / `AskMyNotesScreen` `[src 28,29]`** — Keep-like Markdown editor (dynamic-color note surfaces); RAG answers render with tappable citation chips.
- **`PlannerScreen` + Home top-card `[src 40]`** — high-emphasis `WhatsNextCard` with per-item rationale line and inline actions (start timer / navigate / run scene / snooze).
- **`ArchiveTimelineScreen` / `TrailEditorScreen` `[src 12]`** — searchable timeline; annotation layer visually distinct from source; trails as reorderable cards; export button.
- **`HaDashboardScreen` / `HaLinksScreen` `[src 43]`** — live entity/scene cards (state-reactive color), link builder as a simple "when → then" form.
- **`ServerAppsScreen` `[src 23]`** — an app-store-style grid of NAS packages with one-tap install + health chips.
- **`WarrantyScreen` / `SubscriptionsScreen` `[src 25,26]`** — countdown chips (return/expiry), annualized-cost emphasis, one-tap cancel.
- **`VaultScreen` `[src 20]`** — biometric-gated list; lock state obvious; export/wipe.

---
# 8. Setup & Integration Guide

DS923+ is **x86-64, CPU-only**; run Proton Bridge/hydroxide, the mail MCP, Ollama, **and Home Assistant** in Container Manager; expand RAM before Gemma 4 12B/26B.

## 8.1 Synology DSM connection
`SYNO.API.Info`→`SYNO.API.Auth` (`passwd=TODO("insert key")`, `otp_code` if 2FA) → `sid` (encrypted in `NasConnectionEntity.sidRef`) → `SYNO.FileStation.*`. Prefer local IP/DDNS + HTTPS (or Tailscale/WireGuard); QuickConnect ID `TODO("insert key")` via relay. Pin self-signed cert per connection.

## 8.2 On-device Gemma 4 (LiteRT-LM / AICore)
Ship **E2B** (mobile QAT ~1 GB) or **E4B** (~5 GB, full audio/vision); `.litertlm`/`.task` at `getExternalFilesDir("models")`. First-run download or `adb push`. Load via LiteRT-LM/AICore; `GemmaEngine` lazy-loads, configures thinking, releases on `onTrimMemory`. Self-test in `AiSettingsScreen`.

## 8.3 Ollama on the NAS
Container Manager → `ollama/ollama`, port `11434` LAN-only; `ollama pull gemma4:12b` (or 26B A4B). Set base URL + model in settings; 30 s health TTL drives routing.

## 8.4 Sideloading on the S22 Ultra
`adb install -r -g app-release.apk`; push model. Or direct APK + *Install unknown apps*, then the **onboarding checklist**: Notification access, Accessibility, Display over other apps, All files access, battery-opt exemption, exact alarm, calendar, **biometric enroll (Vault)**, **Digital assistant app → LifeOS**.

## 8.5 protonmail-pro MCP on the NAS (email)
Proton Bridge/hydroxide (Docker) → **Bridge app password** (`TODO("insert key")`). Deploy `protonmail-pro-mcp`: **review source, pin/fork a reviewed commit**, `npm i && npm run build`; point IMAP/SMTP at Bridge; set the **Bridge app password** (never the account password); secrets in NAS env. The published MCP is **stdio** → run it behind an **MCP-over-HTTP/SSE** wrapper, LAN/VPN-only; `:core:ai` MCP client connects there. **Fallback:** direct Jakarta-Mail IMAP/SMTP to the same Bridge. Treat "audited/zero-issues" claims as unverified.

## 8.6 Proton Calendar (ICS, one-way — no two-way sync)
Import: Proton web → share **Full-view** link (URL holds the decryption key) → add as a subscription in `CalendarConnectionsScreen` (fetch/parse via ical4j; refresh cadence). Export: publish LifeOS's ICS URL → add in Proton via *Add calendar from URL*. Full read/write is against the **system Calendar Provider** + local calendar. State the one-way limit in-product.

## 8.7 Home Assistant `[src 43]`
In HA: create a **long-lived access token** (Profile → Security). In `HaConnectionScreen`: base URL (`http://<ha-lan-ip>:8123` or HTTPS/Nabu Casa), paste token (`TODO("insert key")`, stored via `:core:vault`), "test." `:core:network` opens an authenticated **WebSocket** (state subscription) in the foreground service + REST for service calls. Build automation links in `HaLinksScreen` (calendar/zone/reminder → scene). LAN/VPN-only. **One-tap install `[src 23]`:** the NAS `ServerAppsScreen` can provision HA (Container) alongside Bridge/MCP/Ollama.

## 8.8 Vault `[src 20]`
On first run, `:core:vault` generates a hardware-backed **Android Keystore** key (StrongBox where available). Bodies are Tink-envelope-encrypted; **writes need no auth**, **reads require BiometricPrompt**. Optional NAS mirror stores blobs **still encrypted** (keys never leave the phone). `VaultScreen` offers export (decrypted, auth-gated) and secure wipe. Losing the device key = unrecoverable ciphertext by design; offer an explicit user-controlled key-backup/export flow if they want recoverability.

---

# 9. Dependency & Build Configuration Plan

## 9.1 Structure
Root `settings.gradle.kts` + `build-logic` composite; convention plugins; `gradle/libs.versions.toml` pins versions (baseline below — pin latest stable).

## 9.2 Dependencies (v3 additions in **bold**)
Baseline: Kotlin `2.1.x`, AGP `8.7+`, Compose BOM `2026.x` (expressive `material3`), Hilt `2.5x`, Room `2.7.x`, WorkManager `2.10.x`, CameraX `1.4.x`, Glance `1.1.x`, ical4j `4.x`.

| Library | Purpose |
|---|---|
| compose-bom (+`ui`,`material3`,`material3-adaptive`,`material-icons-extended`,`ui-tooling`) | Compose + M3 Expressive + adaptive |
| `androidx.compose.animation` (+SharedTransition), **`androidx.graphics:graphics-shapes`** | motion + M3E shape morphing |
| `androidx.navigation:navigation-compose` | type-safe nav + deep links |
| `androidx.lifecycle:*-compose` | ViewModel + lifecycle state |
| `com.google.dagger:hilt-android`(+compiler), `androidx.hilt:hilt-navigation-compose`, `hilt-work` | DI |
| `androidx.room:runtime/ktx/compiler` | persistence |
| `androidx.datastore:datastore-preferences` | settings/config/refs |
| `androidx.work:work-runtime-ktx` | DHL/email/ICS/index/embeddings/**planner tick**/transfers |
| `okhttp`, `logging-interceptor`; `retrofit`, `converter-kotlinx-serialization` | DSM/DHL/Ollama/ICS + **HA REST**; **OkHttp WebSocket for HA** `[src 43]` |
| `kotlinx-serialization-json`, `kotlinx-coroutines-android` | envelope/DTOs/JSON columns; concurrency |
| `io.coil-kt:coil-compose` | images (authed URLs, covers, scans) |
| **LiteRT-LM / Google AI Edge (AICore)** | **Gemma 4 E2B/E4B on-device** |
| **`com.google.mediapipe:tasks-text` (or LiteRT text embedder)** | **on-device embeddings for NotesRag/Memex** `[src 29]` |
| *(optional)* `onnxruntime-android` | alt on-device Gemma 4 path |
| `io.modelcontextprotocol:kotlin-sdk` | MCP client → NAS mail MCP |
| `com.google.mlkit:text-recognition`, `barcode-scanning` | OCR + ISBN/tracking/**receipt/board** `[src 15,25]` |
| `androidx.camera:core/camera2/lifecycle/view` | CameraX |
| `androidx.glance:glance-appwidget` | widgets + quick-capture widget |
| `com.google.android.gms:play-services-location` | FusedLocation (route/events/**HA zones**/log fields) |
| *(optional)* `maps-compose` + Maps SDK | route preview (key `TODO("insert key")`) |
| `org.eclipse.angus:angus-mail` | IMAP/SMTP fallback to Bridge |
| `org.mnode.ical4j:ical4j` | iCalendar (RFC 5545) for Calendar |
| Android `CalendarContract` (framework) | system-calendar interop |
| **`com.google.crypto.tink:tink-android`** | **Vault envelope encryption** `[src 20]` |
| **`androidx.biometric:biometric`** | **Vault read auth** `[src 20]` |
| **compose Markdown renderer (e.g. `com.mikepenz:multiplatform-markdown-renderer-m3`)** | **Notes rendering** `[src 28]` |
| `com.patrykandpatrick.vico:compose-m3` (or Canvas) | finance/logger/ADHD charts `[src 11]` |
| `androidx.security:security-crypto` | encrypt small creds/refs (sid/tokens) |
| Testing: `junit`, `coroutines-test`, `room-testing`, `turbine`, `mockk`, `ui-test-junit4`, `hilt-android-testing` | unit/DAO/Flow/Compose tests |

*(No cloud AI SDKs; Bridge/MCP/Ollama/HA are NAS-side, not app deps. All new AI features — RAG, planner, macro compile, brain-dump — run on-device or on the user's own NAS.)*

## 9.3 Key/secret convention
`TODO("insert key")` at call sites; runtime values in encrypted DataStore / **Vault** / `BuildConfig`, or NAS-side for servers:
- DHL API key, Maps SDK key (optional), DSM password, QuickConnect ID — app-side.
- **Proton Bridge app password** — NAS-side (Bridge + MCP), never on the phone.
- **HA long-lived token** — app-side, stored in the Vault `[src 43]`.
- Ollama URL, on-device model path, MCP endpoint, Proton ICS URL, HA base URL, book-import files, on-device STT/embeddings/Gemma 4/ML Kit/`CalendarContract`/Open Library — configured or keyless.

## 9.4 ProGuard/R8 (sideloaded release)
`isMinifyEnabled` + `isShrinkResources` with surgical keeps: Room/Hilt/kotlinx.serialization/Retrofit/OkHttp (documented rules + `@Serializable` keeps); **Jakarta Mail** provider `META-INF` service files; **ical4j** service-loader classes; **LiteRT-LM/ML Kit/ONNX/MediaPipe text-embedder** native bridges (+ keep `jniLibs`, exclude models from shrinking); **Tink** (keep crypto registry/`KeyTypeManager`s) `[src 20]`; **MCP SDK** protocol types; Glance/Compose manifest-referenced receivers. Dedicated release keystore, stable across installs (so updates don't wipe the DB/Vault). Test the minified build per phase.

---

## Appendix A — Definition of done (per module)
Room entities + DAOs with migrations; repository behind an interface publishing the right `LifeEvent`s; ViewModel with one `UiState` + `onEvent`; screens on the M3E design system (dynamic color, Roboto Flex, spring motion, adaptive, search-app-bar/FAB/floating-toolbar where apt); permissions with rationale; AI via `AiRouter` with the module's template + interaction logging; cross-module rules wired with provenance/undo; **sensitive bodies stored only via `:core:vault`**; a minified `release` build runs it on the S22 Ultra.

## Appendix B — Global non-functionals
Offline-first; graceful AI/NAS degradation; all destructive/auto actions undoable + provenance-tagged; reduced-motion + accessibility respected; **secrets/sensitive bodies encrypted at rest (phone) and still-encrypted if mirrored to NAS**; coordination service survives reboot + battery-opt; nothing acts without an enabled rule or explicit confirmation (the **Planner proposes, the user disposes** `[src 40]`); Proton mail/calendar limits surfaced honestly in-product; the capture spine meets the **60-day friction test** (capture ≤1 tap / one utterance) `[src 11,16]`.

## Appendix C — Community sources (why each feature exists, and how it acts here)
*These are the demand signals behind the v3 features; the coding agent should read them to understand intent and interactions. Some are single organic posts; a few are aggregators/vendor pages that accurately describe a recurring demand (flagged). Idea numbers match the research report.*

- **`[src 11]` Structured logger** — Hacker News, "Ask HN: What small software do you wish existed?": a personal data-logging app where you *"log anything with a little bit of structure… I would pay for this app"* (custom form fields, auto-typed, tables/charts). → **Module 20 (Capture & Logger).** Acts as the quantified-self spine feeding the future correlation engine, mood/sleep, and the Planner. https://news.ycombinator.com/item?id=32937902
- **`[src 12]` Personal Memex** — Hacker News, "Ask HN: What tool/product do you wish existed?": *"a proxy that records everything you view… annotate without altering the source… auto-purge un-annotated content… export shareable trails."* → **Module 22 (Memex).** Bounded to opt-in streams; bodies in the Vault; NAS-mirrored; feeds NotesRag. https://news.ycombinator.com/item?id=37203948
- **`[src 15]` Physical board → digital** — Hacker News, "Ask HN: What developer tool do you wish existed in 2026?": *"just a camera with on-device VLMs and LLMs… interpret the handwriting, track the tokens, and sync the state digitally."* → **Module 11 (`BoardCaptureScreen`).** Uses Gemma 4 vision; outputs to Notes/To-Do. https://news.ycombinator.com/item?id=46345827
- **`[src 16]`★ Voice-first brain-dump** — ADHD-productivity community/aggregator: voice capture removes *"the barrier of typing and structuring thoughts at the same time."* → **Modules 10 + 20 (voice brain-dump → organized items, R10).** On-device Gemma 4 audio + splitting. *(Aggregator source; demand well-attested across ADHD communities.)* https://recallify.ai/adhd-apps-productivity-tools/
- **`[src 20]` Encrypted sensitive-data vault** — beepb00p public idea list: a place for *"sensitive information (notes, photos, recordings)"* using asymmetric encryption so capture is frictionless but reads are protected, filesystem-backed for sync. → **`:core:vault`.** Underpins Notes/Memex/Finance docs. https://beepb00p.xyz/ideas.html
- **`[src 23]` One-tap self-hosting** — Hacker News, "Ask HN: What open source projects do you wish existed?": configure self-hosted services *"as easily as on an iPad… go to the App Store, download… within minutes."* → **Module 8 (`ServerAppsScreen`).** Provisions Bridge/MCP/Ollama/HA. https://news.ycombinator.com/item?id=9828461
- **`[src 24]` Swipe-to-categorize** — Hacker News, "Ask HN: What simple web apps do you wish existed?": *"sorting through all my credit card transactions! Swipe left for business and right for personal."* → **Module 6 (`SwipeCategorizeScreen`).** Gemma 4 pre-suggests; user swipes. https://news.ycombinator.com/item?id=35029337
- **`[src 25]` Receipt → finance + warranty** — Hacker News (same thread): a warranty-claims tracker cited as a real want; receipts as the capture source. → **Modules 11 + 6 (R8).** One scan → transaction + warranty + filed doc. https://news.ycombinator.com/item?id=35029337
- **`[src 26]` Subscription detection** — post-Mint personal-finance coverage (Rocket Money et al.): detecting/cancelling forgotten recurring charges is the core draw for Mint refugees. → **Module 6 + Email signal (R9).** *(Secondary/finance-community source.)* Spendify Mint-shutdown explainer.
- **`[src 27]` Privacy-first local budget** — Intuit shut down Mint (Mar 23, 2024; ~25M users), migrating "only your account login" to Credit Karma; users scattered to Monarch/YNAB/Copilot/Rocket Money. → **Module 6 positioning + Mint importer.** *(Secondary source.)* Spendify Mint-shutdown explainer.
- **`[src 28]` Local-first plaintext notes** — PKM/Obsidian community aggregator: Markdown files *"readable in any text editor, on any operating system, forever. There is no vendor lock-in."* → **Module 21 (Notes).** https://yaabot.com/41747/the-best-life-organization-apps/
- **`[src 29]` On-device AI over notes** — same aggregator: privacy purists reject cloud-AI notes because data *"must still sync to their cloud for AI processing"*; they want AI that *"run[s] locally."* → **Module 21 (`NotesRag`).** https://yaabot.com/41747/the-best-life-organization-apps/
- **`[src 40]`★ Agentic day planner (Jarvis)** — Hacker News, "Ask HN: What developer tool do you wish existed in 2026?": *"I could really use a Jarvis at work right now and it seems like the whole damn promise of AI is to deliver that. I'm waiting."* → **Module 24 (`PlannerEngine`).** Reads the whole graph, ranks "what's next," proposes actions on-device. https://news.ycombinator.com/item?id=46345827
- **`[src 41]`★ Natural-language macro creation** — Tasker/automation community (theme-level): repeated demand to describe an automation in words rather than hand-wire steps. → **Module 12 (`MacroCompiler`/`NlMacroScreen`).** NL → validated IR → dry-run → confirm. *(Theme-level; re-verify against a specific post; keep opt-in + preview-gated.)*
- **`[src 43]` Calendar/presence → home automations** — Home Assistant official docs: a *"Calendar trigger [that] fires when a Calendar event starts or ends… optional time offset"* and zone triggers that fire when a person *"enters or leaves the relevant zone."* → **Module 23 (Smart Home) + R11.** https://www.home-assistant.io/docs/automation/trigger/
- **`[src 45]` What-to-read-next** — Hacker News, "Ask HN: What software/app do you wish would exist?": *"Amazon recommendations not browsable enough. Goodreads not assistive enough."* → **Module 16 (`RecommendationsScreen`).** On-device recs from your own library + rationale. https://news.ycombinator.com/item?id=21402234
- **`[src 46]` Privacy book tracker** — book-tracking community aggregator: an ad-free, Amazon-free Goodreads alternative with Goodreads/StoryGraph import, per-book privacy, half-star ratings, session tracking. → **Module 16 (Books).** https://isbndb.com/blog/book-tracking-apps-and-websites/

*Full demand context, quotes, and the 56-idea catalog live in the separate research report + its `sources.md`.*
