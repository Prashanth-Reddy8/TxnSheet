# TxnSheet privacy model

TxnSheet is an owner-controlled, local-first financial utility. It reads notifications only
after the owner grants Android notification access and only for source apps the owner enables.

## Data handled

For an enabled source, TxnSheet may temporarily handle the notification's visible text and
metadata in order to recognize a completed transaction. It derives ledger fields such as time,
amount, currency, direction, payment method, counterparty, category, institution, masked account
suffix, reference ID, optional balance, source app, confidence, parser rule, and notes.

Raw notification text is never uploaded. When an item needs review, a bounded
copy is encrypted on-device with Android Keystore AES-GCM and deleted at expiry or when the item
is resolved. Ignored messages are not retained as raw text. Diagnostics contain fixed event codes
and deliberately exclude transaction values, message bodies, and identifiers.

## Storage and deletion

Structured transactions, configuration, source choices, review state, and privacy-safe diagnostics
are stored in the app's private local database. Android backups are disabled. The owner can delete
individual transactions and clear all local app data. Clearing all data also removes the review encryption key.

Budgets, debt records, goals, planning assumptions, transaction classifications, and user-selected
Excel workbook snapshots are also stored locally. Imported workbook cells can include personal
text and remain available until the snapshot is replaced or all app data is erased. Workbooks are
chosen using Android's document picker; no broad storage permission is requested.

## Network use

TxnSheet declares no internet permission and contains no advertising SDK or analytics integration. A release operator must
update this document and the store disclosure before adding any telemetry or other data recipient.
