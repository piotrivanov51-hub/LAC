package com.fashionstudio.aipro.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.fashionstudio.aipro.core.ai.*
import com.fashionstudio.aipro.core.ai.model.*
import com.fashionstudio.aipro.core.database.UserPreferencesRepository
import com.fashionstudio.aipro.core.database.dao.ApiUsageDao
import com.fashionstudio.aipro.core.database.dao.ProjectDao
import com.fashionstudio.aipro.core.database.dao.ProjectVersionDao
import com.fashionstudio.aipro.core.database.dao.ReferenceImageDao
import com.fashionstudio.aipro.core.database.entity.ApiUsageLogEntity
import com.fashionstudio.aipro.core.database.entity.ProjectEntity
import com.fashionstudio.aipro.core.database.entity.ProjectVersionEntity
import com.fashionstudio.aipro.core.database.entity.ReferenceImageEntity
import com.fashionstudio.aipro.core.imaging.*
import com.fashionstudio.aipro.core.security.ApiKeyVault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PipelineExecutionOutput(
    val version: ProjectVersionEntity,
    val verificationReport: ProductDnaVerificationReport,
    val aiQualityAudit: AiQualityAuditResult?,
    val outputBitmap: Bitmap,
    val diffHeatmapBitmap: Bitmap
)

/**
 * End-to-end Product DNA Lock orchestration pipeline.
 * Enforces:
 * 1. Level 1 Immutable Original SHA-256 verification before every run.
 * 2. Level 2-4 Garment Segmentation & Protected / Critical Detail Masks.
 * 3. Pre-flight Monthly Budget Guard (never silently overspends user API credits).
 * 4. Real Gemini API image generation using user's BYOK key.
 * 5. Level 5 Deterministic Mode A LayerCompositor (copies exact original interior pixels) OR Mode B VTON.
 * 6. Independent deterministic ProductDnaVerificationEngine + optional Gemini Vision QC.
 * 7. Non-destructive version persistence in Room.
 */
