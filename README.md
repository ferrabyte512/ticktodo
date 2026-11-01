# Tick ToDo

A lightweight personal to-do app for Android. Tasks are stored on the device first and work fully offline. They sync to your Google account through Firebase, and reminders are designed to arrive on time.

- **Stack:** Kotlin · Jetpack Compose (Material 3) · Room · Hilt · WorkManager · Firebase Auth (Google sign-in via Credential Manager) · Cloud Firestore
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 36 (Android 16)
- **Compile SDK:** 37

## Features

| | |
|---|---|
| **Task scheduling** | Due dates and times, priorities, lists, #tags, notes, subtasks, repeating tasks, Today / Upcoming / Inbox / Completed views, search, sorting, bulk actions, undo |
| **Fast capture** | Quick-add sheet opens with the keyboard ready; Enter saves and stays open for the next task; `#tag` shorthand |
| **Reliable reminders** | Exact alarms, snooze and complete from the notification, re-armed after reboot and time changes, and a *Reminder health* checklist |
| **Focus timer sessions** | Opt-in per task. Focus and break rounds with custom lengths that repeat until you stop, pause / skip / stop, live notification countdown, focus statistics |
| **Home-screen widgets** | Today & overdue · Upcoming · Task list (any list, tag or view) · Quick add · Focus timer |
| **Themes** | Ten accent presets (Emerald by default) or any custom colour; every shade of the app is derived from the accent, so a blue accent gives a navy dark mode and a pale-blue light mode. Light/dark/system, optional wallpapers, large easy-to-tap task cards |
| **Offline + sync** | Works fully offline; syncs through Firestore when online; per-user security rules |
| **Account control** | Google sign-in, sign-out with unsynced-change warning, in-app account and data deletion |

> **Status (2026-11-01):** CI (GitHub Actions) and this machine build the debug APK and release `.aab`, with Android Lint at 0 errors, 99/99 unit and UI tests and 23/23 Firestore rules tests passing. See `docs/release/launch-readiness.md` for the per-item verification status.

---

## Contents

