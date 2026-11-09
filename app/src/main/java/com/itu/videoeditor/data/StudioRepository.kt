package com.itu.videoeditor.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.itu.videoeditor.domain.*
import com.itu.videoeditor.media.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import org.json.JSONObject

private val Context.preferences by preferencesDataStore("studio_preferences")
data class StudioPreferences(val muted: Boolean = false, val vaultGrid: Boolean = true, val loopPreview: Boolean = false,
    val showHints: Boolean = true, val reduceMotion: Boolean = false, val collapsePanels: Boolean = false)

class StudioRepository(private val context: Context, private val db: StudioDatabase) {
    val projects = db.projects().observeProjects()
    val sources = db.sources().observeSources()
    fun project(id: String) = db.projects().observeProject(id)
    private val inspector = VideoInspector(context)
    private val inspectGate = Mutex()
    private val gridKey = booleanPreferencesKey("vault_grid")
    private val muteKey = booleanPreferencesKey("mute_preview")
    private val lastFormatKey = stringPreferencesKey("last_confirmed_format")
    val preferences = context.preferences.data.map { p -> StudioPreferences(p[muteKey] ?: false, p[gridKey] ?: true,
        p[booleanPreferencesKey("loop_preview")] ?: false, p[booleanPreferencesKey("show_hints")] ?: true,
        p[booleanPreferencesKey("reduce_motion")] ?: false, p[booleanPreferencesKey("collapse_panels")] ?: false) }
    val vaultGrid = preferences.map { it.vaultGrid }
    val muted = preferences.map { it.muted }
    val lastFormat = context.preferences.data.map { MetadataJson.readFormat(it[lastFormatKey]) }
    suspend fun setVaultGrid(grid: Boolean) { context.preferences.edit { it[gridKey] = grid } }
    suspend fun setMuted(muted: Boolean) { context.preferences.edit { it[muteKey] = muted } }
    suspend fun setPreference(key: String, value: Boolean) {
        require(key in setOf("loop_preview", "show_hints", "reduce_motion", "collapse_panels"))
        context.preferences.edit { it[booleanPreferencesKey(key)] = value }
    }
    suspend fun inspectImport(uri: Uri): InspectedVideo = inspectGate.withLock {
        withContext(Dispatchers.IO) { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val video = inspector.inspect(uri)
        persist(video)
        video
    }
    private suspend fun persist(video: InspectedVideo) {
        val old = db.sources().get(video.uri)
        val now = System.currentTimeMillis()
        db.sources().save(MediaSourceEntity(video.uri, video.name, MetadataJson.encode(video.info), old?.importedAt ?: now, now))
    }
    suspend fun refresh(uri: String) = inspectGate.withLock { persist(inspector.inspect(Uri.parse(uri))) }
    suspend fun ensureLegacySources() {
        val existing = projects.first().distinctBy { it.sourceUri }
        for (project in existing) {
            if (db.sources().get(project.sourceUri) != null) continue
            inspectGate.withLock {
                if (db.sources().get(project.sourceUri) != null) return@withLock
                val video = try { inspector.inspect(Uri.parse(project.sourceUri)) }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { InspectedVideo(project.sourceUri, project.sourceName, MediaInfo(
                    width = project.width.takeIf { it > 0 }, height = project.height.takeIf { it > 0 }, rotation = project.rotation.takeIf { it != 0 },
                    durationUs = project.durationUs.takeIf { it > 0 }, error = "Metadata refresh failed: ${e.message}")) }
                persist(video)
            }
        }
    }
    suspend fun createProject(video: InspectedVideo, name: String, format: ProjectFormat): String {
        require(format.valid()) { "Project format values must be positive or Unknown." }
        require(isReadable(video.uri)) { "Source media is missing or read permission was lost. Select the original video again." }
        val duration = requireNotNull(video.info.durationUs?.takeIf { it > 0 }) { "The source duration is Unknown; editing requires a readable duration." }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        db.withTransaction {
            persist(video)
            db.projects().insert(ProjectEntity(id, name.trim().ifBlank { video.name.substringBeforeLast('.') }, video.uri, video.name,
                duration, video.info.width ?: 0, video.info.height ?: 0, video.info.rotation ?: 0, trimEndUs = duration,
                createdAt = now, updatedAt = now, formatJson = MetadataJson.format(format)))
        }
        // Project commit is authoritative; preference failures must never create a duplicate on retry.
        runCatching { context.preferences.edit { it[lastFormatKey] = MetadataJson.format(format) } }
        return id
    }
    /**
     * Atomic trim commit. The project row, immutable revision and its operation
     * either all persist or all roll back. Existing projects gain history only
     * when a user actually saves a new trim.
     */
    suspend fun trim(id: String, range: TrimRange) {
        db.withTransaction {
            val project = requireNotNull(db.projects().get(id)) { "Project not found." }
            require(range.startUs >= 0L && range.endUs > range.startUs && range.endUs <= project.durationUs) {
                "Trim must be within the original video duration."
            }
            if (project.trimStartUs == range.startUs && project.trimEndUs == range.endUs) return@withTransaction

            val number = db.revisionFoundation().latestRevisionNumber(id) + 1L
            require(number > 0L) { "Revision sequence overflow." }
            val now = System.currentTimeMillis()
            val revisionId = UUID.randomUUID().toString()
            val snapshot = JSONObject()
                .put("projectId", project.id)
                .put("sourceUri", project.sourceUri)
                .put("durationUs", project.durationUs)
                .put("trimStartUs", range.startUs)
                .put("trimEndUs", range.endUs)
                .put("formatJson", project.formatJson ?: JSONObject.NULL)
                .toString()
            val operation = JSONObject()
                .put("previousStartUs", project.trimStartUs)
                .put("previousEndUs", project.trimEndUs)
                .put("startUs", range.startUs)
                .put("endUs", range.endUs)
                .toString()
            val revisions = db.revisionFoundation()
            revisions.insertRevision(ProjectRevisionEntity(revisionId, id, number, 1, snapshot, now))
            revisions.insertOperation(TimelineOperationEntity(UUID.randomUUID().toString(), revisionId, 1L, "trim", 1, operation, now))
            db.projects().trim(id, range.startUs, range.endUs, now)
        }
    }
    /**
     * Persist a source-relative split and its immutable revision in one Room transaction.
     * This does not change the project's trim or the original media file.
     */
    suspend fun split(id: String, segmentIndex: Int, atUs: Long): List<SourceSegment> = db.withTransaction {
        val project = requireNotNull(db.projects().get(id)) { "Project not found." }
        val latest = db.revisionFoundation().revisions(id).lastOrNull()
        val segments = if (latest != null && JSONObject(latest.snapshotJson).has("timelineVersion")) {
            SegmentSnapshotCodec.decode(latest.snapshotJson, id, project.sourceUri, project.durationUs)
        } else {
            listOf(SourceSegment(project.trimStartUs, project.trimEndUs))
        }
        val updated = splitSegmentAt(segments, segmentIndex, atUs)
        val number = (latest?.revisionNumber ?: 0L) + 1L
        require(number > 0L) { "Revision sequence overflow." }
        val now = System.currentTimeMillis()
        val revisionId = UUID.randomUUID().toString()
        val snapshot = JSONObject(SegmentSnapshotCodec.encode(id, project.sourceUri, updated))
            .put("durationUs", project.durationUs)
            .put("trimStartUs", project.trimStartUs)
            .put("trimEndUs", project.trimEndUs)
            .put("formatJson", project.formatJson ?: JSONObject.NULL)
            .toString()
        val operation = JSONObject()
            .put("segmentIndex", segmentIndex)
            .put("atUs", atUs)
            .put("previousSegmentCount", segments.size)
            .put("newSegmentCount", updated.size)
            .toString()
        db.revisionFoundation().insertRevision(ProjectRevisionEntity(revisionId, id, number, 1, snapshot, now))
        db.revisionFoundation().insertOperation(TimelineOperationEntity(
            UUID.randomUUID().toString(), revisionId, 1L, "split", 1, operation, now
        ))
        updated
    }

    /** Returns the latest committed segments, or the legacy single trim range. */
    suspend fun loadSegments(id: String): List<SourceSegment> {
        val project = requireNotNull(db.projects().get(id)) { "Project not found." }
        val latest = db.revisionFoundation().revisions(id).lastOrNull()
        return if (latest != null && JSONObject(latest.snapshotJson).has("timelineVersion")) {
            SegmentSnapshotCodec.decode(latest.snapshotJson, id, project.sourceUri, project.durationUs)
        } else {
            listOf(SourceSegment(project.trimStartUs, project.trimEndUs))
        }
    }

    /** Read-only, keyset-paginated immutable revision history. */
    suspend fun getRevisionHistory(
        projectId: String, cursor: String? = null, limit: Int = 20
    ): RevisionHistoryResult = RevisionHistoryReader(db).read(projectId, cursor, limit)

    suspend fun isReadable(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false }.getOrDefault(false)
    }
}
