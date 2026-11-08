package com.itu.videoeditor.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String, val name: String, val sourceUri: String, val sourceName: String,
    val durationUs: Long, val width: Int, val height: Int, val rotation: Int,
    val trimStartUs: Long = 0, val trimEndUs: Long, val createdAt: Long, val updatedAt: Long,
    val formatJson: String? = null
)
@Entity(tableName = "media_sources")
data class MediaSourceEntity(@PrimaryKey val uri: String, val name: String, val metadataJson: String, val importedAt: Long, val inspectedAt: Long)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC") fun observeProjects(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE id = :id") fun observeProject(id: String): Flow<ProjectEntity?>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(project: ProjectEntity)
    @Query("UPDATE projects SET trimStartUs = :startUs, trimEndUs = :endUs, updatedAt = :now WHERE id = :id")
    suspend fun trim(id: String, startUs: Long, endUs: Long, now: Long)
}
@Dao
interface SourceDao {
    @Query("SELECT * FROM media_sources ORDER BY importedAt DESC") fun observeSources(): Flow<List<MediaSourceEntity>>
    @Query("SELECT * FROM media_sources WHERE uri = :uri") suspend fun get(uri: String): MediaSourceEntity?
    @Upsert suspend fun save(source: MediaSourceEntity)
}
@Database(entities = [ProjectEntity::class, MediaSourceEntity::class], version = 2, exportSchema = true)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun sources(): SourceDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN formatJson TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS media_sources (uri TEXT NOT NULL, name TEXT NOT NULL, metadataJson TEXT NOT NULL, importedAt INTEGER NOT NULL, inspectedAt INTEGER NOT NULL, PRIMARY KEY(uri))")
            }
        }
    }
}
