# TxnSheet privacy model

TxnSheet is an owner-controlled, local-first financial utility. It reads notifications only
after the owner grants Android notification access and only for source apps the owner enables.

## Data handled

For an enabled source, TxnSheet may temporarily handle the notification's visible text and
metadata in order to recognize a completed transaction. It derives ledger fields such as time,
amount, currency, direction, payment method, counterparty, category, institution, masked account
suffix, reference ID, optional balance, source app, confidence, parser rule, and notes.

Raw notification text is never uploaded to Google Sheets. When an item needs review, a bounded
copy is encrypted on-device with Android Keystore AES-GCM and deleted at expiry or when the item
is resolved. Ignored messages are not retained as raw text. Diagnostics contain fixed event codes
and deliberately exclude transaction values, message bodies, identifiers, tokens, and workbook IDs.

## Google access

Google authorization is requested only when the owner starts Sheets setup. TxnSheet requests the
per-file `drive.file` scope so it can create and update the ledger file used by the app. Short-lived
access tokens are used in memory and are not persisted by TxnSheet. The owner can revoke access and
delete local data from the app.

## Storage and deletion

Structured transactions, configuration, source choices, review state, and privacy-safe diagnostics
are stored in the app's private local database. Android backups are disabled. The owner can delete
individual transactions and clear all local app data. Clearing all data also cancels background work
and removes the review encryption key; it does not silently delete the owner's Google spreadsheet.

## Network use

TxnSheet communicates with Google APIs over HTTPS for authorization and owner-approved spreadsheet
operations. It contains no advertising SDK and no analytics integration. A release operator must
update this document and the store disclosure before adding any telemetry or other data recipient.

