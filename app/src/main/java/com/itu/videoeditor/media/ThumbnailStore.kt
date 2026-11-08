package com.itu.videoeditor.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.itu.videoeditor.data.ProjectEntity
import com.itu.videoeditor.domain.displayAspect
import com.itu.videoeditor.domain.MediaInfo
import com.itu.videoeditor.data.MediaSourceEntity
import com.itu.videoeditor.data.MetadataJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

data class ThumbnailRequest(val sourceUri: String, val sourceName: String, val durationUs: Long, val width: Int, val height: Int, val rotation: Int, val trimStartUs: Long = 0)
fun ProjectEntity.thumbnailRequest(info: MediaInfo? = null) = ThumbnailRequest(sourceUri, sourceName, info?.durationUs ?: durationUs, info?.width ?: width, info?.height ?: height, info?.rotation ?: rotation, trimStartUs)
fun MediaSourceEntity.thumbnailRequest(): ThumbnailRequest {
    val info = MetadataJson.decode(metadataJson)
    return ThumbnailRequest(uri, name, info.durationUs ?: 0, info.width ?: 0, info.height ?: 0, info.rotation ?: 0)
}

/** One background retriever at a time; bounded bitmap memory and disposable disk cache. */
class ThumbnailStore(private val context: Context) {
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 32).coerceIn(2L * 1024 * 1024, 24L * 1024 * 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val gate = Mutex()
    private val directory get() = File(context.cacheDir, "prism-thumbnails-v1")
    fun clearMemory() = memory.evictAll()

    suspend fun cacheBytes(): Long = withContext(Dispatchers.IO) { gate.withLock { directory.listFiles()?.sumOf { it.length() } ?: 0L } }
    suspend fun clearCache() = withContext(Dispatchers.IO) { gate.withLock { memory.evictAll(); directory.deleteRecursively(); Unit } }
    suspend fun load(project: ThumbnailRequest, refresh: Boolean = false): Bitmap = withContext(Dispatchers.IO) {
        val identity = "${project.sourceUri}|${project.trimStartUs}|${project.durationUs}|${project.width}|${project.height}|${project.rotation}"
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        gate.withLock {
            coroutineContext.ensureActive()
            val file = File(directory, "$key.jpg")
            if (refresh) { memory.remove(key); file.delete() }
            memory.get(key)?.let { return@withLock it }
            if (file.exists()) {
                BitmapFactory.decodeFile(file.path)?.let { bitmap ->
                    file.setLastModified(System.currentTimeMillis()); memory.put(key, bitmap); return@withLock bitmap
                }
                file.delete()
            }
            val retriever = MediaMetadataRetriever()
            val bitmap = try {
                retriever.setDataSource(context, Uri.parse(project.sourceUri))
                val aspect = displayAspect(project.width, project.height, project.rotation)
                val width = if (aspect >= 4f / 3f) 480 else (360 * aspect).roundToInt().coerceAtLeast(1)
                val height = (width / aspect).roundToInt().coerceIn(1, 360)
                retriever.getScaledFrameAtTime(project.trimStartUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height)
                    ?: error("No video frame could be read")
            } finally { retriever.release() }
            coroutineContext.ensureActive()
            memory.put(key, bitmap)
            // A full cache or disk write failure must not hide a successfully decoded thumbnail.
            runCatching {
                directory.mkdirs()
                val temporary = File(directory, "$key.tmp")
                try {
                    temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 84, it) }
                    check(temporary.renameTo(file))
                } finally { temporary.delete() }
                var size = directory.listFiles()?.sumOf { it.length() } ?: 0L
                directory.listFiles()?.sortedBy { it.lastModified() }?.forEach {
                    if (size > 48L * 1024 * 1024) { val length = it.length(); if (it.delete()) size -= length }
                }
            }
            bitmap
        }
    }
}
