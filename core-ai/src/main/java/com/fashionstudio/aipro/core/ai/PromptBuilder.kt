package com.fashionstudio.aipro.core.ai

import com.fashionstudio.aipro.core.ai.model.GenerationConfig
import com.fashionstudio.aipro.core.ai.model.ReferenceAngle
import com.fashionstudio.aipro.core.imaging.FidelityMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider-agnostic AI Prompt Engine.
 * Converts structured studio UI parameters into disciplined commercial photography instructions
 * while enforcing Product DNA Lock constraints and anti-artifact guardrails.
 */
@Singleton
class PromptBuilder @Inject constructor() {

    fun buildGenerationPrompt(config: GenerationConfig): String {
        config.advancedCustomPromptOverride?.takeIf { it.isNotBlank() }?.let { return it }

        val sb = StringBuilder()

        if (config.fidelityMode == FidelityMode.ORIGINAL_PIXEL_PROTECTION) {
            sb.appendLine("PRIMARY DIRECTIVE — PRODUCT DNA LOCK (MODE A: ORIGINAL PIXEL PROTECTION):")
            sb.appendLine("Generate ONLY a clean, photorealistic commercial photography backdrop, floor surface, and studio lighting environment around the reference garment.")
            sb.appendLine("DO NOT alter, redraw, recolor, smooth, or reinvent the reference garment (${config.garmentCategory.displayName}, ${config.garmentColor}).")
            sb.appendLine("Keep exact garment silhouette, position, proportions, seams, pockets, buttons, zippers, prints, embroidery, and fabric texture unchanged.")
        } else {
            sb.appendLine("PRIMARY DIRECTIVE — AUTHENTIC VIRTUAL TRY-ON (MODE B):")
            sb.appendLine("Dress the virtual fashion model in the EXACT reference garment (${config.garmentCategory.displayName}, ${config.garmentColor}) from the attached reference photo(s).")
            sb.appendLine("CRITICAL PRODUCT DNA CONSTRAINTS: Preserve original color, shade, denim/fabric weave, cut, proportions, pocket count/placement, stitching, buttons, zippers, logos, and prints.")
            sb.appendLine("NEVER invent missing details, extra pockets, or altered logos. Available reference angles: ${config.availableAngles.joinToString { it.displayName }}.")
        }

        sb.appendLine()
        sb.appendLine("STUDIO MODE: ${config.studioMode.displayName}")
        sb.appendLine("PROTECTED PRODUCT FEATURES: ${config.protectedFeatures}")

        if (config.modelConfig.enabled && config.fidelityMode == FidelityMode.AUTHENTIC_VIRTUAL_TRY_ON) {
            val m = config.modelConfig
            sb.appendLine()
            sb.appendLine("VIRTUAL MODEL & POSE:")
            sb.appendLine("- Subject: ${m.gender}, age ${m.ageRange}, height ~${m.heightCm}cm, ${m.bodyType}, ${m.skinTone} skin, ${m.hairStyle}, ${m.facialHair}, ${m.expression} expression.")
            sb.appendLine("- Archetype: ${m.modelArchetype}. Framing: ${m.framing}. Pose: ${config.posePreset}.")
            sb.appendLine("- Complementary Outfit (must NOT distract from the main product): ${config.outfitConfig.complementColor} ${config.outfitConfig.complementMaterial} ${config.outfitConfig.complementItemType} (${config.outfitConfig.complementStyle}).")
            sb.appendLine("- Footwear: ${config.outfitConfig.footwearColor} ${config.outfitConfig.footwearBrand} ${config.outfitConfig.footwearModel} style.")
            if (config.outfitConfig.accessories.isNotEmpty()) {
                sb.appendLine("- Subtle Accessories: ${config.outfitConfig.accessories.joinToString()}.")
            }
        }

        sb.appendLine()
        sb.appendLine("BACKGROUND & ENVIRONMENT:")
        sb.appendLine("- Category: ${config.backgroundConfig.category} — ${config.backgroundConfig.presetName}.")
        config.backgroundConfig.customHexColor?.let { sb.appendLine("- Exact background color target: $it.") }
        config.backgroundConfig.customPrompt?.takeIf { it.isNotBlank() }?.let { sb.appendLine("- Custom scene: $it.") }
        sb.appendLine("- Grounding: Natural perspective alignment and realistic soft contact shadows beneath the product/feet.")

        sb.appendLine()
        sb.appendLine("VIRTUAL CAMERA & OPTICS CHARACTER (VISUAL SIMULATION ONLY):")
        sb.appendLine("- Optical look inspired by ${config.cameraConfig.cameraProfile} with ${config.cameraConfig.lensPreset} (${config.cameraConfig.lensProfile} at ${config.cameraConfig.aperture}, ${config.cameraConfig.shootingAngle}).")
        sb.appendLine("- Focal behavior: ${focalGuidance(config.cameraConfig.lensProfile)}.")

        sb.appendLine()
        sb.appendLine("STUDIO LIGHTING:")
        sb.appendLine("- Setup: ${config.lightingConfig.presetName}, key light from ${config.lightingConfig.keyLightDirection}, color temperature ${config.lightingConfig.colorTempKelvin}K, soft natural transitions.")

        sb.appendLine()
        sb.appendLine("STRICT QUALITY & ANTI-ARTIFACT CONSTRAINTS:")
        sb.appendLine("- Avoid plastic skin, deformed hands, extra fingers, mismatched footwear, warped logos, unrealistic fabric folds, artificial harsh drop shadows, color cast on the product, or over-retouching.")
        sb.appendLine("- Aspect ratio: ${config.aspectRatio}.")

        return sb.toString().trim()
    }

    fun checkAngleReferenceGuardrail(requestedPose: String, availableAngles: List<ReferenceAngle>): String? {
        val lower = requestedPose.lowercase()
        if ((lower.contains("back") || lower.contains("сзади")) && !availableAngles.contains(ReferenceAngle.BACK)) {
            return "Back view requested, but no Back View reference photo was provided. Generating a back view would require guessing unseen garment details and is blocked from Verified Commercial status."
        }
        if ((lower.contains("side") || lower.contains("сбоку")) &&
            !availableAngles.contains(ReferenceAngle.LEFT_SIDE) &&
            !availableAngles.contains(ReferenceAngle.RIGHT_SIDE)
        ) {
            return "Side view requested without a Side View reference photo. Add a side photo or proceed knowing side details will be marked Virtual Reconstruction."
        }
        return null
    }

    private fun focalGuidance(lens: String): String = when {
        lens.contains("24") || lens.contains("28") || lens.contains("35") -> "wider environmental lifestyle perspective with natural spatial context"
        lens.contains("50") -> "neutral human-eye commercial catalog perspective with minimal distortion"
        lens.contains("85") -> "flattering fashion portrait compression with smooth background separation"
        lens.contains("105") -> "macro fabric and stitching clarity with shallow depth of field"
        lens.contains("135") || lens.contains("70") -> "pronounced telephoto perspective compression from studio distance"
        else -> "balanced commercial studio framing"
    }
}
