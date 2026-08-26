# TxnSheet 1.0.0-alpha.1 — Nothing Phone (1) device test

This is a debug-signed prerelease for installation and physical-device validation.
It is not an owner-signed production build.

## Install

1. On the Nothing Phone (1), expand **Assets** below.
2. Download `TxnSheet-1.0.0-NothingPhone1-debug.apk`.
3. Open the download and allow that browser or file manager to install unknown apps
   if Android requests it.
4. Confirm the installation. A Play Protect warning can appear because this APK is
   sideloaded and signed with an Android development certificate.
5. Open TxnSheet and review the in-app disclosure before enabling notification access.

## Verified build

- Package: `app.txnsheet.personal.debug`
- Version: `1.0.0-debug` (`versionCode 1`)
- Minimum Android: 8.0 / API 26
- Target Android: API 36
- Signing: Android debug certificate, APK Signature Scheme v2
- Certificate SHA-1: `73:B1:90:49:50:FF:BF:60:0C:D2:C1:55:0A:D7:CB:9A:81:51:86:A7`
- APK SHA-256: `9DE49CE936497F62BA93E62B716AF9A295DE0E0C27648C3CD554A71EA63425A7`
- Automated verification: 31 unit tests passed; Android lint reported 0 errors

Use `SHA256SUMS.txt` from this release to verify the download.

## Google Sheets setup

Google authorization requires the owner to enable the Google Sheets and Drive APIs
and register an Android OAuth client for the exact debug package and certificate
SHA-1 above. No OAuth client secret belongs in the APK.

The app requests only `drive.file`. It can create its own workbook or use a workbook
already granted to the app. Linking an arbitrary existing Drive spreadsheet is not
advertised in this build because that needs an explicit Google Picker grant.

## Scope and limitations

- Local capture, parsing, review, manual entry, rules, diagnostics, and encrypted
  retention are included.
- Notification access is optional and source apps are disabled until explicitly enabled.
- No SMS permission is requested and raw notification text is never sent to Sheets.
- Updates must be signed by the same key; this debug key is not the future production key.
- The unsigned release APK and AAB are intentionally not attached.
- Owner signing, hosted policy pages, live OAuth checks, and the physical-device matrix
  in `docs/RELEASE_CHECKLIST.md` remain required before a production release.

Nothing Phone (1) has completed its announced security-support window. Because this
app handles financial data, prefer a currently supported Android device for long-term use.
