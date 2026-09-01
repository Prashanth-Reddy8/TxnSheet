## TxnSheet 1.0.4 alpha 5

This update fixes a Nothing OS condition where notification access appears enabled but Android
has not connected TxnSheet's listener, leaving **Notification sources** empty.

TxnSheet now explicitly requests a listener reconnect whenever the app resumes with access
enabled. After reconnecting, it discovers apps from notifications already visible on the phone.

### Install and use

1. Install this APK over the existing app and choose **Update**.
2. Keep at least one notification visible in the notification shade.
3. Open TxnSheet and wait a few seconds.
4. Open **Settings → Notification sources** and enable the source you trust.

This is a debug-signed prerelease for device testing.
