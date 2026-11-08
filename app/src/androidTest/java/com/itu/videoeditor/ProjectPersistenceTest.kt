package com.itu.videoeditor

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itu.videoeditor.data.ProjectEntity
import com.itu.videoeditor.data.StudioDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectPersistenceTest {
    @Test fun savedTrimSurvivesDatabaseReopenAndKeepsSource() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, StudioDatabase::class.java, name).build()
        try {
            val project = ProjectEntity("test", "Gameplay", "content://test/source", "game.mp4", 5_000_000, 1920, 1080, 0,
                trimEndUs = 5_000_000, createdAt = 1, updatedAt = 1)
            db.projects().insert(project)
            db.projects().trim("test", 500_000, 3_000_000, 2)
            db.close()
            db = Room.databaseBuilder(context, StudioDatabase::class.java, name).build()
            val saved = db.projects().observeProject("test").first()!!
            assertEquals(500_000L, saved.trimStartUs)
            assertEquals(3_000_000L, saved.trimEndUs)
            assertEquals(project.sourceUri, saved.sourceUri)
            assertEquals(5_000_000L, saved.durationUs)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
