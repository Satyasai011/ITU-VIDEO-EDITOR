# ITU VIDEO EDITOR — Milestone 1

Native Kotlin/Compose Android project for a landscape-first tablet editor. This delivery is M1, not the complete V1.0. No advanced timeline buttons or simulated operations are presented.

## Requirements and build

- Android Studio Ladybug (2024.2.1) or a compatible newer version.
- JDK 17 or 21; project bytecode targets Java 17.
- Android SDK Platform 35, Build Tools 35.0.0, platform-tools; Android SDK licenses accepted.
- Gradle 8.9 wrapper, Android Gradle Plugin 8.7.3, Kotlin 2.0.21.
- Android 10 / API 29 minimum; target API 35. Primary test target: OnePlus Pad 2 in landscape. Portrait and smaller windows use a compact layout.
- Maven access to Google, Maven Central, and Gradle Plugin Portal for the first build.

Open this directory in Android Studio, configure its SDK, and sync. For command-line builds, set `ANDROID_HOME` or create `local.properties` containing your own `sdk.dir`.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
# Requires a connected authorized Android device or running emulator:
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk` when the build succeeds. Instrumentation tests compile separately; compilation does not mean they ran.

## Included

- Obsidian Prism Material 3 theme and responsive studio navigation.
- Dashboard, new project, Media Vault, single-source preview/trim workspace, settings.
- Real SAF video import with persistable read permission, metadata extraction, Room project persistence.
- Media3 ExoPlayer playback with native seek controls, error reporting, preview mute stored in DataStore, and lifecycle pause/release.
- Non-destructive source-range trim persisted to Room; M1 controls have millisecond precision. This is not the M2 frame-accurate timeline engine.
- Real device encoder queries including hardware/software identity, 4K60 bitrate format checks, and vertical resolution/rate checks.
- A real foreground-only 720p H.264/AAC trim/export validation path using Media3 Transformer, backed by Android codecs, GL effects, and MP4 muxing. Original frame timing is used; no 60 FPS conversion or 4K preset is claimed.
- Measured encoder progress when available, indeterminate status otherwise, cancellation, storage/source checks, MP4 video-track/sample validation, and publication through a pending MediaStore record.
- Unit tests for trim invariants and instrumentation tests for navigation and Room persistence.

For a phone/tablet download without Codex attachments, see [GitHub Actions build and Android installation](docs/GITHUB_ACTIONS.md).

See [executed build validation](docs/BUILD_VALIDATION.md), [feature status](docs/FEATURE_STATUS.md), [architecture](docs/ARCHITECTURE.md), and [critical device test](docs/DEVICE_VALIDATION.md).

## Release instructions

Use Android Studio **Build → Generate Signed App Bundle / APK → APK** and create/select your own private signing key. Keep key material and passwords outside this repository. Select the `release` variant. Alternatively configure a private Gradle signing configuration and run `./gradlew :app:assembleRelease`. Without a signing configuration the release APK is unsigned and cannot be installed as a release. No signed release artifact or release readiness is claimed.

Run the critical device test, connected tests, and performance checks before distributing. Do not advance to an advanced multitrack renderer until the real-media hardware gate passes on the primary device.
