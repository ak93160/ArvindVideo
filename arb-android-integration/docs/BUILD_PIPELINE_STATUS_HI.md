# GitHub APK build pipeline status

GitHub Actions workflow: `.github/workflows/build-android-apk.yml`

It is configured to:
1. Download the owner's base APK and standalone XPI from direct release asset URLs.
2. Decode the target APK with APKTool.
3. Copy the extension scripts into app assets and add an Android bootstrap ContentProvider.
4. Compile the Java bootstrap into an additional DEX file.
5. Rebuild, align and sign the APK.
6. Upload the APK as a workflow artifact and publish a GitHub Release.

The workflow has not been executed in this conversation because the GitHub repository does not contain the binary input assets yet. The user must upload the original APK and corrected XPI as assets of a GitHub Release and then run the workflow with their download URLs.

A successful CI build is a first integration build, not a proof of runtime compatibility. The target APK must still be installed and exercised on the intended Android device.
