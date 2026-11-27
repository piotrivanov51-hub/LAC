package com.fashionstudio.aipro.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sku: String,
    val folderName: String = "Default",
    val garmentCategory: String,
    val primaryColor: String,
    val secondaryColorsJson: String = "[]",
    val estimatedMaterial: String = "",
    val fitStyle: String = "",
    val hasPrints: Boolean = false,
    val hasLogos: Boolean = false,
    val studioMode: String,                // AI_PRODUCT_STUDIO, AI_FASHION_MODEL, BACKGROUND_REPLACEMENT, etc.
    val fidelityMode: String,              // ORIGINAL_PIXEL_PROTECTION (Mode A) or AUTHENTIC_VIRTUAL_TRY_ON (Mode B)
    val dnaLockStatus: String,             // PROTECTED, VERIFIED, REVIEW_REQUIRED, FIDELITY_FAILED, VIRTUAL_RECONSTRUCTION
    val immutableOriginalPath: String,
    val originalSha256: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val protectedMaskPath: String? = null,
    val criticalDetailMaskPath: String? = null,
    val latestOutputPath: String? = null,
    val generationConfigJson: String = "{}",
    val availableAnglesJson: String = "[\"FRONT\"]",
    val unknownCharacteristicsJson: String = "[]",
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "reference_images",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ReferenceImageEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val angleType: String,                 // FRONT, BACK, LEFT_SIDE, RIGHT_SIDE, FABRIC_MACRO, STITCHING, POCKET, LOGO, BUTTON, LABEL
    val immutableFilePath: String,
    val sha256: String,
    val width: Int,
    val height: Int,
    val createdAtMillis: Long
)

@Entity(
    tableName = "project_versions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ProjectVersionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val versionNumber: Int,
    val outputImagePath: String,
    val diffHeatmapPath: String? = null,
    val fidelityModeUsed: String,
    val dnaStatus: String,                 // VERIFIED, REVIEW_REQUIRED, FIDELITY_FAILED, VIRTUAL_RECONSTRUCTION
    val ssimScore: Double,
    val deltaE00Mean: Double,
    val deltaE00Max: Double,
    val exactInteriorMatchRatio: Double,
    val edgePreservationScore: Double,
    val criticalDetailPass: Boolean,
    val aiQualityReportJson: String? = null,
    val generationConfigJson: String,
    val providerId: String,
    val modelUsed: String,
    val estimatedCostUsd: Double,
    val createdAtMillis: Long
)

@Entity(tableName = "brand_profiles")
data class BrandProfileEntity(
    @PrimaryKey val id: String,
    val storeName: String,
    val logoPath: String? = null,
    val brandHexColorsJson: String = "[\"#101114\",\"#C9A66B\"]",
    val defaultBackgroundPreset: String,
    val defaultModelProfileJson: String,
    val defaultLensPreset: String,
    val defaultLightingPreset: String,
    val defaultPosePreset: String,
    val defaultFramingPreset: String,
    val defaultExportFormat: String = "PNG",
    val consistencyLockEnabled: Boolean = true,
    val updatedAtMillis: Long
)

@Entity(tableName = "api_usage_logs")
data class ApiUsageLogEntity(
    @PrimaryKey val id: String,
    val projectId: String?,
    val providerId: String,
    val modelId: String,
    val operationType: String,             // ANALYZE, AUTO_STYLE, GENERATE_IMAGE, QUALITY_AUDIT, TEST_CONNECTION
    val resolutionTier: String,            // 0.5K, 1K, 2K, 4K, TEXT
    val estimatedCostUsd: Double,
    val success: Boolean,
    val errorSummary: String? = null,
    val timestampMillis: Long
)

@Entity(tableName = "batch_jobs")
data class BatchJobEntity(
    @PrimaryKey val id: String,
    val title: String,
    val totalItems: Int,
    val completedItems: Int,
    val failedItems: Int,
    val status: String,                    // QUEUED, RUNNING, PAUSED, COMPLETED, CANCELLED, BUDGET_STOPPED
    val budgetLimitUsd: Double,
    val spentUsd: Double,
    val itemProjectIdsJson: String,
    val shotTypesJson: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)
