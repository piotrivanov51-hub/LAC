package com.fashionstudio.aipro.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionstudio.aipro.core.ai.*
import com.fashionstudio.aipro.core.ai.model.*
import com.fashionstudio.aipro.core.database.UserPreferencesRepository
import com.fashionstudio.aipro.core.database.dao.*
import com.fashionstudio.aipro.core.database.entity.*
import com.fashionstudio.aipro.core.imaging.*
import com.fashionstudio.aipro.core.security.ApiKeyVault
import com.fashionstudio.aipro.core.ui.catalog.ShoeCatalogItem
import com.fashionstudio.aipro.core.ui.catalog.ShoeCatalogRepository
import com.fashionstudio.aipro.domain.StudioPipelineUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class StudioUiState(
    val activeProject: ProjectEntity? = null,
    val config: GenerationConfig = GenerationConfig(),
    val undoStack: List<GenerationConfig> = emptyList(),
    val redoStack: List<GenerationConfig> = emptyList(),
    val originalBitmap: Bitmap? = null,
    val latestOutputBitmap: Bitmap? = null,
    val latestHeatmapBitmap: Bitmap? = null,
    val latestReport: ProductDnaVerificationReport? = null,
    val latestAiAudit: AiQualityAuditResult? = null,
    val autoStyleConcepts: List<AutoStyleConcept> = emptyList(),
    val shoeCatalog: List<ShoeCatalogItem> = emptyList(),
    val estimatedGenerationCostUsd: Double = 0.101,
    val angleGuardrailWarning: String? = null,
    val isBusy: Boolean = false,
    val busyMessage: String? = null,
    val errorBanner: String? = null,
    val exportFeedback: String? = null
)

