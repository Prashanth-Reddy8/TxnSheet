# Security policy

TxnSheet handles notification-derived financial records, so security reports should
not include real notification text, account identifiers, signing keys, or other secrets.

## Reporting a vulnerability

Use the repository's private security-advisory feature. If that is unavailable, open
an issue containing only a non-sensitive summary and ask the owner for a private
reporting channel.

Do not test against another person's phone or data. Do not
publish an unpatched vulnerability or any captured financial data.

## Supported versions

`v2.0.0` is a device-test build and does not yet receive a production
security-support commitment. Production distribution is blocked on the checks in
[`docs/RELEASE_CHECKLIST.md`](docs/RELEASE_CHECKLIST.md).
