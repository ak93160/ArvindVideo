# Original extension feature inventory

The local source pack preserves the supplied XPI files unchanged. This public repository contains only integration scaffolding and notes.

- Auto-Buy engine and order matching: `content.js`
- Exact amount / amount range, payment-app preference, speed and refresh settings: `content.js`, `popup.js`, `popup.html`
- Page network / WebSocket observation and page-world hooks: `page-hook.js`
- Background alarms, licensing refresh, tab coordination and notifications: `background.js`
- In-page HUD and Start/Stop controls: `content.js`
- Notification sound and extension icons: `alert.wav`, `icons/`

File presence does not prove runtime compatibility inside the target APK. Full Android API compatibility and integration into the target WebView remain required.
