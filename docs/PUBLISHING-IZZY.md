# Publishing to IzzyOnDroid

IzzyOnDroid is a third-party F-Droid repository run by Izzy ([@IzzySoft](https://github.com/IzzySoft)). It hosts the GitHub-Releases-signed APK directly (no rebuild on F-Droid's infrastructure), so updates show up in users' F-Droid clients shortly after each GitHub Release.

## Prerequisites

- ☑ MIT license at the repo root (`LICENSE`).
- ☑ fastlane metadata at `fastlane/metadata/android/en-US/`.
- ☑ At least one signed APK published as a GitHub Release asset.
- ☐ Optional but recommended: 2–4 phone screenshots in `fastlane/metadata/android/en-US/images/phoneScreenshots/` (PNG, ~1080×2400, named `01_home.png`, `02_picker.png`, etc.). The repo has the directory ready — just drop screenshots in.
- ☐ Optional: 512×512 launcher icon at `fastlane/metadata/android/en-US/images/icon.png`. If absent, IzzyOnDroid uses the APK's icon.

## Submission

1. Make sure v0.1.0 (or later) is published as a Release at <https://github.com/Fezzo753/sms-simple-bu/releases> with `SimpleBackup-vX.Y.Z.apk` attached.
2. Open <https://github.com/IzzyOnDroid/repo/issues/new?assignees=&labels=Inclusion&template=APP.yml&title=%5BInclusion%5D+%3CApp+name%3E>
3. Fill in the Inclusion form. Reference values:

| Field | Value |
|---|---|
| App name | `SimpleBackup` |
| Source code | `https://github.com/Fezzo753/sms-simple-bu` |
| Package name | `com.simplebackup.app` |
| Released APKs | `https://github.com/Fezzo753/sms-simple-bu/releases` |
| License | `MIT` |
| Categories | `Phone & SMS`, `System` |
| Anti-features (be honest) | `NonFreeNet` if any cloud calls (we have none — leave blank), otherwise none |

4. Submit. Izzy or one of the maintainers reviews — usually within a few days. They may ask for tweaks (e.g. screenshots, license clarification).
5. Once accepted, your app appears at `https://apt.izzysoft.de/fdroid/index/apk/com.simplebackup.app` and shows up in any F-Droid client that has the IzzyOnDroid repo added.

## After acceptance

Subsequent releases publish automatically: cut a new GitHub Release with the signed APK as `SimpleBackup-vX.Y.Z.apk`, and IzzyOnDroid's bot picks it up within ~24 hours. No further action needed.

If the package name or signing key ever changes, treat it as a new app — Izzy needs to be notified, and existing users will have to uninstall and reinstall.

## Adding the IzzyOnDroid repo on a phone (for users)

1. Install F-Droid client from <https://f-droid.org>.
2. Open F-Droid → Settings → Repositories → `+` → enter `https://apt.izzysoft.de/fdroid/repo`.
3. Update repos. SimpleBackup will appear under search.

## Why not F-Droid main?

F-Droid main builds from source on its own infrastructure and signs with its own key. It's the canonical FOSS Android store but the review queue can take weeks to months. IzzyOnDroid is faster and well-trusted; many apps live in both. Submitting to F-Droid main later is a separate process — see <https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/>.
