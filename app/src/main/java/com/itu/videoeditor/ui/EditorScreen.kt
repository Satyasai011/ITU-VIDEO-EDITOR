package com.itu.videoeditor.ui

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.itu.videoeditor.data.ProjectEntity
import com.itu.videoeditor.domain.displayAspect
import com.itu.videoeditor.domain.MediaInfo
import com.itu.videoeditor.domain.decimalRate
import com.itu.videoeditor.data.StudioPreferences
import com.itu.videoeditor.data.MetadataJson
import com.itu.videoeditor.media.ValidationExporter
import com.itu.videoeditor.ui.components.*
import kotlinx.coroutines.delay
import kotlin.math.min

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EditorScreen(project: ProjectEntity, muted: Boolean, save: (ProjectEntity, Long, Long) -> Unit, preferences: StudioPreferences = StudioPreferences(), info: MediaInfo? = null, refreshMetadata: (() -> Unit)? = null, back: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(project.id) { ExoPlayer.Builder(context).build() }
    val exporter = remember(project.id) { ValidationExporter(context.applicationContext) }
    val export by exporter.state.collectAsStateWithLifecycle()
    var playbackError by remember { mutableStateOf<String?>(null) }
    var outputError by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var leftVisible by rememberSaveable { mutableStateOf(true) }
    var rightVisible by rememberSaveable { mutableStateOf(true) }
    var panel by rememberSaveable { mutableStateOf("trim") }
    var timelineVisible by rememberSaveable(project.id) { mutableStateOf(true) }
    var historyOpen by rememberSaveable(project.id) { mutableStateOf(false) }
    var resumeAfterHistory by remember { mutableStateOf(false) }
    var startUs by rememberSaveable(project.id, project.trimStartUs) { mutableLongStateOf(project.trimStartUs) }
    var endUs by rememberSaveable(project.id, project.trimEndUs) { mutableLongStateOf(project.trimEndUs) }
    var previewStartUs by remember { mutableLongStateOf(project.trimStartUs) }
    var previewEndUs by remember { mutableLongStateOf(project.trimEndUs) }
    var videoAspect by remember { mutableFloatStateOf(displayAspect(project.width, project.height, project.rotation)) }
    val dirty = startUs != project.trimStartUs || endUs != project.trimEndUs
    val motionMs = if (LocalPrismMotion.current) 220 else 0
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(preferences.loopPreview) { player.repeatMode = if (preferences.loopPreview) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    LaunchedEffect(project.id, preferences.collapsePanels) { leftVisible = !preferences.collapsePanels; rightVisible = !preferences.collapsePanels }
    LaunchedEffect(project.sourceUri, project.trimStartUs, project.trimEndUs) {
        ready = false; playbackError = null
        previewStartUs = project.trimStartUs; previewEndUs = project.trimEndUs
        player.setMediaItem(clippedItem(project, previewStartUs, previewEndUs)); player.prepare()
    }
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { playbackError = "${error.errorCodeName}: ${error.message}. Check the source file is still available."; ready = false }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { ready = state == Player.STATE_READY || state == Player.STATE_ENDED }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { player.pause(); exporter.cancel() } }
        player.addListener(listener); lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release(); exporter.release() }
    }
    LaunchedEffect(player) { while (true) { positionMs = player.currentPosition; delay(100) } }
    LaunchedEffect(export.active) { if (export.active) panel = "export" }
    fun preview() {
        previewStartUs = startUs; previewEndUs = endUs; ready = false
        player.setMediaItem(clippedItem(project, startUs, endUs)); player.prepare(); player.play()
    }
    @Composable fun TrimPanel() {
        if (preferences.showHints) Text("Drag IN / OUT or set a boundary at the playhead. Save trim commits this source range to the project.", color = Muted, fontSize = 11.sp)
        TrimControls(startUs, endUs, project.durationUs, !export.active, ready,
            change = { start, end -> startUs = start; endUs = end },
            setIn = { startUs = (previewStartUs + player.currentPosition * 1000).coerceIn(0, (endUs - 1000).coerceAtLeast(0)) },
            setOut = { endUs = (previewStartUs + player.currentPosition * 1000).coerceIn((startUs + 1000).coerceAtMost(project.durationUs), project.durationUs) }, preview = ::preview)
    }
    @Composable fun ExportPanel() {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("TEST EXPORT", color = Violet, fontSize = 10.sp)
            Text("720p · H.264 / AAC", style = MaterialTheme.typography.titleMedium)
            Text("Keep the app in the foreground. Output is saved to Movies/ITU Video Editor.", color = Muted, fontSize = 12.sp)
            if (export.active) {
                Text(export.stage, color = Cyan, fontSize = 12.sp)
                export.progress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()); Text("$it%", color = Muted) }
                    ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton({ exporter.cancel() }, enabled = export.canCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (export.canCancel) "Cancel export" else "Finishing…") }
            } else {
                export.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (export.output != null || export.stage.startsWith("Cancelled")) Text(export.stage, color = Cyan, fontSize = 12.sp)
                PrismAction(if (export.error != null) "Retry test export" else "Start test export", Glyph.Export,
                    enabled = ready && playbackError == null, primary = true) {
                    player.pause(); outputError = null; exporter.start(project.copy(trimStartUs = startUs, trimEndUs = endUs))
                }
                export.output?.let { uri -> PrismAction("Open exported MP4", Glyph.Play) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        .onFailure { outputError = "No application could open this MP4: ${it.message}" }
                } }
                outputError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp && maxHeight >= 480.dp
        val short = maxHeight < 430.dp
        val contextHeight = (maxHeight * 0.42f).coerceIn(140.dp, 290.dp)
        Column(Modifier.fillMaxSize().then(if (short) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                GlyphButton(Glyph.Back, "Back to Studio", onClick = back)
                Column(Modifier.weight(1f)) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (dirty) "UNSAVED TRIM" else "OBSIDIAN PRECISION", color = if (dirty) Violet else Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                GlyphButton(Glyph.Info, "Revision History") { resumeAfterHistory = player.isPlaying; player.pause(); historyOpen = true }
                if (wide) {
                    GlyphButton(Glyph.Vault, if (leftVisible) "Collapse media panel" else "Expand media panel", leftVisible) { leftVisible = !leftVisible }
                    GlyphButton(Glyph.Info, if (rightVisible) "Collapse inspector" else "Expand inspector", rightVisible) { rightVisible = !rightVisible }
                }
            }
            Row(Modifier.fillMaxWidth().then(if (short) Modifier.height(210.dp) else Modifier.weight(1f)), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (wide) AnimatedVisibility(leftVisible, enter = expandHorizontally(tween(motionMs)) + fadeIn(tween(motionMs)), exit = shrinkHorizontally(tween(motionMs)) + fadeOut(tween(motionMs))) {
                    Panel(Modifier.width(184.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { SourceInfo(project, info, refreshMetadata) }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
                        val fittedWidth = min(maxWidth.value, maxHeight.value * videoAspect).coerceAtLeast(1f).dp
                        val fittedHeight = (fittedWidth / videoAspect).coerceAtLeast(1.dp)
                        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; keepScreenOn = true } },
                            update = { it.player = player }, modifier = Modifier.size(fittedWidth, fittedHeight))
                        if (!ready && playbackError == null) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        playbackError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxSize().background(Obsidian.copy(alpha = 0.9f)).verticalScroll(rememberScrollState()).padding(12.dp), fontSize = 12.sp) }
                    }
                    PlaybackControls(positionMs * 1000, previewStartUs, previewEndUs, playing, ready,
                        playPause = { if (playing) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } },
                        seek = { fraction -> player.seekTo(((previewEndUs - previewStartUs) / 1000.0 * fraction).toLong().coerceIn(0, ((previewEndUs - previewStartUs) / 1000 - 1).coerceAtLeast(0))) })
                }
                if (wide) AnimatedVisibility(rightVisible, enter = expandHorizontally(tween(motionMs)) + fadeIn(tween(motionMs)), exit = shrinkHorizontally(tween(motionMs)) + fadeOut(tween(motionMs))) {
                    Panel(Modifier.width(196.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        Text("INSPECTOR", color = Muted, fontSize = 10.sp)
                        Text("Source range", style = MaterialTheme.typography.titleMedium)
                        Text("IN  ${timeLabel(startUs)}\nOUT ${timeLabel(endUs)}", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text("Original media stays untouched. Trim precision is milliseconds.", color = Muted, fontSize = 12.sp)
                        MetadataJson.readFormat(project.formatJson)?.let { format -> Text("Project intent: ${format.width ?: "Unknown"} × ${format.height ?: "Unknown"} · ${format.frameRate?.let(::decimalRate) ?: "Unknown"} FPS", color = Muted, fontSize = 12.sp) }
                        Text("Export remains 720p validation.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
            // Timeline is a read-only source overview with a seekable playhead.
            // It deliberately does not represent non-existent split or multi-track operations.
            Surface(color = Graphite, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("SOURCE TIMELINE", color = Cyan, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { timelineVisible = !timelineVisible }) {
                            Text(if (timelineVisible) "Hide" else "Show", fontSize = 12.sp)
                        }
                    }
                    if (timelineVisible) {
                        val durationMs = ((previewEndUs - previewStartUs) / 1000).coerceAtLeast(1L)
                        val timelineFraction = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(timeLabel(previewStartUs), color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text("PLAYHEAD " + timeLabel(previewStartUs + positionMs.coerceIn(0L, durationMs) * 1000), color = Cyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text(timeLabel(previewEndUs), color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Slider(value = timelineFraction, onValueChange = { fraction ->
                            player.seekTo((durationMs * fraction).toLong().coerceIn(0L, durationMs))
                        }, enabled = ready && !export.active, modifier = Modifier.fillMaxWidth())
                        Text("Source preview · drag to seek · non-destructive", color = Muted, fontSize = 10.sp)
                    }
                }
            }
            Surface(color = Graphite, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    listOf(Triple("media", Glyph.Vault, "Source media"), Triple("trim", Glyph.Trim, "Trim controls"), Triple("export", Glyph.Export, "Test export")).forEach { (key, icon, label) ->
                        GlyphButton(icon, label, panel == key, primary = true) { panel = if (panel == key) "" else key }
                    }
                    GlyphButton(Glyph.Save, "Save trim", enabled = dirty && !export.active, primary = true) { save(project, startUs, endUs) }
                }
            }
            AnimatedVisibility(panel.isNotEmpty(), enter = expandVertically(tween(motionMs)) + fadeIn(tween(motionMs)), exit = shrinkVertically(tween(motionMs)) + fadeOut(tween(motionMs))) {
                Panel(Modifier.fillMaxWidth().heightIn(max = if (wide) 240.dp else contextHeight).verticalScroll(rememberScrollState())) {
                    when (panel) { "media" -> SourceInfo(project, info, refreshMetadata); "trim" -> TrimPanel(); "export" -> ExportPanel() }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
    if (historyOpen) RevisionHistoryOverlay(project.id, onClose = {
        historyOpen = false
        if (resumeAfterHistory && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) player.play()
        resumeAfterHistory = false
    }, reduceMotion = preferences.reduceMotion)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private fun clippedItem(project: ProjectEntity, startUs: Long, endUs: Long) = MediaItem.Builder().setUri(project.sourceUri)
    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startUs / 1000).setEndPositionMs(endUs / 1000).build()).build()

@Composable
private fun SourceInfo(project: ProjectEntity, info: MediaInfo?, refresh: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SOURCE INFORMATION", color = Muted, fontSize = 10.sp)
        MediaThumbnail(project, Modifier.fillMaxWidth().heightIn(max = 110.dp).aspectRatio(16f / 9f), info)
        Text(project.sourceName, style = MaterialTheme.typography.titleMedium, overflow = TextOverflow.Ellipsis, maxLines = 2)
        MediaInformation(info)
        if (refresh != null) OutlinedButton(onClick = refresh) { Text("Refresh metadata") }
    }
}
