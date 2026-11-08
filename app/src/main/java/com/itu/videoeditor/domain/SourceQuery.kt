package com.itu.videoeditor.domain

import java.util.Locale

data class VaultSource(val uri: String, val name: String, val info: MediaInfo, val importedAt: Long, val projectNames: List<String> = emptyList())
fun querySources(sources: List<VaultSource>, query: String, sort: VaultSort, filter: VaultFilter): List<VaultSource> {
    val search = query.trim().lowercase(Locale.ROOT)
    val filtered = sources.distinctBy { it.uri }.filter { source ->
        val match = search.isEmpty() || source.name.lowercase(Locale.ROOT).contains(search) || source.projectNames.any { it.lowercase(Locale.ROOT).contains(search) }
        val info = source.info
        val known = info.displayWidth != null && info.displayHeight != null && info.rotation != null
        match && when (filter) {
            VaultFilter.All -> true
            VaultFilter.Landscape -> known && info.displayWidth!! >= info.displayHeight!!
            VaultFilter.Portrait -> known && info.displayWidth!! < info.displayHeight!!
        }
    }
    return when (sort) {
        VaultSort.Recent -> filtered.sortedByDescending { it.importedAt }
        VaultSort.Name -> filtered.sortedBy { it.name.lowercase(Locale.ROOT) }
        VaultSort.Duration -> filtered.sortedWith(compareByDescending<VaultSource> { it.info.durationUs ?: -1 }.thenBy { it.uri })
    }
}
