# TxnSheet 2.1 — workbook dashboard

This update follows the Personal Finance Master Dashboard workbook using the local
Excel copy confirmed by the owner. Google Sheets is not required at runtime.

- Home: monthly income/spending/savings, six-month chart, category and merchant breakdowns,
  account activity, financing movements, and understandable action cards.
- Plan: budgets versus actuals, twelve-month cash forecast, EMI and debt details,
  payoff comparisons, savings goals, and editable planning assumptions.
- Activity: month/search filters, editable amounts, direction and methods; explicit
  self-transfer/refund/card settlement and essential/discretionary classification.
- Workbook import: choose an `.xlsx`, review counts, then import recognized tables.
  All original populated tabs, including legacy tables, remain in Workbook data.
- Capture: bundled Messages alerts are handled individually, bank names are separated
  from messaging apps, repeated alerts are suppressed, failed/pending transactions
  are excluded, and unknown Truecaller directions remain in Review.
- Merchant rules: conservative default mappings and remembered owner corrections.

## Install and use

Download `TxnSheet-2.1.0-workbook-dashboard-debug.apk` below. Install it over the previous
debug build to retain local data; do not uninstall first. The package is
`app.txnsheet.personal.debug`, version code 9. Android's existing notification access
and source choices should be checked after updating.

To load personal workbook values, put the `.xlsx` on your phone and use
**Settings → Import Excel file**. Your financial data is not embedded in the APK.
Legacy/formula-only cells remain in the reference viewer rather than being guessed
into transaction totals. Existing app records and plans are retained; review any
overlap between imported transactions and captured notification records.

## Verification and limits

81 unit tests passed, Android lint completed with 0 errors (18 warnings and 1 hint),
and the debug APK built successfully. The signing certificate matches the previous
2.0.0 debug APK, supporting an in-place update.
The six new database tables match the additive migration; all nine existing tables
are unchanged. No internet, SMS, contacts, or broad storage permission is requested.

The supplied workbook was also parsed directly: all 11 tabs were retained. Its
supported transaction, budget, debt and goal input tables supplied no concrete
records, so this file adds reference content without manufacturing dashboard totals.

This is a debug-signed device-test prerelease. No phone was connected for a hands-on
layout or notification-delivery check. Android may hide, delay, or suppress notifications,
so capture is not guaranteed at 100%. Cash/forecast/payoff figures depend on entered
assumptions and available records, and are not live bank balances.
