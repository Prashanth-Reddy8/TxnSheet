# TxnSheet release checklist

The code can be built and tested without owner secrets. A production distribution is not
complete until every external item below is verified by the owner.

## Identity and signing

- [ ] Final application ID is `app.txnsheet.personal`.
- [ ] Release keystore is stored outside the repository and backed up securely.
- [ ] Release AAB/APK is signed with the intended key and verified with `apksigner`.
- [ ] Google OAuth Android client contains the exact release package and certificate SHA-1.
- [ ] If distributed with Google Play, the Play App Signing SHA-1 is also registered.

## Google project

- [ ] Google Sheets API and Google Drive API are enabled.
- [ ] OAuth branding, support email, privacy policy, and terms are correct.
- [ ] Requested scope is only `https://www.googleapis.com/auth/drive.file`.
- [ ] Create-workbook, reconnect, revoke, and account-removal paths are tested.
- [ ] A workbook with altered headers/schema proves that sync stops safely.
- [ ] A simulated timeout after a successful append proves UUID reconciliation prevents a duplicate.

## Privacy and policy

- [ ] Prominent in-app disclosure is reviewed before notification-access Settings opens.
- [ ] Play Data safety answers match the implemented data flow.
- [ ] Published privacy policy matches `docs/PRIVACY.md` and the final retention controls.
- [ ] Deleting local data removes the database, encrypted queue, preferences, scheduled work,
      and the Android Keystore review key.
- [ ] No message text, financial values, account identifiers, access tokens, or spreadsheet IDs
      appear in Logcat, crash reports, analytics, or WorkManager input.

## Device validation

- [ ] Physical Nothing Phone (1), Android 15, Nothing OS: clean install and upgrade tested.
- [ ] Notification access accepted, declined, revoked, and restored.
- [ ] Capture tested with every explicitly supported source package and anonymized real-message corpus.
- [ ] Manual share and paste always show a preview before saving.
- [ ] 60 Hz, 120 Hz, battery saver, Doze, offline, and flaky-network behavior tested.
- [ ] Light/dark mode, large font, display scaling, TalkBack, switch access, and landscape tested.
- [ ] Display cutout, edge-to-edge system bars, IME, predictive back, and gesture navigation tested.

## Quality gates

- [ ] Unit tests and release lint pass.
- [ ] Release build passes R8/resource shrinking and a cold-start smoke test.
- [ ] APK/AAB checksum is recorded with the release notes.
- [ ] Dependency and Play policy review is repeated immediately before publication.

Nothing Phone (1) has completed its announced security-support window. Because TxnSheet handles
financial data, the owner should explicitly accept that device risk or deploy on a currently
supported Android device.

