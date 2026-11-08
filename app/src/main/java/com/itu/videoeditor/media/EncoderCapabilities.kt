package com.itu.videoeditor.media

import android.media.MediaCodecList
import android.media.MediaFormat

/** Queries the device, never assumes a particular tablet/SoC has a working configuration. */
object EncoderCapabilities {
    fun report(): String = buildString {
        appendLine("DEVICE ENCODER CAPABILITIES")
        appendLine("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · API ${android.os.Build.VERSION.SDK_INT}")
        appendLine("Advertised capabilities only. An actual export must still pass.")
        for (mime in listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC)) {
            appendLine("\n$mime")
            val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { it.isEncoder && it.supportedTypes.any { type -> type.equals(mime, true) } }
            if (codecs.isEmpty()) appendLine("No encoder advertised")
            codecs.forEach { codec ->
                appendLine("${codec.name} · ${if (codec.isHardwareAccelerated) "hardware" else "software"}")
                runCatching {
                    val caps = codec.getCapabilitiesForType(mime)
                    val video = caps.videoCapabilities
                    val sizeRate = video.areSizeAndRateSupported(3840, 2160, 60.0)
                    appendLine("4K60: ${if (sizeRate) "advertised" else "unsupported"}; bitrate ${video.bitrateRange}")
                    for (mbps in listOf(55, 70)) {
                        val format = MediaFormat.createVideoFormat(mime, 3840, 2160).apply {
                            setInteger(MediaFormat.KEY_FRAME_RATE, 60)
                            setInteger(MediaFormat.KEY_BIT_RATE, mbps * 1_000_000)
                        }
                        appendLine("4K60 @ $mbps Mbps: ${if (caps.isFormatSupported(format)) "advertised" else "unsupported"}")
                    }
                    appendLine("1080×1920 @ 60: ${video.areSizeAndRateSupported(1080, 1920, 60.0)}")
                }.onFailure { appendLine("Query failed: ${it.message}") }
            }
        }
    }
}
