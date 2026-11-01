# Architecture

Tick ToDo is a single-module Android app (`app/`) written in Kotlin with Jetpack
Compose. It is local-first: every read and write goes to the on-device Room
database, and a background sync engine reconciles that database with Cloud
Firestore when the user is signed in and online.

```
UI (Compose screens + ViewModels)
        │  StateFlow<UiState>
        ▼
TaskRepository / SettingsRepository / FocusManager
        │  Room Flows (single source of truth)
        ▼
AppDatabase (Room)  ◄──►  SyncEngine  ◄──►  FirestoreRemoteStore
        │
        └── TaskChangeEffects ──► ReminderScheduler (AlarmManager)
                                ► WidgetUpdater (Glance)
                                ► SyncScheduler (WorkManager)
```

## Packages

| Package | Responsibility |
|---|---|
| `domain` | Pure Kotlin: models (`Task`, `TaskList`, `Recurrence`, `FocusPlan`), validation (`TaskValidator`), recurrence maths, conflict rule, focus-timer state machine. No Android imports; fully unit tested. |
| `data/local` | Room entities, DAOs, `AppDatabase` (schema exported to `app/schemas`, additive auto-migrations). Every row carries `ownerUid` and every query filters on it. |
| `data/repository` | `TaskRepository`: the only writer of task/list rows. Validates input, stamps `updatedAt`/`localRevision`/`dirty`, runs multi-row changes in transactions, then fires `TaskChangeEffects`. |
| `data/remote` | `RemoteStore` interface and its Firestore implementation; `RemoteMapper` converts documents both ways and never trusts remote data (clamps, truncates, drops malformed docs). |
| `data/auth` | Firebase Auth wrapper; `UserSession.uid` is the flow every per-user query hangs off. |
| `data/settings` | DataStore-backed preferences (theme, accent, appearance, sort, default focus plan). |
| `sync` | `SyncEngine` (pull by server cursor, push dirty rows with check-and-set), `SyncWorker` (WorkManager, network-constrained, exponential backoff), `SyncStatusRepository` (what the UI shows). See [sync-design.md](sync-design.md). |
| `reminders` | `ReminderMath` (pure timing rules), `ReminderScheduler` (one alarm-clock alarm per task), `ReminderHandler` (what happens when it fires), broadcast receivers for alarm/boot/time changes. |
| `focus` | `FocusManager` persists the running session (DataStore), arms a phase-end alarm, posts the ongoing notification. |
| `widget` | Glance app widgets that collect the same Room flows as the screens. |
| `ui` | One package per screen plus `common` (shared components) and `theme` (colour derivation from the accent, typography, shapes). |

## State management

* Each screen has one `@HiltViewModel` exposing a single `StateFlow<UiState>`
  built with `combine` over repository flows and `stateIn(WhileSubscribed)`.
  Screens collect with `collectAsStateWithLifecycle`.
* There are no cached copies of task data in the UI layer. Because every
  screen observes Room, completing a task on Today updates Upcoming, Matrix,
  Browse counts, widgets and the editor without any manual propagation.
* Transient UI state (selection, filters, drag position, dialogs) lives in the
  ViewModel or in `rememberSaveable`, so rotation and process recreation keep it.
* Writes are `suspend` functions on the repository called from
  `viewModelScope`; results that need user feedback go through `UiMessenger`
  (a shared snackbar queue that survives navigation, used for Undo).
* Long-running, app-wide work (sync, reminders, focus) runs in
  `@ApplicationScope` (a `SupervisorJob` + `Dispatchers.Default`), never in a
  ViewModel scope.

## Data model

Tasks and lists are documents keyed by UUID. A task has an optional
`parentId` (subtasks are tasks), optional `listId`, priority 0–3, floating due
date (`dueEpochDay`) and optional time (`dueMinuteOfDay`), reminder offset,
recurrence rule, tags, focus plan and manual sort position. Deletion is a
tombstone (`deleted = 1`) so it can sync; tombstones older than a threshold are
purged server-side by the sync engine.

Local-only bookkeeping columns (`dirty`, `localRevision`, `serverUpdatedAt`,
`snoozedUntil`, `lastNotifiedTrigger`) are never uploaded.

## Dependency injection

Hilt. `AppModule` binds the database, clock, Firebase provider, remote store,
settings and the `TaskChangeEffects` implementation. Interfaces (`RemoteStore`,
`Clock`, `TaskChangeEffects`, `AdsGateway`) exist so unit tests can substitute
fakes (`app/src/test/.../testing/Fakes.kt`).

## Build variants

* `debug`: App Check debug provider, optional Firebase emulators
  (`-Pticktodo.useEmulator=true`), cleartext allowed to the emulator host only.
* `release`: R8 + resource shrinking, Play Integrity App Check provider, no
  cleartext traffic, debug logs stripped. Signing comes from a git-ignored
  `keystore.properties` or `TICKTODO_KEYSTORE_*` environment variables; without
  them the release build is produced unsigned.
