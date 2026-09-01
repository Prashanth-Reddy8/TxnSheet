## TxnSheet 1.0.6 alpha 7

This update reads the structured Android MessagingStyle field used by Google Messages for the
actual SMS body. This addresses continued `PARSE_IGNORE_INFO` results when the complete Kotak SMS
is visible in the notification but absent from the ordinary notification text field.

Diagnostics now include privacy-safe issue names such as `MISSING_AMOUNT` or `MISSING_DIRECTION`
without logging notification text, amounts, account numbers, or reference IDs.

### Test

1. Install over the existing app and choose **Update**.
2. Enable **Messages** and disable **Truecaller** in TxnSheet's notification sources.
3. Make a new test transfer; old notifications are not replayed.

This is a debug-signed prerelease for device testing.
