# M1.2 builds, tests and Android installation

Workflow: https://github.com/Satyasai011/ITU-VIDEO-EDITOR/actions/workflows/android-debug.yml

The repository-root workflow uses Temurin JDK 17, Android SDK 35/Build Tools 35.0.0 and the pinned Gradle 8.9 wrapper. It builds the debug and instrumentation APKs, runs unit tests and lint, verifies application ID/version/signature, and uploads the actual APK plus SIGNING_STATUS.json for **30 days**. A second job runs connected regression tests on an API 35 emulator and uploads the actual test reports. The emulator is not a OnePlus physical-device test.

## Signing comes first

The application ID remains `com.itu.videoeditor`, and M1.2 has versionCode 3. The delivered M1.1 private signing key is not available here. Available local signing material does not match the installed M1.1 baseline. GitHub secret management is blocked by the connected integration's 403 permission response. See [the complete signing status](M1_2.md#signing-status--read-before-installing).

If the original debug keystore is securely supplied as the `ITU_DEBUG_KEYSTORE_BASE64` Actions secret, the workflow restores it on the runner, compares the APK certificate with the delivered M1.1 fingerprint, and fails publication on mismatch. The keystore is removed after the build and never uploaded. Without that secret, the APK artifact name contains **fresh-install-only** and its signing report explicitly says it cannot update M1.1. There is no silent claim that a fresh runner's new key is compatible.

## Download on Android

1. Sign into GitHub in Chrome, open the repository → **Actions → Android Debug APK** and choose the desired run. To start another run, use **Run workflow** on the branch containing the workflow.
2. Check the build and API 35 regression-test results. Open the run Summary → **Artifacts**. Download the M1.2 APK artifact, using Chrome's **Desktop site** view if needed.
3. Extract the artifact ZIP in Files. Read SIGNING_STATUS.json. The installable file is app-debug.apk, not the ZIP.
4. **If it says FRESH_INSTALL_ONLY_NOT_AN_M11_UPDATE, do not install over or uninstall the M1.1 app holding your projects.** Test only on a separate clean Android test device/emulator. When a matching-key build is actually available, verify the report before attempting an update.
5. On an eligible clean test device, open app-debug.apk in Files and allow installation from Files when Android prompts. The source videos remain selected through Android's document picker; no broad storage access is required.

A private Drive copy, when provided, contains the exact extracted APK from the successful build. Its filename carries the fresh-install-only warning when applicable. A Drive download does not change the signing restriction.

Do not claim update compatibility from a successful compilation, higher version code or a valid APK signature alone. A signature must match the existing installation (or a verified signing lineage), and the real device update still needs testing.
