package com.itu.videoeditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itu.videoeditor.data.ProjectEntity
import com.itu.videoeditor.domain.*
import com.itu.videoeditor.ui.components.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Dashboard(library: LibraryState, retry: () -> Unit, create: () -> Unit, vault: () -> Unit, open: (String) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(250.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF19303A), Graphite, Color(0xFF201E30))), MaterialTheme.shapes.large).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { PrismIcon(Glyph.Prism, tint = Cyan); Text("CRYSTAL AURORA", color = Cyan, fontSize = 11.sp, letterSpacing = 2.sp) }
                Text("Your next moment.\nMade unmistakable.", style = MaterialTheme.typography.headlineLarge)
                Text("A precise space for your footage.", color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrismAction("New project", Glyph.Plus, primary = true, onClick = create)
                    PrismAction("Media Vault", Glyph.Vault, onClick = vault)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Recent projects", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); Text("${library.projects.size} LOCAL", color = Muted, fontSize = 11.sp) } }
        if (library.loading || library.error != null || library.projects.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryStatus(library, "Your studio is ready", "Create a project to bring your footage into focus.", retry)
        }
        items(library.projects, key = { it.id }) { project -> MediaCard(project, false) { open(project.id) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaVault(library: LibraryState, grid: Boolean, setGrid: (Boolean) -> Unit, retry: () -> Unit, open: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(VaultSort.Recent.name) }
    var filterName by rememberSaveable { mutableStateOf(VaultFilter.All.name) }
    var sortOpen by remember { mutableStateOf(false) }
    val sort = VaultSort.valueOf(sortName)
    val filter = VaultFilter.valueOf(filterName)
    val sources = remember(library.projects, query, sort, filter) { queryVault(library.projects, query, sort, filter) }
    LazyVerticalGrid(columns = if (grid) GridCells.Adaptive(230.dp) else GridCells.Fixed(1), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Media Vault", style = MaterialTheme.typography.headlineMedium); Text("Originals, within reach.", color = Muted, fontSize = 12.sp) }
                    GlyphButton(Glyph.Grid, "Grid view", selected = grid) { setGrid(true) }
                    GlyphButton(Glyph.List, "List view", selected = !grid) { setGrid(false) }
                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search imported videos") }, leadingIcon = { PrismIcon(Glyph.Search) }, singleLine = true, shape = MaterialTheme.shapes.medium,
                    trailingIcon = { if (query.isNotEmpty()) GlyphButton(Glyph.Close, "Clear search") { query = "" } })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    VaultFilter.entries.forEach { item -> FilterChip(filter == item, { filterName = item.name }, { Text(item.label) }, modifier = Modifier.heightIn(min = 48.dp)) }
                    Box {
                        TextButton(onClick = { sortOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { PrismIcon(Glyph.Sort); Spacer(Modifier.width(6.dp)); Text(sort.label) }
                        DropdownMenu(sortOpen, { sortOpen = false }) { VaultSort.entries.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { sortName = item.name; sortOpen = false }) } }
                    }
                }
                Text("${sources.size} videos · sources stay in their original location", color = Muted, fontSize = 12.sp)
            }
        }
        if (library.loading || library.error != null || sources.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryStatus(library, if (library.projects.isEmpty()) "No videos yet" else "No matching videos",
                if (library.projects.isEmpty()) "Create a project to import your first video." else "Try another name or orientation filter.", retry)
        }
        items(sources, key = { it.sourceUri }) { project -> MediaCard(project, !grid, sourceMetadata = true) { open(project.id) } }
    }
}

@Composable
private fun LibraryStatus(state: LibraryState, title: String, detail: String, retry: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        when {
            state.loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading your projects…", color = Muted) }
            state.error != null -> { Text("Library unavailable", style = MaterialTheme.typography.titleMedium); Text(state.error, color = Muted); PrismAction("Try again", Glyph.Retry, onClick = retry) }
            else -> { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail, color = Muted) }
        }
    }
}

@Composable
private fun MediaCard(project: ProjectEntity, list: Boolean, sourceMetadata: Boolean = false, onClick: () -> Unit) {
    val duration = if (sourceMetadata) project.durationUs else project.trimEndUs - project.trimStartUs
    val dimensions = if (project.rotation % 180 != 0) "${project.height} × ${project.width}" else "${project.width} × ${project.height}"
    Card(onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = Graphite)) {
        if (list) Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MediaThumbnail(project, Modifier.width(110.dp).height(82.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (sourceMetadata) project.sourceName else project.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text("$dimensions · ${timeLabel(duration)}", color = Muted, fontSize = 11.sp)
            }
        } else Column {
            Box {
                MediaThumbnail(project, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Surface(Modifier.align(Alignment.BottomEnd).padding(8.dp), color = Obsidian.copy(alpha = 0.85f), shape = RoundedCornerShape(6.dp)) {
                    Text(timeLabel(duration), color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (sourceMetadata) project.sourceName else project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (sourceMetadata) "$dimensions · Video" else project.sourceName, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                if (!sourceMetadata) Text(dimensions, color = Violet, fontSize = 11.sp)
            }
        }
    }
}
