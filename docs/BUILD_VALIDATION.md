# Build validation — 2026-11-08

Executed in the provided Linux workspace using Eclipse Temurin JDK 17.0.21, Android API 35, Build Tools 35.0.0, Gradle 8.9 and AGP 8.7.3. The Gradle wrapper distribution checksum was compared with the official Gradle SHA-256.

| Check | Result |
| --- | --- |
| `:app:assembleDebug` | PASS; debug APK built |
| `:app:testDebugUnitTest` | PASS; 7 tests, 0 failures, 0 errors |
| `:app:assembleDebugAndroidTest` | PASS; instrumentation test APK compiled |
| `:app:lintDebug` | PASS; 0 errors, 14 warnings |
| APK signature verification | PASS; APK Signature Scheme v2, one debug signer |
| Manifest inspection | Package com.itu.videoeditor, version 0.1.0-m1, minimum API 29, target/compile API 35 |
| Connected instrumentation tests | NOT RUN; ADB reported no connected devices |
| Import/decode/trim/export on actual device | NOT RUN; primary device unavailable |
| Performance/thermal profiling | NOT RUN |
| Signed release APK | NOT BUILT |

The unit tests ran against the final production Kotlin sources. A subsequent manifest-only backup configuration fix was rebuilt and linted; unit tests were not needlessly rerun for that resource change. Lint was explicitly refreshed to replace an outdated cached report.

Lint dependency-update notices reflect the pinned dependency versions. No lint errors are suppressed. See [lint report](validation/lint-results.txt), [unit test results](validation/unit-tests.xml), [build log](validation/build.log), [final package log](validation/final-package.log), and [refreshed lint log](validation/final-lint.log).

Final debug APK SHA-256:

```text
1bff44e3e6cc20a4d725f3cf51c9bc68970223b9186d44267eee54c10ff5b583
```

Compiled code and a structurally valid APK do not verify Android codec behavior, video playback, A/V synchronization, layout usability, or the requested 4K60 export configurations. Follow [the critical device procedure](DEVICE_VALIDATION.md) before starting advanced rendering work.
