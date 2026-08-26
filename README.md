# TxnSheet for Android

TxnSheet turns transaction notifications selected by the owner into a private,
structured Google Sheets ledger. Parsing happens deterministically on the phone;
only normalized ledger fields are sent to the selected spreadsheet.

This repository contains the native Kotlin/Jetpack Compose implementation of TxnSheet.
It does not request SMS permissions and does not upload raw notification text.

## Install on Nothing Phone (1)

The current GitHub prerelease is a debug-signed device-test build, not an owner-signed
production release.

1. On the phone, open the repository's [Releases](../../releases) page.
2. Open `v1.0.1-alpha.2` and download
   `TxnSheet-1.0.1-NothingPhone1-debug.apk` from **Assets**.
3. If Android asks, allow the browser or file manager to install unknown apps, then
   confirm the installation. A Play Protect warning is normal for a sideloaded debug APK.
4. Open TxnSheet and review its disclosure before granting notification access.

For Google Sheets authorization, the Google Cloud project must have an Android OAuth
client for package `app.txnsheet.personal.debug` and the debug certificate SHA-1 shown
in the prerelease notes. Without that owner setup, local capture, review, and manual
entry work, but Google authorization will not.

## Privacy and safety properties

- Notification capture is default-deny. A newly discovered source app remains disabled
  until the owner explicitly enables it.
- OTP, verification, promotional, balance-only, conflicting, and low-confidence messages
  are rejected or held for review by deterministic rules.
- Review-only source text is encrypted with an Android Keystore AES-256-GCM key and is
  automatically expired. It is never written to Google Sheets or diagnostics.
- Transactions are stored locally before work is scheduled. A stable UUID and a remote
  UUID lookup prevent retry-created duplicate rows.
- Google access uses the per-file `drive.file` scope. Access tokens are short-lived and
  are never stored in Room, DataStore, WorkManager input, or logs.
- Sync fails closed if the workbook header or schema version has changed.
- Backups are disabled and cleartext network traffic is blocked.

## Build

Requirements:

- Android Studio with JDK 17 or newer
- Android SDK Platform 36.1 and Build Tools 36.0.0+
- Gradle 9.5 (the checked-in wrapper downloads the matching distribution)

From PowerShell:

```powershell
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Google Cloud setup

Before an owner-signed release can authorize Google:

1. Create or select a Google Cloud project.
2. Enable the Google Sheets API and Google Drive API.
3. Configure the OAuth consent screen with the product privacy policy and support contact.
4. Create an Android OAuth client for package `app.txnsheet.personal` and the SHA-1 of the
   final release or Play App Signing certificate.
5. If a debug build is used for development, create a separate client for
   `app.txnsheet.personal.debug` and its debug certificate SHA-1.

No OAuth client secret belongs in the APK.

## Owner-signed release

Keep the release keystore outside this repository and provide these environment variables:

```text
TXNSHEET_KEYSTORE_PATH
TXNSHEET_KEYSTORE_PASSWORD
TXNSHEET_KEY_ALIAS
TXNSHEET_KEY_PASSWORD
```

Then run:

```powershell
./gradlew clean testReleaseUnitTest lintRelease bundleRelease assembleRelease
```

See [the release checklist](docs/RELEASE_CHECKLIST.md) before distributing the build.

## Repository status

This private repository and its binary artifacts are all-rights-reserved. The first
GitHub binary is deliberately marked as a prerelease until owner signing, Google OAuth,
privacy-policy hosting, and physical-device checks are complete.

## Architecture

- `capture/`: notification listener and minimal extraction
- `parsing/`: bounded, explainable transaction parsing and duplicate fingerprints
- `data/local/`: Room entities and atomic local state transitions
- `security/`: Android Keystore encryption for review payloads
- `data/remote/` and `sync/`: ephemeral Google authorization, schema validation, and
  serialized WorkManager uploads
- `ui/`: edge-to-edge Compose interface with accessible review and setup flows
