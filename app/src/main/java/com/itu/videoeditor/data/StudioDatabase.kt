package com.itu.videoeditor.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceUri: String,
    val sourceName: String,
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val trimStartUs: Long = 0,
    val trimEndUs: Long,
    val createdAt: Long,
    val updatedAt: Long
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC") fun observeProjects(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE id = :id") fun observeProject(id: String): Flow<ProjectEntity?>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(project: ProjectEntity)
    @Query("UPDATE projects SET trimStartUs = :startUs, trimEndUs = :endUs, updatedAt = :now WHERE id = :id")
    suspend fun trim(id: String, startUs: Long, endUs: Long, now: Long)
}

@Database(entities = [ProjectEntity::class], version = 1, exportSchema = true)
abstract class StudioDatabase : RoomDatabase() { abstract fun projects(): ProjectDao }
