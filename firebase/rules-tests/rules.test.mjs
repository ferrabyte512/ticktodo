// Firestore security rules tests. Run with:  npm test   (starts the emulator)
// Document shapes below mirror RemoteMapper.kt exactly; if the app's shape and
// the rules drift apart, these tests fail before users see sync errors.
import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from '@firebase/rules-unit-testing';
import {
  doc, getDoc, getDocs, setDoc, deleteDoc, collection, query, where, orderBy, serverTimestamp, Timestamp,
  runTransaction,
} from 'firebase/firestore';

const here = dirname(fileURLToPath(import.meta.url));
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-ticktodo',
    firestore: { rules: readFileSync(resolve(here, '../firestore.rules'), 'utf8') },
  });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const TASK_ID = '0b8f5c1e-3f7a-4d7e-9a51-2c1f7e9b0a11';
const LIST_ID = '7e1d2c3b-4a59-4687-b7c8-d9e0f1a2b3c4';

/** Same keys and types as RemoteMapper.taskToMap. */
function task(overrides = {}) {
  return {
    id: TASK_ID,
    schema: 1,
    listId: null,
    parentId: null,
    title: 'Buy milk',
    notes: '',
    priority: 0,
    dueEpochDay: null,
    dueMinuteOfDay: null,
    reminderOffsetMinutes: null,
    recurrence: null,
    tags: [],
    focusPlan: null,
    completed: false,
    completedAt: null,
    createdAt: 1_700_000_000_000,
    updatedAt: 1_700_000_000_000,
    deleted: false,
    serverUpdatedAt: serverTimestamp(),
    ...overrides,
  };
}

function list(overrides = {}) {
  return {
    id: LIST_ID, schema: 1, name: 'Work', colorIndex: 3,
    createdAt: 1_700_000_000_000, updatedAt: 1_700_000_000_000, deleted: false,
    serverUpdatedAt: serverTimestamp(), ...overrides,
  };
}

const alice = () => env.authenticatedContext('alice').firestore();
const bob = () => env.authenticatedContext('bob').firestore();
const anon = () => env.unauthenticatedContext().firestore();
const taskRef = (db, uid = 'alice', id = TASK_ID) => doc(db, 'users', uid, 'tasks', id);

// ---- Owner access: the exact documents the app writes must be accepted ----

test('owner can create a minimal task', async () => {
  await assertSucceeds(setDoc(taskRef(alice()), task()));
});

test('owner can create a fully populated task', async () => {
  await assertSucceeds(setDoc(taskRef(alice()), task({
    listId: LIST_ID, priority: 3, notes: 'line 1\nline 2', dueEpochDay: 20_000, dueMinuteOfDay: 9 * 60,
    reminderOffsetMinutes: 15, recurrence: 'FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE', tags: ['home', 'errands'],
    focusPlan: 'f=25;s=5;l=15;r=4;n=4;ab=1;af=0', completed: true, completedAt: 1_700_000_100_000,
  })));
});

test('owner can create a subtask', async () => {
  await assertSucceeds(setDoc(taskRef(alice(), 'alice', 'c1d2e3f4-0000-4000-8000-000000000001'),
    task({ id: 'c1d2e3f4-0000-4000-8000-000000000001', parentId: TASK_ID })));
});

test('a task written by an older app version (no focusPlan field) is accepted', async () => {
  const { focusPlan, ...old } = task();
  await assertSucceeds(setDoc(taskRef(alice()), old));
});

test('owner can push through a transaction, as the sync engine does', async () => {
  const db = alice();
  await assertSucceeds(runTransaction(db, async (tx) => {
    const snap = await tx.get(taskRef(db));
    assert.equal(snap.exists(), false);
    tx.set(taskRef(db), task());
  }));
});

test('owner can update with a newer edit time and read it back', async () => {
  const db = alice();
  await setDoc(taskRef(db), task());
  await assertSucceeds(setDoc(taskRef(db), task({ title: 'Buy oat milk', updatedAt: 1_700_000_000_500 })));
  const snap = await assertSucceeds(getDoc(taskRef(db)));
  assert.equal(snap.data().title, 'Buy oat milk');
});

