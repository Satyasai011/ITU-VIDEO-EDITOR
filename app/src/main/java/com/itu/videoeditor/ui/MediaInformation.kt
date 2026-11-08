package com.itu.videoeditor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itu.videoeditor.domain.*

@Composable
fun MediaInformation(info: MediaInfo?) {
    val data = info ?: MediaInfo()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoLine("Encoded resolution", if (data.width != null && data.height != null) "${data.width} × ${data.height}" else "Unknown")
        InfoLine("Display resolution", data.resolutionLabel)
        InfoLine("Orientation", data.orientationLabel)
        InfoLine("Frame rate", data.frameRateLabel)
        data.frameRateEvidence?.let { Text(it, color = Muted, fontSize = 10.sp) }
        InfoLine("Video codec", codecLabel(data.codec))
        InfoLine("Video bitrate", bitrateLabel(data.videoBitrate))
        InfoLine("Container bitrate", bitrateLabel(data.containerBitrate))
        InfoLine("Source duration", data.durationUs?.let(::timeLabel) ?: "Unknown")
        InfoLine("File size", sizeLabel(data.sizeBytes))
        InfoLine("Audio tracks", data.audio?.size?.toString() ?: "Unknown")
        data.audio?.forEachIndexed { index, audio ->
            Text("Audio ${index + 1}: ${codecLabel(audio.codec)} · ${audio.sampleRate?.let { "$it Hz" } ?: "Unknown sample rate"} · ${audio.channels?.let { "$it channels" } ?: "Unknown channels"}\nBitrate ${bitrateLabel(audio.bitrate)} · Language ${audio.language?.takeUnless { it == "und" } ?: "Unknown"}", color = Muted, fontSize = 12.sp)
        }
        data.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    }
}
@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = Muted, fontSize = 10.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}
