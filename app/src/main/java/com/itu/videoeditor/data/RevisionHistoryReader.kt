package com.itu.videoeditor.data

import android.util.Base64
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import org.json.JSONObject

/**
 * Read-only Room v3 history reader. A continuation fixes the first page's
 * upper revision bound, so newly appended revisions never enter that session.
 */
class RevisionHistoryReader(private val db: StudioDatabase) {
    private data class Cursor(val projectId: String, val limit: Int, val upper: Long, val before: Long)

    suspend fun read(projectId: String, cursor: String? = null, limit: Int = 20): RevisionHistoryResult {
        if (projectId.isBlank()) return RevisionHistoryResult.InvalidRequest("Project ID is blank")
        if (limit !in 1..20) return RevisionHistoryResult.InvalidRequest("Page size must be 1..20")
        val decoded = if (cursor == null) null else try { decode(cursor) }
            catch (_: Exception) { return RevisionHistoryResult.InvalidRequest("Invalid continuation cursor") }
        if (decoded != null && (decoded.projectId != projectId || decoded.limit != limit ||
                decoded.upper <= 0 || decoded.before <= 0 || decoded.before > decoded.upper)) {
            return RevisionHistoryResult.InvalidRequest("Cursor does not match project, page size or revision bounds")
        }

        return try {
            db.withTransaction {
                if (db.projects().get(projectId) == null) return@withTransaction RevisionHistoryResult.ProjectNotFound
                val dao = db.revisionFoundation()
                val upper = decoded?.upper ?: dao.latestRevisionNumber(projectId)
                if (upper <= 0L) return@withTransaction RevisionHistoryResult.Page(projectId, emptyList(), null)
                val rows = dao.revisionPage(projectId, upper, decoded?.before, limit + 1)
                val page = rows.take(limit)
                val grouped = if (page.isEmpty()) emptyMap() else
                    dao.operationsForRevisions(page.map { it.id }).groupBy { it.revisionId }
                val entries = page.map { revision ->
                    RevisionHistoryEntry(
                        revision.id, revision.revisionNumber, revision.createdAt, revision.snapshotVersion,
                        parseSnapshot(revision),
                        (grouped[revision.id] ?: emptyList()).map { operation ->
                            RevisionHistoryOperation(operation.id, operation.sequenceNumber,
                                operation.operationType, operation.payloadVersion, parseOperation(operation))
                        }
                    )
                }
                val next = if (rows.size > limit && page.isNotEmpty()) {
                    encode(Cursor(projectId, limit, upper, page.last().revisionNumber))
                } else null
                RevisionHistoryResult.Page(projectId, entries, next)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RevisionHistoryResult.ReadFailure(e.javaClass.simpleName)
        }
    }

    private fun parseSnapshot(row: ProjectRevisionEntity): HistoryPayload {
        if (row.snapshotVersion != 1) return HistoryPayload.UnsupportedVersion(row.snapshotVersion, "snapshot")
        return try {
            val j = JSONObject(row.snapshotJson)
            val id = j.getString("projectId")
            val uri = j.getString("sourceUri")
            val duration = j.getLong("durationUs")
            val start = j.getLong("trimStartUs")
            val end = j.getLong("trimEndUs")
            require(id == row.projectId && uri.isNotBlank() && duration > 0 && start >= 0 && end > start && end <= duration)
            val format = if (j.isNull("formatJson")) null else j.getString("formatJson")
            HistoryPayload.TrimSnapshot(id, uri, duration, start, end, format)
        } catch (_: Exception) { HistoryPayload.CorruptRecord("Invalid v1 snapshot") }
    }

    private fun parseOperation(row: TimelineOperationEntity): HistoryPayload {
        if (row.operationType != "trim" || row.payloadVersion != 1) {
            return HistoryPayload.UnsupportedVersion(row.payloadVersion, row.operationType)
        }
        return try {
            val j = JSONObject(row.payloadJson)
            val previousStart = j.getLong("previousStartUs")
            val previousEnd = j.getLong("previousEndUs")
            val start = j.getLong("startUs")
            val end = j.getLong("endUs")
            require(previousStart >= 0 && previousEnd > previousStart && start >= 0 && end > start)
            HistoryPayload.TrimOperation(previousStart, previousEnd, start, end)
        } catch (_: Exception) { HistoryPayload.CorruptRecord("Invalid v1 trim operation") }
    }

    private fun encode(cursor: Cursor): String {
        val json = JSONObject().put("v", 1).put("projectId", cursor.projectId)
            .put("limit", cursor.limit).put("upper", cursor.upper).put("before", cursor.before)
        return Base64.encodeToString(json.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun decode(raw: String): Cursor {
        require(raw.isNotEmpty() && raw.length <= 4096)
        val bytes = Base64.decode(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        require(json.getInt("v") == 1)
        return Cursor(json.getString("projectId"), json.getInt("limit"),
            json.getLong("upper"), json.getLong("before"))
    }
}
