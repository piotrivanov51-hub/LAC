package com.fashionstudio.aipro.core.imaging

import kotlinx.serialization.Serializable

/**
 * Product DNA Lock — Levels 3 & 4: Multi-Layer Protected Mask Architecture.
 */
enum class MaskLayerType {
    PROTECTED_GARMENT,      // Level 3: Immutable original garment pixels (interior alpha=255)
    CRITICAL_DETAIL,        // Level 4: Logos, buttons, rivets, pockets, seams, prints, embroidery
    EDITABLE_BACKGROUND,    // Studio backdrop / lifestyle environment
    GENERATED_OUTFIT,       // Complementary top/bottom outside the original product
    GENERATED_FOOTWEAR,     // Complementary shoes
    GENERATED_ENVIRONMENT,  // Props & architectural elements
    EXTERNAL_SHADOW         // Ground contact shadows cast outside protected garment pixels
}

@Serializable
enum class FidelityMode {
    /**
     * Mode A (Default): Maximum pixel protection.
     * Original garment interior pixels are preserved deterministically via LayerCompositor.
     * Generative AI is restricted strictly to background, lighting environment, and external shadows.
     */
    ORIGINAL_PIXEL_PROTECTION,

    /**
     * Mode B: Authentic Virtual Try-On.
     * Controlled garment transfer onto a virtual model with multi-reference guardrails and
     * strict post-generation Product DNA verification. Reconstructed regions are transparently labeled.
     */
    AUTHENTIC_VIRTUAL_TRY_ON
}

@Serializable
enum class DnaLockStatus {
    PROTECTED,               // Masks created & original SHA-256 locked
    VERIFIED,                // Deterministic + AI verification passed thresholds
    REVIEW_REQUIRED,         // Borderline metrics or minor edge delta; manual zoom check required
    FIDELITY_FAILED,         // Violation detected (color, seam, pocket, logo, or shape drift) — commercial export blocked
    VIRTUAL_RECONSTRUCTION   // Mode B generated/warped regions present — cannot be labeled as untouched original pixels
}

data class ProtectedLayerStack(
    val originalGarment: PixelBuffer,
    val protectedGarmentMask: AlphaMask,
    val criticalDetailMask: AlphaMask,
    val editableBackgroundMask: AlphaMask
)

/**
 * Interactive brush editor for refining ProtectedGarmentMask and marking CriticalDetailMask
 * (logos, seams, pockets, buttons, prints) at 200-400% zoom.
 */
object MaskBrushEditor {
    fun applyCircularStroke(
        mask: AlphaMask,
        centerX: Int,
        centerY: Int,
        radiusPx: Int,
        protect: Boolean
    ) {
        val targetAlpha = if (protect) 255 else 0
        val rSq = radiusPx * radiusPx
        val minX = (centerX - radiusPx).coerceAtLeast(0)
        val maxX = (centerX + radiusPx).coerceAtMost(mask.width - 1)
        val minY = (centerY - radiusPx).coerceAtLeast(0)
        val maxY = (centerY + radiusPx).coerceAtMost(mask.height - 1)
        for (y in minY..maxY) {
            val dy = y - centerY
            for (x in minX..maxX) {
                val dx = x - centerX
                if (dx * dx + dy * dy <= rSq) {
                    mask.setAlpha(x, y, targetAlpha)
                }
            }
        }
    }

    fun invertMask(mask: AlphaMask): AlphaMask {
        val out = ByteArray(mask.data.size)
        for (i in mask.data.indices) {
            out[i] = (255 - (mask.data[i].toInt() and 0xFF)).toByte()
        }
        return AlphaMask(mask.width, mask.height, out)
    }
}
