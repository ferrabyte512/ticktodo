# Sync design

## Goals

1. The app must be fully usable offline; the network is never on the critical
   path of a user action.
2. Two devices editing the same account must converge without losing an edit
   silently, and must never create duplicate tasks.
3. A malicious or buggy client must not be able to corrupt another user's data
   or write documents the app cannot read.

## Storage layout

Firestore: `users/{uid}/tasks/{taskId}` and `users/{uid}/lists/{listId}`.
Security rules allow access only when `request.auth.uid == uid`, validate the
document shape and sizes, require `serverUpdatedAt == request.time`, and refuse
any write whose `updatedAt` is older than the stored one. The rules have an
emulator test suite (`firebase/rules-tests`).

## Local bookkeeping

| Column | Meaning |
|---|---|
| `dirty` | Row has local changes not yet confirmed by the server. |
| `localRevision` | Incremented on every local edit. A push clears `dirty` only if the revision is unchanged, so an edit made during an in-flight push is not lost. |
| `updatedAt` | Logical edit time, always `max(now, previous + 1)` so an edit is never equal to or older than the version it replaces, even with a slow or stepped-back clock. |
| `serverUpdatedAt` | Server commit time of the last version received, used as the pull cursor. |

## One sync pass (`SyncEngine.sync`)

1. **Pull**: for each collection, query documents ordered by
   (`serverUpdatedAt`, document id) starting strictly after the saved cursor,
   which is the (time, id) of the last document applied. Because that is a
   total order, two documents sharing a commit time can never be skipped at a
   page boundary, and nothing already applied is read again. For each
   document: if the local row is clean, replace it; if it is dirty, keep the
   version with the greater `updatedAt` (ties favour the remote copy, which is
   already on the server). A document that is this device's own push coming
   back only has its server time recorded and is not reported as a change.
   Malformed documents are skipped and logged by type.
2. **Push**: dirty rows, oldest first, in batches. Each push is a Firestore
   transaction: read the current document; if its `updatedAt` is newer than
   ours, the remote wins and the local row is replaced; otherwise write ours
   with `serverUpdatedAt = serverTimestamp()`. A document the rules reject
   (`PERMISSION_DENIED` / `INVALID_ARGUMENT`) is skipped, counted as "rejected"
   and left dirty so the user can see it in Settings; it does not block the
   rest of the batch.
3. **Housekeeping**: a full pass (first sync, or every 60 days) also removes
   clean local rows that no longer exist remotely. Local tombstones are dropped
   after 30 days and remote ones after 90 days, in batches of 200.

Conflict resolution is therefore last-writer-wins on `updatedAt` at document
granularity, enforced both client-side (pull/push) and server-side (rules), so
no client can be tricked into regressing a document.

## Repeating tasks

Completing a repeating task creates the next occurrence with an id derived
deterministically from the completed task's id
(`UUID.nameUUIDFromBytes("$id:next")`). Two devices completing the same task
offline therefore produce the same next-occurrence document instead of two.

## Scheduling (`SyncScheduler`)

* A one-off `SyncWorker` is enqueued after every local change (3 s initial
  delay, `KEEP` policy so a burst of edits becomes one run) and immediately
  when the app comes to the foreground. These routine runs are
  **pull-throttled**: they push dirty rows at once but skip the pull while the
  last pull is less than 10 minutes old. Sign-in, "Sync now" and
  pull-to-refresh request an unthrottled run, which replaces a queued
  throttled one.
* A periodic worker runs every 12 hours.
* Cost on the Firestore free tier: a run that pulls costs at least one read
  per collection (an empty query bills one read); each push costs one read
  (the transaction's check) and one write, and the pushed document is read
  once more by the next pull. Measured in `SyncEngineTest`, a busy day (8 app
  opens, 6 edits, 2 periodic runs) costs 26 reads and 6 writes per device.
* Both require a connected network and use exponential backoff.
* All sync work is cancelled on sign-out, and the local rows of any other
  account are deleted on sign-in (`AccountService.onSignedIn`).

## What the user sees

`SyncStatusRepository` turns the last outcome into a status (running, pending
count, last success time, or a classified error: offline, signed out, access
denied, changes refused, quota, database missing, index missing). The task
lists show a strip for offline/error states with Retry and Details; Settings
shows the full message.

## Known limitations

* Field-level merging is not attempted: two devices editing different fields
  of the same task offline will keep only the later edit.
* Deleting a list moves its tasks to the Inbox rather than deleting them;
  this is intentional.
* Remote tombstones are purged after 90 days, so a device offline for longer
  than that may resurrect a deleted task if it still holds a dirty edit to it.
