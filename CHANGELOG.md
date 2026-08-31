# Changelog

## 1.0.3-alpha.4 - 2026-09-01

- Discover notification source apps from notifications already visible when Android connects
  TxnSheet's listener, without reading their text before the owner enables the source.
- Fixed the empty source-picker dead end after notification access was granted.

## 1.0.2-alpha.3 - 2026-09-01

- Changed workbook setup to Google's documented minimal create request followed by an
  atomic structural batch update for the Transactions, Dashboard, and Config tabs.
- Added actionable messages for missing accounts, network failures, OAuth identity
  mismatches, denied consent, and missing `drive.file` permission.

## 1.0.1-alpha.2 - 2026-08-26

- Fixed Google Sheets creation failing with `SHEETS_HTTP_400` on device locale or
  timezone identifiers that the Sheets API does not accept during spreadsheet creation.
- Improved safe Google API error codes for future setup diagnosis.

## 1.0.0-alpha.1 - 2026-08-26

First Nothing Phone (1) device-test prerelease.

- Native Kotlin and Jetpack Compose interface with edge-to-edge layouts.
- Default-deny notification source capture with deterministic transaction parsing.
- Encrypted, expiring review payloads backed by Android Keystore.
- Room-backed local ledger, manual/share import preview, review, rules, and diagnostics.
- Serialized WorkManager sync to a per-file Google Sheet using ephemeral authorization.
- UUID reconciliation, schema validation, capped retry, correction, reconnect, and revoke flows.
- Debug build verified by unit tests, Android lint, APK signature inspection, and SHA-256.

This prerelease is debug-signed and is not a production distribution.
