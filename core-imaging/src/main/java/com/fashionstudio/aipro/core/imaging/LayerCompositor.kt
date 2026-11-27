package com.fashionstudio.aipro.core.imaging

import javax.inject.Inject
import javax.inject.Singleton

data class ShadowConfig(
    val enabled: Boolean = true,
    val offsetX: Int = 8,
    val offsetY: Int = 14,
    val opacity: Float = 0.28f
)

/**
 * Product DNA Lock — Level 5: Deterministic Layer-Based Image Compositor (Mode A).
 *
 * Guarantees that every strictly protected interior pixel (mask alpha == 255) is copied
 * verbatim from the immutable original garment buffer, while AI-generated studio backgrounds,
 * lighting ambience, and external contact shadows are rendered strictly outside the product.
 */
@Singleton
class LayerCompositor @Inject constructor() {

    fun compositeOriginalPixelProtection(
        originalGarment: PixelBuffer,
        protectedMask: AlphaMask,
        generatedBackground: PixelBuffer,
        shadowConfig: ShadowConfig = ShadowConfig()
    ): PixelBuffer {
        val w = originalGarment.width
        val h = originalGarment.height
        val bg = if (generatedBackground.width == w && generatedBackground.height == h) {
            generatedBackground
        } else {
            resizeBilinear(generatedBackground, w, h)
        }

        val outPixels = IntArray(w * h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val maskAlpha = protectedMask.alphaAt(x, y)

                // STRICT INTERIOR PROTECTION: copy original pixel bit-for-bit with zero AI alteration
                if (maskAlpha == 255) {
                    outPixels[idx] = originalGarment.pixels[idx] or (0xFF shl 24)
                    continue
                }

                // Compute external contact shadow strictly outside garment interior
                var bgPixel = bg.pixels[idx]
                if (shadowConfig.enabled) {
                    val sx = x - shadowConfig.offsetX
                    val sy = y - shadowConfig.offsetY
                    if (sx in 0 until w && sy in 0 until h && protectedMask.alphaAt(sx, sy) >= 200) {
                        bgPixel = darkenPixel(bgPixel, shadowConfig.opacity)
                    }
                }

                if (maskAlpha == 0) {
                    outPixels[idx] = bgPixel or (0xFF shl 24)
                } else {
                    // Controlled boundary alpha matting (only 1px exterior rim)
                    outPixels[idx] = alphaBlend(originalGarment.pixels[idx], bgPixel, maskAlpha / 255f)
                }
            }
        }
        return PixelBuffer(w, h, outPixels)
    }

    private fun darkenPixel(argb: Int, shadowOpacity: Float): Int {
        val factor = (1f - shadowOpacity.coerceIn(0f, 0.85f))
        val r = (((argb ushr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((argb ushr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun alphaBlend(fg: Int, bg: Int, a: Float): Int {
        val inv = 1f - a
        val r = (((fg ushr 16) and 0xFF) * a + ((bg ushr 16) and 0xFF) * inv).toInt().coerceIn(0, 255)
        val g = (((fg ushr 8) and 0xFF) * a + ((bg ushr 8) and 0xFF) * inv).toInt().coerceIn(0, 255)
        val b = ((fg and 0xFF) * a + (bg and 0xFF) * inv).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun resizeBilinear(src: PixelBuffer, targetW: Int, targetH: Int): PixelBuffer {
        val out = IntArray(targetW * targetH)
        val xRatio = src.width.toFloat() / targetW
        val yRatio = src.height.toFloat() / targetH
        for (y in 0 until targetH) {
            val sy = (y * yRatio).toInt().coerceIn(0, src.height - 1)
            for (x in 0 until targetW) {
                val sx = (x * xRatio).toInt().coerceIn(0, src.width - 1)
                out[y * targetW + x] = src.getPixel(sx, sy)
            }
        }
        return PixelBuffer(targetW, targetH, out)
    }
}
