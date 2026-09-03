# TxnSheet 2.0.0 — local dashboard

TxnSheet no longer requires a Google account or Google Sheets. This build keeps the
ledger and finance dashboard on the device.

## Highlights

- Local monthly dashboard with net balance, income, expenses, savings rate, and activity.
- Updated light/dark UI using the supplied Figma design tokens and screen hierarchy.
- Google authorization, Sheets/Drive access, background sync, and internet permission removed.
- Existing confirmed transactions migrate to local-only records during upgrade.
- Notification capture, review, search, manual entry, category rules, and encrypted review text remain.

## Install

Download `TxnSheet-2.0.0-local-dashboard-debug.apk`. Android may show a sideloading or
Play Protect warning because this is a debug-signed GitHub device-test APK. After opening
the app, grant notification access and enable the transaction source apps you trust.

SHA-256:
`3E070E2BDD6DE634D8C83CFD08330C28C655559E4F9D6F7448A847254442C5C4`
