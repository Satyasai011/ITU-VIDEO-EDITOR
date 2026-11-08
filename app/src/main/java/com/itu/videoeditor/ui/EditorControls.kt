package com.itu.videoeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itu.videoeditor.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrimControls(startUs: Long, endUs: Long, durationUs: Long, enabled: Boolean, ready: Boolean,
                 change: (Long, Long) -> Unit, setIn: () -> Unit, setOut: () -> Unit, preview: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SOURCE TRIM", color = Violet, fontSize = 10.sp)
            Text(timeLabel(endUs - startUs), color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        RangeSlider(startUs.toFloat()..endUs.toFloat(), onValueChange = { range ->
            val start = (range.start.toLong() / 1000 * 1000).coerceIn(0, (durationUs - 1000).coerceAtLeast(0))
            val end = (range.endInclusive.toLong() / 1000 * 1000).coerceIn(1000.coerceAtMost(durationUs), durationUs)
            if (end > start) change(start, end)
        }, valueRange = 0f..durationUs.toFloat(), enabled = enabled,
            startThumb = { TrimHandle("Trim start", "IN", enabled) }, endThumb = { TrimHandle("Trim end", "OUT", enabled) },
            modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(timeLabel(startUs), color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Text(timeLabel(endUs), color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(setIn, enabled = enabled && ready, modifier = Modifier.heightIn(min = 48.dp)) { Text("Set in") }
            OutlinedButton(setOut, enabled = enabled && ready, modifier = Modifier.heightIn(min = 48.dp)) { Text("Set out") }
            OutlinedButton(preview, enabled = enabled && ready, modifier = Modifier.heightIn(min = 48.dp)) { PrismIcon(Glyph.Play); Spacer(Modifier.width(6.dp)); Text("Preview trim") }
        }
    }
}

@Composable
private fun TrimHandle(label: String, text: String, enabled: Boolean) {
    Box(Modifier.size(48.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.width(30.dp).height(40.dp).background(if (enabled) Raised else Graphite, RoundedCornerShape(8.dp))
            .border(1.dp, if (enabled) Cyan else Edge, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text(text, color = if (enabled) Cyan else Muted, fontSize = 9.sp)
        }
    }
}

@Composable
fun PlaybackControls(positionUs: Long, startUs: Long, endUs: Long, playing: Boolean, ready: Boolean,
                     playPause: () -> Unit, seek: (Float) -> Unit) {
    val duration = (endUs - startUs).coerceAtLeast(1)
    Column {
        Slider((positionUs.toDouble() / duration).toFloat().coerceIn(0f, 1f), seek, enabled = ready,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Playback position" })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(timeLabel(startUs + positionUs.coerceIn(0, duration)), Modifier.weight(1f), color = Cyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            GlyphButton(Glyph.Start, "Seek to start", enabled = ready) { seek(0f) }
            GlyphButton(if (playing) Glyph.Pause else Glyph.Play, if (playing) "Pause" else "Play", enabled = ready, primary = true, onClick = playPause)
            GlyphButton(Glyph.End, "Seek to end", enabled = ready) { seek(1f) }
            Text(timeLabel(endUs), Modifier.weight(1f), color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
