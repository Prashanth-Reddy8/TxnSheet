# TxnSheet release checklist

## Identity and signing

- [ ] Final application ID is `app.txnsheet.personal`.
- [ ] Release keystore is stored outside the repository and backed up securely.
- [ ] Release AAB/APK is signed with the intended key and verified with `apksigner`.
- [ ] If distributed with Google Play, Play App Signing is configured.

## Privacy and policy

- [ ] Prominent disclosure is reviewed before notification-access Settings opens.
- [ ] Play Data safety answers match the implemented local-only data flow.
- [ ] Published privacy policy matches `docs/PRIVACY.md`.
- [ ] Erase local data removes the database and Android Keystore review key.
- [ ] No message text, financial values, or account identifiers appear in logs or diagnostics.
- [ ] The final manifest declares no internet, SMS, contacts, or storage permission.

## Device validation

- [ ] Physical Nothing Phone (1): clean install and upgrade tested.
- [ ] Notification access accepted, declined, revoked, and restored.
- [ ] Capture tested with each supported source package and anonymized real alerts.
- [ ] Manual share and paste always show a preview before saving.
- [ ] 60 Hz, 120 Hz, battery saver, Doze, and offline behavior tested.
- [ ] Light/dark mode, large font, TalkBack, landscape, cutouts, IME, and gesture navigation tested.

## Quality gates

- [ ] Unit tests and release lint pass.
- [ ] Release build passes R8/resource shrinking and a cold-start smoke test.
- [ ] APK/AAB checksum is recorded with the release notes.
- [ ] Dependency and Play policy review is repeated immediately before publication.

Nothing Phone (1) has completed its announced security-support window. Because TxnSheet handles
financial data, the owner should accept that risk or deploy on a currently supported device.
