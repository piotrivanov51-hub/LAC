package com.fashionstudio.aipro.core.ai

import android.util.Base64
import com.fashionstudio.aipro.core.ai.model.*
import com.fashionstudio.aipro.core.security.SecuritySanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Google Gemini API (v1beta) REST integration using BYOK credentials passed strictly via
 * the `x-goog-api-key` HTTP header (never in URL query parameters) over HTTPS.
 */
@Singleton
class GeminiApiProvider @Inject constructor(
    private val promptBuilder: PromptBuilder
) : AIProviderInterface {

    override val providerId: String = "gemini"
    override val displayName: String = "Google Gemini API"

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun testConnection(apiKey: String): ConnectionTestResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext ConnectionTestResult(false, emptyList(), "API key is empty.")
        }
        val req = Request.Builder()
            .url("$BASE_URL/models")
            .header("x-goog-api-key", apiKey.trim())
            .get()
            .build()
        try {
            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext ConnectionTestResult(
                        connected = false,
                        availableModels = emptyList(),
                        message = "HTTP ${resp.code}: ${SecuritySanitizer.redact(bodyStr.take(240))}"
                    )
                }
                val root = json.parseToJsonElement(bodyStr).jsonObject
                val models = root["models"]?.jsonArray?.mapNotNull {
                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/")
                }.orEmpty()
                ConnectionTestResult(
                    connected = true,
                    availableModels = models,
                    message = "Connected successfully. ${models.size} models available."
                )
            }
        } catch (e: Exception) {
            ConnectionTestResult(
                connected = false,
                availableModels = emptyList(),
                message = SecuritySanitizer.redact(e.message ?: "Network error")
            )
        }
    }

    override suspend fun analyzeGarment(
        apiKey: String,
        modelId: String,
        imagePngBytes: ByteArray
    ): Result<GarmentAnalysisResult> = withContext(Dispatchers.IO) {
        val instruction = """
            Analyze this garment photograph for commercial fashion photography with strict Product DNA preservation.
            Return ONLY valid JSON with keys:
            category (one of: JEANS, TROUSERS, CHINOS, CARGO, SWEATPANTS, SHORTS, TSHIRT, TANK_TOP, POLO, SHIRT, LONGSLEEVE, SWEATER, CARDIGAN, HOODIE, SWEATSHIRT, BLAZER, JACKET, COAT, DRESS, SKIRT, FOOTWEAR, ACCESSORY),
            primaryColor (string),
            secondaryColors (array of strings),
            estimatedMaterial (string),
            fitStyle (string),
            visualStyle (string),
            cutDetails (string),
            hasPrints (boolean),
            hasLogos (boolean),
            imageQualityNote (string),
            protectedFeaturesSummary (concise list of exact seams, pockets, wash, buttons, zippers, or logos that MUST NOT change).
        """.trimIndent()

        val payload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", instruction) }
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "image/png")
                                put("data", Base64.encodeToString(imagePngBytes, Base64.NO_WRAP))
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            }
        }

        executeGenerateContent(apiKey, modelId, payload).mapCatching { root ->
            val text = extractFirstText(root)
                ?: throw IllegalStateException("Gemini returned no analysis text.")
            json.decodeFromString(GarmentAnalysisResult.serializer(), cleanJsonFence(text))
        }
    }

    override suspend fun suggestAutoStyles(
        apiKey: String,
        modelId: String,
        analysis: GarmentAnalysisResult,
        imagePngBytes: ByteArray
    ): Result<List<AutoStyleConcept>> = withContext(Dispatchers.IO) {
        val instruction = """
            Act as a senior commercial fashion stylist. Based on this ${analysis.category.displayName} (${analysis.primaryColor}, ${analysis.estimatedMaterial}),
            propose 3 distinct commercial styling concepts from styles such as Old Money, Smart Casual, Quiet Luxury, Scandinavian Minimalism, Italian Classic, Streetwear, or Contemporary Fashion.
            Each concept must highlight the uploaded product without distracting from it.
            Return ONLY a JSON array of objects with keys:
            styleName, recommendedComplement, footwear, accessories (array of strings), colorPalette (array of hex or color names),
            backgroundPreset, lightingPreset, lensPreset, posePreset, framing, rationale.
        """.trimIndent()

        val payload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", instruction) }
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "image/png")
                                put("data", Base64.encodeToString(imagePngBytes, Base64.NO_WRAP))
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.4)
            }
        }

        executeGenerateContent(apiKey, modelId, payload).mapCatching { root ->
            val text = extractFirstText(root)
                ?: throw IllegalStateException("Gemini returned no styling concepts.")
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(AutoStyleConcept.serializer()),
                cleanJsonFence(text)
            )
        }
    }

    override suspend fun generateStudioImage(
        apiKey: String,
        modelId: String,
        config: GenerationConfig,
        referenceImagesPng: List<ByteArray>
    ): Result<GeneratedImagePayload> = withContext(Dispatchers.IO) {
        val prompt = promptBuilder.buildGenerationPrompt(config)

        val payload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                        referenceImagesPng.forEach { imgBytes ->
                            addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", "image/png")
                                    put("data", Base64.encodeToString(imgBytes, Base64.NO_WRAP))
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") {
                    add("TEXT")
                    add("IMAGE")
                }
                putJsonObject("imageConfig") {
                    put("aspectRatio", config.aspectRatio)
                    put("imageSize", config.resolutionTier)
                }
            }
        }

        executeGenerateContent(apiKey, modelId, payload).mapCatching { root ->
            val candidates = root["candidates"]?.jsonArray
                ?: throw IllegalStateException("No candidates returned by Gemini image API.")
            val parts = candidates.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?: throw IllegalStateException("Gemini response contained no content parts.")

            var imageBytes: ByteArray? = null
            var mimeType = "image/png"
            val notes = StringBuilder()

            for (partEl in parts) {
                val part = partEl.jsonObject
                part["text"]?.jsonPrimitive?.contentOrNull?.let { notes.appendLine(it) }
                val inline = part["inlineData"]?.jsonObject ?: part["inline_data"]?.jsonObject
                if (inline != null) {
                    val dataB64 = inline["data"]?.jsonPrimitive?.contentOrNull
                    mimeType = inline["mimeType"]?.jsonPrimitive?.contentOrNull
                        ?: inline["mime_type"]?.jsonPrimitive?.contentOrNull
                        ?: "image/png"
                    if (!dataB64.isNullOrBlank()) {
                        imageBytes = Base64.decode(dataB64, Base64.DEFAULT)
                    }
                }
            }

            val bytes = imageBytes
                ?: throw IllegalStateException("Model $modelId did not return an image payload. Notes: ${notes.toString().take(200)}")

            GeneratedImagePayload(
                imageBytes = bytes,
                mimeType = mimeType,
                modelUsed = modelId,
                textNotes = notes.toString().trim().ifEmpty { null }
            )
        }
    }

    override suspend fun auditGeneratedQuality(
        apiKey: String,
        modelId: String,
        originalPngBytes: ByteArray,
        generatedPngBytes: ByteArray
    ): Result<AiQualityAuditResult> = withContext(Dispatchers.IO) {
        val instruction = """
            Compare Image 1 (original real garment) and Image 2 (generated commercial studio result).
            Check for: color fidelity, garment cut/fit, logos/prints/stitching accuracy, model anatomy, hands/fingers, footwear realism, shadows, and artifacts.
            Return ONLY JSON with keys:
            isApproximateAiAssessment (true),
            productFidelityScore (0-100),
            photorealismScore (0-100),
            anatomyQualityScore (0-100),
            lightingQualityScore (0-100),
            brandConsistencyScore (0-100),
            commercialReadinessScore (0-100),
            detectedIssues (array of strings),
            recommendation (string).
        """.trimIndent()

        val payload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", instruction) }
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "image/png")
                                put("data", Base64.encodeToString(originalPngBytes, Base64.NO_WRAP))
                            }
                        }
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "image/png")
                                put("data", Base64.encodeToString(generatedPngBytes, Base64.NO_WRAP))
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
            }
        }

        executeGenerateContent(apiKey, modelId, payload).mapCatching { root ->
            val text = extractFirstText(root)
                ?: throw IllegalStateException("Quality audit returned no text.")
            json.decodeFromString(AiQualityAuditResult.serializer(), cleanJsonFence(text))
        }
    }

    private fun executeGenerateContent(
        apiKey: String,
        modelId: String,
        payload: JsonObject
    ): Result<JsonObject> {
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("Gemini API key is not configured. Open Settings -> API Settings to add your key."))
        }
        val cleanModel = modelId.removePrefix("models/").trim()
        val req = Request.Builder()
            .url("$BASE_URL/models/$cleanModel:generateContent")
            .header("x-goog-api-key", apiKey.trim())
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        return try {
            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val sanitized = SecuritySanitizer.redact(bodyStr.take(400))
                    return Result.failure(IllegalStateException("Gemini API HTTP ${resp.code}: $sanitized"))
                }
                Result.success(json.parseToJsonElement(bodyStr).jsonObject)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException(SecuritySanitizer.redact(e.message ?: "Network request failed"), e))
        }
    }

    private fun extractFirstText(root: JsonObject): String? {
        return root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?.firstNotNullOfOrNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
    }

    private fun cleanJsonFence(raw: String): String {
        return raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }
}
