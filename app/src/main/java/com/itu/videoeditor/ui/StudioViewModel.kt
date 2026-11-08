package com.itu.videoeditor.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itu.videoeditor.ItuApplication
import com.itu.videoeditor.data.*
import com.itu.videoeditor.domain.*
import com.itu.videoeditor.media.InspectedVideo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LibraryState(val projects: List<ProjectEntity> = emptyList(), val loading: Boolean = true, val error: String? = null,
    val sources: List<MediaSourceEntity> = emptyList()) {
    val metadata: Map<String, MediaInfo> by lazy { sources.associate { it.uri to MetadataJson.decode(it.metadataJson) } }
}
data class ProjectSetup(val videos: List<InspectedVideo>, val failures: List<String>, val name: String)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ItuApplication).repository
    private val libraryReload = MutableStateFlow(0)
    val library = libraryReload.flatMapLatest {
        combine(repository.projects, repository.sources) { projects, sources ->
            val known = sources.map { it.uri }.toSet()
            val legacy = projects.distinctBy { it.sourceUri }.filter { it.sourceUri !in known }.map { p ->
                MediaSourceEntity(p.sourceUri, p.sourceName, MetadataJson.encode(MediaInfo(width = p.width.takeIf { it > 0 }, height = p.height.takeIf { it > 0 }, rotation = p.rotation.takeIf { it != 0 }, durationUs = p.durationUs)), p.createdAt, 0)
            }
            LibraryState(projects, loading = false, sources = (sources + legacy).sortedByDescending { it.importedAt })
        }
            .onStart { emit(LibraryState()) }.catch { emit(LibraryState(loading = false, error = "Library could not be loaded: ${it.message}")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryState())
    val preferences = repository.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudioPreferences())
    val vaultGrid = repository.vaultGrid.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val muted = repository.muted.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val lastFormat = repository.lastFormat.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val openedProject = MutableStateFlow<String?>(null)
    val setup = MutableStateFlow<ProjectSetup?>(null)
    init { viewModelScope.launch { safe { repository.ensureLegacySources() } } }
    private suspend fun safe(block: suspend () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { message.value = e.message ?: "Operation failed" }
    }
    fun retryLibrary() { libraryReload.value++; viewModelScope.launch { safe { repository.ensureLegacySources() } } }
    fun setVaultGrid(value: Boolean) { viewModelScope.launch { safe { repository.setVaultGrid(value) } } }
    fun setMuted(value: Boolean) { viewModelScope.launch { safe { repository.setMuted(value) } } }
    fun setPreference(key: String, value: Boolean) { viewModelScope.launch { safe { repository.setPreference(key, value) } } }
    fun refreshMetadata(uri: String) { viewModelScope.launch { safe { repository.refresh(uri) } } }
    fun inspect(uris: List<Uri>, name: String) {
        if (busy.value || uris.isEmpty()) return
        viewModelScope.launch {
            busy.value = true
            val videos = mutableListOf<InspectedVideo>(); val failures = mutableListOf<String>()
            try {
                for (uri in uris.distinct()) {
                    try { videos += repository.inspectImport(uri) }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { failures += "${uri.lastPathSegment ?: "Selected source"}: ${e.message}" }
                }
                if (videos.isEmpty()) message.value = "Import failed: ${failures.joinToString()}"
                else if (videos.first().uri != uris.first().toString()) message.value = "The first selected video could not be inspected. Other readable sources were saved in Media Vault; choose one there to set up a project. ${failures.joinToString()}"
                else setup.value = ProjectSetup(videos, failures, name)
            } finally { busy.value = false }
        }
    }
    fun openSource(source: MediaSourceEntity) { setup.value = ProjectSetup(listOf(InspectedVideo(source.uri, source.name, MetadataJson.decode(source.metadataJson))), emptyList(), "") }
    fun cancelSetup() { setup.value = null }
    fun confirm(format: ProjectFormat) {
        val pending = setup.value ?: return
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { safe { openedProject.value = repository.createProject(pending.videos.first(), pending.name, format); setup.value = null } }
            finally { busy.value = false }
        }
    }
    fun consumedOpen() { openedProject.value = null }
    fun clearMessage() { message.value = null }
    fun saveTrim(project: ProjectEntity, startUs: Long, endUs: Long) { viewModelScope.launch { safe {
        repository.trim(project.id, TrimRange(startUs, endUs, project.durationUs)); message.value = "Trim saved. Original media is unchanged."
    } } }
}
