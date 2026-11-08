# Architecture

`MainActivity` hosts Compose and the Obsidian Prism theme. `StudioApp` owns Navigation Compose destinations; `StudioViewModel` exposes Room project flows and DataStore preference state through StateFlow. `ItuApplication` provides a small application-scoped repository/database container without a DI framework.

`data/StudioRepository` handles SAF permission retention, background metadata reads, persistence, and preview preferences. `data/StudioDatabase` stores source URIs and microsecond trim bounds, never source bytes. A trim update is a single atomic SQL update. Room schema version 1 is checked in. No destructive migrations are configured. Backups are disabled because SAF grants cannot be restored by backing up the database alone.

`domain/TrimRange` enforces source-relative range invariants without assuming a constant frame rate. M1 UI and Media3 clipping APIs currently use milliseconds; this must not be confused with frame-boundary accuracy. M2 must introduce indexed presentation timestamps, linked clip/track models and transactional operation history before Quick Split is exposed.

`ui/EditorScreen` owns one ExoPlayer and one validation exporter while its destination is composed. ExoPlayer decodes only the active source. Disposal releases decoder resources. ON_STOP pauses playback and cancels an active render. Media3 handles surface rendering and native codec interaction. Preview mute does not change export audio. The editor's draft trim is saveable Compose state; Save trim commits it to Room. No undo history is implemented yet.

`media/EncoderCapabilities` queries MediaCodecList and codec format support for the actual device. Advertised encoder capabilities are diagnostic evidence, not proof of a successful export. No preset is automatically enabled based on a device name.

`media/ValidationExporter` runs Media3 Transformer on the main application looper, with real GL scaling to 720p and H.264/AAC output. Its encoder factory explicitly requires re-encoding, including when source codecs and dimensions match the output; completion reports the selected video encoder name. File validation and MediaStore copying run on Dispatchers.IO. This is a critical-path prototype, not the final M5 render architecture. It does not convert to 60 FPS or exercise a 4K60 pipeline. Media3 may select codec fallback; this validation does not certify hardware-only encoding.

Output is rendered to a private temporary MP4, parsed for a video track, positive duration and a video sample, then copied to an IS_PENDING MediaStore entry. Only a complete copy becomes visible. Exceptions delete the pending entry; render errors/cancellation delete the temporary file. This detects basic structural failures, not every possible decoding or A/V synchronization error. Playback on the device is still required. Publication already in progress is allowed to finish if the UI stops; render cancellation applies before publication. Process death can leave private temporary files or pending MediaStore rows; startup cleanup and a foreground export service belong to M5/M6. There is no resume support.

## Expansion boundaries

M2: pure timeline engine + presentation timestamp index, split/trim/Quick Split, linked audio, undo/redo tests.
M3: transactional journal, marker transforms, independent duplicated segment IDs and opening hook range playback.
M4: independent sequences referencing sources, DataStore/Room workspace layout storage separate from projects, crop keyframes.
M5: real multitrack composition/mixing, cached waveforms, capability-validated presets, foreground service with thermal/storage guards.
M6: operation checkpoints, recovery, relinking, migration tests, physical-device profiling and signed release validation.

Avoid introducing renderer or UI affordances for these milestones until their underlying operations and tests exist.
