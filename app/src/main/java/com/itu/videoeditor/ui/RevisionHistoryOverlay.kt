package com.itu.videoeditor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itu.videoeditor.ItuApplication
import com.itu.videoeditor.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val historySurface = Color(0xFF192332)
private val historyCard = Color(0xFF25344B)
private val historyAccent = Color(0xFFB3C7F7)

@Composable
fun RevisionHistoryOverlay(projectId: String, onClose: () -> Unit, reduceMotion: Boolean) {
    val repository = (LocalContext.current.applicationContext as ItuApplication).repository
    val pager = remember(projectId) { HistoryPager(repository::getRevisionHistory) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var lastNavigation by remember(projectId) { mutableStateOf("older") }
    var state by remember(projectId) { mutableStateOf<HistoryPager.Outcome?>(null) }
    var busy by remember(projectId) { mutableStateOf(false) }
    var refreshing by remember(projectId) { mutableStateOf(false) }
    var expanded by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    var restoredRevision by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var restoredOffset by rememberSaveable(projectId) { mutableIntStateOf(0) }
    var restorationPending by remember(projectId) { mutableStateOf(restoredRevision != null) }
    val currentPages = (state as? HistoryPager.Outcome.Ready)?.pages ?: pager.loaded
    val entries = currentPages.flatMap { it.entries }
    val loaded = state as? HistoryPager.Outcome.Ready
    val error = state as? HistoryPager.Outcome.Failure
    val paused = state as? HistoryPager.Outcome.Paused

    fun runRead(refresh: Boolean = false, action: suspend () -> HistoryPager.Outcome) {
        if (busy) return
        busy = true
        refreshing = refresh
        scope.launch {
            try {
                val anchor = entries.getOrNull(listState.firstVisibleItemIndex - if (currentPages.firstOrNull()?.index?.let { it > 1 } == true) 1 else 0)?.id
                val offset = listState.firstVisibleItemScrollOffset
                val result = action()
                state = result
                if (!refresh && result is HistoryPager.Outcome.Ready && anchor != null) {
                    val index = result.pages.flatMap { it.entries }.indexOfFirst { it.id == anchor }
                    if (index >= 0) {
                        val prefix = if (result.hasNewer) 1 else 0
                        listState.scrollToItem(index + prefix, offset)
                    }
                }
                if (refresh && result is HistoryPager.Outcome.Ready) {
                    if (pager.loaded.none { page -> page.entries.any { it.id == expanded } }) expanded = null
                    listState.scrollToItem(0)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { state = HistoryPager.Outcome.Failure("Unable to load history. Try again.") }
            finally { busy = false; refreshing = false }
        }
    }
    LaunchedEffect(projectId) { runRead { pager.first(projectId) } }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(entries.size) {
        if (restorationPending && restoredRevision != null && entries.isNotEmpty()) {
            val index = entries.indexOfFirst { it.id == restoredRevision }
            if (index >= 0) {
                listState.scrollToItem(index + if (currentPages.firstOrNull()?.index?.let { it > 1 } == true) 1 else 0, restoredOffset)
                restorationPending = false
            } else {
                // A process-restored session starts at page one. Never replay pages automatically.
                restorationPending = false
            }
        }
    }
    // Keep only a stable revision anchor and pixel offset in saved state.
    // Never persist history records or opaque pagination cursors.
    LaunchedEffect(projectId, entries.size) {
        if (entries.isNotEmpty() && !restorationPending) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    val firstRevisionIndex = index - if (currentPages.firstOrNull()?.index?.let { it > 1 } == true) 1 else 0
                    entries.getOrNull(firstRevisionIndex)?.let {
                        restoredRevision = it.id
                        restoredOffset = offset
                    }
                }
        }
    }
    DisposableEffect(pager) {
        onDispose { pager.cancelReplay() }
    }
    BackHandler { onClose() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = when {
            maxWidth < 500.dp -> maxWidth * 0.92f
            maxWidth < 900.dp -> (maxWidth * 0.65f).coerceAtMost(520.dp)
            else -> (maxWidth * 0.38f).coerceAtMost(560.dp)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)).clickable(onClick = onClose))
        Column(Modifier.align(Alignment.CenterEnd).width(width).fillMaxHeight().focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onClose(); true
                } else false
            }
            .background(historySurface, RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Revision History", color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = { lastNavigation = "refresh"; runRead(true) { pager.first(projectId) } }, enabled = !busy) {
                    Text(if (refreshing) "Refreshing…" else "Refresh", color = historyAccent)
                }
                TextButton(onClick = onClose, modifier = Modifier.focusRequester(focusRequester)) { Text("Close", color = Color.White) }
            }
            HorizontalDivider(color = Color(0xFF35445C))
            if (busy && entries.isEmpty()) {
                repeat(3) {
                    Surface(color = historyCard, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(0.45f), color = historyAccent)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                Text("Loading saved revisions", color = historyAccent)
            } else if (error != null && (entries.isEmpty() || error.missingProject)) {
                Text(if (error.missingProject) "Project unavailable" else error.message, color = Color.White)
                if (!error.missingProject) OutlinedButton(onClick = { runRead { pager.first(projectId) } }) { Text("Try Again") }
            } else if (!busy && entries.isEmpty()) {
                Text("No saved revisions yet", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("Saved trim changes will appear here after a successful edit.", color = historyAccent)
            } else {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    if (currentPages.firstOrNull()?.index?.let { it > 1 } == true) item(key = "newer") {
                        OutlinedButton(enabled = !busy, onClick = { lastNavigation = "newer"; runRead { pager.newer() } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Load Newer Revisions")
                        }
                    }
                    items(entries, key = { it.id }) { revision ->
                        RevisionCard(revision, expanded == revision.id) {
                            expanded = if (expanded == revision.id) null else revision.id
                        }
                    }
                    if (error != null) item(key = "error") {
                        Column {
                            Text(error.message, color = Color(0xFFFFBDBD))
                            OutlinedButton(onClick = { runRead { if (lastNavigation == "refresh") pager.first(projectId) else if (lastNavigation == "newer") { if (pager.pendingReplay) pager.continueReplay() else pager.newer() } else pager.older() } }) {
                                Text("Retry Navigation")
                            }
                        }
                    }
                    if (paused != null) item(key = "replay") {
                        Column {
                            Text("Navigation paused. Continue to recover the requested page.", color = historyAccent)
                            OutlinedButton(enabled = !busy, onClick = { runRead { pager.continueReplay() } }) { Text("Continue Navigation") }
                            TextButton(onClick = { pager.cancelReplay(); state = HistoryPager.Outcome.Ready(pager.loaded, pager.loaded.firstOrNull()?.index != 1, pager.loaded.lastOrNull()?.nextCursor != null) }) { Text("Cancel Navigation") }
                        }
                    }
                    if (currentPages.lastOrNull()?.nextCursor != null) item(key = "older") {
                        OutlinedButton(enabled = !busy, onClick = { lastNavigation = "older"; runRead { pager.older() } }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "Loading…" else "Load Older Revisions")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RevisionCard(revision: RevisionHistoryEntry, expanded: Boolean, onToggle: () -> Unit) {
    val snapshot = revision.snapshot
    Surface(color = historyCard, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onToggle).semantics {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Revision #${revision.revisionNumber}", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (expanded) "▲" else "▼", color = historyAccent)
            }
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(revision.createdAt)), color = historyAccent)
            when (snapshot) {
                is HistoryPayload.TrimSnapshot -> Text("Trim: ${timeLabel(snapshot.trimStartUs)} – ${timeLabel(snapshot.trimEndUs)}", color = Color.White)
                is HistoryPayload.UnsupportedVersion -> Text("Unsupported revision format", color = Color(0xFFFFD49B))
                is HistoryPayload.CorruptRecord -> Text("History data unavailable", color = Color(0xFFFFBDBD))
                else -> Unit
            }
            if (expanded) {
                HorizontalDivider(color = Color(0xFF465672))
                if (snapshot is HistoryPayload.TrimSnapshot) {
                    Text("Start: ${timeLabel(snapshot.trimStartUs)}", color = Color.White)
                    Text("End: ${timeLabel(snapshot.trimEndUs)}", color = Color.White)
                    Text("Selected duration: ${timeLabel(snapshot.trimEndUs - snapshot.trimStartUs)}", color = historyAccent)
                }
                revision.operations.sortedBy { it.sequenceNumber }.forEach { op ->
                    when (val payload = op.payload) {
                        is HistoryPayload.TrimOperation -> {
                            Text("Trim adjustment", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Previous: ${timeLabel(payload.previousStartUs)} – ${timeLabel(payload.previousEndUs)}", color = historyAccent)
                            Text("New: ${timeLabel(payload.startUs)} – ${timeLabel(payload.endUs)}", color = Color.White)
                        }
                        is HistoryPayload.UnsupportedVersion -> Text("Unsupported operation", color = Color(0xFFFFD49B))
                        is HistoryPayload.CorruptRecord -> Text("History data unavailable", color = Color(0xFFFFBDBD))
                        else -> Unit
                    }
                }
                Text("Read-only history", color = historyAccent)
            }
        }
    }
}
