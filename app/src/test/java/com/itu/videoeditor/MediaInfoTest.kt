package com.itu.videoeditor

import com.itu.videoeditor.domain.*
import org.junit.Assert.*
import org.junit.Test

class MediaInfoTest {
    @Test fun fractional5994RateIsNotRoundedTo60() {
        val pts = (0 until 360).map { it * 1_001_000L / 60 }
        val timing = analyzeTiming(pts)!!
        assertEquals(60000.0 / 1001, timing.fps, 0.001)
        assertEquals("59.94", decimalRate(timing.fps))
        assertFalse(timing.variable)
    }
    @Test fun bFrameReorderingAndDuplicateSamplesDoNotInventRate() {
        val pts = (0 until 120).map { it * 1_001_000L / 60 }.reversed()
        assertEquals(60000.0 / 1001, analyzeTiming(pts + pts.take(3))!!.fps, 0.001)
    }
    @Test fun varyingCadenceIsFlaggedAndSparseTimingIsUnknown() {
        val pts = mutableListOf(0L)
        repeat(60) { pts += pts.last() + if (it % 2 == 0) 16_667L else 33_333L }
        assertTrue(analyzeTiming(pts)!!.variable)
        assertNull(analyzeTiming(listOf(0L, 1L)))
    }
    @Test fun missingMetadataDisplaysUnknown() {
        val info = MediaInfo()
        assertEquals("Unknown", info.frameRateLabel)
        assertEquals("Unknown", info.resolutionLabel)
        assertEquals("Unknown", info.orientationLabel)
        assertEquals("Unknown", codecLabel(info.codec))
        assertEquals("Unknown", sizeLabel(info.sizeBytes))
        assertEquals("Unknown", bitrateLabel(info.videoBitrate))
        assertNull(info.audio)
        assertNotNull(MediaInfo(audio = emptyList()).audio)
    }
    @Test fun projectProposalUsesFirstSourceDisplayDimensionsAndFractionalRate() {
        val source = MediaInfo(width = 3840, height = 2160, rotation = 90, frameRate = 60000.0 / 1001)
        val proposal = ProjectFormat.from(source)
        assertEquals(2160, proposal.width); assertEquals(3840, proposal.height)
        assertEquals(60000.0 / 1001, proposal.frameRate!!, 0.00001)
        assertTrue(formatMismatch(source, ProjectFormat(1920, 1080, 60.0)).containsAll(listOf("resolution", "frame rate")))
        assertFalse(formatMismatch(source, proposal).contains("frame rate"))
    }
    @Test fun formatValidationAllowsUnknownButRejectsInvalidValues() {
        assertTrue(ProjectFormat(null, null, null).valid())
        assertFalse(ProjectFormat(-1, 1080, 60.0).valid())
        assertFalse(ProjectFormat(1920, 1080, Double.NaN).valid())
    }
    @Test fun independentlyImportedSourcesRemainVisibleWithoutProjects() {
        val info = MediaInfo(width = 1920, height = 1080, rotation = 0)
        val sources = listOf(VaultSource("a", "One", info, 1, listOf("Project 1", "Project 2")), VaultSource("b", "Two", info, 2), VaultSource("c", "Three", info, 3))
        assertEquals(3, querySources(sources, "", VaultSort.Recent, VaultFilter.All).size)
        assertEquals(listOf("a"), querySources(sources, "Project 2", VaultSort.Name, VaultFilter.All).map { it.uri })
    }
}
