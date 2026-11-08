# Feature status

| Milestone | State | Scope |
| --- | --- | --- |
| M1 foundation | Implemented; device validation pending | Gradle project, Compose theme/navigation, dashboard, SAF import, Room projects, DataStore mute, actual ExoPlayer playback |
| Critical media gate | Implemented test path; not device verified | Source trim, 720p H.264/AAC export, actual encoder report, basic MP4 validation, open output |
| M2 timeline | Not implemented | Full timeline, frame index/stepping, magnifier, VFR boundary editing, split, Quick Split, undo/redo |
| M3 gameplay | Not implemented | Manual kill markers, highlights, Duplicate to Start, marker shifting, hook auto-preview/replay |
| M4 workspace/Shorts | Not implemented | Movable/resizable controls, named independent layouts, presets, separate sequences, 9:16 crop/keyframes |
| M5 audio/export | Not implemented | Waveforms, mixing/fades, 4K60/Shorts export presets, final capability fallback UI, foreground service, thermal checks |
| M6 reliability/release | Not implemented | Journal, autosave checkpoints, recovery snapshots, missing-media relinking, performance profiling, signed release |

M1 can collapse the source and inspector panels on wide layouts. This does not implement the full Workspace Customizer. The source trim range is not a multitrack timeline.

The editor reports source playback failures, but cannot relink missing media yet. Only one source per project is supported. Media Vault lists imported project sources. Providers that do not grant persistent read permission are rejected rather than creating a project that silently breaks after restart.

Validation export is foreground-only and uses a conservative temporary-space estimate. It is not a production export preset; bitrate and output FPS are not guaranteed. Advertised encoder checks do not establish runtime support. Output validation checks structure; manually verify decoded video and audio synchronization.

No custom OpenGL engine or direct MediaCodec/MediaMuxer engine is implemented in M1: Media3 Transformer provides real codecs, GL rendering and muxing. No background export, export resume, fake progress, or unsupported capability claims are included.

All requested later-stage studios/screens remain absent until their functionality exists. Deferred V1 features remain excluded: hook A/B comparison, AI kill detection, subject tracking, optical flow, cloud collaboration.
