package com.itu.videoeditor.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.itu.videoeditor.BuildConfig
import com.itu.videoeditor.ItuApplication
import com.itu.videoeditor.media.EncoderCapabilities
import com.itu.videoeditor.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

fun timeLabel(us: Long): String {
    val ms = us.coerceAtLeast(0) / 1000
    return String.format(Locale.ROOT, "%02d:%02d.%03d", ms / 60000, ms / 1000 % 60, ms % 1000)
}

@Composable
fun StudioApp(vm: StudioViewModel = viewModel()) {
    val nav = rememberNavController()
    val library by vm.library.collectAsStateWithLifecycle()
    val grid by vm.vaultGrid.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val opened by vm.openedProject.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dashboard"
    val editing = route.startsWith("editor")
    fun navigate(target: String) { nav.navigate(target) { popUpTo("dashboard"); launchSingleTop = true } }
    val sections = listOf(Triple("dashboard", "Studio", Glyph.Studio), Triple("vault", "Media Vault", Glyph.Vault), Triple("settings", "Settings", Glyph.Settings))
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    LaunchedEffect(opened) { opened?.let { nav.navigate("editor/$it") { popUpTo("dashboard") }; vm.consumedOpen() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 1000.dp
        val bottom = !rail && !editing && route != "new" && maxHeight >= 480.dp
        Scaffold(containerColor = Obsidian, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            if (bottom) NavigationBar(containerColor = Graphite, tonalElevation = 0.dp) {
                sections.forEach { (target, label, icon) -> NavigationBarItem(route == target, { navigate(target) }, icon = { PrismIcon(icon) }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Cyan.copy(alpha = 0.10f), selectedIconColor = Cyan)) }
            }
        }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (rail) Column(Modifier.width(80.dp).fillMaxHeight().background(Graphite).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrismIcon(Glyph.Prism, "ITU Prism", Cyan)
                    Spacer(Modifier.height(16.dp))
                    sections.forEach { (target, label, icon) -> GlyphButton(icon, label, route == target) { navigate(target) } }
                    Spacer(Modifier.weight(1f)); Text("M1.1", color = Muted, fontSize = 10.sp)
                }
                Column(Modifier.weight(1f)) {
                    if (!editing) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (route == "new") GlyphButton(Glyph.Back, "Back to Studio") { nav.popBackStack() }
                        else { PrismIcon(Glyph.Prism, tint = Cyan); Spacer(Modifier.width(10.dp)) }
                        Text("ITU", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text(" / VIDEO EDITOR", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        if (!bottom && !rail) {
                            GlyphButton(Glyph.Vault, "Media Vault", route == "vault") { navigate("vault") }
                            GlyphButton(Glyph.Settings, "Settings", route == "settings") { navigate("settings") }
                        }
                    }
                    NavHost(nav, startDestination = "dashboard", modifier = Modifier.weight(1f)) {
                        composable("dashboard") { Dashboard(library, vm::retryLibrary, { nav.navigate("new") }, { navigate("vault") }, { nav.navigate("editor/$it") }) }
                        composable("new") { NewProject(busy) { uri, name -> vm.import(uri, name) } }
                        composable("vault") { MediaVault(library, grid, vm::setVaultGrid, vm::retryLibrary) { nav.navigate("editor/$it") } }
                        composable("settings") { Settings(muted, vm::setMuted) }
                        composable("editor/{id}") { backStack ->
                            val context = LocalContext.current
                            val repository = (context.applicationContext as ItuApplication).repository
                            val id = backStack.arguments?.getString("id") ?: return@composable
                            val project by remember(id) { repository.project(id) }.collectAsStateWithLifecycle(initialValue = null)
                            project?.let { EditorScreen(it, muted, vm::saveTrim) { nav.popBackStack() } }
                                ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewProject(busy: Boolean, import: (Uri, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { import(it, name) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("New project", style = MaterialTheme.typography.headlineLarge)
        Text("One source. A fresh perspective.", color = Muted)
        Panel(Modifier.fillMaxWidth()) {
            OutlinedTextField(name, { name = it }, label = { Text("Project name") }, placeholder = { Text("Use video name") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
            Text("Choose a video through Android's file picker. Your original file stays untouched.", color = Muted)
            PrismAction(if (busy) "Reading video…" else "Choose video & create", Glyph.Plus, Modifier.fillMaxWidth(), enabled = !busy, primary = true) { picker.launch(arrayOf("video/*")) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text("Single-source editing · source trim · 720p test export", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Settings(muted: Boolean, setMuted: (Boolean) -> Unit) {
    var report by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(checking) { if (checking) { report = withContext(Dispatchers.IO) { runCatching { EncoderCapabilities.report() }.getOrElse { "Capability query failed: ${it.message}" } }; checking = false } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Mute preview audio", Modifier.weight(1f)); Switch(muted, setMuted) }
            Text("Preview only. Export keeps the source audio.", color = Muted)
        }
        Panel(Modifier.fillMaxWidth()) {
            Text("Device readiness", style = MaterialTheme.typography.titleLarge)
            Text("Inspect the actual device's advertised encoder limits. A configuration still needs a real export test.", color = Muted)
            PrismAction(if (checking) "Querying…" else "Inspect encoders", Glyph.Info, enabled = !checking) { checking = true }
            report?.let { Text(it, fontSize = 12.sp, color = Muted) }
        }
        Text("ITU VIDEO EDITOR · ${BuildConfig.VERSION_NAME}\nCrystal Aurora / Obsidian Precision\nMotion follows Android animation settings.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
