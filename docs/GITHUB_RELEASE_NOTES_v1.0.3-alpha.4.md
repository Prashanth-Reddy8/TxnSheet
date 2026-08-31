## TxnSheet 1.0.3 alpha 4

This update fixes **No sources discovered** after notification access is enabled.

TxnSheet now discovers source apps from notifications already visible on the phone as soon as
Android connects notification access. It records only the app name/package during discovery;
notification text is still processed only after you explicitly enable that source.

### Install and use

1. Install this APK over the existing version and choose **Update**.
2. Leave at least one bank, payment, Messages, or other notification visible.
3. Open TxnSheet, then open **Settings → Notification sources**.
4. Enable the bank/payment source you trust.
5. The next transaction notification from that enabled source can be captured.

This is a debug-signed prerelease for device testing.
