# ARB Android Integration

This repository contains a work-in-progress Android integration workspace for the ARB browser automation extension.

## Build the APK

The GitHub Actions workflow is available under **Actions → Build ARB Android APK**.

Before running it, upload two assets to a GitHub Release in this repository:
- the first/original APK named `base.apk`
- `Arvind_Bot_Standalone_NoLicenseJS.xpi` named `extension.xpi`

Then run the workflow and paste each Release asset's direct download URL into the two inputs. On success, the workflow publishes `ArvindARB-Automation.apk` as a GitHub Release asset and an Actions artifact.

See the [Android integration branch](https://github.com/ak93160/ArvindVideo/tree/arb-android-integration) for the code, build notes, and limitations.

## Status and limitations

- The workflow is configured but has **not yet been run**; the binary inputs are not in a GitHub Release.
- APK patching uses a compiled-APK injection approach because the original Android Studio source project and signing key were not supplied.
- The resulting APK will be newly signed. It may require uninstalling the original app first.
- Static build success does not prove all browser extension APIs or site selectors work at runtime; device testing is required.
- The user completes all payment steps manually. The Android bridge does not enter OTP/PIN or submit payment.
