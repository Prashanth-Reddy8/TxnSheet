# TxnSheet for Android

TxnSheet turns transaction notifications selected by the owner into a private,
on-device finance dashboard. Parsing and storage stay on the phone; the app has no
Google Sheets integration and does not request internet or SMS permissions.

## What it does

- Captures completed transaction alerts from source apps the owner explicitly enables.
- Shows monthly income, expenses, net balance, savings rate, and recent activity.
- Holds incomplete or ambiguous alerts for review instead of guessing.
- Supports search, debit/credit/review filters, manual entry, category rules, and edits.
- Encrypts temporary review text with Android Keystore and expires it automatically.
- Provides light, dark, and system-aware native Android UI.
- Follows the Personal Finance Master workbook with budgets, twelve-month cash flow, EMI/debt
  records and payoff estimates, goals, configurable assumptions, and an action list.
- Breaks down spending by category, merchant, payment method, and account, with month navigation.
- Distinguishes self-transfers, ATM withdrawals, refunds, card purchases, and card bill settlements.
- Imports a chosen `.xlsx` locally with a preview and keeps every original tab in the workbook viewer.

## Bring your workbook into the app

Save the `.xlsx` on your phone, then open **Settings → Import Excel file**. Review the record counts
and tap **Import**. Completed Transactions, budgets with concrete dates, filled EMI Tracker rows,
and Goals become app records. All original populated cells, including legacy tabs, remain under
**Workbook data**. Formula cells without cached results remain formulas in that reference view;
the native app calculates its own dashboard from local records. Re-imports keep existing app plans
and matching records. Edited workbook transaction facts may become new records; overlapping
notification/import records should be checked in Activity.

## Capture accuracy

Only complete supported alerts enter totals automatically. Bundled MessagingStyle messages are
processed separately, pending/failed alerts are excluded, and ambiguous amounts or direction go
to Review. Merchant mappings prefer explicit owner rules. Unknown merchants remain uncategorized.
Android may withhold, redact, or delay notifications; no application can guarantee every payment
is observed. Diagnostics show actual local outcomes, not an invented accuracy percentage.

The app displays cash and payoff estimates from your inputs; it does not fetch bank balances.
No personal workbook values are included in the distributed APK.

## Install

Download the latest APK from [Releases](../../releases). A GitHub debug APK is intended
for device testing and may show Android sideloading warnings. A store-distributed,
owner-signed build is required to remove most installation friction.

After installation, open **Settings → Notification access** and enable TxnSheet. Inside
TxnSheet, enable only the source apps whose transaction alerts you want captured.

## Privacy and safety

- Notification capture is default-deny per source app.
- OTP, verification, promotional, balance-only, conflicting, and low-confidence alerts
  are rejected or held for review by deterministic rules.
- Confirmed transactions are stored only in the app's local Room database.
- Review source text is encrypted with AES-256-GCM and automatically expires.
- Android backups and cleartext traffic are disabled; the app declares no network access.
- Erase local data is available from Privacy settings.

## Build

Requirements: Android Studio/JDK 17, Android SDK 36.1, and Gradle 9.5.

```powershell
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

- `capture/`: notification listener and source allowlist
- `parsing/`: bounded, explainable transaction parsing and duplicate detection
- `data/local/`: Room-backed device ledger
- `security/`: Android Keystore encryption for temporary review payloads
- `ui/`: Jetpack Compose dashboard, activity, review, and settings flows

Legacy sync columns remain in the local database schema only so existing installations
can upgrade without losing transaction history; startup migrates confirmed records to
the local-only status and clears obsolete jobs.
