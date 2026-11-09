package com.itu.videoeditor

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.itu.videoeditor.data.StudioDatabase
import com.itu.videoeditor.data.StudioRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyMigrationTest {
    @Test fun versionOneMigrationKeepsSourceAndSavedTrimWithoutInventingFormat() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "legacy-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE projects (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sourceUri TEXT NOT NULL, sourceName TEXT NOT NULL, durationUs INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, rotation INTEGER NOT NULL, trimStartUs INTEGER NOT NULL, trimEndUs INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            old.execSQL("INSERT INTO projects VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf("saved", "Existing", "content://original", "source.mp4", 5_000_000L, 1920, 1080, 0, 500_000L, 3_000_000L, 1L, 2L))
            old.version = 1
        }
        val db = Room.databaseBuilder(context, StudioDatabase::class.java, name).addMigrations(StudioDatabase.MIGRATION_1_2, StudioDatabase.MIGRATION_2_3).build()
        try {
            val project = db.projects().observeProject("saved").first()!!
            assertEquals("content://original", project.sourceUri)
            assertEquals(500_000L, project.trimStartUs); assertEquals(3_000_000L, project.trimEndUs)
            assertNull(project.formatJson)
            assertTrue(db.sources().observeSources().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun vaultViewPreferencePersistsAcrossRepositoryInstances() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, StudioDatabase::class.java).build()
        val first = StudioRepository(context, db)
        val original = first.vaultGrid.first()
        try {
            first.setVaultGrid(false)
            assertFalse(StudioRepository(context, db).vaultGrid.first())
            first.setVaultGrid(true)
            assertTrue(StudioRepository(context, db).vaultGrid.first())
        } finally { first.setVaultGrid(original); db.close() }
    }
}
