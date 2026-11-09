package com.itu.videoeditor

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itu.videoeditor.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RevisionMigrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun versionTwoUpgradePreservesProjectsMediaAndTrims() = runBlocking {
        val name = "i2-v2-migration.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE projects (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sourceUri TEXT NOT NULL, sourceName TEXT NOT NULL, durationUs INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, rotation INTEGER NOT NULL, trimStartUs INTEGER NOT NULL, trimEndUs INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, formatJson TEXT)")
            old.execSQL("CREATE TABLE media_sources (uri TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, metadataJson TEXT NOT NULL, importedAt INTEGER NOT NULL, inspectedAt INTEGER NOT NULL)")
            old.execSQL("INSERT INTO projects VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf("saved", "Existing", "content://original", "source.mp4", 5_000_000L, 1920, 1080, 0, 500_000L, 3_000_000L, 1L, 2L, "{\"fps\":59.94}"))
            old.execSQL("INSERT INTO media_sources VALUES (?, ?, ?, ?, ?)", arrayOf("content://original", "source.mp4", "{}", 3L, 4L))
            old.version = 2
        }
        val db = Room.databaseBuilder(context, StudioDatabase::class.java, name)
            .addMigrations(StudioDatabase.MIGRATION_1_2, StudioDatabase.MIGRATION_2_3).build()
        try {
            val project = db.projects().observeProject("saved").first()!!
            assertEquals("content://original", project.sourceUri)
            assertEquals(500_000L, project.trimStartUs)
            assertEquals(3_000_000L, project.trimEndUs)
            assertEquals("{\"fps\":59.94}", project.formatJson)
            assertEquals("{}", db.sources().get("content://original")!!.metadataJson)
            assertTrue(db.revisionFoundation().revisions("saved").isEmpty())
            assertTrue(db.revisionFoundation().pins("saved").isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun freshV3RevisionOperationAndPinRollbackTogether() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java).build()
        try {
            db.projects().insert(ProjectEntity("p", "Project", "content://source", "source.mp4", 10_000_000L, 1920, 1080, 0, 0, 10_000_000L, 1L, 1L))
            val dao = db.revisionFoundation()
            val revision = ProjectRevisionEntity("r", "p", 1L, 1, "{}", 2L)
            try {
                db.withTransaction {
                    dao.insertRevision(revision)
                    dao.insertOperation(TimelineOperationEntity("o", "missing-revision", 1L, "trim", 1, "{}", 2L))
                }
                fail("Expected foreign key rejection")
            } catch (_: android.database.sqlite.SQLiteConstraintException) {
                // Foreign-key enforcement prevents orphan operations and rolls back revision.
            } catch (_: androidx.sqlite.SQLiteException) {
                // Room/SQLite may wrap the same constraint failure.
            }
            assertTrue(dao.revisions("p").isEmpty())
            db.withTransaction {
                dao.insertRevision(revision)
                dao.insertOperation(TimelineOperationEntity("o", "r", 1L, "trim", 1, "{}", 2L))
                dao.insertSourcePin(SourcePinEntity("s", "p", "content://source", "{}", "unknown", 2L, null))
            }
            assertEquals(1, dao.revisions("p").size)
            assertEquals(1, dao.operations("r").size)
            assertEquals(1, dao.pins("p").size)
        } finally { db.close() }
    }
}
