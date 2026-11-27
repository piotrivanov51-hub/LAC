package com.fashionstudio.aipro.core.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transparent pre-flight cost estimation based on official Google Gemini API pricing (Oct/Nov 2026).
 * Clearly separates free local app tools (segmentation, masking, compositing, verification, local export)
 * from paid external AI API requests.
 */
@Singleton
class CostEstimator @Inject constructor() {

    fun estimateImageGenerationUsd(modelId: String, resolutionTier: String, variants: Int = 1, isBatch: Boolean = false): Double {
        val unitPrice = when {
            modelId.contains("nano-banana-2.1") || modelId.contains("nano-banana-2") -> when (resolutionTier.uppercase()) {
                "0.5K" -> 0.045
                "1K" -> 0.067
                "2K" -> 0.101
                "4K" -> 0.151
                else -> 0.101
            }
            modelId.contains("2.5-flash-image") -> 0.039
            else -> 0.101
        }
        val batchMultiplier = if (isBatch) 0.5 else 1.0
        return unitPrice * variants.coerceAtLeast(1) * batchMultiplier
    }

    fun estimateVisionAnalysisUsd(modelId: String): Double {
        return when {
            modelId.contains("flash-lite") -> 0.0005
            modelId.contains("3.8-flash") || modelId.contains("2.5-flash") -> 0.002
            else -> 0.003
        }
    }
}
