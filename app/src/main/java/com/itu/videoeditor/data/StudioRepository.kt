package com.itu.videoeditor.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.itu.videoeditor.domain.TrimRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

private val Context.preferences by preferencesDataStore("studio_preferences")
class StudioRepository(private val context: Context, private val db: StudioDatabase) {
    val projects = db.projects().observeProjects()
    fun project(id: String) = db.projects().observeProject(id)
    private val gridKey = booleanPreferencesKey("vault_grid")
    val vaultGrid = context.preferences.data.map { it[gridKey] ?: true }
    suspend fun setVaultGrid(grid: Boolean) { context.preferences.edit { it[gridKey] = grid } }
    private val muteKey = booleanPreferencesKey("mute_preview")
    val muted = context.preferences.data.map { it[muteKey] ?: false }
    suspend fun setMuted(muted: Boolean) { context.preferences.edit { it[muteKey] = muted } }

    suspend fun import(uri: Uri, projectName: String): String = withContext(Dispatchers.IO) {
        // A project may only reference media we can still open after process death.
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Imported video"
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            require(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "Select a file with a video track." }
            val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            require(durationUs > 0) { "This video's duration could not be read." }
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            db.projects().insert(ProjectEntity(id, projectName.trim().ifBlank { name.substringBeforeLast('.') }, uri.toString(), name,
                durationUs,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
                trimEndUs = durationUs, createdAt = now, updatedAt = now))
            id
        } finally { retriever.release() }
    }
    suspend fun trim(id: String, range: TrimRange) {
        db.projects().trim(id, range.startUs, range.endUs, System.currentTimeMillis())
    }
    suspend fun isReadable(uri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false }.getOrDefault(false)
    }
}
