# ARB Android Integration

This repository contains a work-in-progress Android integration workspace for the ARB browser automation extension.

## Build the APK automatically

1. Open [GitHub Releases](https://github.com/ak93160/ArvindVideo/releases).
2. Create a release with a tag starting with `arb-inputs-`, e.g. `arb-inputs-v1`.
3. Attach the first/original app APK with the exact asset name `base.apk`.
4. Attach `Arvind_Bot_Standalone_NoLicenseJS.xpi` with the exact asset name `extension.xpi`.
5. Publish the release. Publishing it automatically starts **Build ARB Android APK**.

After the workflow finishes, the output release is named/tagged `arb-android-build-<run>` and contains `ArvindARB-Automation.apk`. The APK is also available under the workflow's Actions artifacts.

Alternatively, open [Actions → Build ARB Android APK](https://github.com/ak93160/ArvindVideo/actions/workflows/build-android-apk.yml) and run it manually using direct Release asset URLs.

## Source and status

See the [Android integration branch](https://github.com/ak93160/ArvindVideo/tree/arb-android-integration) for source, compatibility notes and build status.

## Important limitations

- The workflow is configured but cannot run until the owner uploads both binary assets to a Release. The current repository has no input release assets yet.
- This repository is public; Release assets uploaded here will be publicly downloadable. Do not attach private or sensitive files.
- APK patching uses a compiled-APK injection approach because the original Android Studio project and signing key were not supplied.
- The resulting APK is newly signed and may require uninstalling the original app first.
- Static build success does not prove every browser-extension API or site selector works at runtime; device testing is required.
- The app owner completes all payment steps manually. The Android bridge does not enter OTP/PIN or submit/confirm payment.
