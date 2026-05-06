# SimpleBackup

A native Android (Kotlin + Jetpack Compose) app that backs up SMS, MMS text bodies, and call-log entries for selected contacts into a single self-viewable HTML file (with a JSON sibling) in the app's private storage. Subsequent runs merge into the same file. The app includes an in-app WebView to view the generated HTML, and a Share button to send the file out.

Restore is out of scope.

## Build

```
./gradlew assembleDebug
```

APK appears at `app/build/outputs/apk/debug/app-debug.apk`.

For a signed release APK, generate a keystore with `keytool -genkeypair -keystore simplebackup.keystore -alias key0 -keyalg RSA -keysize 2048 -validity 10000` and run:

```
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file=$PWD/simplebackup.keystore \
  -Pandroid.injected.signing.store.password=YOUR_PASS \
  -Pandroid.injected.signing.key.alias=key0 \
  -Pandroid.injected.signing.key.password=YOUR_PASS
```

## Test

```
./gradlew test
```

JVM unit tests cover the pure-Kotlin core: data model + JSON round-trip, phone-number normalization, dedupe + merge, HTML generator, and the backup orchestrator.

## Permissions

- `READ_SMS` — SMS and MMS bodies
- `READ_CALL_LOG` — call entries
- `READ_CONTACTS` — contact names (the picker still works without this, just shows numbers)

## Project layout

```
app/src/main/java/com/simplebackup/app/
├── core/         — pure-Kotlin data model, phone normalization, merge
├── data/         — ContentResolver readers (SMS/MMS/CallLog), Contacts, DataStore
├── html/         — HTML generator + gzip-base64 compression
├── backup/       — orchestrator, progress sealed class
├── ui/           — Compose screens (onboarding, home, picker, viewer, settings)
│   ├── home/
│   ├── picker/
│   ├── viewer/
│   ├── settings/
│   ├── onboarding/
│   ├── permissions/
│   └── theme/
├── AppContainer.kt          — manual DI singleton
└── SimpleBackupApplication.kt
app/src/main/assets/viewer_template.html — in-app and shared HTML viewer
```

Design and implementation plan live in `docs/plans/`.

## Legacy desktop tool

The original Python desktop converter (`sms_processor.py`) for the SMS Backup & Restore XML format lives in `legacy/`. It's still functional and can be used as a fallback; the new Android app supersedes it for capture-from-device workflows.
