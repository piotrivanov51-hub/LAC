package com.fashionstudio.aipro.core.imaging

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Product DNA Lock — Level 2: Pluggable Garment Segmentation Engine.
 * Architecture allows drop-in replacement with LiteRT / ONNX Runtime Mobile (U2-Net, SAM-Mobile,
 * or Human Parsing models) without changing downstream masking or compositing layers.
 */
interface GarmentSegmentationEngine {
    val engineName: String
    fun segmentGarment(source: PixelBuffer, colorSensitivity: Float = 26f): ProtectedLayerStack
}

/**
 * Deterministic, zero-network on-device garment segmenter for MVP:
 * 1. Samples perimeter border pixels to model the table/hanger/studio background distribution.
 * 2. Performs border-seeded region growing (BFS flood-fill) so internal garment holes/prints
 *    that share background colors are NEVER accidentally erased.
 * 3. Applies controlled 1-pixel exterior alpha feathering while keeping all interior garment
 *    pixels locked at alpha = 255.
 */
@Singleton
class DeterministicBorderGarmentSegmenter @Inject constructor() : GarmentSegmentationEngine {

    override val engineName: String = "DeterministicBorderSegmenter-v1 (On-Device)"

    override fun segmentGarment(source: PixelBuffer, colorSensitivity: Float): ProtectedLayerStack {
        val w = source.width
        val h = source.height
        val total = w * h

        // 1. Estimate background mean RGB from 4 image edges
        var sumR = 0L; var sumG = 0L; var sumB = 0L; var borderCount = 0L
        for (x in 0 until w) {
            for (y in intArrayOf(0, h - 1)) {
                val p = source.getPixel(x, y)
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
                borderCount++
            }
        }
        for (y in 1 until h - 1) {
            for (x in intArrayOf(0, w - 1)) {
                val p = source.getPixel(x, y)
                sumR += (p ushr 16) and 0xFF
                sumG += (p ushr 8) and 0xFF
                sumB += p and 0xFF
                borderCount++
            }
        }
        val bgR = (sumR / borderCount.coerceAtLeast(1)).toFloat()
        val bgG = (sumG / borderCount.coerceAtLeast(1)).toFloat()
        val bgB = (sumB / borderCount.coerceAtLeast(1)).toFloat()

        // 2. Border-seeded BFS flood-fill to mark only connected background pixels
        val isBg = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0
        val threshSq = colorSensitivity * colorSensitivity * 3f

        fun tryEnqueue(x: Int, y: Int) {
            val idx = y * w + x
            if (isBg[idx]) return
            val p = source.getPixel(x, y)
            val dr = ((p ushr 16) and 0xFF) - bgR
            val dg = ((p ushr 8) and 0xFF) - bgG
            val db = (p and 0xFF) - bgB
            if (dr * dr + dg * dg + db * db <= threshSq) {
                isBg[idx] = true
                queue[tail++] = idx
            }
        }

        for (x in 0 until w) {
            tryEnqueue(x, 0)
            tryEnqueue(x, h - 1)
        }
        for (y in 1 until h - 1) {
            tryEnqueue(0, y)
            tryEnqueue(w - 1, y)
        }

        while (head < tail) {
            val idx = queue[head++]
            val x = idx % w
            val y = idx / w
            if (x > 0) tryEnqueue(x - 1, y)
            if (x < w - 1) tryEnqueue(x + 1, y)
            if (y > 0) tryEnqueue(x, y - 1)
            if (y < h - 1) tryEnqueue(x, y + 1)
        }

        // 3. Build ProtectedGarmentMask (255 = garment interior, controlled 1px boundary matting)
        val maskBytes = ByteArray(total)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!isBg[idx]) {
                    var bgNeighbors = 0
                    if (x > 0 && isBg[idx - 1]) bgNeighbors++
                    if (x < w - 1 && isBg[idx + 1]) bgNeighbors++
                    if (y > 0 && isBg[idx - w]) bgNeighbors++
                    if (y < h - 1 && isBg[idx + w]) bgNeighbors++
                    // Interior pixels stay strictly 255; boundary pixels get gentle alpha-matting
                    maskBytes[idx] = when (bgNeighbors) {
                        0 -> 255.toByte()
                        1 -> 220.toByte()
                        2 -> 170.toByte()
                        else -> 120.toByte()
                    }
                } else {
                    maskBytes[idx] = 0
                }
            }
        }

        val protectedMask = AlphaMask(w, h, maskBytes)
        val criticalDetailMask = detectHighContrastDetails(source, protectedMask)
        val editableBgMask = MaskBrushEditor.invertMask(protectedMask)

        return ProtectedLayerStack(
            originalGarment = source,
            protectedGarmentMask = protectedMask,
            criticalDetailMask = criticalDetailMask,
            editableBackgroundMask = editableBgMask
        )
    }

    /**
     * Auto-flags high-gradient regions inside the garment (contrast stitching, logos, buttons,
     * pocket edges, prints) into the initial CriticalDetailMask (user can refine with brush).
     */
    private fun detectHighContrastDetails(source: PixelBuffer, garmentMask: AlphaMask): AlphaMask {
        val w = source.width
        val h = source.height
        val detail = ByteArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                if (garmentMask.alphaAt(x, y) < 250) continue
                val c = luma(source.getPixel(x, y))
                val gx = abs(luma(source.getPixel(x + 1, y)) - luma(source.getPixel(x - 1, y)))
                val gy = abs(luma(source.getPixel(x, y + 1)) - luma(source.getPixel(x, y - 1)))
                val mag = sqrt((gx * gx + gy * gy).toDouble())
                if (mag > 42.0 && c >= 0) {
                    detail[y * w + x] = 255.toByte()
                }
            }
        }
        return AlphaMask(w, h, detail)
    }

    private fun luma(argb: Int): Float {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
