# ARB Android Extension Port (work in progress)

This folder records an Android WebView integration path for the browser extension the owner supplied.

## Intended behavior

- Keep the target app's existing screens and native behavior.
- Bring the extension's amount/range matching and site-visible Buy-button flow into the existing ARBPay WebView.
- Preserve the selected payment-app tile behavior where the site and extension entitlement allow it.
- Keep Start/Stop, speed settings, notifications, and the extension UI as porting work items.
- The user completes all payment steps manually. The port must never enter a UPI PIN, OTP, initiate a money transfer, or confirm a payment.

## What is here

- `android/ArbBotBridge.kt`: restricted JavaScript-to-native bridge for storage/messages/notifications.
- `android/ArbBotWebViewInstaller.kt`: host-allowlisted WebView script installer.
- `android/bridge.js`: partial `chrome.storage` / `chrome.runtime` compatibility shim.
- `docs/compatibility-map.md`: observations from the supplied APK and XPI.
- `docs/port-gaps.md`: APIs and integration steps still missing.

## Not a finished APK

This source folder is **not** a patch that can be copied into the compiled APK by itself. The Android Studio source project is not available, and no rebuilt or signed APK was produced. The bridge must be connected to the target app's actual WebView lifecycle and its UI before it can run.

The supplied XPI manifest references `license.js`, but the supplied XPI archive did not contain that file. The original extension also uses browser APIs outside the partial shim; those must be implemented before claiming full extension compatibility.

## Target application integration points

Target package observed in DEX: `com.pt.ar.wallet`.

The target APK contains ARB wallet auto-order-grabbing classes, API route strings, and AgentWeb/WebView classes. This makes an in-app WebView port technically plausible, but does not prove that the integration source is compiled into the app.

## Build/use

See `docs/port-gaps.md` before attempting to merge these files. Do not expose the JavaScript interface on arbitrary websites. Call the installer only after validating the current WebView URL against the allowlist.
