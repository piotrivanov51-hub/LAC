package com.fashionstudio.aipro.core.imaging

import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Serializable
enum class ViolationReason {
    COLOR_SHIFT_DETECTED,
    SEAM_OR_TEXTURE_LOSS,
    CRITICAL_DETAIL_ALTERED,
    INTERIOR_PIXEL_TAMPERED,
    STRUCTURAL_DRIFT_SSIM,
    MISSING_REFERENCE_ANGLE
}

@Serializable
data class ProductDnaVerificationReport(
    val fidelityMode: FidelityMode,
    val status: DnaLockStatus,
    val exactInteriorMatchRatio: Double,      // 0.0..1.0 (Mode A expects >= 0.995)
    val ssimScore: Double,                    // 0.0..1.0 structural similarity inside protected mask
    val deltaE00Mean: Double,                 // Mean CIEDE2000 perceptual color difference (<= 1.5 imperceptible)
    val deltaE00P95: Double,                  // 95th percentile CIEDE2000 (catches localized color/print tampering)
    val edgePreservationScore: Double,        // 0.0..1.0 Sobel seam/texture preservation
    val criticalDetailSsim: Double,           // SSIM restricted to CriticalDetailMask (logos/buttons/pockets)
    val criticalDetailDeltaE00: Double,       // Mean ΔE00 on CriticalDetailMask
    val criticalDetailPass: Boolean,
    val commercialExportAllowed: Boolean,
    val violations: List<ViolationReason>,
    val remediationSuggestions: List<String>,
    val isApproximateVirtualEstimate: Boolean // True in Mode B; never claims 100% untouched pixels
)

data class VerificationBundle(
    val report: ProductDnaVerificationReport,
    val diffHeatmap: PixelBuffer
)

/**
 * Product DNA Verification Engine & Automatic Rejection System.
 *
 * Independent deterministic verification comparing original garment pixels against generated output:
 * - Exact interior pixel match ratio (Mode A)
 * - Luminance Structural Similarity Index (SSIM) inside ProtectedGarmentMask
 * - Full CIEDE2000 (ΔE00) perceptual color difference in CIE L*a*b*
 * - Sobel edge/seam gradient preservation score
 * - Dedicated Critical Detail ROI verification (logos, seams, pockets, buttons, prints)
 * - Per-pixel Difference Heatmap generation for 200-400% manual inspection
 */
@Singleton
class ProductDnaVerificationEngine @Inject constructor() {

    companion object {
        // Calibrated thresholds for Mode A (Original Pixel Protection)
        const val MODE_A_EXACT_MATCH_PASS = 0.995
        const val MODE_A_SSIM_PASS = 0.985
        const val MODE_A_DELTA_E_MEAN_PASS = 1.0
        const val MODE_A_DELTA_E_P95_PASS = 2.0
        const val MODE_A_EDGE_PASS = 0.96

        // Calibrated thresholds for Mode B (Authentic Virtual Try-On)
        const val MODE_B_SSIM_PASS = 0.84
        const val MODE_B_DELTA_E_MEAN_PASS = 4.5
        const val MODE_B_DELTA_E_P95_PASS = 8.5
        const val MODE_B_EDGE_PASS = 0.80
    }

