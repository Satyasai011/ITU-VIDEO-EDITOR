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
    suspend fun trim(id: String, range: TrimRange) { db.projects().trim(id, range.startUs, range.endUs, System.currentTimeMillis()) }
    suspend fun isReadable(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false }.getOrDefault(false)
    }
}
