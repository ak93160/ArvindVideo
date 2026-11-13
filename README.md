# ARB Android Integration

This repository is a work-in-progress Android integration workspace for the ARB browser automation extension supplied by the repository owner.

## Current status

- Target APK inspected: 59.4 MB, four DEX files.
- The target app contains native ARB wallet/auto-order-grabbing code and an Android WebView.
- The supplied XPI is Manifest V3 and contains content, popup, background, and page-hook scripts.
- The XPI manifest references `license.js`, but that file was not present in the supplied archive.
- A complete installable APK has **not** been built from this repository.

## Important

The repository intentionally does not contain the uploaded APKs, signing keys, account data, or a redistribution copy of the full supplied extension. The original extension's popup/background APIs still need a complete Android host implementation. The Android integration source is a scaffold, not a drop-in binary patch.

The app owner performs all payment actions manually. The integration must not enter UPI PINs, OTPs, submit payments, or confirm transactions.

See [arb-android-integration/README.md](arb-android-integration/README.md) for the source package and remaining work.
