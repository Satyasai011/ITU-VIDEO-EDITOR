package com.itu.videoeditor.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itu.videoeditor.BuildConfig
import com.itu.videoeditor.ItuApplication
import com.itu.videoeditor.domain.sizeLabel
import com.itu.videoeditor.media.EncoderCapabilities
import com.itu.videoeditor.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StudioSettings(vm: StudioViewModel) {
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val cache = (LocalContext.current.applicationContext as ItuApplication).thumbnails
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableStateOf<Long?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var cacheError by remember { mutableStateOf<String?>(null) }
    var diagnostic by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(cache) { bytes = cache.cacheBytes() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Implemented controls apply immediately and persist independently of projects.", color = Muted)
        SettingsSection("Video & Playback") {
            PreferenceToggle("Mute preview audio", preferences.muted, vm::setMuted)
            PreferenceToggle("Loop preview playback", preferences.loopPreview) { vm.setPreference("loop_preview", it) }
            Text("Both controls affect preview only; source and export audio remain untouched.", color = Muted)
        }
        SettingsSection("Export & Quality") {
            Text("Implemented: 720p H.264 / AAC validation export. Foreground only; source timing retained.", color = Muted)
            PrismAction(if (checking) "Inspecting encoders…" else "Inspect device encoders", Glyph.Info, enabled = !checking) {
                checking = true
                scope.launch {
                    diagnostic = withContext(Dispatchers.IO) { runCatching { EncoderCapabilities.report() }.getOrElse { "Query failed: ${it.message}" } }
                    checking = false
                }
            }
            diagnostic?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Future capability — production quality presets and 4K60 export are not implemented.", color = Violet)
        }
        SettingsSection("Editing Preferences") {
            PreferenceToggle("Show source-trim guidance", preferences.showHints) { vm.setPreference("show_hints", it) }
            Text("Trim saves are written atomically to Room. Operation journals, undo and recovery snapshots are future capabilities.", color = Muted)
        }
        SettingsSection("Appearance") {
            PreferenceToggle("Reduce application motion", preferences.reduceMotion) { vm.setPreference("reduce_motion", it) }
            Text("Android's disabled-animation settings always take priority. Crystal Aurora / Obsidian Precision is the implemented theme.", color = Muted)
        }
        SettingsSection("Workspace Layouts") {
            PreferenceToggle("Start tablet side panels collapsed", preferences.collapsePanels) { vm.setPreference("collapse_panels", it) }
            PreferenceToggle("Use Grid view in Media Vault", preferences.vaultGrid, vm::setVaultGrid)
            Text("Future capability — named/movable workspace layouts. Current panels remain manually collapsible.", color = Violet)
        }
        SettingsSection("Storage & Performance") {
            Text("Disposable thumbnail disk cache: ${sizeLabel(bytes)}", color = Muted)
            Text("Bounded 48 MiB disk cache, 2–24 MiB bitmap cache and one background thumbnail decoder.", color = Muted)
            PrismAction(if (clearing) "Clearing cache…" else "Clear thumbnail cache", Glyph.Retry, enabled = !clearing) {
                clearing = true; cacheError = null
                scope.launch {
                    try { cache.clearCache(); bytes = cache.cacheBytes() }
                    catch (e: Exception) { cacheError = "Cache could not be cleared: ${e.message}" }
                    finally { clearing = false }
                }
            }
            cacheError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("This clears disposable thumbnails only. Projects, source media and exported videos are not deleted.", color = Muted)
        }
        Text("ITU VIDEO EDITOR · ${BuildConfig.VERSION_NAME}\nSingle-source editing · no M2A or M2B functionality", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Panel(Modifier.fillMaxWidth()) { Text(title, style = MaterialTheme.typography.titleLarge); content() }
}
@Composable
private fun PreferenceToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, change, modifier = Modifier.semantics { contentDescription = label })
    }
}
