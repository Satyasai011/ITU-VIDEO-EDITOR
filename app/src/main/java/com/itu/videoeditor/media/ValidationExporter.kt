package com.itu.videoeditor.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import com.itu.videoeditor.data.ProjectEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** M1 diagnostic path. Activity-bound; deliberately cancels if the app leaves foreground. */
@UnstableApi
class ValidationExporter(private val context: Context) {
    data class State(val active: Boolean = false, val canCancel: Boolean = false, val stage: String = "Ready", val progress: Int? = null, val output: Uri? = null, val error: String? = null)
    val state = MutableStateFlow(State())
    private var transformer: Transformer? = null
    private var temporary: File? = null
    private var publishJob: Job? = null
    private var preparationJob: Job? = null
    private var released = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            val current = transformer ?: return
            val holder = ProgressHolder()
            val available = current.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE
            state.value = state.value.copy(progress = if (available) holder.progress else null)
            handler.postDelayed(this, 250)
        }
    }
    fun start(project: ProjectEntity) {
        if (state.value.active) return
        state.value = State(active = true, canCancel = true, stage = "Checking source and storage")
        preparationJob = scope.launch {
        try {
            withContext(Dispatchers.IO) {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(project.sourceUri), "r")?.use { } ?: error("Missing source media")
            // Conservative space guard for the temporary file and final MediaStore copy.
            val estimated = ((project.trimEndUs - project.trimStartUs) / 1_000_000.0 * 12_000_000 / 8 * 2).toLong() + 128L * 1024 * 1024
            check(StatFs(context.cacheDir.path).availableBytes > estimated) { "Not enough free storage for validation export" }
            }
            temporary = File.createTempFile("itu-validation-", ".mp4", context.cacheDir)
            val item = MediaItem.Builder().setUri(project.sourceUri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(project.trimStartUs / 1000).setEndPositionMs(project.trimEndUs / 1000).build()).build()
            // Force real video rendering/encoding rather than passing through compressed samples.
            val edited = EditedMediaItem.Builder(item).setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(720)))).build()
            val defaultEncoders = DefaultEncoderFactory.Builder(context).build()
            val validationEncoders = object : Codec.EncoderFactory by defaultEncoders {
                override fun videoNeedsEncoding() = true
                override fun audioNeedsEncoding() = true
            }
            transformer = Transformer.Builder(context).setEncoderFactory(validationEncoders)
                .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(poll); transformer = null
                        state.value = state.value.copy(stage = "Validating and publishing MP4", progress = null, canCancel = false)
                        publishJob = scope.launch {
                            try {
                                val uri = withContext(Dispatchers.IO) { validateAndPublish(temporary ?: error("Temporary file missing")) }
                                state.value = State(stage = "Export complete · MP4 track validation passed\nVideo encoder: ${exportResult.videoEncoderName ?: "unreported"}", output = uri)
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { fail(e) }
                            finally { temporary?.delete(); temporary = null; if (released) scope.cancel() }
                        }
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { fail(exportException) }
                }).build()
            state.value = State(active = true, canCancel = true, stage = "Rendering H.264 / AAC · 720p validation")
            transformer!!.start(edited, temporary!!.absolutePath)
            handler.post(poll)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { fail(e) }
        }
    }
    private fun validateAndPublish(file: File): Uri {
        check(file.length() > 0) { "Encoder produced an empty file" }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var videoTrack = -1
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    videoTrack = index
                    check(format.getLong(MediaFormat.KEY_DURATION) > 0) { "Invalid video duration" }
                }
            }
            check(videoTrack >= 0) { "No video track in export" }
            extractor.selectTrack(videoTrack)
            check(extractor.sampleTime >= 0) { "No playable video samples" }
        } finally { extractor.release() }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ITU-validation-${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ITU Video Editor")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }) ?: error("Could not create MediaStore output")
        try {
            resolver.openOutputStream(uri, "w")?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("Could not open export destination")
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) == 1)
            return uri
        } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
    }
    private fun fail(error: Exception) {
        handler.removeCallbacks(poll); transformer?.cancel(); transformer = null
        temporary?.delete(); temporary = null
        state.value = State(stage = "Export failed", error = "${error.javaClass.simpleName}: ${error.message}")
    }
    fun cancel() {
        if (!state.value.active) return
        // Publishing is a short atomic copy: let it finish, then release its temporary file.
        if (publishJob?.isActive == true) return
        preparationJob?.cancel()
        handler.removeCallbacks(poll); transformer?.cancel(); transformer = null
        temporary?.delete(); temporary = null
        state.value = State(stage = "Cancelled · restart export to retry")
    }
    fun release() { released = true; cancel(); if (publishJob?.isActive != true) scope.cancel() }
}