1. [Quick start (5 minutes, no Firebase)](#1-quick-start-5-minutes-no-firebase)
2. [Full setup with Google sign-in and sync](#2-full-setup-with-google-sign-in-and-sync)
3. [Running on a phone or emulator](#3-running-on-a-phone-or-emulator)
4. [Running tests](#4-running-tests)
5. [Building a release (.aab) for Google Play](#5-building-a-release-aab-for-google-play)
6. [Project structure](#6-project-structure)
7. [How it works](#7-how-it-works)
8. [Configuration reference](#8-configuration-reference)
9. [Fixing sync errors](#9-fixing-sync-was-refused-or-other-sync-errors)
10. [Troubleshooting](#10-troubleshooting)
11. [Documentation index](#11-documentation-index)

---

## 1. Quick start (5 minutes, no Firebase)

The project builds and launches **without** any Firebase setup. In that case the app shows a "Firebase not configured" notice instead of the sign-in button. That's enough to check that your toolchain works.

### Requirements

| Tool | Version | Notes |
|---|---|---|
| Android Studio | Latest stable | Bundles a JDK and the SDK manager |
| JDK | 17 or newer to run Gradle; Gradle then provisions the pinned toolchain (`gradle/gradle-daemon-jvm.properties`) itself | Android Studio bundles one |
| Android SDK | Platform 37, Build-Tools 37 | Android Studio installs these on first sync |

### Option A: Android Studio (recommended)

1. Open Android Studio and choose **File → Open**, then select this folder.
2. Wait for the Gradle sync to finish. The first sync downloads dependencies and takes a few minutes.
3. Choose a device in the toolbar and press **Run ▶**.

### Option B: Command line

```bash
./gradlew :app:assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

If Gradle can't find the SDK, create a `local.properties` file in the project root (it is git-ignored):

```properties
sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk
```

---

## 2. Full setup with Google sign-in and sync

You do this once per Firebase project.

### 2.1 Create the Firebase project

1. Go to <https://console.firebase.google.com> and choose **Add project**. Enable Google Analytics if you want the automatic usage events the app ships with (`firebase-analytics`); the app works either way.
2. **Build → Firestore Database → Create database**:
   - Choose **Production mode**.
   - Pick a location, for example `asia-south1` (Mumbai) for users in India. **The location cannot be changed later.**
3. **Build → Authentication → Get started → Sign-in method → Google → Enable**. Set a support email.

### 2.2 Register the Android app

1. **Project settings → Your apps → Add app → Android**.
2. **Package name:** use the value of `ticktodo.applicationId` in [`gradle.properties`](gradle.properties). The current value is `com.ferrabytes.ticktodo`.
   Change it to your own ID (for example `com.yourcompany.ticktodo`) **before** this step and before your first Play upload. It can never change after publishing.
3. Add your **SHA-1** and **SHA-256** debug fingerprints. Google sign-in fails without them:

   ```bash
   ./gradlew :app:signingReport
   ```

   Copy the `SHA1` and `SHA-256` lines from the `debug` variant. Later, add your upload key's and Play App Signing's fingerprints too (see section 5).
4. Download **`google-services.json`** and put it at `app/google-services.json`.
   This file is git-ignored on purpose. Don't commit it.

The build detects the file automatically and enables the Google Services plugin. Sync Gradle again and run the app; the **Continue with Google** button now appears.

### 2.3 Deploy the security rules and indexes

The rules in [`firebase/firestore.rules`](firebase/firestore.rules) restrict every document to its owner and check the document shape. **Deploy them before anyone uses the app.**

```bash
npm install -g firebase-tools
firebase login
firebase use --add                # pick your project, alias "default"
firebase deploy --only firestore:rules,firestore:indexes
```

### 2.4 (Recommended) Turn on App Check

App Check ensures that only your genuine app can call Firestore.

1. **Build → App Check**: register the Android app with **Play Integrity**.
2. Debug builds print a debug token to Logcat (search for `DebugAppCheckProvider`). Add it under **App Check → Apps → Manage debug tokens**.
3. After you have tested both debug and release builds, click **Enforce** for Cloud Firestore.

---

## 3. Running on a phone or emulator

**Physical phone**

1. Enable **Developer options** (tap *Build number* 7 times).
2. Turn on **USB debugging**, connect the phone, and accept the prompt.
3. Press **Run** in Android Studio, or run:

   ```bash
   ./gradlew :app:installDebug
   ```

**Emulator**

1. Open **Device Manager → Create device**.
2. Pick a phone and a system image **with Google Play**, so Google sign-in works.
3. Sign in to a Google account on the emulator, then run the app.

**First run checklist**

1. Sign in with Google.
2. Add a task with the **Add task** button.
3. Open the task, set a due date and time, and choose a reminder.
4. Allow notifications when asked.
5. Open **Settings → Reminder health** and make sure every item shows a tick. If "Alarms & reminders" is off, reminders may arrive late.
6. Optional: long-press the home screen and add the **Today** widget.

### Using the local Firebase emulators (no real data)

```bash
firebase emulators:start --only auth,firestore
./gradlew :app:installDebug -Pticktodo.useEmulator=true
```

The Android emulator reaches your computer at `10.0.2.2`. On a physical phone:

```bash
adb reverse tcp:8080 tcp:8080
adb reverse tcp:9099 tcp:9099
./gradlew :app:installDebug -Pticktodo.useEmulator=true -Pticktodo.emulatorHost=127.0.0.1
```

---

## 4. Running tests

| What | Command | Needs |
|---|---|---|
| Unit and Robolectric tests (domain logic, Room, sync engine, UI components) | `./gradlew :app:testDebugUnitTest` | JDK only |
| Android Lint (correctness and security checks) | `./gradlew :app:lintDebug` | JDK only |
| Firestore security rules tests | `cd firebase/rules-tests && npm install && npm test` | Node 18+, JDK 21+ for the emulator (set `JAVA_HOME` to it) |

Last run (2026-10-21):
- **Unit and UI tests:** 99/99 pass (`./gradlew :app:testDebugUnitTest`).
  - Sync engine: 15, including two simulated devices editing the same task offline, deletion propagation, account isolation and failure handling.
  - Repository and celebrations: 13; database migrations 1→4: 1.
  - Focus engine: 12.
  - Recurrence, validation, conflicts, reminders, grouping and manual order: 26.
  - Theme maths, appearance settings, rendered screenshots, task card, layouts (small phone, tablet landscape, 2× font): 32.
- **Rules tests:** 23/23 pass (`cd firebase/rules-tests && npm test`).
- **Lint:** 0 errors.

Test reports:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`

---

## 5. Building a release (.aab) for Google Play

### 5.1 Create an upload key (once)

Keep this key outside the repository and back it up safely. If you lose it, you have to ask Google for an upload key reset.

```bash
keytool -genkeypair -v -keystore ../keys/ticktodo-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

### 5.2 Tell Gradle where it is

Copy [`keystore.properties.example`](keystore.properties.example) to `keystore.properties` (git-ignored) and fill it in. In CI, set the `TICKTODO_KEYSTORE_FILE`, `TICKTODO_KEYSTORE_PASSWORD`, `TICKTODO_KEY_ALIAS` and `TICKTODO_KEY_PASSWORD` environment variables instead (see [`.env.example`](.env.example)).

### 5.3 Set the version and links

In [`gradle.properties`](gradle.properties):

```properties
ticktodo.applicationId=com.yourcompany.ticktodo
ticktodo.versionCode=1        # increase by 1 for every upload
ticktodo.versionName=1.0.0
ticktodo.privacyPolicyUrl=https://your-site.example/privacy
ticktodo.termsUrl=https://your-site.example/terms
ticktodo.supportEmail=support@your-domain.example
```

### 5.4 Build

```bash
./gradlew :app:bundleRelease
```

The bundle is written to `app/build/outputs/bundle/release/app-release.aab`. Release builds are minified and resource-shrunk with R8.

### 5.5 After the first upload

1. In Play Console, open **Setup → App signing**.
2. Copy the **App signing key** SHA-1 and SHA-256.
3. Add both to your Firebase Android app. Without them, Google sign-in fails in the Play-installed build.

> New personal developer accounts must run a **closed test with at least 12 testers for 14 consecutive days** before production access. See `docs/research/policy-and-legal-research.md`.

---

## 6. Project structure

```
.
├── app/
│   ├── build.gradle.kts            App module build: signing, R8, optional Firebase plugin
│   └── src/
│       ├── main/java/app/ticktodo/
│       │   ├── domain/             Pure Kotlin: models, recurrence, validation, conflict policy, focus engine
│       │   ├── data/local/         Room database, entities, DAOs
│       │   ├── data/remote/        RemoteStore interface + Firestore implementation
│       │   ├── data/auth/          Google sign-in (Credential Manager) + Firebase Auth
│       │   ├── data/repository/    TaskRepository: all local-first reads and writes
│       │   ├── data/settings/      DataStore preferences
│       │   ├── sync/               SyncEngine, SyncWorker, scheduling, status
│       │   ├── reminders/          Alarms, notifications, boot/time-change handling
│       │   ├── focus/              Focus timer: session runtime, alarms, notification controls
│       │   ├── account/            Sign-out, account switching, account deletion
│       │   ├── widget/             Five home-screen widgets (Glance) + list-widget configuration
│       │   ├── ads/                Ads integration point (disabled in v1)
│       │   ├── di/                 Hilt modules
│       │   └── ui/                 Compose screens, theme, navigation
│       ├── debug/                  Debug-only App Check provider, emulator network config
│       ├── release/                Play Integrity App Check provider
│       └── test/                   Unit and Robolectric tests
├── firebase/
│   ├── firestore.rules             Per-user security rules
│   ├── firestore.indexes.json
│   └── rules-tests/                Rules tests against the emulator
├── docs/                           Architecture, sync, security, legal drafts, release checklist
├── gradle/libs.versions.toml       All dependency versions in one place
├── firebase.json
├── keystore.properties.example
└── .env.example
```

---

## 7. How it works

- **Local first:** every change is written to Room right away and marked `dirty`. The UI reads only from Room, so the app behaves the same online and offline.
- **Sync:** WorkManager runs `SyncWorker` when there is a network connection: shortly after each change, when the app opens, and every 12 hours. Runs triggered by opening the app or editing push local changes immediately but only *pull* other devices' changes if the last pull is older than 10 minutes; "Sync now", pull-to-refresh and sign-in always pull.
- **Firebase plan:** the app is designed to run on the free **Spark** plan. Nothing it uses needs Blaze (no Storage, Functions or Hosting). Firestore's free daily quota (50,000 reads, 20,000 writes, 20,000 deletes, 1 GiB) is shared by all users of the project; a busy day costs a device about 26 reads and 6 writes, so the quota covers roughly 1,500 daily active devices. If it is ever exhausted, Firestore refuses requests until the daily reset (midnight Pacific): the app keeps working offline, shows "Daily sync limit reached", and syncs again the next day. Play Integrity (App Check) has its own free quota of 10,000 attestations per day; request an increase in the Play Console once the app is live if daily active users approach ~1,000.
  - Each run pulls remote changes since a stored cursor, then pushes each dirty row in a check-and-write transaction.
  - Runs survive process death and reboot, retry with exponential backoff, and are idempotent.
- **Conflicts:** last writer wins per task, based on the time of the edit.
  - If two devices edit the same task offline, the later edit is kept in full and the other is discarded.
  - Deleting is an edit too, so a later restore beats an earlier delete.
  - Two devices completing the same repeating task create the *same* next occurrence, not duplicates.
  - Full details are in `docs/sync-design.md`.
- **Isolation:**
  - Firestore paths are `users/{uid}/…`, and the rules allow only the owner.
  - Every local row and query is scoped by uid.
  - Local data is wiped on sign-out, and rows from another account are removed on sign-in.
- **Reminders:**
  - Exact alarms are used when the user allows "Alarms & reminders"; otherwise inexact alarms.
  - Alarms are re-created after reboot, app update, and time or time-zone change.
  - Settings → **Reminder health** shows what may block reminders and links to the fix.
  - Lock-screen notifications hide the task title.
- **Account deletion:** Settings → Delete account asks you to confirm your Google account. It then deletes all Firestore data, the Firebase user and the local data.

---

## 8. Configuration reference

| Setting | Where | Default | Purpose |
|---|---|---|---|
| `ticktodo.applicationId` | `gradle.properties` | `com.ferrabytes.ticktodo` | Play package name. Fixed once the app is on Play. |
| `ticktodo.versionCode` / `versionName` | `gradle.properties` | `1` / `1.0.0` | App version |
| `ticktodo.privacyPolicyUrl` / `termsUrl` / `supportEmail` | `gradle.properties` | empty | Shown in the app when set |
| `ticktodo.useEmulator` | `-P` flag | `false` | Debug builds use the local Firebase emulators |
| `ticktodo.emulatorHost` | `-P` flag | `10.0.2.2` | Emulator host address |
| `app/google-services.json` | file (git-ignored) | absent | Enables Firebase |
| `keystore.properties` | file (git-ignored) | absent | Release signing |

**Never commit** `google-services.json`, `keystore.properties`, `*.jks` or any service-account key. `.gitignore` already excludes them.

---

## 9. Fixing "Sync was refused" or other sync errors

The app now says exactly what went wrong, with **Retry** and **Details** buttons. Debug builds also show the developer fix. Your changes always stay on the device until sync succeeds.

| Message in the app | Cause | Fix |
|---|---|---|
| **Sync was refused by the server** | Firestore returned `PERMISSION_DENIED`. A new database in production mode denies everything until the rules are deployed. Also happens if App Check enforcement is on and this build is not registered. | Deploy the rules (below). If you enabled App Check enforcement, register the debug token from Logcat or turn enforcement off while testing. |
| **Cloud storage is not set up yet** | The Firebase project has no Cloud Firestore database (`NOT_FOUND`). | Firebase console → **Firestore Database → Create database** (production mode, pick a region), then deploy the rules. |
| **Sign-in expired** | The Firebase session was revoked. | Sign out and sign in again. |
| **Some changes could not be saved** | The deployed rules are older than the app (for example, missing the `focusPlan` field). | Deploy the rules from this repository again. |

**Deploy the rules and indexes** (once per Firebase project, and again whenever `firebase/firestore.rules` changes). `.firebaserc` is git-ignored; create it with `firebase use --add` (or copy `.firebaserc.example`) so it points at your project and is git-ignored:

```bash
cd firebase/rules-tests
npm install
npx firebase login
npm run deploy
```

Then open the app and tap **Retry**, or pull down on any list.

Before deploying changed rules, run the rules tests locally. Java must be on your PATH.

```bash
cd firebase/rules-tests
npm test
```

---

## 10. Troubleshooting

| Problem | Fix |
|---|---|
| "Firebase not configured" on the sign-in screen | `app/google-services.json` is missing. Add it and sync Gradle. |
| Google sign-in shows an error or closes immediately | Add the SHA-1/SHA-256 of the key that signed the build to Firebase, then download `google-services.json` again. |
| "No Google account is available" | Add a Google account in device settings. On an emulator, use an image with Google Play. |
| Sync shows "needs attention" | Check that the rules are deployed (`firebase deploy --only firestore`) and, if App Check is enforced, that a debug token is registered. |
| Reminders arrive late | Settings → Reminder health: allow notifications and "Alarms & reminders". If reminders are still late, set Tick ToDo's battery usage to Unrestricted in Android settings. |
| `SDK location not found` | Create `local.properties` with `sdk.dir=…` (see section 1). |
| Gradle runs out of memory | Increase `org.gradle.jvmargs` in `gradle.properties`. |

---

## 11. Documentation index

| Document | What it covers |
|---|---|
| `docs/research/competitor-analysis.md` | Competitor matrix and design decisions |
| `docs/research/policy-and-legal-research.md` | Play, Firebase, AdMob and Indian/GDPR requirements (for legal review) |
| `docs/release/store-listing.md` | Play Store title, short and full description, screenshot captions |

| `docs/architecture.md` | Modules, data flow, state management, DI, build variants |
| `docs/sync-design.md` | Offline-first sync: bookkeeping columns, pull/push, conflicts, scheduling |
| `docs/security/threat-model.md` | Assets, trust boundaries, threats and mitigations, permissions |
| `docs/legal/privacy-policy.md` | Privacy policy draft (placeholders must be filled before publishing) |
| `docs/release/data-safety.md` | Play Console Data safety form answers |
| `docs/release/release-checklist.md` | Step-by-step release procedure |
| `docs/release/signing.md` | Upload key creation, storage and verification |
| `docs/release/launch-readiness.md` | Verification status of every launch criterion |
| `docs/known-limitations.md` | What the app deliberately does not do yet |

---

## License

Proprietary. All rights reserved by the project owner (owner details to be added). Third-party components keep their own licences; see the in-app **Open-source licences** screen.
