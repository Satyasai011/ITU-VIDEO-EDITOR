# Download the M1.1 APK from GitHub Actions on Android

Builds are available at https://github.com/Satyasai011/ITU-VIDEO-EDITOR/actions/workflows/android-debug.yml.

The workflow at `.github/workflows/android-debug.yml` belongs at the repository root alongside `settings.gradle.kts`, `gradlew`, `app/`, and `gradle/`. It runs on pushes and pull requests and can also be run manually. It installs Temurin JDK 17, Android Platform 35 and Build Tools 35.0.0, then uses the checked-in Gradle 8.9 wrapper to build the debug APK, run unit tests and lint, and compile instrumentation tests. Instrumentation tests are not executed on GitHub without a configured Android device/emulator. The workflow verifies the APK signature before uploading it.

Each successful run uploads `app/build/outputs/apk/debug/app-debug.apk` as an artifact named `ITU-VIDEO-EDITOR-M1.1-debug-<run number>`, retained for 30 days. Repository or organization artifact policies must permit 30-day retention. This workflow does not use release signing keys and does not run physical-device tests. GitHub generates a debug signing key for the runner, so APKs from different runs can have different signatures.

## Start a build

1. Open the repository in Chrome on your OnePlus Pad 2 and sign into the GitHub account that can access it.
2. Open **Actions**, then **Android Debug APK**. If GitHub asks you to enable Actions for this repository, enable them if you own/manage it.
3. A push containing the workflow normally starts a run automatically. To start another run, tap **Run workflow**, select the branch, and confirm **Run workflow**. Manual dispatch is available once the workflow exists on the repository's default branch.
4. Open the new run and wait for a green check mark. If it fails, open **Build M1.1 Debug APK** and read the failed step; failed builds do not upload a pretend APK.

## Download and install

1. Open the successful run's **Summary** page. Scroll to **Artifacts** and tap `ITU-VIDEO-EDITOR-M1.1-debug-<run number>`.
2. GitHub downloads a ZIP archive. If the artifact section or download control is missing in the mobile view, open Chrome's menu and enable **Desktop site**, then reload. Stay signed in; private repository artifacts require repository access.
3. Open **Files** (or Files by Google), go to **Downloads**, select the ZIP and tap **Extract**. Open the extracted `app-debug.apk`. Install the APK inside the ZIP, not the ZIP itself.
4. When Android prompts for permission, enable **Allow from this source** for the app opening the APK (usually Files or Chrome), return, and tap **Install**. You can turn this setting off afterward.
5. Open **ITU VIDEO EDITOR**, create a project and choose a real video. Follow [the critical device validation procedure](DEVICE_VALIDATION.md).

If Android reports a conflicting package or incompatible signature, a previous debug installation may have been signed with a different key. Preserve any local project data you need before uninstalling the old test app: uninstalling deletes its private project database and preferences. Then install the new APK. The original source videos remain in their original locations. A fresh GitHub debug APK is for M1 device testing; it is not a signed production release. M1.1 has application version code 2 and version name 0.1.1-m1.1.

If the artifact has expired, run the workflow again. A repository policy that disables Actions, lacks runner minutes/storage, or prevents use of these Actions must be resolved in GitHub repository/organization settings; this workflow cannot override it.
