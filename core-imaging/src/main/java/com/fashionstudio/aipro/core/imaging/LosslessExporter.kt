package com.fashionstudio.aipro.core.imaging

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportFormat(val extension: String, val mimeType: String, val isLossless: Boolean) {
    PNG("png", "image/png", true),
    WEBP_LOSSLESS("webp", "image/webp", true),
    JPG("jpg", "image/jpeg", false)
}

enum class MarketplacePreset(
    val displayName: String,
    val aspectRatio: String,
    val recommendedWidth: Int,
    val recommendedHeight: Int,
    val complianceNote: String
) {
    AMAZON("Amazon Main", "1:1", 2048, 2048, "Main image requires pure white #FFFFFF background, >=85% frame fill, real untouched product. Keep original photo on file."),
    ETSY("Etsy Listing", "4:5", 2000, 2500, "Etsy 2026 policy requires an unedited photo of the real item and an AI disclosure tag when AI generates a new scene."),
    EBAY("eBay Listing", "1:1", 1600, 1600, "Must accurately depict the exact item shipped; avoid adding unincluded accessories."),
    SHOPIFY("Shopify Store", "1:1", 2048, 2048, "Square 2048x2048 PNG/WebP recommended for uniform collection grids."),
    INSTAGRAM("Instagram Feed", "4:5", 1080, 1350, "4:5 portrait maximizes vertical feed real estate."),
    TIKTOK("TikTok / Reels", "9:16", 1080, 1920, "9:16 full vertical framing."),
    PINTEREST("Pinterest Pin", "2:3", 1000, 1500, "2:3 vertical editorial framing."),
    FACEBOOK("Facebook Shop", "1:1", 1200, 1200, "Clean 1:1 commercial framing."),
    CUSTOM_STORE("Custom Web Store", "3:4", 1500, 2000, "High-resolution 3:4 fashion lookbook aspect ratio.")
}

data class ExportResult(
    val savedUriOrPath: String,
    val fileName: String,
    val nativeWidth: Int,
    val nativeHeight: Int,
    val isUpscaled: Boolean,
    val lossyCompressionWarning: String?
)

/**
 * Exports verified studio outputs to the Android Gallery (MediaStore) without writing fake EXIF
 * camera hardware tags. Defaults to lossless PNG to preserve protected garment pixels.
 */
@Singleton
class LosslessExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun buildSkuFileName(
        sku: String,
        preset: MarketplacePreset,
        versionNumber: Int,
        format: ExportFormat
    ): String {
        val cleanSku = sku.ifBlank { "PRODUCT" }.uppercase().replace(Regex("[^A-Z0-9_-]"), "_")
        val cleanPreset = preset.name.lowercase()
        return "${cleanSku}_${cleanPreset}_v${versionNumber}.${format.extension}"
    }

    suspend fun exportToGallery(
        bitmap: Bitmap,
        sku: String,
        preset: MarketplacePreset,
        versionNumber: Int,
        format: ExportFormat = ExportFormat.PNG,
        isUpscaled: Boolean = false
    ): ExportResult = withContext(Dispatchers.IO) {
        val fileName = buildSkuFileName(sku, preset, versionNumber, format)
        val warning = if (!format.isLossless) {
            "JPEG uses lossy compression and may alter individual protected garment pixels. Use PNG for strict pixel fidelity."
        } else null

        val compressFormat = when (format) {
            ExportFormat.PNG -> Bitmap.CompressFormat.PNG
            ExportFormat.JPG -> Bitmap.CompressFormat.JPEG
            ExportFormat.WEBP_LOSSLESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        }
        val quality = if (format == ExportFormat.JPG) 96 else 100

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/FashionStudioAIPro")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(compressFormat, quality, out)
                out.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            ExportResult(
                savedUriOrPath = uri.toString(),
                fileName = fileName,
                nativeWidth = bitmap.width,
                nativeHeight = bitmap.height,
                isUpscaled = isUpscaled,
                lossyCompressionWarning = warning
            )
        } else {
            // Fallback to app-specific external pictures directory
            val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FashionStudioAIPro").apply { mkdirs() }
            val outFile = File(fallbackDir, fileName)
            FileOutputStream(outFile).use { out ->
                bitmap.compress(compressFormat, quality, out)
                out.flush()
            }
            ExportResult(
                savedUriOrPath = outFile.absolutePath,
                fileName = fileName,
                nativeWidth = bitmap.width,
                nativeHeight = bitmap.height,
                isUpscaled = isUpscaled,
                lossyCompressionWarning = warning
            )
        }
    }
}
