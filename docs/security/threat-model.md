# Threat model

Scope: the Tick ToDo Android app, its Firestore backend, and the data in
transit between them. Out of scope: Google's own infrastructure, the user's
device OS, and the Play Store distribution channel.

## Assets

| Asset | Where it lives | Sensitivity |
|---|---|---|
| Task content (titles, notes, tags, dates, lists) | Room DB on device; Firestore under `users/{uid}` | Personal; may include health, work or financial detail |
| Account identity (Firebase uid, email, display name, photo URL) | Firebase Auth; shown in Settings | Personal |
| Auth tokens / refresh tokens | Firebase Auth SDK storage (app-private) | Secret |
| Focus statistics (minutes per task) | Room, device only | Personal, low |
| Preferences (theme, accent, imported wallpaper photo) | DataStore + app files | Low |
| Reminder notifications | Notification shade / lock screen | Personal (titles) |

## Trust boundaries

1. **App ↔ Firestore**: TLS, Firebase Auth ID token, App Check token.
2. **App ↔ other apps on the device**: exported components, notifications,
   widgets, share/intents.
3. **App ↔ device storage**: Room, DataStore, app-private files, backups.
4. **Developer ↔ repository/CI**: secrets, signing keys.

## Threats and mitigations

### T1. Cross-user data access on the backend
*Attacker*: any signed-in user with a modified client.
*Mitigation*: Firestore rules allow a request only when `request.auth.uid`
equals the `{uid}` path segment; every collection is nested under the user.
The rules test suite (21 tests, run against the emulator in CI) includes
read/write/list attempts on another user's path. Every local query is also
filtered by `ownerUid`, and `AccountService.onSignedIn` deletes rows of any
other account, so a shared device cannot show the previous user's tasks.
*Residual*: none known.

### T2. Malicious or malformed documents written by a modified client
*Mitigation*: rules validate key set, types, string sizes, numeric ranges and
`serverUpdatedAt == request.time`, and reject writes older than the stored
version. The client's `RemoteMapper` independently clamps and truncates every
field when reading, and drops documents it cannot parse, so one bad document
cannot crash sync or other devices.

### T3. Abuse of the backend by non-app clients (quota exhaustion, scraping)
*Mitigation*: App Check with Play Integrity in release builds; enforcement is
a console switch (see release checklist). The project runs on the Spark plan,
so abuse cannot create a bill: exhausting the free quota pauses the service
until the daily reset, which the app reports to users.
*Residual*: before enforcement is turned on, a scripted client holding a valid
user token can read/write only its own data, bounded by the rules.

### T4. Token theft from the device
*Mitigation*: tokens are held by the Firebase Auth SDK in app-private
storage; the app never logs, displays or copies them. Backups: see T6.
*Residual*: a rooted device exposes app-private storage; out of scope.

### T5. Sensitive data in logs or crash output
*Mitigation*: `AppLog` never receives task content; debug logs are compiled
out of release builds; in release only exception class names are logged, not
messages (some SDK messages echo request payloads). No crash-reporting SDK is
bundled. Firebase Analytics collects only its automatic events, and the
Advertising ID permission is removed from the manifest.

### T6. Data leaving through device backup
*Mitigation*: `data_extraction_rules.xml` and `backup_rules` exclude the Room
database, DataStore and Firebase Auth storage from cloud backup and
device-to-device transfer. Tasks are restored through sync on sign-in instead.

### T7. Intent injection / exported components
*Mitigation*: only `MainActivity` and the widget receivers are exported (the
latter are required by the app-widget framework). `MainActivity` validates
every extra (task ids must look like UUIDs and are resolved only within the
signed-in user's rows). Reminder, focus and system-event receivers are not
exported, and their PendingIntents are immutable with explicit components.

### T8. Lock-screen disclosure
*Mitigation*: reminder and focus notifications use `VISIBILITY_PRIVATE` with
a public version that shows only "Task reminder", so titles are hidden on a
secure lock screen. Widgets necessarily show titles on the home screen; this
is the user's choice when adding a widget.

### T9. Cleartext traffic
*Mitigation*: release `network_security_config.xml` disallows cleartext for
all domains. The debug config permits cleartext only to the Firebase
emulator host.

### T10. Secrets in the repository
*Mitigation*: `google-services.json`, `.firebaserc`, `keystore.properties`,
`*.jks` and `.env` are git-ignored; only `*.example` templates are committed.
Signing credentials in CI come from repository secrets. A pre-launch check is
`git ls-files | grep -iE 'jks|keystore.properties|google-services'`
returning nothing.

### T11. Supply chain
*Mitigation*: all dependencies are pinned in `gradle/libs.versions.toml`
(stable versions only); the Firestore rules tests use `package-lock.json`.
Dependabot/Renovate is recommended but not yet configured.

### T12. Denial of service against the user (reminder spam, huge data)
*Mitigation*: list queries are capped (`LIST_LIMIT`), remote pages are bounded,
titles/notes/tags have size limits enforced on both sides, and several
reminders due together collapse into one notification group with one alert.

## Permissions and why

| Permission | Reason |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Sync and sign-in; offline detection |
| `POST_NOTIFICATIONS` | Reminders and focus timer |
| `SCHEDULE_EXACT_ALARM` | Reminders fire at the chosen minute |
| `RECEIVE_BOOT_COMPLETED` | Re-create alarms after restart |
| `VIBRATE` | Reminder vibration pattern |
| `USE_FULL_SCREEN_INTENT` | Reminder wakes the screen like an alarm |

No location, contacts, camera, microphone, storage or advertising-ID permission
is requested. The wallpaper photo import uses the system photo picker, which
needs no permission.
