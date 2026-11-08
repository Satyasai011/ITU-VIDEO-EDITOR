package com.itu.videoeditor

import com.itu.videoeditor.domain.TrimRange
import org.junit.Assert.assertEquals
import org.junit.Test

class TrimRangeTest {
    @Test fun preservesMicrosecondPrecisionForVfrMedia() {
        val range = TrimRange(41_708, 1_043_377, 2_000_000)
        assertEquals(1_001_669, range.durationUs)
        assertEquals(41_708, range.startUs)
    }
    @Test fun fullSourceIsValid() { assertEquals(3_000_000, TrimRange(0, 3_000_000, 3_000_000).durationUs) }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyRange() { TrimRange(100, 100, 1000) }
    @Test(expected = IllegalArgumentException::class) fun rejectsReversedRange() { TrimRange(200, 100, 1000) }
    @Test(expected = IllegalArgumentException::class) fun rejectsNegativeStart() { TrimRange(-1, 100, 1000) }
    @Test(expected = IllegalArgumentException::class) fun rejectsPastSourceEnd() { TrimRange(0, 1001, 1000) }
    @Test(expected = IllegalArgumentException::class) fun rejectsEmptySource() { TrimRange(0, 0, 0) }
}
