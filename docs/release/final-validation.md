# Final release validation — Tick ToDo v1.0.0 (versionCode 1)

Date: 2026-11-01 · Build under test: commit `9b89ae6` (tag `v1.0.0-rc1`), release
code signed with the debug key for installation over the existing install; the
upload-key-signed bundle `release/v1.0.0/ticktodo-1.0.0-1.aab` was built from
the same commit and verified separately (§9).
Device: OPPO CPH2219, Android 13, account ferrabyte512@gmail.com.
Verdicts: PASS · FAIL · NOT TESTED · BLOCKER.

## 1. Executive summary

The app is functionally ready for Play **internal testing**. Every area that
could be exercised on one phone passed: authentication (including the
sign-out/sign-in round trip that exposed and then confirmed the fix for the
release-only sign-in failure), task management, offline synchronisation,
reminders, focus timer, and all five home-screen widgets including completion
from a widget after the process was killed. Two-device synchronisation and a
device restart could not be exercised physically (one phone; no reboot
authorised) and are covered by automated tests and a written procedure.

Three items remain before **production**: store assets (screenshots, feature
graphic) do not exist yet (**BLOCKER** for the listing, not for internal
testing), one stale sentence on the focus-setup screen, and the console steps
listed in `launch-readiness.md` §15.

## 2. Two-device synchronisation

| Step | Verdict | Evidence |
|---|---|---|
| 1–12 physical two-device steps | **NOT TESTED** | Only one device available. Procedure written: [two-device-test.md](two-device-test.md). |
| Architecture review (identity, Room isolation, per-user Firestore paths, LWW, cursor persistence, pagination, tombstones, WorkManager retry, offline queue) | PASS (code) | Two independent audits earlier today; nothing changed since except the cursor work, which is covered below. |
| Automated two-device flows (fake remote store) | PASS | `SyncEngineTest` 20/20: push/pull, deletion propagation, concurrent offline edits with LWW in both orders, deterministic repeat occurrence, rejection handling, cursor resume after failure, equal-timestamp paging, pull throttle, deletion during throttled run. |
| Live Firestore query with equal commit times | PASS (emulator) | `cursor.test.mjs`: 7 documents in one batch share one `serverUpdatedAt`; paged at limit 2 and 3, resumed from a saved and a mid-group cursor; every document once. |
| Production Firestore, single device | PASS | First pull after v5, unthrottled "Sync now", sign-out/sign-in resync, offline queue flush — all against the live database with no error. |

## 3. Widgets (Today, Upcoming, Task list, Add task, Focus timer)

| Check | Verdict | Evidence |
|---|---|---|
| Rendering | PASS | Today, Upcoming, Add task and Focus timer rendered on the launcher; rounded card, header, "+" button, task rows with priority mark. |
| Data freshness | PASS | Completing/uncompleting in the app updated the Today widget within seconds; Upcoming showed the next occurrence of the repeating task. |
| Completion action | PASS | Tapping the circle on "Buy milk" completed it; widget showed "Nothing due today"; app showed 2 of 2 done. |
| Add-task action | PASS | "+" on the Today widget opened the app's Add task sheet with the keyboard. |
| Refresh behaviour | PASS | Widgets update on every data change (`TaskChangeEffects`), after sync, on focus changes and hourly (`updatePeriodMillis`). |
| Dark/light theme | PASS (dark) / NOT TESTED (light) | Widgets follow the **system** theme via `GlanceTheme`; the phone was in system dark mode. |
| Resizing | NOT TESTED | `resizeMode="horizontal\|vertical"`, `SizeMode.Exact`, compact header under 220 dp are implemented; not exercised on the launcher. |
| Empty state | PASS | "Nothing due today" after the last task was completed. |
| Deleted task handling | PASS (code) | Widget queries filter `deleted = 0`; a deleted task left the list in the app test; not separately re-checked on the widget. |
| After sign-out / sign-in | PASS | Signed out: "Open Tick ToDo to sign in". Signed in again: Upcoming widget showed the tasks within seconds. |
| After process termination | PASS | `am force-stop`, then tick from the widget: task completed, process restarted by the action callback, no crash. |
| After device restart | NOT TESTED | No reboot performed. `SystemEventsReceiver` handles `BOOT_COMPLETED` → `widget.update()`. |
| Picker preview | Observation | OPPO's launcher shows the app icon instead of the `previewLayout`; cosmetic, launcher-specific. |

