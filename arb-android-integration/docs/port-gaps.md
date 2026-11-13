# Remaining work before this can be called a complete Android port

## Blocking items

1. The Android Studio project for the target app is unavailable. The uploaded file is a compiled APK only.
2. The XPI manifest references `license.js`, which was not present in the supplied XPI.
3. The extension's popup/background worker is not yet hosted in Android.
4. The current JavaScript shim does not implement the full browser extension API surface.
5. The installer has not been connected to the target app's real AgentWeb/WebView lifecycle.
6. The output has not been compiled, signed, installed, or runtime-tested.

## Browser APIs still to port

- `chrome.alarms`: Android scheduling/lifecycle replacement.
- `chrome.notifications`: Android NotificationManager and click routing.
- `chrome.tabs`: the single active WebView and navigation lifecycle.
- `chrome.identity`: cannot be assumed available on Android; requires a valid Android auth flow supported by the extension service.
- `chrome.action` / `browser.action`: Android HUD/settings entry point.
- Full runtime message routing between popup, background logic, and page content.

## Safe payment boundary

The Android port is allowed to interact with visible page controls up to opening the payment selector and choosing a provider tile. It must stop before:
- entering an OTP or UPI PIN,
- confirming or submitting a transfer,
- sending funds,
- confirming a completed payment.

## Required verification before release

- Confirm supported ARBPay hostnames using the live app configuration.
- Ensure the native app's own AutoBuy and the extension engine cannot both submit for the same order.
- Test Start/Stop, exact and range amount matching, stale/bought-by-someone-else handling, retry, refresh, provider selection, settings persistence, and notifications.
- Test that no payment form is auto-submitted and no OTP/PIN field is touched.
- Test install/upgrade/signing on the intended Android device.

## Build status

No installable APK has been produced from the uploaded compiled APK. Original source project and signing key are unavailable, and this repository currently contains integration source and technical notes only.
