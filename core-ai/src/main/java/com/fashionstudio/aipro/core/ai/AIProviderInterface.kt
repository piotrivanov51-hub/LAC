package com.fashionstudio.aipro.core.ai

import com.fashionstudio.aipro.core.ai.model.AiQualityAuditResult
import com.fashionstudio.aipro.core.ai.model.AutoStyleConcept
import com.fashionstudio.aipro.core.ai.model.GarmentAnalysisResult
import com.fashionstudio.aipro.core.ai.model.GenerationConfig

enum class ProviderImplementationStatus {
    IMPLEMENTED,
    PLANNED
}

data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    val status: ProviderImplementationStatus,
    val capabilities: String,
    val note: String
)

data class ConnectionTestResult(
    val connected: Boolean,
    val availableModels: List<String>,
    val message: String
)

data class GeneratedImagePayload(
    val imageBytes: ByteArray,
    val mimeType: String,
    val modelUsed: String,
    val textNotes: String? = null
)

/**
 * Provider-agnostic interface so switching or adding AI backends (Gemini, Vertex AI VTON,
 * OpenAI Image API, Black Forest Labs FLUX, fal.ai, Replicate) requires zero UI/domain rewrites.
 */
interface AIProviderInterface {
    val providerId: String
    val displayName: String

    suspend fun testConnection(apiKey: String): ConnectionTestResult

    suspend fun analyzeGarment(
        apiKey: String,
        modelId: String,
        imagePngBytes: ByteArray
    ): Result<GarmentAnalysisResult>

    suspend fun suggestAutoStyles(
        apiKey: String,
        modelId: String,
        analysis: GarmentAnalysisResult,
        imagePngBytes: ByteArray
    ): Result<List<AutoStyleConcept>>

    suspend fun generateStudioImage(
        apiKey: String,
        modelId: String,
        config: GenerationConfig,
        referenceImagesPng: List<ByteArray>
    ): Result<GeneratedImagePayload>

    suspend fun auditGeneratedQuality(
        apiKey: String,
        modelId: String,
        originalPngBytes: ByteArray,
        generatedPngBytes: ByteArray
    ): Result<AiQualityAuditResult>
}

object ProviderCatalog {
    val allProviders: List<ProviderDescriptor> = listOf(
        ProviderDescriptor(
            id = "gemini",
            displayName = "Google Gemini API (BYOK)",
            status = ProviderImplementationStatus.IMPLEMENTED,
            capabilities = "Vision Analysis, Auto Style, Native Image Generation & Editing (Nano Banana 2.1), Quality Audit",
            note = "Active primary provider using your own Google AI Studio API key."
        ),
        ProviderDescriptor(
            id = "vertex_vton",
            displayName = "Google Cloud Vertex AI Virtual Try-On",
            status = ProviderImplementationStatus.PLANNED,
            capabilities = "Specialized Garment-Preserving Virtual Try-On (recontext_image)",
            note = "Planned (requires Google Cloud project + OAuth2/API Gateway)."
        ),
        ProviderDescriptor(
            id = "openai_image",
            displayName = "OpenAI Image API",
            status = ProviderImplementationStatus.PLANNED,
            capabilities = "Image generation & inpainting",
            note = "Planned for Phase 3 Business Edition."
        ),
        ProviderDescriptor(
            id = "bfl_flux",
            displayName = "Black Forest Labs FLUX",
            status = ProviderImplementationStatus.PLANNED,
            capabilities = "Photorealistic studio generation & fill",
            note = "Planned for Phase 3 Business Edition."
        ),
        ProviderDescriptor(
            id = "fal_ai",
            displayName = "fal.ai",
            status = ProviderImplementationStatus.PLANNED,
            capabilities = "Managed VTON & segmentation endpoints",
            note = "Planned for Phase 3 Business Edition."
        ),
        ProviderDescriptor(
            id = "replicate",
            displayName = "Replicate",
            status = ProviderImplementationStatus.PLANNED,
            capabilities = "Hosted open-source VTON & upscaling",
            note = "Planned for Phase 3 Business Edition."
        )
    )
}