test('owner can run the incremental pull query', async () => {
  const db = alice();
  await setDoc(taskRef(db), task());
  const q = query(collection(db, 'users', 'alice', 'tasks'),
    where('serverUpdatedAt', '>=', Timestamp.fromMillis(0)), orderBy('serverUpdatedAt'));
  const res = await assertSucceeds(getDocs(q));
  assert.equal(res.size, 1);
});

test('owner can write a tombstone and delete documents', async () => {
  const db = alice();
  await setDoc(taskRef(db), task());
  await assertSucceeds(setDoc(taskRef(db), task({ deleted: true, updatedAt: 1_700_000_000_001 })));
  await assertSucceeds(deleteDoc(taskRef(db)));
});

test('owner can create and update lists', async () => {
  const db = alice();
  const ref = doc(db, 'users', 'alice', 'lists', LIST_ID);
  await assertSucceeds(setDoc(ref, list()));
  await assertSucceeds(setDoc(ref, list({ name: 'Work stuff', updatedAt: 1_700_000_000_001 })));
});

// ---- Isolation ----

test('unauthenticated users cannot read or write', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => { await setDoc(taskRef(ctx.firestore()), task({ serverUpdatedAt: Timestamp.now() })); });
  await assertFails(getDoc(taskRef(anon())));
  await assertFails(setDoc(taskRef(anon()), task()));
});

test("another user cannot read, list, write or delete someone else's tasks", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => { await setDoc(taskRef(ctx.firestore()), task({ serverUpdatedAt: Timestamp.now() })); });
  const db = bob();
  await assertFails(getDoc(taskRef(db)));
  await assertFails(getDocs(collection(db, 'users', 'alice', 'tasks')));
  await assertFails(setDoc(taskRef(db), task({ updatedAt: 1_800_000_000_000 })));
  await assertFails(deleteDoc(taskRef(db)));
});

test('nothing outside users/{uid} is reachable', async () => {
  await assertFails(setDoc(doc(alice(), 'public', 'x'), { a: 1 }));
  await assertFails(getDocs(collection(alice(), 'users')));
});

// ---- Tampered input ----

test('rejects unknown fields', async () => {
  await assertFails(setDoc(taskRef(alice()), task({ isAdmin: true })));
});

test('rejects empty and oversized titles', async () => {
  await assertFails(setDoc(taskRef(alice()), task({ title: '' })));
  await assertFails(setDoc(taskRef(alice()), task({ title: 'x'.repeat(501) })));
});

test('rejects oversized notes, too many tags and bad priority', async () => {
  await assertFails(setDoc(taskRef(alice()), task({ notes: 'x'.repeat(10_001) })));
  await assertFails(setDoc(taskRef(alice()), task({ tags: Array.from({ length: 21 }, (_, i) => `t${i}`) })));
  await assertFails(setDoc(taskRef(alice()), task({ priority: 7 })));
});

test('rejects a malformed focus plan', async () => {
  await assertFails(setDoc(taskRef(alice()), task({ focusPlan: 'x'.repeat(61) })));
  await assertFails(setDoc(taskRef(alice()), task({ focusPlan: 25 })));
});

test('rejects an id that does not match the document path', async () => {
  await assertFails(setDoc(taskRef(alice()), task({ id: 'something-else' })));
});

test('rejects a client-chosen serverUpdatedAt', async () => {
  await assertFails(setDoc(taskRef(alice()), task({ serverUpdatedAt: Timestamp.fromMillis(1) })));
});

test('rejects overwriting a newer version with an older edit', async () => {
  const db = alice();
  await setDoc(taskRef(db), task({ updatedAt: 1_700_000_000_900 }));
  await assertFails(setDoc(taskRef(db), task({ title: 'stale', updatedAt: 1_700_000_000_100 })));
});

test('rejects a list with an invalid colour or name', async () => {
  const ref = doc(alice(), 'users', 'alice', 'lists', LIST_ID);
  await assertFails(setDoc(ref, list({ colorIndex: 99 })));
  await assertFails(setDoc(ref, list({ name: '' })));
});
