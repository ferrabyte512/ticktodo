# Release checklist

Work through this top to bottom for every Play release. Statuses are recorded
in [launch-readiness.md](launch-readiness.md) for the current version.

## 1. Code

- [ ] `main` is green in CI (build, unit tests, lint, Firestore rules tests).
- [ ] `./gradlew :app:testDebugUnitTest :app:lintDebug` passes locally.
- [ ] No `TODO`/`FIXME` left in `app/src/main` that affects behaviour.
- [ ] `git ls-files | grep -iE 'jks|keystore.properties$|google-services.json|\.firebaserc'` prints nothing.
- [ ] `docs/known-limitations.md` is current.

## 2. Version

- [ ] Bump `ticktodo.versionCode` (integer, always increasing) and
      `ticktodo.versionName` in `gradle.properties`.
- [ ] Database schema: if `AppDatabase.version` changed, the new
      `app/schemas/.../N.json` is committed and the migration is additive
      (auto-migration) or has a tested `Migration`. Never `fallbackToDestructiveMigration`.
- [ ] Firestore rules: if the task/list document shape changed, rules and
      `firebase/rules-tests` are updated and **deployed before** the app ships
      (`cd firebase/rules-tests && npm test && npm run deploy`). Old app
      versions must still pass the new rules.

## 3. Firebase console

- [ ] Project stays on the **Spark** (free) plan: confirm *Usage and billing*
      shows Spark and no billing account is attached. Nothing in the app needs
      Blaze. Check *Firestore → Usage* occasionally: the free quota is
      50 K reads / 20 K writes / 20 K deletes per day for the whole project;
      when exhausted, sync pauses until the daily reset and the app says so.
- [ ] Android app in the project has the **Play App Signing** SHA-1 and
      SHA-256 fingerprints (Play Console → App integrity), in addition to the
      upload key's.
- [ ] Authentication: Google provider enabled, all others disabled,
      e-mail enumeration protection on.
- [ ] App Check: Play Integrity registered; Firestore enforcement is
      **Unenforced** until the Play-distributed build shows >95 % verified
      requests in App Check metrics, then **Enforced**. Play Integrity allows
      10,000 attestations/day by default (about one per device per hour of
      use); request a higher quota from Play Console → App integrity once the
      app is live and before daily active users approach 1,000.
- [ ] Firestore: point-in-time recovery or scheduled backups enabled.
- [ ] Google Cloud → Credentials: the Android API key is restricted to
      `com.ferrabytes.ticktodo` + the SHA-1 fingerprints.

## 4. Build

- [ ] `keystore.properties` present locally (or `TICKTODO_KEYSTORE_*` set) and
      points at the **upload** keystore; see [signing.md](signing.md).
- [ ] Build the release artefacts:

  ```bash
  ./gradlew clean :app:bundleRelease :app:assembleRelease
  ```

  Outputs: `app/build/outputs/bundle/release/app-release.aab` and
  `app/build/outputs/apk/release/app-release.apk`.
- [ ] Verify the AAB is signed with the upload key:

  ```bash
  keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
  ```
- [ ] Install the release APK on a real device over the previous version
      (`adb install -r`) and confirm existing tasks, lists and settings survive.
- [ ] Keep `app/build/outputs/mapping/release/mapping.txt` with the release
      (upload it to Play for readable crash traces).

## 5. Manual smoke test on the release build

- [ ] Sign in; tasks from the account appear; sync status "Last synced".
- [ ] Add, edit, complete, undo, delete, restore a task; counts update on
      Today, Matrix and Browse.
- [ ] Set a reminder 2 minutes ahead, background the app: it rings at the
      exact minute with the alarm tone; Complete and Snooze buttons work.
- [ ] Start a focus session, lock the phone, come back: timer is correct.
- [ ] Switch Light/Dark/System and two accents; status-bar icons stay readable.
- [ ] Sign out: no tasks visible; sign in: tasks return.
- [ ] Airplane mode: edits work, "Offline" strip shows, edits sync when back.

## 6. Play Console

- [ ] Store listing from `store-listing.md` (title, short/full description,
      screenshots in light and dark, feature graphic, icon).
- [ ] Data safety form from `data-safety.md`.
- [ ] Privacy policy URL points at the published version of
      `docs/legal/privacy-policy.md` and matches `ticktodo.privacyPolicyUrl`
      in `gradle.properties`.
- [ ] Content rating questionnaire completed.
- [ ] Target audience: 13+ (no children's features).
- [ ] Declarations: exact alarms (`SCHEDULE_EXACT_ALARM`) justified as
      "user-set reminders"; full-screen intent justified as "alarm/reminder".
- [ ] Internal testing track first; promote to production only after the
      App Check verified-rate check in §3.

## 7. After release

- [ ] Tag the commit: `git tag -a v<versionName> -m "Release <versionName>"`.
- [ ] Watch Play vitals (ANR/crash rate) and Firebase App Check metrics for
      48 hours before raising rollout percentage.
