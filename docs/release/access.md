# Access and recovery record

Who can do what for Tick ToDo. Keep this current; it is the first thing to
check when someone leaves or a device is lost.

| System | Account | Role | Recovery |
|---|---|---|---|
| Google Play Console | ferrabyte512@gmail.com | Account owner (developer account "FerraByte") | Google account recovery; developer-account support |
| Firebase / Google Cloud project `tick-todo-2916c` | ferrabyte512@gmail.com | Owner | Personal account (the original creator) is to be removed once FerraByte access is confirmed — until then it is a second Owner |
| GitHub `ferrabyte512/ticktodo` (public) and GitHub Pages site | ferrabyte512 | Owner | GitHub account recovery; the repository holds no secrets |
| Upload keystore `ticktodo-upload.jks` + passwords | Whoever holds `E:\E\ticktodo-secrets` and the encrypted backups | — | Play App Signing: Google holds the app signing key; a lost upload key is reset from Play Console → App integrity |
| `google-services.json` | Re-downloadable from Firebase → Project settings by any Owner | — | Not secret in the strict sense (public Firebase config), but keep it out of Git |
| Firebase CLI (rule deployment) | ferrabyte512@gmail.com via `firebase login:add` | Owner | Any Owner can deploy |

## Encrypted backups

Created 2026-11-02 in `E:\E\ticktodo-backups\` with `gpg --symmetric
--cipher-algo AES256`:

| Archive | Contents |
|---|---|
| `ticktodo-signing-key-<date>.zip.gpg` | upload keystore + `keystore.properties` (passwords) |
| `ticktodo-release-set-<date>.zip.gpg` | the above plus `google-services.json`, the signed AAB/APK and `mapping.txt` |
| `SHA256SUMS.txt` | checksums of both archives |

The passphrase is in `E:\E\ticktodo-secrets\BACKUP-PASSPHRASE.txt`. **Move
it into a password manager and delete that file**, then copy the two
`.gpg` archives to at least one place off this PC (encrypted cloud drive or
an offline USB stick). Restore with:

```bash
gpg --decrypt ticktodo-release-set-<date>.zip.gpg > release-set.zip
```

Never put the passphrase, the keystore or `keystore.properties` in the
repository, an issue, chat or CI logs.
