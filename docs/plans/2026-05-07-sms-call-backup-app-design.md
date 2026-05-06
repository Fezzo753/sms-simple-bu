# SimpleBackup — SMS & Call-Log Backup App (Design)

**Date:** 2026-05-07
**Status:** Approved, ready for implementation plan
**Replaces / complements:** the existing `sms_processor.py` desktop converter (kept as legacy fallback for raw SMS Backup & Restore XML files; not deleted)

## Goal

A simplified, idiot-proof Android app that replicates the *capture* half of SMS Backup & Restore. The user picks contacts, taps one button, and gets a single self-viewable HTML backup file (plus a JSON sibling) of all SMS, MMS text bodies, and call-log entries for those contacts. Subsequent runs merge into the same files. The app includes an in-app viewer that uses the same HTML.

Restore is explicitly out of scope.

## Non-goals

- Restoring messages back to a phone.
- Cross-platform support (Android only — iOS does not expose third-party SMS access).
- Play Store distribution (sideloaded APK only — Play Store would require a permissions exception process).
- MMS image/video media in v1 (text bodies only; media replaced with `[image]` placeholder).
- Cloud sync, scheduled auto-backup, multi-device aggregation, multiple named backups per device.

## Decisions taken (with rationale)

| # | Decision | Rationale |
|---|---|---|
| 1 | **Sideloaded APK**, no Play Store | Avoids Google's SMS/CallLog permission-exception review. |
| 2 | **Self-contained HTML + JSON sibling**, single shared file per device | Same shareability as the existing `SMS_Viewer.html`; JSON is canonical for re-merge. |
| 3 | **One global backup file per phone** (not named/per-contact) | User said "merges with the same file" — simplest mental model. |
| 4 | **App-private storage + Share button** (not public Downloads) | User chose simplest permissions; trade-off documented as on-screen warning. |
| 5 | **Hybrid responsive HTML viewer** (desktop 2-pane / phone 1-pane) | Same source for in-app and shared file; current viewer is desktop-cramped on phones. |
| 6 | **One-tap home with sticky contact selection** | Editable but invisible after first run; matches "idiot-proof" requirement. |
| 7 | **Calls inline in single timeline + filter chips** (All / Messages / Calls) | Forensic / legal review wants chronological story per contact; chips let calls be isolated when needed. |
| 8 | **All-time scope** with optional date filter in Settings | Default matches 99% of users; legal scope-bounding is one settings screen away. |
| 9 | **Native Kotlin + Jetpack Compose** | Best fit for SMS/CallLog ContentResolver APIs; smallest APK; no abstraction tax. |

## Architecture

**Stack:** Kotlin, Jetpack Compose, single-Activity, MVVM, Coroutines, DataStore for prefs, WorkManager (optional, for backup off the UI thread), `libphonenumber` for E.164 normalization.

**System content providers consumed:**
- `Telephony.Sms.CONTENT_URI` — SMS rows
- `Telephony.Mms.CONTENT_URI` + `Telephony.Mms.Part.CONTENT_URI` — MMS rows + text parts
- `CallLog.Calls.CONTENT_URI` — calls
- `ContactsContract` — for the contact picker only

**Required permissions** (runtime, requested with rationale):
- `READ_SMS` (for SMS + MMS)
- `READ_CALL_LOG`
- `READ_CONTACTS`

**Files written (all in `Context.filesDir`):**

```
filesDir/
├── Backup_<E.164phone>.json    canonical data
├── Backup_<E.164phone>.html    regenerated each backup from JSON
└── datastore/                  selected contacts, last-run, settings
```

## Data model

**JSON schema (v1):**

```json
{
  "version": 1,
  "device_phone": "+15551234567",
  "generated_at": 1715000000000,
  "contacts": {
    "+15559876543": {
      "name": "Alice",
      "first_seen": 1690000000000,
      "last_seen": 1714000000000
    }
  },
  "events": [
    {
      "kind": "sms",
      "addr": "+15559876543",
      "date": 1714000000000,
      "type": 1,
      "body": "...",
      "read": true
    },
    {
      "kind": "mms",
      "addr": "+15559876543",
      "date": 1714000000000,
      "type": 2,
      "body": "...",
      "read": true,
      "parts": ["text/plain"]
    },
    {
      "kind": "call",
      "addr": "+15559876543",
      "date": 1714000000000,
      "type": 1,
      "duration_sec": 263
    }
  ]
}
```

`type` semantics:
- SMS/MMS: `1` = received, `2` = sent (matches `Telephony.TextBasedSmsColumns.MESSAGE_TYPE_*`)
- Call: `1` = incoming, `2` = outgoing, `3` = missed, `5` = rejected, `6` = blocked (matches `CallLog.Calls.*_TYPE`)

