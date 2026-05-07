# Releasing SimpleBackup

SimpleBackup is sideload-only. Releases are signed APKs attached to GitHub Releases. Users download the APK, enable "Install unknown apps" for their browser/file manager, and tap to install.

There is no Play Store release path — the SMS/Call Log permissions that this app needs are subject to Google's Permissions Declaration review, which the app intentionally avoids.

## One-time setup

### 1. Generate a release keystore

Pick **one** of the two paths.

**Android Studio (UI):** Build → Generate Signed App Bundle / APK… → APK → Create new…

- Key store path: outside the project, e.g. `~/keystores/simplebackup.jks`
- Password: strong, write it down (e.g. in 1Password)
- Key alias: `simplebackup`
- Validity: 25 years
- First and Last Name (CN): `SimpleBackup`

**Command line:**

```bash
keytool -genkeypair -v \
  -keystore ~/keystores/simplebackup.jks \
  -alias simplebackup \
  -keyalg RSA -keysize 2048 -validity 9125 \
  -storetype PKCS12 \
  -dname "CN=SimpleBackup, OU=, O=, L=, S=, C=US"
```

> **Critical.** The same keystore must be reused for every future update. If you lose it, existing users have to uninstall/reinstall to upgrade.

Back up the `.jks` file *and* the password somewhere offline.

### 2. Add GitHub Actions secrets (only if you want CI-driven releases)

Settings → Secrets and variables → Actions → New repository secret. Add four:

| Name | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 ~/keystores/simplebackup.jks` (Linux) or `base64 -i ~/keystores/simplebackup.jks` (macOS) |
| `KEYSTORE_PASSWORD` | the password you set |
| `KEY_ALIAS` | `simplebackup` |
| `KEY_PASSWORD` | the key password (often same as keystore password) |

## Cutting a release

### Option A — automated (after the secrets are in place)

```bash
# bump versionCode (must increase) and versionName in app/build.gradle.kts
git commit -am "chore: bump to v0.1.1"
git tag -a v0.1.1 -m "v0.1.1"
git push origin main v0.1.1
```

The `Release APK` workflow runs, builds, signs, and creates a **draft** Release at `https://github.com/Fezzo753/sms-simple-bu/releases`. Edit the description, then click Publish.

### Option B — manual (Android Studio, no secrets needed)

1. In Android Studio: Build → Generate Signed Bundle / APK → APK → use your existing keystore → variant `release` → Finish.
2. Output: `app/build/outputs/apk/release/app-release.apk`. Optionally rename to `SimpleBackup-v0.1.0.apk`.
3. Tag and push:
   ```bash
   git tag -a v0.1.0 -m "v0.1.0 — first shippable build"
   git push origin v0.1.0
   ```
4. On GitHub: Releases → Draft a new release → choose tag `v0.1.0` → drag-drop the APK as an asset → Publish.

## What goes in release notes

Quick punchlist for the description field:

- One-line summary of the headline change.
- Bug fixes since last release (look at `git log --oneline <prev-tag>..HEAD`).
- Install instructions:
  > Download `SimpleBackup-vX.Y.Z.apk`. On your phone, open it from your file manager and approve "Install unknown apps" for the prompting source if asked. After install, grant SMS, Call Log, and Contacts permissions on first launch.
- Known limitations (e.g. MMS media currently captured as `[image]` placeholder; restore is out of scope).

## Versioning rules

- `versionName` is human-facing (e.g. `0.1.1`). Match the git tag.
- `versionCode` is an integer that **must** increase with every release Android sees. Don't recycle. If you ever publish, even informally, a v0.1.0 with versionCode 1, the next release must use 2.
