package com.itu.videoeditor.domain

import org.junit.Assert.*
import org.junit.Test

class SourceSegmentTest {
    @Test fun splitPreservesEveryMicrosecond() {
        val source = SourceSegment(100L, 1000100L)
        val split = splitSourceSegment(source, 400100L)
        assertEquals(SourceSegment(100L, 400100L), split.left)
        assertEquals(SourceSegment(400100L, 1000100L), split.right)
        assertEquals(source.durationUs, split.left.durationUs + split.right.durationUs)
    }

    @Test fun splittingSelectedSegmentPreservesOthers() {
        val clips = listOf(SourceSegment(0, 1000), SourceSegment(2000, 4000))
        assertEquals(
            listOf(SourceSegment(0, 1000), SourceSegment(2000, 3000), SourceSegment(3000, 4000)),
            splitSegmentAt(clips, 1, 3000)
        )
        assertEquals(2, clips.size)
    }

    @Test fun rejectsBoundaryAndOutsideSplits() {
        val clip = SourceSegment(100, 200)
        for (at in listOf(0L, 100L, 200L, 300L)) {
            assertThrows(IllegalArgumentException::class.java) { splitSourceSegment(clip, at) }
        }
    }

    @Test fun rejectsInvalidSegmentsAndIndices() {
        assertThrows(IllegalArgumentException::class.java) { SourceSegment(-1, 10) }
        assertThrows(IllegalArgumentException::class.java) { SourceSegment(10, 10) }
        assertThrows(IllegalArgumentException::class.java) { splitSegmentAt(listOf(SourceSegment(0, 10)), 1, 5) }
    }
}