    fun verify(
        original: PixelBuffer,
        candidate: PixelBuffer,
        protectedMask: AlphaMask,
        criticalDetailMask: AlphaMask? = null,
        fidelityMode: FidelityMode = FidelityMode.ORIGINAL_PIXEL_PROTECTION,
        missingRequiredAngleReference: Boolean = false
    ): VerificationBundle {
        require(original.width == candidate.width && original.height == candidate.height) {
            "Original and candidate buffers must share dimensions for aligned verification"
        }
        val w = original.width
        val h = original.height
        val heatmap = IntArray(w * h)

        var interiorTotal = 0
        var interiorExactMatches = 0
        val deltaEValues = ArrayList<Double>(w * h / 4)

        var sumL1 = 0.0; var sumL2 = 0.0
        var sumL1Sq = 0.0; var sumL2Sq = 0.0; var sumL1L2 = 0.0

        var critCount = 0
        var critDeltaESum = 0.0
        var critL1 = 0.0; var critL2 = 0.0
        var critL1Sq = 0.0; var critL2Sq = 0.0; var critL1L2 = 0.0

        var edgeDot = 0.0; var edgeNorm1 = 0.0; var edgeNorm2 = 0.0

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val maskA = protectedMask.alphaAt(x, y)
                if (maskA < 250) {
                    // Outside protected garment: neutral dark slate in heatmap
                    heatmap[idx] = 0xFF191B20.toInt()
                    continue
                }

                interiorTotal++
                val p1 = original.pixels[idx]
                val p2 = candidate.pixels[idx]
                val r1 = (p1 ushr 16) and 0xFF; val g1 = (p1 ushr 8) and 0xFF; val b1 = p1 and 0xFF
                val r2 = (p2 ushr 16) and 0xFF; val g2 = (p2 ushr 8) and 0xFF; val b2 = p2 and 0xFF

                if (r1 == r2 && g1 == g2 && b1 == b2) {
                    interiorExactMatches++
                }

                val l1 = 0.299 * r1 + 0.587 * g1 + 0.114 * b1
                val l2 = 0.299 * r2 + 0.587 * g2 + 0.114 * b2
                sumL1 += l1; sumL2 += l2
                sumL1Sq += l1 * l1; sumL2Sq += l2 * l2; sumL1L2 += l1 * l2

                val lab1 = rgbToLab(r1, g1, b1)
                val lab2 = rgbToLab(r2, g2, b2)
                val de = ciede2000(lab1, lab2)
                deltaEValues.add(de)

                // Heatmap coloring: Green (<=1.5), Amber (1.5..5.0), Red (>5.0)
                heatmap[idx] = when {
                    de <= 1.5 -> 0xFF22C55E.toInt()
                    de <= 5.0 -> 0xFFF59E0B.toInt()
                    else -> 0xFFEF4444.toInt()
                }

                if (criticalDetailMask != null && criticalDetailMask.alphaAt(x, y) >= 200) {
                    critCount++
                    critDeltaESum += de
                    critL1 += l1; critL2 += l2
                    critL1Sq += l1 * l1; critL2Sq += l2 * l2; critL1L2 += l1 * l2
                }

                if (x in 1 until w - 1 && y in 1 until h - 1) {
                    val gOrig = sobelMagnitude(original, x, y)
                    val gCand = sobelMagnitude(candidate, x, y)
                    edgeDot += gOrig * gCand
                    edgeNorm1 += gOrig * gOrig
                    edgeNorm2 += gCand * gCand
                }
            }
        }

        val n = interiorTotal.coerceAtLeast(1).toDouble()
        val exactMatchRatio = interiorExactMatches / n
        val ssim = computeSsim(n, sumL1, sumL2, sumL1Sq, sumL2Sq, sumL1L2)
        val deltaEMean = if (deltaEValues.isEmpty()) 0.0 else deltaEValues.average()
        val deltaEP95 = if (deltaEValues.isEmpty()) 0.0 else {
            deltaEValues.sort()
            deltaEValues[((deltaEValues.size - 1) * 0.95).toInt()]
        }
        val edgeScore = if (edgeNorm1 == 0.0 && edgeNorm2 == 0.0) 1.0
        else if (edgeNorm1 == 0.0 || edgeNorm2 == 0.0) 0.0
        else (edgeDot / (sqrt(edgeNorm1) * sqrt(edgeNorm2))).coerceIn(0.0, 1.0)

        val critSsim = if (critCount > 0) {
            computeSsim(critCount.toDouble(), critL1, critL2, critL1Sq, critL2Sq, critL1L2)
        } else ssim
        val critDeltaE = if (critCount > 0) critDeltaESum / critCount else deltaEMean

        val violations = mutableListOf<ViolationReason>()
        val suggestions = mutableListOf<String>()

        if (missingRequiredAngleReference) {
            violations.add(ViolationReason.MISSING_REFERENCE_ANGLE)
            suggestions.add("Upload the missing reference angle (e.g., back/side view) before commercial export.")
        }

        val criticalDetailPass = if (fidelityMode == FidelityMode.ORIGINAL_PIXEL_PROTECTION) {
            critSsim >= 0.98 && critDeltaE <= 1.2
        } else {
            critSsim >= 0.85 && critDeltaE <= 4.0
        }

        if (fidelityMode == FidelityMode.ORIGINAL_PIXEL_PROTECTION) {
            if (exactMatchRatio < MODE_A_EXACT_MATCH_PASS) {
                violations.add(ViolationReason.INTERIOR_PIXEL_TAMPERED)
                suggestions.add("Re-run Mode A deterministic compositor to restore unmodified garment pixels.")
            }
            if (deltaEMean > MODE_A_DELTA_E_MEAN_PASS || deltaEP95 > MODE_A_DELTA_E_P95_PASS) {
                violations.add(ViolationReason.COLOR_SHIFT_DETECTED)
                suggestions.add("Disable global color grading on the protected garment layer and export lossless PNG.")
            }
            if (ssim < MODE_A_SSIM_PASS) {
                violations.add(ViolationReason.STRUCTURAL_DRIFT_SSIM)
            }
            if (edgeScore < MODE_A_EDGE_PASS) {
                violations.add(ViolationReason.SEAM_OR_TEXTURE_LOSS)
                suggestions.add("Refine the Protected Garment Mask around seams, pockets, and hems.")
            }
            if (!criticalDetailPass) {
                violations.add(ViolationReason.CRITICAL_DETAIL_ALTERED)
                suggestions.add("Inspect logos, buttons, and prints at 200-400% zoom and expand Critical Detail Mask.")
            }
        } else {
            if (deltaEMean > MODE_B_DELTA_E_MEAN_PASS || deltaEP95 > MODE_B_DELTA_E_P95_PASS) {
                violations.add(ViolationReason.COLOR_SHIFT_DETECTED)
                suggestions.add("Color drift detected in Virtual Try-On. Switch to Original Pixel Protection or adjust lighting.")
            }
            if (ssim < MODE_B_SSIM_PASS) {
                violations.add(ViolationReason.STRUCTURAL_DRIFT_SSIM)
                suggestions.add("Garment geometry drifted. Choose a neutral front pose or add extra angle references.")
            }
            if (edgeScore < MODE_B_EDGE_PASS) {
                violations.add(ViolationReason.SEAM_OR_TEXTURE_LOSS)
                suggestions.add("Seam or fabric texture degraded. Use Mode A for verified e-commerce main photos.")
            }
            if (!criticalDetailPass) {
                violations.add(ViolationReason.CRITICAL_DETAIL_ALTERED)
                suggestions.add("Logo, pocket, or button detail changed. Re-generate only after confirming API cost.")
            }
        }

        val status = when {
            violations.isNotEmpty() -> DnaLockStatus.FIDELITY_FAILED
            fidelityMode == FidelityMode.AUTHENTIC_VIRTUAL_TRY_ON -> DnaLockStatus.VIRTUAL_RECONSTRUCTION
            exactMatchRatio >= MODE_A_EXACT_MATCH_PASS && deltaEMean <= 0.5 -> DnaLockStatus.VERIFIED
            else -> DnaLockStatus.REVIEW_REQUIRED
        }

        val commercialExportAllowed = status == DnaLockStatus.VERIFIED || status == DnaLockStatus.REVIEW_REQUIRED

        return VerificationBundle(
            report = ProductDnaVerificationReport(
                fidelityMode = fidelityMode,
                status = status,
                exactInteriorMatchRatio = exactMatchRatio,
                ssimScore = ssim,
                deltaE00Mean = deltaEMean,
                deltaE00P95 = deltaEP95,
                edgePreservationScore = edgeScore,
                criticalDetailSsim = critSsim,
                criticalDetailDeltaE00 = critDeltaE,
                criticalDetailPass = criticalDetailPass,
                commercialExportAllowed = commercialExportAllowed,
                violations = violations,
                remediationSuggestions = suggestions.distinct(),
                isApproximateVirtualEstimate = fidelityMode == FidelityMode.AUTHENTIC_VIRTUAL_TRY_ON
            ),
            diffHeatmap = PixelBuffer(w, h, heatmap)
        )
    }

    private fun computeSsim(
        n: Double,
        sumX: Double,
        sumY: Double,
        sumXSq: Double,
        sumYSq: Double,
        sumXY: Double
    ): Double {
        val muX = sumX / n
        val muY = sumY / n
        val varX = (sumXSq / n - muX * muX).coerceAtLeast(0.0)
        val varY = (sumYSq / n - muY * muY).coerceAtLeast(0.0)
        val covXY = sumXY / n - muX * muY
        val c1 = (0.01 * 255.0).pow(2)
        val c2 = (0.03 * 255.0).pow(2)
        val num = (2.0 * muX * muY + c1) * (2.0 * covXY + c2)
        val den = (muX * muX + muY * muY + c1) * (varX + varY + c2)
        return (num / den).coerceIn(0.0, 1.0)
    }

    private fun sobelMagnitude(buf: PixelBuffer, x: Int, y: Int): Double {
        fun l(px: Int, py: Int): Double {
            val p = buf.getPixel(px, py)
            return 0.299 * ((p ushr 16) and 0xFF) + 0.587 * ((p ushr 8) and 0xFF) + 0.114 * (p and 0xFF)
        }
        val gx = -l(x - 1, y - 1) - 2 * l(x - 1, y) - l(x - 1, y + 1) +
            l(x + 1, y - 1) + 2 * l(x + 1, y) + l(x + 1, y + 1)
        val gy = -l(x - 1, y - 1) - 2 * l(x, y - 1) - l(x + 1, y - 1) +
            l(x - 1, y + 1) + 2 * l(x, y + 1) + l(x + 1, y + 1)
        return sqrt(gx * gx + gy * gy)
    }

    private fun rgbToLab(r8: Int, g8: Int, b8: Int): DoubleArray {
        fun invCompand(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val r = invCompand(r8)
        val g = invCompand(g8)
        val b = invCompand(b8)

        // sRGB to XYZ (D65)
        val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
        val y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750) / 1.00000
        val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883

        fun f(t: Double): Double = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0
        val fx = f(x); val fy = f(y); val fz = f(z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    /**
     * Full CIEDE2000 perceptual color difference formula (Sharma, Wu, Dalal 2005).
     */
    private fun ciede2000(lab1: DoubleArray, lab2: DoubleArray): Double {
        val l1 = lab1[0]; val a1 = lab1[1]; val b1 = lab1[2]
        val l2 = lab2[0]; val a2 = lab2[1]; val b2 = lab2[2]

        val c1 = hypot(a1, b1)
        val c2 = hypot(a2, b2)
        val cBar = (c1 + c2) / 2.0
        val cBar7 = cBar.pow(7)
        val g = 0.5 * (1.0 - sqrt(cBar7 / (cBar7 + 25.0.pow(7))))

        val a1p = (1.0 + g) * a1
        val a2p = (1.0 + g) * a2
        val c1p = hypot(a1p, b1)
        val c2p = hypot(a2p, b2)

        fun hueAngle(ap: Double, b: Double): Double {
            if (ap == 0.0 && b == 0.0) return 0.0
            val deg = Math.toDegrees(atan2(b, ap))
            return if (deg >= 0.0) deg else deg + 360.0
        }
        val h1p = hueAngle(a1p, b1)
        val h2p = hueAngle(a2p, b2)

        val dLp = l2 - l1
        val dCp = c2p - c1p
        val dhp = when {
            c1p * c2p == 0.0 -> 0.0
            abs(h2p - h1p) <= 180.0 -> h2p - h1p
            h2p - h1p > 180.0 -> h2p - h1p - 360.0
            else -> h2p - h1p + 360.0
        }
        val dHp = 2.0 * sqrt(c1p * c2p) * sin(Math.toRadians(dhp / 2.0))

        val lBarP = (l1 + l2) / 2.0
        val cBarP = (c1p + c2p) / 2.0
        val hBarP = when {
            c1p * c2p == 0.0 -> h1p + h2p
            abs(h1p - h2p) <= 180.0 -> (h1p + h2p) / 2.0
            h1p + h2p < 360.0 -> (h1p + h2p + 360.0) / 2.0
            else -> (h1p + h2p - 360.0) / 2.0
        }

        val t = 1.0 -
            0.17 * cos(Math.toRadians(hBarP - 30.0)) +
            0.24 * cos(Math.toRadians(2.0 * hBarP)) +
            0.32 * cos(Math.toRadians(3.0 * hBarP + 6.0)) -
            0.20 * cos(Math.toRadians(4.0 * hBarP - 63.0))

        val dTheta = 30.0 * exp(-((hBarP - 275.0) / 25.0).pow(2))
        val cBarP7 = cBarP.pow(7)
        val rc = 2.0 * sqrt(cBarP7 / (cBarP7 + 25.0.pow(7)))
        val l50Sq = (lBarP - 50.0).pow(2)
        val sl = 1.0 + (0.015 * l50Sq) / sqrt(20.0 + l50Sq)
        val sc = 1.0 + 0.045 * cBarP
        val sh = 1.0 + 0.015 * cBarP * t
        val rt = -sin(Math.toRadians(2.0 * dTheta)) * rc

        val termL = dLp / sl
        val termC = dCp / sc
        val termH = dHp / sh
        return sqrt(termL * termL + termC * termC + termH * termH + rt * termC * termH)
    }
}
