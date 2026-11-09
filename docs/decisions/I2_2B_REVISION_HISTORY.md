# I2.2B: Paginated Read-Only Revision History API

Implementation branch only. Requires CI and separate merge approval. No release APK.

## Contract
`StudioRepository.getRevisionHistory(projectId, cursor = null, limit = 20)` returns `RevisionHistoryResult`. Page sizes 1–20 are supported; default 20. Newest revision first. The continuation is versioned, URL-safe Base64 JSON containing the project binding, fixed first-page upper revision bound, exclusive next revision boundary and page size. It is not an authorization token; malformed and mismatched tokens are rejected. New revisions created after page one cannot enter the existing session.

Each page runs in one Room read transaction: check project existence, capture first-page upper bound, fetch at most 21 revision rows, batch-fetch operations for up to 20 displayed revisions and map immutable results. The 21st row determines continuation. Empty history for an existing project is valid; missing project is explicit.

## Validation
Known snapshot v1 requires project ID, source URI, duration and valid trim bounds; format JSON is nullable. Known operation is trim v1 with valid previous and current ranges. Unsupported versions/types and corrupt JSON are visible in the returned history and never silently skipped. Database errors return ReadFailure; coroutine cancellation propagates.

## Boundaries
No schema changes (Room v3), migrations, UI, history restoration, undo/redo, source/media access, source pins, repair writes or new permissions. Existing project trims and export remain unchanged. Existing signing compatibility caveat persists.

## CI gates
Debug APK, JVM tests, lint, androidTest compilation, API35 emulator instrumentation including new history tests, prior migration and atomic trim tests, and existing export regression must pass. No merge or release without explicit user approval.
