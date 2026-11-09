# I2.2A Atomic Trim Revision Persistence

Status: implementation branch; CI and review required. No merge or release authorized.

## Transaction contract

`StudioRepository.trim(id, range)` uses a single Room `withTransaction`. It loads the authoritative project inside the transaction, checks the requested bounds against the stored duration, and returns without writing for an unchanged trim. A missing project or invalid bounds fails without creating history.

For a changed trim, the same transaction:
1. Reads the highest persisted per-project revision number and assigns the next number.
2. Inserts an immutable `project_revisions` snapshot (payload version 1) containing the project ID, original source URI, duration, new trim microseconds and original format JSON.
3. Inserts one `timeline_operations` record (type `trim`, payload version 1, sequence 1) with the previous and new trim bounds.
4. Updates the existing `projects` trim fields and updated timestamp.

If any statement fails, Room rolls back all three writes. Concurrent writes through the same Room database instance serialize through Room's transaction machinery. The database unique index remains the final duplicate-revision guard. Existing projects do not receive synthetic history.

## Scope and exclusions

Room schema remains version 3, with no migrations, table or column changes. App ID, signing, permissions, source media, export pipeline and UI stay unchanged. This change does not provide undo/redo, revision restoration, automatic capture of edits other than trim, background autosave, or crash recovery. No destructive fallback is allowed.

## Acceptance gates

Run the existing Android debug APK build, unit tests, lint, API 35 emulator suite, legacy v1→v3 and v2→v3 migration tests, and existing 720p export regression. New emulator tests cover atomic successful trim, ordered revisions, unchanged trim, invalid/missing project and forced database update failure with rollback. Keep PR in draft until CI and review pass. Do not merge or publish an APK release without separate approval.
