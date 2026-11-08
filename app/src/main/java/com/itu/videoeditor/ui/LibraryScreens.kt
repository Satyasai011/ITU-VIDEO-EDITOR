package com.itu.videoeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itu.videoeditor.data.*
import com.itu.videoeditor.domain.*
import com.itu.videoeditor.ui.components.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Dashboard(library: LibraryState, retry: () -> Unit, create: () -> Unit, vault: () -> Unit, open: (String) -> Unit) {
    var details by remember { mutableStateOf<Pair<String, MediaInfo>?>(null) }
    LazyVerticalGrid(GridCells.Adaptive(240.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF19303A), Graphite, Color(0xFF201E30))), MaterialTheme.shapes.large).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { PrismIcon(Glyph.Prism, tint = Cyan); Text("PRISM STUDIO", color = Cyan, fontSize = 11.sp, letterSpacing = 2.sp) }
                Text("Create with precision.", style = MaterialTheme.typography.headlineMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrismAction("New project", Glyph.Plus, primary = true, onClick = create)
                    PrismAction("Media Vault", Glyph.Vault, onClick = vault)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Recent projects", style = MaterialTheme.typography.titleLarge)
                Text("${library.projects.size} projects · ${library.sources.size} imported videos", color = Muted, fontSize = 12.sp)
                Text("Projects reference media. One video can be used by several projects.", color = Muted, fontSize = 11.sp)
            }
        }
        if (library.loading || library.error != null || library.projects.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryStatus(library, "Your studio is ready", if (library.sources.isEmpty()) "Create a project to inspect and import footage." else "Imported footage is in Media Vault. Confirm a format to create its project.", retry)
        }
        items(library.projects, key = { it.id }) { project ->
            val info = library.metadata[project.sourceUri] ?: MediaInfo()
            MediaCard(project.name, info, list = false, thumbnail = { MediaThumbnail(project, it, info) },
                details = { details = project.sourceName to info }, label = "Open project", onClick = { open(project.id) })
        }
    }
    details?.let { MediaDetails(it.first, it.second) { details = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaVault(library: LibraryState, grid: Boolean, setGrid: (Boolean) -> Unit, retry: () -> Unit,
               setupSource: (MediaSourceEntity) -> Unit = {}, open: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(VaultSort.Recent.name) }
    var filterName by rememberSaveable { mutableStateOf(VaultFilter.All.name) }
    var sortOpen by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<MediaSourceEntity?>(null) }
    val sort = VaultSort.valueOf(sortName); val filter = VaultFilter.valueOf(filterName)
    val sources = remember(library.sources, library.projects, query, sort, filter) {
        querySources(library.sources.map { source -> VaultSource(source.uri, source.name, library.metadata[source.uri] ?: MediaInfo(), source.importedAt,
            library.projects.filter { it.sourceUri == source.uri }.map { it.name }) }, query, sort, filter)
    }
    val byUri = remember(library.sources) { library.sources.associateBy { it.uri } }
    LazyVerticalGrid(if (grid) GridCells.Adaptive(230.dp) else GridCells.Fixed(1), Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Media Vault", style = MaterialTheme.typography.headlineMedium); Text("Every imported source, retained.", color = Muted, fontSize = 12.sp) }
                    GlyphButton(Glyph.Grid, "Grid view", grid) { setGrid(true) }; GlyphButton(Glyph.List, "List view", !grid) { setGrid(false) }
                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search imported videos") }, leadingIcon = { PrismIcon(Glyph.Search) }, singleLine = true, shape = MaterialTheme.shapes.medium,
                    trailingIcon = { if (query.isNotEmpty()) GlyphButton(Glyph.Close, "Clear search") { query = "" } })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    VaultFilter.entries.forEach { item -> FilterChip(filter == item, { filterName = item.name }, { Text(item.label) }, modifier = Modifier.heightIn(min = 48.dp)) }
                    Box {
                        TextButton({ sortOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { PrismIcon(Glyph.Sort); Spacer(Modifier.width(6.dp)); Text(sort.label) }
                        DropdownMenu(sortOpen, { sortOpen = false }) { VaultSort.entries.forEach { item -> DropdownMenuItem({ Text(item.label) }, { sortName = item.name; sortOpen = false }) } }
                    }
                }
                Text("${sources.size} shown / ${library.sources.size} imported videos · ${library.projects.size} projects", color = Muted, fontSize = 12.sp)
            }
        }
        if (library.loading || library.error != null || sources.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryStatus(library, if (library.sources.isEmpty()) "No videos yet" else "No matching videos", if (library.sources.isEmpty()) "New project imports and inspects your selected videos." else "Clear the search or orientation filters to show all imported videos.", retry)
        }
        items(sources, key = { it.uri }) { source ->
            val entity = byUri.getValue(source.uri)
            val project = library.projects.firstOrNull { it.sourceUri == source.uri }
            MediaCard(source.name, source.info, !grid, thumbnail = { MediaThumbnail(entity, it) }, details = { details = entity },
                label = if (project != null) "Open project" else "Set up project") {
                if (project != null) open(project.id) else setupSource(entity)
            }
        }
    }
    details?.let { MediaDetails(it.name, library.metadata[it.uri] ?: MediaInfo()) { details = null } }
}

@Composable
private fun LibraryStatus(state: LibraryState, title: String, detail: String, retry: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        when {
            state.loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading your library…", color = Muted) }
            state.error != null -> { Text("Library unavailable"); Text(state.error, color = Muted); PrismAction("Try again", Glyph.Retry, onClick = retry) }
            else -> { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, color = Muted) }
        }
    }
}
@Composable
private fun MediaCard(title: String, info: MediaInfo, list: Boolean, thumbnail: @Composable (Modifier) -> Unit, details: () -> Unit, label: String, onClick: () -> Unit) {
    Card(onClick, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = Graphite)) {
        if (list) Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            thumbnail(Modifier.width(96.dp).height(78.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text("${info.resolutionLabel}\n${info.frameRateLabel} · ${codecLabel(info.codec)}", color = Muted, fontSize = 11.sp)
                Text(info.durationUs?.let(::timeLabel) ?: "Unknown duration", color = Violet, fontSize = 11.sp)
            }
            GlyphButton(Glyph.Info, "Media information for $title", onClick = details)
        } else Column {
            thumbnail(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${info.resolutionLabel} · ${info.frameRateLabel}", color = Cyan, fontSize = 11.sp)
                Text("${codecLabel(info.codec)} · ${info.durationUs?.let(::timeLabel) ?: "Unknown duration"} · ${sizeLabel(info.sizeBytes)}", color = Muted, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), color = Violet, fontSize = 11.sp)
                    GlyphButton(Glyph.Info, "Media information for $title", onClick = details)
                }
            }
        }
    }
}
@Composable
private fun MediaDetails(name: String, info: MediaInfo, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { MediaInformation(info) } },
        confirmButton = { TextButton(close, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") } })
}
