package com.fashionstudio.aipro.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fashionstudio.aipro.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAtMillis DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE title LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' OR garmentCategory LIKE '%' || :query || '%' ORDER BY updatedAtMillis DESC")
    fun searchProjects(query: String): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}

@Dao
interface ReferenceImageDao {
    @Query("SELECT * FROM reference_images WHERE projectId = :projectId ORDER BY createdAtMillis ASC")
    fun observeForProject(projectId: String): Flow<List<ReferenceImageEntity>>

    @Query("SELECT * FROM reference_images WHERE projectId = :projectId ORDER BY createdAtMillis ASC")
    suspend fun getForProject(projectId: String): List<ReferenceImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: ReferenceImageEntity)

    @Query("DELETE FROM reference_images WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ProjectVersionDao {
    @Query("SELECT * FROM project_versions WHERE projectId = :projectId ORDER BY versionNumber DESC")
    fun observeVersions(projectId: String): Flow<List<ProjectVersionEntity>>

    @Query("SELECT * FROM project_versions WHERE projectId = :projectId ORDER BY versionNumber DESC LIMIT 1")
    suspend fun getLatestVersion(projectId: String): ProjectVersionEntity?

    @Query("SELECT COALESCE(MAX(versionNumber), 0) FROM project_versions WHERE projectId = :projectId")
    suspend fun getMaxVersionNumber(projectId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: ProjectVersionEntity)
}

@Dao
interface BrandProfileDao {
    @Query("SELECT * FROM brand_profiles ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<BrandProfileEntity>>

    @Query("SELECT * FROM brand_profiles ORDER BY updatedAtMillis DESC LIMIT 1")
    suspend fun getActiveProfile(): BrandProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: BrandProfileEntity)

    @Query("DELETE FROM brand_profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ApiUsageDao {
    @Query("SELECT * FROM api_usage_logs ORDER BY timestampMillis DESC LIMIT 200")
    fun observeRecentLogs(): Flow<List<ApiUsageLogEntity>>

    @Query("SELECT COALESCE(SUM(estimatedCostUsd), 0.0) FROM api_usage_logs WHERE timestampMillis >= :sinceMillis AND success = 1")
    fun observeSpentSince(sinceMillis: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(estimatedCostUsd), 0.0) FROM api_usage_logs WHERE timestampMillis >= :sinceMillis AND success = 1")
    suspend fun getSpentSince(sinceMillis: Long): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ApiUsageLogEntity)

    @Query("DELETE FROM api_usage_logs")
    suspend fun clearAll()
}

@Dao
interface BatchJobDao {
    @Query("SELECT * FROM batch_jobs ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<BatchJobEntity>>

    @Query("SELECT * FROM batch_jobs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BatchJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: BatchJobEntity)
}
