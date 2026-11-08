package com.itu.videoeditor.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.itu.videoeditor.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class InspectedVideo(val uri: String, val name: String, val info: MediaInfo)

class VideoInspector(private val context: Context) {
    suspend fun inspect(uri: Uri): InspectedVideo = withContext(Dispatchers.IO) {
        var name = uri.lastPathSegment ?: "Imported video"
        var size: Long? = null
        runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex).takeIf { it >= 0 }
            }
        }
        }
        if (size == null) size = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
            it.length.takeIf { value -> value >= 0 } ?: it.parcelFileDescriptor.statSize.takeIf { value -> value >= 0 }
        } }.getOrNull()
        val extractor = MediaExtractor()
        val retriever = MediaMetadataRetriever()
        try {
            extractor.setDataSource(context, uri, null)
            // Retriever supplies container metadata; failures do not discard usable track metadata.
            runCatching { retriever.setDataSource(context, uri) }
            fun meta(key: Int) = runCatching { retriever.extractMetadata(key) }.getOrNull()
            var video: MediaFormat? = null
            var trackIndex = -1
            val audio = mutableListOf<AudioInfo>()
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.string(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true && video == null) { video = format; trackIndex = index }
                if (mime?.startsWith("audio/") == true) audio += AudioInfo(mime, format.number(MediaFormat.KEY_SAMPLE_RATE)?.toInt()?.takeIf { it > 0 },
                    format.number(MediaFormat.KEY_CHANNEL_COUNT)?.toInt()?.takeIf { it > 0 }, format.number(MediaFormat.KEY_BIT_RATE)?.toLong()?.takeIf { it > 0 }, format.string(MediaFormat.KEY_LANGUAGE))
            }
            val format = requireNotNull(video) { "This file has no readable video track." }
            extractor.selectTrack(trackIndex)
            val timestamps = mutableListOf<Long>()
            repeat(360) {
                coroutineContext.ensureActive()
                val time = extractor.sampleTime
                if (time < 0) return@repeat
                timestamps += time
                extractor.advance()
            }
            val timing = analyzeTiming(timestamps)
            val declared = format.number(MediaFormat.KEY_FRAME_RATE)?.toDouble()?.takeIf { it.isFinite() && it > 0 }
            val duration = format.number(MediaFormat.KEY_DURATION)?.toLong()?.takeIf { it > 0 }
                ?: meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }?.times(1000)
            InspectedVideo(uri.toString(), name, MediaInfo(
                width = format.number(MediaFormat.KEY_WIDTH)?.toInt()?.takeIf { it > 0 },
                height = format.number(MediaFormat.KEY_HEIGHT)?.toInt()?.takeIf { it > 0 },
                rotation = format.number(MediaFormat.KEY_ROTATION)?.toInt() ?: meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull(),
                frameRate = timing?.fps ?: declared, frameRateEvidence = if (timing != null) "Sampled presentation timestamps (up to 360 frames)" else if (declared != null) "Reported video-track frame rate" else null,
                variableTimingObserved = timing?.variable, codec = format.string(MediaFormat.KEY_MIME),
                videoBitrate = format.number(MediaFormat.KEY_BIT_RATE)?.toLong()?.takeIf { it > 0 },
                containerBitrate = meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.takeIf { it > 0 },
                durationUs = duration, sizeBytes = size, audio = audio))
        } finally { extractor.release(); retriever.release() }
    }
}
private fun MediaFormat.number(key: String): Number? = if (containsKey(key)) runCatching { getNumber(key) }.getOrNull() else null
private fun MediaFormat.string(key: String): String? = if (containsKey(key)) runCatching { getString(key) }.getOrNull() else null
