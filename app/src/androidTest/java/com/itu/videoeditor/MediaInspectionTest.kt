package com.itu.videoeditor

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itu.videoeditor.data.MetadataJson
import com.itu.videoeditor.media.VideoInspector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaInspectionTest {
    @Test fun actualMp4ReportsFractionalFpsCodecAudioAndSize() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "metadata-test.mp4")
        try {
            instrumentation.context.assets.open("metadata-5994.mp4").use { source -> file.outputStream().use { source.copyTo(it) } }
            val info = VideoInspector(context).inspect(Uri.fromFile(file)).info
            assertEquals(320, info.width); assertEquals(180, info.height)
            assertEquals(60000.0 / 1001, info.frameRate!!, 0.02)
            assertEquals("video/avc", info.codec)
            assertEquals(file.length(), info.sizeBytes)
            assertTrue(info.durationUs!! > 1_900_000)
            assertEquals("audio/mp4a-latm", info.audio!!.single().codec)
            assertEquals(44100, info.audio!!.single().sampleRate)
            assertEquals(1, info.audio!!.single().channels)
            assertEquals(info, MetadataJson.decode(MetadataJson.encode(info)))
        } finally { file.delete() }
    }
}
