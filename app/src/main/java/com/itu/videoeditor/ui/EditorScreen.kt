package com.itu.videoeditor.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.itu.videoeditor.data.ProjectEntity
import com.itu.videoeditor.media.ValidationExporter
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(project: ProjectEntity, muted: Boolean, save: (ProjectEntity, Long, Long) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(project.id) { ExoPlayer.Builder(context).build() }
    val exporter = remember(project.id) { ValidationExporter(context.applicationContext) }
    val export by exporter.state.collectAsStateWithLifecycle()
    var playbackError by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var leftVisible by rememberSaveable { mutableStateOf(true) }
    var rightVisible by rememberSaveable { mutableStateOf(true) }
    var startUs by rememberSaveable(project.id, project.trimStartUs) { mutableLongStateOf(project.trimStartUs) }
    var endUs by rememberSaveable(project.id, project.trimEndUs) { mutableLongStateOf(project.trimEndUs) }
    var previewStartUs by remember { mutableLongStateOf(project.trimStartUs) }
    var previewEndUs by remember { mutableLongStateOf(project.trimEndUs) }
    val dirty = startUs != project.trimStartUs || endUs != project.trimEndUs
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(project.sourceUri, project.trimStartUs, project.trimEndUs) {
        ready = false
        playbackError = null
        previewStartUs = project.trimStartUs
        previewEndUs = project.trimEndUs
        player.setMediaItem(MediaItem.Builder().setUri(project.sourceUri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(project.trimStartUs / 1000).setEndPositionMs(project.trimEndUs / 1000).build()).build())
        player.prepare()
    }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { playbackError = "${error.errorCodeName}: ${error.message}. Check that the source file is still available in its original location." }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { ready = state == Player.STATE_READY || state == Player.STATE_ENDED }
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { player.pause(); exporter.cancel() } }
        player.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release(); exporter.release() }
    }
    LaunchedEffect(player) { while (true) { positionMs = player.currentPosition; delay(100) } }
    fun preview() {
        previewStartUs = startUs
        previewEndUs = endUs
        player.setMediaItem(MediaItem.Builder().setUri(project.sourceUri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startUs / 1000).setEndPositionMs(endUs / 1000).build()).build())
        player.prepare(); player.play()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text(project.name, style = MaterialTheme.typography.titleLarge, maxLines = 1); Text("SOURCE PREVIEW / M1", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp) }
            TextButton(onClick = { leftVisible = !leftVisible }) { Text(if (leftVisible) "Hide media" else "Media") }
            TextButton(onClick = { rightVisible = !rightVisible }) { Text(if (rightVisible) "Hide inspector" else "Inspector") }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 900.dp
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (wide && leftVisible) Panel(Modifier.width(200.dp).fillMaxHeight()) {
                    Text("MEDIA VAULT", color = Muted, fontSize = 11.sp)
                    Text(project.sourceName, style = MaterialTheme.typography.titleMedium)
                    Text("${project.width} × ${project.height}\nRotation ${project.rotation}°\n${timeLabel(project.durationUs)}", color = Muted, fontSize = 12.sp)
                    HorizontalDivider()
                    Text("One source · linked original audio", color = Violet, fontSize = 12.sp)
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
                        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); keepScreenOn = true } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
                        if (!ready && playbackError == null) CircularProgressIndicator()
                        playbackError?.let { Surface(color = Graphite.copy(alpha = 0.95f), modifier = Modifier.padding(20.dp)) { Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(timeLabel(previewStartUs + positionMs * 1000), color = Cyan, fontSize = 12.sp)
                        TextButton(onClick = { if (playing) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }, enabled = playbackError == null && ready) { Text(if (playing) "Pause" else "Play") }
                        Text(timeLabel(previewEndUs), color = Muted, fontSize = 12.sp)
                    }
                }
                if (wide && rightVisible) Panel(Modifier.width(220.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Text("INSPECTOR", color = Muted, fontSize = 11.sp)
                    Text("Source range", style = MaterialTheme.typography.titleMedium)
                    Text("IN  ${timeLabel(startUs)}\nOUT  ${timeLabel(endUs)}", color = Cyan, fontSize = 13.sp)
                    Text("Duration ${timeLabel(endUs - startUs)}", color = Muted, fontSize = 12.sp)
                    Text("Trim is non-destructive. M1 trim controls have millisecond precision; frame-boundary tools arrive in M2.", color = Muted, fontSize = 12.sp)
                }
            }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = Graphite) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("SOURCE TRIM", color = Violet, fontSize = 11.sp); Text("${timeLabel(startUs)} → ${timeLabel(endUs)}", color = Muted, fontSize = 11.sp) }
                RangeSlider(value = startUs.toFloat()..endUs.toFloat(), onValueChange = { range ->
                    val start = (range.start.toLong() / 1000 * 1000).coerceIn(0, (project.durationUs - 1000).coerceAtLeast(0))
                    val end = (range.endInclusive.toLong() / 1000 * 1000).coerceIn(1000.coerceAtMost(project.durationUs), project.durationUs)
                    if (end > start) { startUs = start; endUs = end }
                }, valueRange = 0f..project.durationUs.toFloat(), enabled = !export.active)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { startUs = (previewStartUs + player.currentPosition * 1000).coerceIn(0, (endUs - 1000).coerceAtLeast(0)) }, enabled = ready && !export.active) { Text("Set in") }
                    TextButton(onClick = { endUs = (previewStartUs + player.currentPosition * 1000).coerceIn((startUs + 1000).coerceAtMost(project.durationUs), project.durationUs) }, enabled = ready && !export.active) { Text("Set out") }
                    TextButton(onClick = { preview() }, enabled = !export.active) { Text("Preview trim") }
                    Button(onClick = { save(project, startUs, endUs) }, enabled = dirty && !export.active) { Text("Save trim") }
                    OutlinedButton(onClick = { player.pause(); exporter.start(project.copy(trimStartUs = startUs, trimEndUs = endUs)) }, enabled = !export.active && playbackError == null && ready) { Text("Test export") }
                }
            }
        }
        if (export.active || export.output != null || export.error != null || export.stage.startsWith("Cancelled")) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(export.stage, color = Cyan, fontSize = 12.sp)
                if (export.active) {
                    export.progress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(export.progress?.let { "$it% · Keep app in foreground" } ?: "Waiting for measured progress · Keep app in foreground", color = Muted, fontSize = 12.sp)
                        TextButton(onClick = { exporter.cancel() }, enabled = export.canCancel) { Text(if (export.canCancel) "Cancel" else "Finishing…") }
                    }
                }
                export.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                export.output?.let { uri ->
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                            .onFailure { playbackError = "No app available to play the exported MP4." }
                    }) { Text("Open exported MP4") }
                }
            }
        } else Text("Validation export: 720p H.264 / AAC · foreground only · stored in Movies/ITU Video Editor", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(bottom = 8.dp))
    }
}
