# Play Console "Data safety" answers

Based on the behaviour of the current build (`com.ferrabytes.ticktodo`).
Re-check this file whenever an SDK is added or a permission changes.

## Overview questions

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (TLS to Firebase; cleartext disallowed in release) |
| Do you provide a way for users to request that their data is deleted? | **Yes** – in-app account deletion (Settings → Delete account) |

## Data types

| Category | Data type | Collected | Shared | Processed ephemerally | Required/optional | Purpose |
|---|---|---|---|---|---|---|
| Personal info | Name | Yes | No | No | Required for sign-in | App functionality (shown in Settings) |
| Personal info | Email address | Yes | No | No | Required for sign-in | App functionality, account management |
| Personal info | User IDs | Yes | No | No | Required | App functionality, account management |
| Personal info | Photos (profile picture URL) | Yes | No | No | Required for sign-in | App functionality |
| App activity | Other user-generated content (tasks, notes, tags, lists) | Yes | No | No | Required | App functionality (sync across devices) |
| App activity | App interactions (Firebase Analytics automatic events) | Yes | No | No | Required (cannot be turned off in-app) | Analytics |
| App info & performance | Crash logs / diagnostics | **No** (no crash SDK) | No | – | – | – |
| Device or other IDs | Device or other IDs (Firebase app-instance id, Play Integrity signal) | Yes | No | No | Required | Analytics, fraud prevention / security |
| Location | – | No | No | – | – | – |
| Financial info | – | No | No | – | – | – |
| Health & fitness | – | No | No | – | – | – |
| Contacts, Calendar, Messages, Files, Audio | – | No | No | – | – | – |

"Shared" is **No** for all types: data goes only to Google services acting as
processors for this app (Firebase Auth, Firestore, Analytics, App Check), which
Play treats as service providers, not third-party sharing.

## Security practices

* Encrypted in transit: yes.
* Deletion request mechanism: in-app account deletion; also e-mail support
  (`SUPPORT_EMAIL` BuildConfig).
* Independent security review: no.

## Notes for the reviewer

* No `AD_ID` permission (explicitly removed with `tools:node="remove"`).
* Reminders, focus statistics and appearance settings never leave the device.
* Backups: the database and auth storage are excluded from Android auto backup
  (`res/xml/data_extraction_rules.xml`).
