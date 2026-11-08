package com.itu.videoeditor

import com.itu.videoeditor.data.ProjectEntity
import com.itu.videoeditor.domain.*
import org.junit.Assert.*
import org.junit.Test

class VaultQueryTest {
    private fun clip(id: String, name: String, width: Int = 1920, height: Int = 1080, rotation: Int = 0, duration: Long = 5_000_000, updated: Long = 1, uri: String = id) =
        ProjectEntity(id, name, "content://$uri", "$name.mp4", duration, width, height, rotation, trimEndUs = duration, createdAt = 1, updatedAt = updated)
    @Test fun searchMatchesSourceAndProjectIgnoringCaseAndWhitespace() {
        val clips = listOf(clip("a", "Gameplay"), clip("b", "Lobby"))
        assertEquals(listOf("a"), queryVault(clips, " GAME ", VaultSort.Recent, VaultFilter.All).map { it.id })
        assertEquals(listOf("b"), queryVault(clips, "lobby.mp4", VaultSort.Recent, VaultFilter.All).map { it.id })
    }
    @Test fun rotatedLandscapeSourceIsClassifiedAsPortrait() {
        val clips = listOf(clip("a", "Rotated", rotation = 90), clip("b", "Wide"))
        assertEquals(listOf("a"), queryVault(clips, "", VaultSort.Name, VaultFilter.Portrait).map { it.id })
        assertEquals(listOf("b"), queryVault(clips, "", VaultSort.Name, VaultFilter.Landscape).map { it.id })
    }
    @Test fun repeatedSourceKeepsMostRecentlyEditedProjectReference() {
        val clips = listOf(clip("old", "Old", updated = 1, uri = "same"), clip("new", "New", updated = 5, uri = "same"))
        assertEquals(listOf("new"), queryVault(clips, "", VaultSort.Recent, VaultFilter.All).map { it.id })
    }
    @Test fun sortingUsesSourceDurationAndDeterministicNames() {
        val clips = listOf(clip("b", "Beta", duration = 8_000_000), clip("a", "alpha", duration = 2_000_000))
        assertEquals(listOf("a", "b"), queryVault(clips, "", VaultSort.Name, VaultFilter.All).map { it.id })
        assertEquals(listOf("b", "a"), queryVault(clips, "", VaultSort.Duration, VaultFilter.All).map { it.id })
    }
    @Test fun displayAspectHonorsRotationAndInvalidMetadataFallback() {
        assertEquals(9f / 16f, displayAspect(1920, 1080, 270), 0.0001f)
        assertEquals(16f / 9f, displayAspect(0, 0, 0), 0.0001f)
        assertEquals(16f / 9f, displayAspect(1920, 1080, 180), 0.0001f)
    }
    @Test fun orientationFiltersExcludeUnknownMetadata() {
        val unknown = listOf(clip("u", "Unknown", width = 0, height = 0))
        assertEquals(1, queryVault(unknown, "", VaultSort.Recent, VaultFilter.All).size)
        assertTrue(queryVault(unknown, "", VaultSort.Recent, VaultFilter.Landscape).isEmpty())
        assertTrue(queryVault(unknown, "", VaultSort.Recent, VaultFilter.Portrait).isEmpty())
    }
    @Test fun emptyResultsAreExplicit() {
        assertTrue(queryVault(listOf(clip("a", "Game")), "missing", VaultSort.Recent, VaultFilter.All).isEmpty())
    }
}
