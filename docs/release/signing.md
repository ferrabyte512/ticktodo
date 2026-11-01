# Signing

Tick ToDo uses **Play App Signing**: Google holds the app signing key, and the
developer holds an *upload* key that signs the bundle sent to Play. Losing the
upload key is recoverable (Play Console → App integrity → request upload key
reset); losing `keystore.properties` only costs a reset.

## Files (never committed)

| File | Purpose |
|---|---|
| `keys/ticktodo-upload.jks` | Upload keystore. `keys/` and `*.jks` are git-ignored. |
| `keystore.properties` | Path, store password, key alias, key password. Git-ignored; template in `keystore.properties.example`. |

Keep an encrypted backup of both outside the repository (password manager +
offline copy). Do not paste passwords into chat, issues or CI logs.

## Creating the upload key (once)

```bash
mkdir -p keys
keytool -genkeypair -v -keystore keys/ticktodo-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Tick ToDo upload, O=Ferrabytes"
```

`keytool` prompts for the passwords; it does not echo them. Then copy
`keystore.properties.example` to `keystore.properties` and fill in the values.

Register the key's certificate with Play on first upload (Play Console →
App integrity → "Use a different upload key" or accept the one from the first
bundle), and add its SHA-1/SHA-256 to the Firebase Android app so sign-in
works for locally built release APKs:

```bash
keytool -list -v -keystore keys/ticktodo-upload.jks -alias upload | grep -E "SHA1|SHA256"
```

## How Gradle finds it

`app/build.gradle.kts` reads, in order:

1. `keystore.properties` at the repository root, or
2. environment variables `TICKTODO_KEYSTORE_FILE`, `TICKTODO_KEYSTORE_PASSWORD`,
   `TICKTODO_KEY_ALIAS`, `TICKTODO_KEY_PASSWORD` (used by CI secrets).

If neither is present the release build still succeeds but is **unsigned**;
the build log says so and Play will reject it. Debug builds use the standard
debug keystore.

## Verifying an artefact

```bash
keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab
```

The certificate's SHA-256 must match the upload certificate shown in Play
Console → App integrity.
