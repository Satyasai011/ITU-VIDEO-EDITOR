package com.itu.videoeditor.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itu.videoeditor.ItuApplication
import com.itu.videoeditor.data.ProjectEntity
import com.itu.videoeditor.domain.TrimRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LibraryState(val projects: List<ProjectEntity> = emptyList(), val loading: Boolean = true, val error: String? = null)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ItuApplication).repository
    private val libraryReload = MutableStateFlow(0)
    val library = libraryReload.flatMapLatest {
        repository.projects.map { LibraryState(projects = it, loading = false) }
            .onStart { emit(LibraryState()) }
            .catch { emit(LibraryState(loading = false, error = "Projects could not be loaded: ${it.message}")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryState())
    fun retryLibrary() { libraryReload.value++ }
    val vaultGrid = repository.vaultGrid.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setVaultGrid(grid: Boolean) { viewModelScope.launch {
        runCatching { repository.setVaultGrid(grid) }.onFailure { message.value = "View preference could not be saved" }
    } }
    val muted = repository.muted.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val openedProject = MutableStateFlow<String?>(null)
    fun import(uri: Uri, name: String) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { openedProject.value = repository.import(uri, name) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = "Import failed: ${e.message ?: e.javaClass.simpleName}" }
            finally { busy.value = false }
        }
    }
    fun consumedOpen() { openedProject.value = null }
    fun clearMessage() { message.value = null }
    fun setMuted(value: Boolean) { viewModelScope.launch {
        runCatching { repository.setMuted(value) }.onFailure { message.value = "Preference could not be saved: ${it.message}" }
    } }
    fun saveTrim(project: ProjectEntity, startUs: Long, endUs: Long) {
        viewModelScope.launch {
            runCatching { repository.trim(project.id, TrimRange(startUs, endUs, project.durationUs)) }
                .onSuccess { message.value = "Trim saved. Original media is unchanged." }
                .onFailure { message.value = "Trim failed: ${it.message}" }
        }
    }
}