@Singleton
class StudioPipelineUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val originalStore: ImmutableOriginalStore,
    private val segmenter: DeterministicBorderGarmentSegmenter,
    private val compositor: LayerCompositor,
    private val verificationEngine: ProductDnaVerificationEngine,
    private val aiProvider: AIProviderInterface,
    private val promptBuilder: PromptBuilder,
    private val costEstimator: CostEstimator,
    private val apiKeyVault: ApiKeyVault,
    private val preferences: UserPreferencesRepository,
    private val projectDao: ProjectDao,
    private val referenceImageDao: ReferenceImageDao,
    private val versionDao: ProjectVersionDao,
    private val apiUsageDao: ApiUsageDao
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val outputsDir: File
        get() = File(context.filesDir, "studio_outputs").apply { mkdirs() }

    suspend fun createProjectFromUri(
        title: String,
        sku: String,
        imageUri: Uri,
        studioMode: StudioMode,
        fidelityMode: FidelityMode
    ): Result<ProjectEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val projectId = UUID.randomUUID().toString()
            val record = originalStore.ingestFromUri(projectId, "front", imageUri)

            // Run local Level 2 segmentation immediately (free, offline)
            val origBmp = BitmapFactory.decodeFile(record.filePath)
                ?: throw IllegalStateException("Failed to decode immutable original")
            val stack = segmenter.segmentGarment(PixelBuffer.fromBitmap(origBmp))

            val maskFile = File(outputsDir, "${projectId}_protected_mask.png")
            val critFile = File(outputsDir, "${projectId}_critical_mask.png")
            savePng(stack.protectedGarmentMask.toBitmapMask(), maskFile)
            savePng(stack.criticalDetailMask.toBitmapMask(), critFile)

            // Optional real Gemini Vision garment analysis if API key is already set
            val settings = preferences.settingsFlow.first()
            val apiKey = apiKeyVault.getApiKey(settings.activeProviderId)
            var analysis: GarmentAnalysisResult? = null
            if (!apiKey.isNullOrBlank()) {
                val pngBytes = File(record.filePath).readBytes()
                val res = aiProvider.analyzeGarment(apiKey, settings.analysisModelId, pngBytes)
                analysis = res.getOrNull()
                apiUsageDao.insert(
                    ApiUsageLogEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        providerId = settings.activeProviderId,
                        modelId = settings.analysisModelId,
                        operationType = "ANALYZE",
                        resolutionTier = "TEXT",
                        estimatedCostUsd = costEstimator.estimateVisionAnalysisUsd(settings.analysisModelId),
                        success = res.isSuccess,
                        errorSummary = res.exceptionOrNull()?.message,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
            }

            val category = analysis?.category ?: GarmentCategory.JEANS
            val defaultConfig = GenerationConfig(
                studioMode = studioMode,
                fidelityMode = fidelityMode,
                garmentCategory = category,
                garmentColor = analysis?.primaryColor ?: "Original Color",
                protectedFeatures = analysis?.protectedFeaturesSummary
                    ?: "Preserve exact original fabric color, weave, seams, pockets, buttons, zippers, and logos.",
                modelConfig = ModelCastingConfig(enabled = fidelityMode == FidelityMode.AUTHENTIC_VIRTUAL_TRY_ON),
                resolutionTier = settings.defaultResolutionTier
            )

            val now = System.currentTimeMillis()
            val entity = ProjectEntity(
                id = projectId,
                title = title.ifBlank { "${category.displayName} Shoot" },
                sku = sku.ifBlank { "SKU-${projectId.take(6).uppercase()}" },
                garmentCategory = category.name,
                primaryColor = analysis?.primaryColor ?: "Original",
                secondaryColorsJson = json.encodeToString(analysis?.secondaryColors ?: emptyList<String>()),
                estimatedMaterial = analysis?.estimatedMaterial ?: "Unanalyzed (local mode)",
                fitStyle = analysis?.fitStyle ?: "Original Fit",
                hasPrints = analysis?.hasPrints ?: false,
                hasLogos = analysis?.hasLogos ?: false,
                studioMode = studioMode.name,
                fidelityMode = fidelityMode.name,
                dnaLockStatus = DnaLockStatus.PROTECTED.name,
                immutableOriginalPath = record.filePath,
                originalSha256 = record.sha256,
                originalWidth = record.width,
                originalHeight = record.height,
                protectedMaskPath = maskFile.absolutePath,
                criticalDetailMaskPath = critFile.absolutePath,
                generationConfigJson = json.encodeToString(defaultConfig),
                createdAtMillis = now,
                updatedAtMillis = now
            )
            projectDao.upsert(entity)
            referenceImageDao.insert(
                ReferenceImageEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    angleType = ReferenceAngle.FRONT.name,
                    immutableFilePath = record.filePath,
                    sha256 = record.sha256,
                    width = record.width,
                    height = record.height,
                    createdAtMillis = now
                )
            )
            entity
        }
    }

    suspend fun addReferenceAngle(
        projectId: String,
        angle: ReferenceAngle,
        uri: Uri
    ): Result<ReferenceImageEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val record = originalStore.ingestFromUri(projectId, angle.name, uri)
            val ref = ReferenceImageEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                angleType = angle.name,
                immutableFilePath = record.filePath,
                sha256 = record.sha256,
                width = record.width,
                height = record.height,
                createdAtMillis = System.currentTimeMillis()
            )
            referenceImageDao.insert(ref)
            ref
        }
    }

    suspend fun runStudioGeneration(
        projectId: String,
        config: GenerationConfig,
        runAiQualityAudit: Boolean = true
    ): Result<PipelineExecutionOutput> = withContext(Dispatchers.IO) {
        runCatching {
            val project = projectDao.getById(projectId)
                ?: throw IllegalArgumentException("Project not found: $projectId")

            // Level 1 Integrity check
            val intact = originalStore.verifyIntegrity(project.immutableOriginalPath, project.originalSha256)
            check(intact) { "Product DNA Alert: Immutable original file SHA-256 verification failed!" }

            val settings = preferences.settingsFlow.first()
            val apiKey = apiKeyVault.getApiKey(settings.activeProviderId)
                ?: throw IllegalStateException("No Gemini API key configured. Add your BYOK key in Settings -> API Settings.")

            // Monthly budget check
            val monthStart = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            val spentThisMonth = apiUsageDao.getSpentSince(monthStart)
            val estCost = costEstimator.estimateImageGenerationUsd(settings.generationModelId, config.resolutionTier, 1)
            if (settings.monthlyBudgetLimitUsd > 0 && spentThisMonth + estCost > settings.monthlyBudgetLimitUsd) {
                throw IllegalStateException(
                    "Monthly API budget cap ($${"%.2f".format(settings.monthlyBudgetLimitUsd)}) would be exceeded. Increase limit in Settings to continue."
                )
            }

            // Collect all multi-reference PNGs
            val refs = referenceImageDao.getForProject(projectId)
            val refBytes = refs.mapNotNull { r -> File(r.immutableFilePath).takeIf { it.exists() }?.readBytes() }
                .ifEmpty { listOf(File(project.immutableOriginalPath).readBytes()) }

            val availableAngles = refs.mapNotNull { runCatching { ReferenceAngle.valueOf(it.angleType) }.getOrNull() }
                .ifEmpty { listOf(ReferenceAngle.FRONT) }
            val angleWarning = promptBuilder.checkAngleReferenceGuardrail(config.posePreset, availableAngles)

            // Execute real Gemini API call (with fallback model on error)
            var genResult = aiProvider.generateStudioImage(
                apiKey = apiKey,
                modelId = settings.generationModelId,
                config = config.copy(availableAngles = availableAngles),
                referenceImagesPng = refBytes
            )
            var modelUsed = settings.generationModelId
            if (genResult.isFailure && settings.fallbackGenerationModelId.isNotBlank() &&
                settings.fallbackGenerationModelId != settings.generationModelId
            ) {
                modelUsed = settings.fallbackGenerationModelId
                genResult = aiProvider.generateStudioImage(
                    apiKey = apiKey,
                    modelId = modelUsed,
                    config = config.copy(availableAngles = availableAngles),
                    referenceImagesPng = refBytes
                )
            }

            apiUsageDao.insert(
                ApiUsageLogEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    providerId = settings.activeProviderId,
                    modelId = modelUsed,
                    operationType = "GENERATE_IMAGE",
                    resolutionTier = config.resolutionTier,
                    estimatedCostUsd = estCost,
                    success = genResult.isSuccess,
                    errorSummary = genResult.exceptionOrNull()?.message,
                    timestampMillis = System.currentTimeMillis()
                )
            )

            val generatedPayload = genResult.getOrThrow()
            val aiBitmap = BitmapFactory.decodeByteArray(generatedPayload.imageBytes, 0, generatedPayload.imageBytes.size)
                ?: throw IllegalStateException("Failed to decode generated image bytes from Gemini API.")

            val origBitmap = BitmapFactory.decodeFile(project.immutableOriginalPath)
                ?: throw IllegalStateException("Failed to load immutable original bitmap.")
            val origBuf = PixelBuffer.fromBitmap(origBitmap)
            val stack = segmenter.segmentGarment(origBuf)

            // Level 5: Deterministic Layer Composition in Mode A vs Controlled Transfer in Mode B
            val finalBuf: PixelBuffer = if (config.fidelityMode == FidelityMode.ORIGINAL_PIXEL_PROTECTION) {
                compositor.compositeOriginalPixelProtection(
                    originalGarment = origBuf,
                    protectedMask = stack.protectedGarmentMask,
                    generatedBackground = PixelBuffer.fromBitmap(aiBitmap),
                    shadowConfig = ShadowConfig(enabled = config.backgroundConfig.contactShadowEnabled)
                )
            } else {
                val candidateBuf = PixelBuffer.fromBitmap(aiBitmap)
                if (candidateBuf.width == origBuf.width && candidateBuf.height == origBuf.height) {
                    candidateBuf
                } else {
                    compositor.resizeBilinear(candidateBuf, origBuf.width, origBuf.height)
                }
            }

            // Independent Deterministic Product DNA Verification
            val verification = verificationEngine.verify(
                original = origBuf,
                candidate = finalBuf,
                protectedMask = stack.protectedGarmentMask,
                criticalDetailMask = stack.criticalDetailMask,
                fidelityMode = config.fidelityMode,
                missingRequiredAngleReference = angleWarning != null
            )

            val finalBitmap = finalBuf.toBitmap()
            val heatmapBitmap = verification.diffHeatmap.toBitmap()

            // Optional Gemini Vision semantic quality audit (clearly marked approximate)
            var aiAudit: AiQualityAuditResult? = null
            if (runAiQualityAudit) {
                val origPng = File(project.immutableOriginalPath).readBytes()
                val outPng = bitmapToPngBytes(finalBitmap)
                val auditRes = aiProvider.auditGeneratedQuality(apiKey, settings.qualityAuditModelId, origPng, outPng)
                aiAudit = auditRes.getOrNull()
                apiUsageDao.insert(
                    ApiUsageLogEntity(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        providerId = settings.activeProviderId,
                        modelId = settings.qualityAuditModelId,
                        operationType = "QUALITY_AUDIT",
                        resolutionTier = "TEXT",
                        estimatedCostUsd = costEstimator.estimateVisionAnalysisUsd(settings.qualityAuditModelId),
                        success = auditRes.isSuccess,
                        errorSummary = auditRes.exceptionOrNull()?.message,
                        timestampMillis = System.currentTimeMillis()
                    )
                )
            }

            val nextVersion = versionDao.getMaxVersionNumber(projectId) + 1
            val outFile = File(outputsDir, "${projectId}_v${nextVersion}.png")
            val heatFile = File(outputsDir, "${projectId}_v${nextVersion}_heatmap.png")
            savePng(finalBitmap, outFile)
            savePng(heatmapBitmap, heatFile)

            val versionEntity = ProjectVersionEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                versionNumber = nextVersion,
                outputImagePath = outFile.absolutePath,
                diffHeatmapPath = heatFile.absolutePath,
                fidelityModeUsed = config.fidelityMode.name,
                dnaStatus = verification.report.status.name,
                ssimScore = verification.report.ssimScore,
                deltaE00Mean = verification.report.deltaE00Mean,
                deltaE00Max = verification.report.deltaE00P95,
                exactInteriorMatchRatio = verification.report.exactInteriorMatchRatio,
                edgePreservationScore = verification.report.edgePreservationScore,
                criticalDetailPass = verification.report.criticalDetailPass,
                aiQualityReportJson = aiAudit?.let { json.encodeToString(it) },
                generationConfigJson = json.encodeToString(config),
                providerId = settings.activeProviderId,
                modelUsed = modelUsed,
                estimatedCostUsd = estCost,
                createdAtMillis = System.currentTimeMillis()
            )
            versionDao.insert(versionEntity)
            projectDao.update(
                project.copy(
                    dnaLockStatus = verification.report.status.name,
                    fidelityMode = config.fidelityMode.name,
                    studioMode = config.studioMode.name,
                    latestOutputPath = outFile.absolutePath,
                    generationConfigJson = json.encodeToString(config),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )

            PipelineExecutionOutput(
                version = versionEntity,
                verificationReport = verification.report,
                aiQualityAudit = aiAudit,
                outputBitmap = finalBitmap,
                diffHeatmapBitmap = heatmapBitmap
            )
        }
    }

    private fun savePng(bmp: Bitmap, target: File) {
        FileOutputStream(target).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
    }

    private fun bitmapToPngBytes(bmp: Bitmap): ByteArray {
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }
}
