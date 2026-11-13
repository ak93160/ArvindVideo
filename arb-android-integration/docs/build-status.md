# Build status — 13 November 2026

## Inputs inspected

- Target APK: 59.4 MiB, four DEX files. Static markers identify package `com.pt.ar.wallet`, existing ARB auto-order-grabbing classes, WebView/AgentWeb components, and the app's `domain.json`.
- Feature APK: 5.9 MiB, Kotlin/Firebase based; inspected for feature discovery only.
- XPI: Manifest V3, version 4.5.1, 13 archive entries. It contains `content.js`, `background.js`, `popup.js`, `popup.html`, `page-hook.js`, `SERVER_ENDPOINT.js`, icons and sound.

## Checks run

- `node --check content.js`: PASS
- `node --check background.js`: PASS
- `node --check popup.js`: PASS
- `node --check page-hook.js`: PASS
- `node --check SERVER_ENDPOINT.js`: PASS
- Android bridge JavaScript syntax: PASS
- XPI manifest dependency validation: INCOMPLETE — manifest refers to `license.js`, absent from supplied XPI.

## Source in this branch

- `android/bridge.js`: partial WebView shim for local storage/runtime.
- `android/ArbBotBridge.kt`: Android bridge scaffold.
- `android/ArbBotWebViewInstaller.kt`: allowlisted WebView injector scaffold.
- `docs/compatibility-map.md`: static compatibility evidence.
- `docs/port-gaps.md`: remaining implementation requirements.

The original XPI files are not copied to this public repository. A local source pack is provided separately in the conversation.

## Not completed

1. Connect the Kotlin installer to the target APK's actual AgentWeb/WebView lifecycle.
2. Inject `page-hook.js` in MAIN world at document-start.
3. Host the MV3 background service-worker logic in Android.
4. Implement the complete `chrome.*` API surface used by background and popup scripts.
5. Obtain the absent `license.js` from the extension author/provider; no bypass or fake licence response is implemented.
6. Patch, rebuild, sign, install, and runtime-test the target APK.

## Build blocker

The target is a compiled APK; the Android Studio source project and original signing key were not supplied. This execution environment did not have `apktool`, `jadx`, Android SDK build tools (`aapt2`, `d8`, `apksigner`, `zipalign`) or Gradle, and network attempts to obtain them were blocked. No installable modified APK was produced.

## Payment boundary

The requested flow reaches visible Buy/order controls and configured payment-method selection. The user completes OTP/PIN entry and payment transfer/confirmation manually.
