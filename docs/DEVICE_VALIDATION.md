# Critical real-device gate

Status: NOT RUN. No Android device is connected to this build environment. A successful APK build does not establish the following results.

Use the OnePlus Pad 2 with USB debugging enabled, sufficient free storage, and a real gameplay MP4 containing audible synchronization cues. Include a variable-frame-rate sample and a rotated/portrait sample. Record device model, Android version, source codec/resolution/FPS/duration, and app build.

1. Install the debug APK. Open Settings → Inspect encoders. Save the report externally. Confirm hardware/software identity and advertised HEVC/H.264 3840×2160/60 and 55/70 Mbps results. Unsupported configurations must say unsupported.
2. New project → Choose video & create. Use the system document picker to select the real video. Confirm real source metadata, preview image, audible audio, seek behavior and correct orientation. Cancel the picker once and confirm no project is created.
3. Force-stop/reopen the app. Open the saved project and confirm the persistent URI still works. Move or revoke access to a separate test source and confirm an actionable playback error, without claiming relinking support.
4. Set in/out with the range controls or seek and use Set in/Set out. Preview trim. Save trim, reopen, and confirm retained range. Independently inspect the original source and confirm its duration/bytes are unchanged. M1 precision is milliseconds, not exact presentation-frame selection.
5. Test export while keeping the app foreground. Confirm measured progress or an indeterminate indicator. Record the actual video encoder name shown on completion and compare it with the hardware/software capability report. Confirm the output is in Movies/ITU Video Editor and play it with Open exported MP4 and an independent media player. Verify the selected content, orientation, duration, visible frames and audible A/V sync. A successful track-structure check alone is insufficient.
6. Cancel an active render and retry from the beginning. Background the app during rendering and confirm cancellation. No partial visible MP4 should appear. The short publication copy may finish once rendering has completed. No interrupted export resume is supported.
7. Check low-storage rejection, unsupported/corrupt media, and readable errors. Test wide landscape, portrait and split-screen layouts; verify touch targets and that controls remain reachable.
8. Run `./gradlew :app:connectedDebugAndroidTest` with the device connected; retain the test report.

Record PASS/FAIL for import, decode/preview, trim, MP4 export/playback, capability reporting and A/V synchronization. The M1 validation export is 720p and does not certify 4K60 performance. Only proceed with the advanced multitrack rendering system after the real-device gate passes. Later M5 validation must separately test requested resolutions, FPS, codecs, bitrates, sustained performance, thermals and background service behavior.
