package com.itu.videoeditor.domain

import com.itu.videoeditor.data.ProjectEntity
import java.util.Locale

enum class VaultSort(val label: String) { Recent("Recent"), Name("Name"), Duration("Duration") }
enum class VaultFilter(val label: String) { All("All videos"), Landscape("Landscape"), Portrait("Portrait") }

fun displayAspect(width: Int, height: Int, rotation: Int): Float {
    if (width <= 0 || height <= 0) return 16f / 9f
    return if (((rotation % 360) + 360) % 180 == 90) height.toFloat() / width else width.toFloat() / height
}

fun queryVault(projects: List<ProjectEntity>, query: String, sort: VaultSort, filter: VaultFilter): List<ProjectEntity> {
    val search = query.trim().lowercase(Locale.ROOT)
    val sources = projects.sortedByDescending { it.updatedAt }.distinctBy { it.sourceUri }.filter {
        val aspect = displayAspect(it.width, it.height, it.rotation)
        (search.isEmpty() || it.name.lowercase(Locale.ROOT).contains(search) || it.sourceName.lowercase(Locale.ROOT).contains(search)) &&
            when (filter) { VaultFilter.All -> true; VaultFilter.Landscape -> it.width > 0 && it.height > 0 && aspect >= 1f; VaultFilter.Portrait -> it.width > 0 && it.height > 0 && aspect < 1f }
    }
    return when (sort) {
        VaultSort.Recent -> sources
        VaultSort.Name -> sources.sortedWith(compareBy<ProjectEntity> { it.sourceName.lowercase(Locale.ROOT) }.thenBy { it.id })
        VaultSort.Duration -> sources.sortedWith(compareByDescending<ProjectEntity> { it.durationUs }.thenBy { it.id })
    }
}
