# Two-device synchronisation test procedure

Run before each release that touches `sync/`, `data/remote/` or the Firestore
rules. Needs two Android phones (or one phone and one emulator with Play
services) signed into the **same** Google account, both on the release build.
Record each step as PASS / FAIL in `launch-readiness.md`.

Conventions: **A** and **B** are the devices. "Refresh" means pull-to-refresh
on Today or Settings → Sync now (an unthrottled pull); without it, a device
picks up the other's changes within 10 minutes of its next app open or at the
12-hourly background sync. Keep both devices online unless the step says
otherwise. Use task titles with the step number so results are unambiguous.

| # | Step | Expected | Checks |
|---|---|---|---|
| 1 | A: Add task "T1 from A" (Today). | Settings on A shows "Last synced 0 minutes ago", 0 waiting. | push |
| 2 | B: Refresh. | "T1 from A" appears on B's Today. | pull, cursor |
| 3 | B: Open T1, change title to "T1 edited on B", Save. | B: 0 waiting after a few seconds. | push of an edit |
| 4 | A: Refresh. | Title updated on A; the card keeps its position, no duplicate. | pull replaces by id |
| 5 | A: Delete T1 (swipe left or Delete in the editor). | Gone on A. | tombstone push |
| 6 | B: Refresh. | T1 gone on B; Browse → All tasks count decreased by one. | tombstone pull |
| 7 | A: Add "T7", set priority High. B: Refresh. | B shows T7 in the Matrix "High" box with the red mark. | priority field |
| 8 | B: Open T7, set due date = tomorrow, time 09:00, reminder "At time of task", Save. A: Refresh. | A shows "Tomorrow · 9:00" with the bell; A's Settings → Reminder health unchanged; A registers the alarm (optional: `adb shell dumpsys alarm \| grep ReminderReceiver` on A shows the trigger). | date/time/reminder fields |
| 9 | A: Browse → + → list "Project X", colour 3. Move T7 into it (long-press T7 → Move → Project X). B: Refresh. | B shows list "Project X" with count 1 and T7 inside; same colour. Then A: delete the list. B: Refresh → list gone, T7 back in Inbox on B. | list create / delete / reassignment |
| 10 | Both online. A and B open T7 at the same time. A: title "T7 A wins", B: title "T7 B wins". Tap Save on A, then within ~2 s on B. Refresh both. | Both devices show **the same** title (the later `updatedAt` wins; expected "T7 B wins"). No duplicate, no "changes refused". | last-writer-wins, push transaction |
| 11 | Airplane mode on **both**. A: edit T7 notes = "offline A", add "T11 from A". B: complete T7, add "T11 from B". Airplane off on A, wait for 0 waiting; airplane off on B, wait; Refresh both. | Both show T11 from A and T11 from B exactly once; T7 is completed on both (B's completion and A's note edit were on different fields: with document-level LWW only the later edit survives — record which one did; either is acceptable, duplicates are not). | offline queue, reconnection, LWW |
| 12 | A: Add "T12" then delete it before syncing (airplane on, add, delete, airplane off). B: Refresh. | T12 never appears on B, or appears and disappears; never remains. | tombstone of an unsynced task |
| 13 | A: Sign out, sign back in. | A shows every task B has, once each; no resurrected deleted tasks (T1, T12 absent). | full resync, deletion survives resync |
| 14 | B: Settings → Delete account (only on a throw-away test account!). A: Refresh. | A is signed out on its next sync (UNAUTHENTICATED) and shows the sign-in screen; Firestore console shows no `users/{uid}` document. | account deletion |

Pass criteria: no duplicate tasks or lists, no missing tasks, no deleted task
reappearing, no "changes refused" status, and both devices show "Last synced"
at the end.

## What the automated tests already cover

`SyncEngineTest` runs these flows with two simulated devices against the fake
remote store: push/pull, deletion propagation, concurrent offline edits with
last-writer-wins in both orders, deterministic next occurrence for repeating
tasks completed on both devices, rejection handling, cursor resume after a
failure, equal-timestamp pagination, and the pull throttle. `cursor.test.mjs`
runs the real pull query against the Firestore emulator. Steps 10–13 above
are the physical confirmation of those tests.
