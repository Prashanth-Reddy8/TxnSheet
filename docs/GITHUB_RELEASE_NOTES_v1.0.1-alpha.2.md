# TxnSheet 1.0.1-alpha.2 — Google Sheets setup fix

This device-test update fixes Google Sheet creation failing with
`SHEETS_HTTP_400` after successful Google authorization.

## Install the update

1. Download `TxnSheet-1.0.1-NothingPhone1-debug.apk` from **Assets** below.
2. Open it and choose **Update**. You do not need to uninstall version 1.0.0.
3. Reopen TxnSheet and select **Google Sheet → Create private ledger**.

The package and debug signing certificate are unchanged, while `versionCode` has
increased from 1 to 2 so Android can install this over the existing build.

## Fix

- Spreadsheet creation now avoids forcing locale and timezone identifiers in the
  initial Google Sheets request. Google supports only a subset of identifiers and
  rejected the complete request with HTTP 400 on the affected device.
- TxnSheet continues to store `Asia/Kolkata` in the protected Config tab and uses it
  when converting transaction timestamps.
- Safe Google API status and reason codes are now preserved in setup errors.

## Verified build

- Package: `app.txnsheet.personal.debug`
- Version: `1.0.1-debug` (`versionCode 2`)
- Minimum Android: API 26
- Target Android: API 36
- Signing: same Android debug certificate, APK Signature Scheme v2
- Certificate SHA-1: `73:B1:90:49:50:FF:BF:60:0C:D2:C1:55:0A:D7:CB:9A:81:51:86:A7`
- APK SHA-256: `C52264DE9B3F2708F053EF6A55E3CAD44D6DD1743C1A5B4CAC8B84ECE786DBE3`
- Verification: unit tests, Android lint, assembly, manifest inspection, and APK
  signature verification passed

This remains a debug-signed device-test build, not an owner-signed production release.
