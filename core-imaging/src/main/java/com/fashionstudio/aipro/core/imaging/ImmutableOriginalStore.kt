package com.fashionstudio.aipro.core.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class ImmutableOriginalRecord(
    val filePath: String,
    val sha256: String,
    val width: Int,
    val height: Int,
    val byteSize: Long
)

/**
 * Product DNA Lock — Level 1: Immutable Original Store.
 * Stores every uploaded garment photo as a read-only lossless PNG with SHA-256 verification.
 * No editing or generation step is ever permitted to overwrite an original file.
 */
@Singleton
class ImmutableOriginalStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val originalsDir: File
        get() = File(context.filesDir, "product_dna_originals").apply { mkdirs() }

    suspend fun ingestFromUri(projectId: String, angleTag: String, uri: Uri): ImmutableOriginalRecord =
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Unable to open image URI: $uri")
            val decoded = inputStream.use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalArgumentException("Unsupported or corrupt image format")
            saveImmutableBitmap(projectId, angleTag, decoded)
        }

    suspend fun saveImmutableBitmap(
        projectId: String,
        angleTag: String,
        bitmap: Bitmap
    ): ImmutableOriginalRecord = withContext(Dispatchers.IO) {
        val safeTag = angleTag.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val targetFile = File(originalsDir, "${projectId}_${safeTag}_${System.currentTimeMillis()}_orig.png")
        FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        val sha = computeSha256(targetFile)
        // Enforce read-only at filesystem level
        targetFile.setReadOnly()
        ImmutableOriginalRecord(
            filePath = targetFile.absolutePath,
            sha256 = sha,
            width = bitmap.width,
            height = bitmap.height,
            byteSize = targetFile.length()
        )
    }

    suspend fun verifyIntegrity(filePath: String, expectedSha256: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@withContext false
            computeSha256(file).equals(expectedSha256, ignoreCase = true)
        }

    companion object {
        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun computeSha256(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
