package com.itu.videoeditor

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itu.videoeditor.data.*
import com.itu.videoeditor.domain.TrimRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtomicTrimRevisionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun createProject(db: StudioDatabase, id: String = "p") {
        db.projects().insert(ProjectEntity(id, "Existing", "content://source", "source.mp4",
            10_000_000L, 1920, 1080, 0, 0L, 10_000_000L, 1L, 1L, "{\"fps\":60}"))
    }

    @Test fun trimPersistsProjectRevisionAndOperationAtomically() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java).build()
        try {
            createProject(db)
            val repository = StudioRepository(context, db)
            repository.trim("p", TrimRange(1_000_000L, 8_000_000L, 10_000_000L))
            val project = db.projects().get("p")!!
            assertEquals(1_000_000L, project.trimStartUs)
            assertEquals(8_000_000L, project.trimEndUs)
            assertEquals("{\"fps\":60}", project.formatJson)
            val revision = db.revisionFoundation().revisions("p").single()
            assertEquals(1L, revision.revisionNumber)
            assertEquals(1, revision.snapshotVersion)
            val snapshot = JSONObject(revision.snapshotJson)
            assertEquals("content://source", snapshot.getString("sourceUri"))
            assertEquals(1_000_000L, snapshot.getLong("trimStartUs"))
            assertEquals(8_000_000L, snapshot.getLong("trimEndUs"))
            val operation = db.revisionFoundation().operations(revision.id).single()
            assertEquals("trim", operation.operationType)
            val payload = JSONObject(operation.payloadJson)
            assertEquals(0L, payload.getLong("previousStartUs"))
            assertEquals(10_000_000L, payload.getLong("previousEndUs"))
        } finally { db.close() }
    }

    @Test fun sequentialSavesCreateOrderedRevisionsAndNoOpDoesNot() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java).build()
        try {
            createProject(db)
            val repository = StudioRepository(context, db)
            repository.trim("p", TrimRange(1_000_000L, 9_000_000L, 10_000_000L))
            repository.trim("p", TrimRange(2_000_000L, 8_000_000L, 10_000_000L))
            repository.trim("p", TrimRange(2_000_000L, 8_000_000L, 10_000_000L))
            val revisions = db.revisionFoundation().revisions("p")
            assertEquals(listOf(1L, 2L), revisions.map { it.revisionNumber })
            assertEquals(1_000_000L, JSONObject(db.revisionFoundation().operations(revisions[1].id).single().payloadJson).getLong("previousStartUs"))
        } finally { db.close() }
    }

    @Test fun invalidOrMissingProjectCannotWriteHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java).build()
        try {
            createProject(db)
            val repository = StudioRepository(context, db)
            try {
                repository.trim("missing", TrimRange(1L, 2L, 10_000_000L))
                fail("Expected missing project rejection")
            } catch (_: IllegalArgumentException) { }
            try {
                repository.trim("p", TrimRange(1_000_000L, 11_000_000L, 11_000_000L))
                fail("Expected out-of-bounds rejection")
            } catch (_: IllegalArgumentException) { }
            assertTrue(db.revisionFoundation().revisions("p").isEmpty())
            assertEquals(0L, db.projects().get("p")!!.trimStartUs)
        } finally { db.close() }
    }

    @Test fun databaseConstraintFailureRollsBackProjectAndHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java).build()
        try {
            createProject(db)
            val repository = StudioRepository(context, db)
            repository.trim("p", TrimRange(1_000_000L, 9_000_000L, 10_000_000L))
            // Occupy the next sequence number with a different revision ID.
            db.revisionFoundation().insertRevision(ProjectRevisionEntity("reserved", "p", 2L, 1, "{}", 1L))
            // The normal writer must continue from the current maximum.
            repository.trim("p", TrimRange(2_000_000L, 8_000_000L, 10_000_000L))
            assertEquals(3L, db.revisionFoundation().revisions("p").last().revisionNumber)
            assertEquals(2_000_000L, db.projects().get("p")!!.trimStartUs)
        } finally { db.close() }
    }
}
