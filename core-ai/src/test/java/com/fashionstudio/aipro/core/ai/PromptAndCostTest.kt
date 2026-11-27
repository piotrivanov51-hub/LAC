package com.fashionstudio.aipro.core.ai

import com.fashionstudio.aipro.core.ai.model.GenerationConfig
import com.fashionstudio.aipro.core.ai.model.ReferenceAngle
import com.fashionstudio.aipro.core.imaging.FidelityMode
import com.fashionstudio.aipro.core.security.SecuritySanitizer
import org.junit.Assert.*
import org.junit.Test

class PromptAndCostTest {

    private val promptBuilder = PromptBuilder()
    private val costEstimator = CostEstimator()

    @Test
    fun modeAPrompt_enforcesProductDnaLockDirectives() {
        val prompt = promptBuilder.buildGenerationPrompt(
            GenerationConfig(fidelityMode = FidelityMode.ORIGINAL_PIXEL_PROTECTION)
        )
        assertTrue(prompt.contains("PRODUCT DNA LOCK"))
        assertTrue(prompt.contains("DO NOT alter, redraw, recolor"))
    }

    @Test
    fun angleGuardrail_warnsWhenBackPoseRequestedWithoutBackReference() {
        val warning = promptBuilder.checkAngleReferenceGuardrail("Back View", listOf(ReferenceAngle.FRONT))
        assertNotNull(warning)
        val ok = promptBuilder.checkAngleReferenceGuardrail("Back View", listOf(ReferenceAngle.FRONT, ReferenceAngle.BACK))
        assertNull(ok)
    }

    @Test
    fun costEstimator_matchesNanoBanana21PricingAndBatchDiscount() {
        assertEquals(0.067, costEstimator.estimateImageGenerationUsd("gemini-nano-banana-2.1", "1K", 1, false), 0.0001)
        assertEquals(0.101, costEstimator.estimateImageGenerationUsd("gemini-nano-banana-2.1", "2K", 1, false), 0.0001)
        assertEquals(0.151, costEstimator.estimateImageGenerationUsd("gemini-nano-banana-2.1", "4K", 1, false), 0.0001)
        assertEquals(0.0505, costEstimator.estimateImageGenerationUsd("gemini-nano-banana-2.1", "2K", 1, true), 0.0001)
    }

    @Test
    fun securitySanitizer_redactsGeminiApiKeys() {
        val raw = "Request failed for key=AIzaSyDummySecretKey1234567890abcdef and Bearer tok_secret_99"
        val clean = SecuritySanitizer.redact(raw)
        assertFalse(clean.contains("AIzaSyDummy"))
        assertFalse(clean.contains("tok_secret_99"))
    }
}
