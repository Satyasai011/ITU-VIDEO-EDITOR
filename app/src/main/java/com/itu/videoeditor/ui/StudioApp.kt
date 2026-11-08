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
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val grid by vm.vaultGrid.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val opened by vm.openedProject.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dashboard"
    val editing = route.startsWith("editor")
    fun navigate(target: String) { nav.navigate(target) { popUpTo("dashboard"); launchSingleTop = true } }
    val sections = listOf(Triple("dashboard", "Studio", Glyph.Studio), Triple("vault", "Media Vault", Glyph.Vault), Triple("settings", "Settings", Glyph.Settings))
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    LaunchedEffect(setup) { if (setup != null && nav.currentDestination?.route != "new") nav.navigate("new") }
    LaunchedEffect(opened) { opened?.let { nav.navigate("editor/$it") { popUpTo("dashboard") }; vm.consumedOpen() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 1000.dp
        val bottom = !rail && !editing && route != "new" && maxHeight >= 480.dp
        Scaffold(containerColor = Obsidian, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            if (bottom) NavigationBar(containerColor = Graphite, tonalElevation = 0.dp) {
                sections.forEach { (target, label, icon) -> NavigationBarItem(route == target, { navigate(target) }, icon = { PrismIcon(icon) }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Cyan.copy(alpha = 0.10f), selectedIconColor = Cyan)) }
            }
        }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                if (rail) Column(Modifier.width(80.dp).fillMaxHeight().background(Graphite).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrismIcon(Glyph.Prism, "ITU Prism", Cyan)
                    Spacer(Modifier.height(16.dp))
                    sections.forEach { (target, label, icon) -> GlyphButton(icon, label, route == target) { navigate(target) } }
                    Spacer(Modifier.weight(1f)); Text("M1.2", color = Muted, fontSize = 10.sp)
                }
                Column(Modifier.weight(1f)) {
                    if (!editing) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (route == "new") GlyphButton(Glyph.Back, "Back to Studio") { nav.popBackStack() }
                        else { PrismIcon(Glyph.Prism, tint = Cyan); Spacer(Modifier.width(10.dp)) }
                        Text("ITU", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text(" / VIDEO EDITOR", Modifier.weight(1f), color = Muted, fontSize = 10.sp, letterSpacing = 1.sp,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (!bottom && !rail) {
                            GlyphButton(Glyph.Vault, "Media Vault", route == "vault") { navigate("vault") }
                            GlyphButton(Glyph.Settings, "Settings", route == "settings") { navigate("settings") }
                        }
                    }
                    NavHost(nav, startDestination = "dashboard", modifier = Modifier.weight(1f)) {
                        composable("dashboard") { Dashboard(library, vm::retryLibrary, { nav.navigate("new") }, { navigate("vault") }, { nav.navigate("editor/$it") }) }
                        composable("new") { ProjectSetupScreen(vm) }
                        composable("vault") { MediaVault(library, grid, vm::setVaultGrid, vm::retryLibrary, vm::openSource) { nav.navigate("editor/$it") } }
                        composable("settings") { StudioSettings(vm) }
                        composable("editor/{id}") { backStack ->
                            val context = LocalContext.current
                            val repository = (context.applicationContext as ItuApplication).repository
                            val id = backStack.arguments?.getString("id") ?: return@composable
                            val project by remember(id) { repository.project(id) }.collectAsStateWithLifecycle(initialValue = null)
                            project?.let { EditorScreen(it, muted, vm::saveTrim, preferences, library.metadata[it.sourceUri], { vm.refreshMetadata(it.sourceUri) }) { nav.popBackStack() } }
                                ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }
    }
}
