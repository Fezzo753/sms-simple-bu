# Privacy Policy — Simple SMS Backup & Viewer

**Effective date:** 10 May 2026

This Privacy Policy explains how the Android application **Simple SMS Backup & Viewer** ("the app", package name `com.sms_export_viewer`) handles your information.

## Who provides the app

The app is published on Google Play by Fezzo753 (the "developer", "we", "us"). Source code is available at <https://github.com/Fezzo753/sms-simple-bu> under the MIT License.

## Plain-English summary

- The app runs entirely on your phone. It does **not** request internet access — `INTERNET` is not in its manifest, so the app physically cannot send your data to a server, to us, or to anyone else.
- We do not collect, transmit, sell, share, or have any access to your messages, call history, contacts, phone number, or any other information from your device.
- The data the app reads from your phone is written only to the app's private storage on the device itself, where only the app and you can reach it.
- There are no third-party SDKs, analytics, advertising, crash reporting, or tracking of any kind.
- Uninstalling the app deletes everything the app stored.

## What information the app reads, and why

The app needs three runtime permissions to perform its single function — creating an offline backup of the SMS, MMS, and call-log entries you choose, with contact names attached. Each permission is requested with a plain-language rationale on first launch.

| Permission | Why the app uses it | What it reads |
|---|---|---|
| `READ_SMS` | Capture SMS and MMS bodies for the contacts you select | Sender / recipient phone number, timestamp, message body text, read/unread state |
| `READ_CALL_LOG` | Capture call entries (date, duration, direction) for the contacts you select | Other party's phone number, timestamp, call type, duration |
| `READ_CONTACTS` | Display contact **names** in the backup instead of bare phone numbers; populate the contact picker | Contact display name and associated phone numbers |
| `READ_PHONE_NUMBERS` and `READ_PHONE_STATE` (optional) | Detect your device's SIM phone number, used to name backup files (e.g. `Backup_+15551234567.json`). You can override this in Settings if you'd rather not grant these | SIM line-1 phone number |

For MMS, the app captures only text content. Attached images, video, and audio are replaced with a `[image]` / `[video]` / `[audio]` placeholder and not extracted.

## Where the data goes

The app writes two files per device to its **private app storage** (`Context.filesDir`):

- `Backup_<phone>.json` — canonical structured data used to merge future runs.
- `Backup_<phone>.html` — a single self-contained viewer with the same data embedded.

This directory is sandboxed by Android: no other app can read it without root, and the data does not appear in your photo library, Files app shared storage, or any cloud backup unless you explicitly export it (see "Sharing" below).

The app also stores a small set of preferences (selected contact list, optional date filter, optional phone-number override, last-run timestamp) via Android's Jetpack DataStore. These never leave the device.

## Sharing

The app provides a **Share** button. When you tap it, Android's standard share sheet opens, and you choose where the backup file goes — email, cloud drive, messenger, file manager, etc. Sharing is always explicit: the app cannot share anything without your active tap. The recipient and what they do with the file is governed by *their* privacy policy, not ours.

The app also provides an in-app viewer with **Print** and **Export TXT** functions. Print uses Android's system PrintManager (Save as PDF, your installed printers, etc.). Export TXT writes a plain-text dump of the currently filtered events into your cache directory and opens the same share sheet so you can decide where it goes. Both are user-initiated.

## Data we do not collect

We do **not** collect:

- Your phone number, IMEI, IMSI, device ID, or advertising ID.
- Your messages, MMS attachments, call history, or contacts.
- Crash reports, analytics, telemetry, or usage statistics.
- Your IP address or any network identifier (the app does not have internet access).
- Your name, email, or any account information (there are no accounts).
- Location data.

## Retention and deletion

All data the app writes is stored on your device. To delete it:

- **Reset backup** in Settings deletes the JSON and HTML backup files.
- Uninstalling the app removes the app's private storage entirely (including DataStore preferences).
- Files you exported with Share / Export TXT live in whichever destination you chose; deleting them is up to you.

We have no remote copy of anything. There is no "request my data" or "delete my data" form because we do not hold any of your data on any server.

## Children

The app is not directed at children under 13. It does not knowingly collect data from anyone, including children.

## Third-party libraries

The app uses open-source libraries that run on-device only and have no network access of their own in our use:

- Jetpack Compose, AndroidX (UI)
- kotlinx.serialization (JSON encoding)
- Google libphonenumber (phone number normalization, runs locally)

We do not embed Google Analytics, Firebase, Crashlytics, Facebook SDK, AdMob, or any equivalent.

## Permissions you can deny

If you deny `READ_CONTACTS`, the app still works — backups will show phone numbers without names. Denying `READ_SMS` or `READ_CALL_LOG` disables backup of that category. Denying `READ_PHONE_NUMBERS` / `READ_PHONE_STATE` only affects auto-detection of your phone number for the backup file name; you can set it manually in Settings.

## Changes to this policy

If we change this policy, the updated version will be committed to the project repository, the **Effective date** at the top will be updated, and the new version will be referenced from the Play Store listing on the next app update. Substantive changes will also be summarised in the release notes for the corresponding app version.

## Contact

Questions about this policy, or about the app's data handling, can be raised by filing an issue at <https://github.com/Fezzo753/sms-simple-bu/issues>.
