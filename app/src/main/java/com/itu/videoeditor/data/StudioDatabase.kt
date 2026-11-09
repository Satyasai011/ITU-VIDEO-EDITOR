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
@Database(entities = [ProjectEntity::class, MediaSourceEntity::class, ProjectRevisionEntity::class, TimelineOperationEntity::class, SourcePinEntity::class], version = 3, exportSchema = true)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun sources(): SourceDao
    abstract fun revisionFoundation(): RevisionFoundationDao
    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `project_revisions` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `revisionNumber` INTEGER NOT NULL, `snapshotVersion` INTEGER NOT NULL, `snapshotJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_project_revisions_projectId_revisionNumber` ON `project_revisions` (`projectId`, `revisionNumber`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `timeline_operations` (`id` TEXT NOT NULL, `revisionId` TEXT NOT NULL, `sequenceNumber` INTEGER NOT NULL, `operationType` TEXT NOT NULL, `payloadVersion` INTEGER NOT NULL, `payloadJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`revisionId`) REFERENCES `project_revisions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_timeline_operations_revisionId_sequenceNumber` ON `timeline_operations` (`revisionId`, `sequenceNumber`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `source_pins` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `sourceUri` TEXT NOT NULL, `expectedMetadataJson` TEXT NOT NULL, `availabilityState` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `checkedAt` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_source_pins_projectId_sourceUri` ON `source_pins` (`projectId`, `sourceUri`)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN formatJson TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS media_sources (uri TEXT NOT NULL, name TEXT NOT NULL, metadataJson TEXT NOT NULL, importedAt INTEGER NOT NULL, inspectedAt INTEGER NOT NULL, PRIMARY KEY(uri))")
            }
        }
    }
}