@HiltViewModel
class StudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pipeline: StudioPipelineUseCase,
    private val projectDao: ProjectDao,
    private val versionDao: ProjectVersionDao,
    private val referenceImageDao: ReferenceImageDao,
    private val brandProfileDao: BrandProfileDao,
    private val batchJobDao: BatchJobDao,
    private val aiProvider: AIProviderInterface,
    private val promptBuilder: PromptBuilder,
    private val costEstimator: CostEstimator,
    private val exporter: LosslessExporter,
    private val apiKeyVault: ApiKeyVault,
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _uiState = MutableStateFlow(
        StudioUiState(shoeCatalog = ShoeCatalogRepository.loadFromAssets(context))
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    val allProjects: StateFlow<List<ProjectEntity>> = projectDao.observeAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batchJobs: StateFlow<List<BatchJobEntity>> = batchJobDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val brandProfiles: StateFlow<List<BrandProfileEntity>> = brandProfileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createProject(
        title: String,
        sku: String,
        imageUri: Uri,
        studioMode: StudioMode,
        fidelityMode: FidelityMode,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "Locking Product DNA & segmenting garment...", errorBanner = null) }
            val result = pipeline.createProjectFromUri(title, sku, imageUri, studioMode, fidelityMode)
            result.onSuccess { project ->
                loadProject(project.id)
                _uiState.update { it.copy(isBusy = false, busyMessage = null) }
                onCreated(project.id)
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false, busyMessage = null, errorBanner = err.message) }
            }
        }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val project = projectDao.getById(projectId) ?: return@launch
            val cfg = runCatching {
                json.decodeFromString(GenerationConfig.serializer(), project.generationConfigJson)
            }.getOrDefault(GenerationConfig())
            val origBmp = BitmapFactory.decodeFile(project.immutableOriginalPath)
            val latestVer = versionDao.getLatestVersion(projectId)
            val outBmp = latestVer?.outputImagePath?.let { BitmapFactory.decodeFile(it) }
            val heatBmp = latestVer?.diffHeatmapPath?.let { BitmapFactory.decodeFile(it) }
            val settings = preferences.settingsFlow.first()
            val est = costEstimator.estimateImageGenerationUsd(settings.generationModelId, cfg.resolutionTier, cfg.variantCount)

            _uiState.update {
                it.copy(
                    activeProject = project,
                    config = cfg,
                    originalBitmap = origBmp,
                    latestOutputBitmap = outBmp,
                    latestHeatmapBitmap = heatBmp,
                    estimatedGenerationCostUsd = est,
                    angleGuardrailWarning = promptBuilder.checkAngleReferenceGuardrail(cfg.posePreset, cfg.availableAngles),
                    errorBanner = null
                )
            }
        }
    }

    fun updateConfig(transform: (GenerationConfig) -> GenerationConfig) {
        viewModelScope.launch {
            val current = _uiState.value.config
            val updated = transform(current)
            val settings = preferences.settingsFlow.first()
            val est = costEstimator.estimateImageGenerationUsd(settings.generationModelId, updated.resolutionTier, updated.variantCount)
            val warning = promptBuilder.checkAngleReferenceGuardrail(updated.posePreset, updated.availableAngles)
            _uiState.update {
                it.copy(
                    undoStack = (it.undoStack + current).takeLast(25),
                    redoStack = emptyList(),
                    config = updated,
                    estimatedGenerationCostUsd = est,
                    angleGuardrailWarning = warning
                )
            }
        }
    }

    fun undo() {
        val stack = _uiState.value.undoStack
        if (stack.isEmpty()) return
        val prev = stack.last()
        _uiState.update {
            it.copy(
                config = prev,
                undoStack = stack.dropLast(1),
                redoStack = it.redoStack + it.config
            )
        }
    }

    fun redo() {
        val stack = _uiState.value.redoStack
        if (stack.isEmpty()) return
        val next = stack.last()
        _uiState.update {
            it.copy(
                config = next,
                redoStack = stack.dropLast(1),
                undoStack = it.undoStack + it.config
            )
        }
    }

    fun buildPromptPreview(): String = promptBuilder.buildGenerationPrompt(_uiState.value.config)

    fun runAutoStyle() {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "AI Stylist analyzing garment...", errorBanner = null) }
            val settings = preferences.settingsFlow.first()
            val apiKey = apiKeyVault.getApiKey(settings.activeProviderId)
            if (apiKey.isNullOrBlank()) {
                _uiState.update { it.copy(isBusy = false, busyMessage = null, errorBanner = "Configure your Gemini API Key in Settings first.") }
                return@launch
            }
            val origBytes = File(project.immutableOriginalPath).readBytes()
            val cat = runCatching { GarmentCategory.valueOf(project.garmentCategory) }.getOrDefault(GarmentCategory.JEANS)
            val analysis = GarmentAnalysisResult(
                category = cat,
                primaryColor = project.primaryColor,
                estimatedMaterial = project.estimatedMaterial,
                fitStyle = project.fitStyle,
                visualStyle = "Commercial",
                cutDetails = "",
                hasPrints = project.hasPrints,
                hasLogos = project.hasLogos,
                imageQualityNote = "OK",
                protectedFeaturesSummary = _uiState.value.config.protectedFeatures
            )
            val res = aiProvider.suggestAutoStyles(apiKey, settings.analysisModelId, analysis, origBytes)
            res.onSuccess { concepts ->
                _uiState.update { it.copy(isBusy = false, busyMessage = null, autoStyleConcepts = concepts) }
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false, busyMessage = null, errorBanner = err.message) }
            }
        }
    }

    fun generate(onComplete: () -> Unit) {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, busyMessage = "Generating studio environment & verifying Product DNA...", errorBanner = null) }
            val res = pipeline.runStudioGeneration(project.id, _uiState.value.config)
            res.onSuccess { out ->
                val refreshed = projectDao.getById(project.id)
                _uiState.update {
                    it.copy(
                        activeProject = refreshed,
                        latestOutputBitmap = out.outputBitmap,
                        latestHeatmapBitmap = out.diffHeatmapBitmap,
                        latestReport = out.verificationReport,
                        latestAiAudit = out.aiQualityAudit,
                        isBusy = false,
                        busyMessage = null
                    )
                }
                onComplete()
            }.onFailure { err ->
                _uiState.update { it.copy(isBusy = false, busyMessage = null, errorBanner = err.message) }
            }
        }
    }

    fun exportLatest(preset: MarketplacePreset, format: ExportFormat) {
        val project = _uiState.value.activeProject ?: return
        val bmp = _uiState.value.latestOutputBitmap ?: return
        val report = _uiState.value.latestReport
        if (report != null && !report.commercialExportAllowed) {
            _uiState.update {
                it.copy(errorBanner = "Commercial export blocked: Product DNA Lock detected fidelity violations. Review diff map or switch to Mode A.")
            }
            return
        }
        viewModelScope.launch {
            val verNum = versionDao.getMaxVersionNumber(project.id).coerceAtLeast(1)
            val result = exporter.exportToGallery(bmp, project.sku, preset, verNum, format)
            val msg = buildString {
                append("Exported ${result.fileName} (${result.nativeWidth}x${result.nativeHeight})")
                result.lossyCompressionWarning?.let { append("\nWarning: $it") }
            }
            _uiState.update { it.copy(exportFeedback = msg) }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch { projectDao.deleteById(projectId) }
    }

    fun saveBrandProfile(storeName: String, backgroundPreset: String, lensPreset: String, lightingPreset: String) {
        viewModelScope.launch {
            brandProfileDao.upsert(
                BrandProfileEntity(
                    id = UUID.randomUUID().toString(),
                    storeName = storeName.ifBlank { "My Fashion Store" },
                    defaultBackgroundPreset = backgroundPreset,
                    defaultModelProfileJson = "{}",
                    defaultLensPreset = lensPreset,
                    defaultLightingPreset = lightingPreset,
                    defaultPosePreset = "Catalog Neutral Front",
                    defaultFramingPreset = "Full Body",
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun clearError() { _uiState.update { it.copy(errorBanner = null, exportFeedback = null) } }
}