**Dedupe key:** `(kind, addr_e164, date_ms, type, body_or_duration)` — hashed into a `HashSet<Long>` (FNV-1a or `String.hashCode()` is fine; collisions are tolerable because only equal-key rows ever drop).

**Merge algorithm:**
1. Load existing JSON if present → build dedupe set from its `events`.
2. Open ContentResolver cursor for SMS, scoped to selected contact addresses (E.164-normalized; query uses an `OR`-joined `address` predicate matching all candidate formats per contact).
3. For each cursor row, compute key → if not in set, append to in-memory new-events list and add key to set.
4. Repeat for MMS and CallLog.
5. Concatenate `existing.events + new_events`, sort by `date` ascending, write back.
6. Regenerate the HTML file from the merged JSON.
7. Update `contacts` map's `first_seen` / `last_seen` per address.

The dedupe set lives only for the duration of the merge — cheap to rebuild on next run.

## Screens

1. **Onboarding** (first launch only) — purpose statement, runtime permission requests with rationales.
2. **Home** — phone number, last-backup time, message/call counts, contact chips, big "Back up now" button, View / Share buttons, app-private-storage warning, settings gear.
3. **Contact picker** — search, "Include all contacts" toggle, list defaulted to contacts with prior SMS/MMS/call history; "Show all contacts" link reveals the full address book; selection persists.
4. **Backup-in-progress** — modal sheet, two-line progress label per phase (SMS → MMS → calls → save), cancel button (clean rollback).
5. **Backup complete** — snackbar on home: "Added N new messages and M new calls."
6. **Viewer** — full-screen WebView loading the generated HTML.
7. **Settings** — phone-number override, optional "From date" filter, About, "Reset backup" (destructive, with confirm).

**Permissions failure modes:**
- SMS denied → backup button disabled; tap shows rationale screen.
- Contacts denied → app still works, picker shows numbers without names.
- Call log denied → call entries skipped; "Calls" filter chip greyed.

## HTML viewer template

Same dark-theme aesthetic as the existing `SMS_Viewer.html`; generated by Kotlin string-templating, not by the Python script.

**Layout breakpoints:**
- `≥ 800px` — unchanged 2-pane: contact sidebar + message pane.
- `< 800px` — single-pane navigation: contact list → tap → thread view with back arrow.

**New filter chips** above the thread:
```
[ All ]  [ Messages ]  [ Calls ]
```

**Call rendering** (centered system row, distinct from message bubbles):
```
─── 📞 Outgoing call · 4m 23s ───
       Mar 12, 2025 · 3:42 PM
```
Color-coded (green=outgoing, blue=incoming, red=missed/rejected). No duration for missed.

**Self-contained payload:** JSON gzipped + base64-embedded in a `<script>` tag, decompressed at load via `DecompressionStream` (same trick as `sms_processor.py`). Sibling JSON file is the *uncompressed* canonical version, used for the next merge cycle and any programmatic access.

**Header stats:** `Messages: N | Calls: M | Contacts: K | Period: YYYY-MM-DD – YYYY-MM-DD`.

**Retained from the existing tool:** full-text search, date filters, sent/received toggle, contact sidebar with per-contact counts, Print PDF, Export TXT, pagination (500 messages / 1000 calls per page).

## Testing strategy

**Unit (JVM):**
1. Dedupe-key correctness across phone-number format variants.
2. Merge correctness (overlap + new events → exact expected output).
3. JSON round-trip.
4. HTML template generation (assert payload + stats; snapshot test outer markup).
5. MMS body extraction across `mms` + `part` join.
6. Call type → label mapping.

**Instrumented (emulator/device):**
7. End-to-end with seeded `adb`-pushed rows: full backup → assert output JSON.

**Manual pre-ship verification:**
- Permission denial paths (each permission denied once).
- 5,000+ message contact (worst-case progress UX).
- Idempotent re-run (expect "Added 0 new").
- Dual-SIM device (phone-number override path).
- Generated HTML on desktop browser — looks like existing `SMS_Viewer.html`.
- Generated HTML in in-app WebView at 5" phone size — responsive collapse works.
- Uninstall + reinstall — clean "no backup" state on home.

## Open questions / future work

- **MMS media attachments.** v1 strips them; users wanting forensic-grade capture will need v2.
- **Phone-number normalization edge cases.** Short codes, international formats from contacts saved without `+`, alphanumeric senders. `libphonenumber` handles most; unrecognizable addresses fall through as raw strings (still dedupe-correct per their original form).
- **WebView performance** at >50,000 events. The existing tool's pagination handles this; will verify on device.
- **Restore.** Out of scope; if ever wanted, the JSON file is structured to make it easy.

## Repository state note

The project directory is **not currently a git repository**. Before implementation begins, recommend `git init` so the design doc and subsequent code are version-controlled. (No commit was made of this doc for the same reason.)
