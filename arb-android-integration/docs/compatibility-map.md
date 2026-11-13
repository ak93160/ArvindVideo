# Compatibility map

## Target APK

Static inspection of the uploaded target APK found:

- Package namespace strings: `com.pt.ar.wallet`.
- Four DEX files.
- Kotlin/AndroidX code and AgentWeb/WebView classes.
- Native ARB order-grabbing code in `classes3.dex`, including:
  - `AutoBuyFragment.kt`
  - `OrderGrabbingStartModel`
  - `/buyCenter/autoBuySwitch`
  - `/buyCenter/buy`
  - `/smartRangeBuy/match/start`
  - `/smartRangeBuy/match/cancel`
  - `/smartRangeBuy/status`

These are static strings/class references, not proof that every endpoint is currently available or that arbitrary extension code can call them.

## Supplied XPI

The supplied XPI is Manifest V3, version 4.5.1. Its archive contains:

- `content.js`
- `background.js`
- `popup.html`
- `popup.js`
- `page-hook.js`
- `SERVER_ENDPOINT.js`
- icons and `alert.wav`

Its manifest references `license.js`, but that file was absent from the archive.

API usage observed by static search:

| Script | Browser APIs observed |
|---|---|
| content.js | runtime, storage |
| background.js | runtime, storage, alarms, notifications, tabs, action |
| popup.js | runtime, storage, tabs, identity |
| page-hook.js | storage |

## Port feasibility

| Capability | Feasibility | Required Android work |
|---|---|---|
| Content-script amount matching and visible DOM Buy click | Plausible | Inject the supplied content script on approved ARBPay hosts and bridge its storage/runtime calls |
| Exact/range settings | Plausible | Persist preferences and connect the extension settings UI |
| Payment-app tile selection | Conditional | Verify site DOM, app eligibility and extension's configured provider; never automate payment submission |
| Refresh/retry behavior | Conditional | Port alarms/background scheduling and tab lifecycle semantics to Android lifecycle/WorkManager |
| Popup and settings UI | Not yet integrated | Host popup HTML/JS or reimplement the settings UI in Android |
| Notifications | Plausible | Notification channel + Android 13+ permission flow |
| Floating system bubble | Not equivalent to WebView HUD | Overlay permission + foreground service + drag/minimize UI |
| License/account behavior | Blocked/incomplete | The missing `license.js` must be obtained from its author; do not bypass its checks |

## Important distinction

The target app already has native auto-order-grabbing code. A port must not blindly run two competing buy engines at once. The final implementation must choose one orchestrator and map settings into it, or explicitly coordinate both.
