package com.itu.videoeditor.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.itu.videoeditor.ItuApplication
import com.itu.videoeditor.data.ProjectEntity
import com.itu.videoeditor.media.EncoderCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

fun timeLabel(us: Long): String {
    val ms = us / 1000
    return String.format(Locale.ROOT, "%02d:%02d.%03d", ms / 60000, ms / 1000 % 60, ms % 1000)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioApp(vm: StudioViewModel = viewModel()) {
    val nav = rememberNavController()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val opened by vm.openedProject.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dashboard"
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    LaunchedEffect(opened) { opened?.let { nav.navigate("editor/$it") { popUpTo("dashboard") }; vm.consumedOpen() } }
    Scaffold(containerColor = Obsidian, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 850.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) Column(Modifier.width(204.dp).fillMaxHeight().background(Graphite).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("◈", color = Cyan, fontSize = 40.sp)
                    Text("ITU", fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 5.sp)
                    Text("VIDEO EDITOR", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(24.dp))
                    listOf("dashboard" to "Studio", "vault" to "Media Vault", "settings" to "Settings").forEach { (target, title) ->
                        TextButton(onClick = { nav.navigate(target) { popUpTo("dashboard"); launchSingleTop = true } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = if (target == route) Cyan else Muted)) { Text(title) }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("OBSIDIAN PRISM", fontSize = 10.sp, letterSpacing = 1.sp, color = Violet)
                    Text("M1 · Foundation", color = Muted, fontSize = 12.sp)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        if (route != "dashboard" && (!wide || route.startsWith("editor") || route == "new")) TextButton(onClick = { if (!nav.popBackStack()) nav.navigate("dashboard") }) { Text("‹ Studio") }
                        else Text("YOUR CREATIVE SPACE", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                        if (!wide) TextButton(onClick = { nav.navigate("settings") }) { Text("Settings") }
                        else Text("LOCAL MEDIA · PRIVATE BY DESIGN", color = Muted, fontSize = 10.sp)
                    }
                    NavHost(nav, startDestination = "dashboard", modifier = Modifier.weight(1f)) {
                        composable("dashboard") { Dashboard(projects, { nav.navigate("new") }, { nav.navigate("vault") }, { nav.navigate("editor/$it") }) }
                        composable("new") { NewProject(busy) { uri, name -> vm.import(uri, name) } }
                        composable("vault") { MediaVault(projects) { nav.navigate("editor/$it") } }
                        composable("settings") { Settings(muted, vm::setMuted) }
                        composable("editor/{id}") { backStack ->
                            val context = LocalContext.current
                            val repository = (context.applicationContext as ItuApplication).repository
                            val id = backStack.arguments?.getString("id") ?: return@composable
                            val project by remember(id) { repository.project(id) }.collectAsStateWithLifecycle(initialValue = null)
                            project?.let { EditorScreen(it, muted, vm::saveTrim) } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashboard(projects: List<ProjectEntity>, create: () -> Unit, vault: () -> Unit, open: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Graphite, androidx.compose.ui.graphics.Color(0xFF17283A), androidx.compose.ui.graphics.Color(0xFF211C35))), RoundedCornerShape(28.dp)).padding(28.dp)) {
                Text("STUDIO / 01", color = Cyan, fontSize = 12.sp, letterSpacing = 3.sp)
                Spacer(Modifier.height(16.dp))
                Text("Every great story\nstarts with a moment.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text("Bring your footage into focus.", color = Muted)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = create, modifier = Modifier.heightIn(min = 48.dp)) { Text("＋ New project") }
                    OutlinedButton(onClick = vault, modifier = Modifier.heightIn(min = 48.dp)) { Text("Media Vault") }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Recent projects", style = MaterialTheme.typography.titleLarge); Text("${projects.size} PROJECTS", color = Muted, fontSize = 12.sp) } }
        if (projects.isEmpty()) item { Panel { Text("Your studio is ready", style = MaterialTheme.typography.titleMedium); Text("Create a project and select a video from your device. Source files stay untouched.", color = Muted) } }
        items(projects, key = { it.id }) { ProjectCard(it) { open(it.id) } }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Graphite, border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF253040))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ProjectCard(project: ProjectEntity, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Graphite), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(58.dp).background(Cyan.copy(alpha = 0.08f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("▶", color = Cyan, fontSize = 24.sp) }
            Column(Modifier.weight(1f)) { Text(project.name, style = MaterialTheme.typography.titleMedium); Text(project.sourceName, color = Muted, maxLines = 1); Text("${project.width} × ${project.height} · ${timeLabel(project.trimEndUs - project.trimStartUs)}", fontSize = 12.sp, color = Violet) }
            Text("Open ›", color = Cyan)
        }
    }
}

@Composable
private fun NewProject(busy: Boolean, import: (Uri, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { import(it, name) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("New project", style = MaterialTheme.typography.headlineLarge)
        Text("A clean canvas for your next story.", color = Muted)
        Panel {
            OutlinedTextField(name, { name = it }, label = { Text("Project name") }, placeholder = { Text("Use video name") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            Text("Select one video to begin. Android's file picker gives this app access only to the file you choose.", color = Muted)
            Button(onClick = { picker.launch(arrayOf("video/*")) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (busy) "Reading video…" else "Choose video & create") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text("M1 supports one source per project, preview, and a trim/export validation workflow. Multitrack editing follows in later milestones.", color = Muted)
    }
}

@Composable
private fun MediaVault(projects: List<ProjectEntity>, open: (String) -> Unit) {
    LazyColumn(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Media Vault", style = MaterialTheme.typography.headlineLarge); Text("Imported project sources · original files remain in place", color = Muted) }
        if (projects.isEmpty()) item { Panel { Text("No imported media yet. Create a project to import a video.") } }
        items(projects.distinctBy { it.sourceUri }, key = { it.sourceUri }) { ProjectCard(it) { open(it.id) } }
    }
}

@Composable
private fun Settings(muted: Boolean, setMuted: (Boolean) -> Unit) {
    var report by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(checking) { if (checking) { report = withContext(Dispatchers.IO) { runCatching { EncoderCapabilities.report() }.getOrElse { "Capability query failed: ${it.message}" } }; checking = false } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Mute preview audio"); Switch(muted, setMuted) }
            Text("Preview only. Export keeps the source audio.", color = Muted)
        }
        Panel {
            Text("Device readiness", style = MaterialTheme.typography.titleLarge)
            Text("Query this Android device's advertised encoder limits. A supported configuration still needs a real export test.", color = Muted)
            OutlinedButton(onClick = { checking = true }, enabled = !checking) { Text(if (checking) "Querying…" else "Inspect encoders") }
            report?.let { Text(it, fontSize = 12.sp, color = Muted) }
        }
        Text("ITU VIDEO EDITOR · 0.1.0 M1\nLocal projects · Android 10+ · Landscape-first tablet interface", color = Muted)
    }
}
