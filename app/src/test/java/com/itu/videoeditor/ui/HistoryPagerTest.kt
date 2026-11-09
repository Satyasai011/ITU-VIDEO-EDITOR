package com.itu.videoeditor.ui

import com.itu.videoeditor.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HistoryPagerTest {
    private fun entry(n: Long) = RevisionHistoryEntry("id$n", n, n, 1,
        HistoryPayload.CorruptRecord("fixture"), emptyList())
    private fun reader(total: Int): suspend (String, String?, Int) -> RevisionHistoryResult = { project, cursor, limit ->
        val upper = cursor?.toIntOrNull() ?: total
        val end = (upper - limit + 1).coerceAtLeast(1)
        val nums = if (upper < 1) emptyList() else (upper downTo end).toList()
        RevisionHistoryResult.Page(project, nums.map { entry(it.toLong()) },
            if (end > 1) (end - 1).toString() else null)
    }

    @Test fun fortySevenRevisionsLoadTwentyTwentySeven() = runBlocking {
        val pager = HistoryPager(reader(47))
        assertTrue(pager.first("p") is HistoryPager.Outcome.Ready)
        assertEquals(20, pager.loaded.sumOf { it.entries.size })
        pager.older()
        assertEquals(40, pager.loaded.sumOf { it.entries.size })
        pager.older()
        assertEquals(47, pager.loaded.sumOf { it.entries.size })
        assertNull(pager.loaded.last().nextCursor)
    }

    @Test fun slidingWindowKeepsTenPagesAndRevisitsNewer() = runBlocking {
        val pager = HistoryPager(reader(260))
        pager.first("p")
        repeat(10) { assertTrue(pager.older() is HistoryPager.Outcome.Ready) }
        assertEquals(10, pager.loaded.size)
        assertEquals(2, pager.loaded.first().index)
        assertEquals(11, pager.loaded.last().index)
        assertTrue(pager.newer() is HistoryPager.Outcome.Ready)
        assertEquals(1, pager.loaded.first().index)
        assertEquals(10, pager.loaded.last().index)
    }

    @Test fun failedFetchDoesNotEvictWindow() = runBlocking {
        var fail = false
        val base = reader(260)
        val pager = HistoryPager { id, cursor, limit ->
            if (fail) RevisionHistoryResult.ReadFailure("fixture") else base(id, cursor, limit)
        }
        pager.first("p")
        repeat(9) { pager.older() }
        val before = pager.loaded
        fail = true
        assertTrue(pager.older() is HistoryPager.Outcome.Failure)
        assertEquals(before, pager.loaded)
    }

    @Test fun emptyHistoryIsNotAnError() = runBlocking {
        val pager = HistoryPager(reader(0))
        assertTrue(pager.first("p") is HistoryPager.Outcome.Ready)
        assertTrue(pager.loaded.single().entries.isEmpty())
    }
}
