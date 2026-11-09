package com.itu.videoeditor.data

import androidx.room.*

/**
 * Additive v3 persistence contracts. These records are not yet wired into the
 * editing UI. Existing projects and imported media remain authoritative.
 */
@Entity(
    tableName = "project_revisions",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["projectId", "revisionNumber"], unique = true)]
)
data class ProjectRevisionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val revisionNumber: Long,
    val snapshotVersion: Int,
    val snapshotJson: String,
    val createdAt: Long
)

@Entity(
    tableName = "timeline_operations",
    foreignKeys = [ForeignKey(entity = ProjectRevisionEntity::class, parentColumns = ["id"], childColumns = ["revisionId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["revisionId", "sequenceNumber"], unique = true)]
)
data class TimelineOperationEntity(
    @PrimaryKey val id: String,
    val revisionId: String,
    val sequenceNumber: Long,
    val operationType: String,
    val payloadVersion: Int,
    val payloadJson: String,
    val createdAt: Long
)

@Entity(
    tableName = "source_pins",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["projectId", "sourceUri"], unique = true)]
)
data class SourcePinEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val sourceUri: String,
    val expectedMetadataJson: String,
    val availabilityState: String,
    val createdAt: Long,
    val checkedAt: Long?
)

@Dao
interface RevisionFoundationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: ProjectRevisionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperation(operation: TimelineOperationEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSourcePin(pin: SourcePinEntity)

    @Query("SELECT * FROM project_revisions WHERE projectId = :projectId ORDER BY revisionNumber")
    suspend fun revisions(projectId: String): List<ProjectRevisionEntity>

    @Query("SELECT * FROM timeline_operations WHERE revisionId = :revisionId ORDER BY sequenceNumber")
    suspend fun operations(revisionId: String): List<TimelineOperationEntity>

    @Query("SELECT * FROM source_pins WHERE projectId = :projectId")
    suspend fun pins(projectId: String): List<SourcePinEntity>
}
