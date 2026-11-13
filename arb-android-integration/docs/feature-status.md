# Original extension feature inventory

The supplied XPI source pack preserves the extension's JavaScript, UI, settings, speed choices and feature flags. The corrected standalone manifest removes only the reference to the absent `license.js` file.

- Auto-Buy engine and order matching: `content.js`
- Exact amount / amount range, payment-app preference, speed and refresh settings: `content.js`, `popup.js`, `popup.html`
- Page network / WebSocket observation and page-world hooks: `page-hook.js`
- Background alarms, tab coordination and notifications: `background.js`
- In-page HUD and Start/Stop controls: `content.js`
- Notification sound and extension icons: `alert.wav`, `icons/`

The source contains standalone startup paths. Removing the dangling manifest reference does not itself make a Firefox/Chrome extension run as an Android app. Full Android WebView API compatibility and target APK integration remain incomplete.

## Android app toggle

A native in-app `EXTENSION ON/OFF` pill is added to the app window. It persists the enabled state in Android SharedPreferences. When OFF, matching WebViews are reloaded without injection. When ON, a matching ARBPay WebView is reloaded and the bootstrap injects on the next page load. The label reports `NO WEBVIEW`, `OPEN ARB PAGE`, or `ARB PAGE FOUND` to distinguish app-screen and domain-detection failures. This still requires on-device verification.
