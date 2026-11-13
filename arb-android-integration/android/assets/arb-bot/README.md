# Required extension assets

The host Android project must place the extension's authorized, complete runtime assets under:

`app/src/main/assets/arb-bot/`

Expected files for the full port include:

- `bridge.js` (provided in this repository)
- `content.js` (from the supplied XPI)
- `popup.html`, `popup.js`
- `background.js` logic ported to Android lifecycle/scheduling
- `page-hook.js` if the feature still requires MAIN-world instrumentation
- `alert.wav` and the referenced icons

The supplied archive's manifest also referenced `license.js`, but it was absent. Obtain that component from the extension author/provider. Do not fake or bypass the missing license component.

The XPI's popup, background worker and page-hook are not automatically loaded by `ArbBotWebViewInstaller`. They need their own Android host and API bridge.
