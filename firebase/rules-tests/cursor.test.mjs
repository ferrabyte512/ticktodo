// Verifies the sync engine's pull query against the Firestore emulator:
//   orderBy(serverUpdatedAt).orderBy(documentId()).startAfter(time, id).limit(n)
// with several documents sharing one commit time (a batch write), so that paging
// inside that group neither skips nor duplicates anything. Run by `npm test`.
import { readFileSync } from 'node:fs';
import { test, after, before } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection, doc, documentId, getDocs, limit, orderBy, query, serverTimestamp,
  startAfter, where, writeBatch,
} from 'firebase/firestore';

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-ticktodo',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8') },
  });
  await env.clearFirestore();
});
after(async () => env?.cleanup());

const alice = () => env.authenticatedContext('alice').firestore();

function task(id, i) {
  return {
    id, schema: 1, listId: null, parentId: null, title: `Task ${i}`, notes: '', priority: 0,
    dueEpochDay: null, dueMinuteOfDay: null, reminderOffsetMinutes: null, recurrence: null,
    tags: [], focusPlan: null, completed: false, completedAt: null,
    createdAt: 1_700_000_000_000 + i, updatedAt: 1_700_000_000_000 + i, deleted: false,
    serverUpdatedAt: serverTimestamp(),
  };
}

/** Pulls every page the way SyncEngine does, resuming from (time, id). */
async function pullAll(db, pageSize, since = { at: 0, id: null }) {
  const col = collection(db, 'users/alice/tasks');
  const seen = [];
  let cursor = since;
  let after = null;
  for (;;) {
    let q = query(col,
      where('serverUpdatedAt', '>=', new Date(cursor.at)),
      orderBy('serverUpdatedAt'), orderBy(documentId()), limit(pageSize));
    if (after) q = query(q, startAfter(after));
    else if (cursor.id) q = query(q, startAfter(new Date(cursor.at), cursor.id));
    const snap = await getDocs(q);
    for (const d of snap.docs) {
      seen.push(d.id);
      cursor = { at: d.get('serverUpdatedAt').toMillis(), id: d.id };
    }
    if (snap.size < pageSize) return { seen, cursor };
    after = snap.docs[snap.docs.length - 1];
  }
}

test('seven documents committed together share one serverUpdatedAt', async () => {
  const db = alice();
  const batch = writeBatch(db);
  const ids = Array.from({ length: 7 }, (_, i) => `b0000000-0000-4000-8000-00000000000${i}`);
  ids.forEach((id, i) => batch.set(doc(db, 'users/alice/tasks', id), task(id, i)));
  await batch.commit();
  const snap = await getDocs(collection(db, 'users/alice/tasks'));
  const times = new Set(snap.docs.map((d) => d.get('serverUpdatedAt').toMillis()));
  assert.equal(snap.size, 7);
  assert.equal(times.size, 1, 'batch gives all documents the same commit time');
});

test('paging with limit 3 inside the equal-time group returns each document exactly once', async () => {
  const { seen } = await pullAll(alice(), 3);
  assert.deepEqual([...seen].sort(), [...new Set(seen)].sort(), 'no duplicates');
  assert.equal(seen.length, 7, 'nothing skipped');
});

test('resuming from a saved (time, id) cursor reads only what came later, in the same group too', async () => {
  const db = alice();
  // Save the cursor after the first run.
  const first = await pullAll(db, 3);
  const { cursor } = first;

  // Nothing new: an empty result, not the document at the cursor again.
  const idle = await pullAll(db, 3, cursor);
  assert.equal(idle.seen.length, 0);

  // Two more documents, committed together; then a third on its own.
  const batch = writeBatch(db);
  for (const i of [7, 8]) {
    const id = `c0000000-0000-4000-8000-00000000000${i}`;
    batch.set(doc(db, 'users/alice/tasks', id), task(id, i));
  }
  await batch.commit();
  const loneId = 'd0000000-0000-4000-8000-000000000009';
  await writeBatch(db).set(doc(db, 'users/alice/tasks', loneId), task(loneId, 9)).commit();

  const next = await pullAll(db, 2, cursor);
  assert.deepEqual(next.seen.sort(), [
    'c0000000-0000-4000-8000-000000000007', 'c0000000-0000-4000-8000-000000000008', loneId,
  ].sort());

  // A cursor that sits in the middle of an equal-time group resumes after that exact id.
  const midGroup = { at: cursor.at, id: 'b0000000-0000-4000-8000-000000000003' };
  const fromMid = await pullAll(db, 2, midGroup);
  assert.deepEqual(fromMid.seen.filter((id) => id.startsWith('b')).sort(), [
    'b0000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000005', 'b0000000-0000-4000-8000-000000000006',
  ]);
});
