## TxnSheet 1.0.5 alpha 6

This update fixes transaction SMS notifications received through Google Messages or Truecaller
being read as compact summaries and then reported as `PARSE_IGNORE_INFO`.

TxnSheet now prefers the complete Android MessagingStyle message or the longest visible body. The
reported Kotak `Sent Rs... A/c... UPI Ref...` format is covered by an automated regression test.

### Install and test

1. Install this APK over the existing app and choose **Update**.
2. Keep **Messages** enabled in **Settings → Notification sources**.
3. Make a new test transaction. Existing ignored notifications are not replayed.

Truecaller's compact card can remain disabled if Google Messages already supplies the full SMS.
This avoids processing two notifications for the same bank transaction.

This is a debug-signed prerelease for device testing.
