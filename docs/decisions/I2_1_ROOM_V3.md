# I2.1 Room v3 foundation

Status: implementation branch, pending CI and review. No merge or release authorized.

Existing `projects` and `media_sources` tables, project IDs, source URIs, trim microseconds, project-format JSON, DataStore and signing configuration are unchanged.

The additive `MIGRATION_2_3` creates:
- `project_revisions`: immutable revision payloads with unique (projectId, revisionNumber) and a restrictive FK to projects.
- `timeline_operations`: operation payloads ordered by unique (revisionId, sequenceNumber), FK to project_revisions.
- `source_pins`: expected metadata and source availability for each project/source URI, FK to projects.

All tables are empty on migration. No synthetic history is created for legacy projects. No source bytes are stored, removed or modified. No new UI, automatic revision capture, autosave or recovery is enabled by this change. Existing `trim()` remains the current authoritative SQL update until the later transactional integration milestone.

The application registers both `MIGRATION_1_2` and `MIGRATION_2_3`, so v1 installations can upgrade sequentially.

Required gates: Android build, unit/lint, emulator legacy v1→v3 and v2→v3 data preservation, new v3 transaction/foreign-key tests, existing media and export regression tests. Generated Room schema v3 export must be retained and reviewed before final approval. A successful build is not device certification.

Existing M1.1 APK signing-key mismatch remains unresolved; debug artifacts may be fresh-install-only. Never uninstall an existing app containing project data. No release APK or merge without separate approval.
