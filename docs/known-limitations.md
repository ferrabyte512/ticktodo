# Known limitations

What the current version deliberately does not do, and what to tell users
who ask. Updated 2026-11-01.

## Product

| Area | Limitation |
|---|---|
| Accounts | Google sign-in only. No e-mail/password, no offline-only (guest) mode: the app requires an account to store tasks. |
| Sharing | Lists cannot be shared with other people; there is no collaboration. |
| Attachments | Tasks have notes and tags but no file or image attachments. |
| Calendar | No calendar view; Upcoming groups tasks by day instead. No import/export to device calendars. |
| Statistics | Focus time per task and per day is recorded and shown on the focus screen; there is no reports/statistics screen or charts. |
| Search | Searches titles, notes and tags of the signed-in user; no saved searches, no "recent searches". |
| Recurrence | Daily / weekdays / weekly / every N weeks / monthly / yearly from the due date. No "every 2nd Tuesday", no end date, no "N times". |
| Reminders | One reminder per task (offset from the due time; all-day tasks remind relative to 09:00). No multiple reminders, no location reminders. |
| Priority matrix | Quadrants are the four priority levels (High = urgent and important, Medium = urgent, Low = important, Optional = neither). Urgency and importance are not stored as separate fields. |
| Themes | Light, dark and system, one accent (preset or custom) at a time; wallpapers are decorative. No per-list colours in the theme. |
| Widgets | Today, Upcoming, a configurable list, quick add and focus. Widgets show task titles on the home screen by design. |
| Languages | English only. All strings are in `res/values/strings.xml` ready for translation. |
| Tablets | Layouts adapt (navigation rail, wider editor); there is no dedicated two-pane tablet layout. |

## Technical

| Area | Limitation |
|---|---|
| Conflict resolution | Document-level last-writer-wins on `updatedAt`. Two devices editing *different fields* of the same task while offline keep only the later edit. |
| Deleted tasks | Remote tombstones are purged after 90 days; a device that stays offline longer and still holds a dirty edit to a deleted task will recreate it. |
| Firestore quotas | On the Spark plan sync stops for everyone when the daily quota is exhausted; the app shows "Quota" in Settings. Use Blaze with a budget alert. |
| App Check | Protection is only effective once enforcement is switched on in the console after the Play build is live; before that it is monitoring only. Locally built release APKs fail Play Integrity (they are not from Play) and need a debug token or enforcement off. |
| Exact alarms | On Android 12+ reminders fire at the exact minute only while "Alarms & reminders" is allowed; otherwise they may be delayed by the system. Settings → Reminder health shows this. |
| Full-screen reminders | On Android 14+ the user may revoke full-screen notifications; reminders then ring and show as heads-up but do not light a locked screen. |
| Battery managers | Some OEM battery optimisers (Xiaomi, Huawei, OPPO/realme, Samsung in Sleeping apps) can still kill background apps; the app re-registers its alarms every time it is opened, and reminders are alarm-clock alarms, which these managers generally respect. |
| Analytics | Firebase Analytics automatic events are always on; there is no in-app opt-out (documented in the privacy policy). |
| Backups | Android auto-backup is disabled on purpose; data is restored by signing in. |
| Baseline profile | `baseline-prof.txt` is a hand-written class/method list, not a Macrobenchmark-generated profile; start-up gains are modest. |
| Tests | Unit, Robolectric UI and migration tests exist; there are no instrumented (`androidTest`) runs on real devices in CI. Device testing is manual (see release checklist §5). |
| Firestore rules tests | Need JDK 21+ for the Firestore emulator (`JAVA_HOME` must point at it). |