## 4. Authentication

| Check | Verdict |
|---|---|
| Google Sign-In (Credential Manager, account chooser) | PASS |
| Sign-out (local data wiped, alarms cancelled, widgets show sign-in prompt) | PASS |
| Sign-in after sign-out | PASS |
| Session persistence across force-stop and relaunch | PASS |
| Existing account data restoration | PASS (24dce069 account restored 7 tasks earlier tonight; ferrabyte512 restored its 2) |
| Account deletion | NOT TESTED on device (would delete a real account); code path reviewed: reauth → cloud purge → auth user delete → local wipe |
| `default_web_client_id` retained in release resources | PASS — present in `base/resources.pb` of the final AAB; absent from the earlier AAB (bug A25, fixed) |

## 5. Task management

| Check | Verdict |
|---|---|
| Create / edit / delete | PASS |
| Complete, Undo, uncomplete ("Mark not complete") | PASS |
| Priorities and Matrix placement | PASS (High → "Urgent and important" box) |
| Due date, time, reminder editing | PASS (editor rows; reminder fired at the exact minute) |
| Recurring task | PASS (daily task completed → next occurrence tomorrow in Upcoming) |
| Subtasks | PASS (0/1 shown on the card) |
| Lists | PASS (create "Groceries"); delete NOT TESTED tonight (tested on 23 Oct) |
| Tags | PASS (`#errands` chip, Browse → Tags) |
| Search | PASS |
| Focus timer: start, +5 min (24:56 → 29:49), pause/resume, stop, ongoing notification | PASS |
| Notifications and alarms | PASS (alarm-clock alarm, alarm tone channel) |

## 6. Offline synchronisation

