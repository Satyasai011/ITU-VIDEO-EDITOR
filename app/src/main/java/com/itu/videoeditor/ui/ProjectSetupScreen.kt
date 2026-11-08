package com.itu.videoeditor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.itu.videoeditor.domain.*
import com.itu.videoeditor.ui.components.*

@Composable
fun ProjectSetupScreen(vm: StudioViewModel) {
    val pending by vm.setup.collectAsState()
    val busy by vm.busy.collectAsState()
    val previous by vm.lastFormat.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.inspect(uris, name) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (pending == null) "Prism Project Setup" else "Confirm project format", style = MaterialTheme.typography.headlineMedium)
        if (pending == null) {
            Text("Inspect first. Confirm the format. Keep every source untouched.", color = Muted)
            Panel(Modifier.fillMaxWidth()) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Project name") }, singleLine = true, enabled = !busy)
                Text("Select one or more videos. The first selected video proposes the project format. Additional sources are retained in Media Vault; M1.2 edits one source per project.", color = Muted)
                PrismAction(if (busy) "Inspecting selected videos…" else "Choose videos to inspect", Glyph.Plus, enabled = !busy, primary = true) { picker.launch(arrayOf("video/*")) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        } else {
            val setup = pending!!
            val proposal = remember(setup.videos.first().uri) { ProjectFormat.from(setup.videos.first().info) }
            var width by rememberSaveable(setup.videos.first().uri) { mutableStateOf(proposal.width?.toString() ?: "") }
            var height by rememberSaveable(setup.videos.first().uri) { mutableStateOf(proposal.height?.toString() ?: "") }
            var rate by rememberSaveable(setup.videos.first().uri) { mutableStateOf(proposal.frameRate?.let(::decimalRate) ?: "") }
            val chosen = ProjectFormat(width.toIntOrNull(), height.toIntOrNull(), rate.toDoubleOrNull())
            val valid = chosen.valid() && (width.isBlank() || width.toIntOrNull() != null) && (height.isBlank() || height.toIntOrNull() != null) && (rate.isBlank() || rate.toDoubleOrNull() != null)
            Panel(Modifier.fillMaxWidth()) {
                Text("Proposed from first selected video", color = Cyan)
                Text(setup.videos.first().name, style = MaterialTheme.typography.titleMedium)
                Text("${setup.videos.first().info.resolutionLabel} · ${setup.videos.first().info.frameRateLabel}", color = Muted)
                OutlinedTextField(width, { width = it }, Modifier.fillMaxWidth(), label = { Text("Project width · blank = Unknown") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !busy)
                OutlinedTextField(height, { height = it }, Modifier.fillMaxWidth(), label = { Text("Project height · blank = Unknown") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !busy)
                OutlinedTextField(rate, { rate = it }, Modifier.fillMaxWidth(), label = { Text("Project FPS · blank = Unknown") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = !busy)
                if (!valid) Text("Use positive numbers, or leave unavailable values blank.", color = MaterialTheme.colorScheme.error)
                previous?.let { last ->
                    Text("Last confirmed: ${last.width ?: "Unknown"} × ${last.height ?: "Unknown"} · ${last.frameRate?.let(::decimalRate) ?: "Unknown"} FPS. Optional suggestion only.", color = Muted)
                    OutlinedButton({ width = last.width?.toString() ?: ""; height = last.height?.toString() ?: ""; rate = last.frameRate?.let(::decimalRate) ?: "" }, enabled = !busy) { Text("Use last confirmed format") }
                }
                Text("This records project intent. Playback keeps source timing. The real validation export remains 720p; this screen does not enable 4K or FPS conversion.", color = Muted)
                PrismAction(if (busy) "Creating project…" else "Confirm format & create", Glyph.Save, enabled = valid && !busy && setup.videos.first().info.durationUs != null, primary = true) { vm.confirm(chosen) }
                OutlinedButton(vm::cancelSetup, enabled = !busy) { Text("Cancel setup · keep imported media") }
            }
            setup.videos.forEachIndexed { index, video -> Panel(Modifier.fillMaxWidth()) {
                Text("${index + 1}. ${video.name}", style = MaterialTheme.typography.titleMedium)
                val differences = formatMismatch(video.info, chosen)
                if (differences.isNotEmpty()) Text("Format warning: ${differences.joinToString()}", color = Violet)
                MediaInformation(video.info)
            } }
            setup.failures.forEach { Text("Not imported: $it", color = MaterialTheme.colorScheme.error) }
        }
    }
}
