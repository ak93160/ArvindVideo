# ARB Automation Android APK — GitHub build

यह v5 pack पहली APK को base बनाकर Android WebView में XPI के Auto-Buy HUD/logic को जोड़ने के लिए build pipeline देता है। यह अभी source/build pipeline है; runtime APK को GitHub Actions में input files देने के बाद ही बनेगी।

## Features intended for the APK

- Extension का content HUD, Amount Matching और Start/Stop
- Original speed choices और refresh logic
- Configured payment selector interaction
- Extension background notifications और alarms का WebView shim
- Page hook injection
- Payment user स्वयं करेगा; OTP/PIN auto-entry और payment submission का कोई bridge नहीं है।

## GitHub build steps

1. Repository में एक GitHub Release बनाओ, जैसे tag `build-inputs-v1`.
2. उसमें पहली APK को `base.apk` नाम से और `Arvind_Bot_Standalone_NoLicenseJS.xpi` को `extension.xpi` नाम से upload करो.
3. GitHub → Actions → **Build ARB Android APK** → Run workflow खोलो.
4. दोनों release-asset download URLs inputs में डालो. Release asset URL का format:
   `https://github.com/OWNER/REPO/releases/download/TAG/FILE`
5. Successful run के बाद release में `ArvindARB-Automation.apk` मिलेगा और Actions artifact में भी APK रहेगा.

## Important limitations

- Target APK compiled है; original Android Studio source और original signing key उपलब्ध नहीं हैं.
- Build नई signing key से होगा; original installed app के ऊपर update नहीं होगा. Original app हटाकर modified app install करनी पड़ सकती है.
- WebView injection केवल ARBPay frontend domains पर होता है.
- Android content provider सभी app activities के WebViews scan करता है; यह injection strategy static build में बनाई गई है, पर target device पर runtime validation जरूरी है.
- Manifest V3 के background/tabs/alarms/notifications APIs का Android shim complete browser replacement नहीं है. Workflow APK build कर सकता है; हर browser-only feature के runtime behavior की पुष्टि अलग testing से होगी.
- XPI का popup browser action Android में अलग popup की तरह host नहीं होता; content.js का in-page HUD injected रहता है.
- No guarantee of site/selector compatibility until a device test is performed.
