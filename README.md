# ARB Android Integration

This is a work-in-progress Android integration workspace for the ARB browser automation extension supplied in this conversation.

## Where the source lives

The integration source and compatibility notes are on the **`arb-android-integration`** branch:

- [Open the integration source folder](https://github.com/ak93160/ArvindVideo/tree/arb-android-integration/arb-android-integration)
- [Build status](https://github.com/ak93160/ArvindVideo/blob/arb-android-integration/arb-android-integration/docs/build-status.md)
- [Compatibility map](https://github.com/ak93160/ArvindVideo/blob/arb-android-integration/arb-android-integration/docs/compatibility-map.md)

## Current status

- Target APK statically inspected: 59.4 MB, four DEX files.
- The target app contains native ARB wallet auto-order-grabbing code and Android WebView classes.
- The supplied XPI is Manifest V3 and has content, popup, background, and page-hook scripts.
- The XPI manifest references `license.js`, but that file was not present in the supplied archive.
- A complete installable APK has **not** been built.

## Privacy and payment boundary

This repository does not contain the uploaded APKs, signing keys, account data, or a redistribution copy of the full supplied extension. The extension's popup/background APIs still need a complete Android host implementation.

The app owner performs all payment actions manually. The integration must not enter UPI PINs or OTPs, submit payments, or confirm transactions.
