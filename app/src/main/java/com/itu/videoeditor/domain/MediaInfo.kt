package com.itu.videoeditor.domain

import kotlin.math.abs
import java.util.Locale

data class AudioInfo(val codec: String?, val sampleRate: Int?, val channels: Int?, val bitrate: Long?, val language: String?)
data class MediaInfo(
    val width: Int? = null, val height: Int? = null, val rotation: Int? = null,
    val frameRate: Double? = null, val frameRateEvidence: String? = null, val variableTimingObserved: Boolean? = null,
    val codec: String? = null, val videoBitrate: Long? = null, val containerBitrate: Long? = null,
    val durationUs: Long? = null, val sizeBytes: Long? = null, val audio: List<AudioInfo>? = null,
    val error: String? = null
) {
    val displayWidth: Int? get() = if (rotation != null && rotation % 180 != 0) height else width
    val displayHeight: Int? get() = if (rotation != null && rotation % 180 != 0) width else height
    val resolutionLabel: String get() = if (displayWidth != null && displayHeight != null) "$displayWidth × $displayHeight" else "Unknown"
    val orientationLabel: String get() = when {
        displayWidth == null || displayHeight == null || rotation == null -> "Unknown"
        displayWidth!! > displayHeight!! -> "Landscape ($rotation°)"
        displayWidth!! < displayHeight!! -> "Portrait ($rotation°)"
        else -> "Square ($rotation°)"
    }
    val frameRateLabel: String get() = frameRate?.let { "${decimalRate(it)} FPS${if (variableTimingObserved == true) " · variable timing sampled" else ""}" } ?: "Unknown"
}

data class ProjectFormat(val width: Int?, val height: Int?, val frameRate: Double?) {
    fun valid() = (width == null || width > 0) && (height == null || height > 0) && (frameRate == null || frameRate.isFinite() && frameRate > 0)
    companion object { fun from(info: MediaInfo) = ProjectFormat(info.displayWidth, info.displayHeight, info.frameRate) }
}

data class SampledTiming(val fps: Double, val variable: Boolean)
fun analyzeTiming(timesUs: List<Long>): SampledTiming? {
    val times = timesUs.filter { it >= 0 }.distinct().sorted()
    if (times.size < 12 || times.last() <= times.first()) return null
    val deltas = times.zipWithNext { a, b -> b - a }.sorted()
    val low = deltas[deltas.size / 10].toDouble()
    val high = deltas[(deltas.size * 9 / 10).coerceAtMost(deltas.lastIndex)].toDouble()
    val variable = low > 0 && high / low > 1.05
    val median = deltas[deltas.size / 2].toDouble()
    val regular = deltas.filter { abs(it - median) <= median * 0.05 }
    val rate = if (!variable && regular.isNotEmpty()) 1_000_000.0 / regular.average()
        else (times.size - 1) * 1_000_000.0 / (times.last() - times.first())
    if (!rate.isFinite() || rate <= 0) return null
    return SampledTiming(rate, variable)
}
fun decimalRate(rate: Double) = String.format(Locale.ROOT, "%.3f", rate).trimEnd('0').trimEnd('.')
fun formatMismatch(info: MediaInfo, format: ProjectFormat): List<String> = buildList {
    if (info.displayWidth != null && format.width != null && info.displayWidth != format.width ||
        info.displayHeight != null && format.height != null && info.displayHeight != format.height) add("resolution")
    if (info.frameRate != null && format.frameRate != null && abs(info.frameRate - format.frameRate) > 0.01) add("frame rate")
    if (info.variableTimingObserved == true) add("variable timing sampled")
}
fun codecLabel(mime: String?) = when (mime) {
    null -> "Unknown"; "video/avc" -> "H.264 / AVC"; "video/hevc" -> "H.265 / HEVC"
    "video/av01" -> "AV1"; "video/x-vnd.on2.vp9" -> "VP9"; "audio/mp4a-latm" -> "AAC"
    else -> mime
}
fun sizeLabel(bytes: Long?) = bytes?.let { String.format(Locale.ROOT, "%.1f MiB", it / 1048576.0) } ?: "Unknown"
fun bitrateLabel(bits: Long?) = bits?.let { String.format(Locale.ROOT, "%.2f Mbps", it / 1_000_000.0) } ?: "Unknown"
