package com.itu.videoeditor

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itu.videoeditor.data.ProjectEntity
import com.itu.videoeditor.media.ValidationExporter
import com.itu.videoeditor.media.VideoInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class ExportRegressionTest {
    @Test fun realSourceTrimExportsDecodable720pMp4WithoutModifyingOriginal() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File.createTempFile("export-regression-", ".mp4", context.cacheDir)
        var output: Uri? = null
        val exporter = withContext(Dispatchers.Main) { ValidationExporter(context) }
        try {
            instrumentation.context.assets.open("metadata-5994.mp4").use { input -> source.outputStream().use { input.copyTo(it) } }
            val originalHash = MessageDigest.getInstance("SHA-256").digest(source.readBytes())
            val info = VideoInspector(context).inspect(Uri.fromFile(source)).info
            val project = ProjectEntity("test-export", "Real fixture", Uri.fromFile(source).toString(), source.name,
                info.durationUs!!, info.width!!, info.height!!, info.rotation ?: 0,
                trimStartUs = 500_000, trimEndUs = 1_500_000, createdAt = 1, updatedAt = 1)
            withContext(Dispatchers.Main) { exporter.start(project) }
            val result = withTimeout(120_000) { exporter.state.first { !it.active && (it.output != null || it.error != null) } }
            assertNull("Actual export error: ${result.error}", result.error)
            output = requireNotNull(result.output)
            val exported = VideoInspector(context).inspect(output!!).info
            assertEquals("video/avc", exported.codec)
            assertEquals(1280, exported.width); assertEquals(720, exported.height)
            assertTrue(exported.durationUs!! in 800_000L..1_200_000L)
            assertTrue(exported.audio!!.any { it.codec == "audio/mp4a-latm" })
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, output)
                val decoded = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                assertNotNull("Export must contain an actually decodable video frame", decoded)
                decoded?.recycle()
            } finally { retriever.release() }
            assertArrayEquals("Original source bytes changed", originalHash, MessageDigest.getInstance("SHA-256").digest(source.readBytes()))
        } finally {
            withContext(Dispatchers.Main) { exporter.release() }
            output?.let { context.contentResolver.delete(it, null, null) }
            source.delete()
        }
    }
}
