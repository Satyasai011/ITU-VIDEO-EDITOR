package com.itu.videoeditor

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itu.videoeditor.data.*
import com.itu.videoeditor.domain.TrimRange
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RevisionHistoryReadTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun seed(db: StudioDatabase, id: String = "p") {
        db.projects().insert(ProjectEntity(id, "Project", "content://original", "source.mp4",
            10_000_000L, 1920, 1080, 0, 0L, 10_000_000L, 1L, 1L))
    }

    private suspend fun add(db: StudioDatabase, projectId: String, n: Int, version: Int = 1,
                            snapshot: String = """{"projectId":"p","sourceUri":"content://original","durationUs":10000000,"trimStartUs":0,"trimEndUs":10000000}""") {
        val id = "$projectId-$n"
        db.revisionFoundation().insertRevision(ProjectRevisionEntity(id, projectId, n.toLong(), version, snapshot, n.toLong()))
        db.revisionFoundation().insertOperation(TimelineOperationEntity("$id-op", id, 1L, "trim", 1,
            """{"previousStartUs":0,"previousEndUs":10000000,"startUs":1000000,"endUs":9000000}""", n.toLong()))
    }

    private fun page(result: RevisionHistoryResult) = result as RevisionHistoryResult.Page

    @Test fun fortySevenRevisionsPageWithoutDuplicates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java).build()
        try {
            seed(db)
            for (n in 1..47) add(db, "p", n)
            val reader = RevisionHistoryReader(db)
            val first = page(reader.read("p"))
            assertEquals(20, first.entries.size)
            assertEquals(47L, first.entries.first().revisionNumber)
            val second = page(reader.read("p", first.nextCursor))
            val third = page(reader.read("p", second.nextCursor))
            assertEquals(listOf(20, 20, 7), listOf(first.entries.size, second.entries.size, third.entries.size))
            assertNull(third.nextCursor)
            assertEquals((47 downTo 1).map { it.toLong() },
                (first.entries + second.entries + third.entries).map { it.revisionNumber })
            assertTrue(first.entries.all { it.operations.size == 1 && it.operations.single().payload is HistoryPayload.TrimOperation })
        } finally { db.close() }
    }

    @Test fun exactlyTwentyAndLegacyEmptyHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java).build()
        try {
            seed(db)
            val reader = RevisionHistoryReader(db)
            assertTrue(page(reader.read("p")).entries.isEmpty())
            assertTrue(reader.read("missing") is RevisionHistoryResult.ProjectNotFound)
            for (n in 1..20) add(db, "p", n)
            val result = page(reader.read("p"))
            assertEquals(20, result.entries.size)
            assertNull(result.nextCursor)
        } finally { db.close() }
    }

    @Test fun newRevisionsDoNotEnterAnExistingPaginationSession() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java).build()
        try {
            seed(db)
            for (n in 1..25) add(db, "p", n)
            val reader = RevisionHistoryReader(db)
            val first = page(reader.read("p"))
            add(db, "p", 26)
            val second = page(reader.read("p", first.nextCursor))
            assertEquals((25 downTo 1).map { it.toLong() },
                (first.entries + second.entries).map { it.revisionNumber })
            assertNull(second.nextCursor)
            assertEquals(26L, page(reader.read("p")).entries.first().revisionNumber)
        } finally { db.close() }
    }

    @Test fun cursorCannotCrossProjectsOrPageSizes() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java).build()
        try {
            seed(db)
            seed(db, "other")
            for (n in 1..22) add(db, "p", n)
            val first = page(RevisionHistoryReader(db).read("p"))
            val reader = RevisionHistoryReader(db)
            assertTrue(reader.read("other", first.nextCursor) is RevisionHistoryResult.InvalidRequest)
            assertTrue(reader.read("p", first.nextCursor, 10) is RevisionHistoryResult.InvalidRequest)
            assertTrue(reader.read("p", "invalid cursor") is RevisionHistoryResult.InvalidRequest)
            assertTrue(reader.read("p", limit = 21) is RevisionHistoryResult.InvalidRequest)
            assertTrue(reader.read("") is RevisionHistoryResult.InvalidRequest)
        } finally { db.close() }
    }

    @Test fun corruptAndUnsupportedPayloadsRemainVisible() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java).build()
        try {
            seed(db)
            add(db, "p", 1, 999)
            add(db, "p", 2, 1, "not-json")
            val result = page(RevisionHistoryReader(db).read("p"))
            assertEquals(2, result.entries.size)
            assertTrue(result.entries[0].snapshot is HistoryPayload.CorruptRecord)
            assertTrue(result.entries[1].snapshot is HistoryPayload.UnsupportedVersion)
        } finally { db.close() }
    }

    @Test fun readDoesNotMutateProjectOrRevisionHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java).build()
        try {
            seed(db)
            val repo = StudioRepository(context, db)
            repo.trim("p", TrimRange(1_000_000L, 9_000_000L, 10_000_000L))
            val before = db.projects().get("p")!!
            val revisions = db.revisionFoundation().revisions("p")
            val result = page(repo.getRevisionHistory("p"))
            assertEquals(1, result.entries.size)
            assertEquals(before, db.projects().get("p"))
            assertEquals(revisions, db.revisionFoundation().revisions("p"))
            assertTrue(db.revisionFoundation().pins("p").isEmpty())
        } finally { db.close() }
    }
}