| Check | Verdict |
|---|---|
| Create offline (airplane mode) | PASS — strip "Offline. 1 change will sync when you reconnect." |
| Edit / delete offline | PASS (edit tested 23 Oct and tonight's rename; delete via tombstone test in `SyncEngineTest`) |
| Reconnect and synchronise | PASS — "Last synced 0 minutes ago", 0 waiting |
| No data loss | PASS — all offline tasks present after sync and after sign-out/sign-in |

## 7. Security audit

| Check | Verdict |
|---|---|
| Firestore rules enforce user isolation; no public access | PASS (26/26 emulator tests incl. cross-user and unauthenticated denial; deployed 2026-11-01) |
| No secrets in Git (tracked files and full history) | PASS |
| No debug Firebase configuration in release | PASS — `mapping.txt` has no `DebugAppCheckProviderFactory`, release dex has no emulator host, release `network_security_config` is HTTPS-only |
| No task content in logs | PASS (`AppLog` only; release logs exception class names) |
| Account deletion removes cloud and local data | PASS (code) / NOT TESTED (device) |
| Spark plan, no Blaze-only services, no paid APIs | PASS — Auth, Firestore, App Check, Analytics only; no Storage/Functions/Hosting; billing not touched |

## 8. Performance and stability (release build, OPPO CPH2219)

| Metric | Result |
|---|---|
| Cold start (`am start -W`, 3 runs after force-stop) | 1162 / 836 / 952 ms |
| Memory | 120 MB PSS after a tour of all screens |
| Scrolling Today (3 fling cycles) | 278 frames, 2.9 % janky, p90 27 ms |
| WorkManager jobs registered | 4 (one-time sync, periodic sync + WorkManager's own); no duplicates |
| Firestore reads | 26 per device per busy day (measured in tests; was 44) |
| Crashes across the whole session | 0 (`AndroidRuntime` log) |
| Battery | NOT TESTED (no multi-hour measurement); no wake locks, alarms only for user reminders, periodic sync 12 h |

## 9. Play Store compliance

| Item | Verdict |
|---|---|
| Application id `com.ferrabytes.ticktodo`, versionCode 1, versionName 1.0.0 | PASS |
| Release signing (upload key, `CN=Tick ToDo upload, O=Ferrabytes`), Play App Signing to be enabled on first upload | PASS |
| R8 + resource shrinking | PASS (and the one shrinker casualty is fixed) |
| `google-services.json` present in the build; not committed | PASS |
| Privacy policy URL live (`ferrabyte512.github.io/ticktodo/legal/privacy-policy.html`) and in the app | PASS |
| Support e-mail | PASS |
| Data safety answers prepared | PASS (`data-safety.md`) — must be entered in the console |
| Account deletion in-app + policy mention (Play requirement) | PASS |
| App icon (adaptive + monochrome) | PASS |
| Screenshots (min 2 phone), feature graphic 1024×500 | **BLOCKER** — none exist yet |
| Target SDK 36 (Play requires ≥ 35 for new apps since Aug 2025) | PASS |
| Permissions: 6, all justified; no AD_ID | PASS |
| Notification permission requested in context (first reminder) | PASS |
| Backup declaration (`allowBackup=false`, extraction rules) | PASS |
| Exact-alarm and full-screen-intent console declarations | to be filled in the console (text in `release-checklist.md`) |

## 10. Bugs discovered tonight and fixes applied

| Id | Severity | Bug | Status |
|---|---|---|---|
| A25 | P0 | Release builds could not sign in: `default_web_client_id` removed by the resource shrinker | Fixed (`res/raw/keep.xml`), verified on device and in the AAB |
| A26 | P3 | Offline strip said "1 changes" | Fixed (plurals) |
| A27 | P3 | Focus-setup text still says "a longer break after a few rounds" (long breaks were removed) | **Proposed fix, awaiting approval** |
| A28 | P3 | OPPO launcher shows the app icon instead of widget preview layouts | Proposed: ship `previewImage` PNGs rendered from the preview layouts (optional) |
| — | — | Store assets missing | Proposed: capture 6 phone screenshots (light + dark) from this device and generate a 1024×500 feature graphic |

## 11. Files modified in this validation

`firebase/rules-tests/cursor.test.mjs` (new), `firebase/rules-tests/package.json`,
`.github/workflows/android.yml`, `app/src/main/res/raw/keep.xml` (new),
`app/src/main/res/values/strings.xml`, `app/src/main/java/app/ticktodo/ui/common/Components.kt`,
`docs/release/two-device-test.md` (new), `docs/release/launch-readiness.md`, this file.

## 12. Automated test results

Unit + Robolectric: **106/106**. Lint: 0 errors. Firestore rules + cursor
(emulator): **26/26**. Migration v1→v5: PASS. Layout tests (320 dp, tablet
landscape, 2× font): PASS.

## 13. Remaining risks

1. Two-device behaviour verified only by simulation; run `two-device-test.md`
   on the first internal-testing build with a second phone.
2. Device-restart path (alarm and widget re-registration) not exercised tonight
   (was exercised for alarms on 23 Oct).
3. Widget light theme and resizing not exercised.
4. Spark quota is shared by all users; ~1,500 busy devices/day fit; no alert
   exists on Spark — watch Firestore → Usage.
5. Play Integrity default quota 10 K/day; keep App Check unenforced until
   verified traffic is seen.

## 14. Recommendation

**Approved for Play internal testing** with the current AAB. For the
production listing: add the store assets (blocker), apply the A27 text fix,
run the two-device procedure once, then follow `launch-readiness.md` §15.
