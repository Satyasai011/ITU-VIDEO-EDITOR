# M9 target module migration (design only)

PR-01 keeps only :app. No modules are extracted in this pull request.

Later, in separately reviewed changes:
1. Extract Android-free immutable IDs/models and contracts to :core:model.
2. Extract reusable Compose Obsidian Prism components to :core:designsystem.
3. Move Room/SAF infrastructure to :core:data without changing persisted schema or DataStore keys.
4. Introduce :engine:media with explicit Media3/MediaCodec ownership; retain 720p exporter as regression baseline.
5. Introduce :engine:dsp after a golden PCM/native compatibility harness exists.
6. Keep :app as Android manifest, application ID, lifecycle and DI composition root.

No domain module may depend on Compose, Room, Android or Media3. No destructive Room migration. Persisted sources and project IDs must survive. Export state and publication must eventually use revision-fenced journals. Do not claim future M0-M8 design features are implemented until tests demonstrate them.
