# Launch readiness — v1.0.0 (versionCode 1)

Audit and verification performed 2026-11-01 on the commit tagged `v1.0.0-rc1`.
Statuses: **PASS** verified; **FAIL** failed verification; **NOT IMPLEMENTED**
feature does not exist; **NOT VERIFIED** could not be tested in the available
environment (one OPPO CPH2219 on Android 13, Robolectric, no tablet, no
Android 14 device).

## 1. Original architecture

Single-module Kotlin/Compose app: Room (local source of truth) → repository →
ViewModel `StateFlow` → Compose screens; offline-first sync to Firestore via
WorkManager; AlarmManager reminders; DataStore settings; Glance widgets; Hilt.
Details in [architecture.md](../architecture.md) and [sync-design.md](../sync-design.md).

## 2. Issues discovered (two independent read-only audits + manual checks)

| Id | Sev | Finding | Status |
|---|---|---|---|
| A1 | P0 | `.gitignore` rule `release/` also ignored `app/src/release/` (Play Integrity App Check provider) and `docs/release/`; a clean clone could not build release | Fixed: rule scoped to `/release/` and `/app/release/`; files committed |
| A2 | P0 | No privacy policy URL, terms URL or support e-mail configured (`gradle.properties`); Play requires a published policy | **Open — needs the owner** (see §15) |
| A3 | P1 | Reminder group-summary notification and focus-timer notification shared id 7100; each could dismiss the other | Fixed: distinct ids |
| A4 | P1 | Monthly/yearly repeats drifted permanently after a short month (31 Jan → 28 Feb → 28 Mar…) | Fixed: `BYMONTHDAY` anchor in the rule; tests added |
| A5 | P1 | Firebase Analytics shipped while manifest/README said "no analytics" | Fixed: kept (owner's choice), manifest, README, Licences, privacy policy and Data safety now declare it |
| A6 | P1 | Room schema v4 not exported; no migration test | Fixed: `4.json` committed; `MigrationTest` upgrades a v1 database with data to v4 |
| A7 | P1 | Release build silently produced unsigned / Firebase-less artefacts when config was missing | Fixed: build refuses unless `-Pticktodo.allowIncompleteRelease=true` |
| A8 | P1 | No CI | Fixed: GitHub Actions (build, unit tests, lint, release assembly, Firestore rules tests) |
| A9 | P1 | A reminder already on screen was not dismissed when its task was completed/deleted in-app | Fixed |
| A10 | P2 | Focus banner spun at 50 Hz after a phase ended until the (possibly late) alarm fired | Fixed: banner advances the session itself |
| A11 | P2 | Double-tap opened two editors; Save tapped twice popped two screens | Fixed: resumed-state guard; save re-entry guard |
| A12 | P2 | Corrupt DataStore file would crash every launch; application scope had no exception handler | Fixed: corruption handlers reset to defaults; handler logs instead of crashing |
| A13 | P2 | Out-of-range `dueEpochDay` from a hostile client would crash every screen | Fixed: clamped on read; range enforced in rules |
| A14 | P2 | `deleteList` re-dirtied tombstoned tasks | Fixed |
| A15 | P2 | Search screen deleted/completed without Undo | Fixed: same Undo path as other screens |
| A16 | P2 | `USE_FULL_SCREEN_INTENT` used without the Android 14 `canUseFullScreenIntent()` check | Fixed: checked; "Wake the screen" row in Reminder health with a settings link |
| A17 | P2 | Firestore rules: tag element type, timestamps, schema, completion consistency unchecked | Fixed in rules + 2 new rules tests (**rules not yet deployed**, see §15) |
| A18 | P2 | README stale (application id, theme list, test counts, missing docs) | Fixed |
| A19 | P2 | Completed-task queries had no index | Fixed (v4 index) |
| A20 | P3 | Unused `VIBRATE` permission; unused test libraries; dead code; widget priority colours differed from app | Fixed |
| A21 | P3 | `ListWidgetConfigActivity` accepted any widget id | Fixed: only this app's widgets |
| A22 | P3 | Reminders missed during a force-stop never shown | Fixed: caught up (6 h grace) when the app opens |
| A23 | P3 | Task grouping/sorting ran on the main thread | Fixed: `Dispatchers.Default` |
| — | P3 | Not changed (documented in known-limitations): incremental pull re-reads this device's own pushes; widgets refresh "today" hourly; no field-level merge; baseline profile hand-written | Open |

## 3–10. Improvements by area

* **Crashes/stability:** A3, A10–A14, exception handler on the application scope.
* **UI/UX:** A9, A15, A16; design system verified in light and dark on device.
* **State management:** single Room source of truth confirmed; Undo actions now run in a ViewModel scope so they complete even if the snackbar host leaves composition; search joins the shared Undo pattern.
* **Database:** v4 index, exported schema, migration test; all multi-row writes already transactional.
* **Integration:** Firestore rules tightened and tested (23/23 on the emulator); user isolation re-verified by both audits.
* **Security:** repository history scanned for secrets (none); backups disabled; cleartext disabled; exported components minimal; threat model written.
* **Performance:** grouping off main thread; busy loop removed; baseline profile present (hand-written).
* **Accessibility:** content descriptions on all icon buttons, swipe actions exposed as custom actions, drag-reorder has Move up/down actions, 4.5:1 contrast enforced by the theme, large-font layout test.

## 11–13. Tests and build

| Check | Result |
|---|---|
| Unit + Robolectric UI tests (`:app:testDebugUnitTest`) | PASS — 100/100 (includes the new migration and layout tests) |
| Android Lint (`:app:lintDebug`, abort on error) | PASS — 0 errors |
| Firestore rules tests (emulator) | PASS — 23/23 |
| Migration test v1 → v4 | PASS |
| Layout tests (320 dp phone, 960×600 tablet landscape, 2.0× font) | PASS |
| Release build `:app:bundleRelease :app:assembleRelease` | PASS — `clean bundleRelease assembleRelease` succeeded; AAB 10.98 MB, APK 5.15 MB, R8 mapping produced |
| AAB/APK signed with the upload key | PASS — `keytool -printcert` on the AAB shows `CN=Tick ToDo upload, O=Ferrabytes`, SHA-256 `D6:BF:5C:22:…:B5:A1:C0` |
| Release APK installed over the previous build on OPPO CPH2219 (Android 13), data kept | PASS — same release code signed with the debug key installed over the previous build on OPPO CPH2219 (Android 13): existing tasks, lists and settings present; Today, Upcoming, Matrix, Browse and Settings open; sync "Last synced 0 minutes ago"; no crash in logcat. The upload-key-signed APK itself was not installed, because that would need an uninstall. |

## 14. Remaining known issues

See [known-limitations.md](../known-limitations.md).

## 15. Release blockers (owner actions)

1. **Privacy policy:** written (FerraByte, ferrabyte512@gmail.com, version 1) and configured in `gradle.properties`; it goes live when the repository is pushed to GitHub as `ferrabyte512/ticktodo` and GitHub Pages is enabled from `main` → `/docs`. Configured values:
   `ticktodo.privacyPolicyUrl`, `ticktodo.termsUrl` (or leave terms empty), `ticktodo.supportEmail`. The app hides the links while they are empty.
2. ~~Deploy the tightened Firestore rules~~ — deployed to `tick-todo-2916c` on 2026-11-01 after 23/23 emulator tests.
3. **Firebase console:** add the upload key's SHA-1/SHA-256 (SHA-1 `FF:5C:EA:FC:D7:93:3F:B7:CD:BB:13:FC:5B:E3:90:97:69:3F:CB:9D`, SHA-256 `D6:BF:5C:22:74:A7:6C:75:B6:4D:AF:AF:1A:96:C1:06:57:1A:04:61:9D:1A:BA:47:E6:23:71:35:27:B5:A1:C0`) and, after the first Play upload, the Play App Signing fingerprints; Blaze plan + budget alert; App Check enforcement stays off until the Play build shows verified traffic.
4. **Back up** the secrets folder (`E:\E\ticktodo-secrets`: upload keystore, `keystore.properties`, `google-services.json`, the signed AAB) to a password manager or an encrypted offline copy.
5. **Play Console:** Data safety form from `data-safety.md`; exact-alarm and full-screen-intent declarations; store listing from `store-listing.md`; internal testing track first.

## 16. Build commands

```bash
# checks
./gradlew :app:testDebugUnitTest :app:lintDebug
cd firebase/rules-tests && npm test && cd ../..

# release (refuses to run without google-services.json and signing)
./gradlew clean :app:bundleRelease :app:assembleRelease
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
```

## 17. Artefacts

| Artefact | Path |
|---|---|
| Android App Bundle (upload this) | `release/v1.0.0/ticktodo-1.0.0-1.aab` (copy of `app/build/outputs/bundle/release/app-release.aab`) |
| Release APK, upload-key signed (device testing) | `release/v1.0.0/ticktodo-1.0.0-1.apk` |
| R8 mapping (upload with the release for readable stack traces) | `release/v1.0.0/mapping.txt` |
| Lint report | `app/build/reports/lint-results-debug.html` |
| Unit test report | `app/build/reports/tests/testDebugUnitTest/index.html` |

`release/` is git-ignored; keep it with the signing backup. CI uploads test and lint reports as workflow artefacts.
