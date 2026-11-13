# Build status — 13 November 2026

## Inputs inspected

- Target APK: 59.4 MiB, four DEX files. Static markers identify package `com.pt.ar.wallet`, existing ARB auto-order-grabbing classes, WebView/AgentWeb components, and the app's `domain.json`.
- Feature APK: 5.9 MiB, Kotlin/Firebase based; inspected for feature discovery only.
- XPI: Manifest V3, version 4.5.1. It contains `content.js`, `background.js`, `popup.js`, `popup.html`, `page-hook.js`, `SERVER_ENDPOINT.js`, icons and sound.

## Checks run locally

- `node --check content.js`: PASS
- `node --check background.js`: PASS
- `node --check popup.js`: PASS
- `node --check page-hook.js`: PASS
- `node --check SERVER_ENDPOINT.js`: PASS
- Android shim JavaScript syntax: PASS
- Python patch/staging script syntax: PASS
- Synthetic manifest patch + XPI asset staging: PASS
- Synthetic additional-DEX ZIP injection: PASS

## GitHub build pipeline

- Added `.github/workflows/build-android-apk.yml` to both `main` and `arb-android-integration`.
- Workflow decodes the owner-supplied APK with APKTool, copies the XPI assets, adds a bootstrap ContentProvider and DEX, rebuilds, aligns and signs, then publishes an APK Release asset.
- **The workflow has not yet been run.** The first APK and corrected XPI must first be uploaded as Release assets and their direct download URLs supplied to the manual workflow inputs.

## Integration source

- `android/extension-shim.js`: partial Chrome storage/runtime/tabs/alarms/notifications shim.
- `android/bootstrap/src/.../ArbNativeBridge.java`: minimal storage, navigation, notification and asset bridge.
- `android/bootstrap/src/.../ArbBootstrapProvider.java`: scans the app's Activity view trees and injects scripts into allowlisted ARBPay WebViews after page load.
- `scripts/patch_target.py` and `scripts/inject_dex.py`: APK patching helpers.

## Important remaining limitations

1. The original Android Studio source project and original signing key were not supplied.
2. The APK patch uses a ContentProvider/ActivityLifecycleCallbacks WebView scan. It has not been runtime-tested on the target device.
3. `page-hook.js` is injected after page load, not at WebView document-start. Initial network/WebSocket events may be missed.
4. Popup HTML/JS is included as assets, but the browser's toolbar popup is not hosted as a native Android screen.
5. MV3 APIs are shimmed only in part. This is not a complete browser runtime.
6. APK signing uses a new CI key; the modified app may require uninstalling the original app first.

## Payment boundary

The intended automation reaches visible Buy/order controls and configured payment-method selection. The app owner manually handles OTP/PIN entry and final payment transfer/confirmation.
