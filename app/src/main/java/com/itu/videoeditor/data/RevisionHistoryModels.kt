package com.itu.videoeditor.data

/** Immutable, read-only history contracts. No Room entities escape the reader. */
sealed interface RevisionHistoryResult {
    data class Page(
        val projectId: String,
        val entries: List<RevisionHistoryEntry>,
        val nextCursor: String?
    ) : RevisionHistoryResult
    data object ProjectNotFound : RevisionHistoryResult
    data class InvalidRequest(val reason: String) : RevisionHistoryResult
    data class ReadFailure(val reason: String) : RevisionHistoryResult
}

data class RevisionHistoryEntry(
    val id: String,
    val revisionNumber: Long,
    val createdAt: Long,
    val snapshotVersion: Int,
    val snapshot: HistoryPayload,
    val operations: List<RevisionHistoryOperation>
)

data class RevisionHistoryOperation(
    val id: String,
    val sequenceNumber: Long,
    val operationType: String,
    val payloadVersion: Int,
    val payload: HistoryPayload
)

sealed interface HistoryPayload {
    data class TrimSnapshot(
        val projectId: String, val sourceUri: String, val durationUs: Long,
        val trimStartUs: Long, val trimEndUs: Long, val formatJson: String?
    ) : HistoryPayload
    data class TrimOperation(
        val previousStartUs: Long, val previousEndUs: Long,
        val startUs: Long, val endUs: Long
    ) : HistoryPayload
    data class UnsupportedVersion(val version: Int, val type: String) : HistoryPayload
    data class CorruptRecord(val reason: String) : HistoryPayload
}
